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

import com.moakiee.ae2lt.api.pattern.PatternProviderUiProfile;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.packaged.logic.PackagedPatternProviderLogic;
import com.moakiee.ae2lt.packaged.registry.PPBlockEntities;
import com.moakiee.ae2lt.packaged.registry.PPBlocks;

public class PackagedPatternProviderBlockEntity extends OverloadedPatternProviderBlockEntity
        implements PatternProviderUiProfile {
    private final ProviderMode fixedProviderMode;

    public PackagedPatternProviderBlockEntity(BlockPos pos, BlockState blockState) {
        this(PPBlockEntities.PACKAGED_PATTERN_PROVIDER.get(), pos, blockState, ProviderMode.NORMAL);
    }

    protected PackagedPatternProviderBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState blockState,
                                                ProviderMode fixedProviderMode) {
        super(type, pos, blockState);
        this.fixedProviderMode = fixedProviderMode;
        sanitizePackagedModes();
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
