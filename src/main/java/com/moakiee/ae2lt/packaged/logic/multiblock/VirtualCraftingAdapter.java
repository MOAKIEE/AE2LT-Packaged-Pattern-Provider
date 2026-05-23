package com.moakiee.ae2lt.packaged.logic.multiblock;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.KeyCounter;

public interface VirtualCraftingAdapter extends MultiblockAdapter {

    @Nullable
    VirtualCraftingResult planVirtualCraft(ServerLevel level, BlockPos mainPos,
                                           IPatternDetails pattern, KeyCounter[] inputs,
                                           IActionSource source);
}
