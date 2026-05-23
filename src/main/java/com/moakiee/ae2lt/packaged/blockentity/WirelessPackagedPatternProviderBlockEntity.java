package com.moakiee.ae2lt.packaged.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.stacks.AEItemKey;

import com.moakiee.ae2lt.packaged.registry.PPBlockEntities;
import com.moakiee.ae2lt.packaged.registry.PPBlocks;

public class WirelessPackagedPatternProviderBlockEntity extends PackagedPatternProviderBlockEntity {

    public WirelessPackagedPatternProviderBlockEntity(BlockPos pos, BlockState blockState) {
        super(PPBlockEntities.WIRELESS_PACKAGED_PATTERN_PROVIDER.get(), pos, blockState, ProviderMode.WIRELESS);
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return new ItemStack(PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER.get());
    }

    @Override
    public AEItemKey getTerminalIcon() {
        return AEItemKey.of(PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER.get());
    }

    @Override
    protected Item getItemFromBlockEntity() {
        return PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER.get().asItem();
    }

    @Override
    public String ae2lt$titleTranslationKey() {
        return "ae2ltpp.gui.title.wireless_packaged_pattern_provider";
    }
}
