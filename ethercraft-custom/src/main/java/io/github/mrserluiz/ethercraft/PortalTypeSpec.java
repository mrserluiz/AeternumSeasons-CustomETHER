package io.github.mrserluiz.ethercraft;

import java.util.*;

/** Configuration and routing rules, independent of Bukkit for verification. */
public record PortalTypeSpec(String id, boolean enabled, String frameBlock, String activationItem,
                             ActivationMode mode, List<String> sourceWorlds, String destinationWorld, Shape shape) {
    public enum ActivationMode { INTERACT, PROJECTILE, DROP_ITEM }
    public enum Shape { VERTICAL, HORIZONTAL_POOL }
    public PortalTypeSpec(String id, boolean enabled, String frameBlock, String activationItem,
                          ActivationMode mode, List<String> sourceWorlds, String destinationWorld) {
        this(id, enabled, frameBlock, activationItem, mode, sourceWorlds, destinationWorld, Shape.VERTICAL);
    }
    public PortalTypeSpec {
        if (shape == null) throw new IllegalArgumentException("shape obrigatório.");
        if (mode == ActivationMode.DROP_ITEM && shape != Shape.HORIZONTAL_POOL)
            throw new IllegalArgumentException(id + ": DROP_ITEM requer HORIZONTAL_POOL.");
        if (shape == Shape.HORIZONTAL_POOL && mode == ActivationMode.PROJECTILE)
            throw new IllegalArgumentException(id + ": HORIZONTAL_POOL suporta INTERACT ou DROP_ITEM.");
        if (id == null || !id.matches("[a-z][a-z0-9_-]{0,39}"))
            throw new IllegalArgumentException("ID de portal inválido: " + id);
        if (frameBlock == null || frameBlock.isBlank() || activationItem == null || activationItem.isBlank() || mode == null)
            throw new IllegalArgumentException(id + ": bloco, item e modo de ativação obrigatórios.");
        if (destinationWorld == null || destinationWorld.isBlank() || !destinationWorld.equals(destinationWorld.trim()))
            throw new IllegalArgumentException(id + ": destination-world obrigatório, sem espaços nas extremidades.");
        sourceWorlds = List.copyOf(sourceWorlds);
        if (sourceWorlds.stream().anyMatch(w -> w.isBlank() || !w.equals(w.trim()))
            || new HashSet<>(sourceWorlds).size() != sourceWorlds.size() || sourceWorlds.contains(destinationWorld))
            throw new IllegalArgumentException(id + ": origens vazias/duplicadas ou iguais ao destino.");
    }
    public boolean acceptsWorld(String world) {
        return enabled && (destinationWorld.equals(world) || sourceWorlds.contains(world));
    }
    public boolean permitsPair(String a, String b) {
        return enabled && ((sourceWorlds.contains(a) && destinationWorld.equals(b))
            || (sourceWorlds.contains(b) && destinationWorld.equals(a)));
    }
    public boolean acceptsWorld(String name, String key) {
        return acceptsWorld(name) || acceptsWorld(key);
    }
    private boolean source(String name, String key) { return sourceWorlds.contains(name) || sourceWorlds.contains(key); }
    private boolean destination(String name, String key) { return destinationWorld.equals(name) || destinationWorld.equals(key); }
    public boolean permitsPair(String aName, String aKey, String bName, String bKey) {
        return enabled && ((source(aName, aKey) && destination(bName, bKey))
            || (source(bName, bKey) && destination(aName, aKey)));
    }
    /** Multiple sources need an explicit link for the return journey. */
    public String unlinkedTarget(String name, String key) {
        if (!enabled) return null;
        if (source(name, key) && !destination(name, key)) return destinationWorld;
        if (destination(name, key) && !source(name, key) && sourceWorlds.size() == 1) return sourceWorlds.getFirst();
        return null;
    }
    public static boolean routingChanged(PortalTypeSpec before, PortalTypeSpec after) {
        return before == null || after == null || before.enabled != after.enabled
            || !before.destinationWorld.equals(after.destinationWorld)
            || !new HashSet<>(before.sourceWorlds).equals(new HashSet<>(after.sourceWorlds));
    }
    public static void validateDistinct(Collection<PortalTypeSpec> specs) {
        if (specs.size() > 64) throw new IllegalArgumentException("Limite de 64 tipos de portal.");
        List<PortalTypeSpec> all = new ArrayList<>(specs);
        Set<String> ids = new HashSet<>();
        for (PortalTypeSpec spec : all) if (!ids.add(spec.id)) throw new IllegalArgumentException("ID duplicado: " + spec.id);
        for (int i = 0; i < all.size(); i++) for (int j = i + 1; j < all.size(); j++) {
            var a = all.get(i); var b = all.get(j);
            if (!a.enabled || !b.enabled || !a.frameBlock.equals(b.frameBlock)
                || !a.activationItem.equals(b.activationItem) || a.mode != b.mode || a.shape != b.shape) continue;
            Set<String> worlds = new HashSet<>(a.sourceWorlds); worlds.add(a.destinationWorld);
            if (worlds.stream().anyMatch(b::acceptsWorld))
                throw new IllegalArgumentException("Ativação ambígua entre " + a.id + " e " + b.id + ": mesmo bloco/item/modo no mesmo mundo.");
        }
    }
}
