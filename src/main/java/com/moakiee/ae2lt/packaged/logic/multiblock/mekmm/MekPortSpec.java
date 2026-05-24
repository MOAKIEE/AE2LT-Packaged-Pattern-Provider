package com.moakiee.ae2lt.packaged.logic.multiblock.mekmm;

public record MekPortSpec(int backSteps, int leftSteps, int y, RelativeAccess access) {

    public enum RelativeAccess {
        FRONT,
        BACK,
        LEFT,
        RIGHT,
        TOP,
        BOTTOM
    }
}
