package io.github.mrserluiz.ethercraft;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PoolGeometryTest {
    @Test void poolAndRimCoverExactlyFourByFourWithCorners() {
        Set<FrameGeometry.Cell> cells = new HashSet<>(PoolGeometry.interior());
        assertEquals(4, cells.size()); assertEquals(12, PoolGeometry.border().size());
        assertTrue(Collections.disjoint(PoolGeometry.interior(), PoolGeometry.border()));
        cells.addAll(PoolGeometry.border()); assertEquals(16, cells.size());
        for (int u = -1; u <= 2; u++) for (int v = -1; v <= 2; v++)
            assertTrue(cells.contains(new FrameGeometry.Cell(u, v)));
    }
    @Test void returnPlanCoversWaterFloorFlowersAndSafeHeadroomWithoutOverlap() {
        Set<PoolGeometry.Cell> edits = new HashSet<>(PoolGeometry.edits());
        assertEquals(32, edits.size()); assertEquals(20, PoolGeometry.clearance().size());
        assertTrue(Collections.disjoint(edits, PoolGeometry.clearance()));
        for (var c : PoolGeometry.border()) {
            assertTrue(edits.contains(new PoolGeometry.Cell(c.u(), c.v(), 0)));
            assertTrue(edits.contains(new PoolGeometry.Cell(c.u(), c.v(), 1)));
            assertTrue(PoolGeometry.clearance().contains(new PoolGeometry.Cell(c.u(), c.v(), 2)));
        }
        for (var c : PoolGeometry.interior()) {
            assertTrue(edits.contains(new PoolGeometry.Cell(c.u(), c.v(), -1)));
            assertTrue(edits.contains(new PoolGeometry.Cell(c.u(), c.v(), 0)));
            for (int h = 1; h <= 2; h++) assertTrue(PoolGeometry.clearance().contains(new PoolGeometry.Cell(c.u(), c.v(), h)));
        }
    }
    @Test void breakingAnyRimFlowerWaterOrFloorInvalidatesPool() {
        assertTrue(PoolGeometry.valid(c -> true, c -> true, c -> true, c -> true));
        for (var missing : PoolGeometry.border()) {
            assertFalse(PoolGeometry.valid(c -> !c.equals(missing), c -> true, c -> true, c -> true));
            assertFalse(PoolGeometry.valid(c -> true, c -> !c.equals(missing), c -> true, c -> true));
        }
        for (var missing : PoolGeometry.interior()) {
            assertFalse(PoolGeometry.valid(c -> true, c -> true, c -> !c.equals(missing), c -> true));
            assertFalse(PoolGeometry.valid(c -> true, c -> true, c -> true, c -> !c.equals(missing)));
        }
    }
    @Test void returnFlowersAreVariedSupportedAndExcludeHazardousOrTallPlants() {
        assertEquals(6, new HashSet<>(PoolGeometry.returnFlowers()).size());
        assertTrue(PoolGeometry.returnFlowers().stream().allMatch(PoolGeometry::flower));
        for (String rejected : List.of("WITHER_ROSE", "SUNFLOWER", "TALL_GRASS", "AIR", "MOSS_CARPET"))
            assertFalse(PoolGeometry.flower(rejected));
    }
}
