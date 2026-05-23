package com.moakiee.ae2lt.packaged.logic.multiblock.binding;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple per-lane per-tick admission limiter.
 *
 * <p>Used by {@code pushPattern} to cap how many virtual crafts a single lane
 * may accept inside one game tick. Set to {@code 64} by the packaged provider
 * so a saturated AE crafting CPU still cannot wedge the server with a single
 * pattern.
 */
public final class LaneRateLimiter {

    private record Use(long gameTick, int count) {}

    private final Map<LaneKey, Use> uses = new HashMap<>();
    private final int capacityPerTick;

    public LaneRateLimiter(int capacityPerTick) {
        if (capacityPerTick <= 0) {
            throw new IllegalArgumentException("capacityPerTick must be positive");
        }
        this.capacityPerTick = capacityPerTick;
    }

    public boolean hasCapacity(LaneKey lane, long gameTick) {
        var use = uses.get(lane);
        return use == null || use.gameTick != gameTick || use.count < capacityPerTick;
    }

    public boolean tryAcquire(LaneKey lane, long gameTick) {
        var use = uses.get(lane);
        if (use == null || use.gameTick != gameTick) {
            uses.put(lane, new Use(gameTick, 1));
            return true;
        }
        if (use.count >= capacityPerTick) {
            return false;
        }
        uses.put(lane, new Use(gameTick, use.count + 1));
        return true;
    }

    public void retainAll(Collection<LaneKey> activeLanes) {
        uses.keySet().retainAll(activeLanes);
    }

    public void clear() {
        uses.clear();
    }
}
