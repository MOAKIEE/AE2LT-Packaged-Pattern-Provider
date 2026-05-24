package com.moakiee.ae2lt.packaged.logic.multiblock;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.moakiee.ae2lt.logic.AllowedOutputFilter;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingResult;

public interface MultiblockAdapter {

    /** Smaller value matches first. */
    int priority();

    /**
     * Cheap check: is this position a main block handled by this adapter?
     *
     * <p>{@code be} can be {@code null} when the target block is a "plain" block
     * (e.g. Occultism's spirit_fire is a {@code BaseFireBlock} with no BE).
     * Adapters that require a BE must guard with {@code be != null} explicitly.
     */
    boolean recognizesMain(ServerLevel level, BlockPos pos, @Nullable BlockEntity be);

    /**
     * The adapter-key-card id this adapter requires the provider to have
     * installed (see {@code AdapterIds} for the canonical list).
     *
     * <p>Returns {@code null} when no key card is required. Tiered adapters
     * (Extended Crafting's 4 table tiers) should return the id corresponding
     * to the **block at the queried position** so the slot can hold a single
     * higher-tier card covering multiple lower tiers via
     * {@code MultiblockAdapterItem.covers(...)}.
     */
    @Nullable
    default ResourceLocation requiredAdapterId(ServerLevel level, BlockPos pos) {
        return null;
    }

    /**
     * One-shot recipe + structure search, called at pattern-bind time.
     * Returns an opaque handle the adapter can interpret later, or null when
     * this adapter cannot serve the pattern at this lane.
     *
     * <p>Implementations should perform the full recipe lookup here (the
     * expensive step previously done on every push) so that subsequent
     * {@code planWithBinding} / {@code planVirtualWithBinding} calls can skip
     * directly to layout / validation.
     */
    @Nullable
    BindingResult bind(ServerLevel level, BlockPos mainPos, IPatternDetails pattern);

    /**
     * Fast-path check: can the multiblock accept a fresh dispatch right now?
     * Used as a pre-flight gate before {@link #planWithBinding}; failure routes
     * the lane into the per-lane cooldown schedule without searching recipes.
     */
    boolean canDispatch(ServerLevel level, BlockPos mainPos, Object handle);

    /**
     * Build a dispatch plan using the previously computed {@code handle}.
     * Implementations may still revalidate the recipe match against the live
     * world state, but must not perform a fresh full-table search.
     */
    @Nullable
    DispatchPlan planWithBinding(ServerLevel level, BlockPos mainPos,
                                  IPatternDetails pattern, KeyCounter[] inputs,
                                  Object handle, IActionSource source);

    /**
     * Actively pull allowed outputs from the multiblock into the provider's
     * return inventory. Implementations must remove any returned stacks from
     * the target machine.
     */
    List<GenericStack> extractOutputs(ServerLevel level, BlockPos mainPos,
                                      AllowedOutputFilter filter,
                                      IActionSource source);

    /**
     * Adapter-owned auto-return filter. The default is permissive: once an
     * adapter has validated the correct output position/slot, anything there
     * is considered a legitimate machine result. Adapters that read from a
     * shared inventory or broad dropped-item area can override this to keep a
     * pattern-level allow-list.
     */
    default boolean allowsAutoReturn(ServerLevel level, BlockPos mainPos,
                                     AllowedOutputFilter filter, AEKey key) {
        return true;
    }
}
