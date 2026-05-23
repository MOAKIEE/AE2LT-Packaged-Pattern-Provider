package com.moakiee.ae2lt.packaged.logic.multiblock.malum;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.ModList;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.moakiee.ae2lt.logic.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan;
import com.moakiee.ae2lt.packaged.logic.multiblock.DroppedItemDispatch;
import com.moakiee.ae2lt.packaged.logic.multiblock.InsertionStrategy;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.TargetSlot;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingResult;

/**
 * Runtime adapter for Malum's Spirit Altar infusion.
 * Extra inputs are placed on Malum item access points captured by Malum's own
 * AltarCraftingHelper, preserving the altar's range and ordering semantics.
 */
public final class MalumSpiritInfusionAdapter implements MultiblockAdapter {

    private static final String MOD_ID = "malum";
    private static final ResourceLocation ALTAR_BLOCK = malumId("spirit_altar");
    private static final ResourceLocation RECIPE_TYPE_ID = malumId("spirit_infusion");
    private static final int MAX_INPUT_AMOUNT = 128;

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean recognizesMain(ServerLevel level, BlockPos pos, BlockEntity be) {
        return be != null
                && isMalumLoaded()
                && blockId(be.getBlockState()).equals(ALTAR_BLOCK)
                && MalumReflection.isSpiritAltar(be);
    }

    @Override
    public ResourceLocation requiredAdapterId(ServerLevel level, BlockPos pos) {
        return com.moakiee.ae2lt.packaged.item.AdapterIds.MALUM_SPIRIT_INFUSION;
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
        var pedestals = loadedPedestals(level, mainPos);
        var recipes = findCandidateRecipes(level, pedestals.size());
        return recipes.isEmpty()
                ? null
                : new BindingResult(new InfusionBindHandle(recipes), BindingMode.REAL);
    }

