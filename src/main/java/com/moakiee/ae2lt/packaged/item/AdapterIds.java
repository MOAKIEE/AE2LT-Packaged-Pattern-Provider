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
    public static final ResourceLocation MA_AWAKENING =
            id("mysticalagriculture/awakening_adapter");
    public static final ResourceLocation MA_INFUSION =
            id("mysticalagriculture/infusion_adapter");
    public static final ResourceLocation OCCULTISM_RITUAL =
            id("occultism/ritual_adapter");
    public static final ResourceLocation OCCULTISM_SPIRIT_FIRE =
            id("occultism/spirit_fire_adapter");
    public static final ResourceLocation MEKMM_NUCLEOSYNTHESIZER =
            id("mekmm/large_nucleosynthesizer_adapter");
    public static final ResourceLocation MEKMM_CHEMICAL_INFUSER =
            id("mekmm/large_chemical_infuser_adapter");
    public static final ResourceLocation MEKMM_ELECTROLYTIC_SEPARATOR =
            id("mekmm/large_electrolytic_separator_adapter");
    public static final ResourceLocation MEKMM_ROTARY_CONDENSENTRATOR =
            id("mekmm/large_rotary_condensentrator_adapter");
    public static final ResourceLocation MEKMM_SOLAR_NEUTRON_ACTIVATOR =
            id("mekmm/large_solar_neutron_activator_adapter");
    public static final ResourceLocation MEKMM_PIGMENT_MIXER =
            id("mekmm/large_pigment_mixer_adapter");

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(AE2LTPackagedProvider.MODID, path);
    }
}
