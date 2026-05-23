package com.moakiee.ae2lt.packaged.logic.multiblock.ec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class ExtendedCraftingTablePlanCacheTest {

    @Test
    void reusesNullAndNonNullResultsWithinSameTick() {
        var cache = new ExtendedCraftingTablePlanCache<String, String>();
        var calls = new AtomicInteger();

        assertNull(cache.getOrCompute(10, "same", () -> {
            calls.incrementAndGet();
            return null;
        }));
        assertNull(cache.getOrCompute(10, "same", () -> {
            calls.incrementAndGet();
            return "unexpected";
        }));

        assertEquals("planned", cache.getOrCompute(11, "same", () -> {
            calls.incrementAndGet();
            return "planned";
        }));
        assertEquals("planned", cache.getOrCompute(11, "same", () -> {
            calls.incrementAndGet();
            return "unexpected";
        }));
        assertEquals(2, calls.get());
    }
}
