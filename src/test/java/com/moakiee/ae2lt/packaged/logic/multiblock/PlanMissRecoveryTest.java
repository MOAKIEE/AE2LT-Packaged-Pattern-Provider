package com.moakiee.ae2lt.packaged.logic.multiblock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PlanMissRecoveryTest {

    @Test
    void retriesOnlyWhenBusyTargetReturnedOutputs() {
        assertTrue(PlanMissRecovery.shouldRetryAfterAutoReturn(false, true));
        assertFalse(PlanMissRecovery.shouldRetryAfterAutoReturn(false, false));
        assertFalse(PlanMissRecovery.shouldRetryAfterAutoReturn(true, true));
    }
}
