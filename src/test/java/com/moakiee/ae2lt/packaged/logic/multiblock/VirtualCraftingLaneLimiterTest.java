package com.moakiee.ae2lt.packaged.logic.multiblock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Set;

import org.junit.jupiter.api.Test;

class VirtualCraftingLaneLimiterTest {

    @Test
    void limitsEachLanePerTickAndResetsNextTick() {
        var limiter = new VirtualCraftingLaneLimiter<String>(new HashMap<>(), 2);

        assertTrue(limiter.tryAcquire("table_a", 100));
        assertTrue(limiter.tryAcquire("table_a", 100));
        assertFalse(limiter.tryAcquire("table_a", 100));

        assertTrue(limiter.tryAcquire("table_b", 100));
        assertTrue(limiter.tryAcquire("table_a", 101));
    }

    @Test
    void removesDisconnectedLanes() {
        var state = new HashMap<String, VirtualCraftingLaneLimiter.LaneUse>();
        var limiter = new VirtualCraftingLaneLimiter<String>(state, 1);

        assertTrue(limiter.tryAcquire("table_a", 100));
        assertTrue(limiter.tryAcquire("table_b", 100));

        limiter.retainAll(Set.of("table_b"));

        assertFalse(state.containsKey("table_a"));
        assertTrue(state.containsKey("table_b"));
    }
}
