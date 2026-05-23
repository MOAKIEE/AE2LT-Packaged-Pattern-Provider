package com.moakiee.ae2lt.packaged.logic;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.LockCraftingMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.util.inv.filter.IAEItemFilter;

import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.ProviderMode;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.ReturnMode;
import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity.WirelessConnection;
import com.moakiee.ae2lt.item.OverloadPatternItem;
import com.moakiee.ae2lt.logic.AllowedOutputFilter;
import com.moakiee.ae2lt.logic.OverloadedPatternProviderLogic;
import com.moakiee.ae2lt.logic.SmartDoublingCompat;
import com.moakiee.ae2lt.logic.energy.PowerCostUtil;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchExecutor;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapterRegistry;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.NeighborMainBlockIndex;
import com.moakiee.ae2lt.packaged.logic.multiblock.VirtualCraftingAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.VirtualCraftingLaneLimiter;
import com.moakiee.ae2lt.packaged.logic.multiblock.VirtualCraftingResult;
import com.moakiee.ae2lt.mixin.PatternProviderLogicAccessor;

public class PackagedPatternProviderLogic extends OverloadedPatternProviderLogic {
    private static final Logger LOG = LoggerFactory.getLogger(PackagedPatternProviderLogic.class);

    private static final int BACKOFF_MIN = 5;
    private static final int BACKOFF_MAX = 40;
    private static final int FAILURE_THRESHOLD = 3;
    private static final int FAILURE_BACKOFF_TICKS = 20;
    private static final int RETURN_SPREAD_TICKS = 20;
    private static final int VIRTUAL_CRAFTS_PER_TABLE_PER_TICK = 4;
    private static final String NORMAL_NO_TARGET_REASON = "No adjacent multiblock target accepted this pattern.";
    private static final String WIRELESS_NO_CONNECTION_REASON = "No valid wireless connections were available.";
    private static final String WIRELESS_NO_TARGET_REASON = "No wireless multiblock target accepted this pattern.";

    private final NeighborMainBlockIndex neighborIndex;
    private final EnumMap<Direction, Integer> faceFailures = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, Long> faceBackoffUntil = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, Integer> faceReturnBackoff = new EnumMap<>(Direction.class);
    private final EnumMap<Direction, Long> faceNextReturnPoll = new EnumMap<>(Direction.class);
    private final Map<WirelessConnection, Integer> connFailures = new HashMap<>();
    private final Map<WirelessConnection, Long> connBackoffUntil = new HashMap<>();
    private final Map<WirelessConnection, Integer> connReturnBackoff = new HashMap<>();
    private final Map<WirelessConnection, Long> connNextReturnPoll = new HashMap<>();
    private final EnumMap<Direction, VirtualCraftingLaneLimiter.LaneUse> faceVirtualUses =
            new EnumMap<>(Direction.class);
    private final Map<WirelessConnection, VirtualCraftingLaneLimiter.LaneUse> connVirtualUses = new HashMap<>();
    private final VirtualCraftingLaneLimiter<Direction> faceVirtualLimiter =
            new VirtualCraftingLaneLimiter<>(faceVirtualUses, VIRTUAL_CRAFTS_PER_TABLE_PER_TICK);
    private final VirtualCraftingLaneLimiter<WirelessConnection> connVirtualLimiter =
            new VirtualCraftingLaneLimiter<>(connVirtualUses, VIRTUAL_CRAFTS_PER_TABLE_PER_TICK);
    private int packagedRoundRobin;
    private int returnRoundRobin;
    private long lastReturnRoundRobinTick = -1;

