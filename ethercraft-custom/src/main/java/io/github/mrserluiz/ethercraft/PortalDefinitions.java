package io.github.mrserluiz.ethercraft;

import java.util.*;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

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
        YamlConfiguration y = new YamlConfiguration();
        y.load(new java.io.File(plugin.getDataFolder(), "portal-types.yaml"));
        if (y.getInt("schema") != 1) throw new IllegalArgumentException("portal-types.yaml: schema deve ser 1.");
        ConfigurationSection section = y.getConfigurationSection("portals");
        if (section == null) throw new IllegalArgumentException("portal-types.yaml: seção portals obrigatória.");
        Map<String, Definition> candidate = new LinkedHashMap<>();
        for (String id : section.getKeys(false)) {
            ConfigurationSection p = section.getConfigurationSection(id);
            if (p == null) throw new IllegalArgumentException(id + ": definição deve ser uma seção YAML.");
            Material frame = material(p.getString("frame-block"), id + ".frame-block");
            Material item = material(p.getString("activation.item"), id + ".activation.item");
            if (!frame.isBlock() || !frame.isSolid() || frame == Material.OBSIDIAN)
                throw new IllegalArgumentException(id + ": frame-block deve ser bloco sólido; OBSIDIAN é reservado ao Nether vanilla.");
            if (!item.isItem() || item.isAir()) throw new IllegalArgumentException(id + ": activation.item deve ser item válido.");
            PortalTypeSpec.ActivationMode mode;
            try { mode = PortalTypeSpec.ActivationMode.valueOf(p.getString("activation.mode", "INTERACT").toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException e) { throw new IllegalArgumentException(id + ": activation.mode deve ser INTERACT ou PROJECTILE."); }
            if (mode == PortalTypeSpec.ActivationMode.PROJECTILE && item != Material.SNOWBALL && item != Material.EGG)
                throw new IllegalArgumentException(id + ": PROJECTILE suporta SNOWBALL ou EGG; para outros itens use INTERACT.");
            if (p.contains("enabled") && !p.isBoolean("enabled")) throw new IllegalArgumentException(id + ": enabled deve ser true ou false.");
            if (!p.isList("source-worlds") || p.getList("source-worlds").stream().anyMatch(w -> !(w instanceof String)))
                throw new IllegalArgumentException(id + ": source-worlds deve ser lista de nomes de mundos.");
            PortalTypeSpec spec = new PortalTypeSpec(id, p.getBoolean("enabled", true), frame.name(), item.name(), mode,
                p.getStringList("source-worlds"), p.getString("destination-world"));
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
