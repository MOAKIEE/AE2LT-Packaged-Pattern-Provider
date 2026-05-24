package com.moakiee.ae2lt.packaged.logic.multiblock.ars;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

final class ArsImbuementInputMatcher {

    private ArsImbuementInputMatcher() {
    }

    @Nullable
    static <T> Match<T> match(List<T> inputs,
                              Predicate<T> reagentPredicate,
                              List<Predicate<T>> pedestalPredicates) {
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(reagentPredicate, "reagentPredicate");
        Objects.requireNonNull(pedestalPredicates, "pedestalPredicates");

        if (inputs.size() != pedestalPredicates.size() + 1) {
            return null;
        }

        for (int reagentIndex = 0; reagentIndex < inputs.size(); reagentIndex++) {
            var reagent = inputs.get(reagentIndex);
            if (!reagentPredicate.test(reagent)) {
                continue;
            }

            var used = new boolean[inputs.size()];
            used[reagentIndex] = true;
            var pedestals = new ArrayList<T>(pedestalPredicates.size());
            if (assignPedestal(inputs, pedestalPredicates, used, pedestals, 0)) {
                return new Match<>(reagent, List.copyOf(pedestals));
            }
        }
        return null;
    }

    private static <T> boolean assignPedestal(List<T> inputs,
                                              List<Predicate<T>> pedestalPredicates,
                                              boolean[] used,
                                              List<T> pedestals,
                                              int pedestalIndex) {
        if (pedestalIndex == pedestalPredicates.size()) {
            return allUsed(used);
        }

        var predicate = pedestalPredicates.get(pedestalIndex);
        for (int inputIndex = 0; inputIndex < inputs.size(); inputIndex++) {
            if (used[inputIndex]) {
                continue;
            }
            var input = inputs.get(inputIndex);
            if (!predicate.test(input)) {
                continue;
            }

            used[inputIndex] = true;
            pedestals.add(input);
            if (assignPedestal(inputs, pedestalPredicates, used, pedestals, pedestalIndex + 1)) {
                return true;
            }
            pedestals.remove(pedestals.size() - 1);
            used[inputIndex] = false;
        }
        return false;
    }

    private static boolean allUsed(boolean[] used) {
        for (boolean value : used) {
            if (!value) {
                return false;
            }
        }
        return true;
    }

    record Match<T>(T reagent, List<T> pedestals) {
    }
}
