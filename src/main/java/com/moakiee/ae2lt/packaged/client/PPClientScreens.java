package com.moakiee.ae2lt.packaged.client;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import appeng.client.gui.style.StyleManager;

import com.moakiee.ae2lt.client.OverloadedPatternProviderScreen;
import com.moakiee.ae2lt.packaged.AE2LTPackagedProvider;
import com.moakiee.ae2lt.packaged.menu.PackagedPatternProviderMenu;

@EventBusSubscriber(modid = AE2LTPackagedProvider.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PPClientScreens {
    private PPClientScreens() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(PackagedPatternProviderMenu.TYPE, PPClientScreens::createPackagedPatternProviderScreen);
    }

    private static OverloadedPatternProviderScreen<PackagedPatternProviderMenu> createPackagedPatternProviderScreen(
            PackagedPatternProviderMenu menu,
            Inventory inv,
            Component title) {
        var style = StyleManager.loadStyleDoc("/screens/packaged_pattern_provider.json");
        return new OverloadedPatternProviderScreen(menu, inv, title, style);
    }
}
