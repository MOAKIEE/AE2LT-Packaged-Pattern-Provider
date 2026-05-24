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

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
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

public final class LargePigmentMixerAdapter implements MultiblockAdapter {

    private static final ResourceLocation MAIN_BLOCK =
            ResourceLocation.fromNamespaceAndPath("mekmm", "large_pigment_mixer");
    private static final String TILE_CLASS =
            "com.jerry.meklm.common.tile.machine.TileEntityLargePigmentMixer";
    private static final Object BIND_HANDLE = new Object();

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public ResourceLocation requiredAdapterId(ServerLevel level, BlockPos pos) {
        return AdapterIds.MEKMM_PIGMENT_MIXER;
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
        var leftPort = MekPortLayout.pigmentMixerLeftInput(mainPos, facing);
        var rightPort = MekPortLayout.pigmentMixerRightInput(mainPos, facing);

        if (!level.isLoaded(leftPort.pos()) || !level.isLoaded(rightPort.pos())) return null;

        AEKey chemA = null;
        long amountA = 0;
        AEKey chemB = null;
        long amountB = 0;

        for (var counter : inputs) {
            for (var entry : counter) {
                AEKey key = entry.getKey();
                long amount = entry.getLongValue();
                if (amount <= 0) continue;
                if (!AppmekReflection.isMekanismKey(key)) return null;
                if (chemA == null) {
                    chemA = key;
                    amountA = amount;
                } else if (chemB == null) {
                    chemB = key;
                    amountB = amount;
                } else {
                    return null;
                }
            }
        }

        if (chemA == null || chemB == null) return null;

        var targets = new ArrayList<TargetSlot>();
        targets.add(new TargetSlot(level, leftPort.pos(), leftPort.accessSide(),
                List.of(new GenericStack(chemA, amountA)),
                InsertionStrategy.CUSTOM,
                chemicalInserter(level, leftPort)));
        targets.add(new TargetSlot(level, rightPort.pos(), rightPort.accessSide(),
                List.of(new GenericStack(chemB, amountB)),
                InsertionStrategy.CUSTOM,
                chemicalInserter(level, rightPort)));

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
        var outPort = MekPortLayout.pigmentMixerOutput(mainPos, facing);
        if (!level.isLoaded(outPort.pos())) return List.of();

        Object handler = MekReflection.getChemicalHandler(level, outPort.pos(), outPort.accessSide());
        if (handler == null) return List.of();

        var results = new ArrayList<GenericStack>();
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
        return results;
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
