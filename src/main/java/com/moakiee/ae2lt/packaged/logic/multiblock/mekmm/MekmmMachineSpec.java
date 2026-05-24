package com.moakiee.ae2lt.packaged.logic.multiblock.mekmm;

import java.util.List;
import java.util.function.ToIntFunction;

import org.jetbrains.annotations.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Declarative description of a Mekanism multiblock controller for the
 * unified {@link MekanismMoreMachinesAdapter}.
 *
 * <p>One spec covers all behaviour that previously required a hand-written
 * adapter per machine: which block id is the controller, which tile class
 * to identity-check, whether dispatch is gated on the machine being idle,
 * and the input/output port layouts relative to the controller's facing.
 *
 * <p>Most machines run in a single mode (one {@code PortGroup}). Machines
 * with selectable internal modes (e.g. the Rotary Condensentrator's
 * condensentrating vs. decondensentrating mode) supply multiple groups and
 * a {@code modeSelector} mapping a live block entity to an index into the
 * lists.
 */
public record MekmmMachineSpec(
        ResourceLocation block,
        String tileClass,
        boolean idleGated,
        List<PortGroup> modes,
        @Nullable ToIntFunction<BlockEntity> modeSelector
) {

    public PortGroup activeMode(BlockEntity be) {
        int idx = modeSelector == null ? 0 : modeSelector.applyAsInt(be);
        if (idx < 0 || idx >= modes.size()) idx = 0;
        return modes.get(idx);
    }

    public enum PortType { ITEM, FLUID, CHEMICAL }

    public record PortDef(MekPortSpec spec, PortType type) {}

    public record PortGroup(List<PortDef> inputs, List<PortDef> outputs) {}
}
