package com.moakiee.ae2lt.packaged.logic.multiblock.mekmm;

final class MekItemInsertion {

    private MekItemInsertion() {}

    interface SlotAccess<S> {
        int slots();

        S insert(int slot, S stack, boolean simulate);

        int amount(S stack);
    }

    static <S> long insertIntoAnySlot(S stack, boolean simulate, SlotAccess<S> access) {
        int originalAmount = access.amount(stack);
        if (originalAmount <= 0) return 0;

        S remainder = stack;
        for (int slot = 0; slot < access.slots() && access.amount(remainder) > 0; slot++) {
            remainder = access.insert(slot, remainder, simulate);
            if (remainder == null) return originalAmount;
        }

        return originalAmount - Math.max(0, access.amount(remainder));
    }
}
