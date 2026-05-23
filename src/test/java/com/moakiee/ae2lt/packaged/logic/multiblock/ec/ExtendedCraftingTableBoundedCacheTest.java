package com.moakiee.ae2lt.packaged.logic.multiblock.ec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class ExtendedCraftingTableBoundedCacheTest {

    @Test
    void evictsLeastRecentlyUsedEntry() {
        var cache = new ExtendedCraftingTableBoundedCache<String, String>(2);

        cache.put("a", "first");
        cache.put("b", "second");
        assertEquals("first", cache.get("a"));

        cache.put("c", "third");

        assertEquals("first", cache.get("a"));
        assertNull(cache.get("b"));
        assertEquals("third", cache.get("c"));
    }
}
