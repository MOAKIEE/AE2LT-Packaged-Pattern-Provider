package com.moakiee.ae2lt.packaged.menu;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

import appeng.api.inventories.InternalInventory;
import appeng.client.gui.Icon;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.slot.AppEngSlot;

import com.moakiee.ae2lt.menu.OverloadedPatternProviderMenu;
import com.moakiee.ae2lt.packaged.AE2LTPackagedProvider;
import com.moakiee.ae2lt.packaged.blockentity.PackagedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.item.MultiblockAdapterItem;

public class PackagedPatternProviderMenu extends OverloadedPatternProviderMenu {
    public static final MenuType<PackagedPatternProviderMenu> TYPE = MenuTypeBuilder
            .create(PackagedPatternProviderMenu::new, PatternProviderLogicHost.class)
            .buildUnregistered(ResourceLocation.fromNamespaceAndPath(
                    AE2LTPackagedProvider.MODID, "packaged_pattern_provider"));

    public PackagedPatternProviderMenu(int id, Inventory playerInventory, PatternProviderLogicHost host) {
        super(TYPE, id, playerInventory, host);

        if (!(host instanceof PackagedPatternProviderBlockEntity packagedHost)) {
            throw new IllegalArgumentException("Packaged provider menu opened for non-packaged host: " + host);
        }

        var adapterSlot = new AdapterCardSlot(packagedHost.getAdapterInv(), 0);
        adapterSlot.setEmptyTooltip(() -> List.of(Component.translatable("ae2ltpp.gui.adapter_slot")));
        addSlot(adapterSlot, PackagedPatternProviderSlotSemantics.ADAPTER_CARD);
    }

    private static final class AdapterCardSlot extends AppEngSlot {
        private AdapterCardSlot(InternalInventory inv, int invSlot) {
            super(inv, invSlot);
            setIcon(Icon.BACKGROUND_UPGRADE);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof MultiblockAdapterItem && super.mayPlace(stack);
        }
    }
}
