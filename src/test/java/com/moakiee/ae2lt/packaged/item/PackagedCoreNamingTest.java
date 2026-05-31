package com.moakiee.ae2lt.packaged.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class PackagedCoreNamingTest {
    private static final List<String> EXPECTED_ITEM_IDS = List.of(
            "aa_reconstructor_packaged_core",
            "aa_empowerer_packaged_core",
            "ars_apparatus_packaged_core",
            "ars_imbuement_packaged_core",
            "de_fusion_packaged_core",
            "ec_basic_table_packaged_core",
            "ec_advanced_table_packaged_core",
            "ec_elite_table_packaged_core",
            "ec_ultimate_table_packaged_core",
            "ec_ender_crafter_packaged_core",
            "ec_flux_crafter_packaged_core",
            "ec_combination_packaged_core",
            "fa_hephaestus_forge_packaged_core",
            "fa_clibano_packaged_core",
            "ma_awakening_altar_packaged_core",
            "ma_infusion_altar_packaged_core",
            "malum_spirit_focusing_packaged_core",
            "malum_spirit_infusion_packaged_core",
            "occultism_ritual_packaged_core",
            "occultism_spirit_fire_packaged_core",
            "mekmm_packaged_core",
            "botania_petal_apothecary_packaged_core",
            "botania_mana_pool_packaged_core",
            "botania_alfheim_portal_packaged_core",
            "botania_terra_plate_packaged_core",
            "botania_runic_altar_packaged_core");

    @Test
    void itemRegistryNamesUsePackagedCoreSuffix() throws IOException {
        var source = read("src/main/java/com/moakiee/ae2lt/packaged/registry/PPItems.java");
        var matcher = Pattern.compile("register(?:Adapter|PackagedCore)\\(\"([^\"]+)\"").matcher(source);
        var actual = new ArrayList<String>();
        while (matcher.find()) {
            actual.add(matcher.group(1));
        }

        assertEquals(EXPECTED_ITEM_IDS, actual);
    }

    @Test
    void adapterContractIdsUsePackagedCoreSuffix() throws IllegalAccessException {
        for (var field : AdapterIds.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != ResourceLocation.class) {
                continue;
            }

            var id = (ResourceLocation) field.get(null);
            assertTrue(id.getPath().endsWith("_packaged_core"), id.toString());
            assertFalse(id.getPath().endsWith("_adapter"), id.toString());
        }
    }

    @Test
    void languageKeysAndNamesUsePackagedCore() throws IOException {
        var en = read("src/main/resources/assets/ae2ltpp/lang/en_us.json");
        var zh = read("src/main/resources/assets/ae2ltpp/lang/zh_cn.json");

        assertTrue(en.contains("\"ae2ltpp.guide.title\": \"AE2LT Packaged Pattern Provider\""));
        assertTrue(zh.contains("\"ae2ltpp.guide.title\": \"闪电科技：封包\""));
        assertTrue(en.contains("\"ae2ltpp.gui.adapter_slot\": \"Packaged Core\""));
        assertTrue(zh.contains("\"ae2ltpp.gui.adapter_slot\": \"封包核心\""));
        for (var id : EXPECTED_ITEM_IDS) {
            assertTrue(en.contains("\"item.ae2ltpp." + id + "\":"), id);
            assertTrue(zh.contains("\"item.ae2ltpp." + id + "\":"), id);
        }

        assertFalse(en.contains("_adapter\":"));
        assertFalse(zh.contains("_adapter\":"));
        assertFalse(en.contains("Adapter\""));
        assertFalse(zh.contains("适配器\""));
    }

    @Test
    void guideItemLinksUsePackagedCoreIds() throws IOException {
        var guide = readTree("src/main/resources/assets/ae2ltpp/ae2guide");
        for (var id : EXPECTED_ITEM_IDS) {
            assertTrue(guide.contains("ae2ltpp:" + id), id);
        }

        assertFalse(guide.contains("Adapter card"));
        assertFalse(guide.contains("Adapter cards"));
        assertFalse(guide.contains("Adapter Card"));
        assertFalse(Pattern.compile("\\badapters?\\b", Pattern.CASE_INSENSITIVE).matcher(guide).find());
        assertFalse(guide.contains("适配器卡片"));
        assertFalse(guide.contains("适配器"));
        assertFalse(guide.contains("适配卡"));
        assertFalse(Pattern.compile("ae2ltpp:[a-z0-9_]+_adapter").matcher(guide).find());
        assertFalse(Pattern.compile("icon: [a-z0-9_]+_adapter").matcher(guide).find());
    }

    @Test
    void guideItemLinksResolveToTheirPages() throws IOException {
        assertItemLinksDeclaredAsPageItems(Path.of("src/main/resources/assets/ae2ltpp/ae2guide"));
        assertItemLinksDeclaredAsPageItems(Path.of("src/main/resources/assets/ae2ltpp/ae2guide/_zh_cn"));
    }

    @Test
    void guideIsMountedInAe2GuideAfterMainMod() throws IOException {
        assertFalse(Files.exists(Path.of("src/main/resources/assets/ae2ltpp/guideme_guides/guide.json")));

        var enIndex = read("src/main/resources/assets/ae2ltpp/ae2guide/index.md");
        var zhIndex = read("src/main/resources/assets/ae2ltpp/ae2guide/_zh_cn/index.md");

        assertTrue(enIndex.contains("title: AE2LT Packaged Pattern Provider"));
        assertTrue(enIndex.contains("position: 67"));
        assertTrue(enIndex.contains("# AE2LT Packaged Pattern Provider"));
        assertTrue(enIndex.contains("ae2:guide"));
        assertFalse(enIndex.contains("ae2ltpp:guide"));
        assertTrue(zhIndex.contains("title: 闪电科技：封包"));
        assertTrue(zhIndex.contains("position: 67"));
        assertTrue(zhIndex.contains("# 闪电科技：封包"));
        assertTrue(zhIndex.contains("ae2:guide"));
        assertFalse(zhIndex.contains("ae2ltpp:guide"));
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }

    private static String readTree(String path) throws IOException {
        var root = Path.of(path);
        var builder = new StringBuilder();
        try (var files = Files.walk(root)) {
            for (var file : files.filter(Files::isRegularFile).sorted().toList()) {
                builder.append(Files.readString(file)).append('\n');
            }
        }
        return builder.toString();
    }

    private static void assertItemLinksDeclaredAsPageItems(Path root) throws IOException {
        var itemLinkPattern = Pattern.compile("<ItemLink id=\"(ae2ltpp:[^\"]+_packaged_core)\"\\s*/>");
        try (var files = Files.walk(root)) {
            for (var file : files.filter(Files::isRegularFile).sorted().toList()) {
                var page = Files.readString(file);
                var matcher = itemLinkPattern.matcher(page);
                while (matcher.find()) {
                    var itemId = matcher.group(1);
                    assertTrue(page.contains("item_ids:"), file + " links " + itemId);
                    assertTrue(page.contains("- " + itemId), file + " links " + itemId);
                }
            }
        }
    }
}
