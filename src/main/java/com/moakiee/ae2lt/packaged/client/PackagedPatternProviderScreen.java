package com.moakiee.ae2lt.packaged.client;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.UpgradesPanel;

import com.moakiee.ae2lt.client.OverloadedPatternProviderScreen;
import com.moakiee.ae2lt.packaged.menu.PackagedPatternProviderMenu;

public class PackagedPatternProviderScreen extends OverloadedPatternProviderScreen<PackagedPatternProviderMenu> {

    public PackagedPatternProviderScreen(PackagedPatternProviderMenu menu, Inventory playerInventory,
                                         Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);

        var adapterSlot = menu.getAdapterSlot();
        if (adapterSlot != null) {
            widgets.add("adapterCard", new UpgradesPanel(List.of(adapterSlot), List::of));
        }
    }
}
