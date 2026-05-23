package com.moakiee.ae2lt.packaged.logic.multiblock.binding;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-lane retry schedule for real-dispatch failures.
 *
 * <p>The retry offsets are absolute deltas from the lane's first failure tick:
 * {@code 1, 2, 3, 4, 5, 8, 10, 20, 40} then every {@code 40} ticks thereafter.
 * A successful push clears the lane entirely (next attempt is allowed
 * immediately).
 *
 * <p>This intentionally does NOT do the binary-search self-tuning the AE2LT
 * wireless cooldown does. Packaged providers prefer fast retry windows because
 * the bound recipe already validated the structure; failures are short-lived
 * (machine briefly busy / output slot full) rather than systemic.
 */
public final class LaneCooldownTable {

    private static final int[] RETRY_SCHEDULE = {1, 2, 3, 4, 5, 8, 10, 20, 40};
    private static final int TAIL_INTERVAL = 40;

    private static final class Entry {
        long firstFailureTick;
        int failureCount;
        long nextAllowedTick;
    }

    private final Map<LaneKey, Entry> entries = new HashMap<>();

    /** True if this lane can attempt a real dispatch on {@code gameTick}. */
    public boolean isReady(LaneKey lane, long gameTick) {
        var entry = entries.get(lane);
        return entry == null || gameTick >= entry.nextAllowedTick;
    }

    /** Mark a real-dispatch success; clears the lane's failure history. */
    public void recordSuccess(LaneKey lane) {
        entries.remove(lane);
    }

    /** Mark a real-dispatch failure and compute the next allowed tick. */
    public void recordFailure(LaneKey lane, long gameTick) {
        var entry = entries.computeIfAbsent(lane, k -> new Entry());
        if (entry.failureCount == 0) {
            entry.firstFailureTick = gameTick;
        }
        entry.failureCount++;
        entry.nextAllowedTick = computeNextAllowedTick(entry.firstFailureTick, entry.failureCount);
    }

    /** Drop bookkeeping for lanes that no longer exist (e.g. wireless conn removed). */
    public void retainAll(Collection<LaneKey> activeLanes) {
        entries.keySet().retainAll(activeLanes);
    }

    public void clear() {
        entries.clear();
    }

    static long computeNextAllowedTick(long firstFailureTick, int failureCount) {
        if (failureCount <= 0) {
            return firstFailureTick;
        }
        if (failureCount <= RETRY_SCHEDULE.length) {
            return firstFailureTick + RETRY_SCHEDULE[failureCount - 1];
        }
        int extra = failureCount - RETRY_SCHEDULE.length;
        return firstFailureTick + (long) TAIL_INTERVAL + (long) extra * TAIL_INTERVAL;
    }
}
