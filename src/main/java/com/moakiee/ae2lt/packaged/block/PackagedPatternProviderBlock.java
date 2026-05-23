package com.moakiee.ae2lt.packaged.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import com.moakiee.ae2lt.block.OverloadedPatternProviderBlock;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.item.MultiblockAdapterItem;

public class PackagedPatternProviderBlock extends OverloadedPatternProviderBlock<PackagedPatternProviderBlockEntity> {
    public PackagedPatternProviderBlock() {
        super();
    }

    /**
     * Adapter-key-card install path: when the player right-clicks the provider
     * with a {@link MultiblockAdapterItem} in hand, swap it into the adapter
     * slot (or take the old card out if the slot is occupied). Any other held
     * item / empty hand falls through to the parent which opens the GUI.
     *
     * <p>This is the lightweight stand-in for a proper GUI slot. It keeps the
     * key card mechanic usable until the dedicated adapter slot widget is
     * stitched into the provider GUI.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack heldItem, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (heldItem.getItem() instanceof MultiblockAdapterItem) {
            var be = getBlockEntity(level, pos);
            if (be != null) {
                if (!level.isClientSide()) {
                    swapAdapterCard(be, player, heldItem, hand);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide());
            }
        }
        return super.useItemOn(heldItem, state, level, pos, player, hand, hit);
    }

    /**
     * Swap the held key card with whatever's already in the adapter slot.
     * The player ends up holding the previous card (which may be EMPTY),
     * and the slot ends up holding the new card.
     */
    private static void swapAdapterCard(PackagedPatternProviderBlockEntity be,
                                         Player player,
                                         ItemStack heldItem,
                                         InteractionHand hand) {
        var inv = be.getAdapterInv();
        var previous = inv.getStackInSlot(0);

        // Pull a single card off the held stack (key cards stack to 1 anyway,
        // but be defensive for /give shenanigans).
        var inserted = heldItem.copy();
        inserted.setCount(1);
        inv.setItemDirect(0, inserted);

        if (heldItem.getCount() <= 1) {
            player.setItemInHand(hand, previous);
        } else {
            heldItem.shrink(1);
            // Give the previous card back via inventory; if full, drop at feet.
            if (!previous.isEmpty() && !player.getInventory().add(previous)) {
                player.drop(previous, false);
            }
        }
    }
}
