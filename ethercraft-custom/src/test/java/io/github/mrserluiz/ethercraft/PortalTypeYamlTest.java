package io.github.mrserluiz.ethercraft;

import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PortalTypeYamlTest {
    @TempDir Path directory;
    private Path file() throws Exception {
        Path file = directory.resolve("portal-types.yaml");
        Files.writeString(file, """
            schema: 1
            portals:
              heat:
                enabled: false
                frame-block: NETHER_WART_BLOCK
                activation:
                  item: FLINT_AND_STEEL
                  mode: INTERACT
                source-worlds: []
                destination-world: aeternum_heat
            """);
        return file;
    }
    @Test void enablingHeatWritesActiveFileAndAppliesCurrentOriginWithoutRestart() throws Exception {
        Path file = file(); assertFalse(PortalTypeYaml.read(file.toFile()).getFirst().enabled());
        PortalTypeYaml.setEnabled(file.toFile(), "heat", true, "survival");
        var loaded = PortalTypeYaml.read(file.toFile()).getFirst();
        assertTrue(loaded.enabled()); assertEquals(List.of("survival"), loaded.sourceWorlds());
        assertEquals("aeternum_heat", loaded.unlinkedTarget("survival", "minecraft:overworld"));
    }
    @Test void externalYamlEditsAreReadAgainRatherThanCached() throws Exception {
        Path file = file(); var old = PortalTypeYaml.read(file.toFile()).getFirst();
        Files.writeString(file, Files.readString(file).replace("enabled: false", "enabled: true")
            .replace("source-worlds: []", "source-worlds: [world]").replace("aeternum_heat", "custom:heat"));
        var updated = PortalTypeYaml.read(file.toFile()).getFirst();
        assertFalse(old.enabled()); assertTrue(updated.enabled());
        assertEquals("custom:heat", updated.unlinkedTarget("world", "minecraft:overworld"));
    }
    @Test void disablingPreservesExplicitSourcesAndInvalidEditLeavesFileIntact() throws Exception {
        Path file = file(); PortalTypeYaml.setEnabled(file.toFile(), "heat", true, "world");
        PortalTypeYaml.setEnabled(file.toFile(), "heat", false, "other");
        assertEquals(List.of("world"), PortalTypeYaml.read(file.toFile()).getFirst().sourceWorlds());
        String before = Files.readString(file);
        assertThrows(IllegalArgumentException.class, () -> PortalTypeYaml.setEnabled(file.toFile(), "missing", true, "world"));
        assertEquals(before, Files.readString(file));
    }
    @Test void legacyYamlDefaultsToVerticalAndPoolShapeSurvivesEnableAndReload() throws Exception {
        Path file = file();
        assertEquals(PortalTypeSpec.Shape.VERTICAL, PortalTypeYaml.read(file.toFile()).getFirst().shape());
        Files.writeString(file, Files.readString(file).replace("frame-block: NETHER_WART_BLOCK", "shape: HORIZONTAL_POOL\n    frame-block: MOSS_BLOCK")
            .replace("FLINT_AND_STEEL", "DIAMOND").replace("INTERACT", "DROP_ITEM"));
        PortalTypeYaml.setEnabled(file.toFile(), "heat", true, "world");
        var pool = PortalTypeYaml.read(file.toFile()).getFirst();
        assertEquals(PortalTypeSpec.Shape.HORIZONTAL_POOL, pool.shape());
        assertEquals(PortalTypeSpec.ActivationMode.DROP_ITEM, pool.mode());
        assertEquals("aeternum_heat", pool.unlinkedTarget("world", "minecraft:overworld"));
        assertTrue(pool.permitsPair("world", "aeternum_heat"));
    }
    @Test void invalidShapeAndVerticalDropAreRejectedBeforeFileEdits() throws Exception {
        Path file = file();
        Files.writeString(file, Files.readString(file).replace("INTERACT", "DROP_ITEM"));
        String before = Files.readString(file);
        assertThrows(IllegalArgumentException.class, () -> PortalTypeYaml.setEnabled(file.toFile(), "heat", true, "world"));
        assertEquals(before, Files.readString(file));
        Files.writeString(file, before.replace("frame-block:", "shape: DIAGONAL\n    frame-block:"));
        assertThrows(IllegalArgumentException.class, () -> PortalTypeYaml.read(file.toFile()));
    }
    @Test void bundledYamlContainsDisabledDiamondPoolWithoutChangingExistingTypes() throws Exception {
        Path file = directory.resolve("defaults.yaml");
        try (var input = getClass().getResourceAsStream("/portal-types.yaml")) { Files.copy(input, file); }
        var all = PortalTypeYaml.read(file.toFile());
        assertEquals(4, all.size());
        var frost = all.stream().filter(t -> t.id().equals("frost")).findFirst().orElseThrow();
        assertTrue(frost.enabled()); assertEquals(PortalTypeSpec.Shape.VERTICAL, frost.shape());
        var pool = all.stream().filter(t -> t.id().equals("twilight")).findFirst().orElseThrow();
        assertFalse(pool.enabled()); assertEquals("MOSS_BLOCK", pool.frameBlock()); assertEquals("DIAMOND", pool.activationItem());
        assertEquals(PortalTypeSpec.Shape.HORIZONTAL_POOL, pool.shape());
        assertEquals(PortalTypeSpec.ActivationMode.DROP_ITEM, pool.mode());
    }
}
