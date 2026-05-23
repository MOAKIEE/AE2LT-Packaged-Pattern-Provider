package com.moakiee.ae2lt.packaged.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class DispatchCycleTest {

    @Test
    void shouldContinueWhenExecutionFails() {
        var visited = new ArrayList<String>();

        var result = DispatchCycle.tryCandidates(
                List.of("first", "second"),
                candidate -> {
                    visited.add(candidate);
                    if (candidate.equals("first")) {
                        return DispatchResult.failure("first failed");
                    }
                    return DispatchResult.success("second succeeded");
                },
                "no target");

        assertTrue(result.success());
        assertEquals("second succeeded", result.value());
        assertEquals(List.of("first", "second"), visited);
    }

    @Test
    void shouldFailWhenAllTargetsFail() {
        var result = DispatchCycle.tryCandidates(
                List.of("first", "second"),
                candidate -> DispatchResult.failure(candidate + " failed"),
                "no target");

        assertFalse(result.success());
        assertTrue(result.failure());
        assertEquals("second failed", result.reason());
    }

    @Test
    void shouldSkipWhenPlanCannotBeCreated() {
        var visited = new ArrayList<String>();

        var result = DispatchCycle.tryCandidates(
                List.of("first", "second"),
                candidate -> {
                    visited.add(candidate);
                    if (candidate.equals("first")) {
                        return DispatchResult.skipped("no plan");
                    }
                    return DispatchResult.success("second succeeded");
                },
                "no target");

        assertTrue(result.success());
        assertEquals("second succeeded", result.value());
        assertEquals(List.of("first", "second"), visited);
    }

    @Test
    void shouldFailWhenNoCandidatesAreAvailable() {
        var result = DispatchCycle.tryCandidates(
                List.<String>of(),
                candidate -> DispatchResult.success(candidate),
                "no target available");

        assertFalse(result.success());
        assertTrue(result.failure());
        assertEquals("no target available", result.reason());
    }
}
