package com.moakiee.ae2lt.packaged.logic.multiblock.occultism;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.IItemHandler;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.moakiee.ae2lt.logic.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan;
import com.moakiee.ae2lt.packaged.logic.multiblock.InsertionStrategy;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.TargetSlot;
import com.moakiee.ae2lt.packaged.logic.multiblock.VirtualCraftingAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.VirtualCraftingResult;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingResult;
import com.moakiee.ae2lt.overload.model.MatchMode;
import com.moakiee.ae2lt.overload.pattern.OverloadedProviderOnlyPatternDetails;

/**
 * Runtime adapter for Occultism rituals.
 *
 * <p>Occultism is intentionally optional, so all Occultism and Modonomicon access
 * stays reflection-based.
 *
 * <p>Splits into two binding modes per the equivalence principle:
 * <ul>
 *   <li><b>{@link BindingMode#VIRTUAL}</b> &mdash; "flame of automation" producing
 *       rituals (summon variants). These return an item-form flame directly with
 *       no waiting on the live ritual progress; the in-world ritual would otherwise
 *       spawn entities + waste ticks. Pentacle structure is still validated.</li>
 *   <li><b>{@link BindingMode#REAL}</b> &mdash; every other ritual. Sacrificial bowls
 *       must be placed in the world, the golden bowl ticks the ritual normally, and
 *       results are pulled from the inverted output bowl by extractOutputs.</li>
 * </ul>
 */
public final class OccultismRitualAdapter implements MultiblockAdapter, VirtualCraftingAdapter {

    private static final String MOD_ID = "occultism";
    private static final ResourceLocation RITUAL_RECIPE_TYPE = occultismId("ritual");
    private static final ResourceLocation FLAME_OF_AUTOMATION = occultismId("flame_of_automation");
    private static final int MAX_INPUT_UNITS = 128;

    private static final Set<ResourceLocation> FLAME_PRODUCING_RITUAL_TYPES = Set.of(
            occultismId("summon"),
            occultismId("summon_tamed"),
            occultismId("summon_with_chance_of_chicken"),
            occultismId("summon_with_chance_of_chicken_tamed"),
            occultismId("summon_spirit_with_job"),
            occultismId("summon_wild"),
            occultismId("familiar"),
            occultismId("resurrect_familiar"),
            occultismId("execute_command"));

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean recognizesMain(ServerLevel level, BlockPos pos, BlockEntity be) {
        return be != null && isOccultismLoaded() && OccultismReflection.isGoldenBowl(be);
    }

    @Override
    public ResourceLocation requiredAdapterId(ServerLevel level, BlockPos pos) {
        return com.moakiee.ae2lt.packaged.item.AdapterIds.OCCULTISM_RITUAL;
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
        var candidate = findCandidateRecipe(level, mainPos, pattern);
        if (candidate == null) {
            return null;
        }
        return new BindingResult(candidate,
                candidate.isFlame() ? BindingMode.VIRTUAL : BindingMode.REAL);
    }