    @Override
    public boolean canDispatch(ServerLevel level, BlockPos mainPos, Object handle) {
        if (!(handle instanceof InfusionBindHandle bind) || bind.recipes().isEmpty()) {
            return false;
        }
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be) || !MalumReflection.isAltarIdle(be)) {
            return false;
        }
        var mainInventory = MalumReflection.altarInventory(be);
        var spiritInventory = MalumReflection.altarSpiritInventory(be);
        var extrasInventory = MalumReflection.altarExtrasInventory(be);
        return mainInventory != null
                && spiritInventory != null
                && extrasInventory != null
                && mainInventory.getSlots() >= 1
                && MalumAdapterSupport.inventoryEmpty(mainInventory)
                && MalumAdapterSupport.inventoryEmpty(spiritInventory)
                && MalumAdapterSupport.inventoryEmpty(extrasInventory);
    }

    @Override
    @Nullable
    public DispatchPlan planWithBinding(ServerLevel level, BlockPos mainPos,
                                        IPatternDetails pattern, KeyCounter[] inputs,
                                        Object handle, IActionSource source) {
        if (!(handle instanceof InfusionBindHandle bind)) {
            return null;
        }
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be) || !MalumReflection.isAltarIdle(be)) {
            return null;
        }

        var mainInventory = MalumReflection.altarInventory(be);
        var spiritInventory = MalumReflection.altarSpiritInventory(be);
        var extrasInventory = MalumReflection.altarExtrasInventory(be);
        if (mainInventory == null
                || spiritInventory == null
                || extrasInventory == null
                || mainInventory.getSlots() < 1
                || !MalumAdapterSupport.inventoryEmpty(mainInventory)
                || !MalumAdapterSupport.inventoryEmpty(spiritInventory)
                || !MalumAdapterSupport.inventoryEmpty(extrasInventory)) {
            return null;
        }

        var aggregatedInputs = MalumAdapterSupport.aggregateInputs(inputs, MAX_INPUT_AMOUNT);
        if (aggregatedInputs == null) {
            return null;
        }

        var pedestals = loadedPedestals(level, mainPos);
        var match = findRecipeMatch(bind.recipes(), level, pattern, aggregatedInputs);
        if (match == null
                || match.spirits().size() > spiritInventory.getSlots()
                || match.extras().size() > pedestals.size()) {
            return null;
        }
        if (!match.extras().isEmpty() && !pedestals.stream().allMatch(MalumSpiritInfusionAdapter::pedestalEmpty)) {
            return null;
        }

        var targets = new ArrayList<TargetSlot>(1 + match.spirits().size() + match.extras().size());
        targets.add(new TargetSlot(
                level,
                mainPos,
                null,
                List.of(MalumAdapterSupport.toGenericStack(match.main())),
                InsertionStrategy.CUSTOM,
                altarInserter(level, mainPos, false, 0, match.main())));

        for (int i = 0; i < match.spirits().size(); i++) {
            var assignment = match.spirits().get(i);
            targets.add(new TargetSlot(
                    level,
                    mainPos,
                    null,
                    List.of(MalumAdapterSupport.toGenericStack(assignment)),
                    InsertionStrategy.CUSTOM,
                    altarInserter(level, mainPos, true, i, assignment)));
        }

        for (int i = 0; i < match.extras().size(); i++) {
            var assignment = match.extras().get(i);
            var pedestal = pedestals.get(i);
            targets.add(new TargetSlot(
                    level,
                    pedestal.pos(),
                    null,
                    List.of(MalumAdapterSupport.toGenericStack(assignment)),
                    InsertionStrategy.CUSTOM,
                    pedestalInserter(level, mainPos, pedestal.pos(), assignment)));
        }

        return new DispatchPlan(
                List.copyOf(targets),
                () -> MalumReflection.recalculateAltar(level, mainPos));
    }

    @Override
    public List<GenericStack> extractOutputs(ServerLevel level, BlockPos mainPos,
                                             AllowedOutputFilter filter,
                                             IActionSource source) {
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)) {
            return List.of();
        }
        return DroppedItemDispatch.collectOutputs(
                level,
                altarOutputAabb(mainPos),
                filter,
                entity -> true);
    }

    @Nullable
    private static RecipeMatch findRecipeMatch(
            List<Object> recipes,
            ServerLevel level,
            IPatternDetails pattern,
            List<MalumRecipeInputMatcher.Input<AEItemKey>> inputs) {
        for (var recipe : recipes) {
            var mainInput = MalumReflection.infusionInput(recipe);
            var spiritStacks = MalumReflection.infusionSpirits(recipe);
            var extraInputs = MalumReflection.infusionExtras(recipe);
            if (mainInput == null || spiritStacks == null || extraInputs == null) {
                continue;
            }

            var spiritRequirements = MalumAdapterSupport.requirementsFromStacks(spiritStacks);
            if (spiritRequirements == null) {
                continue;
            }

            var extraRequirements = extraInputs.stream()
                    .map(MalumAdapterSupport::requirement)
                    .toList();

            var match = MalumRecipeInputMatcher.match(
                    inputs,
                    MalumAdapterSupport.requirement(mainInput),
                    spiritRequirements,
                    extraRequirements);
            if (match == null) {
                continue;
            }

            var output = MalumReflection.infusionOutput(
                    recipe,
                    level,
                    MalumAdapterSupport.toItemStack(match.main()));
            if (output != null && !output.isEmpty() && MalumAdapterSupport.outputMatches(pattern, output)) {
                return new RecipeMatch(match.main(), match.spirits(), match.extras());
            }
        }
        return null;
    }

    private static List<Object> findCandidateRecipes(ServerLevel level, int availablePedestals) {
        var matches = new ArrayList<Object>();
        for (var holder : recipes(level)) {
            var recipe = holder.value();
            var mainInput = MalumReflection.infusionInput(recipe);
            var spiritStacks = MalumReflection.infusionSpirits(recipe);
            var extraInputs = MalumReflection.infusionExtras(recipe);
            if (mainInput == null || spiritStacks == null || extraInputs == null) {
                continue;
            }
            if (extraInputs.size() > availablePedestals) {
                continue;
            }
            var spiritRequirements = MalumAdapterSupport.requirementsFromStacks(spiritStacks);
            if (spiritRequirements == null) {
                continue;
            }
            matches.add(recipe);
        }
        return List.copyOf(matches);
    }

    private static BiFunction<GenericStack, Actionable, Long> altarInserter(
            ServerLevel level,
            BlockPos mainPos,
            boolean spirit,
            int slot,
            MalumRecipeInputMatcher.Assignment<AEItemKey> assignment) {
        return (stack, mode) -> {
            if (!MalumAdapterSupport.matchesPlannedStack(stack, assignment)) {
                return 0L;
            }

            var be = level.getBlockEntity(mainPos);
            if (be == null
                    || !blockId(be.getBlockState()).equals(ALTAR_BLOCK)
                    || !MalumReflection.isSpiritAltar(be)
                    || !MalumReflection.isAltarInactive(be)) {
                return 0L;
            }

            var inventory = spirit
                    ? MalumReflection.altarSpiritInventory(be)
                    : MalumReflection.altarInventory(be);
            if (inventory == null) {
                return 0L;
            }

            var planned = MalumAdapterSupport.toItemStack(assignment);
            if (!MalumAdapterSupport.canPlace(inventory, slot, planned)) {
                return 0L;
            }

            if (mode == Actionable.MODULATE) {
                inventory.setStackInSlot(slot, planned);
            }
            return assignment.amount();
        };
    }

    private static BiFunction<GenericStack, Actionable, Long> pedestalInserter(
            ServerLevel level,
            BlockPos altarPos,
            BlockPos pedestalPos,
            MalumRecipeInputMatcher.Assignment<AEItemKey> assignment) {
        return (stack, mode) -> {
            if (!MalumAdapterSupport.matchesPlannedStack(stack, assignment)) {
                return 0L;
            }

            var altar = level.getBlockEntity(altarPos);
            if (altar == null
                    || !blockId(altar.getBlockState()).equals(ALTAR_BLOCK)
                    || !MalumReflection.isSpiritAltar(altar)
                    || !MalumReflection.isAltarInactive(altar)) {
                return 0L;
            }

            var pedestal = pedestalAt(level, altarPos, pedestalPos);
            if (pedestal == null) {
                return 0L;
            }

            var planned = MalumAdapterSupport.toItemStack(assignment);
            if (!MalumAdapterSupport.canPlace(pedestal.inventory(), 0, planned)) {
                return 0L;
            }

            if (mode == Actionable.MODULATE) {
                pedestal.inventory().setStackInSlot(0, planned);
            }
            return assignment.amount();
        };
    }

    private static List<MalumReflection.Pedestal> loadedPedestals(ServerLevel level, BlockPos altarPos) {
        return MalumReflection.capturePedestals(level, altarPos).stream()
                .filter(pedestal -> level.isLoaded(pedestal.pos()))
                .toList();
    }

    @Nullable
    private static MalumReflection.Pedestal pedestalAt(ServerLevel level, BlockPos altarPos, BlockPos pedestalPos) {
        for (var pedestal : loadedPedestals(level, altarPos)) {
            if (pedestal.pos().equals(pedestalPos)) {
                return pedestal;
            }
        }
        return null;
    }

    private static boolean pedestalEmpty(MalumReflection.Pedestal pedestal) {
        return pedestal.inventory().getSlots() >= 1 && pedestal.inventory().getStackInSlot(0).isEmpty();
    }

    private static AABB altarOutputAabb(BlockPos pos) {
        return new AABB(
                pos.getX() - 1.0,
                pos.getY(),
                pos.getZ() - 1.0,
                pos.getX() + 2.0,
                pos.getY() + 2.5,
                pos.getZ() + 2.0);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<RecipeHolder<?>> recipes(ServerLevel level) {
        return BuiltInRegistries.RECIPE_TYPE.getOptional(RECIPE_TYPE_ID)
                .map(type -> (List<RecipeHolder<?>>) (List<?>) level.getRecipeManager()
                        .getAllRecipesFor((RecipeType) type))
                .orElse(List.of());
    }

    private static boolean isMalumLoaded() {
        try {
            return ModList.get().isLoaded(MOD_ID);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private static ResourceLocation blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock());
    }

    private static ResourceLocation malumId(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static boolean hasSingleItemOutput(IPatternDetails pattern) {
        var outputs = pattern.getOutputs();
        return outputs.size() == 1 && outputs.getFirst().what() instanceof AEItemKey;
    }

    private record RecipeMatch(
            MalumRecipeInputMatcher.Assignment<AEItemKey> main,
            List<MalumRecipeInputMatcher.Assignment<AEItemKey>> spirits,
            List<MalumRecipeInputMatcher.Assignment<AEItemKey>> extras) {
        RecipeMatch {
            Objects.requireNonNull(main, "main");
            spirits = List.copyOf(spirits);
            extras = List.copyOf(extras);
        }
    }

    /** Opaque binding handle returned from {@link #bind}. */
    private record InfusionBindHandle(List<Object> recipes) {
        InfusionBindHandle {
            recipes = List.copyOf(recipes);
        }
    }
}
