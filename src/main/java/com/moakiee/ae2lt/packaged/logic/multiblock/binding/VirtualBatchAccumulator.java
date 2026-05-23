package com.moakiee.ae2lt.packaged.logic.multiblock.binding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

/**
 * Batches virtual-craft outputs and flushes them on a fixed interval (default
 * every 10 ticks).
 *
 * <p>Outputs are merged by {@link AEKey} inside the pending buffer so each
 * pattern only contributes one entry per key per flush window, keeping the
 * inserted stack count bounded even when push frequency is very high.
 *
 * <p>The class itself enforces no upper bound on accumulated amount; the
 * outer push path is expected to apply a per-tick admission cap (e.g. 64
 * outputs per lane per tick) before calling {@link #enqueue}.
 */
public final class VirtualBatchAccumulator {

    public static final int FLUSH_INTERVAL_TICKS = 10;

    private final Map<AEKey, Long> pending = new LinkedHashMap<>();
    private long lastFlushTick = Long.MIN_VALUE;

    /** Merge {@code outputs} into the pending buffer keyed by {@link AEKey}. */
    public void enqueue(List<GenericStack> outputs) {
        if (outputs.isEmpty()) {
            return;
        }
        for (var stack : outputs) {
            if (stack.amount() <= 0) {
                continue;
            }
            pending.merge(stack.what(), stack.amount(), Long::sum);
        }
    }

    /**
     * Flush every {@link #FLUSH_INTERVAL_TICKS} ticks. The {@code sink} receives
     * a fresh {@link List} of merged stacks; the internal buffer is cleared on
     * each successful flush.
     *
     * @return true when a flush actually happened on this call
     */
    public boolean tickFlush(long gameTick, Consumer<List<GenericStack>> sink) {
        if (lastFlushTick == Long.MIN_VALUE) {
            lastFlushTick = gameTick;
            return false;
        }
        if (gameTick - lastFlushTick < FLUSH_INTERVAL_TICKS) {
            return false;
        }
        lastFlushTick = gameTick;
        if (pending.isEmpty()) {
            return true;
        }
        var merged = new ArrayList<GenericStack>(pending.size());
        for (var entry : pending.entrySet()) {
            merged.add(new GenericStack(entry.getKey(), entry.getValue()));
        }
        pending.clear();
        sink.accept(merged);
        return true;
    }

    /** Force flush regardless of interval; used on host removal / clearContent. */
    public List<GenericStack> drainAll() {
        if (pending.isEmpty()) {
            return List.of();
        }
        var merged = new ArrayList<GenericStack>(pending.size());
        for (var entry : pending.entrySet()) {
            merged.add(new GenericStack(entry.getKey(), entry.getValue()));
        }
        pending.clear();
        return merged;
    }

    public boolean hasPending() {
        return !pending.isEmpty();
    }

    public void clear() {
        pending.clear();
        lastFlushTick = Long.MIN_VALUE;
    }

    /** Snapshot for debug / inspection. Not part of the steady-state hot path. */
    public Map<AEKey, Long> snapshot() {
        return new HashMap<>(pending);
    }
}
