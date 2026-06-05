package com.moakiee.ae2lt.packaged.logic.multiblock.avaritia;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

final class AvaritiaExtremeSmithingInputMatcher {

    private static final int INPUT_COUNT = 5;

    private AvaritiaExtremeSmithingInputMatcher() {
    }

    @Nullable
    static <T> Match<T> match(List<T> inputs,
                              Predicate<T> templateMatcher,
                              Predicate<T> baseMatcher,
                              Predicate<T> additionMatcher) {
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(templateMatcher, "templateMatcher");
        Objects.requireNonNull(baseMatcher, "baseMatcher");
        Objects.requireNonNull(additionMatcher, "additionMatcher");
        if (inputs.size() != INPUT_COUNT) {
            return null;
        }

        for (int templateIndex = 0; templateIndex < inputs.size(); templateIndex++) {
            var template = inputs.get(templateIndex);
            if (!templateMatcher.test(template)) {
                continue;
            }

            for (int baseIndex = 0; baseIndex < inputs.size(); baseIndex++) {
                if (baseIndex == templateIndex) {
                    continue;
                }
                var base = inputs.get(baseIndex);
                if (!baseMatcher.test(base)) {
                    continue;
                }

                var additions = new ArrayList<T>(3);
                boolean valid = true;
                for (int i = 0; i < inputs.size(); i++) {
                    if (i == templateIndex || i == baseIndex) {
                        continue;
                    }
                    var addition = inputs.get(i);
                    if (!additionMatcher.test(addition)) {
                        valid = false;
                        break;
                    }
                    additions.add(addition);
                }

                if (valid && additions.size() == 3) {
                    return new Match<>(template, base, List.copyOf(additions));
                }
            }
        }
        return null;
    }

    record Match<T>(T template, T base, List<T> additions) {
        Match {
            Objects.requireNonNull(template, "template");
            Objects.requireNonNull(base, "base");
            additions = List.copyOf(additions);
        }
    }
}