    public PackagedPatternProviderLogic(IManagedGridNode mainNode,
                                        OverloadedPatternProviderBlockEntity host,
                                        int patternInventorySize) {
        super(mainNode, host, patternInventorySize);
        ((PatternProviderLogicAccessor) this).getPatternInventory().setFilter(new IAEItemFilter() {
            @Override
            public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) {
                return isPackagedPatternStack(stack);
            }
        });
        this.neighborIndex = new NeighborMainBlockIndex(host.getBlockPos());
    }

    @Override
    public void updatePatterns() {
        super.updatePatterns();
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        if (!getGridNode().isActive()) {
            return false;
        }
        if (!SmartDoublingCompat.containsOrUnwrapped(getAvailablePatterns(), patternDetails)) {
            return false;
        }
        if (getCraftingLockedReason() != LockCraftingMode.NONE) {
            return false;
        }

        var level = getOverloadedHost().getLevel();
        if (!(level instanceof ServerLevel sl)) {
            return false;
        }

        double cost = PowerCostUtil.totalCost(inputHolder);
        var grid = getGridNode().getGrid();
        if (!PowerCostUtil.canAfford(grid, cost)) {
            return false;
        }

        var dispatch = getOverloadedHost().getProviderMode() == ProviderMode.NORMAL
                ? dispatchNormal(sl, patternDetails, inputHolder)
                : dispatchWireless(sl, patternDetails, inputHolder);

        if (dispatch.success()) {
            PowerCostUtil.consumeRaw(grid, cost);
            syncPendingUnlockRule(patternDetails);
            insertOutputsToReturnInv(dispatch.value() == null ? List.of() : dispatch.value());
            resetExtractPollTimers();
            alertGridTick();
            ((PatternProviderLogicAccessor) this).invokeOnPushPatternSuccess(patternDetails);
        } else if (dispatch.reason() != null) {
            LOG.debug("Packaged dispatch failed for pattern {}: {}", patternDetails, dispatch.reason());
        }
        return dispatch.success();
    }

    private DispatchResult<List<GenericStack>> dispatchNormal(ServerLevel level, IPatternDetails pattern, KeyCounter[] inputs) {
        long gameTick = level.getGameTime();
        return DispatchCycle.tryCandidates(neighborIndex.adapterFaces(level), face -> {
            if (gameTick < faceBackoffUntil.getOrDefault(face, 0L)) {
                return DispatchResult.skipped("Face " + face + " is cooling down after recent failures.");
            }

            var adapter = neighborIndex.getAdapter(face);
            if (adapter == null) {
                return DispatchResult.skipped("Face " + face + " no longer has a registered adapter.");
            }

            var mainPos = getOverloadedHost().getBlockPos().relative(face);
            var virtual = tryVirtualCraft(level, mainPos, adapter, pattern, inputs,
                    faceVirtualLimiter, face, gameTick);
            if (virtual != null) {
                faceFailures.remove(face);
                return DispatchResult.success(virtual.outputs());
            }
            if (adapter instanceof VirtualCraftingAdapter) {
                return DispatchResult.skipped("Virtual crafting adapter on face " + face + " could not accept the pattern.");
            }

            var plan = adapter.plan(level, mainPos, pattern, inputs, getActionSource());
            if (plan == null) {
                return DispatchResult.skipped("Adapter on face " + face + " could not produce a dispatch plan.");
            }

            var execution = DispatchExecutor.execute(plan, getActionSource(), getInternalReturnInv());
            if (execution.success()) {
                faceFailures.remove(face);
                return DispatchResult.success(List.of());
            }

            bumpFaceFailure(face, gameTick);
            return DispatchResult.failure("Face " + face + " failed dispatch: " + execution.reason());
        }, NORMAL_NO_TARGET_REASON);
    }

    private DispatchResult<List<GenericStack>> dispatchWireless(ServerLevel providerLevel, IPatternDetails pattern, KeyCounter[] inputs) {
        long gameTick = providerLevel.getGameTime();
        var valid = getValidConnections(providerLevel, gameTick);
        if (valid.isEmpty()) {
            return DispatchResult.failure(WIRELESS_NO_CONNECTION_REASON);
        }

        int total = valid.size();
        record OrderedConnection(int index, WirelessConnection connection) {
        }
        var ordered = new ArrayList<OrderedConnection>(total);
        for (int i = 0; i < total; i++) {
            int connIndex = Math.floorMod(packagedRoundRobin + i, total);
            ordered.add(new OrderedConnection(connIndex, valid.get(connIndex)));
        }
        return DispatchCycle.tryCandidates(ordered, candidate -> {
            int connIndex = candidate.index();
            var conn = candidate.connection();
            if (gameTick < connBackoffUntil.getOrDefault(conn, 0L)) {
                return DispatchResult.skipped("Wireless target " + conn + " is cooling down after recent failures.");
            }

            var targetLevel = providerLevel.getServer().getLevel(conn.dimension());
            if (targetLevel == null || !targetLevel.isLoaded(conn.pos())) {
                return DispatchResult.skipped("Wireless target " + conn + " is not loaded.");
            }
            var be = targetLevel.getBlockEntity(conn.pos());
            if (be == null) {
                return DispatchResult.skipped("Wireless target " + conn + " has no block entity.");
            }
            var adapter = MultiblockAdapterRegistry.find(targetLevel, conn.pos(), be);
            if (adapter == null) {
                return DispatchResult.skipped("Wireless target " + conn + " has no registered adapter.");
            }

            var virtual = tryVirtualCraft(targetLevel, conn.pos(), adapter, pattern, inputs,
                    connVirtualLimiter, conn, gameTick);
            if (virtual != null) {
                connFailures.remove(conn);
                packagedRoundRobin = (connIndex + 1) % total;
                return DispatchResult.success(virtual.outputs());
            }
            if (adapter instanceof VirtualCraftingAdapter) {
                return DispatchResult.skipped("Virtual wireless adapter " + conn + " could not accept the pattern.");
            }

            var plan = adapter.plan(targetLevel, conn.pos(), pattern, inputs, getActionSource());
            if (plan == null) {
                return DispatchResult.skipped("Wireless target " + conn + " could not produce a dispatch plan.");
            }

            var execution = DispatchExecutor.execute(plan, getActionSource(), getInternalReturnInv());
            if (execution.success()) {
                connFailures.remove(conn);
                packagedRoundRobin = (connIndex + 1) % total;
                return DispatchResult.success(List.of());
            }

            bumpConnFailure(conn, gameTick);
            return DispatchResult.failure("Wireless target " + conn + " failed dispatch: " + execution.reason());
        }, WIRELESS_NO_TARGET_REASON);
    }

    @Nullable
    private <K> VirtualCraftingResult tryVirtualCraft(ServerLevel targetLevel, BlockPos targetPos,
                                                      MultiblockAdapter adapter,
                                                      IPatternDetails pattern, KeyCounter[] inputs,
                                                      VirtualCraftingLaneLimiter<K> limiter, K lane,
                                                      long gameTick) {
        if (!(adapter instanceof VirtualCraftingAdapter virtualAdapter) || !limiter.hasCapacity(lane, gameTick)) {
            return null;
        }

        var result = virtualAdapter.planVirtualCraft(targetLevel, targetPos, pattern, inputs, getActionSource());
        if (result == null || result.outputs().isEmpty() || !limiter.tryAcquire(lane, gameTick)) {
            return null;
        }
        return result;
    }

    @Override
    public void tickAutoReturn() {
        if (!hasAnyTickWork()) {
            return;
        }

        tickWirelessInductionEnergy();

        if (getOverloadedHost().getReturnMode() != ReturnMode.AUTO || !getGridNode().isActive()) {
            return;
        }

        var allowedOutputs = getOrBuildOutputFilter();
        if (allowedOutputs.isEmpty()) {
            return;
        }

        var level = getOverloadedHost().getLevel();
        if (!(level instanceof ServerLevel sl)) {
            return;
        }

        long gameTick = sl.getGameTime();
        if (getOverloadedHost().getProviderMode() == ProviderMode.NORMAL) {
            packagedAutoReturnNormal(sl, allowedOutputs, gameTick);
        } else {
            packagedAutoReturnWireless(sl, allowedOutputs, gameTick);
        }
    }

    private void packagedAutoReturnNormal(ServerLevel level, AllowedOutputFilter allowedOutputs, long gameTick) {
        for (var face : neighborIndex.adapterFaces(level)) {
            if (gameTick < faceNextReturnPoll.getOrDefault(face, 0L)) {
                continue;
            }
            var adapter = neighborIndex.getAdapter(face);
            if (adapter == null) {
                continue;
            }
            if (adapter instanceof VirtualCraftingAdapter) {
                continue;
            }
            var outputs = adapter.extractOutputs(
                    level, getOverloadedHost().getBlockPos().relative(face), allowedOutputs, getActionSource());
            insertOutputsToReturnInv(outputs);
            updateFaceReturnBackoff(face, gameTick, !outputs.isEmpty());
        }
    }

    private void packagedAutoReturnWireless(ServerLevel providerLevel,
                                            AllowedOutputFilter allowedOutputs,
                                            long gameTick) {
        var valid = getValidConnections(providerLevel, gameTick);
        int total = valid.size();
        if (total == 0) {
            return;
        }

        long elapsed = lastReturnRoundRobinTick >= 0 ? gameTick - lastReturnRoundRobinTick : 1;
        lastReturnRoundRobinTick = gameTick;
        int perTick = Math.max(1, (total + RETURN_SPREAD_TICKS - 1) / RETURN_SPREAD_TICKS);
        int toProcess = (int) Math.min((long) perTick * elapsed, total);

        for (int i = 0; i < toProcess; i++) {
            int idx = returnRoundRobin % total;
            returnRoundRobin = (returnRoundRobin + 1) % total;
            var conn = valid.get(idx);
            if (gameTick < connNextReturnPoll.getOrDefault(conn, 0L)) {
                continue;
            }

            var targetLevel = providerLevel.getServer().getLevel(conn.dimension());
            if (targetLevel == null || !targetLevel.isLoaded(conn.pos())) {
                continue;
            }
            var be = targetLevel.getBlockEntity(conn.pos());
            if (be == null) {
                continue;
            }
            var adapter = MultiblockAdapterRegistry.find(targetLevel, conn.pos(), be);
            if (adapter == null) {
                continue;
            }
            if (adapter instanceof VirtualCraftingAdapter) {
                continue;
            }

            var outputs = adapter.extractOutputs(targetLevel, conn.pos(), allowedOutputs, getActionSource());
            insertOutputsToReturnInv(outputs);
            updateConnReturnBackoff(conn, gameTick, !outputs.isEmpty());
        }
    }

    @Override
    public void onNeighborChanged() {
        neighborIndex.invalidate();
        super.onNeighborChanged();
    }

    @Override
    public void onHostStateChanged() {
        pruneWirelessState();
        super.onHostStateChanged();
    }

    private void bumpFaceFailure(Direction face, long gameTick) {
        int failures = faceFailures.merge(face, 1, Integer::sum);
        if (failures >= FAILURE_THRESHOLD) {
            faceFailures.put(face, 0);
            faceBackoffUntil.put(face, gameTick + FAILURE_BACKOFF_TICKS);
        }
    }

    private void bumpConnFailure(WirelessConnection conn, long gameTick) {
        int failures = connFailures.merge(conn, 1, Integer::sum);
        if (failures >= FAILURE_THRESHOLD) {
            connFailures.put(conn, 0);
            connBackoffUntil.put(conn, gameTick + FAILURE_BACKOFF_TICKS);
        }
    }

    private void updateFaceReturnBackoff(Direction face, long gameTick, boolean foundItems) {
        int interval = foundItems
                ? BACKOFF_MIN
                : Math.min(faceReturnBackoff.getOrDefault(face, BACKOFF_MIN) * 2, BACKOFF_MAX);
        faceReturnBackoff.put(face, interval);
        faceNextReturnPoll.put(face, gameTick + interval);
    }

    private void updateConnReturnBackoff(WirelessConnection conn, long gameTick, boolean foundItems) {
        int interval = foundItems
                ? BACKOFF_MIN
                : Math.min(connReturnBackoff.getOrDefault(conn, BACKOFF_MIN) * 2, BACKOFF_MAX);
        connReturnBackoff.put(conn, interval);
        connNextReturnPoll.put(conn, gameTick + interval);
    }

    private void resetExtractPollTimers() {
        faceNextReturnPoll.clear();
        connNextReturnPoll.clear();
        faceReturnBackoff.clear();
        connReturnBackoff.clear();
    }

    private void pruneWirelessState() {
        var active = getOverloadedHost().getConnections();
        connFailures.keySet().retainAll(active);
        connBackoffUntil.keySet().retainAll(active);
        connReturnBackoff.keySet().retainAll(active);
        connNextReturnPoll.keySet().retainAll(active);
        connVirtualLimiter.retainAll(active);
    }

    private static boolean isPackagedPatternStack(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof OverloadPatternItem overloadPatternItem) {
            return overloadPatternItem.hasPayload(stack);
        }
        return PatternDetailsHelper.isEncodedPattern(stack);
    }
}
