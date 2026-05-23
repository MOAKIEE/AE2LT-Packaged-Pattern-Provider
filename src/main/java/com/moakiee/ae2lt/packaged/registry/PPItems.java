package com.moakiee.ae2lt.packaged.registry;

import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.moakiee.ae2lt.packaged.AE2LTPackagedProvider;
import com.moakiee.ae2lt.packaged.item.AdapterIds;
import com.moakiee.ae2lt.packaged.item.MultiblockAdapterItem;

public final class PPItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(AE2LTPackagedProvider.MODID);

    // ===== Adapter key cards (1 per slot, max-stack 1) =====
    //
    // Each provider holds exactly one card and is locked to that machine family.
    // EC tiers form a covers-down chain (ultimate ⊇ elite ⊇ advanced ⊇ basic) so
    // higher cards work on lower tables, matching the user's "向下兼容" requirement.

    public static final DeferredItem<MultiblockAdapterItem> AA_RECONSTRUCTOR_ADAPTER =
            registerAdapter("aa_reconstructor_adapter",
                    AdapterIds.AA_RECONSTRUCTOR,
                    Set.of(AdapterIds.AA_RECONSTRUCTOR));

    public static final DeferredItem<MultiblockAdapterItem> AA_EMPOWERER_ADAPTER =
            registerAdapter("aa_empowerer_adapter",
                    AdapterIds.AA_EMPOWERER,
                    Set.of(AdapterIds.AA_EMPOWERER));

    public static final DeferredItem<MultiblockAdapterItem> ARS_APPARATUS_ADAPTER =
            registerAdapter("ars_apparatus_adapter",
                    AdapterIds.ARS_APPARATUS,
                    Set.of(AdapterIds.ARS_APPARATUS));

    public static final DeferredItem<MultiblockAdapterItem> DE_FUSION_ADAPTER =
            registerAdapter("de_fusion_adapter",
                    AdapterIds.DE_FUSION,
                    Set.of(AdapterIds.DE_FUSION));

    public static final DeferredItem<MultiblockAdapterItem> EC_BASIC_ADAPTER =
            registerAdapter("ec_basic_table_adapter",
                    AdapterIds.EC_BASIC,
                    Set.of(AdapterIds.EC_BASIC));

    public static final DeferredItem<MultiblockAdapterItem> EC_ADVANCED_ADAPTER =
            registerAdapter("ec_advanced_table_adapter",
                    AdapterIds.EC_ADVANCED,
                    chain(AdapterIds.EC_BASIC, AdapterIds.EC_ADVANCED));

    public static final DeferredItem<MultiblockAdapterItem> EC_ELITE_ADAPTER =
            registerAdapter("ec_elite_table_adapter",
                    AdapterIds.EC_ELITE,
                    chain(AdapterIds.EC_BASIC, AdapterIds.EC_ADVANCED, AdapterIds.EC_ELITE));

    public static final DeferredItem<MultiblockAdapterItem> EC_ULTIMATE_ADAPTER =
            registerAdapter("ec_ultimate_table_adapter",
                    AdapterIds.EC_ULTIMATE,
                    chain(AdapterIds.EC_BASIC, AdapterIds.EC_ADVANCED,
                            AdapterIds.EC_ELITE, AdapterIds.EC_ULTIMATE));

    public static final DeferredItem<MultiblockAdapterItem> MA_AWAKENING_ADAPTER =
            registerAdapter("ma_awakening_altar_adapter",
                    AdapterIds.MA_AWAKENING,
                    Set.of(AdapterIds.MA_AWAKENING));

    public static final DeferredItem<MultiblockAdapterItem> MA_INFUSION_ADAPTER =
            registerAdapter("ma_infusion_altar_adapter",
                    AdapterIds.MA_INFUSION,
                    Set.of(AdapterIds.MA_INFUSION));

    public static final DeferredItem<MultiblockAdapterItem> MALUM_SPIRIT_FOCUSING_ADAPTER =
            registerAdapter("malum_spirit_focusing_adapter",
                    AdapterIds.MALUM_SPIRIT_FOCUSING,
                    Set.of(AdapterIds.MALUM_SPIRIT_FOCUSING));

    public static final DeferredItem<MultiblockAdapterItem> MALUM_SPIRIT_INFUSION_ADAPTER =
            registerAdapter("malum_spirit_infusion_adapter",
                    AdapterIds.MALUM_SPIRIT_INFUSION,
                    Set.of(AdapterIds.MALUM_SPIRIT_INFUSION));

    public static final DeferredItem<MultiblockAdapterItem> OCCULTISM_RITUAL_ADAPTER =
            registerAdapter("occultism_ritual_adapter",
                    AdapterIds.OCCULTISM_RITUAL,
                    Set.of(AdapterIds.OCCULTISM_RITUAL));

    public static final DeferredItem<MultiblockAdapterItem> OCCULTISM_SPIRIT_FIRE_ADAPTER =
            registerAdapter("occultism_spirit_fire_adapter",
                    AdapterIds.OCCULTISM_SPIRIT_FIRE,
                    Set.of(AdapterIds.OCCULTISM_SPIRIT_FIRE));

    public static final DeferredItem<MultiblockAdapterItem> MEKMM_NUCLEOSYNTHESIZER_ADAPTER =
            registerAdapter("mekmm_nucleosynthesizer_adapter",
                    AdapterIds.MEKMM_NUCLEOSYNTHESIZER,
                    Set.of(AdapterIds.MEKMM_NUCLEOSYNTHESIZER));

    public static final DeferredItem<MultiblockAdapterItem> MEKMM_CHEMICAL_INFUSER_ADAPTER =
            registerAdapter("mekmm_chemical_infuser_adapter",
                    AdapterIds.MEKMM_CHEMICAL_INFUSER,
                    Set.of(AdapterIds.MEKMM_CHEMICAL_INFUSER));

    public static final DeferredItem<MultiblockAdapterItem> MEKMM_ELECTROLYTIC_SEPARATOR_ADAPTER =
            registerAdapter("mekmm_electrolytic_separator_adapter",
                    AdapterIds.MEKMM_ELECTROLYTIC_SEPARATOR,
                    Set.of(AdapterIds.MEKMM_ELECTROLYTIC_SEPARATOR));

    public static final DeferredItem<MultiblockAdapterItem> MEKMM_ROTARY_CONDENSENTRATOR_ADAPTER =
            registerAdapter("mekmm_rotary_condensentrator_adapter",
                    AdapterIds.MEKMM_ROTARY_CONDENSENTRATOR,
                    Set.of(AdapterIds.MEKMM_ROTARY_CONDENSENTRATOR));

    public static final DeferredItem<MultiblockAdapterItem> MEKMM_SOLAR_NEUTRON_ACTIVATOR_ADAPTER =
            registerAdapter("mekmm_solar_neutron_activator_adapter",
                    AdapterIds.MEKMM_SOLAR_NEUTRON_ACTIVATOR,
                    Set.of(AdapterIds.MEKMM_SOLAR_NEUTRON_ACTIVATOR));

    public static final DeferredItem<MultiblockAdapterItem> MEKMM_PIGMENT_MIXER_ADAPTER =
            registerAdapter("mekmm_pigment_mixer_adapter",
                    AdapterIds.MEKMM_PIGMENT_MIXER,
                    Set.of(AdapterIds.MEKMM_PIGMENT_MIXER));

    private PPItems() {
    }

    private static DeferredItem<MultiblockAdapterItem> registerAdapter(
            String name,
            ResourceLocation primaryId,
            Set<ResourceLocation> coveredIds) {
        return ITEMS.register(name,
                () -> new MultiblockAdapterItem(
                        new Item.Properties().stacksTo(1),
                        primaryId,
                        coveredIds));
    }

    /** Build an insertion-ordered set (deterministic NBT-stable ordering). */
    private static Set<ResourceLocation> chain(ResourceLocation... ids) {
        var set = new LinkedHashSet<ResourceLocation>(ids.length);
        for (var id : ids) {
            set.add(id);
        }
        return set;
    }
}
