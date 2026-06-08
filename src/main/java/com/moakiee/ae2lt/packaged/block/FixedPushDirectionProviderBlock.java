package com.moakiee.ae2lt.packaged.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import appeng.block.crafting.PatternProviderBlock;
import appeng.block.crafting.PushDirection;

import com.moakiee.ae2lt.block.OverloadedPatternProviderBlock;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;

class FixedPushDirectionProviderBlock<T extends OverloadedPatternProviderBlockEntity>
        extends OverloadedPatternProviderBlock<T> {

    @Override
    public void setSide(Level level, BlockPos pos, Direction facing) {
        forceAllPushDirection(level, pos);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos,
                                Block block, BlockPos fromPos, boolean isMoving) {
        forceAllPushDirection(level, pos);
        super.neighborChanged(level.getBlockState(pos), level, pos, block, fromPos, isMoving);
    }

    private void forceAllPushDirection(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (!state.hasProperty(PatternProviderBlock.PUSH_DIRECTION)
                || state.getValue(PatternProviderBlock.PUSH_DIRECTION) == PushDirection.ALL) {
            return;
        }

        level.setBlockAndUpdate(pos, state.setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.ALL));
        var be = getBlockEntity(level, pos);
        if (be != null) {
            be.onNeighborChanged();
        }
    }
}
