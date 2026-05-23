package com.moakiee.ae2lt.packaged.logic.multiblock.ec;

import java.util.Objects;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

final class ExtendedCraftingTablePlanCache<K, V> {

    private @Nullable Entry<K, V> entry;

    @Nullable
    V getOrCompute(long gameTick, K key, Supplier<@Nullable V> computer) {
        var current = entry;
        if (current != null && current.gameTick == gameTick && Objects.equals(current.key, key)) {
            return current.value;
        }

        var value = computer.get();
        entry = new Entry<>(gameTick, key, value);
        return value;
    }

    private record Entry<K, V>(long gameTick, K key, @Nullable V value) {
    }
}
