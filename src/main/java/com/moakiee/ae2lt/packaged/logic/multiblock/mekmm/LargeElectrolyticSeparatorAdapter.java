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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.moakiee.ae2lt.logic.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.item.AdapterIds;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan;
import com.moakiee.ae2lt.packaged.logic.multiblock.InsertionStrategy;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.TargetSlot;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingResult;

public final class LargeElectrolyticSeparatorAdapter implements MultiblockAdapter {

    private static final ResourceLocation MAIN_BLOCK =
            ResourceLocation.fromNamespaceAndPath("mekmm", "large_electrolytic_separator");
    private static final String TILE_CLASS =
            "com.jerry.meklm.common.tile.machine.TileEntityLargeElectrolyticSeparator";
    private static final Object BIND_HANDLE = new Object();

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public ResourceLocation requiredAdapterId(ServerLevel level, BlockPos pos) {
        return AdapterIds.MEKMM_ELECTROLYTIC_SEPARATOR;
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
        if (be == null || !MekReflection.isTileEntityClass(be, TILE_CLASS)) return null;
        if (MekReflection.isActive(be)) return null;

        Direction facing = MekReflection.getFacing(be);
        var fluidPort = MekPortLayout.electrolyticSeparatorFluidInput(mainPos, facing);
        if (!level.isLoaded(fluidPort.pos())) return null;

        AEFluidKey fluidInput = null;
        long fluidAmount = 0;

        for (var counter : inputs) {
            for (var entry : counter) {
                AEKey key = entry.getKey();
                long amount = entry.getLongValue();
                if (amount <= 0) continue;
                if (key instanceof AEFluidKey fk) {
                    if (fluidInput != null) return null;
                    fluidInput = fk;
                    fluidAmount = amount;
                } else {
                    return null;
                }
            }
        }

        if (fluidInput == null) return null;

        var targets = new ArrayList<TargetSlot>();
        targets.add(new TargetSlot(level, fluidPort.pos(), fluidPort.accessSide(),
                List.of(new GenericStack(fluidInput, fluidAmount)),
                InsertionStrategy.CUSTOM,
                fluidInserter(level, fluidPort)));

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
        var leftOut = MekPortLayout.electrolyticSeparatorLeftOutput(mainPos, facing);
        var rightOut = MekPortLayout.electrolyticSeparatorRightOutput(mainPos, facing);

        var results = new ArrayList<GenericStack>();
        extractChemicalFromPort(level, leftOut, filter, results);
        extractChemicalFromPort(level, rightOut, filter, results);
        return results;
    }

    private static void extractChemicalFromPort(ServerLevel level, MekPortLayout.PortInfo port,
                                                AllowedOutputFilter filter, List<GenericStack> results) {
        if (!level.isLoaded(port.pos())) return;
        Object handler = MekReflection.getChemicalHandler(level, port.pos(), port.accessSide());
        if (handler == null) return;
        int tanks = MekReflection.getChemicalTanks(handler);
        for (int i = 0; i < tanks; i++) {
            Object stack = MekReflection.getChemicalInTank(handler, i);
            if (MekReflection.isChemicalEmpty(stack)) continue;
            AEKey key = AppmekReflection.fromChemicalStack(stack);
            if (key == null || !filter.matches(key)) continue;
            long stored = MekReflection.getChemicalAmount(stack);
            Object extracted = MekReflection.extractChemical(handler, stored, false);
            if (extracted == null || MekReflection.isChemicalEmpty(extracted)) continue;
            results.add(new GenericStack(key, MekReflection.getChemicalAmount(extracted)));
        }
    }

    private static BiFunction<GenericStack, Actionable, Long> fluidInserter(
            ServerLevel level, MekPortLayout.PortInfo port) {
        return (stack, mode) -> {
            if (!(stack.what() instanceof AEFluidKey fluidKey)) return 0L;
            IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK,
                    port.pos(), port.accessSide());
            if (handler == null) return 0L;
            FluidStack toInsert = fluidKey.toStack((int) stack.amount());
            int filled = handler.fill(toInsert,
                    mode == Actionable.SIMULATE ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
            return (long) filled;
        };
    }
}
