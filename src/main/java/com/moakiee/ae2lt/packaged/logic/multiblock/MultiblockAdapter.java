package com.moakiee.ae2lt.packaged.logic.multiblock;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.moakiee.ae2lt.logic.AllowedOutputFilter;

public interface MultiblockAdapter {

    /** Smaller value matches first. */
    int priority();

    /** Cheap check: is this BlockEntity a main block handled by this adapter? */
    boolean recognizesMain(ServerLevel level, BlockPos pos, BlockEntity be);

    /**
     * Return a prevalidated dispatch plan, or null when the pattern cannot run
     * against this multiblock right now.
     */
    @Nullable
    DispatchPlan plan(ServerLevel level, BlockPos mainPos,
                      IPatternDetails pattern, KeyCounter[] inputs,
                      IActionSource source);

    /**
     * Actively pull allowed outputs from the multiblock into the provider's
     * return inventory. Implementations must remove any returned stacks from
     * the target machine.
     */
    List<GenericStack> extractOutputs(ServerLevel level, BlockPos mainPos,
                                      AllowedOutputFilter filter,
                                      IActionSource source);
}
