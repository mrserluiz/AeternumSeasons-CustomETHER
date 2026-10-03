package io.github.mrserluiz.ethercraft;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** The same disk parser is used by reload, edits, and regression tests. */
public final class PortalTypeYaml {
    private PortalTypeYaml() {}
    public static List<PortalTypeSpec> read(File file) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration(); yaml.load(file); return read(yaml);
    }
    private static List<PortalTypeSpec> read(YamlConfiguration yaml) {
        if (yaml.getInt("schema") != 1) throw new IllegalArgumentException("portal-types.yaml: schema deve ser 1.");
        ConfigurationSection section = yaml.getConfigurationSection("portals");
        if (section == null) throw new IllegalArgumentException("portal-types.yaml: seção portals obrigatória.");
        List<PortalTypeSpec> specs = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            var p = section.getConfigurationSection(id);
            if (p == null) throw new IllegalArgumentException(id + ": definição deve ser uma seção YAML.");
            if (p.contains("enabled") && !p.isBoolean("enabled")) throw new IllegalArgumentException(id + ": enabled deve ser true ou false.");
            if (!p.isList("source-worlds") || p.getList("source-worlds").stream().anyMatch(w -> !(w instanceof String)))
                throw new IllegalArgumentException(id + ": source-worlds deve ser lista de nomes/chaves de mundos.");
            PortalTypeSpec.ActivationMode mode;
            try { mode = PortalTypeSpec.ActivationMode.valueOf(p.getString("activation.mode", "INTERACT").toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException error) { throw new IllegalArgumentException(id + ": activation.mode deve ser INTERACT ou PROJECTILE."); }
            specs.add(new PortalTypeSpec(id, p.getBoolean("enabled", true), p.getString("frame-block"),
                p.getString("activation.item"), mode, p.getStringList("source-worlds"), p.getString("destination-world")));
        }
        return List.copyOf(specs);
    }
    public static void setEnabled(File file, String id, boolean enabled, String origin) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration(); yaml.load(file); read(yaml);
        var section = yaml.getConfigurationSection("portals." + id);
        if (section == null || !id.matches("[a-z][a-z0-9_-]{0,39}")) throw new IllegalArgumentException("Tipo desconhecido: " + id);
        section.set("enabled", enabled);
        if (enabled && section.getStringList("source-worlds").isEmpty() && origin != null)
            section.set("source-worlds", List.of(origin));
        read(yaml);
        write(file.toPath(), yaml.saveToString().getBytes(StandardCharsets.UTF_8));
    }
    public static void write(Path file, byte[] bytes) throws java.io.IOException {
        Path tmp = Files.createTempFile(file.toAbsolutePath().getParent(), "portal-types-", ".tmp");
        try {
            Files.write(tmp, bytes);
            try { Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException error) { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(tmp); }
    }
}
