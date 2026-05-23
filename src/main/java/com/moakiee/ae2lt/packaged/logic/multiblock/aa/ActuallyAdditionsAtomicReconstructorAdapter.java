package com.moakiee.ae2lt.packaged.logic.multiblock.aa;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.moakiee.ae2lt.logic.AllowedOutputFilter;
import com.moakiee.ae2lt.overload.model.MatchMode;
import com.moakiee.ae2lt.overload.pattern.OverloadedProviderOnlyPatternDetails;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan;
import com.moakiee.ae2lt.packaged.logic.multiblock.DroppedItemDispatch;
import com.moakiee.ae2lt.packaged.logic.multiblock.InsertionStrategy;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.TargetSlot;

/**
 * Runtime adapter for Actually Additions' Atomic Reconstructor conversion lens.
 * The input is dispatched as a dropped item in the first block in front of the
 * reconstructor, matching AA's item-conversion beam AABB and output marker.
 */
public final class ActuallyAdditionsAtomicReconstructorAdapter implements MultiblockAdapter {

    private static final String MOD_ID = "actuallyadditions";
    private static final ResourceLocation ATOMIC_RECONSTRUCTOR_BLOCK = aaId("atomic_reconstructor");
    private static final ResourceLocation LASER_RECIPE_TYPE = aaId("laser");
    private static final String INPUT_MARKER = "ae2ltpp_atomic_reconstructor_input";
    private static final String AA_CONVERTED_MARKER = "aa_cnv";
    private static final int DEFAULT_LENS_DISTANCE = 10;
    private static final int BASE_ENERGY_USE = 1000;
    private static final int MAX_INPUT_AMOUNT = 64;

    @Override
    public int priority() {
        return 90;
    }

    @Override
    public boolean recognizesMain(ServerLevel level, BlockPos pos, BlockEntity be) {
        return isActuallyAdditionsLoaded()
                && blockId(be.getBlockState()).equals(ATOMIC_RECONSTRUCTOR_BLOCK)
                && AaReconstructorReflection.isAtomicReconstructor(be);
    }

