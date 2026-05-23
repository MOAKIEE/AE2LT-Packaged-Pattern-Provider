package com.moakiee.ae2lt.packaged.logic.multiblock.binding;

import java.util.List;

/**
 * Cached, immutable resolution for a single pattern slot.
 *
 * <p>{@code candidates} is sorted by adapter priority and discovery order. An empty
 * list means the pattern is currently unmatchable (UNMATCHED state); the table
 * caches this negative result to avoid re-running searches on every push.
 *
 * <p>{@code computedAtTick} lets the table apply an inexpensive TTL check on top of
 * the targeted invalidation hooks (neighbor / connection / recipe-reload).
 */
public record PatternBinding(List<LaneCandidate> candidates, long computedAtTick) {
    public PatternBinding {
        candidates = List.copyOf(candidates);
    }

    public boolean isMatched() {
        return !candidates.isEmpty();
    }

    public static PatternBinding unmatched(long tick) {
        return new PatternBinding(List.of(), tick);
    }
}
