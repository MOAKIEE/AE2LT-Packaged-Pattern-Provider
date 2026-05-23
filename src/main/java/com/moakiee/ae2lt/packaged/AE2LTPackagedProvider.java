package com.moakiee.ae2lt.packaged;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import appeng.api.AECapabilities;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.blockentity.AEBaseBlockEntity;

import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.logic.InsertOnlyReturnInvWrapper;
import com.moakiee.ae2lt.logic.OverloadedPatternProviderLogic;
import com.moakiee.ae2lt.logic.UnlimitedReturnInventory;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.blockentity.WirelessPackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapterRegistry;
import com.moakiee.ae2lt.packaged.logic.multiblock.aa.ActuallyAdditionsAtomicReconstructorAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.aa.ActuallyAdditionsEmpowererAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.ars.ArsNouveauEnchantingApparatusAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.de.DraconicFusionCraftingAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.ec.ExtendedCraftingTableAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.ma.AwakeningAltarAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.ma.InfusionAltarAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.occultism.OccultismRitualAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.occultism.OccultismSpiritFireAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.mekmm.LargeNucleosynthesizerAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.mekmm.LargeChemicalInfuserAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.mekmm.LargeElectrolyticSeparatorAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.mekmm.LargeRotaryCondensentratorAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.mekmm.LargeSolarNeutronActivatorAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.mekmm.LargePigmentMixerAdapter;
import com.moakiee.ae2lt.packaged.registry.PPBlockEntities;
import com.moakiee.ae2lt.packaged.registry.PPBlocks;
import com.moakiee.ae2lt.packaged.registry.PPCreativeTabs;
import com.moakiee.ae2lt.packaged.registry.PPItems;
import com.moakiee.ae2lt.packaged.registry.PPMenuTypes;

@Mod(AE2LTPackagedProvider.MODID)
public class AE2LTPackagedProvider {
    public static final String MODID = "ae2ltpp";
    private static final Logger LOGGER = LogUtils.getLogger();

    public AE2LTPackagedProvider(IEventBus modEventBus, ModContainer modContainer) {
        PPItems.ITEMS.register(modEventBus);
        PPBlocks.BLOCKS.register(modEventBus);
        PPBlockEntities.BLOCK_ENTITY_TYPES.register(modEventBus);
        PPMenuTypes.MENU_TYPES.register(modEventBus);
        PPCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerCapabilities);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            MultiblockAdapterRegistry.register(new ActuallyAdditionsAtomicReconstructorAdapter());
            MultiblockAdapterRegistry.register(new ActuallyAdditionsEmpowererAdapter());
            MultiblockAdapterRegistry.register(new ArsNouveauEnchantingApparatusAdapter());
            MultiblockAdapterRegistry.register(new DraconicFusionCraftingAdapter());
            MultiblockAdapterRegistry.register(new ExtendedCraftingTableAdapter());
            MultiblockAdapterRegistry.register(new OccultismRitualAdapter());
            MultiblockAdapterRegistry.register(new OccultismSpiritFireAdapter());
            MultiblockAdapterRegistry.register(new InfusionAltarAdapter());
            MultiblockAdapterRegistry.register(new AwakeningAltarAdapter());
            MultiblockAdapterRegistry.register(new LargeNucleosynthesizerAdapter());
            MultiblockAdapterRegistry.register(new LargeChemicalInfuserAdapter());
            MultiblockAdapterRegistry.register(new LargeElectrolyticSeparatorAdapter());
            MultiblockAdapterRegistry.register(new LargeRotaryCondensentratorAdapter());
            MultiblockAdapterRegistry.register(new LargeSolarNeutronActivatorAdapter());
            MultiblockAdapterRegistry.register(new LargePigmentMixerAdapter());

            var packagedBlock = PPBlocks.PACKAGED_PATTERN_PROVIDER.get();
            var packagedBeType = PPBlockEntities.PACKAGED_PATTERN_PROVIDER.get();
            packagedBlock.setBlockEntity(
                    PackagedPatternProviderBlockEntity.class,
                    packagedBeType,
                    null,
                    OverloadedPatternProviderBlockEntity::serverTick);
            AEBaseBlockEntity.registerBlockEntityItem(packagedBeType, packagedBlock.asItem());

            var wirelessPackagedBlock = PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER.get();
            var wirelessPackagedBeType = PPBlockEntities.WIRELESS_PACKAGED_PATTERN_PROVIDER.get();
            wirelessPackagedBlock.setBlockEntity(
                    WirelessPackagedPatternProviderBlockEntity.class,
                    wirelessPackagedBeType,
                    null,
                    OverloadedPatternProviderBlockEntity::serverTick);
            AEBaseBlockEntity.registerBlockEntityItem(
                    wirelessPackagedBeType,
                    wirelessPackagedBlock.asItem());

            LOGGER.info("AE2LT Packaged Pattern Provider initialized");
        });
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        registerGridNodeHost(event, PPBlockEntities.PACKAGED_PATTERN_PROVIDER.get());
        registerGridNodeHost(event, PPBlockEntities.WIRELESS_PACKAGED_PATTERN_PROVIDER.get());

        event.registerBlock(
                AECapabilities.GENERIC_INTERNAL_INV,
                (level, pos, state, blockEntity, context) -> {
                    if (blockEntity instanceof OverloadedPatternProviderBlockEntity be) {
                        var logic = (OverloadedPatternProviderLogic) be.getLogic();
                        return new InsertOnlyReturnInvWrapper(
                                (UnlimitedReturnInventory) logic.getInternalReturnInv(),
                                logic);
                    }
                    return null;
                },
                PPBlocks.PACKAGED_PATTERN_PROVIDER.get(),
                PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER.get());
    }

    private static void registerGridNodeHost(
            RegisterCapabilitiesEvent event,
            BlockEntityType<? extends OverloadedPatternProviderBlockEntity> type) {
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                type,
                (blockEntity, context) -> (IInWorldGridNodeHost) blockEntity);
    }

}
