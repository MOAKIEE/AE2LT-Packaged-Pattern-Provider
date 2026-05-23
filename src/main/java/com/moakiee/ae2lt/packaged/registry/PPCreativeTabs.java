package com.moakiee.ae2lt.packaged.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.moakiee.ae2lt.packaged.AE2LTPackagedProvider;

public final class PPCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AE2LTPackagedProvider.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB =
            CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ae2ltpp"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> PPBlocks.PACKAGED_PATTERN_PROVIDER.get().asItem().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(PPBlocks.PACKAGED_PATTERN_PROVIDER);
                        output.accept(PPBlocks.WIRELESS_PACKAGED_PATTERN_PROVIDER);
                        // Adapter key cards
                        output.accept(PPItems.AA_RECONSTRUCTOR_ADAPTER);
                        output.accept(PPItems.AA_EMPOWERER_ADAPTER);
                        output.accept(PPItems.ARS_APPARATUS_ADAPTER);
                        output.accept(PPItems.DE_FUSION_ADAPTER);
                        output.accept(PPItems.EC_BASIC_ADAPTER);
                        output.accept(PPItems.EC_ADVANCED_ADAPTER);
                        output.accept(PPItems.EC_ELITE_ADAPTER);
                        output.accept(PPItems.EC_ULTIMATE_ADAPTER);
                        output.accept(PPItems.MA_AWAKENING_ADAPTER);
                        output.accept(PPItems.MA_INFUSION_ADAPTER);
                        output.accept(PPItems.OCCULTISM_RITUAL_ADAPTER);
                        output.accept(PPItems.OCCULTISM_SPIRIT_FIRE_ADAPTER);
                        output.accept(PPItems.MEKMM_NUCLEOSYNTHESIZER_ADAPTER);
                        output.accept(PPItems.MEKMM_CHEMICAL_INFUSER_ADAPTER);
                        output.accept(PPItems.MEKMM_ELECTROLYTIC_SEPARATOR_ADAPTER);
                        output.accept(PPItems.MEKMM_ROTARY_CONDENSENTRATOR_ADAPTER);
                        output.accept(PPItems.MEKMM_SOLAR_NEUTRON_ACTIVATOR_ADAPTER);
                        output.accept(PPItems.MEKMM_PIGMENT_MIXER_ADAPTER);
                    })
                    .build());

    private PPCreativeTabs() {
    }
}
