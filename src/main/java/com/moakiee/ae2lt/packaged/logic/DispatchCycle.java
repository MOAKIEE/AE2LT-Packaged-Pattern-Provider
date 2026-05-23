package com.moakiee.ae2lt.packaged.logic;

import java.util.function.Function;

final class DispatchCycle {
    private DispatchCycle() {
    }

    static <C, T> DispatchResult<T> tryCandidates(
            Iterable<C> candidates,
            Function<C, DispatchResult<T>> attempt,
            String noTargetReason) {
        DispatchResult<T> lastFailure = null;
        for (var candidate : candidates) {
            var result = attempt.apply(candidate);
            if (result.success()) {
                return result;
            }
            if (result.failure()) {
                lastFailure = result;
            }
        }
        return lastFailure != null ? lastFailure : DispatchResult.failure(noTargetReason);
    }
}
