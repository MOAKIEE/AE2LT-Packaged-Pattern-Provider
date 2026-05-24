package com.moakiee.ae2lt.packaged.logic.multiblock;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

public final class ReflectionSupport {
    private ReflectionSupport() {
    }

    public static Optional<Class<?>> findClass(String className) {
        try {
            return Optional.of(Class.forName(className));
        } catch (ClassNotFoundException | LinkageError e) {
            return Optional.empty();
        }
    }

    public static Optional<Method> findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return Optional.of(type.getMethod(name, parameterTypes));
        } catch (NoSuchMethodException | SecurityException e) {
            return Optional.empty();
        }
    }

    public static Optional<Object> invoke(Method method, Object target, Object... args) {
        try {
            return Optional.ofNullable(method.invoke(target, args));
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
