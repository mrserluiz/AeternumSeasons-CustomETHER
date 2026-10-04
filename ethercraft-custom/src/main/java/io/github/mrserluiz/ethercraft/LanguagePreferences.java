package io.github.mrserluiz.ethercraft;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

final class LanguagePreferences {
    private final Path file;
    private Map<UUID, String> values;
    LanguagePreferences(Path file) throws Exception { this.file = file; values = read(file); }
    static Map<UUID, String> read(Path file) throws Exception {
        var result = new HashMap<UUID, String>();
        if (!Files.exists(file)) return result;
        var yaml = new YamlConfiguration(); yaml.load(file.toFile());
        for (String key : yaml.getKeys(false)) {
            String language = LanguageCatalog.normalize(yaml.getString(key));
            if (language == null) throw new IOException("Invalid player language: " + key);
            result.put(UUID.fromString(key), language);
        }
        return result;
    }
    String get(UUID player) { return values.get(player); }
    void set(UUID player, String language) throws IOException {
        String normalized = LanguageCatalog.normalize(language);
        if (language != null && normalized == null) throw new IllegalArgumentException("Unsupported language");
        var next = new HashMap<>(values);
        if (normalized == null) next.remove(player); else next.put(player, normalized);
        var yaml = new YamlConfiguration(); next.forEach((id, locale) -> yaml.set(id.toString(), locale));
        PortalTypeYaml.write(file, yaml.saveToString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        values = next;
    }
}
