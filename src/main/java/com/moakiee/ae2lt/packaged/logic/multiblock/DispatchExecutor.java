package com.moakiee.ae2lt.packaged.logic.multiblock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import appeng.helpers.patternprovider.PatternProviderTarget;

public final class DispatchExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(DispatchExecutor.class);

    private DispatchExecutor() {
    }

    public static boolean execute(DispatchPlan plan,
                                  IActionSource source,
                                  PatternProviderReturnInventory returnInv) {
        var targets = plan.targets();
        if (targets.isEmpty()) {
            LOG.debug("Dispatch skipped: plan contained no insertion targets.");
            return false;
        }

        // Two-phase commit: simulate first so caller can fall over to the next
        // lane candidate cleanly if any target rejects the plan. Logging the
        // rejection reason is intentional - it makes "why did the provider
        // skip this lane this tick?" investigable without a debugger.
        for (var target : targets) {
            if (!simulateTarget(target, source)) {
                return false;
            }
        }

        for (var target : targets) {
            for (var stack : target.stacks()) {
                long accepted = insertOne(target, stack, Actionable.MODULATE, source);
                if (accepted < stack.amount()) {
                    long residual = stack.amount() - accepted;
                    returnInv.insert(0, stack.what(), residual, Actionable.MODULATE);
                    LOG.warn("Dispatch race: {} x{} undelivered at {}; residual moved to return inventory",
                            stack.what(), residual, target.pos());
                }
            }
        }

        if (plan.onCommit() != null) {
            plan.onCommit().run();
        }
        return true;
    }

    private static boolean simulateTarget(TargetSlot target, IActionSource source) {
        for (var stack : target.stacks()) {
            long accepted = insertOne(target, stack, Actionable.SIMULATE, source);
            if (accepted < stack.amount()) {
                LOG.debug("Dispatch simulation rejected at {}: {} x{} (only {} would fit)",
                        target.pos(), stack.what(), stack.amount(), accepted);
                return false;
            }
        }
        return true;
    }

    private static long insertOne(TargetSlot target, GenericStack stack,
                                  Actionable mode, IActionSource source) {
        return switch (target.strategy()) {
            case STANDARD -> {
                var be = target.level().getBlockEntity(target.pos());
                if (be == null) {
                    yield 0L;
                }
                var ppt = PatternProviderTarget.get(target.level(), target.pos(), be, target.face(), source);
                if (ppt == null) {
                    yield 0L;
                }
                yield ppt.insert(stack.what(), stack.amount(), mode);
            }
            case CUSTOM -> {
                if (target.customInserter() == null) {
                    yield 0L;
                }
                yield target.customInserter().apply(stack, mode);
            }
        };
    }
}
