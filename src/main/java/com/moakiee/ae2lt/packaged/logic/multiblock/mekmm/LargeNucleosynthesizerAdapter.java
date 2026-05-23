package com.moakiee.ae2lt.packaged.logic.multiblock.mekmm;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import com.moakiee.ae2lt.logic.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.item.AdapterIds;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan;
import com.moakiee.ae2lt.packaged.logic.multiblock.InsertionStrategy;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.TargetSlot;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingResult;

public final class LargeNucleosynthesizerAdapter implements MultiblockAdapter {

    private static final Logger LOG = LogUtils.getLogger();

    private static final ResourceLocation MAIN_BLOCK =
            ResourceLocation.fromNamespaceAndPath("mekmm", "large_antiprotonic_nucleosynthesizer");
    private static final String TILE_CLASS =
            "com.jerry.meklm.common.tile.machine.TileEntityLargeAntiprotonicNucleosynthesizer";
    private static final Object BIND_HANDLE = new Object();

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public ResourceLocation requiredAdapterId(ServerLevel level, BlockPos pos) {
        return AdapterIds.MEKMM_NUCLEOSYNTHESIZER;
    }

    @Override
    public boolean recognizesMain(ServerLevel level, BlockPos pos, BlockEntity be) {
        if (be == null || !MekReflection.isModLoaded()) return false;
        if (BuiltInRegistries.BLOCK.getKey(be.getBlockState().getBlock()).equals(MAIN_BLOCK)
                && MekReflection.isTileEntityClass(be, TILE_CLASS)) {
            return true;
        }
        if (MekReflection.isBoundingBlock(be)) {
            BlockPos mainPos = MekReflection.getMainPos(be);
            if (mainPos == null || !level.isLoaded(mainPos)) return false;
            var mainBe = level.getBlockEntity(mainPos);
            return mainBe != null
                    && BuiltInRegistries.BLOCK.getKey(mainBe.getBlockState().getBlock()).equals(MAIN_BLOCK)
                    && MekReflection.isTileEntityClass(mainBe, TILE_CLASS);
        }
        return false;
    }

    private BlockPos resolveMainPos(ServerLevel level, BlockPos pos) {
        var be = level.getBlockEntity(pos);
        if (be != null && MekReflection.isBoundingBlock(be)) {
            BlockPos resolved = MekReflection.getMainPos(be);
            if (resolved != null) return resolved;
        }
        return pos;
    }

    @Override
    @Nullable
    public BindingResult bind(ServerLevel level, BlockPos mainPos, IPatternDetails pattern) {
        mainPos = resolveMainPos(level, mainPos);
        var be = level.getBlockEntity(mainPos);
        if (be == null || !MekReflection.isTileEntityClass(be, TILE_CLASS)) return null;
        return new BindingResult(BIND_HANDLE, BindingMode.REAL);
    }

    @Override
    public boolean canDispatch(ServerLevel level, BlockPos mainPos, Object handle) {
        if (handle != BIND_HANDLE) return false;
        mainPos = resolveMainPos(level, mainPos);
        var be = level.getBlockEntity(mainPos);
        return be != null
                && MekReflection.isTileEntityClass(be, TILE_CLASS)
                && !MekReflection.isActive(be);
    }

