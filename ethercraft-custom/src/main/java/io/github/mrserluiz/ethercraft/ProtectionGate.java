package io.github.mrserluiz.ethercraft;

import java.util.List;

/** Validate every planned location against every provider before editing any blocks. */
public final class ProtectionGate {
    @FunctionalInterface public interface Query<T> { String denial(T location) throws Exception; }
    public record Provider<T>(String name, Query<T> query) {}
    private ProtectionGate() {}
    public static <T> String firstDenial(List<T> locations, List<Provider<T>> providers) {
        for (Provider<T> provider : providers) for (T location : locations) {
            try {
                String denial = provider.query().denial(location);
                if (denial != null) return provider.name() + ": " + denial;
            } catch (Exception | LinkageError error) {
                return provider.name() + ": não foi possível consultar a proteção (" + error.getClass().getSimpleName() + ").";
            }
        }
        return null;
    }
}
