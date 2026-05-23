package com.moakiee.ae2lt.packaged.blockentity;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.stacks.AEItemKey;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.util.SettingsFrom;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;

import com.moakiee.ae2lt.api.pattern.PatternProviderUiProfile;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.item.MultiblockAdapterItem;
import com.moakiee.ae2lt.packaged.logic.PackagedPatternProviderLogic;
import com.moakiee.ae2lt.packaged.registry.PPBlockEntities;
import com.moakiee.ae2lt.packaged.registry.PPBlocks;

public class PackagedPatternProviderBlockEntity extends OverloadedPatternProviderBlockEntity
        implements PatternProviderUiProfile {

    private static final String TAG_ADAPTER_INV = "ae2ltpp_adapter_inv";

    private final ProviderMode fixedProviderMode;

    /**
     * Single-slot inventory holding the {@link MultiblockAdapterItem} key card
     * that unlocks this provider's machine family. Empty slot = no adapter
     * installed = nothing matches in {@link PackagedPatternProviderLogic}'s
     * binding stage.
     *
     * <p>Mirrors AE2LT's {@code OverloadedInterface.filterInv} pattern: a
     * dedicated {@link AppEngInternalInventory} with a per-slot type filter
     * and an inventory listener that re-runs the binding pipeline on change.
     */
    private final InternalInventoryHost adapterInvHost = new InternalInventoryHost() {
        @Override
        public void saveChangedInventory(AppEngInternalInventory inv) {
            saveChanges();
            markForUpdate();
        }

        @Override
        public void onChangeInventory(AppEngInternalInventory inv, int slot) {
            onAdapterInventoryChanged();
        }

        @Override
        public boolean isClientSide() {
            return level != null && level.isClientSide();
        }
    };

    private final AppEngInternalInventory adapterInv = new AppEngInternalInventory(adapterInvHost, 1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return !stack.isEmpty() && stack.getItem() instanceof MultiblockAdapterItem;
        }
    };

    public PackagedPatternProviderBlockEntity(BlockPos pos, BlockState blockState) {
        this(PPBlockEntities.PACKAGED_PATTERN_PROVIDER.get(), pos, blockState, ProviderMode.NORMAL);
    }

    protected PackagedPatternProviderBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState,
                                                ProviderMode fixedProviderMode) {
        super(type, pos, blockState);
        this.fixedProviderMode = fixedProviderMode;
        sanitizePackagedModes();
    }

    public AppEngInternalInventory getAdapterInv() {
        return adapterInv;
    }

    /**
     * Convenience: the single key card currently installed, or
     * {@link ItemStack#EMPTY} when the slot is empty.
     */
    public ItemStack getInstalledAdapterStack() {
        return adapterInv.getStackInSlot(0);
    }

    /**
     * Notify the dispatch logic that bindings must be re-evaluated because
     * the player swapped the key card. Safely called from the inventory host
     * before logic is fully ready (early load phase).
     */
    private void onAdapterInventoryChanged() {
        var logic = getLogic();
        if (logic instanceof PackagedPatternProviderLogic packaged) {
            packaged.onAdapterSlotChanged();
        }
    }

    @Override
    protected PatternProviderLogic createLogic() {
        return new PackagedPatternProviderLogic(this.getMainNode(), this, getTotalPatternCapacity());
    }

    @Override
    public ProviderMode getProviderMode() {
        return fixedProviderMode();
    }

    @Override
    public void setProviderMode(ProviderMode providerMode) {
        super.setProviderMode(fixedProviderMode());
    }

    @Override
    public void setReturnMode(ReturnMode mode) {
        super.setReturnMode(mode == ReturnMode.EJECT ? ReturnMode.OFF : mode);
    }

    @Override
    public ReturnMode getReturnMode() {
        var mode = super.getReturnMode();
        return mode == ReturnMode.EJECT ? ReturnMode.OFF : mode;
    }

    @Override
    public boolean isAutoReturn() {
        return getReturnMode() == ReturnMode.AUTO;
    }

    @Override
    public void setWirelessSpeedMode(WirelessSpeedMode wirelessSpeedMode) {
        super.setWirelessSpeedMode(WirelessSpeedMode.NORMAL);
    }

    @Override
    public WirelessSpeedMode getWirelessSpeedMode() {
        return WirelessSpeedMode.NORMAL;
    }

    @Override
    public void setFilteredImport(boolean filteredImport) {
        super.setFilteredImport(false);
    }

    @Override
    public boolean isFilteredImport() {
        return false;
    }

    @Override
    public boolean ae2lt$isPackagedProviderUi() {
        return true;
    }

    @Override
    public boolean ae2lt$isModeSwitchVisible() {
        return false;
    }

    @Override
    public boolean ae2lt$isFilteredImportVisible() {
        return false;
    }

    @Override
    public boolean ae2lt$isWirelessTuningVisible() {
        return false;
    }

    @Override
    public boolean ae2lt$isBlockingModeVisible() {
        return false;
    }

    @Override
    public String ae2lt$titleTranslationKey() {
        return "ae2ltpp.gui.title.packaged_pattern_provider";
    }

    @Override
    public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        super.loadTag(data, registries);
        sanitizePackagedModes();
        if (data.contains(TAG_ADAPTER_INV)) {
            adapterInv.readFromNBT(data, TAG_ADAPTER_INV, registries);
        }
    }

    @Override
    public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        adapterInv.writeToNBT(data, TAG_ADAPTER_INV, registries);
    }

    @Override
    public void addAdditionalDrops(net.minecraft.world.level.Level level,
                                    BlockPos pos,
                                    java.util.List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        var card = adapterInv.getStackInSlot(0);
        if (!card.isEmpty()) {
            drops.add(card.copy());
            adapterInv.setItemDirect(0, ItemStack.EMPTY);
        }
    }

    @Override
    public void importSettings(SettingsFrom mode, DataComponentMap input, @Nullable Player player) {
        super.importSettings(mode, input, player);
        sanitizePackagedModes();
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return new ItemStack(PPBlocks.PACKAGED_PATTERN_PROVIDER.get());
    }

    @Override
    public AEItemKey getTerminalIcon() {
        return AEItemKey.of(PPBlocks.PACKAGED_PATTERN_PROVIDER.get());
    }

    @Override
    protected Item getItemFromBlockEntity() {
        return PPBlocks.PACKAGED_PATTERN_PROVIDER.get().asItem();
    }

    protected ProviderMode fixedProviderMode() {
        return fixedProviderMode == null ? ProviderMode.NORMAL : fixedProviderMode;
    }

    private void sanitizePackagedModes() {
        super.setProviderMode(fixedProviderMode());
        if (super.getReturnMode() == ReturnMode.EJECT) {
            super.setReturnMode(ReturnMode.OFF);
        }
        super.setWirelessSpeedMode(WirelessSpeedMode.NORMAL);
        super.setFilteredImport(false);
    }
}
