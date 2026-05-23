package com.moakiee.ae2lt.packaged.logic.multiblock.binding;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import appeng.api.crafting.IPatternDetails;

/**
 * Lazy cache of {@link PatternBinding} for each pattern slot.
 *
 * <p>Bindings are computed on the first push that needs them (lazy bind) and
 * invalidated as a unit when any structural premise changes:
 * <ul>
 *   <li>pattern slot edited &mdash; {@link #invalidateAll()} from {@code updatePatterns}</li>
 *   <li>neighbor block changed &mdash; {@link #invalidateAll()} from {@code onNeighborChanged}</li>
 *   <li>wireless connection list changed &mdash; {@link #invalidateAll()} from {@code onHostStateChanged}</li>
 *   <li>recipe table reloaded &mdash; {@link #invalidateAll()} from {@code RecipesUpdatedEvent}</li>
 * </ul>
 *
 * <p>The cache stores {@code PatternBinding} objects (which may be empty
 * &laquo;UNMATCHED&raquo; instances) so negative lookups are also cached and
 * not retried until invalidation.
 *
 * <p>Keyed by pattern identity (the {@code IPatternDetails} returned by
 * {@code PatternDetailsHelper.decodePattern}) rather than equality, because
 * {@code updatePatterns} rebuilds the pattern list wholesale and replaces every
 * instance &mdash; identity matches the cache invalidation lifecycle exactly.
 */
public final class PatternBindingTable {

    private final Map<IPatternDetails, PatternBinding> bindings = new IdentityHashMap<>();

    @Nullable
    public PatternBinding get(IPatternDetails pattern) {
        return bindings.get(pattern);
    }

    public void put(IPatternDetails pattern, PatternBinding binding) {
        bindings.put(pattern, binding);
    }

    public void invalidate(IPatternDetails pattern) {
        bindings.remove(pattern);
    }

    public void invalidateAll() {
        bindings.clear();
    }

    public int size() {
        return bindings.size();
    }

    /** Snapshot for debug; not part of the hot path. */
    public Map<IPatternDetails, PatternBinding> snapshot() {
        return new HashMap<>(bindings);
    }
}
