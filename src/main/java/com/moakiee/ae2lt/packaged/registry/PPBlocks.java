package com.moakiee.ae2lt.packaged.registry;

import java.util.function.Supplier;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.moakiee.ae2lt.packaged.AE2LTPackagedProvider;
import com.moakiee.ae2lt.packaged.block.PackagedPatternProviderBlock;
import com.moakiee.ae2lt.packaged.block.WirelessPackagedPatternProviderBlock;

public final class PPBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(AE2LTPackagedProvider.MODID);

    public static final DeferredBlock<PackagedPatternProviderBlock> PACKAGED_PATTERN_PROVIDER =
            registerBlock("packaged_pattern_provider", PackagedPatternProviderBlock::new);

    public static final DeferredBlock<WirelessPackagedPatternProviderBlock> WIRELESS_PACKAGED_PATTERN_PROVIDER =
            registerBlock("wireless_packaged_pattern_provider", WirelessPackagedPatternProviderBlock::new);

    private PPBlocks() {
    }

    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> blockFactory) {
        var registered = BLOCKS.register(name, blockFactory);
        PPItems.ITEMS.register(name, () -> new BlockItem(registered.get(), new Item.Properties()));
        return registered;
    }
}
