package com.moakiee.ae2lt.packaged.logic.multiblock.fa;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import com.moakiee.ae2lt.logic.AllowedOutputFilter;
import com.moakiee.ae2lt.overload.model.MatchMode;
import com.moakiee.ae2lt.overload.pattern.OverloadedProviderOnlyPatternDetails;
import com.moakiee.ae2lt.packaged.item.AdapterIds;
import com.moakiee.ae2lt.packaged.logic.multiblock.DispatchPlan;
import com.moakiee.ae2lt.packaged.logic.multiblock.InsertionStrategy;
import com.moakiee.ae2lt.packaged.logic.multiblock.MultiblockAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.TargetSlot;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingResult;

/**
 * Runtime adapter for Forbidden &amp; Arcanus's Hephaestus Forge.
 *
 * <p>The Forge is a single central BE holding the "main" ingredient in slot 4
 * and surrounded by up to 8 {@code pedestal} blocks within a 4-block POI
 * radius (FA uses the POI manager). Rituals are datapack-defined Holders
 * registered under {@code forbidden_arcanus:ritual} and store an essence cost
 * (aureal/souls/blood/experience) that the player must pre-fill via FA's own
 * essence pickup loop &mdash; the adapter does <strong>not</strong> manage
 * essence supply, only consumes from whatever the Forge already has.
 *
 * <p>Mode is {@link BindingMode#REAL}: a ritual has a fixed in-world tick
 * duration ({@link com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.Ritual#duration()}
 * which is 220 by default and visible magic-circle particles, so we can't
 * collapse it into a virtual evaluation.
 *
 * <p>We deliberately avoid {@link
 * com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.RitualManager#startRitual(net.minecraft.server.level.ServerPlayer, com.stal111.forbidden_arcanus.common.block.entity.forge.essence.EssencesStorage)}
 * because its sole {@code ServerPlayer} usage is {@code player.getUUID()} for
 * a fail/finish-advancement trigger. Instead we reflectively invoke the
 * private {@code RitualManager.setActiveRitual(Holder<Ritual>, UUID)} with
 * {@link net.minecraft.Util#NIL_UUID} and replicate the essence cost / magic
 * circle creation that {@code startRitual}'s lambda performs &mdash; no
 * FakePlayer required, no advancement noise.
 */
public final class ForbiddenArcanusHephaestusForgeAdapter implements MultiblockAdapter {

    private static final Logger LOG = LoggerFactory.getLogger("ae2ltpp/fa-hephaestus-forge");

    private static final String MOD_ID = "forbidden_arcanus";
    private static final Set<ResourceLocation> FORGE_BLOCKS = Set.of(
            faId("hephaestus_forge_tier_1"),
            faId("hephaestus_forge_tier_2"),
            faId("hephaestus_forge_tier_3"),
            faId("hephaestus_forge_tier_4"),
            faId("hephaestus_forge_tier_5"));
    private static final Set<ResourceLocation> PEDESTAL_BLOCKS = Set.of(
            faId("darkstone_pedestal"),
            faId("magnetized_darkstone_pedestal"));
    private static final ResourceLocation RITUAL_REGISTRY_PATH = faId("ritual");
    private static final ResourceKey<Registry<Object>> RITUAL_REGISTRY_KEY =
            ResourceKey.createRegistryKey(RITUAL_REGISTRY_PATH);

    private static final int MAIN_SLOT = 4;
    private static final int PEDESTAL_RADIUS = 4;
    private static final int MAX_INGREDIENTS = 24;
    private static final int MAX_INPUT_UNITS = 128;

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean recognizesMain(ServerLevel level, BlockPos pos, BlockEntity be) {
        return be != null && isFaLoaded() && isForgeBlock(be.getBlockState());
    }

    @Override
    public ResourceLocation requiredAdapterId(ServerLevel level, BlockPos pos) {
        return AdapterIds.FA_HEPHAESTUS_FORGE;
    }

