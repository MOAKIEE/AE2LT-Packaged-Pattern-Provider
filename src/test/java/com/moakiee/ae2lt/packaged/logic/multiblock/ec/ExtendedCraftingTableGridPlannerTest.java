package com.moakiee.ae2lt.packaged.logic.multiblock.ec;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class ExtendedCraftingTableGridPlannerTest {

    @Test
    void placesCompactRecipeRowsIntoTopLeftOfLargerTableGrid() {
        var slots = ExtendedCraftingTableGridPlanner.place(3, 5,
                List.of("a", "b", "c", "d", "e", "f"));

        assertEquals(List.of(
                new ExtendedCraftingTableGridPlanner.Assignment<>(0, "a"),
                new ExtendedCraftingTableGridPlanner.Assignment<>(1, "b"),
                new ExtendedCraftingTableGridPlanner.Assignment<>(2, "c"),
                new ExtendedCraftingTableGridPlanner.Assignment<>(5, "d"),
                new ExtendedCraftingTableGridPlanner.Assignment<>(6, "e"),
                new ExtendedCraftingTableGridPlanner.Assignment<>(7, "f")), slots);
    }

    @Test
    void leavesEmptyRecipeCellsUnassigned() {
        var slots = ExtendedCraftingTableGridPlanner.place(2, 7,
                Arrays.asList("a", null, null, "d"));

        assertEquals(List.of(
                new ExtendedCraftingTableGridPlanner.Assignment<>(0, "a"),
                new ExtendedCraftingTableGridPlanner.Assignment<>(8, "d")), slots);
    }

    @Test
    void assignsGroupedInputsToRepeatedShapedIngredients() {
        var recipeCells = Arrays.asList(
                "D", "L", "L", "L", "L", "L", "D",
                "D", "G", "I", "N", "I", "G", "D",
                "D", "G", "I", "N", "I", "G", "D",
                "D", "L", "L", "L", "L", "L", "D");

        var groupedInputs = List.of(
                "diamond", "diamond", "diamond", "diamond", "diamond", "diamond", "diamond", "diamond",
                "lapis", "lapis", "lapis", "lapis", "lapis", "lapis", "lapis", "lapis", "lapis", "lapis",
                "gold", "gold", "gold", "gold",
                "iron", "iron", "iron", "iron",
                "nether_star", "nether_star");

        var slots = ExtendedCraftingTableGridPlanner.placeMatching(
                7,
                7,
                recipeCells,
                groupedInputs,
                (ingredient, input) -> switch (ingredient) {
                    case "D" -> input.equals("diamond");
                    case "L" -> input.equals("lapis");
                    case "G" -> input.equals("gold");
                    case "I" -> input.equals("iron");
                    case "N" -> input.equals("nether_star");
                    default -> false;
                });

        assertEquals(28, slots.size());
        assertEquals(new ExtendedCraftingTableGridPlanner.Assignment<>(0, "diamond"), slots.get(0));
        assertEquals(new ExtendedCraftingTableGridPlanner.Assignment<>(1, "lapis"), slots.get(1));
        assertEquals(new ExtendedCraftingTableGridPlanner.Assignment<>(8, "gold"), slots.get(8));
        assertEquals(new ExtendedCraftingTableGridPlanner.Assignment<>(10, "nether_star"), slots.get(10));
        assertEquals(new ExtendedCraftingTableGridPlanner.Assignment<>(27, "diamond"), slots.get(27));
    }

    @Test
    void backtracksWhenAnEarlyIngredientCanAcceptMultipleInputs() {
        var slots = ExtendedCraftingTableGridPlanner.placeMatching(
                2,
                3,
                List.of("any_ingot", "iron_only"),
                List.of("iron", "gold"),
                (ingredient, input) -> switch (ingredient) {
                    case "any_ingot" -> input.equals("iron") || input.equals("gold");
                    case "iron_only" -> input.equals("iron");
                    default -> false;
                });

        assertEquals(List.of(
                new ExtendedCraftingTableGridPlanner.Assignment<>(0, "gold"),
                new ExtendedCraftingTableGridPlanner.Assignment<>(1, "iron")), slots);
    }
}
