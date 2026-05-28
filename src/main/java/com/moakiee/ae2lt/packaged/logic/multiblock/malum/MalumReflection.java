package com.moakiee.ae2lt.packaged.logic.multiblock.malum;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import com.moakiee.ae2lt.packaged.logic.multiblock.ReflectionSupport;

final class MalumReflection {

    private static final String ALTAR_CLASS =
            "com.sammy.malum.common.block.curiosities.sorcery.spirit_altar.SpiritAltarBlockEntity";
    private static final String ALTAR_HELPER_CLASS =
            "com.sammy.malum.common.block.curiosities.sorcery.spirit_altar.AltarCraftingHelper";
    private static final String CRUCIBLE_CLASS =
            "com.sammy.malum.common.block.curiosities.artifice.spirit_crucible.SpiritCrucibleCoreBlockEntity";
    private static final String ACCESS_POINT_CLASS =
            "com.sammy.malum.common.block.storage.IMalumSpecialItemAccessPoint";
    private static final String INFUSION_RECIPE_CLASS =
            "com.sammy.malum.common.recipe.SpiritInfusionRecipe";
    private static final String FOCUSING_RECIPE_CLASS =
            "com.sammy.malum.common.recipe.SpiritFocusingRecipe";
    private static final String SPIRIT_INGREDIENT_CLASS =
            "com.sammy.malum.core.systems.recipe.SpiritIngredient";

    private static volatile boolean lookupDone;
    private static volatile @Nullable Class<?> altarClass;
    private static volatile @Nullable Class<?> crucibleClass;
    private static volatile @Nullable Class<?> accessPointClass;
    private static volatile @Nullable Class<?> infusionRecipeClass;
    private static volatile @Nullable Class<?> focusingRecipeClass;
    private static volatile @Nullable Class<?> spiritIngredientClass;

    private static volatile @Nullable Field altarInventoryField;
    private static volatile @Nullable Field altarSpiritInventoryField;
    private static volatile @Nullable Field altarExtrasInventoryField;
    private static volatile @Nullable Field altarRecipeField;
    private static volatile @Nullable Field altarCraftingField;
    private static volatile @Nullable Field altarProgressField;
    private static volatile @Nullable Field crucibleInventoryField;
    private static volatile @Nullable Field crucibleSpiritInventoryField;
    private static volatile @Nullable Field crucibleRecipeField;
    private static volatile @Nullable Field crucibleCraftingField;
    private static volatile @Nullable Field crucibleProgressField;
    private static volatile @Nullable Field infusionInputField;
    private static volatile @Nullable Field infusionSpiritsField;
    private static volatile @Nullable Field infusionExtrasField;

    private static volatile @Nullable Method altarRecalculateMethod;
    private static volatile @Nullable Method crucibleUpdateMethod;
    private static volatile @Nullable Method capturePedestalsMethod;
    private static volatile @Nullable Method getSuppliedInventoryMethod;
    private static volatile @Nullable Method getAccessPointBlockPosMethod;
    private static volatile @Nullable Method infusionGetOutputMethod;
    private static volatile @Nullable Method focusingGetInputMethod;
    private static volatile @Nullable Method focusingGetSpiritsMethod;
    private static volatile @Nullable Method focusingCreateOutputMethod;
    private static volatile @Nullable Method spiritAsItemStackMethod;

    private MalumReflection() {
    }

    static boolean isSpiritAltar(Object o) {
        ensureLookup();
        return altarClass != null && altarClass.isInstance(o);
    }

    static boolean isSpiritCrucible(Object o) {
        ensureLookup();
        return crucibleClass != null && crucibleClass.isInstance(o);
    }

    static boolean isAltarIdle(BlockEntity be) {
        return isAltarInactive(be)
                && fieldValue(altarRecipeField, be) == null;
    }

    static boolean isCrucibleIdle(BlockEntity be) {
        return isCrucibleInactive(be)
                && fieldValue(crucibleRecipeField, be) == null;
    }

    static boolean isAltarInactive(BlockEntity be) {
        return isSpiritAltar(be)
                && !booleanField(altarCraftingField, be)
                && numberField(altarProgressField, be) <= 0;
    }

    static boolean isCrucibleInactive(BlockEntity be) {
        return isSpiritCrucible(be)
                && !booleanField(crucibleCraftingField, be)
                && numberField(crucibleProgressField, be) <= 0;
    }

    @Nullable
    static IItemHandlerModifiable altarInventory(BlockEntity be) {
        return itemHandler(altarInventoryField, be);
    }

    @Nullable
    static IItemHandlerModifiable altarSpiritInventory(BlockEntity be) {
        return itemHandler(altarSpiritInventoryField, be);
    }

    @Nullable
    static IItemHandlerModifiable altarExtrasInventory(BlockEntity be) {
        return itemHandler(altarExtrasInventoryField, be);
    }

