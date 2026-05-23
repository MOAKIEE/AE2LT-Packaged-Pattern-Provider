package com.moakiee.ae2lt.packaged.logic.multiblock.ec;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

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
import com.moakiee.ae2lt.packaged.logic.multiblock.VirtualCraftingAdapter;
import com.moakiee.ae2lt.packaged.logic.multiblock.VirtualCraftingResult;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingMode;
import com.moakiee.ae2lt.packaged.logic.multiblock.binding.BindingResult;

/**
 * Runtime adapter for ExtendedCrafting's ordinary crafting tables.
 * Pure reflection: ExtendedCrafting is not a compile dependency.
 *
 * Supported blocks:
 *   basic_table    3x3, tier 1
 *   advanced_table 5x5, tier 2
 *   elite_table    7x7, tier 3
 *   ultimate_table 9x9, tier 4
 *
 * These tables act as virtual crafting lanes for packaged providers. Inputs are
 * consumed by AE, while this adapter validates the matching table recipe and
 * returns the assembled result plus remaining items without inserting into the
 * table grid.
 */
public final class ExtendedCraftingTableAdapter implements VirtualCraftingAdapter {

    private static final String MOD_ID = "extendedcrafting";
    private static final ResourceLocation BASIC_TABLE_BLOCK = ecId("basic_table");
    private static final ResourceLocation ADVANCED_TABLE_BLOCK = ecId("advanced_table");
    private static final ResourceLocation ELITE_TABLE_BLOCK = ecId("elite_table");
    private static final ResourceLocation ULTIMATE_TABLE_BLOCK = ecId("ultimate_table");
    private static final ResourceLocation RECIPE_TYPE_ID = ecId("table");

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean recognizesMain(ServerLevel level, BlockPos pos, BlockEntity be) {
        return be != null
                && isExtendedCraftingLoaded()
                && tableSpec(be.getBlockState()) != null
                && EcReflection.isSupportedTable(be);
    }

    @Override
    @org.jetbrains.annotations.Nullable
    public net.minecraft.resources.ResourceLocation requiredAdapterId(ServerLevel level, BlockPos pos) {
        // Tier id matches the live block at the queried position. Higher-tier
        // adapter cards cover all lower tiers via MultiblockAdapterItem.covers,
        // so a single ec_ultimate card unlocks every EC table.
        var spec = tableSpec(level.getBlockState(pos));
        if (spec == null) {
            return AdapterIds.EC_BASIC;
        }
        return switch (spec.tier()) {
            case 1 -> AdapterIds.EC_BASIC;
            case 2 -> AdapterIds.EC_ADVANCED;
            case 3 -> AdapterIds.EC_ELITE;
            case 4 -> AdapterIds.EC_ULTIMATE;
            default -> AdapterIds.EC_BASIC;
        };
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
        var spec = tableSpec(be.getBlockState());
        if (spec == null) {
            return null;
        }
        return new BindingResult(spec, BindingMode.VIRTUAL);
    }

    @Override
    public boolean canDispatch(ServerLevel level, BlockPos mainPos, Object handle) {
        // Virtual lanes have no machine state to check; the actual grid-empty
        // gate runs inside planVirtualWithBinding because EC tables share the
        // physical grid with player interaction.
        return true;
    }

    @Override
    @Nullable
    public DispatchPlan planWithBinding(ServerLevel level, BlockPos mainPos,
                                        IPatternDetails pattern, KeyCounter[] inputs,
                                        Object handle, IActionSource source) {
        // Virtual adapters never produce a real DispatchPlan; route through
        // planVirtualWithBinding instead.
        return null;
    }

