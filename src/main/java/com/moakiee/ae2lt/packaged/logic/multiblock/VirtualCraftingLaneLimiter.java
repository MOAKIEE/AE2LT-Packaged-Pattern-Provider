package com.moakiee.ae2lt.packaged.logic.multiblock;

import java.util.Collection;
import java.util.Map;

public final class VirtualCraftingLaneLimiter<K> {

    private final Map<K, LaneUse> laneUses;
    private final int craftsPerTick;

    public VirtualCraftingLaneLimiter(Map<K, LaneUse> laneUses, int craftsPerTick) {
        if (craftsPerTick <= 0) {
            throw new IllegalArgumentException("craftsPerTick must be positive");
        }
        this.laneUses = laneUses;
        this.craftsPerTick = craftsPerTick;
    }

    public boolean tryAcquire(K lane, long gameTick) {
        var use = laneUses.get(lane);
        if (use == null || use.gameTick != gameTick) {
            laneUses.put(lane, new LaneUse(gameTick, 1));
            return true;
        }
        if (use.count >= craftsPerTick) {
            return false;
        }
        laneUses.put(lane, new LaneUse(gameTick, use.count + 1));
        return true;
    }

    public boolean hasCapacity(K lane, long gameTick) {
        var use = laneUses.get(lane);
        return use == null || use.gameTick != gameTick || use.count < craftsPerTick;
    }

    public void retainAll(Collection<K> activeLanes) {
        laneUses.keySet().retainAll(activeLanes);
    }

    public record LaneUse(long gameTick, int count) {
    }
}
