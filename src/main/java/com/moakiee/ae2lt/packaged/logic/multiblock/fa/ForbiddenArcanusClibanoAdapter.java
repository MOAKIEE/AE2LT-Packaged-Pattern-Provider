package com.moakiee.ae2lt.packaged.logic.multiblock.fa;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.moakiee.ae2lt.logic.AllowedOutputFilter;
import com.moakiee.ae2lt.overload.model.MatchMode;
import com.moakiee.ae2lt.overload.pattern.OverloadedProviderOnlyPatternDetails;
import com.moakiee.ae2lt.packaged.item.AdapterIds;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan;
import com.moakiee.ae2lt.packaged.logic.multiblock.InsertionStrategy;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.TargetSlot;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingResult;

/**
 * Runtime adapter for Forbidden &amp; Arcanus's Clibano &mdash; a 3×3×3
 * multiblock furnace with a hidden {@code clibano_main_part} BE acting as
 * the entry point. The block exposes 7 slots:
 *
 * <pre>
 * 0 SOUL | 1 FUEL | 2 ENHANCER | 3 INPUT_A | 4 INPUT_B | 5 RESULT_A | 6 RESULT_B
 * </pre>
 *
 * <p>We use only the {@code A} pair (slot 3 in, slot 5 out) so two
 * independent patterns can run on the same Clibano via lane parallelism.
 * SOUL / FUEL / ENHANCER stay in the player's manual upkeep loop &mdash;
 * Clibano needs them in-world to run, but providing them isn't a pattern
 * input so the adapter doesn't manage them. We also intentionally skip the
 * {@link com.stal111.forbidden_arcanus.common.block.entity.clibano.ClibanoFireType}
 * compatibility check: if the player feeds the wrong fuel/soul, the BE's
 * own canSmelt() will reject the recipe and our input simply waits in
 * slot 3 until the right fire type is available, which is the same UX a
 * manual user would see.
 *
 * <p>Mode is {@link BindingMode#REAL} because Clibano has a tick-driven
 * burn time (typically 200&ndash;400 ticks per item) and consumes physical
 * fuel/soul during smelting.
 */
public final class ForbiddenArcanusClibanoAdapter implements MultiblockAdapter {

    private static final Logger LOG = LoggerFactory.getLogger("ae2ltpp/fa-clibano");

    private static final String MOD_ID = "forbidden_arcanus";
    private static final ResourceLocation MAIN_PART_BLOCK = faId("clibano_main_part");
    private static final ResourceLocation RECIPE_TYPE_ID = faId("clibano_combustion");

    private static final int INPUT_SLOT_A = 3;
    private static final int INPUT_SLOT_B = 4;
    private static final int RESULT_SLOT_A = 5;
    private static final int RESULT_SLOT_B = 6;
    private static final int MAX_INPUT_UNITS = 64;

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean recognizesMain(ServerLevel level, BlockPos pos, BlockEntity be) {
        return be != null && isFaLoaded() && blockId(be.getBlockState()).equals(MAIN_PART_BLOCK);
    }

    @Override
    public ResourceLocation requiredAdapterId(ServerLevel level, BlockPos pos) {
        return AdapterIds.FA_CLIBANO;
    }

