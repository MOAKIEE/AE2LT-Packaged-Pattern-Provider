package com.moakiee.ae2lt.packaged.logic.multiblock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class MultiblockAdapterRegistry {

    private static final List<MultiblockAdapter> ADAPTERS = new ArrayList<>();
    private static boolean sorted = true;

    private MultiblockAdapterRegistry() {
    }

    /** Register during mod setup. Not thread-safe with concurrent lookup. */
    public static void register(MultiblockAdapter adapter) {
        ADAPTERS.add(adapter);
        sorted = false;
    }

    @Nullable
    public static MultiblockAdapter find(ServerLevel level, BlockPos pos, BlockEntity be) {
        ensureSorted();
        for (var adapter : ADAPTERS) {
            if (adapter.recognizesMain(level, pos, be)) {
                return adapter;
            }
        }
        return null;
    }

    public static List<MultiblockAdapter> getAll() {
        ensureSorted();
        return Collections.unmodifiableList(ADAPTERS);
    }

    private static void ensureSorted() {
        if (!sorted) {
            ADAPTERS.sort(Comparator.comparingInt(MultiblockAdapter::priority));
            sorted = true;
        }
    }
}
