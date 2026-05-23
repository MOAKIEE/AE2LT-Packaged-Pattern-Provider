package com.moakiee.ae2lt.packaged.logic.multiblock.mekmm;

record MekPortSpec(int backSteps, int leftSteps, int y, RelativeAccess access) {

    enum RelativeAccess {
        FRONT,
        BACK,
        LEFT,
        RIGHT,
        TOP,
        BOTTOM
    }
}