    @Override
    @Nullable
    public VirtualCraftingResult planVirtualWithBinding(ServerLevel level, BlockPos mainPos,
                                                        IPatternDetails pattern, KeyCounter[] inputs,
                                                        Object handle, IActionSource source) {
        if (!(handle instanceof TableSpec spec)) {
            return null;
        }

        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be) || !spec.matches(be.getBlockState())) {
            return null;
        }

        var handler = EcReflection.getHandler(be);
        if (handler == null || handler.getSlots() < spec.slots() || !gridIsEmpty(handler, spec.slots())) {
            return null;
        }

        // New algorithm (user-specified):
        //   1. Read pattern.output -> patternOutAmount, patternOutKey.
        //   2. For each candidate recipe:
        //        K = patternOutAmount / recipe.result.count (must divide)
        //        Then for each non-empty ingredient, find a provided item
        //        whose item id matches the ingredient (NBT-loose) and which
        //        has at least K available; deduct K.
        //        After all ingredients consumed, provided must be exactly
        //        empty (no swallowing).
        //   3. The matched recipe + per-craft layout + K drive the output.
        var match = resolveBatchMatch(level, pattern, inputs, spec);
        if (match == null) {
            return null;
        }

        var planned = layoutGridFromIngredients(match.recipe(), match.perCraftStacks(), spec);
        if (planned == null) {
            return null;
        }

        var input = EcReflection.createTableInput(spec.gridSize(), stacksForPlan(spec.slots(), planned), spec.tier());
        if (input == null || !recipeMatches(match.recipe(), input, level)) {
            return null;
        }

        var result = assemble(match.recipe(), input, level);
        if (result == null || result.isEmpty()) {
            return null;
        }
        long totalCount = (long) match.craftRatio() * result.getCount();
        if (totalCount <= 0 || totalCount > Integer.MAX_VALUE) {
            return null;
        }
        if (!outputMatchesBatch(pattern, result, totalCount)) {
            return null;
        }

        var remaining = remainingItems(match.recipe(), input);
        if (remaining == null) {
            return null;
        }

        var outputs = virtualOutputs(result, remaining, match.craftRatio());
        if (outputs.isEmpty()) {
            return null;
        }
        return new VirtualCraftingResult(outputs);
    }

    @Override
    public List<GenericStack> extractOutputs(ServerLevel level, BlockPos mainPos,
                                             AllowedOutputFilter filter,
                                             IActionSource source) {
        var be = level.getBlockEntity(mainPos);
        if (be == null || !recognizesMain(level, mainPos, be)) {
            return List.of();
        }

        var spec = tableSpec(be.getBlockState());
        var handler = EcReflection.getHandler(be);
        if (spec == null || !(handler instanceof IItemHandlerModifiable modifiable)
                || handler.getSlots() < spec.slots() || gridIsEmpty(handler, spec.slots())) {
            return List.of();
        }

        var input = EcReflection.createTableInput(spec.gridSize(), currentStacks(handler, spec.slots()), spec.tier());
        if (input == null) {
            return List.of();
        }

        var recipe = findMatchingRecipe(level, input);
        if (recipe == null) {
            return List.of();
        }

        var result = assemble(recipe, input, level);
        if (result == null || result.isEmpty()) {
            return List.of();
        }

        var key = AEItemKey.of(result);
        if (!filter.matches(key)) {
            return List.of();
        }

        var remaining = remainingItems(recipe, input);
        if (remaining == null || !canApplyRemaining(handler, spec.gridSize(), input, remaining)) {
            return List.of();
        }

        applyCraftRemainders(modifiable, spec.gridSize(), input, remaining);
        return List.of(new GenericStack(AEItemKey.of(result), result.getCount()));
    }

    private static List<GenericStack> virtualOutputs(ItemStack result, NonNullList<ItemStack> remaining,
                                                     int craftRatio) {
        var outputs = new ArrayList<GenericStack>();
        outputs.add(new GenericStack(AEItemKey.of(result), (long) craftRatio * result.getCount()));
        for (var stack : remaining) {
            if (!stack.isEmpty()) {
                outputs.add(new GenericStack(AEItemKey.of(stack), (long) craftRatio * stack.getCount()));
            }
        }
        return List.copyOf(outputs);
    }

    /**
     * Resolves the batch multiplier K and the matching recipe by:
     *
     * <ol>
     *   <li>Reading the pattern's single primary output amount.</li>
     *   <li>Iterating every EC table recipe whose result item id equals the
     *       pattern's output item id, and checking
     *       {@code K = patternOutAmount / recipe.result.count} divides
     *       cleanly.</li>
     *   <li>Aggregating the {@link KeyCounter}-provided inputs by item id
     *       (NBT-loose) and trying to satisfy each non-empty ingredient by
     *       consuming exactly K units from one of the provided items.</li>
     *   <li>Confirming nothing is left over (no swallowing) and that the
     *       pattern output mode (STRICT vs ID_ONLY) is honored.</li>
     * </ol>
     *
     * <p>"NBT-loose" means an ingredient {@code ing} matches a provided
     * {@link AEItemKey} {@code key} when either {@code ing.test(key.toStack(1))}
     * succeeds, or any of {@code ing.getItems()} carries the same vanilla
     * {@link Item} as {@code key}. Pattern inputs may carry NBT/component
     * variants that the ingredient predicate would otherwise reject — by
     * relaxing to item-id equality we cover those cases.
     *
     * <p>The grid layout returned alongside the recipe uses one
     * representative stack per ingredient (preferring the provided key when
     * it strictly passes the ingredient, otherwise falling back to the
     * item's default instance) so that
     * {@link Recipe#matches}/{@link Recipe#assemble} run cleanly.
     */
    @Nullable
    private static BatchMatch resolveBatchMatch(ServerLevel level, IPatternDetails pattern,
                                                KeyCounter[] inputs, TableSpec spec) {
        if (!hasSingleItemOutput(pattern)) {
            return null;
        }

        var patternOut = pattern.getOutputs().getFirst();
        if (!(patternOut.what() instanceof AEItemKey patternOutKey) || patternOut.amount() <= 0) {
            return null;
        }
        long patternOutAmount = patternOut.amount();

        var provided = new LinkedHashMap<Item, Long>();
        var keysByItem = new HashMap<Item, AEItemKey>();
        for (var counter : inputs) {
            for (var entry : counter) {
                if (!(entry.getKey() instanceof AEItemKey itemKey)) {
                    return null;
                }
                long amount = entry.getLongValue();
                if (amount <= 0) {
                    continue;
                }
                var item = itemKey.getItem();
                provided.merge(item, amount, Long::sum);
                keysByItem.putIfAbsent(item, itemKey);
            }
        }
        if (provided.isEmpty()) {
            return null;
        }

        var outputMode = outputMode(pattern, 0);

        for (var holder : recipes(level)) {
            var recipe = holder.value();
            var resultPreview = resultItem(recipe, level);
            if (resultPreview == null || resultPreview.isEmpty()) {
                continue;
            }
            if (resultPreview.getItem() != patternOutKey.getItem()) {
                continue;
            }
            if (outputMode == MatchMode.STRICT
                    && !patternOutKey.equals(AEItemKey.of(resultPreview))) {
                continue;
            }

            int resultCount = resultPreview.getCount();
            if (resultCount <= 0 || patternOutAmount % resultCount != 0) {
                continue;
            }
            long k = patternOutAmount / resultCount;
            if (k <= 0 || k > Integer.MAX_VALUE) {
                continue;
            }

            var ingredients = ingredients(recipe);
            if (ingredients == null) {
                continue;
            }
            int ingredientCount = 0;
            for (var ing : ingredients) {
                if (!ing.isEmpty()) {
                    ingredientCount++;
                }
            }
            if (ingredientCount == 0 || ingredientCount > spec.slots()) {
                continue;
            }

            var working = new LinkedHashMap<>(provided);
            var perCraftStacks = new ArrayList<ItemStack>(ingredientCount);
            boolean ok = true;
            for (var ing : ingredients) {
                if (ing.isEmpty()) {
                    continue;
                }
                ItemStack chosenGridStack = pickStackForIngredient(ing, working, keysByItem, k);
                if (chosenGridStack == null) {
                    ok = false;
                    break;
                }
                perCraftStacks.add(chosenGridStack);
            }
            if (!ok) {
                continue;
            }

            boolean leftover = false;
            for (var v : working.values()) {
                if (v > 0) {
                    leftover = true;
                    break;
                }
            }
            if (leftover) {
                continue;
            }

            return new BatchMatch((int) k, recipe, List.copyOf(perCraftStacks));
        }
        return null;
    }

    /**
     * Picks one item from the provided pool that satisfies the ingredient.
     *
     * <p>Order of preference:
     * <ol>
     *   <li>Strict {@code ing.test(providedKey.toStack(1))} — preserves the
     *       NBT/components of the pattern-provided key on the grid.</li>
     *   <li>NBT-loose item-id match against {@code ing.getItems()} — falls
     *       back to the item's default instance so that ingredients which
     *       check components (e.g. an enchanted-book ingredient) still
     *       accept a bare provided variant.</li>
     * </ol>
     *
     * <p>Deducts {@code k} from the chosen item's pool on success; returns
     * {@code null} when no provided item meets the criteria.
     */
    @Nullable
    private static ItemStack pickStackForIngredient(Ingredient ing,
                                                    LinkedHashMap<Item, Long> working,
                                                    Map<Item, AEItemKey> keysByItem,
                                                    long k) {
        ItemStack strictChoice = null;
        Item strictItem = null;
        ItemStack looseChoice = null;
        Item looseItem = null;
        for (var entry : working.entrySet()) {
            if (entry.getValue() < k) {
                continue;
            }
            var item = entry.getKey();
            var key = keysByItem.get(item);
            if (key == null) {
                continue;
            }
            var strictStack = key.toStack(1);
            if (ing.test(strictStack)) {
                strictChoice = strictStack;
                strictItem = item;
                break;
            }
            if (looseChoice == null && ingredientAcceptsItemId(ing, item)) {
                looseChoice = item.getDefaultInstance();
                looseItem = item;
            }
        }
        ItemStack chosenStack;
        Item chosenItem;
        if (strictChoice != null) {
            chosenStack = strictChoice;
            chosenItem = strictItem;
        } else if (looseChoice != null) {
            chosenStack = looseChoice;
            chosenItem = looseItem;
        } else {
            return null;
        }
        long remaining = working.get(chosenItem) - k;
        if (remaining == 0) {
            working.remove(chosenItem);
        } else {
            working.put(chosenItem, remaining);
        }
        return chosenStack;
    }

    private static boolean ingredientAcceptsItemId(Ingredient ing, Item item) {
        try {
            for (var candidate : ing.getItems()) {
                if (candidate.getItem() == item) {
                    return true;
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
        return false;
    }

    /**
     * Places one ingredient's representative stack into a grid layout the
     * EC {@code TableCraftingInput.of} factory can trim and feed to
     * {@link Recipe#matches}.
     *
     * <p>For shaped recipes we walk the ingredient list in its native
     * {@code width × height} order and copy non-empty cells to the top-left
     * of the table grid. EC's input wrapper trims the empty borders, so the
     * absolute placement doesn't matter as long as the relative shape is
     * preserved.
     *
     * <p>For shapeless recipes we drop the stacks row-major into the top of
     * the grid; shapeless matching ignores positions.
     */
    @Nullable
    private static List<PlannedUnit> layoutGridFromIngredients(Object recipe,
                                                               List<ItemStack> perCraftStacks,
                                                               TableSpec spec) {
        var ingredients = ingredients(recipe);
        if (ingredients == null || ingredients.isEmpty()) {
            return null;
        }
        int gridSize = spec.gridSize();
        int slots = spec.slots();

        var width = EcReflection.recipeWidth(recipe);
        if (width == null) {
            if (perCraftStacks.size() > slots) {
                return null;
            }
            var planned = new ArrayList<PlannedUnit>(perCraftStacks.size());
            for (int i = 0; i < perCraftStacks.size(); i++) {
                int row = i / gridSize;
                int col = i % gridSize;
                if (row >= gridSize) {
                    return null;
                }
                planned.add(new PlannedUnit(row * gridSize + col, perCraftStacks.get(i).copy()));
            }
            return List.copyOf(planned);
        }

        if (width <= 0 || width > gridSize) {
            return null;
        }
        int height = (ingredients.size() + width - 1) / width;
        if (height > gridSize) {
            return null;
        }

        var planned = new ArrayList<PlannedUnit>(perCraftStacks.size());
        int nonEmptyIdx = 0;
        for (int relRow = 0; relRow < height; relRow++) {
            for (int relCol = 0; relCol < width; relCol++) {
                int ingIdx = relRow * width + relCol;
                if (ingIdx >= ingredients.size()) {
                    break;
                }
                var ing = ingredients.get(ingIdx);
                if (ing.isEmpty()) {
                    continue;
                }
                if (nonEmptyIdx >= perCraftStacks.size()) {
                    return null;
                }
                int slot = relRow * gridSize + relCol;
                planned.add(new PlannedUnit(slot, perCraftStacks.get(nonEmptyIdx++).copy()));
            }
        }
        if (nonEmptyIdx != perCraftStacks.size()) {
            return null;
        }
        return List.copyOf(planned);
    }

    @Nullable
    private static Object findMatchingRecipe(ServerLevel level, CraftingInput input) {
        for (var holder : recipes(level)) {
            var recipe = holder.value();
            if (recipeMatches(recipe, input, level)) {
                return recipe;
            }
        }
        return null;
    }

    private static boolean canApplyRemaining(IItemHandler handler, int gridSize, CraftingInput input,
                                             NonNullList<ItemStack> remaining) {
        if (remaining.size() < input.size()) {
            return false;
        }
        int top = EcReflection.top(input);
        int left = EcReflection.left(input);
        for (int y = 0; y < input.height(); y++) {
            for (int x = 0; x < input.width(); x++) {
                int inputIndex = x + y * input.width();
                int slot = x + left + (y + top) * gridSize;
                var current = handler.getStackInSlot(slot);
                if (current.isEmpty()) {
                    continue;
                }

                var remainder = remaining.get(inputIndex);
                if (remainder.isEmpty()) {
                    continue;
                }

                int afterConsume = current.getCount() - 1;
                if (afterConsume <= 0) {
                    continue;
                }
                if (!ItemStack.isSameItemSameComponents(current, remainder)) {
                    return false;
                }
                if (afterConsume + remainder.getCount() > current.getMaxStackSize()) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void applyCraftRemainders(IItemHandlerModifiable handler, int gridSize, CraftingInput input,
                                             NonNullList<ItemStack> remaining) {
        int top = EcReflection.top(input);
        int left = EcReflection.left(input);
        for (int y = 0; y < input.height(); y++) {
            for (int x = 0; x < input.width(); x++) {
                int inputIndex = x + y * input.width();
                int slot = x + left + (y + top) * gridSize;
                var current = handler.getStackInSlot(slot);
                if (!current.isEmpty()) {
                    handler.extractItem(slot, 1, false);
                }

                var remainder = remaining.get(inputIndex);
                if (remainder.isEmpty()) {
                    continue;
                }

                var after = handler.getStackInSlot(slot);
                if (after.isEmpty()) {
                    handler.setStackInSlot(slot, remainder.copy());
                } else if (ItemStack.isSameItemSameComponents(after, remainder)) {
                    var combined = after.copy();
                    combined.grow(remainder.getCount());
                    handler.setStackInSlot(slot, combined);
                }
            }
        }
    }

    private static boolean gridIsEmpty(IItemHandler handler, int slots) {
        for (int i = 0; i < slots; i++) {
            if (!handler.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static List<ItemStack> stacksForPlan(int slots, List<PlannedUnit> planned) {
        var stacks = NonNullList.withSize(slots, ItemStack.EMPTY);
        for (var unit : planned) {
            stacks.set(unit.slot(), unit.stack().copy());
        }
        return stacks;
    }

    private static List<ItemStack> currentStacks(IItemHandler handler, int slots) {
        var stacks = NonNullList.withSize(slots, ItemStack.EMPTY);
        for (int i = 0; i < slots; i++) {
            stacks.set(i, handler.getStackInSlot(i).copy());
        }
        return stacks;
    }

    private static boolean hasSingleItemOutput(IPatternDetails pattern) {
        var outputs = pattern.getOutputs();
        return outputs.size() == 1 && outputs.getFirst().what() instanceof AEItemKey;
    }

    /**
     * Batch match: pattern output amount must equal the derived total
     * (K × per-craft result count). Used in the virtual-craft hot path
     * so the user can write any uniform N:N pattern (e.g.
     * {@code 8 stone → 8 brick}) and have it consumed in one push.
     */
    private static boolean outputMatchesBatch(IPatternDetails pattern, ItemStack result,
                                               long expectedTotalCount) {
        var outputs = pattern.getOutputs();
        if (outputs.size() != 1) {
            return false;
        }
        var expected = outputs.getFirst();
        if (!(expected.what() instanceof AEItemKey expectedKey)
                || expected.amount() != expectedTotalCount) {
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean recipeMatches(Object recipe, CraftingInput input, ServerLevel level) {
        try {
            return recipe instanceof Recipe<?> r && ((Recipe) r).matches(input, level);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    @Nullable
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ItemStack assemble(Object recipe, CraftingInput input, ServerLevel level) {
        try {
            return recipe instanceof Recipe<?> r
                    ? ((Recipe) r).assemble(input, level.registryAccess())
                    : null;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static NonNullList<ItemStack> remainingItems(Object recipe, CraftingInput input) {
        try {
            return recipe instanceof Recipe<?> r
                    ? ((Recipe) r).getRemainingItems(input)
                    : null;
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    private static ItemStack resultItem(Object recipe, ServerLevel level) {
        if (!(recipe instanceof Recipe<?> r)) {
            return null;
        }
        try {
            return r.getResultItem(level.registryAccess());
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    private static NonNullList<Ingredient> ingredients(Object recipe) {
        if (!(recipe instanceof Recipe<?> r)) {
            return null;
        }
        try {
            return r.getIngredients();
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<RecipeHolder<?>> recipes(ServerLevel level) {
        return BuiltInRegistries.RECIPE_TYPE.getOptional(RECIPE_TYPE_ID)
                .map(type -> (List<RecipeHolder<?>>) (List<?>) level.getRecipeManager()
                        .getAllRecipesFor((RecipeType) type))
                .orElse(List.of());
    }

    @Nullable
    private static TableSpec tableSpec(BlockState state) {
        var id = blockId(state);
        if (id.equals(BASIC_TABLE_BLOCK)) {
            return new TableSpec(BASIC_TABLE_BLOCK, 3, 1);
        }
        if (id.equals(ADVANCED_TABLE_BLOCK)) {
            return new TableSpec(ADVANCED_TABLE_BLOCK, 5, 2);
        }
        if (id.equals(ELITE_TABLE_BLOCK)) {
            return new TableSpec(ELITE_TABLE_BLOCK, 7, 3);
        }
        if (id.equals(ULTIMATE_TABLE_BLOCK)) {
            return new TableSpec(ULTIMATE_TABLE_BLOCK, 9, 4);
        }
        return null;
    }

    private static boolean isExtendedCraftingLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    private static ResourceLocation blockId(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock());
    }

    private static ResourceLocation ecId(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private record TableSpec(ResourceLocation blockId, int gridSize, int tier) {
        int slots() {
            return gridSize * gridSize;
        }

        boolean matches(BlockState state) {
            return ExtendedCraftingTableAdapter.blockId(state).equals(blockId);
        }
    }

    private record PlannedUnit(int slot, ItemStack stack) {
        PlannedUnit {
            Objects.requireNonNull(stack, "stack");
        }
    }

    private record BatchMatch(int craftRatio, Object recipe, List<ItemStack> perCraftStacks) {
    }

    private static final class EcReflection {
        private static final String BASIC_TABLE_CLASS =
                "com.blakebr0.extendedcrafting.tileentity.BasicTableTileEntity";
        private static final String ADVANCED_TABLE_CLASS =
                "com.blakebr0.extendedcrafting.tileentity.AdvancedTableTileEntity";
        private static final String ELITE_TABLE_CLASS =
                "com.blakebr0.extendedcrafting.tileentity.EliteTableTileEntity";
        private static final String ULTIMATE_TABLE_CLASS =
                "com.blakebr0.extendedcrafting.tileentity.UltimateTableTileEntity";
        private static final String BASE_INVENTORY_CLASS =
                "com.blakebr0.cucumber.tileentity.BaseInventoryTileEntity";
        private static final String TABLE_INPUT_CLASS =
                "com.blakebr0.extendedcrafting.api.TableCraftingInput";

        private static volatile boolean lookupDone;
        private static volatile @Nullable Set<Class<?>> tableClasses;
        private static volatile @Nullable Method getInventoryMethod;
        private static volatile @Nullable Method tableInputOfMethod;
        private static volatile @Nullable Method tableInputTopMethod;
        private static volatile @Nullable Method tableInputLeftMethod;
        private static volatile @Nullable Method shapedRecipeWidthMethod;

        static boolean isSupportedTable(Object o) {
            ensureLookup();
            if (tableClasses == null) {
                return false;
            }
            for (var clazz : tableClasses) {
                if (clazz.isInstance(o)) {
                    return true;
                }
            }
            return false;
        }

        @Nullable
        static IItemHandler getHandler(BlockEntity be) {
            ensureLookup();
            if (getInventoryMethod == null) {
                return null;
            }
            try {
                var value = getInventoryMethod.invoke(be);
                return value instanceof IItemHandler h ? h : null;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        @Nullable
        static CraftingInput createTableInput(int size, List<ItemStack> stacks, int tier) {
            ensureLookup();
            if (tableInputOfMethod == null) {
                return null;
            }
            try {
                var value = tableInputOfMethod.invoke(null, size, size, stacks, tier);
                return value instanceof CraftingInput input ? input : null;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        static int top(CraftingInput input) {
            ensureLookup();
            if (tableInputTopMethod == null) {
                return 0;
            }
            try {
                var value = tableInputTopMethod.invoke(input);
                return value instanceof Integer i ? i : 0;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return 0;
            }
        }

        static int left(CraftingInput input) {
            ensureLookup();
            if (tableInputLeftMethod == null) {
                return 0;
            }
            try {
                var value = tableInputLeftMethod.invoke(input);
                return value instanceof Integer i ? i : 0;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return 0;
            }
        }

        @Nullable
        static Integer recipeWidth(Object recipe) {
            ensureLookup();
            if (shapedRecipeWidthMethod == null) {
                return null;
            }
            try {
                if (!shapedRecipeWidthMethod.getDeclaringClass().isInstance(recipe)) {
                    return null;
                }
                var value = shapedRecipeWidthMethod.invoke(recipe);
                return value instanceof Integer i ? i : null;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return null;
            }
        }

        private static void ensureLookup() {
            if (lookupDone) {
                return;
            }
            synchronized (EcReflection.class) {
                if (lookupDone) {
                    return;
                }
                try {
                    doLookup();
                } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                } finally {
                    lookupDone = true;
                }
            }
        }

        private static void doLookup() throws ReflectiveOperationException {
            tableClasses = Set.of(
                    Class.forName(BASIC_TABLE_CLASS),
                    Class.forName(ADVANCED_TABLE_CLASS),
                    Class.forName(ELITE_TABLE_CLASS),
                    Class.forName(ULTIMATE_TABLE_CLASS));

            var baseInventoryClass = Class.forName(BASE_INVENTORY_CLASS);
            getInventoryMethod = baseInventoryClass.getMethod("getInventory");

            var tableInputClass = Class.forName(TABLE_INPUT_CLASS);
            tableInputOfMethod = tableInputClass.getMethod("of", int.class, int.class, List.class, int.class);
            tableInputTopMethod = tableInputClass.getMethod("top");
            tableInputLeftMethod = tableInputClass.getMethod("left");

            var shapedRecipeClass = Class.forName("com.blakebr0.extendedcrafting.crafting.recipe.ShapedTableRecipe");
            shapedRecipeWidthMethod = shapedRecipeClass.getMethod("getWidth");
        }
    }
}
