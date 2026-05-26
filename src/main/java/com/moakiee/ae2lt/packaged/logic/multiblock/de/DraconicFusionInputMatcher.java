package com.moakiee.ae2lt.packaged.logic.multiblock.de;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

final class DraconicFusionInputMatcher {

    private DraconicFusionInputMatcher() {
    }

    @Nullable
    static <T> Match<T> match(List<T> units,
                              int catalystCount,
                              Predicate<T> catalystMatcher,
                              List<Predicate<T>> ingredientMatchers) {
        Objects.requireNonNull(units, "units");
        Objects.requireNonNull(catalystMatcher, "catalystMatcher");
        Objects.requireNonNull(ingredientMatchers, "ingredientMatchers");

        if (catalystCount <= 0 || units.size() != catalystCount + ingredientMatchers.size()) {
            return null;
        }

        for (int catalystIndex = 0; catalystIndex < units.size(); catalystIndex++) {
            var catalystKey = units.get(catalystIndex);
            if (!catalystMatcher.test(catalystKey)) {
                continue;
            }

            var used = new boolean[units.size()];
            if (!useCatalystGroup(units, used, catalystKey, catalystCount)) {
                continue;
            }

            var ingredients = new ArrayList<T>(ingredientMatchers.size());
            if (assignIngredient(units, used, ingredients, ingredientMatchers, 0)) {
                return new Match<>(catalystKey, List.copyOf(ingredients));
            }
        }

        return null;
    }

    private static <T> boolean useCatalystGroup(List<T> units, boolean[] used,
                                                T catalystKey, int catalystCount) {
        int matched = 0;
        for (int i = 0; i < units.size(); i++) {
            if (Objects.equals(catalystKey, units.get(i))) {
                used[i] = true;
                matched++;
                if (matched == catalystCount) {
                    return true;
                }
            }
        }
        return false;
    }

    private static <T> boolean assignIngredient(List<T> units,
                                                boolean[] used,
                                                List<T> ingredients,
                                                List<Predicate<T>> ingredientMatchers,
                                                int ingredientIndex) {
        if (ingredientIndex == ingredientMatchers.size()) {
            return allUsed(used);
        }

        var matcher = ingredientMatchers.get(ingredientIndex);
        for (int unitIndex = 0; unitIndex < units.size(); unitIndex++) {
            if (used[unitIndex]) {
                continue;
            }

            var unit = units.get(unitIndex);
            if (!matcher.test(unit)) {
                continue;
            }

            used[unitIndex] = true;
            ingredients.add(unit);
            if (assignIngredient(units, used, ingredients, ingredientMatchers, ingredientIndex + 1)) {
                return true;
            }
            ingredients.remove(ingredients.size() - 1);
            used[unitIndex] = false;
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

    record Match<T>(T catalystKey, List<T> ingredients) {
    }
}
