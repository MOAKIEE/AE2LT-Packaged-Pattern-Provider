package com.moakiee.ae2lt.packaged.logic.multiblock;

import java.util.List;

import appeng.api.stacks.GenericStack;

public record VirtualCraftingResult(List<GenericStack> outputs) {

    public VirtualCraftingResult {
        outputs = List.copyOf(outputs);
    }
}
