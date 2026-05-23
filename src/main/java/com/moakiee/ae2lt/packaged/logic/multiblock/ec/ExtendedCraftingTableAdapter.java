package com.moakiee.ae2lt.packaged.logic.multiblock.ec;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.moakiee.ae2lt.logic.AllowedOutputFilter;
import com.moakiee.ae2lt.overload.model.MatchMode;
import com.moakiee.ae2lt.overload.pattern.OverloadedProviderOnlyPatternDetails;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan;
import com.moakiee.ae2lt.packaged.logic.multiblock.VirtualCraftingAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.VirtualCraftingResult;

/**
 * Runtime adapter for ExtendedCrafting's ordinary crafting tables.
 * Pure reflection: ExtendedCrafting is not a compile dependency.
 *
 * Supported blocks:
 *   basic_table    3x3, tier 1
 *   advanced_table 5x5, tier 2
 *   elite_table    7x7, tier 3
 *   ultimate_table 9x9, tier 4
 *
 * These tables act as virtual crafting lanes for packaged providers. Inputs are
 * consumed by AE, while this adapter validates the matching table recipe and
 * returns the assembled result plus remaining items without inserting into the
 * table grid.
 */
public final class ExtendedCraftingTableAdapter implements VirtualCraftingAdapter {

    private static final String MOD_ID = "extendedcrafting";
    private static final ResourceLocation BASIC_TABLE_BLOCK = ecId("basic_table");
    private static final ResourceLocation ADVANCED_TABLE_BLOCK = ecId("advanced_table");
    private static final ResourceLocation ELITE_TABLE_BLOCK = ecId("elite_table");
    private static final ResourceLocation ULTIMATE_TABLE_BLOCK = ecId("ultimate_table");
    private static final ResourceLocation RECIPE_TYPE_ID = ecId("table");
    private static final int MAX_INPUT_UNITS = 81;
    private static final int MAX_STABLE_PLAN_CACHE_ENTRIES = 128;

    private final ExtendedCraftingTableBoundedCache<PlanCacheKey, PlanMatch> stablePlanCache =
            new ExtendedCraftingTableBoundedCache<>(MAX_STABLE_PLAN_CACHE_ENTRIES);

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean recognizesMain(ServerLevel level, BlockPos pos, BlockEntity be) {
        return be != null && isExtendedCraftingLoaded()
                && tableSpec(be.getBlockState()) != null
                && EcReflection.isSupportedTable(be);
    }

    @Override
    @Nullable
    public DispatchPlan plan(ServerLevel level, BlockPos mainPos,
                             IPatternDetails pattern, KeyCounter[] inputs,
                             IActionSource source) {
        return null;
    }

