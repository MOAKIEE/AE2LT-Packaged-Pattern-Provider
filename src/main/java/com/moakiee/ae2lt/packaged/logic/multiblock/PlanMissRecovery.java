package com.moakiee.ae2lt.packaged.logic.multiblock;

public final class PlanMissRecovery {

    private PlanMissRecovery() {
    }

    public static boolean shouldRetryAfterAutoReturn(boolean shouldBackoffPlanMiss, boolean extractedOutputs) {
        return !shouldBackoffPlanMiss && extractedOutputs;
    }
}
