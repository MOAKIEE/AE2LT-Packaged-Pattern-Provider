package com.moakiee.ae2lt.packaged.logic.multiblock.avaritia;

import java.util.Objects;
import java.util.function.Function;

final class AvaritiaExtremeSmithingOutputMatcher {

    private AvaritiaExtremeSmithingOutputMatcher() {
    }

    static <K> boolean matches(long expectedAmount, K expectedKey,
                               long actualAmount, K actualKey,
                               Function<K, K> dropSecondary) {
        Objects.requireNonNull(expectedKey, "expectedKey");
        Objects.requireNonNull(actualKey, "actualKey");
        Objects.requireNonNull(dropSecondary, "dropSecondary");

        return expectedAmount == actualAmount
                && Objects.equals(dropSecondary.apply(expectedKey), dropSecondary.apply(actualKey));
    }
}
