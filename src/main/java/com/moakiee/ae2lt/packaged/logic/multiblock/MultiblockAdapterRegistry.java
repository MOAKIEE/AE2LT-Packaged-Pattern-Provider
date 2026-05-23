package com.moakiee.ae2lt.packaged.logic.multiblock;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.moakiee.ae2lt.packaged.AE2LTPackagedProvider;

public final class MultiblockAdapterRegistry {

    private static final Map<ResourceLocation, AdapterRegistration> REGISTRATIONS = new LinkedHashMap<>();

    private MultiblockAdapterRegistry() {
    }

    /** Register during mod setup. Not thread-safe with concurrent lookup. */
    public static void register(MultiblockAdapter adapter) {
        register(new AdapterRegistration(defaultId(adapter), adapter.priority(), () -> true, adapter));
    }

    /** Register during mod setup. Not thread-safe with concurrent lookup. */
    public static void register(AdapterRegistration registration) {
        var existing = REGISTRATIONS.putIfAbsent(registration.id(), registration);
        if (existing != null) {
            throw new IllegalStateException("Duplicate multiblock adapter: " + registration.id());
        }
    }

    @Nullable
    public static MultiblockAdapter find(ServerLevel level, BlockPos pos, @Nullable BlockEntity be) {
        for (var adapter : activeAdapters()) {
            if (adapter.recognizesMain(level, pos, be)) {
                return adapter;
            }
        }
        return null;
    }

    public static List<MultiblockAdapter> getAll() {
        return activeAdapters();
    }

    public static List<MultiblockAdapter> activeAdapters() {
        return Collections.unmodifiableList(
                REGISTRATIONS.values().stream()
                        .filter(AdapterRegistration::isEnabled)
                        .sorted(Comparator.comparingInt(AdapterRegistration::priority))
                        .map(AdapterRegistration::adapter)
                        .collect(Collectors.toList()));
    }

    public static List<AdapterRegistration> registrations() {
        return Collections.unmodifiableList(List.copyOf(REGISTRATIONS.values()));
    }

    static void clearForTests() {
        REGISTRATIONS.clear();
    }

    private static ResourceLocation defaultId(MultiblockAdapter adapter) {
        return ResourceLocation.fromNamespaceAndPath(
                AE2LTPackagedProvider.MODID,
                toSnakeCase(adapter.getClass().getSimpleName()));
    }

    private static String toSnakeCase(String value) {
        var out = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    char previous = value.charAt(i - 1);
                    boolean nextLower = i + 1 < value.length() && Character.isLowerCase(value.charAt(i + 1));
                    if (Character.isLowerCase(previous) || Character.isDigit(previous) || nextLower) {
                        out.append('_');
                    }
                }
                out.append(Character.toLowerCase(c));
            } else {
                out.append(c);
            }
        }
        return out.toString().toLowerCase(Locale.ROOT);
    }
}
