package com.moakiee.ae2lt.packaged.logic.multiblock.binding;

import java.util.Objects;

/**
 * Opaque adapter-specific binding handle plus its dispatch mode.
 * Returned by {@code MultiblockAdapter.bind(...)} once per pattern and reused
 * on every subsequent push to avoid repeating recipe / structure searches.
 *
 * <p>The {@code handle} is intentionally typed as {@link Object}; the same
 * adapter that produced it interprets it on subsequent calls (e.g. EC's
 * {@code PlanMatch}, DE's {@code RecipeMatch}, AA's {@code RecipeMatch}).
 */
public record BindingResult(Object handle, BindingMode mode) {
    public BindingResult {
        Objects.requireNonNull(handle, "handle");
        Objects.requireNonNull(mode, "mode");
    }
}
