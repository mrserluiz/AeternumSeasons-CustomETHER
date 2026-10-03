package io.github.mrserluiz.ethercraft;

import java.util.*;
import java.util.function.*;

/** Removes dead endpoints, preserves their surviving partners, and drops obsolete pairs. */
public final class PortalRegistryReconciler {
    private PortalRegistryReconciler() {}
    public static <T> List<T> prune(Map<String, T> frames, Map<String, String> links,
                                    Predicate<T> remove, BiPredicate<T, T> compatible) {
        List<T> removed = new ArrayList<>();
        frames.entrySet().removeIf(entry -> {
            if (!remove.test(entry.getValue())) return false;
            removed.add(entry.getValue()); return true;
        });
        links.entrySet().removeIf(entry -> {
            T origin = frames.get(entry.getKey()), destination = frames.get(entry.getValue());
            return origin == null || destination == null || entry.getKey().equals(entry.getValue())
                || !entry.getKey().equals(links.get(entry.getValue())) || !compatible.test(origin, destination);
        });
        return List.copyOf(removed);
    }
}
