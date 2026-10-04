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
        assertEquals(28, edits.size()); assertEquals(48, PoolGeometry.clearance().size());
        assertTrue(Collections.disjoint(edits, PoolGeometry.clearance()));
        for (var c : PoolGeometry.border()) {
            assertTrue(edits.contains(new PoolGeometry.Cell(c.u(), c.v(), 0)));
            assertTrue(edits.contains(new PoolGeometry.Cell(c.u(), c.v(), 1)));
            for (int h = 2; h <= 4; h++) assertTrue(PoolGeometry.clearance().contains(new PoolGeometry.Cell(c.u(), c.v(), h)));
        }
        for (var c : PoolGeometry.interior()) {
            assertFalse(edits.contains(new PoolGeometry.Cell(c.u(), c.v(), -1)), "Existing pool floor must be preserved");
            assertTrue(edits.contains(new PoolGeometry.Cell(c.u(), c.v(), 0)));
            for (int h = 1; h <= 3; h++) assertTrue(PoolGeometry.clearance().contains(new PoolGeometry.Cell(c.u(), c.v(), h)));
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
    @Test void flowersArePlacedOnlyAfterCompleteRimAndWaterWithoutTemporarySolidFlowerLayer() {
        var plan = PoolGeometry.constructionPlan();
        assertEquals(new HashSet<>(PoolGeometry.edits()), new HashSet<>(plan.stream().map(PoolGeometry.Placement::cell).toList()));
        assertEquals(28, plan.size());
        assertTrue(plan.subList(0, 12).stream().allMatch(p -> p.part() == PoolGeometry.Part.FRAME && p.cell().height() == 0));
        assertTrue(plan.subList(12, 16).stream().allMatch(p -> p.part() == PoolGeometry.Part.WATER));
        assertTrue(plan.subList(16, 28).stream().allMatch(p -> p.part() == PoolGeometry.Part.FLOWER && p.cell().height() == 1));
    }
    @Test void undergroundFlowersNeedLightAndNaturalSkylightWorksAtNight() {
        assertFalse(PoolGeometry.flowerLight(0, 0));
        assertFalse(PoolGeometry.flowerLight(0, 7));
        assertTrue(PoolGeometry.flowerLight(0, 8));
        assertTrue(PoolGeometry.flowerLight(15, 0));
    }
}
