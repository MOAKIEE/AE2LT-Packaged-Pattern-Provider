package com.moakiee.ae2lt.packaged.logic.multiblock.mekmm;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

final class MekPortLayout {

    record PortInfo(BlockPos pos, Direction accessSide) {}

    private MekPortLayout() {}

    private static Direction left(Direction facing) {
        return facing.getClockWise();
    }

    private static Direction right(Direction facing) {
        return facing.getCounterClockWise();
    }

    private static Direction back(Direction facing) {
        return facing.getOpposite();
    }

    private static BlockPos offset(BlockPos main, Direction facing, int backSteps, int leftSteps, int y) {
        Direction b = back(facing);
        Direction l = left(facing);
        return main.offset(b.getStepX() * backSteps + l.getStepX() * leftSteps,
                y,
                b.getStepZ() * backSteps + l.getStepZ() * leftSteps);
    }

    private static Direction accessSide(Direction facing, MekPortSpec.RelativeAccess access) {
        return switch (access) {
            case FRONT -> facing;
            case BACK -> back(facing);
            case LEFT -> left(facing);
            case RIGHT -> right(facing);
            case TOP -> Direction.UP;
            case BOTTOM -> Direction.DOWN;
        };
    }

    private static PortInfo port(BlockPos main, Direction facing, MekPortSpec spec) {
        return new PortInfo(offset(main, facing, spec.backSteps(), spec.leftSteps(), spec.y()),
                accessSide(facing, spec.access()));
    }

    // === Large Antiprotonic Nucleosynthesizer (3×3×3) ===

    static PortInfo nucleosynthesizerChemicalInput(BlockPos main, Direction facing) {
        return new PortInfo(offset(main, facing, 1, 1, 2), back(facing));
    }

    static PortInfo nucleosynthesizerItemInput(BlockPos main, Direction facing) {
        return new PortInfo(offset(main, facing, 1, 0, 0), back(facing));
    }

    static PortInfo nucleosynthesizerItemOutput(BlockPos main, Direction facing) {
        return new PortInfo(offset(main, facing, 1, 0, 2), back(facing));
    }

    // === Large Chemical Infuser (3×3×2+top) ===
    // Chemical input left: back-left y=0, accessed from back or left
    static PortInfo chemicalInfuserLeftInput(BlockPos main, Direction facing) {
        return new PortInfo(offset(main, facing, 1, 1, 0), left(facing));
    }

    // Chemical input right: back-right y=0, accessed from back or right
    static PortInfo chemicalInfuserRightInput(BlockPos main, Direction facing) {
        return new PortInfo(offset(main, facing, 1, -1, 0), right(facing));
    }

    // Chemical output: front-left y=0, accessed from front
    static PortInfo chemicalInfuserOutput(BlockPos main, Direction facing) {
        return new PortInfo(offset(main, facing, -1, 1, 0), facing);
    }

    // === Large Electrolytic Separator (3×3×2) ===
    // Fluid input: back-left y=0, accessed from back or left
    static PortInfo electrolyticSeparatorFluidInput(BlockPos main, Direction facing) {
        return new PortInfo(offset(main, facing, 1, 1, 0), back(facing));
    }

    // Chemical output left: front-left y=0, accessed from front
    static PortInfo electrolyticSeparatorLeftOutput(BlockPos main, Direction facing) {
        return new PortInfo(offset(main, facing, -1, 1, 0), facing);
    }

    // Chemical output right: front-right y=0, accessed from front
    static PortInfo electrolyticSeparatorRightOutput(BlockPos main, Direction facing) {
        return new PortInfo(offset(main, facing, -1, -1, 0), facing);
    }

    // === Large Rotary Condensentrator (3×3×3) ===
    // Chemical input: back-left y=0, accessed from back
    static PortInfo rotaryCondensentratorChemicalInput(BlockPos main, Direction facing) {
        return port(main, facing, MekPortSpecs.rotaryChemicalInput());
    }

    // Chemical output: left y=1, accessed from left
    static PortInfo rotaryCondensentratorChemicalOutput(BlockPos main, Direction facing) {
        return port(main, facing, MekPortSpecs.rotaryChemicalOutput());
    }

    // Fluid input: back-right y=0, accessed from back
    static PortInfo rotaryCondensentratorFluidInput(BlockPos main, Direction facing) {
        return port(main, facing, MekPortSpecs.rotaryFluidInput());
    }

    // Fluid output: right y=1, accessed from right
    static PortInfo rotaryCondensentratorFluidOutput(BlockPos main, Direction facing) {
        return port(main, facing, MekPortSpecs.rotaryFluidOutput());
    }

    // === Large Solar Neutron Activator (3×3×3) ===
    // Chemical input: back-left y=0, accessed from left
    static PortInfo solarNeutronActivatorInput(BlockPos main, Direction facing) {
        return port(main, facing, MekPortSpecs.solarInput());
    }

    // Chemical output: back-right y=0, accessed from back or right
    static PortInfo solarNeutronActivatorOutput(BlockPos main, Direction facing) {
        return port(main, facing, MekPortSpecs.solarOutput());
    }

    // === Large Pigment Mixer (3×3×2+2) ===
    // Pigment input left: back-left y=0, accessed from left
    static PortInfo pigmentMixerLeftInput(BlockPos main, Direction facing) {
        return new PortInfo(offset(main, facing, 1, 1, 0), left(facing));
    }

    // Pigment input right: back-right y=0, accessed from right
    static PortInfo pigmentMixerRightInput(BlockPos main, Direction facing) {
        return new PortInfo(offset(main, facing, 1, -1, 0), right(facing));
    }

    // Pigment output: front-left y=0, accessed from front
    static PortInfo pigmentMixerOutput(BlockPos main, Direction facing) {
        return new PortInfo(offset(main, facing, -1, 1, 0), facing);
    }
}
