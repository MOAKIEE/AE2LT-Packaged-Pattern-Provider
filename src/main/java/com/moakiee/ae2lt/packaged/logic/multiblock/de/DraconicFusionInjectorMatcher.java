package com.moakiee.ae2lt.packaged.logic.multiblock.de;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

final class DraconicFusionInjectorMatcher {

    private DraconicFusionInjectorMatcher() {
    }

    static <T> Optional<List<T>> selectAvailableTargets(
            List<Candidate<T>> injectors,
            int recipeTierIndex,
            int requiredCount) {
        Objects.requireNonNull(injectors, "injectors");
        if (requiredCount < 0) {
            throw new IllegalArgumentException("requiredCount must be non-negative");
        }

        var selected = new ArrayList<T>(requiredCount);
        for (var injector : injectors) {
            if (!injector.empty()) {
                return Optional.empty();
            }
            if (injector.tierIndex() >= recipeTierIndex && selected.size() < requiredCount) {
                selected.add(injector.target());
            }
        }

        return selected.size() == requiredCount
                ? Optional.of(List.copyOf(selected))
                : Optional.empty();
    }

    record Candidate<T>(T target, int tierIndex, boolean empty) {
        Candidate {
            Objects.requireNonNull(target, "target");
        }
    }
}
