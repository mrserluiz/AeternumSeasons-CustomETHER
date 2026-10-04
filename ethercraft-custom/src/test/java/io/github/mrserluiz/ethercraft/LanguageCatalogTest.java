package io.github.mrserluiz.ethercraft;

import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LanguageCatalogTest {
    @TempDir Path directory;
    @Test void everyBundledLanguageContainsAllMessagesAndIdenticalPlaceholders() throws Exception {
        var catalog = LanguageCatalog.load(directory);
        var keys = catalog.keys("en_US");
        assertEquals(11, LanguageCatalog.SUPPORTED.size());
        for (String locale : LanguageCatalog.SUPPORTED.keySet()) {
            assertEquals(keys, catalog.keys(locale), locale);
            var yaml = new YamlConfiguration();
            yaml.loadFromString(new String(getClass().getResourceAsStream("/languages/" + locale + ".yml").readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            assertEquals(keys, yaml.getKeys(false), "No missing bundled translations: " + locale);
            for (String key : keys) {
                assertEquals(LanguageCatalog.placeholders(catalog.text("en_US", key)), LanguageCatalog.placeholders(catalog.text(locale, key)), locale + "/" + key);
                assertFalse(catalog.text(locale, key).isBlank());
            }
        }
    }
    @Test void explicitChoiceWinsAndUnsupportedClientFallsBackToServerLanguage() {
        assertEquals("de_DE", LanguageCatalog.resolve("de-de", "en_us", "pt_BR", true));
        assertEquals("fr_FR", LanguageCatalog.resolve(null, "fr_fr", "pt_BR", true));
        assertEquals("pt_BR", LanguageCatalog.resolve(null, "ja_jp", "pt_BR", true));
        assertEquals("es_ES", LanguageCatalog.resolve(null, "en_us", "es_ES", false));
        assertEquals("pt_BR", LanguageCatalog.resolve(null, null, "unsupported", false));
    }
    @Test void customTranslationsReloadAndMissingKeysUseBundledTranslation() throws Exception {
        LanguageCatalog.load(directory);
        Files.writeString(directory.resolve("pt_BR.yml"), "permission: 'Sem acesso.'\n");
        var catalog = LanguageCatalog.load(directory);
        assertEquals("Sem acesso.", catalog.text("pt_BR", "permission"));
        assertEquals("Idioma salvo: en_US.", catalog.text("pt_BR", "language-set", "en_US"));
    }
    @Test void invalidOverrideCannotReplaceAnExistingLoadedCatalog() throws Exception {
        var previous = LanguageCatalog.load(directory);
        Files.writeString(directory.resolve("pt_BR.yml"), "language-set: 'Idioma salvo.'\n");
        assertThrows(java.io.IOException.class, () -> LanguageCatalog.load(directory));
        assertEquals("Idioma salvo: pt_BR.", previous.text("pt_BR", "language-set", "pt_BR"));
    }
    @Test void insertedArgumentsCannotRewriteOtherPlaceholders() throws Exception {
        var catalog = LanguageCatalog.load(directory);
        assertEquals("Return portal {1} created at 10, 20, 30.", catalog.text("en_US", "debug-created", "{1}", 10, 20, 30));
    }
}