    @Override
    @Nullable
    public VirtualCraftingResult planVirtualCraft(ServerLevel level, BlockPos mainPos,
                                                  IPatternDetails pattern, KeyCounter[] inputs,
                                                  IActionSource source) {
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)) {
            return null;
        }

        var spec = tableSpec(be.getBlockState());
        if (spec == null) {
            return null;
        }

        var handler = EcReflection.getHandler(be);
        if (handler == null || handler.getSlots() < spec.slots() || !gridIsEmpty(handler, spec.slots())) {
            return null;
        }

        var match = findPlanMatchCached(level, pattern, inputs, spec);
        if (match == null || match.units().isEmpty()) {
            return null;
        }

        var input = EcReflection.createTableInput(spec.gridSize(), stacksForPlan(spec.slots(), match.units()), spec.tier());
        if (input == null || !recipeMatches(match.recipe(), input, level)) {
            stablePlanCache.remove(new PlanCacheKey(level.dimension(), spec, patternKey(pattern), inputSignature(inputs)));
            return null;
        }

        var result = assemble(match.recipe(), input, level);
        if (result == null || result.isEmpty() || !outputMatches(pattern, result)) {
            return null;
        }

        var remaining = remainingItems(match.recipe(), input);
        if (remaining == null) {
            return null;
        }

        var outputs = virtualOutputs(result, remaining);
        if (outputs.isEmpty()) {
            return null;
        }
        return new VirtualCraftingResult(outputs);
    }

    @Override
    public List<GenericStack> extractOutputs(ServerLevel level, BlockPos mainPos,
                                             AllowedOutputFilter filter,
                                             IActionSource source) {
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)) {
            return List.of();
        }

        var spec = tableSpec(be.getBlockState());
        var handler = EcReflection.getHandler(be);
        if (spec == null || !(handler instanceof IItemHandlerModifiable modifiable)
                || handler.getSlots() < spec.slots() || gridIsEmpty(handler, spec.slots())) {
            return List.of();
        }

        var input = EcReflection.createTableInput(spec.gridSize(), currentStacks(handler, spec.slots()), spec.tier());
        if (input == null) {
            return List.of();
        }

        var recipe = findMatchingRecipe(level, input);
        if (recipe == null) {
            return List.of();
        }

        var result = assemble(recipe, input, level);
        if (result == null || result.isEmpty()) {
            return List.of();
        }

        var key = AEItemKey.of(result);
        if (!filter.matches(key)) {
            return List.of();
        }

        var remaining = remainingItems(recipe, input);
        if (remaining == null || !canApplyRemaining(handler, spec.gridSize(), input, remaining)) {
            return List.of();
        }

        applyCraftRemainders(modifiable, spec.gridSize(), input, remaining);
        return List.of(new GenericStack(AEItemKey.of(result), result.getCount()));
    }

    @Nullable
    private PlanMatch findPlanMatchCached(ServerLevel level, IPatternDetails pattern,
                                          KeyCounter[] inputs, TableSpec spec) {
        var key = new PlanCacheKey(level.dimension(), spec, patternKey(pattern), inputSignature(inputs));
        var cached = stablePlanCache.get(key);
        if (cached != null && cachedPlanStillMatches(level, pattern, spec, cached)) {
            return cached;
        }
        if (cached != null) {
            stablePlanCache.remove(key);
        }

        var computed = findPlanMatch(level, pattern, inputs, spec);
        if (computed != null) {
            stablePlanCache.put(key, computed);
        }
        return computed;
    }

    private static boolean cachedPlanStillMatches(ServerLevel level, IPatternDetails pattern,
                                                  TableSpec spec, PlanMatch match) {
        var input = EcReflection.createTableInput(
                spec.gridSize(), stacksForPlan(spec.slots(), match.units()), spec.tier());
        if (input == null || !recipeMatches(match.recipe(), input, level)) {
            return false;
        }
        var result = assemble(match.recipe(), input, level);
        return result != null && !result.isEmpty() && outputMatches(pattern, result);
    }

    private static List<GenericStack> virtualOutputs(ItemStack result, NonNullList<ItemStack> remaining) {
        var outputs = new ArrayList<GenericStack>();
        outputs.add(new GenericStack(AEItemKey.of(result), result.getCount()));
        for (var stack : remaining) {
            if (!stack.isEmpty()) {
                outputs.add(new GenericStack(AEItemKey.of(stack), stack.getCount()));
            }
        }
        return List.copyOf(outputs);
    }

    private static String patternKey(IPatternDetails pattern) {
        if (pattern instanceof OverloadedProviderOnlyPatternDetails overload) {
            return overload.overloadPatternIdentity();
        }
        return String.valueOf(pattern.getDefinition());
    }

    private static List<InputCounterSignature> inputSignature(KeyCounter[] inputs) {
        var signature = new ArrayList<InputCounterSignature>(inputs.length);
        for (var input : inputs) {
            var entries = new ArrayList<InputAmount>();
            for (var entry : input) {
                long amount = entry.getLongValue();
                if (amount > 0) {
                    entries.add(new InputAmount(entry.getKey(), amount));
                }
            }
            entries.sort(Comparator
                    .comparingInt((InputAmount entry) -> entry.key().hashCode())
                    .thenComparing(entry -> String.valueOf(entry.key())));
            signature.add(new InputCounterSignature(List.copyOf(entries)));
        }
        return List.copyOf(signature);
    }

    @Nullable
    private static PlanMatch findPlanMatch(ServerLevel level, IPatternDetails pattern,
                                           KeyCounter[] inputs, TableSpec spec) {
        if (!hasSingleItemOutput(pattern)) {
            return null;
        }

        var slotted = planOverloadedInputs(pattern, inputs, spec);
        if (slotted != null) {
            var input = EcReflection.createTableInput(spec.gridSize(), stacksForPlan(spec.slots(), slotted), spec.tier());
            if (input == null) {
                return null;
            }
            var recipe = findMatchingRecipe(level, pattern, input);
            return recipe == null ? null : new PlanMatch(slotted, recipe);
        }

        var units = expandInputUnits(inputs);
        if (units == null || units.isEmpty() || units.size() > spec.slots()) {
            return null;
        }

        for (var holder : recipes(level)) {
            var recipe = holder.value();
            var result = resultItem(recipe, level);
            if (result == null || result.isEmpty() || !outputMatches(pattern, result)) {
                continue;
            }

            var planned = planCompactInputsForRecipe(recipe, units, spec);
            if (planned == null) {
                continue;
            }
            var input = EcReflection.createTableInput(spec.gridSize(), stacksForPlan(spec.slots(), planned), spec.tier());
            if (input != null && recipeMatches(recipe, input, level)) {
                return new PlanMatch(planned, recipe);
            }
        }
        return null;
    }

    @Nullable
    private static List<PlannedUnit> planOverloadedInputs(IPatternDetails pattern, KeyCounter[] inputs, TableSpec spec) {
        if (!(pattern instanceof OverloadedProviderOnlyPatternDetails overload)) {
            return null;
        }

        var overloadInputs = overload.overloadPatternDetailsView().inputs();
        if (overloadInputs.isEmpty() || overloadInputs.size() > inputs.length) {
            return null;
        }

        var planned = new ArrayList<PlannedUnit>(overloadInputs.size());
        var usedSlots = new HashSet<Integer>();
        long total = 0;
        for (int i = 0; i < overloadInputs.size(); i++) {
            var inputSlot = overloadInputs.get(i);
            int slot = inputSlot.slotIndex();
            if (slot < 0 || slot >= spec.slots() || !usedSlots.add(slot) || inputSlot.amountPerCraft() != 1) {
                return null;
            }
            var key = singleItemKey(inputs[i], inputSlot.matchMode(), AEItemKey.of(inputSlot.template()));
            if (key == null) {
                return null;
            }
            total++;
            if (total > MAX_INPUT_UNITS) {
                return null;
            }
            planned.add(new PlannedUnit(slot, key));
        }
        return List.copyOf(planned);
    }

    @Nullable
    private static AEItemKey singleItemKey(KeyCounter counter, MatchMode mode, AEItemKey template) {
        AEItemKey result = null;
        long total = 0;
        for (var entry : counter) {
            if (!(entry.getKey() instanceof AEItemKey itemKey)) {
                return null;
            }
            long amount = entry.getLongValue();
            if (amount <= 0) {
                continue;
            }
            total += amount;
            if (total > 1) {
                return null;
            }
            if (mode == MatchMode.ID_ONLY) {
                if (itemKey.getItem() != template.getItem()) {
                    return null;
                }
            } else if (!itemKey.equals(template)) {
                return null;
            }
            result = itemKey;
        }
        return total == 1 ? result : null;
    }

    @Nullable
    private static List<PlannedUnit> planCompactInputsForRecipe(Object recipe,
                                                                List<AEItemKey> units,
                                                                TableSpec spec) {
        var ingredients = ingredients(recipe);
        if (ingredients == null || ingredients.isEmpty()) {
            return null;
        }

        var width = EcReflection.recipeWidth(recipe);
        if (width == null) {
            return planShapelessCompactInputs(units, spec);
        }
        if (width <= 0 || width > spec.gridSize()) {
            return null;
        }

        var cells = new ArrayList<Ingredient>(ingredients.size());
        for (var ingredient : ingredients) {
            if (ingredient.isEmpty()) {
                cells.add(null);
                continue;
            }
            cells.add(ingredient);
        }

        var assignments = ExtendedCraftingTableGridPlanner.placeMatching(
                width,
                spec.gridSize(),
                cells,
                units,
                (ingredient, key) -> ingredient.test(key.toStack(1)));
        if (assignments.isEmpty()) {
            return null;
        }

        var planned = new ArrayList<PlannedUnit>(assignments.size());
        for (var assignment : assignments) {
            planned.add(new PlannedUnit(assignment.slot(), assignment.value()));
        }
        return List.copyOf(planned);
    }

    private static List<PlannedUnit> planShapelessCompactInputs(List<AEItemKey> units, TableSpec spec) {
        var cells = new ArrayList<PlannedUnit>(units.size());
        for (var unit : units) {
            cells.add(new PlannedUnit(-1, unit));
        }
        var assignments = ExtendedCraftingTableGridPlanner.place(spec.gridSize(), spec.gridSize(), cells);
        var planned = new ArrayList<PlannedUnit>(assignments.size());
        for (var assignment : assignments) {
            planned.add(new PlannedUnit(assignment.slot(), assignment.value().key()));
        }
        return List.copyOf(planned);
    }

    @Nullable
    private static List<AEItemKey> expandInputUnits(KeyCounter[] inputs) {
        var units = new ArrayList<AEItemKey>();
        long total = 0;
        for (var counter : inputs) {
            for (var entry : counter) {
                if (!(entry.getKey() instanceof AEItemKey itemKey)) {
                    return null;
                }
                long amount = entry.getLongValue();
                if (amount <= 0) {
                    continue;
                }
                total += amount;
                if (total > MAX_INPUT_UNITS) {
                    return null;
                }
                for (long i = 0; i < amount; i++) {
                    units.add(itemKey);
                }
            }
        }
        return units;
    }

    @Nullable
    private static Object findMatchingRecipe(ServerLevel level, IPatternDetails pattern, CraftingInput input) {
        if (!hasSingleItemOutput(pattern)) {
            return null;
        }

        for (var holder : recipes(level)) {
            var recipe = holder.value();
            var result = assemble(recipe, input, level);
            if (result == null || result.isEmpty() || !outputMatches(pattern, result)) {
                continue;
            }
            if (recipeMatches(recipe, input, level)) {
                return recipe;
            }
        }
        return null;
    }

    @Nullable
    private static Object findMatchingRecipe(ServerLevel level, CraftingInput input) {
        for (var holder : recipes(level)) {
            var recipe = holder.value();
            if (recipeMatches(recipe, input, level)) {
                return recipe;
            }
        }
        return null;
    }

    private static boolean canApplyRemaining(IItemHandler handler, int gridSize, CraftingInput input,
                                             NonNullList<ItemStack> remaining) {
        if (remaining.size() < input.size()) {
            return false;
        }
        int top = EcReflection.top(input);
        int left = EcReflection.left(input);
        for (int y = 0; y < input.height(); y++) {
            for (int x = 0; x < input.width(); x++) {
                int inputIndex = x + y * input.width();
                int slot = x + left + (y + top) * gridSize;
                var current = handler.getStackInSlot(slot);
                if (current.isEmpty()) {
                    continue;
                }

                var remainder = remaining.get(inputIndex);
                if (remainder.isEmpty()) {
                    continue;
                }

                int afterConsume = current.getCount() - 1;
                if (afterConsume <= 0) {
                    continue;
                }
                if (!ItemStack.isSameItemSameComponents(current, remainder)) {
                    return false;
                }
                if (afterConsume + remainder.getCount() > current.getMaxStackSize()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void applyCraftRemainders(IItemHandlerModifiable handler, int gridSize, CraftingInput input,
                                             NonNullList<ItemStack> remaining) {
        int top = EcReflection.top(input);
        int left = EcReflection.left(input);
        for (int y = 0; y < input.height(); y++) {
            for (int x = 0; x < input.width(); x++) {
                int inputIndex = x + y * input.width();
                int slot = x + left + (y + top) * gridSize;
                var current = handler.getStackInSlot(slot);
                if (!current.isEmpty()) {
                    handler.extractItem(slot, 1, false);
                }

                var remainder = remaining.get(inputIndex);
                if (remainder.isEmpty()) {
                    continue;
                }

                var after = handler.getStackInSlot(slot);
                if (after.isEmpty()) {
                    handler.setStackInSlot(slot, remainder.copy());
                } else if (ItemStack.isSameItemSameComponents(after, remainder)) {
                    var combined = after.copy();
                    combined.grow(remainder.getCount());
                    handler.setStackInSlot(slot, combined);
                }
            }
        }
    }

    private static boolean gridIsEmpty(IItemHandler handler, int slots) {
        for (int i = 0; i < slots; i++) {
            if (!handler.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static List<ItemStack> stacksForPlan(int slots, List<PlannedUnit> planned) {
        var stacks = NonNullList.withSize(slots, ItemStack.EMPTY);
        for (var unit : planned) {
            stacks.set(unit.slot(), unit.stack());
        }
        return stacks;
    }

    private static List<ItemStack> currentStacks(IItemHandler handler, int slots) {
        var stacks = NonNullList.withSize(slots, ItemStack.EMPTY);
        for (int i = 0; i < slots; i++) {
            stacks.set(i, handler.getStackInSlot(i).copy());
        }
        return stacks;
    }

    private static boolean hasSingleItemOutput(IPatternDetails pattern) {
        var outputs = pattern.getOutputs();
        return outputs.size() == 1 && outputs.getFirst().what() instanceof AEItemKey;
    }

    private static boolean outputMatches(IPatternDetails pattern, ItemStack result) {
        var outputs = pattern.getOutputs();
        if (outputs.size() != 1) {
            return false;
        }
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean recipeMatches(Object recipe, CraftingInput input, ServerLevel level) {
        try {
            return recipe instanceof Recipe<?> r && ((Recipe) r).matches(input, level);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    @Nullable
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ItemStack assemble(Object recipe, CraftingInput input, ServerLevel level) {
        try {
            return recipe instanceof Recipe<?> r
                    ? ((Recipe) r).assemble(input, level.registryAccess())
                    : null;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static NonNullList<ItemStack> remainingItems(Object recipe, CraftingInput input) {
        try {
            return recipe instanceof Recipe<?> r
                    ? ((Recipe) r).getRemainingItems(input)
                    : null;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    private static ItemStack resultItem(Object recipe, ServerLevel level) {
        if (!(recipe instanceof Recipe<?> r)) {
            return null;
        }
        try {
            return r.getResultItem(level.registryAccess());
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    private static NonNullList<Ingredient> ingredients(Object recipe) {
        if (!(recipe instanceof Recipe<?> r)) {
            return null;
        }
        try {
            return r.getIngredients();
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

    @Nullable
    private static TableSpec tableSpec(BlockState state) {
        var id = blockId(state);
        if (id.equals(BASIC_TABLE_BLOCK)) {
            return new TableSpec(BASIC_TABLE_BLOCK, 3, 1);
        }
        if (id.equals(ADVANCED_TABLE_BLOCK)) {
            return new TableSpec(ADVANCED_TABLE_BLOCK, 5, 2);
        }
        if (id.equals(ELITE_TABLE_BLOCK)) {
            return new TableSpec(ELITE_TABLE_BLOCK, 7, 3);
        }
        if (id.equals(ULTIMATE_TABLE_BLOCK)) {
            return new TableSpec(ULTIMATE_TABLE_BLOCK, 9, 4);
        }
        return null;
    }

    private static boolean isExtendedCraftingLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    private static ResourceLocation blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock());
    }

    private static ResourceLocation ecId(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private record TableSpec(ResourceLocation blockId, int gridSize, int tier) {
        int slots() {
            return gridSize * gridSize;
        }

        boolean matches(BlockState state) {
            return ExtendedCraftingTableAdapter.blockId(state).equals(blockId);
        }
    }

    private record PlannedUnit(int slot, AEItemKey key) {
        PlannedUnit {
            Objects.requireNonNull(key, "key");
        }

        ItemStack stack() {
            return key.toStack(1);
        }

    }

    private record PlanMatch(List<PlannedUnit> units, Object recipe) {
    }

    private record PlanCacheKey(ResourceKey<Level> dimension,
                                TableSpec spec,
                                String patternKey,
                                List<InputCounterSignature> inputSignature) {
    }

    private record InputCounterSignature(List<InputAmount> entries) {
    }

    private record InputAmount(Object key, long amount) {
    }

    private static final class EcReflection {
        private static final String BASIC_TABLE_CLASS =
                "com.blakebr0.extendedcrafting.tileentity.BasicTableTileEntity";
        private static final String ADVANCED_TABLE_CLASS =
                "com.blakebr0.extendedcrafting.tileentity.AdvancedTableTileEntity";
        private static final String ELITE_TABLE_CLASS =
                "com.blakebr0.extendedcrafting.tileentity.EliteTableTileEntity";
        private static final String ULTIMATE_TABLE_CLASS =
                "com.blakebr0.extendedcrafting.tileentity.UltimateTableTileEntity";
        private static final String BASE_INVENTORY_CLASS =
                "com.blakebr0.cucumber.tileentity.BaseInventoryTileEntity";
        private static final String TABLE_INPUT_CLASS =
                "com.blakebr0.extendedcrafting.api.TableCraftingInput";

        private static volatile boolean lookupDone;
        private static volatile @Nullable Set<Class<?>> tableClasses;
        private static volatile @Nullable Method getInventoryMethod;
        private static volatile @Nullable Method tableInputOfMethod;
        private static volatile @Nullable Method tableInputTopMethod;
        private static volatile @Nullable Method tableInputLeftMethod;
        private static volatile @Nullable Method shapedRecipeWidthMethod;

        static boolean isSupportedTable(Object o) {
            ensureLookup();
            if (tableClasses == null) {
                return false;
            }
            for (var clazz : tableClasses) {
                if (clazz.isInstance(o)) {
                    return true;
                }
            }
            return false;
        }

        @Nullable
        static IItemHandler getHandler(BlockEntity be) {
            ensureLookup();
            if (getInventoryMethod == null) {
                return null;
            }
            try {
                var value = getInventoryMethod.invoke(be);
                return value instanceof IItemHandler h ? h : null;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        @Nullable
        static CraftingInput createTableInput(int size, List<ItemStack> stacks, int tier) {
            ensureLookup();
            if (tableInputOfMethod == null) {
                return null;
            }
            try {
                var value = tableInputOfMethod.invoke(null, size, size, stacks, tier);
                return value instanceof CraftingInput input ? input : null;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        static int top(CraftingInput input) {
            ensureLookup();
            if (tableInputTopMethod == null) {
                return 0;
            }
            try {
                var value = tableInputTopMethod.invoke(input);
                return value instanceof Integer i ? i : 0;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return 0;
            }
        }

        static int left(CraftingInput input) {
            ensureLookup();
            if (tableInputLeftMethod == null) {
                return 0;
            }
            try {
                var value = tableInputLeftMethod.invoke(input);
                return value instanceof Integer i ? i : 0;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return 0;
            }
        }

        @Nullable
        static Integer recipeWidth(Object recipe) {
            ensureLookup();
            if (shapedRecipeWidthMethod == null) {
                return null;
            }
            try {
                if (!shapedRecipeWidthMethod.getDeclaringClass().isInstance(recipe)) {
                    return null;
                }
                var value = shapedRecipeWidthMethod.invoke(recipe);
                return value instanceof Integer i ? i : null;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        private static void ensureLookup() {
            if (lookupDone) {
                return;
            }
            synchronized (EcReflection.class) {
                if (lookupDone) {
                    return;
                }
                try {
                    doLookup();
                } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                } finally {
                    lookupDone = true;
                }
            }
        }

        private static void doLookup() throws ReflectiveOperationException {
            tableClasses = Set.of(
                    Class.forName(BASIC_TABLE_CLASS),
                    Class.forName(ADVANCED_TABLE_CLASS),
                    Class.forName(ELITE_TABLE_CLASS),
                    Class.forName(ULTIMATE_TABLE_CLASS));

            var baseInventoryClass = Class.forName(BASE_INVENTORY_CLASS);
            getInventoryMethod = baseInventoryClass.getMethod("getInventory");

            var tableInputClass = Class.forName(TABLE_INPUT_CLASS);
            tableInputOfMethod = tableInputClass.getMethod("of", int.class, int.class, List.class, int.class);
            tableInputTopMethod = tableInputClass.getMethod("top");
            tableInputLeftMethod = tableInputClass.getMethod("left");

            var shapedRecipeClass = Class.forName("com.blakebr0.extendedcrafting.crafting.recipe.ShapedTableRecipe");
            shapedRecipeWidthMethod = shapedRecipeClass.getMethod("getWidth");
        }
    }
}
