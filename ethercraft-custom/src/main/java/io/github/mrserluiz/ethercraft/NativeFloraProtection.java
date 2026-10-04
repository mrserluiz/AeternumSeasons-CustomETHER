package io.github.mrserluiz.ethercraft;

import java.lang.reflect.*;
import java.util.*;

/** Narrow access to the audited SeasonalFloraController's existing player-placement exemption. */
final class NativeFloraProtection<T> {
    private final Object controller;
    private final Method mark, unmark, protectedByPlayer;
    private final Field enabled, protectPlayerPlaced;
    private final Map<String, T> owned = new HashMap<>();
    NativeFloraProtection(Object controller, Class<T> blockType) throws ReflectiveOperationException {
        this.controller = controller;
        Class<?> type = controller.getClass();
        mark = type.getDeclaredMethod("markPlayerPlaced", blockType);
        unmark = type.getDeclaredMethod("unmarkPlayerPlaced", blockType);
        protectedByPlayer = type.getDeclaredMethod("isProtectedByPlayer", blockType);
        enabled = type.getDeclaredField("enabled");
        protectPlayerPlaced = type.getDeclaredField("protectPlayerPlaced");
        for (var method : List.of(mark, unmark, protectedByPlayer)) method.setAccessible(true);
        enabled.setAccessible(true); protectPlayerPlaced.setAccessible(true);
    }
    boolean available() throws IllegalAccessException {
        return !enabled.getBoolean(controller) || protectPlayerPlaced.getBoolean(controller);
    }
    void refresh(Map<String, T> requested) throws ReflectiveOperationException {
        // No global setting changes; the native controller must honor placement exemptions.
        if (!enabled.getBoolean(controller) || !protectPlayerPlaced.getBoolean(controller)) { release(); return; }
        for (var iterator = owned.entrySet().iterator(); iterator.hasNext();) {
            var entry = iterator.next();
            if (!requested.containsKey(entry.getKey())) { unmark.invoke(controller, entry.getValue()); iterator.remove(); }
        }
        for (var entry : requested.entrySet()) {
            if (!(boolean) protectedByPlayer.invoke(controller, entry.getValue())) {
                mark.invoke(controller, entry.getValue()); owned.put(entry.getKey(), entry.getValue());
            }
        }
    }
    // A genuine player edit transfers ownership to native BlockPlace/BlockBreak handling.
    void forget(String key) { owned.remove(key); }
    void release() throws ReflectiveOperationException {
        for (T block : owned.values()) unmark.invoke(controller, block);
        owned.clear();
    }
}
