package com.moakiee.ae2lt.packaged.logic.multiblock.occultism;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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
import com.moakiee.ae2lt.packaged.logic.multiblock.InsertionStrategy;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.TargetSlot;

/**
 * Runtime adapter for Occultism Spirit Fire item conversion.
 * Spirit fire has no BlockEntity — items are dropped into the fire block and
 * converted via entityInside on the next tick.
 */
public final class OccultismSpiritFireAdapter implements MultiblockAdapter {

    private static final String MOD_ID = "occultism";
    private static final ResourceLocation SPIRIT_FIRE_BLOCK = occultismId("spirit_fire");
    private static final ResourceLocation SPIRIT_FIRE_RECIPE_TYPE = occultismId("spirit_fire");
    private static final String INPUT_MARKER = "ae2ltpp_spirit_fire_input";
    private static final int MAX_INPUT_AMOUNT = 64;

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean recognizesMain(ServerLevel level, BlockPos pos, @Nullable BlockEntity be) {
        return isOccultismLoaded()
                && blockId(level.getBlockState(pos)).equals(SPIRIT_FIRE_BLOCK);
    }

    @Override
    @Nullable
    public DispatchPlan plan(ServerLevel level, BlockPos mainPos,
                             IPatternDetails pattern, KeyCounter[] inputs,
                             IActionSource source) {
        if (!recognizesMain(level, mainPos, null)) {
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

        var fireBounds = fireAabb(mainPos);
        if (hasPendingInput(level, fireBounds)) {
            return null;
        }

        var spawned = new AtomicBoolean(false);
        var target = new TargetSlot(
                level,
                mainPos,
                null,
                List.of(input.toGenericStack()),
                InsertionStrategy.CUSTOM,
                dropInserter(level, mainPos, input, fireBounds, spawned));

        return new DispatchPlan(List.of(target), null);
    }

    @Override
    public List<GenericStack> extractOutputs(ServerLevel level, BlockPos mainPos,
                                             AllowedOutputFilter filter,
                                             IActionSource source) {
        if (!level.isLoaded(mainPos)) {
            return List.of();
        }
        var state = level.getBlockState(mainPos);
        if (!blockId(state).equals(SPIRIT_FIRE_BLOCK)) {
            return List.of();
        }

        var bounds = outputAabb(mainPos);
        var outputs = new ArrayList<GenericStack>();
        for (var entity : level.getEntitiesOfClass(ItemEntity.class, bounds)) {
            if (!entity.isAlive()) {
                continue;
            }
            if (entity.getPersistentData().getBoolean(INPUT_MARKER)) {
                continue;
            }
            var stack = entity.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            var key = AEItemKey.of(stack);
            if (!filter.matches(key)) {
                continue;
            }
            outputs.add(new GenericStack(key, stack.getCount()));
            entity.discard();
        }
        return List.copyOf(outputs);
    }

    // === Inserter ===

    private static BiFunction<GenericStack, Actionable, Long> dropInserter(
            ServerLevel level,
            BlockPos mainPos,
            PlannedInput input,
            AABB fireBounds,
            AtomicBoolean spawned) {
        return (stack, mode) -> {
            if (!matchesPlannedStack(stack, input)) {
                return 0L;
            }
            if (!level.isLoaded(mainPos)
                    || !blockId(level.getBlockState(mainPos)).equals(SPIRIT_FIRE_BLOCK)) {
                return 0L;
            }
            if (hasPendingInput(level, fireBounds)) {
                return 0L;
            }

            if (mode == Actionable.MODULATE) {
                var position = fireCenter(mainPos);
                var itemStack = input.stack();
                var entity = new ItemEntity(level, position.x, position.y, position.z, itemStack);
                entity.setDeltaMovement(0, 0, 0);
                entity.setPickUpDelay(20);
                entity.getPersistentData().putBoolean(INPUT_MARKER, true);
                if (!level.addFreshEntity(entity)) {
                    return 0L;
                }
                spawned.set(true);
            }
            return input.amount();
        };
    }

    // === Recipe matching ===

    @Nullable
    private static RecipeMatch findRecipeMatch(ServerLevel level, IPatternDetails pattern, PlannedInput input) {
        if (!hasSingleItemOutput(pattern)) {
            return null;
        }

        for (var holder : recipes(level)) {
            var recipe = holder.value();
            var ingredient = getIngredient(recipe);
            if (ingredient == null || !ingredient.test(input.singleStack())) {
                continue;
            }

            var result = resultItem(recipe, level);
            if (result.isEmpty() || !outputMatches(pattern, result, input.amount())) {
                continue;
            }

            return new RecipeMatch(recipe);
        }
        return null;
    }

    private static boolean outputMatches(IPatternDetails pattern, ItemStack result, long inputCount) {
        var outputs = pattern.getOutputs();
        if (outputs.size() != 1) {
            return false;
        }

        var expected = outputs.getFirst();
        if (!(expected.what() instanceof AEItemKey expectedKey) || expected.amount() != inputCount) {
            return false;
        }

        var actual = AEItemKey.of(result);
        var mode = outputMode(pattern, 0);
        return mode == MatchMode.ID_ONLY
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

    // === Input parsing ===

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

    // === Helpers ===

    private static boolean hasSingleItemOutput(IPatternDetails pattern) {
        var outputs = pattern.getOutputs();
        return outputs.size() == 1 && outputs.getFirst().what() instanceof AEItemKey;
    }

    private static boolean matchesPlannedStack(GenericStack stack, PlannedInput input) {
        return stack.amount() == input.amount() && input.key().equals(stack.what());
    }

    private static boolean hasPendingInput(ServerLevel level, AABB bounds) {
        for (var entity : level.getEntitiesOfClass(ItemEntity.class, bounds)) {
            if (entity.isAlive() && entity.getPersistentData().getBoolean(INPUT_MARKER)) {
                return true;
            }
        }
        return false;
    }

    private static Vec3 fireCenter(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    private static AABB fireAabb(BlockPos pos) {
        return new AABB(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
    }

    private static AABB outputAabb(BlockPos pos) {
        return new AABB(pos.getX() - 2, pos.getY(), pos.getZ() - 2,
                pos.getX() + 3, pos.getY() + 3, pos.getZ() + 3);
    }

    @Nullable
    private static Ingredient getIngredient(Object recipe) {
        if (!(recipe instanceof Recipe<?> r)) {
            return null;
        }
        var ingredients = r.getIngredients();
        if (ingredients.isEmpty()) {
            return null;
        }
        var first = ingredients.getFirst();
        return first == Ingredient.EMPTY ? null : first;
    }

    private static ItemStack resultItem(Object recipe, ServerLevel level) {
        if (!(recipe instanceof Recipe<?> r)) {
            return ItemStack.EMPTY;
        }
        try {
            return r.getResultItem(level.registryAccess()).copy();
        } catch (RuntimeException | LinkageError ignored) {
            return ItemStack.EMPTY;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<RecipeHolder<?>> recipes(ServerLevel level) {
        return BuiltInRegistries.RECIPE_TYPE.getOptional(SPIRIT_FIRE_RECIPE_TYPE)
                .map(type -> (List<RecipeHolder<?>>) (List<?>) level.getRecipeManager()
                        .getAllRecipesFor((RecipeType) type))
                .orElse(List.of());
    }

    private static boolean isOccultismLoaded() {
        try {
            return ModList.get().isLoaded(MOD_ID);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static ResourceLocation blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock());
    }

    private static ResourceLocation occultismId(String path) {
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

    private record RecipeMatch(Object recipe) {
    }
}
