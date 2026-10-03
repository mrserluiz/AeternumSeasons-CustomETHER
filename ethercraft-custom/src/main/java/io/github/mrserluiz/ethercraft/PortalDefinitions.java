package io.github.mrserluiz.ethercraft;

import java.util.*;
import org.bukkit.Material;

public final class PortalDefinitions {
    public record Definition(PortalTypeSpec spec, Material frame, Material item) {}
    private Map<String, Definition> types = Map.of();
    private final AeternumCustomPortalPlugin plugin;
    public PortalDefinitions(AeternumCustomPortalPlugin plugin) throws Exception {
        this.plugin = plugin;
        var file = new java.io.File(plugin.getDataFolder(), "portal-types.yaml");
        if (!file.exists()) plugin.saveResource("portal-types.yaml", false);
        reload();
    }
    Map<String, Definition> snapshot() { return types; }
    void restore(Map<String, Definition> snapshot) { types = snapshot; }
    public int size() { return types.size(); }
    public Collection<Definition> all() { return types.values(); }
    public Definition get(String id) { return types.get(id); }
    public void reload() throws Exception {
        Map<String, Definition> candidate = new LinkedHashMap<>();
        for (PortalTypeSpec raw : PortalTypeYaml.read(new java.io.File(plugin.getDataFolder(), "portal-types.yaml"))) {
            String id = raw.id();
            Material frame = material(raw.frameBlock(), id + ".frame-block");
            Material item = material(raw.activationItem(), id + ".activation.item");
            if (!frame.isBlock() || !frame.isSolid() || frame == Material.OBSIDIAN)
                throw new IllegalArgumentException(id + ": frame-block deve ser bloco sólido; OBSIDIAN é reservado ao Nether vanilla.");
            if (!item.isItem() || item.isAir()) throw new IllegalArgumentException(id + ": activation.item deve ser item válido.");
            if (raw.mode() == PortalTypeSpec.ActivationMode.PROJECTILE && item != Material.SNOWBALL && item != Material.EGG)
                throw new IllegalArgumentException(id + ": PROJECTILE suporta SNOWBALL ou EGG; para outros itens use INTERACT.");
            PortalTypeSpec spec = new PortalTypeSpec(id, raw.enabled(), frame.name(), item.name(), raw.mode(), raw.sourceWorlds(), raw.destinationWorld());
            candidate.put(id, new Definition(spec, frame, item));
        }
        PortalTypeSpec.validateDistinct(candidate.values().stream().map(Definition::spec).toList());
        // Replace only after the entire file validates. A bad reload preserves current types.
        types = Collections.unmodifiableMap(candidate);
    }
    private Material material(String raw, String field) {
        Material material = raw == null ? null : Material.matchMaterial(raw);
        if (material == null) throw new IllegalArgumentException(field + ": material desconhecido: " + raw);
        return material;
    }
}
