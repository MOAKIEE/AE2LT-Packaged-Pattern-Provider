package com.moakiee.ae2lt.packaged.logic;

import org.jetbrains.annotations.Nullable;

public record DispatchResult<T>(Status status, @Nullable T value, @Nullable String reason) {

    public enum Status {
        SUCCESS,
        SKIPPED,
        FAILURE
    }

    public boolean success() {
        return status == Status.SUCCESS;
    }

    public boolean skipped() {
        return status == Status.SKIPPED;
    }

    public boolean failure() {
        return status == Status.FAILURE;
    }

    public static <T> DispatchResult<T> success(@Nullable T value) {
        return new DispatchResult<>(Status.SUCCESS, value, null);
    }

    public static <T> DispatchResult<T> skipped(String reason) {
        return new DispatchResult<>(Status.SKIPPED, null, reason);
    }

    public static <T> DispatchResult<T> failure(String reason) {
        return new DispatchResult<>(Status.FAILURE, null, reason);
    }
}
