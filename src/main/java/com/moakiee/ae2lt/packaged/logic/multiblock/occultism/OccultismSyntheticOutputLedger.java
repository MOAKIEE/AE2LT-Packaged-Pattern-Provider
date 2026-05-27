package com.moakiee.ae2lt.packaged.logic.multiblock.occultism;

import java.util.Optional;

final class OccultismSyntheticOutputLedger {

    private static final String OCCULTISM_PREFIX = "occultism:";
    private static final String RITUAL_DUMMY_PATH = "ritual_dummy/";
    private static final String JEI_DUMMY_NONE = "occultism:jei_dummy_none";

    private OccultismSyntheticOutputLedger() {
    }

    static Optional<SyntheticCandidate> primaryRealOutput(SyntheticCandidate output) {
        if (output.amount() > 0
                && !output.spawnEgg()
                && !isDisplayOnlyOutput(output.itemId())) {
            return Optional.of(output);
        }
        return Optional.empty();
    }

    static boolean isDisplayOnlyOutput(String itemId) {
        return itemId.equals(JEI_DUMMY_NONE)
                || itemId.startsWith(OCCULTISM_PREFIX + RITUAL_DUMMY_PATH);
    }

    static long remainingAfterPhysicalExtraction(String syntheticItemId, long syntheticAmount,
                                                 String physicalItemId, long physicalAmount) {
        if (syntheticAmount <= 0) {
            return 0;
        }
        if (!syntheticItemId.equals(physicalItemId)) {
            return syntheticAmount;
        }
        return Math.max(0, syntheticAmount - Math.max(0, physicalAmount));
    }

    record SyntheticCandidate(String itemId, long amount, boolean spawnEgg) {
    }
}
