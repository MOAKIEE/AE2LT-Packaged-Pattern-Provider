package com.moakiee.ae2lt.packaged.logic.multiblock.aa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

class EmpowererInputMatcherTest {

    @Test
    void assignsBaseAndModifiersWhenStandInputsAreOutOfRecipeOrder() {
        var match = EmpowererInputMatcher.match(
                List.of("clay", "redstone", "input", "coal", "dye"),
                item -> item.equals("input"),
                List.of(
                        item -> item.equals("redstone"),
                        item -> item.equals("dye"),
                        item -> item.equals("clay"),
                        item -> item.equals("coal")));

        assertNotNull(match);
        assertEquals("input", match.base());
        assertEquals(List.of("redstone", "dye", "clay", "coal"), match.modifiers());
    }

    @Test
    void rejectsUnusedExtraInputs() {
        var match = EmpowererInputMatcher.match(
                List.of("redstone", "input", "coal", "dye", "clay", "extra"),
                item -> item.equals("input"),
                fourExactModifiers());

        assertNull(match);
    }

    @Test
    void rejectsDuplicateUseOfOneInputForTwoModifiers() {
        var match = EmpowererInputMatcher.match(
                List.of("redstone", "input", "coal", "dye", "clay"),
                item -> item.equals("input"),
                List.of(
                        item -> item.equals("redstone"),
                        item -> item.equals("redstone"),
                        item -> item.equals("dye"),
                        item -> item.equals("coal")));

        assertNull(match);
    }

    private static List<Predicate<String>> fourExactModifiers() {
        return List.of(
                item -> item.equals("redstone"),
                item -> item.equals("dye"),
                item -> item.equals("clay"),
                item -> item.equals("coal"));
    }
}
