package com.moakiee.ae2lt.packaged.logic.multiblock;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.KeyCounter;

/**
 * Marker for adapters whose recipes have no in-world delay: outputs are
 * computed and returned directly without a physical dispatch cycle.
 *
 * <p>Virtual lanes feed the batch accumulator instead of the real cooldown
 * machinery; they can be filled by every connected provider in parallel.
 */
public interface VirtualCraftingAdapter extends MultiblockAdapter {

    /**
     * Compute the virtual result with an existing binding handle.
     * Returns {@code null} when current world state invalidates the binding
     * (e.g. recipe no longer matches, source pool / charge insufficient).
     */
    @Nullable
    VirtualCraftingResult planVirtualWithBinding(ServerLevel level, BlockPos mainPos,
                                                 IPatternDetails pattern, KeyCounter[] inputs,
                                                 Object handle, IActionSource source);

    /**
     * Sound event id played at the provider when a virtual batch from this
     * adapter actually delivers items into the return inventory. Used by
     * {@code PackagedPatternProviderLogic#playFlushSound} to mimic the audio
     * cue the player would hear from the underlying machine.
     *
     * <p>The id is looked up against {@code BuiltInRegistries.SOUND_EVENT}
     * at flush time, so an unloaded mod (or a renamed sound) silently falls
     * back to the generic "products arrived" cue.
     *
     * <p>Returning {@code null} means "use the generic fallback" — appropriate
     * for adapters with no signature sound (e.g. plain crafting tables).
     */
    @Nullable
    default ResourceLocation flushSoundId() {
        return null;
    }

    /**
     * Optional machine-side effect to run when the virtual batch actually
     * surfaces in the provider return inventory.
     *
     * <p>This is intentionally tied to flush time rather than push time: a
     * virtual push only admits work into the accumulator, while flush is when
     * the player sees products arrive. Adapters can use this for visual-only
     * machine feedback, e.g. firing a laser once without placing dropped
     * inputs in the world.
     */
    default void onVirtualBatchFlush(ServerLevel level, BlockPos mainPos,
                                     Object handle, IActionSource source) {
    }
}
