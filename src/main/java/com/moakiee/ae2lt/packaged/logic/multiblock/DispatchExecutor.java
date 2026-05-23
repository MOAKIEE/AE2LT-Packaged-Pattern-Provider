package com.moakiee.ae2lt.packaged.logic.multiblock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import appeng.helpers.patternprovider.PatternProviderTarget;

import com.moakiee.ae2lt.packaged.logic.DispatchResult;

public final class DispatchExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(DispatchExecutor.class);

    private DispatchExecutor() {
    }

    public static DispatchResult<Void> execute(DispatchPlan plan,
                                               IActionSource source,
                                               PatternProviderReturnInventory returnInv) {
        var targets = plan.targets();
        if (targets.isEmpty()) {
            return DispatchResult.failure("Dispatch plan contained no insertion targets.");
        }

        for (var target : targets) {
            var simulation = simulateTarget(target, source);
            if (simulation.failure()) {
                return simulation;
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
        return DispatchResult.success(null);
    }

    private static DispatchResult<Void> simulateTarget(TargetSlot target, IActionSource source) {
        for (var stack : target.stacks()) {
            long accepted = insertOne(target, stack, Actionable.SIMULATE, source);
            if (accepted < stack.amount()) {
                return DispatchResult.failure(
                        "Target at " + target.pos() + " rejected "
                                + stack.what() + " x" + stack.amount() + " during simulation.");
            }
        }
        return DispatchResult.success(null);
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
