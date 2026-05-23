package com.moakiee.ae2lt.packaged.logic.multiblock.ec;

import java.util.LinkedHashMap;

import org.jetbrains.annotations.Nullable;

final class ExtendedCraftingTableBoundedCache<K, V> {

    private final int maxEntries;
    private final LinkedHashMap<K, V> entries;

    ExtendedCraftingTableBoundedCache(int maxEntries) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        this.maxEntries = maxEntries;
        this.entries = new LinkedHashMap<>(16, 0.75f, true);
    }

    @Nullable
    V get(K key) {
        return entries.get(key);
    }

    void put(K key, V value) {
        entries.put(key, value);
        if (entries.size() <= maxEntries) {
            return;
        }

        var iterator = entries.entrySet().iterator();
        if (iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    void remove(K key) {
        entries.remove(key);
    }

    int size() {
        return entries.size();
    }

}
