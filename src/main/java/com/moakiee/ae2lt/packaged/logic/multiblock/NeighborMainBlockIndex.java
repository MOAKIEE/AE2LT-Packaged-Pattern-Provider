package com.moakiee.ae2lt.packaged.logic.multiblock;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

public final class NeighborMainBlockIndex {

    private static final int TTL_TICKS = 200;

    private final BlockPos origin;
    private final EnumMap<Direction, MultiblockAdapter> cache = new EnumMap<>(Direction.class);
    private long lastScanTick = -1;
    private boolean dirty = true;

    public NeighborMainBlockIndex(BlockPos origin) {
        this.origin = origin;
    }

    public void invalidate() {
        dirty = true;
    }

    public List<Direction> adapterFaces(ServerLevel level) {
        long tick = level.getGameTime();
        if (dirty || tick - lastScanTick >= TTL_TICKS) {
            rebuild(level, tick);
        }
        return new ArrayList<>(cache.keySet());
    }

    @Nullable
    public MultiblockAdapter getAdapter(Direction face) {
        return cache.get(face);
    }

    private void rebuild(ServerLevel level, long tick) {
        cache.clear();
        for (var dir : Direction.values()) {
            var pos = origin.relative(dir);
            if (!level.isLoaded(pos)) {
                continue;
            }
            // BlockEntity may be null for block-only targets. Adapters that
            // need a BE must filter for it themselves.
            var be = level.getBlockEntity(pos);
            var adapter = MultiblockAdapterRegistry.find(level, pos, be);
            if (adapter != null) {
                cache.put(dir, adapter);
            }
        }
        dirty = false;
        lastScanTick = tick;
    }
}