    @Nullable
    static IItemHandlerModifiable crucibleInventory(BlockEntity be) {
        return itemHandler(crucibleInventoryField, be);
    }

    @Nullable
    static IItemHandlerModifiable crucibleSpiritInventory(BlockEntity be) {
        return itemHandler(crucibleSpiritInventoryField, be);
    }

    static void recalculateAltar(ServerLevel level, BlockPos pos) {
        var be = level.getBlockEntity(pos);
        if (be == null || !isSpiritAltar(be) || altarRecalculateMethod == null) {
            return;
        }
        try {
            ReflectionSupport.invoke(altarRecalculateMethod, be);
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    static void updateCrucibleRecipe(ServerLevel level, BlockPos pos) {
        var be = level.getBlockEntity(pos);
        if (be == null || !isSpiritCrucible(be) || crucibleUpdateMethod == null) {
            return;
        }
        try {
            ReflectionSupport.invoke(crucibleUpdateMethod, be);
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    static List<Pedestal> capturePedestals(ServerLevel level, BlockPos altarPos) {
        ensureLookup();
        if (capturePedestalsMethod == null
                || getSuppliedInventoryMethod == null
                || getAccessPointBlockPosMethod == null
                || accessPointClass == null) {
            return List.of();
        }

        try {
            var value = capturePedestalsMethod.invoke(null, level, altarPos);
            if (!(value instanceof List<?> list)) {
                return List.of();
            }

            var pedestals = new ArrayList<Pedestal>(list.size());
            for (var accessPoint : list) {
                if (!accessPointClass.isInstance(accessPoint)) {
                    continue;
                }
                var pos = getAccessPointBlockPosMethod.invoke(accessPoint);
                var inventory = getSuppliedInventoryMethod.invoke(accessPoint);
                if (pos instanceof BlockPos blockPos
                        && inventory instanceof IItemHandlerModifiable itemHandler) {
                    pedestals.add(new Pedestal(blockPos, itemHandler));
                }
            }
            return List.copyOf(pedestals);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return List.of();
        }
    }

    @Nullable
    static SizedIngredient infusionInput(Object recipe) {
        ensureLookup();
        if (!isInfusionRecipe(recipe)) {
            return null;
        }
        var value = fieldValue(infusionInputField, recipe);
        return value instanceof SizedIngredient ingredient ? ingredient : null;
    }

    @Nullable
    static List<ItemStack> infusionSpirits(Object recipe) {
        ensureLookup();
        if (!isInfusionRecipe(recipe)) {
            return null;
        }
        return spiritStacks(fieldValue(infusionSpiritsField, recipe));
    }

    @Nullable
    static List<SizedIngredient> infusionExtras(Object recipe) {
        ensureLookup();
        if (!isInfusionRecipe(recipe)) {
            return null;
        }
        return sizedIngredients(fieldValue(infusionExtrasField, recipe));
    }

    @Nullable
    static ItemStack infusionOutput(Object recipe, ServerLevel level, ItemStack input) {
        ensureLookup();
        if (!isInfusionRecipe(recipe) || infusionGetOutputMethod == null) {
            return null;
        }
        try {
            var value = infusionGetOutputMethod.invoke(recipe, level, input);
            return value instanceof ItemStack stack ? stack.copy() : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    static Ingredient focusingInput(Object recipe) {
        ensureLookup();
        if (!isFocusingRecipe(recipe) || focusingGetInputMethod == null) {
            return null;
        }
        try {
            var value = focusingGetInputMethod.invoke(recipe);
            return value instanceof Ingredient ingredient ? ingredient : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    static List<ItemStack> focusingSpirits(Object recipe) {
        ensureLookup();
        if (!isFocusingRecipe(recipe) || focusingGetSpiritsMethod == null) {
            return null;
        }
        try {
            return spiritStacks(focusingGetSpiritsMethod.invoke(recipe));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    @Nullable
    static ItemStack focusingOutput(Object recipe) {
        ensureLookup();
        if (!isFocusingRecipe(recipe) || focusingCreateOutputMethod == null) {
            return null;
        }
        try {
            var value = focusingCreateOutputMethod.invoke(recipe);
            return value instanceof ItemStack stack ? stack.copy() : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static boolean isInfusionRecipe(Object recipe) {
        return infusionRecipeClass != null && infusionRecipeClass.isInstance(recipe);
    }

    private static boolean isFocusingRecipe(Object recipe) {
        return focusingRecipeClass != null && focusingRecipeClass.isInstance(recipe);
    }

    @Nullable
    private static IItemHandlerModifiable itemHandler(@Nullable Field field, Object target) {
        var value = fieldValue(field, target);
        return value instanceof IItemHandlerModifiable itemHandler ? itemHandler : null;
    }

    @Nullable
    private static Object fieldValue(@Nullable Field field, Object target) {
        if (field == null) {
            return null;
        }
        try {
            return field.get(target);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static boolean booleanField(@Nullable Field field, Object target) {
        var value = fieldValue(field, target);
        return value instanceof Boolean b && b;
    }

    private static double numberField(@Nullable Field field, Object target) {
        var value = fieldValue(field, target);
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    @Nullable
    private static List<ItemStack> spiritStacks(@Nullable Object value) {
        if (!(value instanceof List<?> list) || spiritAsItemStackMethod == null) {
            return null;
        }
        var stacks = new ArrayList<ItemStack>(list.size());
        for (var spiritIngredient : list) {
            if (spiritIngredientClass == null || !spiritIngredientClass.isInstance(spiritIngredient)) {
                return null;
            }
            try {
                var stack = spiritAsItemStackMethod.invoke(spiritIngredient);
                if (!(stack instanceof ItemStack itemStack) || itemStack.isEmpty()) {
                    return null;
                }
                stacks.add(itemStack.copy());
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                return null;
            }
        }
        return List.copyOf(stacks);
    }

    @Nullable
    private static List<SizedIngredient> sizedIngredients(@Nullable Object value) {
        if (!(value instanceof List<?> list)) {
            return null;
        }
        var ingredients = new ArrayList<SizedIngredient>(list.size());
        for (var ingredient : list) {
            if (!(ingredient instanceof SizedIngredient sizedIngredient)) {
                return null;
            }
            ingredients.add(sizedIngredient);
        }
        return List.copyOf(ingredients);
    }

    private static void ensureLookup() {
        if (lookupDone) {
            return;
        }
        synchronized (MalumReflection.class) {
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
        altarClass = requiredClass(ALTAR_CLASS);
        crucibleClass = requiredClass(CRUCIBLE_CLASS);
        accessPointClass = requiredClass(ACCESS_POINT_CLASS);
        infusionRecipeClass = requiredClass(INFUSION_RECIPE_CLASS);
        focusingRecipeClass = requiredClass(FOCUSING_RECIPE_CLASS);
        spiritIngredientClass = requiredClass(SPIRIT_INGREDIENT_CLASS);
        var altarHelperClass = requiredClass(ALTAR_HELPER_CLASS);

        altarInventoryField = field(altarClass, "inventory");
        altarSpiritInventoryField = field(altarClass, "spiritInventory");
        altarExtrasInventoryField = field(altarClass, "extrasInventory");
        altarRecipeField = field(altarClass, "recipe");
        altarCraftingField = field(altarClass, "isCrafting");
        altarProgressField = field(altarClass, "progress");
        crucibleInventoryField = field(crucibleClass, "inventory");
        crucibleSpiritInventoryField = field(crucibleClass, "spiritInventory");
        crucibleRecipeField = field(crucibleClass, "recipe");
        crucibleCraftingField = field(crucibleClass, "isCrafting");
        crucibleProgressField = field(crucibleClass, "progress");
        infusionInputField = field(infusionRecipeClass, "input");
        infusionSpiritsField = field(infusionRecipeClass, "spirits");
        infusionExtrasField = field(infusionRecipeClass, "extraInputs");

        altarRecalculateMethod = method(altarClass, "recalculateRecipes");
        crucibleUpdateMethod = method(crucibleClass, "updateRecipe");
        capturePedestalsMethod = requiredMethod(altarHelperClass, "capturePedestals", Level.class, BlockPos.class);
        getSuppliedInventoryMethod = requiredMethod(accessPointClass, "getSuppliedInventory");
        getAccessPointBlockPosMethod = requiredMethod(accessPointClass, "getAccessPointBlockPos");
        infusionGetOutputMethod = requiredMethod(infusionRecipeClass, "getOutput", ServerLevel.class, ItemStack.class);
        focusingGetInputMethod = requiredMethod(focusingRecipeClass, "getInput");
        focusingGetSpiritsMethod = requiredMethod(focusingRecipeClass, "getSpirits");
        focusingCreateOutputMethod = requiredMethod(focusingRecipeClass, "createOutput");
        spiritAsItemStackMethod = requiredMethod(spiritIngredientClass, "asItemStack");
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Method method(Class<?> type, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        var method = type.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method;
    }

    private static Class<?> requiredClass(String className) throws ClassNotFoundException {
        return ReflectionSupport.findClass(className)
                .orElseThrow(() -> new ClassNotFoundException(className));
    }

    private static Method requiredMethod(Class<?> type, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        return ReflectionSupport.findMethod(type, name, parameterTypes)
                .orElseThrow(() -> new NoSuchMethodException(type.getName() + "#" + name));
    }

    record Pedestal(BlockPos pos, IItemHandlerModifiable inventory) {
    }
}
