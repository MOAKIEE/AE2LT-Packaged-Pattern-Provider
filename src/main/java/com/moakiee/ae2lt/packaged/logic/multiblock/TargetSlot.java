package com.moakiee.ae2lt.packaged.logic.multiblock;

import java.util.List;
import java.util.function.BiFunction;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

import appeng.api.config.Actionable;
import appeng.api.stacks.GenericStack;

public record TargetSlot(
        ServerLevel level,
        BlockPos pos,
        @Nullable Direction face,
        List<GenericStack> stacks,
        InsertionStrategy strategy,
        @Nullable BiFunction<GenericStack, Actionable, Long> customInserter
) {
    public TargetSlot(ServerLevel level, BlockPos pos, @Nullable Direction face,
                      List<GenericStack> stacks, InsertionStrategy strategy) {
        this(level, pos, face, stacks, strategy, null);
    }
}