    @Override
    @Nullable
    public BindingResult bind(ServerLevel level, BlockPos mainPos, IPatternDetails pattern) {
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)) {
            return null;
        }
        if (!hasSingleItemOutput(pattern)) {
            return null;
        }
        var candidate = findCandidateRecipe(level, pattern);
        if (candidate == null) {
            return null;
        }
        LOG.info("bind: matched recipe at {} (ingredients={})",
                mainPos, candidate.ingredients().size());
        return new BindingResult(new ClibanoBindHandle(candidate), BindingMode.REAL);
    }

    @Override
    public boolean canDispatch(ServerLevel level, BlockPos mainPos, Object handle) {
        if (!(handle instanceof ClibanoBindHandle bind)) {
            return false;
        }
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)) {
            return false;
        }
        var handler = ClibanoReflection.getItemHandler(be);
        if (handler == null || handler.getSlots() <= RESULT_SLOT_B) {
            return false;
        }
        if (!handler.getStackInSlot(INPUT_SLOT_A).isEmpty()) {
            return false;
        }
        // Block dispatch while result slot A still has the last output the
        // owner hasn't pulled yet — otherwise the next batch's finished
        // stack collides on top of the previous one (ClibanoMainBlockEntity
        // refuses to write when the result isn't stackable-mergeable).
        if (!handler.getStackInSlot(RESULT_SLOT_A).isEmpty()) {
            return false;
        }
        return bind.recipe().ingredients().size() == 1
                || handler.getStackInSlot(INPUT_SLOT_B).isEmpty();
    }

    @Override
    @Nullable
    public DispatchPlan planWithBinding(ServerLevel level, BlockPos mainPos,
                                        IPatternDetails pattern, KeyCounter[] inputs,
                                        Object handle, IActionSource source) {
        if (!(handle instanceof ClibanoBindHandle bind)) {
            return null;
        }

        var ingredients = bind.recipe().ingredients();
        var units = expandInputUnits(inputs);
        if (units == null || units.size() != ingredients.size()) {
            return null;
        }

        // Greedy assignment: each declared ingredient claims the first
        // matching unit. Single-input recipes go to slot 3; double-input
        // recipes go to slot 3 + 4 in declaration order.
        boolean[] used = new boolean[units.size()];
        var matched = new ArrayList<PlannedUnit>(ingredients.size());
        for (var ing : ingredients) {
            PlannedUnit picked = null;
            for (int i = 0; i < units.size(); i++) {
                if (used[i]) continue;
                if (ing.test(units.get(i).stack())) {
                    used[i] = true;
                    picked = units.get(i);
                    break;
                }
            }
            if (picked == null) return null;
            matched.add(picked);
        }

        var targets = new ArrayList<TargetSlot>(matched.size());
        targets.add(new TargetSlot(
                level, mainPos, null,
                List.of(matched.get(0).toGenericStack()),
                InsertionStrategy.CUSTOM,
                slotInserter(level, mainPos, matched.get(0), INPUT_SLOT_A)));
        if (matched.size() >= 2) {
            targets.add(new TargetSlot(
                    level, mainPos, null,
                    List.of(matched.get(1).toGenericStack()),
                    InsertionStrategy.CUSTOM,
                    slotInserter(level, mainPos, matched.get(1), INPUT_SLOT_B)));
        }

        return new DispatchPlan(List.copyOf(targets), null);
    }

    @Override
    public List<GenericStack> extractOutputs(ServerLevel level, BlockPos mainPos,
                                             AllowedOutputFilter filter,
                                             IActionSource source) {
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)) {
            return List.of();
        }
        var handler = ClibanoReflection.getItemHandler(be);
        if (handler == null || handler.getSlots() <= RESULT_SLOT_B
                || !(handler instanceof IItemHandlerModifiable mod)) {
            return List.of();
        }
        var pulled = pullSlot(mod, RESULT_SLOT_A, level, mainPos, filter);
        if (pulled != null) {
            be.setChanged();
            return List.of(pulled);
        }
        pulled = pullSlot(mod, RESULT_SLOT_B, level, mainPos, filter);
        if (pulled != null) {
            be.setChanged();
            return List.of(pulled);
        }
        return List.of();
    }

    @Nullable
    private GenericStack pullSlot(IItemHandlerModifiable mod, int slot,
                                  ServerLevel level, BlockPos mainPos,
                                  AllowedOutputFilter filter) {
        var stack = mod.getStackInSlot(slot);
        if (stack.isEmpty()) {
            return null;
        }
        var key = AEItemKey.of(stack);
        if (!allowsAutoReturn(level, mainPos, filter, key)) {
            return null;
        }
        var copy = stack.copy();
        mod.setStackInSlot(slot, ItemStack.EMPTY);
        return new GenericStack(AEItemKey.of(copy), copy.getCount());
    }

    // ===== Inserters =====

    private static BiFunction<GenericStack, Actionable, Long> slotInserter(
            ServerLevel level, BlockPos pos, PlannedUnit unit, int slot) {
        return (stack, mode) -> {
            if (stack.amount() != 1 || !unit.key().equals(stack.what())) {
                return 0L;
            }
            var be = level.getBlockEntity(pos);
            if (be == null || !blockId(be.getBlockState()).equals(MAIN_PART_BLOCK)) {
                return 0L;
            }
            var handler = ClibanoReflection.getItemHandler(be);
            if (!(handler instanceof IItemHandlerModifiable mod) || mod.getSlots() <= slot) {
                return 0L;
            }
            if (!mod.getStackInSlot(slot).isEmpty()) {
                return 0L;
            }
            if (mode == Actionable.MODULATE) {
                mod.setStackInSlot(slot, unit.stack());
                be.setChanged();
            }
            return 1L;
        };
    }

    // ===== Recipe matching =====

    @Nullable
    private static ClibanoRecipeView findCandidateRecipe(ServerLevel level, IPatternDetails pattern) {
        for (var holder : recipes(level)) {
            var recipe = holder.value();
            var result = recipeResult(recipe, level);
            if (result == null || result.isEmpty()) continue;
            if (!outputMatches(pattern, result)) continue;
            var view = ClibanoRecipeView.from(recipe);
            if (view == null) continue;
            return view;
        }
        return null;
    }

    @Nullable
    private static ItemStack recipeResult(Object recipe, ServerLevel level) {
        if (!(recipe instanceof Recipe<?> r)) return null;
        try {
            return r.getResultItem(level.registryAccess());
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<RecipeHolder<?>> recipes(ServerLevel level) {
        return BuiltInRegistries.RECIPE_TYPE.getOptional(RECIPE_TYPE_ID)
                .map(type -> (List<RecipeHolder<?>>) (List<?>) level.getRecipeManager()
                        .getAllRecipesFor((RecipeType) type))
                .orElse(List.of());
    }

    // ===== Helpers =====

    @Nullable
    private static List<PlannedUnit> expandInputUnits(KeyCounter[] inputs) {
        var units = new ArrayList<PlannedUnit>();
        long total = 0;
        for (var counter : inputs) {
            for (var entry : counter) {
                if (!(entry.getKey() instanceof AEItemKey itemKey)) {
                    return null;
                }
                long amount = entry.getLongValue();
                if (amount <= 0) continue;
                total += amount;
                if (total > MAX_INPUT_UNITS) return null;
                for (long i = 0; i < amount; i++) {
                    units.add(new PlannedUnit(itemKey));
                }
            }
        }
        return units;
    }

    private static boolean hasSingleItemOutput(IPatternDetails pattern) {
        var outputs = pattern.getOutputs();
        return outputs.size() == 1 && outputs.getFirst().what() instanceof AEItemKey;
    }

    private static boolean outputMatches(IPatternDetails pattern, ItemStack result) {
        var outputs = pattern.getOutputs();
        if (outputs.size() != 1) return false;
        var expected = outputs.getFirst();
        if (!(expected.what() instanceof AEItemKey expectedKey) || expected.amount() != result.getCount()) {
            return false;
        }
        var actual = AEItemKey.of(result);
        return outputMode(pattern, 0) == MatchMode.ID_ONLY
                ? expectedKey.dropSecondary().equals(actual.dropSecondary())
                : expectedKey.equals(actual);
    }

    private static MatchMode outputMode(IPatternDetails pattern, int outputIndex) {
        if (pattern instanceof OverloadedProviderOnlyPatternDetails overload) {
            var outputs = overload.overloadPatternDetailsView().outputs();
            if (outputIndex >= 0 && outputIndex < outputs.size()) {
                return outputs.get(outputIndex).matchMode();
            }
        }
        return MatchMode.STRICT;
    }

    private static boolean isFaLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    private static ResourceLocation blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock());
    }

    private static ResourceLocation faId(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    // ===== Records =====

    private record PlannedUnit(AEItemKey key) {
        PlannedUnit {
            Objects.requireNonNull(key, "key");
        }

        ItemStack stack() {
            return key.toStack(1);
        }

        GenericStack toGenericStack() {
            return new GenericStack(key, 1);
        }
    }

    private record ClibanoBindHandle(ClibanoRecipeView recipe) {}

    /**
     * Lazy view of a {@code ClibanoRecipe}. The recipe's raw ingredients field
     * is an {@code Either<Ingredient, Pair<Ingredient, Ingredient>>}, but the
     * standard {@link Recipe#getIngredients()} call already flattens it to a
     * {@code NonNullList<Ingredient>} of size 1 or 2 &mdash; we just unwrap
     * that and keep it.
     */
    private record ClibanoRecipeView(Object rawRecipe, List<Ingredient> ingredients) {
        @Nullable
        static ClibanoRecipeView from(Object recipe) {
            if (!(recipe instanceof Recipe<?> r)) return null;
            try {
                var list = r.getIngredients();
                if (list == null || list.isEmpty() || list.size() > 2) return null;
                var copy = new ArrayList<Ingredient>(list.size());
                for (var ing : list) {
                    if (ing == null || ing.isEmpty()) return null;
                    copy.add(ing);
                }
                return new ClibanoRecipeView(recipe, List.copyOf(copy));
            } catch (RuntimeException | LinkageError ignored) {
                return null;
            }
        }
    }

    // ===== Reflection =====

    private static final class ClibanoReflection {
        private static final String VALHELSIA_CONTAINER_CLASS =
                "net.valhelsia.valhelsia_core.api.common.block.entity.neoforge.ValhelsiaContainerBlockEntity";

        private static volatile boolean lookupDone;
        private static volatile @Nullable Method getItemStackHandlerMethod;

        @Nullable
        static IItemHandler getItemHandler(BlockEntity be) {
            ensureLookup();
            if (getItemStackHandlerMethod == null) return null;
            try {
                var v = getItemStackHandlerMethod.invoke(be);
                return v instanceof IItemHandler h ? h : null;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        private static void ensureLookup() {
            if (lookupDone) return;
            synchronized (ClibanoReflection.class) {
                if (lookupDone) return;
                try {
                    var valhelsiaClass = Class.forName(VALHELSIA_CONTAINER_CLASS);
                    getItemStackHandlerMethod = valhelsiaClass.getMethod("getItemStackHandler");
                } catch (ClassNotFoundException | NoSuchMethodException
                         | SecurityException | LinkageError e) {
                    LOG.warn("Clibano reflection: ValhelsiaContainerBlockEntity#getItemStackHandler lookup failed: {}",
                            e.toString());
                }
                lookupDone = true;
            }
        }
    }
}
