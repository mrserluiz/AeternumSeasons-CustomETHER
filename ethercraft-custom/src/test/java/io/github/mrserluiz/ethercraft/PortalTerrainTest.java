package io.github.mrserluiz.ethercraft;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PortalTerrainTest {
    @Test void terrainFallbackCanClearSnowGrassAndUndergroundTerrain() {
        for (String material : new String[]{"AIR", "SNOW", "SHORT_GRASS", "STONE", "NETHERRACK", "DEEPSLATE"})
            assertTrue(PortalTerrain.canReplace(material));
    }
    @Test void fallbackRejectsLiquidsContainersProtectedBlocksAndOtherPortalFrames() {
        for (String material : new String[]{"LAVA", "WATER", "CHEST", "BARREL", "BEDROCK", "OBSIDIAN", "NETHER_PORTAL", "GLOWSTONE", "NETHER_WART_BLOCK", "OAK_PLANKS"})
            assertFalse(PortalTerrain.canReplace(material));
    }
}
