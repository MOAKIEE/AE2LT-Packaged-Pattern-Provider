package com.moakiee.ae2lt.packaged.logic.multiblock.ec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

import org.jetbrains.annotations.Nullable;

final class ExtendedCraftingTableIngredientAssigner {

    private ExtendedCraftingTableIngredientAssigner() {
    }

    @Nullable
    static <I, K> List<K> assign(List<I> ingredients,
                                 List<Input<K>> inputs,
                                 long amountPerIngredient,
                                 BiPredicate<I, K> matcher) {
        Objects.requireNonNull(ingredients, "ingredients");
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(matcher, "matcher");
        if (amountPerIngredient <= 0) {
            return null;
        }

        var units = new ArrayList<K>();
        for (var input : inputs) {
            if (input.amount() == 0) {
                continue;
            }
            if (input.amount() % amountPerIngredient != 0) {
                return null;
            }
            long copies = input.amount() / amountPerIngredient;
            if (copies > ingredients.size() - units.size()) {
                return null;
            }
            for (long i = 0; i < copies; i++) {
                units.add(input.key());
            }
        }
        if (units.size() != ingredients.size()) {
            return null;
        }

        var orderedIngredientIndexes = new ArrayList<Integer>(ingredients.size());
        for (int i = 0; i < ingredients.size(); i++) {
            orderedIngredientIndexes.add(i);
        }
        orderedIngredientIndexes.sort(Comparator.comparingInt(index ->
                candidateCount(ingredients.get(index), units, matcher)));

        var used = new boolean[units.size()];
        var chosenUnitByIngredient = new int[ingredients.size()];
        Arrays.fill(chosenUnitByIngredient, -1);
        if (!assignOne(0, ingredients, units, matcher, orderedIngredientIndexes, used, chosenUnitByIngredient)) {
            return null;
        }

        var assigned = new ArrayList<K>(ingredients.size());
        for (int i = 0; i < ingredients.size(); i++) {
            int unitIndex = chosenUnitByIngredient[i];
            if (unitIndex < 0) {
                return null;
            }
            assigned.add(units.get(unitIndex));
        }
        return List.copyOf(assigned);
    }

    private static <I, K> int candidateCount(I ingredient,
                                             List<K> units,
                                             BiPredicate<I, K> matcher) {
        int count = 0;
        for (var unit : units) {
            if (matcher.test(ingredient, unit)) {
                count++;
            }
        }
        return count;
    }

    private static <I, K> boolean assignOne(int orderIndex,
                                            List<I> ingredients,
                                            List<K> units,
                                            BiPredicate<I, K> matcher,
                                            List<Integer> orderedIngredientIndexes,
                                            boolean[] used,
                                            int[] chosenUnitByIngredient) {
        if (orderIndex >= orderedIngredientIndexes.size()) {
            return true;
        }

        int ingredientIndex = orderedIngredientIndexes.get(orderIndex);
        var ingredient = ingredients.get(ingredientIndex);
        for (int unitIndex = 0; unitIndex < units.size(); unitIndex++) {
            if (used[unitIndex] || !matcher.test(ingredient, units.get(unitIndex))) {
                continue;
            }
            used[unitIndex] = true;
            chosenUnitByIngredient[ingredientIndex] = unitIndex;
            if (assignOne(orderIndex + 1, ingredients, units, matcher,
                    orderedIngredientIndexes, used, chosenUnitByIngredient)) {
                return true;
            }
            chosenUnitByIngredient[ingredientIndex] = -1;
            used[unitIndex] = false;
        }
        return false;
    }

    record Input<K>(K key, long amount) {
        Input {
            Objects.requireNonNull(key, "key");
            if (amount < 0) {
                throw new IllegalArgumentException("amount must be non-negative");
            }
        }
    }
}