    @Override
    @Nullable
    public DispatchPlan plan(ServerLevel level, BlockPos mainPos,
                             IPatternDetails pattern, KeyCounter[] inputs,
                             IActionSource source) {
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)) {
            return null;
        }

        var facing = facingOf(be.getBlockState());
        if (facing == null || !AaReconstructorReflection.hasDefaultConversionLens(be)) {
            return null;
        }

        var input = singleInput(inputs);
        if (input == null) {
            return null;
        }

        var match = findRecipeMatch(level, pattern, input);
        if (match == null) {
            return null;
        }

        var inputDropBounds = DroppedItemDispatch.dropAabb(mainPos, facing);
        if (!hasClearInputDropBlock(level, mainPos, facing)
                || !hasNoUnconvertedItems(level, inputDropBounds)
                || !hasEnergy(be, match, input)) {
            return null;
        }

        var spawned = new AtomicBoolean(false);
        var target = new TargetSlot(
                level,
                mainPos,
                null,
                List.of(input.toGenericStack()),
                InsertionStrategy.CUSTOM,
                dropInserter(level, mainPos, facing, input, match, inputDropBounds, spawned));

        return new DispatchPlan(
                List.of(target),
                () -> {
                    if (!spawned.get()) {
                        return;
                    }
                    var current = level.getBlockEntity(mainPos);
                    if (current != null && recognizesMain(level, mainPos, current)) {
                        AaReconstructorReflection.invokeReconstructor(current);
                    }
                });
    }

    @Override
    public List<GenericStack> extractOutputs(ServerLevel level, BlockPos mainPos,
                                             AllowedOutputFilter filter,
                                             IActionSource source) {
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)) {
            return List.of();
        }

        var facing = facingOf(be.getBlockState());
        if (facing == null) {
            return List.of();
        }

        return DroppedItemDispatch.collectOutputs(
                level,
                beamBounds(mainPos, facing),
                filter,
                ActuallyAdditionsAtomicReconstructorAdapter::isAaConvertedEntity);
    }

    private static BiFunction<GenericStack, Actionable, Long> dropInserter(
            ServerLevel level,
            BlockPos mainPos,
            Direction facing,
            PlannedInput input,
            RecipeMatch match,
            AABB inputDropBounds,
            AtomicBoolean spawned) {
        return (stack, mode) -> {
            if (!matchesPlannedStack(stack, input)) {
                return 0L;
            }

            var be = level.getBlockEntity(mainPos);
            if (be == null
                    || !blockId(be.getBlockState()).equals(ATOMIC_RECONSTRUCTOR_BLOCK)
                    || !AaReconstructorReflection.isAtomicReconstructor(be)
                    || !AaReconstructorReflection.hasDefaultConversionLens(be)
                    || !hasClearInputDropBlock(level, mainPos, facing)
                    || !hasNoUnconvertedItems(level, inputDropBounds)
                    || !hasEnergy(be, match, input)) {
                return 0L;
            }

            if (mode == Actionable.MODULATE) {
                var position = DroppedItemDispatch.dropPosition(mainPos, facing);
                if (!DroppedItemDispatch.spawnInput(level, position, input.stack(), INPUT_MARKER)) {
                    return 0L;
                }
                spawned.set(true);
            }
            return input.amount();
        };
    }

    @Nullable
    private static RecipeMatch findRecipeMatch(ServerLevel level, IPatternDetails pattern, PlannedInput input) {
        if (!hasSingleItemOutput(pattern)) {
            return null;
        }

        for (var holder : recipes(level)) {
            var recipe = holder.value();
            var ingredient = AaReconstructorReflection.getInput(recipe);
            int energy = AaReconstructorReflection.getRecipeEnergy(recipe);
            if (ingredient == null || energy <= 0 || !ingredient.test(input.singleStack())) {
                continue;
            }

            var result = resultItem(recipe, level);
            if (result.isEmpty() || !outputMatches(pattern, result, input.amount())) {
                continue;
            }

            return new RecipeMatch(recipe, energy);
        }
        return null;
    }

    private static boolean outputMatches(IPatternDetails pattern, ItemStack result, long convertedCount) {
        var outputs = pattern.getOutputs();
        if (outputs.size() != 1) {
            return false;
        }

        var expected = outputs.getFirst();
        if (!(expected.what() instanceof AEItemKey expectedKey) || expected.amount() != convertedCount) {
            return false;
        }

        var actual = AEItemKey.of(result);
        return AtomicReconstructorOutputMatcher.matches(
                expectedKey,
                actual,
                outputMode(pattern, 0),
                (left, right) -> left.equals(right),
                AEItemKey::dropSecondary);
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

    @Nullable
    private static PlannedInput singleInput(KeyCounter[] inputs) {
        AEItemKey key = null;
        long amount = 0;

        for (var counter : inputs) {
            for (var entry : counter) {
                if (!(entry.getKey() instanceof AEItemKey itemKey)) {
                    return null;
                }
                long entryAmount = entry.getLongValue();
                if (entryAmount <= 0) {
                    continue;
                }
                if (key == null) {
                    key = itemKey;
                } else if (!key.equals(itemKey)) {
                    return null;
                }
                amount += entryAmount;
                if (amount > MAX_INPUT_AMOUNT) {
                    return null;
                }
            }
        }

        return key != null && amount > 0 ? new PlannedInput(key, amount) : null;
    }

    private static boolean matchesPlannedStack(GenericStack stack, PlannedInput input) {
        return stack.amount() == input.amount() && input.key().equals(stack.what());
    }

    private static boolean hasEnergy(BlockEntity be, RecipeMatch match, PlannedInput input) {
        return AaReconstructorReflection.getEnergy(be) >= BASE_ENERGY_USE + match.energy() * input.amount();
    }

    private static boolean hasClearInputDropBlock(ServerLevel level, BlockPos mainPos, Direction facing) {
        var pos = DroppedItemDispatch.dropBlock(mainPos, facing);
        return level.isLoaded(pos) && level.getBlockState(pos).isAir();
    }

    private static boolean hasNoUnconvertedItems(ServerLevel level, AABB bounds) {
        for (var entity : level.getEntitiesOfClass(ItemEntity.class, bounds)) {
            if (entity.isAlive()
                    && !entity.getItem().isEmpty()
                    && !entity.getPersistentData().getBoolean(AA_CONVERTED_MARKER)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAaConvertedEntity(ItemEntity entity) {
        return entity.getPersistentData().getBoolean(AA_CONVERTED_MARKER);
    }

    private static AABB beamBounds(BlockPos mainPos, Direction facing) {
        return DroppedItemDispatch.beamAabb(
                mainPos,
                mainPos.relative(facing, DEFAULT_LENS_DISTANCE),
                facing);
    }

    @Nullable
    private static Direction facingOf(BlockState state) {
        return state.hasProperty(BlockStateProperties.FACING)
                ? state.getValue(BlockStateProperties.FACING)
                : null;
    }

    private static ItemStack resultItem(Object recipe, ServerLevel level) {
        if (!(recipe instanceof Recipe<?> typedRecipe)) {
            return ItemStack.EMPTY;
        }
        try {
            return typedRecipe.getResultItem(level.registryAccess()).copy();
        } catch (RuntimeException | LinkageError ignored) {
            return ItemStack.EMPTY;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<RecipeHolder<?>> recipes(ServerLevel level) {
        return BuiltInRegistries.RECIPE_TYPE.getOptional(LASER_RECIPE_TYPE)
                .map(type -> (List<RecipeHolder<?>>) (List<?>) level.getRecipeManager()
                        .getAllRecipesFor((RecipeType) type))
                .orElse(List.of());
    }

    private static boolean isActuallyAdditionsLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    private static ResourceLocation blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock());
    }

    private static ResourceLocation aaId(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private record PlannedInput(AEItemKey key, long amount) {
        PlannedInput {
            Objects.requireNonNull(key, "key");
        }

        ItemStack singleStack() {
            return key.toStack(1);
        }

        ItemStack stack() {
            return key.toStack((int) amount);
        }

        GenericStack toGenericStack() {
            return new GenericStack(key, amount);
        }
    }

    private record RecipeMatch(Object recipe, int energy) {
    }

    private static final class AaReconstructorReflection {
        private static final String RECONSTRUCTOR_CLASS =
                "de.ellpeck.actuallyadditions.mod.tile.TileEntityAtomicReconstructor";
        private static final String LENS_CONVERSION_CLASS =
                "de.ellpeck.actuallyadditions.api.lens.LensConversion";
        private static final String LASER_RECIPE_CLASS =
                "de.ellpeck.actuallyadditions.mod.crafting.LaserRecipe";
        private static final String API_CLASS =
                "de.ellpeck.actuallyadditions.api.ActuallyAdditionsAPI";
        private static final String METHOD_HANDLER_CLASS =
                "de.ellpeck.actuallyadditions.api.internal.IMethodHandler";
        private static final String ATOMIC_RECONSTRUCTOR_INTERFACE =
                "de.ellpeck.actuallyadditions.api.internal.IAtomicReconstructor";

        private static volatile boolean lookupDone;
        private static volatile @Nullable Class<?> reconstructorClass;
        private static volatile @Nullable Class<?> lensConversionClass;
        private static volatile @Nullable Class<?> laserRecipeClass;
        private static volatile @Nullable Field methodHandlerField;
        private static volatile @Nullable Method getLensMethod;
        private static volatile @Nullable Method getEnergyMethod;
        private static volatile @Nullable Method invokeReconstructorMethod;
        private static volatile @Nullable Method getInputMethod;
        private static volatile @Nullable Method getRecipeEnergyMethod;

        static boolean isAtomicReconstructor(Object o) {
            ensureLookup();
            return reconstructorClass != null && reconstructorClass.isInstance(o);
        }

        static boolean hasDefaultConversionLens(BlockEntity be) {
            ensureLookup();
            if (getLensMethod == null || lensConversionClass == null || !isAtomicReconstructor(be)) {
                return false;
            }
            try {
                var lens = getLensMethod.invoke(be);
                return lensConversionClass.isInstance(lens);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return false;
            }
        }

        static int getEnergy(BlockEntity be) {
            ensureLookup();
            if (getEnergyMethod == null || !isAtomicReconstructor(be)) {
                return 0;
            }
            try {
                var value = getEnergyMethod.invoke(be);
                return value instanceof Number number ? number.intValue() : 0;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return 0;
            }
        }

        static boolean invokeReconstructor(BlockEntity be) {
            ensureLookup();
            if (methodHandlerField == null || invokeReconstructorMethod == null || !isAtomicReconstructor(be)) {
                return false;
            }
            try {
                var handler = methodHandlerField.get(null);
                if (handler == null) {
                    return false;
                }
                var value = invokeReconstructorMethod.invoke(handler, be);
                return value instanceof Boolean b && b;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return false;
            }
        }

        @Nullable
        static Ingredient getInput(Object recipe) {
            ensureLookup();
            if (getInputMethod == null || !isLaserRecipe(recipe)) {
                return null;
            }
            try {
                var value = getInputMethod.invoke(recipe);
                return value instanceof Ingredient ingredient ? ingredient : null;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        static int getRecipeEnergy(Object recipe) {
            ensureLookup();
            if (getRecipeEnergyMethod == null || !isLaserRecipe(recipe)) {
                return 0;
            }
            try {
                var value = getRecipeEnergyMethod.invoke(recipe);
                return value instanceof Number number ? number.intValue() : 0;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return 0;
            }
        }

        private static boolean isLaserRecipe(Object recipe) {
            return laserRecipeClass != null && laserRecipeClass.isInstance(recipe);
        }

        private static void ensureLookup() {
            if (lookupDone) {
                return;
            }
            synchronized (AaReconstructorReflection.class) {
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
            reconstructorClass = Class.forName(RECONSTRUCTOR_CLASS);
            lensConversionClass = Class.forName(LENS_CONVERSION_CLASS);
            laserRecipeClass = Class.forName(LASER_RECIPE_CLASS);
            var apiClass = Class.forName(API_CLASS);
            var methodHandlerClass = Class.forName(METHOD_HANDLER_CLASS);
            var atomicReconstructorInterface = Class.forName(ATOMIC_RECONSTRUCTOR_INTERFACE);

            methodHandlerField = apiClass.getField("methodHandler");
            getLensMethod = atomicReconstructorInterface.getMethod("getLens");
            getEnergyMethod = atomicReconstructorInterface.getMethod("getEnergy");
            invokeReconstructorMethod = methodHandlerClass.getMethod("invokeReconstructor", atomicReconstructorInterface);
            getInputMethod = laserRecipeClass.getMethod("getInput");
            getRecipeEnergyMethod = laserRecipeClass.getMethod("getEnergy");
        }
    }
}
