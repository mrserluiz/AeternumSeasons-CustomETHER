package io.github.mrserluiz.ethercraft;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PortalCoordinatesTest {
    @Test void equalDimensionScalesPreservePositiveAndNegativeCoordinates() {
        assertEquals(1200.5, PortalCoordinates.scale(1200.5, 1, 1));
        assertEquals(-845.5, PortalCoordinates.scale(-845.5, 1, 1));
    }
    @Test void netherScalingAndReturnAreReciprocal() {
        assertEquals(100, PortalCoordinates.scale(800, 1, 8));
        assertEquals(800, PortalCoordinates.scale(100, 8, 1));
        assertEquals(-100, PortalCoordinates.scale(-800, 1, 8));
        assertEquals(-800, PortalCoordinates.scale(-100, 8, 1));
    }
    @Test void customScalesAreUsedAndSpawnCoordinatesAreIrrelevant() {
        assertEquals(1500, PortalCoordinates.scale(3000, 2, 4));
        assertFalse(PortalCoordinates.nearby(0, 0, 3000, 2000, 128));
        assertTrue(PortalCoordinates.nearby(2990, 2010, 3000, 2000, 16));
    }
    @Test void borderClampReservesRoomForFrameAndExit() {
        assertEquals(46, PortalCoordinates.clamp(1000, 0, 100, 4));
        assertEquals(-46, PortalCoordinates.clamp(-1000, 0, 100, 4));
        assertEquals(1046, PortalCoordinates.clamp(2000, 1000, 100, 4));
    }
    @Test void invalidScalesFailBeforeAnyWorldEdits() {
        assertThrows(IllegalArgumentException.class, () -> PortalCoordinates.scale(100, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> PortalCoordinates.scale(100, 1, Double.NaN));
    }
}