    @Override
    @Nullable
    public BindingResult bind(ServerLevel level, BlockPos mainPos, IPatternDetails pattern) {
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)) {
            return null;
        }
        if (!hasSingleItemOutput(pattern)) {
            return null;
        }
        var candidate = findCandidateRitual(level, pattern);
        if (candidate == null) {
            LOG.info("bind: no ritual matched pattern output at {}", mainPos);
            return null;
        }
        LOG.info("bind: matched ritual at {} (inputs={}, mainCost={})",
                mainPos, candidate.inputs().size(), candidate.totalInputUnits());
        return new BindingResult(new ForgeBindHandle(candidate), BindingMode.REAL);
    }

    @Override
    public boolean canDispatch(ServerLevel level, BlockPos mainPos, Object handle) {
        if (!(handle instanceof ForgeBindHandle bind)) {
            return false;
        }
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)) {
            return false;
        }
        var ritualManager = FaReflection.getRitualManager(be);
        if (ritualManager == null) {
            return false;
        }
        if (FaReflection.isRitualActive(ritualManager)) {
            return false;
        }
        var handler = FaReflection.getItemHandler(be);
        if (handler == null || handler.getSlots() <= MAIN_SLOT) {
            return false;
        }
        if (!handler.getStackInSlot(MAIN_SLOT).isEmpty()) {
            return false;
        }
        if (findEmptyPedestals(level, mainPos).size() < bind.ritual().inputs().size()) {
            return false;
        }
        // Don't pre-flight essence: the player may be in the middle of feeding
        // it. The eventual reduce() in onCommit will no-op-underflow safely
        // because EssencesStorage clamps to 0 (see FA's EssencesStorage.reduce).
        return true;
    }

    @Override
    @Nullable
    public DispatchPlan planWithBinding(ServerLevel level, BlockPos mainPos,
                                        IPatternDetails pattern, KeyCounter[] inputs,
                                        Object handle, IActionSource source) {
        if (!(handle instanceof ForgeBindHandle bind)) {
            return null;
        }
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)) {
            return null;
        }

        var ritual = bind.ritual();
        var units = expandInputUnits(inputs);
        if (units == null || units.isEmpty()) {
            return null;
        }

        var match = matchInputs(units, ritual);
        if (match == null) {
            LOG.info("planWithBinding: runtime inputs ({} units) didn't match bound ritual at {}",
                    units.size(), mainPos);
            return null;
        }

        var pedestals = findEmptyPedestals(level, mainPos);
        if (pedestals.size() < match.pedestalUnits().size()) {
            return null;
        }

        var targets = new ArrayList<TargetSlot>(match.pedestalUnits().size() + 1);

        targets.add(new TargetSlot(
                level, mainPos, null,
                List.of(match.mainUnit().toGenericStack()),
                InsertionStrategy.CUSTOM,
                forgeMainInserter(level, mainPos, match.mainUnit())));

        for (int i = 0; i < match.pedestalUnits().size(); i++) {
            var unit = match.pedestalUnits().get(i);
            var pedestalPos = pedestals.get(i);
            targets.add(new TargetSlot(
                    level, pedestalPos, null,
                    List.of(unit.toGenericStack()),
                    InsertionStrategy.CUSTOM,
                    pedestalInserter(level, pedestalPos, unit)));
        }

        Runnable onCommit = () -> {
            var liveBe = level.getBlockEntity(mainPos);
            if (liveBe == null || !recognizesMain(level, mainPos, liveBe)) {
                return;
            }
            var rm = FaReflection.getRitualManager(liveBe);
            if (rm == null) {
                LOG.warn("onCommit: ritualManager null at {}", mainPos);
                return;
            }
            // After all inserters MODULATE, ValhelsiaContainerBlockEntity's
            // ItemStackHandler#onContentsChanged → onSlotChanged → onDataChanged
            // has already refreshed RitualManager.validRitual (synchronous in
            // setStackInSlot), so getValidRitual() should now return our
            // bound ritual. We still cross-check identity and abort silently
            // if FA decided differently (e.g. essence shortage after a
            // concurrent ritual depleted the buffer).
            var validHolder = FaReflection.getValidRitualHolder(rm);
            if (validHolder == null) {
                LOG.info("onCommit: getValidRitual() returned empty at {} (essence?)", mainPos);
                return;
            }
            var ritualValue = FaReflection.holderValue(validHolder);
            if (ritualValue != ritual.rawRitual()) {
                LOG.info("onCommit: valid ritual changed mid-dispatch at {} (have {}, want {})",
                        mainPos, ritualValue, ritual.rawRitual());
                return;
            }
            var ess = FaReflection.getEssenceStorage(liveBe);
            if (ess == null) {
                LOG.warn("onCommit: essenceStorage null at {}", mainPos);
                return;
            }
            if (!FaReflection.setActiveRitual(rm, validHolder, net.minecraft.Util.NIL_UUID)) {
                LOG.warn("onCommit: setActiveRitual reflection failed at {}", mainPos);
                return;
            }
            FaReflection.reduceEssence(ess, ritual.essenceCost());
            FaReflection.createMagicCircle(liveBe, level, mainPos, ritual.magicCircleType());
            FaReflection.raisePedestalItems(rm);
            liveBe.setChanged();
            LOG.info("onCommit: ritual activated at {}", mainPos);
        };

        return new DispatchPlan(List.copyOf(targets), onCommit);
    }

    @Override
    public List<GenericStack> extractOutputs(ServerLevel level, BlockPos mainPos,
                                             AllowedOutputFilter filter,
                                             IActionSource source) {
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)) {
            return List.of();
        }
        var rm = FaReflection.getRitualManager(be);
        if (rm == null || FaReflection.isRitualActive(rm)) {
            return List.of();
        }
        var handler = FaReflection.getItemHandler(be);
        if (handler == null || handler.getSlots() <= MAIN_SLOT) {
            return List.of();
        }
        var stack = handler.getStackInSlot(MAIN_SLOT);
        if (stack.isEmpty()) {
            return List.of();
        }
        var key = AEItemKey.of(stack);
        if (!allowsAutoReturn(level, mainPos, filter, key)) {
            return List.of();
        }
        if (!(handler instanceof IItemHandlerModifiable mod)) {
            return List.of();
        }
        var copy = stack.copy();
        mod.setStackInSlot(MAIN_SLOT, ItemStack.EMPTY);
        be.setChanged();
        return List.of(new GenericStack(AEItemKey.of(copy), copy.getCount()));
    }

    // ===== Inserters =====

    private static BiFunction<GenericStack, Actionable, Long> forgeMainInserter(
            ServerLevel level, BlockPos pos, PlannedUnit unit) {
        return (stack, mode) -> {
            if (stack.amount() != 1 || !unit.key().equals(stack.what())) {
                return 0L;
            }
            var be = level.getBlockEntity(pos);
            if (be == null || !isForgeBlock(be.getBlockState())) {
                return 0L;
            }
            var handler = FaReflection.getItemHandler(be);
            if (!(handler instanceof IItemHandlerModifiable mod)) {
                return 0L;
            }
            if (mod.getSlots() <= MAIN_SLOT || !mod.getStackInSlot(MAIN_SLOT).isEmpty()) {
                return 0L;
            }
            if (mode == Actionable.MODULATE) {
                mod.setStackInSlot(MAIN_SLOT, unit.stack());
                be.setChanged();
            }
            return 1L;
        };
    }

    private static BiFunction<GenericStack, Actionable, Long> pedestalInserter(
            ServerLevel level, BlockPos pos, PlannedUnit unit) {
        return (stack, mode) -> {
            if (stack.amount() != 1 || !unit.key().equals(stack.what())) {
                return 0L;
            }
            var be = level.getBlockEntity(pos);
            if (be == null || !isPedestalBlock(be.getBlockState())) {
                return 0L;
            }
            if (!FaReflection.pedestalEmpty(be)) {
                return 0L;
            }
            if (mode == Actionable.MODULATE) {
                if (!FaReflection.setPedestalStack(be, unit.stack())) {
                    return 0L;
                }
                be.setChanged();
            }
            return 1L;
        };
    }

    // ===== Ritual lookup / matching =====

    @Nullable
    private static RitualCandidate findCandidateRitual(ServerLevel level, IPatternDetails pattern) {
        var holders = ritualHolders(level);
        if (holders == null || holders.isEmpty()) {
            return null;
        }
        for (var holder : holders) {
            var ritualObj = FaReflection.holderValue(holder);
            if (ritualObj == null) {
                continue;
            }
            var result = FaReflection.getRitualCreateItemResult(ritualObj);
            if (result == null || result.isEmpty()) {
                continue;
            }
            if (!outputMatches(pattern, result)) {
                continue;
            }
            var candidate = RitualCandidate.from(ritualObj);
            if (candidate == null) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    /**
     * Greedy shapeless match: pull a main ingredient first, then assign one
     * unit to each ritual {@link RitualInputSpec ingredient} (amount-scaled).
     * FA itself doesn't care about pedestal order at finish time &mdash;
     * {@code Ritual.checkIngredients} validates the unordered collection
     * against ingredient × amount &mdash; so any consistent assignment works.
     */
    @Nullable
    private static InputMatch matchInputs(List<PlannedUnit> units, RitualCandidate ritual) {
        int total = 1;
        for (var input : ritual.inputs()) {
            total += input.amount();
        }
        if (units.size() != total) {
            return null;
        }
        boolean[] used = new boolean[units.size()];

        PlannedUnit mainUnit = null;
        for (int i = 0; i < units.size(); i++) {
            if (ritual.mainIngredient().test(units.get(i).stack())) {
                mainUnit = units.get(i);
                used[i] = true;
                break;
            }
        }
        if (mainUnit == null) {
            return null;
        }

        var pedestalUnits = new ArrayList<PlannedUnit>(total - 1);
        for (var input : ritual.inputs()) {
            for (int copy = 0; copy < input.amount(); copy++) {
                PlannedUnit picked = null;
                for (int i = 0; i < units.size(); i++) {
                    if (used[i]) continue;
                    if (input.ingredient().test(units.get(i).stack())) {
                        picked = units.get(i);
                        used[i] = true;
                        break;
                    }
                }
                if (picked == null) {
                    return null;
                }
                pedestalUnits.add(picked);
            }
        }
        return new InputMatch(mainUnit, List.copyOf(pedestalUnits));
    }

    // ===== Pedestal scanning =====

    /**
     * Walks a {@code [-4,-1,-4]..[+4,+1,+4]} cube around the Forge collecting
     * empty pedestals. We follow FA's own POI lookup radius (which is
     * {@code 4} on the horizontal plane) but widen ±1 vertically since
     * pedestals are sometimes placed at the Forge's y-level rather than
     * strictly below the {@code UpdateForgeIngredientsEffect} POI grid.
     */
    private static List<BlockPos> findEmptyPedestals(ServerLevel level, BlockPos mainPos) {
        var result = new ArrayList<BlockPos>();
        var cursor = new BlockPos.MutableBlockPos();
        for (int x = -PEDESTAL_RADIUS; x <= PEDESTAL_RADIUS; x++) {
            for (int z = -PEDESTAL_RADIUS; z <= PEDESTAL_RADIUS; z++) {
                for (int y = -1; y <= 1; y++) {
                    cursor.set(mainPos.getX() + x, mainPos.getY() + y, mainPos.getZ() + z);
                    if (cursor.equals(mainPos)) continue;
                    if (!level.isLoaded(cursor)) continue;
                    if (!isPedestalBlock(level.getBlockState(cursor))) continue;
                    var be = level.getBlockEntity(cursor);
                    if (be == null) continue;
                    if (!FaReflection.pedestalEmpty(be)) continue;
                    result.add(cursor.immutable());
                    if (result.size() >= MAX_INGREDIENTS) {
                        return result;
                    }
                }
            }
        }
        return result;
    }

    // ===== Helpers =====

    @Nullable
    private static List<PlannedUnit> expandInputUnits(KeyCounter[] inputs) {
        var units = new ArrayList<PlannedUnit>();
        long total = 0;
        for (var counter : inputs) {
            for (var entry : counter) {
                if (!(entry.getKey() instanceof AEItemKey itemKey)) {
                    return null;
                }
                long amount = entry.getLongValue();
                if (amount <= 0) continue;
                total += amount;
                if (total > MAX_INPUT_UNITS) return null;
                for (long i = 0; i < amount; i++) {
                    units.add(new PlannedUnit(itemKey));
                }
            }
        }
        return units;
    }

    private static boolean hasSingleItemOutput(IPatternDetails pattern) {
        var outputs = pattern.getOutputs();
        return outputs.size() == 1 && outputs.getFirst().what() instanceof AEItemKey;
    }

    private static boolean outputMatches(IPatternDetails pattern, ItemStack result) {
        var outputs = pattern.getOutputs();
        if (outputs.size() != 1) return false;
        var expected = outputs.getFirst();
        if (!(expected.what() instanceof AEItemKey expectedKey) || expected.amount() != result.getCount()) {
            return false;
        }
        var actual = AEItemKey.of(result);
        return outputMode(pattern, 0) == MatchMode.ID_ONLY
                ? expectedKey.dropSecondary().equals(actual.dropSecondary())
                : expectedKey.equals(actual);
    }

    private static MatchMode outputMode(IPatternDetails pattern, int outputIndex) {
        if (pattern instanceof OverloadedProviderOnlyPatternDetails overload) {
            var outputs = overload.overloadPatternDetailsView().outputs();
            if (outputIndex >= 0 && outputIndex < outputs.size()) {
                return outputs.get(outputIndex).matchMode();
            }
        }
        return MatchMode.STRICT;
    }

    /**
     * Pull every datapack-registered ritual via {@code level.registryAccess()
     * .lookupOrThrow(...)}. The {@link #RITUAL_REGISTRY_KEY} is typed
     * {@code <Object>} because the actual {@code Ritual} class isn't on our
     * compile classpath; we rely on per-element reflection in {@link
     * FaReflection#holderValue(Object)} to walk back to a real instance.
     */
    @Nullable
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<Object> ritualHolders(ServerLevel level) {
        try {
            var optional = level.registryAccess()
                    .lookup((ResourceKey) RITUAL_REGISTRY_KEY);
            if (optional.isEmpty()) {
                return null;
            }
            var registry = (net.minecraft.core.HolderLookup.RegistryLookup<?>) optional.get();
            var holders = new ArrayList<Object>();
            registry.listElements().forEach(holders::add);
            return holders;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static boolean isFaLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    private static ResourceLocation blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock());
    }

    private static boolean isForgeBlock(BlockState state) {
        return FORGE_BLOCKS.contains(blockId(state));
    }

    private static boolean isPedestalBlock(BlockState state) {
        return PEDESTAL_BLOCKS.contains(blockId(state));
    }

    private static ResourceLocation faId(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    // ===== Records =====

    private record PlannedUnit(AEItemKey key) {
        PlannedUnit {
            Objects.requireNonNull(key, "key");
        }

        ItemStack stack() {
            return key.toStack(1);
        }

        GenericStack toGenericStack() {
            return new GenericStack(key, 1);
        }
    }

    private record InputMatch(PlannedUnit mainUnit, List<PlannedUnit> pedestalUnits) {}

    private record RitualInputSpec(Ingredient ingredient, int amount) {}

    private record ForgeBindHandle(RitualCandidate ritual) {}

    /**
     * Pre-extracted view of an FA {@code Ritual} record so the matching loop
     * doesn't have to re-reflect on every input pairing. {@link #rawRitual()}
     * is held to allow identity-equality with the holder we re-resolve in
     * onCommit (FA may rebuild holders across reloads, but for an in-flight
     * single tick the identity is stable).
     */
    private record RitualCandidate(
            Object rawRitual,
            Ingredient mainIngredient,
            List<RitualInputSpec> inputs,
            Object essenceCost,
            Object magicCircleType
    ) {
        int totalInputUnits() {
            int sum = 0;
            for (var i : inputs) sum += i.amount();
            return sum;
        }

        @Nullable
        static RitualCandidate from(Object ritual) {
            var main = FaReflection.getMainIngredient(ritual);
            var rawInputs = FaReflection.getRitualInputs(ritual);
            var essences = FaReflection.getRitualEssences(ritual);
            var circle = FaReflection.getRitualMagicCircle(ritual);
            if (main == null || rawInputs == null) {
                return null;
            }
            var inputs = new ArrayList<RitualInputSpec>(rawInputs.size());
            for (var raw : rawInputs) {
                var ing = FaReflection.getRitualInputIngredient(raw);
                int amt = FaReflection.getRitualInputAmount(raw);
                if (ing == null || amt <= 0) {
                    return null;
                }
                inputs.add(new RitualInputSpec(ing, amt));
            }
            return new RitualCandidate(ritual, main, List.copyOf(inputs), essences, circle);
        }
    }

    // ===== Reflection =====

    /**
     * Single-binder reflection cache for Forbidden &amp; Arcanus &amp;
     * Valhelsia Core. Every accessor returns {@code null} / {@code false} on
     * any reflective failure so the adapter degrades to a no-op rather than
     * spamming throws.
     */
    private static final class FaReflection {
        private static final String FORGE_BE_CLASS =
                "com.stal111.forbidden_arcanus.common.block.entity.forge.HephaestusForgeBlockEntity";
        private static final String VALHELSIA_CONTAINER_CLASS =
                "net.valhelsia.valhelsia_core.api.common.block.entity.neoforge.ValhelsiaContainerBlockEntity";
        private static final String RITUAL_MANAGER_CLASS =
                "com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.RitualManager";
        private static final String ESSENCE_MANAGER_CLASS =
                "com.stal111.forbidden_arcanus.common.block.entity.forge.essence.EssenceManager";
        private static final String ESSENCES_STORAGE_CLASS =
                "com.stal111.forbidden_arcanus.common.block.entity.forge.essence.EssencesStorage";
        private static final String MAGIC_CIRCLE_CONTROLLER_CLASS =
                "com.stal111.forbidden_arcanus.common.block.entity.forge.circle.MagicCircleController";
        private static final String RITUAL_CLASS =
                "com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.Ritual";
        private static final String RITUAL_INPUT_CLASS =
                "com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.RitualInput";
        private static final String RITUAL_REQUIREMENTS_CLASS =
                "com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.RitualRequirements";
        private static final String CREATE_ITEM_RESULT_CLASS =
                "com.stal111.forbidden_arcanus.common.block.entity.forge.ritual.result.CreateItemResult";
        private static final String PEDESTAL_BE_CLASS =
                "com.stal111.forbidden_arcanus.common.block.entity.PedestalBlockEntity";
        private static final String PEDESTAL_EFFECT_TRIGGER_CLASS =
                "com.stal111.forbidden_arcanus.common.block.pedestal.effect.PedestalEffectTrigger";

        private static volatile boolean lookupDone;
        private static volatile @Nullable Class<?> forgeBeClass;
        private static volatile @Nullable Method getItemStackHandlerMethod;
        private static volatile @Nullable Method getRitualManagerMethod;
        private static volatile @Nullable Method getEssenceManagerMethod;
        private static volatile @Nullable Method getMagicCircleControllerMethod;
        private static volatile @Nullable Method getStorageMethod;
        private static volatile @Nullable Method essencesReduceMethod;
        private static volatile @Nullable Method isRitualActiveMethod;
        private static volatile @Nullable Method getValidRitualMethod;
        private static volatile @Nullable Method setActiveRitualMethod;
        private static volatile @Nullable Method magicCircleCreateMethod;
        private static volatile @Nullable Method ritualMainIngredientMethod;
        private static volatile @Nullable Method ritualInputsMethod;
        private static volatile @Nullable Method ritualRequirementsMethod;
        private static volatile @Nullable Method ritualMagicCircleTypeMethod;
        private static volatile @Nullable Method ritualResultMethod;
        private static volatile @Nullable Method requirementsEssencesMethod;
        private static volatile @Nullable Method ritualInputIngredientMethod;
        private static volatile @Nullable Method ritualInputAmountMethod;
        private static volatile @Nullable Class<?> createItemResultClass;
        private static volatile @Nullable Field createItemResultStackField;
        private static volatile @Nullable Class<?> pedestalBeClass;
        private static volatile @Nullable Field pedestalStackField;
        private static volatile @Nullable Method pedestalSetStackMethod;
        private static volatile @Nullable Object pedestalTriggerPlace;
        private static volatile @Nullable Method forEachPedestalMethod;
        private static volatile @Nullable Method setItemHeightTargetMethod;

        @Nullable
        static IItemHandler getItemHandler(BlockEntity be) {
            ensureLookup();
            if (getItemStackHandlerMethod == null) return null;
            try {
                var v = getItemStackHandlerMethod.invoke(be);
                return v instanceof IItemHandler h ? h : null;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        @Nullable
        static Object getRitualManager(Object forgeBe) {
            ensureLookup();
            if (getRitualManagerMethod == null) return null;
            try {
                return getRitualManagerMethod.invoke(forgeBe);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        @Nullable
        static Object getEssenceStorage(Object forgeBe) {
            ensureLookup();
            if (getEssenceManagerMethod == null || getStorageMethod == null) return null;
            try {
                var em = getEssenceManagerMethod.invoke(forgeBe);
                if (em == null) return null;
                return getStorageMethod.invoke(em);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        static boolean isRitualActive(Object ritualManager) {
            ensureLookup();
            if (isRitualActiveMethod == null) return false;
            try {
                var v = isRitualActiveMethod.invoke(ritualManager);
                return v instanceof Boolean b && b;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return false;
            }
        }

        @Nullable
        static Object getValidRitualHolder(Object ritualManager) {
            ensureLookup();
            if (getValidRitualMethod == null) return null;
            try {
                var v = getValidRitualMethod.invoke(ritualManager);
                if (!(v instanceof java.util.Optional<?> opt) || opt.isEmpty()) {
                    return null;
                }
                return opt.get();
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        static boolean setActiveRitual(Object ritualManager, Object holder, UUID uuid) {
            ensureLookup();
            if (setActiveRitualMethod == null) return false;
            try {
                setActiveRitualMethod.invoke(ritualManager, holder, uuid);
                return true;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                LOG.warn("setActiveRitual reflection failed: {}", e.toString());
                return false;
            }
        }

        static void reduceEssence(Object storage, @Nullable Object essences) {
            ensureLookup();
            if (essencesReduceMethod == null || essences == null) return;
            try {
                essencesReduceMethod.invoke(storage, essences);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                LOG.warn("EssencesStorage.reduce failed: {}", e.toString());
            }
        }

        static void createMagicCircle(Object forgeBe, ServerLevel level, BlockPos pos,
                                      @Nullable Object magicCircleType) {
            ensureLookup();
            if (getMagicCircleControllerMethod == null
                    || magicCircleCreateMethod == null
                    || magicCircleType == null) {
                return;
            }
            try {
                var controller = getMagicCircleControllerMethod.invoke(forgeBe);
                if (controller == null) return;
                magicCircleCreateMethod.invoke(controller, level, pos, magicCircleType);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            }
        }

        /**
         * Mirrors {@code RitualManager#lambda$startRitual$4} so dispatched
         * pedestals start their lift animation immediately instead of waiting
         * 220 ticks for FA to discover the active ritual indirectly.
         */
        static void raisePedestalItems(Object ritualManager) {
            ensureLookup();
            if (forEachPedestalMethod == null || setItemHeightTargetMethod == null) {
                return;
            }
            try {
                java.util.function.Predicate<Object> filter = pe -> true;
                java.util.function.Consumer<Object> consumer = pe -> {
                    try {
                        setItemHeightTargetMethod.invoke(pe, 140);
                    } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                    }
                };
                forEachPedestalMethod.invoke(ritualManager, filter, consumer);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            }
        }

        @Nullable
        static Object holderValue(Object holder) {
            if (holder instanceof Holder<?> h) {
                try {
                    return h.value();
                } catch (RuntimeException | LinkageError ignored) {
                    return null;
                }
            }
            return null;
        }

        @Nullable
        static Ingredient getMainIngredient(Object ritual) {
            ensureLookup();
            if (ritualMainIngredientMethod == null) return null;
            try {
                var v = ritualMainIngredientMethod.invoke(ritual);
                return v instanceof Ingredient i && !i.isEmpty() ? i : null;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        @Nullable
        static List<?> getRitualInputs(Object ritual) {
            ensureLookup();
            if (ritualInputsMethod == null) return null;
            try {
                var v = ritualInputsMethod.invoke(ritual);
                return v instanceof List<?> l ? l : null;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        @Nullable
        static Object getRitualEssences(Object ritual) {
            ensureLookup();
            if (ritualRequirementsMethod == null || requirementsEssencesMethod == null) return null;
            try {
                var req = ritualRequirementsMethod.invoke(ritual);
                if (req == null) return null;
                return requirementsEssencesMethod.invoke(req);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        @Nullable
        static Object getRitualMagicCircle(Object ritual) {
            ensureLookup();
            if (ritualMagicCircleTypeMethod == null) return null;
            try {
                return ritualMagicCircleTypeMethod.invoke(ritual);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        @Nullable
        static ItemStack getRitualCreateItemResult(Object ritual) {
            ensureLookup();
            if (ritualResultMethod == null
                    || createItemResultClass == null
                    || createItemResultStackField == null) {
                return null;
            }
            try {
                var result = ritualResultMethod.invoke(ritual);
                if (result == null || !createItemResultClass.isInstance(result)) {
                    return null;
                }
                var stack = createItemResultStackField.get(result);
                return stack instanceof ItemStack s ? s : null;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        @Nullable
        static Ingredient getRitualInputIngredient(Object ritualInput) {
            ensureLookup();
            if (ritualInputIngredientMethod == null) return null;
            try {
                var v = ritualInputIngredientMethod.invoke(ritualInput);
                return v instanceof Ingredient i && !i.isEmpty() ? i : null;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        static int getRitualInputAmount(Object ritualInput) {
            ensureLookup();
            if (ritualInputAmountMethod == null) return 0;
            try {
                var v = ritualInputAmountMethod.invoke(ritualInput);
                return v instanceof Integer i ? i : 0;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return 0;
            }
        }

        static boolean pedestalEmpty(BlockEntity be) {
            ensureLookup();
            if (pedestalBeClass == null || !pedestalBeClass.isInstance(be) || pedestalStackField == null) {
                return false;
            }
            try {
                var stack = pedestalStackField.get(be);
                return stack instanceof ItemStack s && s.isEmpty();
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return false;
            }
        }

        static boolean setPedestalStack(BlockEntity be, ItemStack stack) {
            ensureLookup();
            if (pedestalSetStackMethod == null || pedestalTriggerPlace == null) {
                return false;
            }
            try {
                pedestalSetStackMethod.invoke(be, stack, null, pedestalTriggerPlace);
                return true;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                LOG.warn("PedestalBlockEntity#setStack failed: {}", e.toString());
                return false;
            }
        }

        private static void ensureLookup() {
            if (lookupDone) return;
            synchronized (FaReflection.class) {
                if (lookupDone) return;
                doLookup();
                lookupDone = true;
            }
        }

        @SuppressWarnings("unchecked")
        private static void doLookup() {
            forgeBeClass = tryClass(FORGE_BE_CLASS);
            var valhelsiaClass = tryClass(VALHELSIA_CONTAINER_CLASS);
            if (valhelsiaClass != null) {
                getItemStackHandlerMethod = tryMethod(valhelsiaClass, "getItemStackHandler");
            }
            if (forgeBeClass != null) {
                getRitualManagerMethod = tryMethod(forgeBeClass, "getRitualManager");
                getEssenceManagerMethod = tryMethod(forgeBeClass, "getEssenceManager");
                getMagicCircleControllerMethod = tryMethod(forgeBeClass, "getMagicCircleController");
            }
            var essenceManagerClass = tryClass(ESSENCE_MANAGER_CLASS);
            if (essenceManagerClass != null) {
                getStorageMethod = tryMethod(essenceManagerClass, "getStorage");
            }
            var essencesStorageClass = tryClass(ESSENCES_STORAGE_CLASS);
            var essencesDefinitionClass = tryClass(
                    "com.stal111.forbidden_arcanus.common.block.entity.forge.essence.EssencesDefinition");
            if (essencesStorageClass != null && essencesDefinitionClass != null) {
                essencesReduceMethod = tryMethod(essencesStorageClass, "reduce", essencesDefinitionClass);
            }
            var ritualManagerClass = tryClass(RITUAL_MANAGER_CLASS);
            if (ritualManagerClass != null) {
                isRitualActiveMethod = tryMethod(ritualManagerClass, "isRitualActive");
                getValidRitualMethod = tryMethod(ritualManagerClass, "getValidRitual");
                setActiveRitualMethod = tryDeclaredMethod(ritualManagerClass, "setActiveRitual",
                        Holder.class, UUID.class);
                forEachPedestalMethod = tryDeclaredMethod(ritualManagerClass, "forEachPedestal",
                        java.util.function.Predicate.class, java.util.function.Consumer.class);
            }
            var mcControllerClass = tryClass(MAGIC_CIRCLE_CONTROLLER_CLASS);
            var mcTypeClass = tryClass(
                    "com.stal111.forbidden_arcanus.common.block.entity.forge.circle.MagicCircleType");
            if (mcControllerClass != null && mcTypeClass != null) {
                magicCircleCreateMethod = tryMethod(mcControllerClass, "createMagicCircle",
                        ServerLevel.class, BlockPos.class, Holder.class);
            }
            var ritualClass = tryClass(RITUAL_CLASS);
            if (ritualClass != null) {
                ritualMainIngredientMethod = tryMethod(ritualClass, "mainIngredient");
                ritualInputsMethod = tryMethod(ritualClass, "inputs");
                ritualRequirementsMethod = tryMethod(ritualClass, "requirements");
                ritualMagicCircleTypeMethod = tryMethod(ritualClass, "magicCircleType");
                ritualResultMethod = tryMethod(ritualClass, "result");
            }
            var requirementsClass = tryClass(RITUAL_REQUIREMENTS_CLASS);
            if (requirementsClass != null) {
                requirementsEssencesMethod = tryMethod(requirementsClass, "essences");
            }
            var ritualInputClass = tryClass(RITUAL_INPUT_CLASS);
            if (ritualInputClass != null) {
                ritualInputIngredientMethod = tryMethod(ritualInputClass, "ingredient");
                ritualInputAmountMethod = tryMethod(ritualInputClass, "amount");
            }
            createItemResultClass = tryClass(CREATE_ITEM_RESULT_CLASS);
            if (createItemResultClass != null) {
                createItemResultStackField = tryDeclaredField(createItemResultClass, "result");
            }
            pedestalBeClass = tryClass(PEDESTAL_BE_CLASS);
            if (pedestalBeClass != null) {
                pedestalStackField = tryDeclaredField(pedestalBeClass, "stack");
                var pedestalEffectTriggerClass = tryClass(PEDESTAL_EFFECT_TRIGGER_CLASS);
                if (pedestalEffectTriggerClass != null) {
                    pedestalSetStackMethod = tryMethod(pedestalBeClass, "setStack",
                            ItemStack.class,
                            net.minecraft.world.entity.player.Player.class,
                            pedestalEffectTriggerClass);
                    pedestalTriggerPlace = enumValue(pedestalEffectTriggerClass, "PLAYER_PLACE_ITEM");
                }
            }
            var pedestalBeClassLocal = pedestalBeClass;
            if (pedestalBeClassLocal != null) {
                setItemHeightTargetMethod = tryMethod(pedestalBeClassLocal, "setItemHeightTarget", int.class);
            }
        }

        @Nullable
        private static Class<?> tryClass(String name) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException | LinkageError ignored) {
                return null;
            }
        }

        @Nullable
        private static Method tryMethod(Class<?> declaring, String name, Class<?>... params) {
            try {
                return declaring.getMethod(name, params);
            } catch (NoSuchMethodException | SecurityException | LinkageError ignored) {
                return null;
            }
        }

        @Nullable
        private static Method tryDeclaredMethod(Class<?> declaring, String name, Class<?>... params) {
            try {
                var m = declaring.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException | SecurityException | LinkageError ignored) {
                return null;
            }
        }

        @Nullable
        private static Field tryDeclaredField(Class<?> declaring, String name) {
            try {
                var f = declaring.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException | SecurityException | LinkageError ignored) {
                return null;
            }
        }

        @Nullable
        @SuppressWarnings({"rawtypes", "unchecked"})
        private static Object enumValue(Class<?> enumClass, String name) {
            if (!enumClass.isEnum()) return null;
            try {
                return Enum.valueOf((Class<Enum>) enumClass, name);
            } catch (IllegalArgumentException | LinkageError ignored) {
                return null;
            }
        }
    }
}
