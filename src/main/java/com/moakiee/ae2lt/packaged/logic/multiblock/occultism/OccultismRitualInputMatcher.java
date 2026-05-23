package com.moakiee.ae2lt.packaged.logic.multiblock.occultism;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

final class OccultismRitualInputMatcher {

    private OccultismRitualInputMatcher() {
    }

    @Nullable
    static <T> Match<T> match(List<T> units,
                              Predicate<T> activationMatcher,
                              List<Predicate<T>> ingredientMatchers) {
        if (units.size() != ingredientMatchers.size() + 1) {
            return null;
        }

        for (int i = 0; i < units.size(); i++) {
            if (!activationMatcher.test(units.get(i))) {
                continue;
            }

            var match = matchIngredients(units, i, ingredientMatchers);
            if (match != null) {
                return new Match<>(units.get(i), match);
            }
        }

        return null;
    }

    @Nullable
    private static <T> List<T> matchIngredients(List<T> units,
                                                int activationIndex,
                                                List<Predicate<T>> ingredientMatchers) {
        var used = new boolean[units.size()];
        used[activationIndex] = true;

        var ingredients = new ArrayList<T>(ingredientMatchers.size());
        for (var matcher : ingredientMatchers) {
            T matched = null;
            for (int i = 0; i < units.size(); i++) {
                if (!used[i] && matcher.test(units.get(i))) {
                    matched = units.get(i);
                    used[i] = true;
                    break;
                }
            }
            if (matched == null) {
                return null;
            }
            ingredients.add(matched);
        }

        return List.copyOf(ingredients);
    }

    record Match<T>(T activation, List<T> ingredients) {
    }
}
