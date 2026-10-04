package io.github.mrserluiz.ethercraft;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import org.bukkit.configuration.file.YamlConfiguration;

final class LanguageCatalog {
    static final Map<String, String> SUPPORTED;
    static {
        var names = new LinkedHashMap<String, String>();
        names.put("en_US", "English"); names.put("es_ES", "Español");
        names.put("id_ID", "Indonesian"); names.put("it_IT", "Italiano");
        names.put("fr_FR", "Français"); names.put("de_DE", "Deutsch");
        names.put("pt_BR", "Português (Brasil)"); names.put("ru_RU", "Русский");
        names.put("pl_PL", "Polski"); names.put("vi_VN", "Tiếng Việt"); names.put("tr_TR", "Türkçe");
        SUPPORTED = Collections.unmodifiableMap(names);
    }
    private final Map<String, Map<String, String>> translations;
    private LanguageCatalog(Map<String, Map<String, String>> translations) { this.translations = translations; }
    static String normalize(String locale) {
        if (locale == null) return null;
        String candidate = locale.replace('-', '_');
        return SUPPORTED.keySet().stream().filter(id -> id.equalsIgnoreCase(candidate)).findFirst().orElse(null);
    }
    static String resolve(String preference, String client, String fallback, boolean useClient) {
        String explicit = normalize(preference), automatic = useClient ? normalize(client) : null;
        String defaultId = normalize(fallback);
        return explicit != null ? explicit : automatic != null ? automatic : defaultId != null ? defaultId : "pt_BR";
    }
    static LanguageCatalog load(Path directory) throws Exception {
        var all = new LinkedHashMap<String, Map<String, String>>();
        Files.createDirectories(directory);
        for (String id : SUPPORTED.keySet()) {
            var bundled = new YamlConfiguration();
            try (InputStream stream = LanguageCatalog.class.getResourceAsStream("/languages/" + id + ".yml")) {
                if (stream == null) throw new IOException("Missing language: " + id);
                String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                bundled.loadFromString(text);
                if (!Files.exists(directory.resolve(id + ".yml")))
                    Files.writeString(directory.resolve(id + ".yml"), text, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            }
            var custom = new YamlConfiguration(); custom.load(directory.resolve(id + ".yml").toFile());
            var messages = new LinkedHashMap<String, String>();
            for (String key : bundled.getKeys(false)) {
                String text = custom.getString(key, bundled.getString(key));
                if (text == null || text.isBlank() || !placeholders(text).equals(placeholders(bundled.getString(key))))
                    throw new IOException("Invalid language message: " + id + "/" + key);
                messages.put(key, text);
            }
            all.put(id, Map.copyOf(messages));
        }
        return new LanguageCatalog(Map.copyOf(all));
    }
    static Set<String> placeholders(String text) {
        var found = new TreeSet<String>();
        var matcher = Pattern.compile("\\{[0-9]+\\}").matcher(text);
        while (matcher.find()) found.add(matcher.group());
        return found;
    }
    Set<String> keys(String locale) { return translations.get(locale).keySet(); }
    String text(String locale, String key, Object... args) {
        String template = translations.getOrDefault(locale, translations.get("pt_BR")).get(key);
        if (template == null) throw new IllegalArgumentException("Unknown message: " + key);
        // Replace only placeholders from the template, never placeholders inside supplied arguments.
        var matcher = Pattern.compile("\\{([0-9]+)\\}").matcher(template);
        var output = new StringBuilder();
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            matcher.appendReplacement(output, java.util.regex.Matcher.quoteReplacement(index < args.length ? String.valueOf(args[index]) : matcher.group()));
        }
        matcher.appendTail(output); return output.toString();
    }
}