    @Override
    public boolean canDispatch(ServerLevel level, BlockPos mainPos, Object handle) {
        if (!(handle instanceof OccultismBindHandle bind)) {
            return false;
        }
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be) || !OccultismReflection.isIdle(be)) {
            return false;
        }
        var goldenHandler = OccultismReflection.itemHandler(be);
        if (goldenHandler == null || !slotEmpty(goldenHandler)) {
            return false;
        }
        if (!OccultismReflection.hasValidPentacle(bind.recipe(), level, mainPos)) {
            return false;
        }
        if (bind.isFlame() && !hasEmptyInvertedOutputBowl(level, mainPos)) {
            return false;
        }
        return true;
    }

    @Override
    @Nullable
    public DispatchPlan planWithBinding(ServerLevel level, BlockPos mainPos,
                                        IPatternDetails pattern, KeyCounter[] inputs,
                                        Object handle, IActionSource source) {
        if (!(handle instanceof OccultismBindHandle bind) || bind.isFlame()) {
            return null;
        }

        var units = expandInputUnits(inputs);
        if (units == null || units.isEmpty()) {
            return null;
        }

        var match = matchInputsToRecipe(units, bind.recipe());
        if (match == null) {
            return null;
        }

        var bowls = findEmptySacrificialBowls(level, mainPos, bind.recipe());
        if (bowls == null || bowls.size() < match.ingredients().size()) {
            return null;
        }

        var targets = new ArrayList<TargetSlot>(match.ingredients().size() + 1);
        for (int i = 0; i < match.ingredients().size(); i++) {
            var unit = match.ingredients().get(i);
            var bowl = bowls.get(i);
            targets.add(new TargetSlot(
                    level,
                    bowl.pos(),
                    null,
                    List.of(unit.toGenericStack()),
                    InsertionStrategy.CUSTOM,
                    sacrificialBowlInserter(level, bowl.pos(), unit)));
        }

        targets.add(new TargetSlot(
                level,
                mainPos,
                null,
                List.of(match.activation().toGenericStack()),
                InsertionStrategy.CUSTOM,
                goldenBowlInserter(level, mainPos, bind.recipe(), match.activation())));

        return new DispatchPlan(List.copyOf(targets), null);
    }

    @Override
    @Nullable
    public VirtualCraftingResult planVirtualWithBinding(ServerLevel level, BlockPos mainPos,
                                                        IPatternDetails pattern, KeyCounter[] inputs,
                                                        Object handle, IActionSource source) {
        if (!(handle instanceof OccultismBindHandle bind) || !bind.isFlame()) {
            return null;
        }
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)) {
            return null;
        }
        if (!OccultismReflection.hasValidPentacle(bind.recipe(), level, mainPos)) {
            return null;
        }

        // Validate inputs still match the bound recipe (ID_ONLY pattern may be looser).
        var units = expandInputUnits(inputs);
        if (units == null || units.isEmpty()) {
            return null;
        }
        if (matchInputsToRecipe(units, bind.recipe()) == null) {
            return null;
        }

        // Output comes from pattern.getOutputs() directly: the flame is an item-form
        // result whose amount/key the pattern already declared. The in-world summon
        // would produce a flame_of_automation entity that AA's hopper grabs, but the
        // virtual lane short-circuits that entire entity lifecycle.
        var outputs = pattern.getOutputs();
        if (outputs.size() != 1 || !(outputs.getFirst().what() instanceof AEItemKey)) {
            return null;
        }
        return new VirtualCraftingResult(List.of(outputs.getFirst()));
    }

    @Override
    public List<GenericStack> extractOutputs(ServerLevel level, BlockPos mainPos,
                                             AllowedOutputFilter filter,
                                             IActionSource source) {
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be) || !OccultismReflection.isIdle(be)) {
            return List.of();
        }

        for (int y = 1; y <= 3; y++) {
            var bowlPos = mainPos.above(y);
            if (!level.isLoaded(bowlPos)) {
                continue;
            }

            var bowlBe = level.getBlockEntity(bowlPos);
            if (bowlBe == null
                    || !OccultismReflection.isSacrificialBowl(bowlBe)
                    || !isUpsideDownBowl(bowlBe.getBlockState())) {
                continue;
            }

            var handler = OccultismReflection.itemHandler(bowlBe);
            if (handler == null || handler.getSlots() <= 0) {
                continue;
            }

            var stack = handler.getStackInSlot(0);
            if (stack.isEmpty()) {
                continue;
            }

            var key = AEItemKey.of(stack);
            if (!filter.matches(key)) {
                continue;
            }

            var extracted = handler.extractItem(0, stack.getCount(), false);
            if (extracted.isEmpty()) {
                continue;
            }
            return List.of(new GenericStack(AEItemKey.of(extracted), extracted.getCount()));
        }

        return List.of();
    }

    /**
     * Recipe search executed during {@link #bind}. Considers only pattern-level
     * metadata (outputs + automation flame heuristic) and pentacle validity, so
     * it can be cached and replayed without the runtime KeyCounter inputs.
     */
    @Nullable
    private static OccultismBindHandle findCandidateRecipe(ServerLevel level, BlockPos mainPos,
                                                            IPatternDetails pattern) {
        var expectsAutomationFlame = expectsAutomationFlame(pattern);
        for (var holder : recipes(level)) {
            var recipe = holder.value();
            if (!OccultismReflection.hasValidPentacle(recipe, level, mainPos)) {
                continue;
            }

            var ritualType = OccultismReflection.getRitualType(recipe);
            if (ritualType == null) {
                continue;
            }

            boolean flameProducing = FLAME_PRODUCING_RITUAL_TYPES.contains(ritualType);
            if (expectsAutomationFlame != flameProducing) {
                continue;
            }
            if (!expectsAutomationFlame && !recipeResultMatches(recipe, level, pattern)) {
                continue;
            }
            if (OccultismReflection.getActivationItem(recipe) == null) {
                continue;
            }
            if (OccultismReflection.getIngredients(recipe) == null) {
                continue;
            }
            return new OccultismBindHandle(recipe, expectsAutomationFlame);
        }
        return null;
    }

    /**
     * Per-push input matching: pairs runtime inputs with the bound recipe's
     * activation + ingredient ingredients. Cheap because the recipe is already
     * locked in.
     */
    @Nullable
    private static InputMatch matchInputsToRecipe(List<PlannedUnit> units, Object recipe) {
        var activation = OccultismReflection.getActivationItem(recipe);
        var ingredients = OccultismReflection.getIngredients(recipe);
        if (activation == null || ingredients == null) {
            return null;
        }

        var ingredientMatchers = new ArrayList<Predicate<PlannedUnit>>(ingredients.size());
        for (var ingredient : ingredients) {
            ingredientMatchers.add(unit -> ingredient.test(unit.stack()));
        }
        var matched = OccultismRitualInputMatcher.match(
                units,
                unit -> activation.test(unit.stack()),
                List.copyOf(ingredientMatchers));
        if (matched == null) {
            return null;
        }
        return new InputMatch(matched.activation(), matched.ingredients());
    }

    @Nullable
    private static List<BowlSlot> findEmptySacrificialBowls(ServerLevel level, BlockPos mainPos, Object recipe) {
        var bowls = OccultismReflection.getSacrificialBowls(recipe, level, mainPos);
        if (bowls == null) {
            return null;
        }

        var result = new ArrayList<BowlSlot>();
        for (var bowl : bowls) {
            if (!(bowl instanceof BlockEntity bowlBe)) {
                return null;
            }
            if (bowlBe.getBlockPos().equals(mainPos) || isUpsideDownBowl(bowlBe.getBlockState())) {
                continue;
            }

            var handler = OccultismReflection.itemHandler(bowlBe);
            if (handler == null || handler.getSlots() <= 0) {
                return null;
            }
            if (!handler.getStackInSlot(0).isEmpty()) {
                return null;
            }
            result.add(new BowlSlot(bowlBe.getBlockPos()));
        }
        return result;
    }

    private static boolean hasEmptyInvertedOutputBowl(ServerLevel level, BlockPos mainPos) {
        for (int y = 1; y <= 3; y++) {
            var bowlPos = mainPos.above(y);
            if (!level.isLoaded(bowlPos)) {
                continue;
            }
            var be = level.getBlockEntity(bowlPos);
            if (be == null
                    || !OccultismReflection.isSacrificialBowl(be)
                    || !isUpsideDownBowl(be.getBlockState())) {
                continue;
            }

            var handler = OccultismReflection.itemHandler(be);
            if (handler != null && handler.getSlots() > 0 && handler.getStackInSlot(0).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static BiFunction<GenericStack, Actionable, Long> sacrificialBowlInserter(
            ServerLevel level, BlockPos pos, PlannedUnit unit) {
        return (stack, mode) -> {
            if (!matchesPlannedStack(stack, unit)) {
                return 0L;
            }

            var be = level.getBlockEntity(pos);
            if (be == null
                    || !OccultismReflection.isSacrificialBowl(be)
                    || isUpsideDownBowl(be.getBlockState())) {
                return 0L;
            }

            var handler = OccultismReflection.itemHandler(be);
            if (handler == null || handler.getSlots() <= 0 || !handler.getStackInSlot(0).isEmpty()) {
                return 0L;
            }

            if (mode == Actionable.MODULATE) {
                var remainder = handler.insertItem(0, unit.stack(), false);
                return 1L - remainder.getCount();
            }
            return 1L;
        };
    }

    private static BiFunction<GenericStack, Actionable, Long> goldenBowlInserter(
            ServerLevel level, BlockPos pos, Object recipe, PlannedUnit unit) {
        return (stack, mode) -> {
            if (!matchesPlannedStack(stack, unit)) {
                return 0L;
            }

            var be = level.getBlockEntity(pos);
            if (be == null
                    || !OccultismReflection.isGoldenBowl(be)
                    || !OccultismReflection.isIdle(be)
                    || !OccultismReflection.hasValidPentacle(recipe, level, pos)) {
                return 0L;
            }

            var handler = OccultismReflection.itemHandler(be);
            if (handler == null || handler.getSlots() <= 0 || !handler.getStackInSlot(0).isEmpty()) {
                return 0L;
            }

            if (mode == Actionable.MODULATE) {
                var remainder = handler.insertItem(0, unit.stack(), false);
                return 1L - remainder.getCount();
            }
            return 1L;
        };
    }

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
                if (amount <= 0) {
                    continue;
                }
                total += amount;
                if (total > MAX_INPUT_UNITS) {
                    return null;
                }
                for (long i = 0; i < amount; i++) {
                    units.add(new PlannedUnit(itemKey));
                }
            }
        }
        return units;
    }

    private static boolean recipeResultMatches(Object recipe, ServerLevel level, IPatternDetails pattern) {
        var result = OccultismReflection.getResultItem(recipe, level.registryAccess());
        return result != null && !result.isEmpty() && outputMatches(pattern, result);
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

    private static boolean expectsAutomationFlame(IPatternDetails pattern) {
        var outputs = pattern.getOutputs();
        if (outputs.size() != 1 || outputs.getFirst().amount() != 1) {
            return false;
        }
        return outputs.getFirst().what() instanceof AEItemKey key
                && FLAME_OF_AUTOMATION.equals(key.getId());
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

    private static boolean hasSingleItemOutput(IPatternDetails pattern) {
        var outputs = pattern.getOutputs();
        return outputs.size() == 1 && outputs.getFirst().what() instanceof AEItemKey;
    }

    private static boolean matchesPlannedStack(GenericStack stack, PlannedUnit unit) {
        return stack.amount() == 1 && unit.key().equals(stack.what());
    }

    private static boolean slotEmpty(IItemHandler handler) {
        return handler.getSlots() > 0 && handler.getStackInSlot(0).isEmpty();
    }

    private static boolean isUpsideDownBowl(BlockState state) {
        return state.hasProperty(BlockStateProperties.FACING)
                && state.getValue(BlockStateProperties.FACING) == Direction.DOWN;
    }

    private static List<RecipeHolder<?>> recipes(ServerLevel level) {
        return BuiltInRegistries.RECIPE_TYPE.getOptional(RITUAL_RECIPE_TYPE)
                .map(type -> recipesForType(level, type))
                .orElse(List.of());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<RecipeHolder<?>> recipesForType(ServerLevel level, RecipeType<?> type) {
        return (List<RecipeHolder<?>>) (List<?>) level.getRecipeManager()
                .getAllRecipesFor((RecipeType) type);
    }

    private static boolean isOccultismLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    private static ResourceLocation occultismId(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

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

    /**
     * Opaque handle returned from {@link #bind}. Caches the matched recipe and
     * whether it's a flame-of-automation rite so {@code canDispatch} /
     * {@code planWithBinding} / {@code planVirtualWithBinding} can skip the
     * recipe-table scan entirely.
     */
    private record OccultismBindHandle(Object recipe, boolean isFlame) {
    }

    /** Per-push input-to-ingredient match result. */
    private record InputMatch(PlannedUnit activation, List<PlannedUnit> ingredients) {
    }

    private record BowlSlot(BlockPos pos) {
    }

    private static final class OccultismReflection {
        private static final String GOLDEN_BOWL_CLASS =
                "com.klikli_dev.occultism.common.blockentity.GoldenSacrificialBowlBlockEntity";
        private static final String SACRIFICIAL_BOWL_CLASS =
                "com.klikli_dev.occultism.common.blockentity.SacrificialBowlBlockEntity";
        private static final String RITUAL_RECIPE_CLASS =
                "com.klikli_dev.occultism.crafting.recipe.RitualRecipe";
        private static final String RITUAL_CLASS =
                "com.klikli_dev.occultism.common.ritual.Ritual";

        private static volatile boolean lookupDone;
        private static volatile @Nullable Class<?> goldenBowlClass;
        private static volatile @Nullable Class<?> sacrificialBowlClass;
        private static volatile @Nullable Method getCurrentRitualRecipeMethod;
        private static volatile @Nullable Field itemStackHandlerField;
        private static volatile @Nullable Method getActivationItemMethod;
        private static volatile @Nullable Method getIngredientsMethod;
        private static volatile @Nullable Method getResultItemMethod;
        private static volatile @Nullable Method getRitualTypeMethod;
        private static volatile @Nullable Method getPentacleMethod;
        private static volatile @Nullable Method getRitualMethod;
        private static volatile @Nullable Method getSacrificialBowlsMethod;

        static boolean isGoldenBowl(Object object) {
            ensureLookup();
            return goldenBowlClass != null && goldenBowlClass.isInstance(object);
        }

        static boolean isSacrificialBowl(Object object) {
            ensureLookup();
            return sacrificialBowlClass != null && sacrificialBowlClass.isInstance(object);
        }

        static boolean isIdle(Object goldenBowl) {
            ensureLookup();
            if (getCurrentRitualRecipeMethod == null) {
                return false;
            }
            try {
                return getCurrentRitualRecipeMethod.invoke(goldenBowl) == null;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return false;
            }
        }

        @Nullable
        static IItemHandler itemHandler(Object bowl) {
            ensureLookup();
            if (itemStackHandlerField == null) {
                return null;
            }
            try {
                var value = itemStackHandlerField.get(bowl);
                return value instanceof IItemHandler handler ? handler : null;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        @Nullable
        static Ingredient getActivationItem(Object recipe) {
            ensureLookup();
            if (getActivationItemMethod == null) {
                return null;
            }
            try {
                var value = getActivationItemMethod.invoke(recipe);
                return value instanceof Ingredient ingredient ? ingredient : null;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        @Nullable
        static List<Ingredient> getIngredients(Object recipe) {
            ensureLookup();
            if (getIngredientsMethod == null) {
                return null;
            }
            try {
                var value = getIngredientsMethod.invoke(recipe);
                if (!(value instanceof List<?> list)) {
                    return null;
                }

                var result = new ArrayList<Ingredient>(list.size());
                for (var item : list) {
                    if (!(item instanceof Ingredient ingredient)) {
                        return null;
                    }
                    result.add(ingredient);
                }
                return List.copyOf(result);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        @Nullable
        static ItemStack getResultItem(Object recipe, HolderLookup.Provider registries) {
            ensureLookup();
            if (getResultItemMethod == null) {
                return null;
            }
            try {
                var value = getResultItemMethod.invoke(recipe, registries);
                return value instanceof ItemStack stack ? stack.copy() : null;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        @Nullable
        static ResourceLocation getRitualType(Object recipe) {
            ensureLookup();
            if (getRitualTypeMethod == null) {
                return null;
            }
            try {
                var value = getRitualTypeMethod.invoke(recipe);
                return value instanceof ResourceLocation id ? id : null;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        static boolean hasValidPentacle(Object recipe, Level level, BlockPos pos) {
            var pentacle = getPentacle(recipe);
            if (pentacle == null) {
                return false;
            }
            try {
                var validate = pentacle.getClass().getMethod("validate", Level.class, BlockPos.class);
                return validate.invoke(pentacle, level, pos) != null;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return false;
            }
        }

        @Nullable
        static List<?> getSacrificialBowls(Object recipe, Level level, BlockPos pos) {
            ensureLookup();
            if (getRitualMethod == null || getSacrificialBowlsMethod == null) {
                return null;
            }
            try {
                var ritual = getRitualMethod.invoke(recipe);
                var value = getSacrificialBowlsMethod.invoke(ritual, level, pos);
                return value instanceof List<?> list ? list : null;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        @Nullable
        private static Object getPentacle(Object recipe) {
            ensureLookup();
            if (getPentacleMethod == null) {
                return null;
            }
            try {
                return getPentacleMethod.invoke(recipe);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        private static void ensureLookup() {
            if (lookupDone) {
                return;
            }
            synchronized (OccultismReflection.class) {
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
            goldenBowlClass = Class.forName(GOLDEN_BOWL_CLASS);
            sacrificialBowlClass = Class.forName(SACRIFICIAL_BOWL_CLASS);
            var ritualRecipeClass = Class.forName(RITUAL_RECIPE_CLASS);
            var ritualClass = Class.forName(RITUAL_CLASS);

            getCurrentRitualRecipeMethod = goldenBowlClass.getMethod("getCurrentRitualRecipe");
            itemStackHandlerField = sacrificialBowlClass.getField("itemStackHandler");
            getActivationItemMethod = ritualRecipeClass.getMethod("getActivationItem");
            getIngredientsMethod = ritualRecipeClass.getMethod("getIngredients");
            getResultItemMethod = ritualRecipeClass.getMethod("getResultItem", HolderLookup.Provider.class);
            getRitualTypeMethod = ritualRecipeClass.getMethod("getRitualType");
            getPentacleMethod = ritualRecipeClass.getMethod("getPentacle");
            getRitualMethod = ritualRecipeClass.getMethod("getRitual");
            getSacrificialBowlsMethod = ritualClass.getMethod("getSacrificialBowls", Level.class, BlockPos.class);
        }
    }
}
