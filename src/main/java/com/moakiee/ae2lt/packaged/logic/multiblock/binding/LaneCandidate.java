package com.moakiee.ae2lt.packaged.logic.multiblock.binding;

import java.util.Objects;

import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapter;

/**
 * One viable (lane, adapter, opaque-handle, mode) triple matched at pattern-bind time.
 *
 * <p>A pattern may have multiple candidates when several adjacent faces or wireless
 * connections expose compatible multiblocks; virtual candidates are filled in parallel
 * while real candidates are tried in priority order with per-lane cooldown.
 */
public record LaneCandidate(LaneKey lane, MultiblockAdapter adapter, Object handle, BindingMode mode) {
    public LaneCandidate {
        Objects.requireNonNull(lane, "lane");
        Objects.requireNonNull(adapter, "adapter");
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(mode, "mode");
    }
}