    @Override
    @Nullable
    public DispatchPlan planWithBinding(ServerLevel level, BlockPos mainPos,
                                        IPatternDetails pattern, KeyCounter[] inputs,
                                        Object handle, IActionSource source) {
        if (handle != BIND_HANDLE) return null;
        mainPos = resolveMainPos(level, mainPos);
        var be = level.getBlockEntity(mainPos);
        if (be == null || !MekReflection.isTileEntityClass(be, TILE_CLASS)) {
            LOG.debug("[ae2ltpp] Nucleosynthesizer plan: BE null or wrong class at {}", mainPos);
            return null;
        }
        if (MekReflection.isActive(be)) {
            LOG.debug("[ae2ltpp] Nucleosynthesizer plan: machine active at {}", mainPos);
            return null;
        }

        Direction facing = MekReflection.getFacing(be);
        var chemPort = MekPortLayout.nucleosynthesizerChemicalInput(mainPos, facing);
        var itemInPort = MekPortLayout.nucleosynthesizerItemInput(mainPos, facing);

        if (!level.isLoaded(chemPort.pos()) || !level.isLoaded(itemInPort.pos())) {
            LOG.debug("[ae2ltpp] Nucleosynthesizer plan: port not loaded");
            return null;
        }

        AEItemKey itemInput = null;
        long itemAmount = 0;
        AEKey chemicalInput = null;
        long chemicalAmount = 0;

        for (var counter : inputs) {
            for (var entry : counter) {
                AEKey key = entry.getKey();
                long amount = entry.getLongValue();
                if (amount <= 0) continue;
                if (key instanceof AEItemKey itemKey) {
                    if (itemInput != null) return null;
                    itemInput = itemKey;
                    itemAmount = amount;
                } else if (AppmekReflection.isMekanismKey(key)) {
                    if (chemicalInput != null) return null;
                    chemicalInput = key;
                    chemicalAmount = amount;
                } else {
                    LOG.debug("[ae2ltpp] Nucleosynthesizer plan: unrecognized key type {}", key.getClass().getName());
                    return null;
                }
            }
        }

        if (itemInput == null) {
            LOG.debug("[ae2ltpp] Nucleosynthesizer plan: no item input found");
            return null;
        }

        var targets = new ArrayList<TargetSlot>();

        targets.add(new TargetSlot(level, itemInPort.pos(), itemInPort.accessSide(),
                List.of(new GenericStack(itemInput, itemAmount)),
                InsertionStrategy.CUSTOM,
                itemInserter(level, itemInPort)));

        if (chemicalInput != null) {
            targets.add(new TargetSlot(level, chemPort.pos(), chemPort.accessSide(),
                    List.of(new GenericStack(chemicalInput, chemicalAmount)),
                    InsertionStrategy.CUSTOM,
                    chemicalInserter(level, chemPort)));
        }

        return new DispatchPlan(List.copyOf(targets), null);
    }

    @Override
    public List<GenericStack> extractOutputs(ServerLevel level, BlockPos mainPos,
                                             AllowedOutputFilter filter,
                                             IActionSource source) {
        mainPos = resolveMainPos(level, mainPos);
        var be = level.getBlockEntity(mainPos);
        if (be == null || !MekReflection.isTileEntityClass(be, TILE_CLASS)) return List.of();
        if (MekReflection.isActive(be)) return List.of();

        Direction facing = MekReflection.getFacing(be);
        var itemOutPort = MekPortLayout.nucleosynthesizerItemOutput(mainPos, facing);
        if (!level.isLoaded(itemOutPort.pos())) return List.of();

        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK,
                itemOutPort.pos(), itemOutPort.accessSide());
        if (handler == null) return List.of();

        var results = new ArrayList<GenericStack>();
        for (int i = 0; i < handler.getSlots(); i++) {
            var stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            var key = AEItemKey.of(stack);
            if (!filter.matches(key)) continue;
            var extracted = handler.extractItem(i, stack.getCount(), false);
            if (!extracted.isEmpty()) {
                results.add(new GenericStack(AEItemKey.of(extracted), extracted.getCount()));
            }
        }
        return results;
    }

    private static BiFunction<GenericStack, Actionable, Long> itemInserter(
            ServerLevel level, MekPortLayout.PortInfo port) {
        return (stack, mode) -> {
            if (!(stack.what() instanceof AEItemKey itemKey)) return 0L;
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK,
                    port.pos(), port.accessSide());
            if (handler == null) return 0L;
            var toInsert = itemKey.toStack((int) stack.amount());
            return MekItemInsertion.insertIntoAnySlot(toInsert, mode == Actionable.SIMULATE,
                    new MekItemInsertion.SlotAccess<>() {
                        @Override
                        public int slots() {
                            return handler.getSlots();
                        }

                        @Override
                        public ItemStack insert(int slot, ItemStack stack, boolean simulate) {
                            return handler.insertItem(slot, stack, simulate);
                        }

                        @Override
                        public int amount(ItemStack stack) {
                            return stack.getCount();
                        }
                    });
        };
    }

    private static BiFunction<GenericStack, Actionable, Long> chemicalInserter(
            ServerLevel level, MekPortLayout.PortInfo port) {
        return (stack, mode) -> {
            if (!AppmekReflection.isMekanismKey(stack.what())) return 0L;
            Object chemStack = AppmekReflection.toChemicalStackWithAmount(stack.what(), stack.amount());
            if (chemStack == null) return 0L;
            Object handler = MekReflection.getChemicalHandler(level, port.pos(), port.accessSide());
            if (handler == null) return 0L;
            return MekReflection.insertChemical(handler, chemStack, mode == Actionable.SIMULATE);
        };
    }
}
