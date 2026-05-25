package com.moakiee.ae2lt.packaged.item;

import net.minecraft.resources.ResourceLocation;

import com.moakiee.ae2lt.packaged.AE2LTPackagedProvider;

/**
 * Central registry of adapter ids used as a contract between:
 * <ul>
 *   <li>{@code MultiblockAdapter.requiredAdapterId} &mdash; what an adapter needs.</li>
 *   <li>{@link MultiblockAdapterItem} &mdash; what an installed key card covers.</li>
 * </ul>
 *
 * <p>Naming convention:
 * <pre>
 *   ae2ltpp:&lt;target_mod_id&gt;/&lt;recipe_type&gt;_adapter
 * </pre>
 * The target mod id is the upstream mod (e.g. {@code actuallyadditions}), the
 * recipe type is the upstream-side recipe registry name (e.g. {@code laser}),
 * and the {@code _adapter} suffix marks this as a key-card namespace rather
 * than a raw resource.
 *
 * <p>EC's four table tiers share the {@code extendedcrafting/table} recipe
 * type, so they're distinguished by a {@code _tierN} infix inside the recipe
 * portion to keep the scheme uniform.
 *
 * <p>These strings are stable: changing one orphans any existing in-world key
 * card holding the old id.
 */
public final class AdapterIds {

    private AdapterIds() {
    }

    public static final ResourceLocation AA_RECONSTRUCTOR =
            id("actuallyadditions/laser_adapter");
    public static final ResourceLocation AA_EMPOWERER =
            id("actuallyadditions/empower_adapter");
    public static final ResourceLocation ARS_APPARATUS =
            id("ars_nouveau/enchanting_apparatus_adapter");
    public static final ResourceLocation ARS_IMBUEMENT =
            id("ars_nouveau/imbuement_adapter");
    public static final ResourceLocation DE_FUSION =
            id("draconicevolution/fusion_crafting_adapter");
    public static final ResourceLocation EC_BASIC =
            id("extendedcrafting/table_tier1_adapter");
    public static final ResourceLocation EC_ADVANCED =
            id("extendedcrafting/table_tier2_adapter");
    public static final ResourceLocation EC_ELITE =
            id("extendedcrafting/table_tier3_adapter");
    public static final ResourceLocation EC_ULTIMATE =
            id("extendedcrafting/table_tier4_adapter");
    public static final ResourceLocation EC_ENDER =
            id("extendedcrafting/ender_crafter_adapter");
    public static final ResourceLocation EC_FLUX =
            id("extendedcrafting/flux_crafter_adapter");
    public static final ResourceLocation EC_COMBINATION =
            id("extendedcrafting/combination_adapter");
    public static final ResourceLocation FA_HEPHAESTUS_FORGE =
            id("forbidden_arcanus/hephaestus_forge_adapter");
    public static final ResourceLocation FA_CLIBANO =
            id("forbidden_arcanus/clibano_adapter");
    public static final ResourceLocation MA_AWAKENING =
            id("mysticalagriculture/awakening_adapter");
    public static final ResourceLocation MA_INFUSION =
            id("mysticalagriculture/infusion_adapter");
    public static final ResourceLocation OCCULTISM_RITUAL =
            id("occultism/ritual_adapter");
    /**
     * Single key card covering every Mekanism: More Machines large multiblock
     * (chemical infuser, electrolytic separator, antiprotonic nucleosynthesizer,
     * rotary condensentrator, solar neutron activator, pigment mixer). The
     * generic {@code MekanismMoreMachinesAdapter} dispatches between them at
     * runtime via {@code MekmmMachines}.
     */
    public static final ResourceLocation MEKMM =
            id("mekmm/large_machine_adapter");

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(AE2LTPackagedProvider.MODID, path);
    }
}
