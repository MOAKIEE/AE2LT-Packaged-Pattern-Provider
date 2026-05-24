package com.moakiee.ae2lt.packaged.logic.multiblock.mekmm;

import static com.moakiee.ae2lt.packaged.logic.multiblock.mekmm.MekPortSpec.RelativeAccess.BACK;
import static com.moakiee.ae2lt.packaged.logic.multiblock.mekmm.MekPortSpec.RelativeAccess.LEFT;
import static com.moakiee.ae2lt.packaged.logic.multiblock.mekmm.MekPortSpec.RelativeAccess.RIGHT;

final class MekPortSpecs {

    private MekPortSpecs() {}

    static MekPortSpec rotaryChemicalInput() {
        return new MekPortSpec(1, 1, 0, BACK);
    }

    static MekPortSpec rotaryChemicalOutput() {
        return new MekPortSpec(0, 1, 1, LEFT);
    }

    static MekPortSpec rotaryFluidInput() {
        return new MekPortSpec(1, -1, 0, BACK);
    }

    static MekPortSpec rotaryFluidOutput() {
        return new MekPortSpec(0, -1, 1, RIGHT);
    }

    static MekPortSpec solarInput() {
        return new MekPortSpec(1, 1, 0, LEFT);
    }

    static MekPortSpec solarOutput() {
        return new MekPortSpec(1, -1, 0, BACK);
    }
}
