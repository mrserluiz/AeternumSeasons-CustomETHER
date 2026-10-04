package io.github.mrserluiz.ethercraft;

import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LanguagePreferencesTest {
    @TempDir Path directory;
    @Test void choicesSurviveRestartAndAreIndependentBetweenPlayers() throws Exception {
        Path file = directory.resolve("player-languages.yml");
        var preferences = new LanguagePreferences(file);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        preferences.set(first, "en-us"); preferences.set(second, "ru_RU");
        var reopened = new LanguagePreferences(file);
        assertEquals("en_US", reopened.get(first)); assertEquals("ru_RU", reopened.get(second));
        reopened.set(first, null);
        var reset = new LanguagePreferences(file);
        assertNull(reset.get(first)); assertEquals("ru_RU", reset.get(second));
    }
    @Test void unsupportedChoicePreservesPreferenceOnDiskAndInMemory() throws Exception {
        Path file = directory.resolve("player-languages.yml");
        var preferences = new LanguagePreferences(file); UUID player = UUID.randomUUID();
        preferences.set(player, "pt_BR"); byte[] previous = Files.readAllBytes(file);
        assertThrows(IllegalArgumentException.class, () -> preferences.set(player, "unknown"));
        assertEquals("pt_BR", preferences.get(player)); assertArrayEquals(previous, Files.readAllBytes(file));
    }
    @Test void failedWriteDoesNotChangeLastSavedPreference() throws Exception {
        Path file = directory.resolve("player-languages.yml");
        var preferences = new LanguagePreferences(file); UUID player = UUID.randomUUID();
        preferences.set(player, "pt_BR");
        Files.delete(file); Files.createDirectory(file); Files.writeString(file.resolve("prevent-replacement"), "occupied");
        assertThrows(java.io.IOException.class, () -> preferences.set(player, "en_US"));
        assertEquals("pt_BR", preferences.get(player));
    }
}
