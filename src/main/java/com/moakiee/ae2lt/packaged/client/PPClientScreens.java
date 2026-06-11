package com.moakiee.ae2lt.packaged.client;

import java.util.stream.Stream;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

import appeng.client.gui.style.StyleManager;

import com.moakiee.ae2lt.packaged.AE2LTPackagedProvider;
import com.moakiee.ae2lt.packaged.item.PackagedCoreDefinition;
import com.moakiee.ae2lt.packaged.menu.PackagedPatternProviderMenu;
import com.moakiee.ae2lt.packaged.registry.PPItems;

@EventBusSubscriber(modid = AE2LTPackagedProvider.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PPClientScreens {
    private PPClientScreens() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(PackagedPatternProviderMenu.TYPE, PPClientScreens::createPackagedPatternProviderScreen);
    }

    @SubscribeEvent
    public static void registerItemExtensions(RegisterClientExtensionsEvent event) {
        var renderer = new PackagedCoreItemRenderer();
        event.registerItem(new IClientItemExtensions() {
            @Override
            public PackagedCoreItemRenderer getCustomRenderer() {
                return renderer;
            }
        }, Stream.concat(
                Stream.of((Item) PPItems.BLANK_PACKAGED_CORE.get()),
                PackagedCoreDefinition.all().stream()
                .map(definition -> (Item) definition.runtimeItem().get())
        ).toArray(Item[]::new));
    }

    private static PackagedPatternProviderScreen createPackagedPatternProviderScreen(
            PackagedPatternProviderMenu menu,
            Inventory inv,
            Component title) {
        var style = StyleManager.loadStyleDoc("/screens/packaged_pattern_provider.json");
        return new PackagedPatternProviderScreen(menu, inv, title, style);
    }
}
