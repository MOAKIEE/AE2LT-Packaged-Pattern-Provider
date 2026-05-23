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

    public static final DeferredItem<MultiblockAdapterItem> OCCULTISM_RITUAL_ADAPTER =
            registerAdapter("occultism_ritual_adapter",
                    AdapterIds.OCCULTISM_RITUAL,
                    Set.of(AdapterIds.OCCULTISM_RITUAL));

    public static final DeferredItem<MultiblockAdapterItem> OCCULTISM_SPIRIT_FIRE_ADAPTER =
            registerAdapter("occultism_spirit_fire_adapter",
                    AdapterIds.OCCULTISM_SPIRIT_FIRE,
                    Set.of(AdapterIds.OCCULTISM_SPIRIT_FIRE));

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
