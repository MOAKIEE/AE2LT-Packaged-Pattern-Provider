package com.moakiee.ae2lt.packaged.logic.multiblock.mekmm;

import java.lang.reflect.Method;

import org.jetbrains.annotations.Nullable;

final class MekCapabilityReflection {

    private MekCapabilityReflection() {}

    static Object resolveBlock(Object capabilityCarrier, Class<?> blockCapabilityClass) {
        if (capabilityCarrier == null) return null;
        if (blockCapabilityClass.isInstance(capabilityCarrier)) return capabilityCarrier;
        try {
            Method blockMethod = getBlockMethod(capabilityCarrier.getClass());
            blockMethod.setAccessible(true);
            Object blockCapability = blockMethod.invoke(capabilityCarrier);
            if (blockCapabilityClass.isInstance(blockCapability)) return blockCapability;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
            return null;
        }
        return null;
    }

    @Nullable
    static Method findZeroArgMethod(Class<?> targetClass, String... names) {
        for (String name : names) {
            Method method = findZeroArgMethod(targetClass, name);
            if (method != null) return method;
        }
        return null;
    }

    private static Method getBlockMethod(Class<?> carrierClass) throws NoSuchMethodException {
        try {
            return carrierClass.getMethod("block");
        } catch (NoSuchMethodException ignored) {
            return carrierClass.getDeclaredMethod("block");
        }
    }

    @Nullable
    private static Method findZeroArgMethod(Class<?> targetClass, String name) {
        try {
            Method method = targetClass.getMethod(name);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            try {
                Method method = targetClass.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignoredAgain) {
                return null;
            }
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }
}
