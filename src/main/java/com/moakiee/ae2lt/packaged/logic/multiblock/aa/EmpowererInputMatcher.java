package com.moakiee.ae2lt.packaged.logic.multiblock.aa;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

final class EmpowererInputMatcher {

    private EmpowererInputMatcher() {
    }

    @Nullable
    static <T> Match<T> match(List<T> inputs, Predicate<T> basePredicate,
                              List<Predicate<T>> modifierPredicates) {
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(basePredicate, "basePredicate");
        Objects.requireNonNull(modifierPredicates, "modifierPredicates");

        if (inputs.size() != 5 || modifierPredicates.size() != 4) {
            return null;
        }

        for (int baseIndex = 0; baseIndex < inputs.size(); baseIndex++) {
            var base = inputs.get(baseIndex);
            if (!basePredicate.test(base)) {
                continue;
            }

            var used = new boolean[inputs.size()];
            used[baseIndex] = true;
            var modifiers = new ArrayList<T>(modifierPredicates.size());
            if (assignModifier(inputs, modifierPredicates, used, modifiers, 0)) {
                return new Match<>(base, List.copyOf(modifiers));
            }
        }
        return null;
    }

    private static <T> boolean assignModifier(List<T> inputs,
                                              List<Predicate<T>> modifierPredicates,
                                              boolean[] used,
                                              List<T> modifiers,
                                              int modifierIndex) {
        if (modifierIndex == modifierPredicates.size()) {
            return allUsed(used);
        }

        var predicate = modifierPredicates.get(modifierIndex);
        for (int inputIndex = 0; inputIndex < inputs.size(); inputIndex++) {
            if (used[inputIndex]) {
                continue;
            }
            var input = inputs.get(inputIndex);
            if (!predicate.test(input)) {
                continue;
            }

            used[inputIndex] = true;
            modifiers.add(input);
            if (assignModifier(inputs, modifierPredicates, used, modifiers, modifierIndex + 1)) {
                return true;
            }
            modifiers.remove(modifiers.size() - 1);
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

    record Match<T>(T base, List<T> modifiers) {
    }
}
