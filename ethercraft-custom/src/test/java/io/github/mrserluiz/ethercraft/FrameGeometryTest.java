package io.github.mrserluiz.ethercraft;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;

class FrameGeometryTest {
    @Test void completeFourByFiveRectangleWithoutOverlap() {
        var interior = new HashSet<>(FrameGeometry.interior());
        var border = new HashSet<>(FrameGeometry.border());
        assertEquals(6, interior.size()); assertEquals(14, border.size());
        assertTrue(interior.stream().noneMatch(border::contains));
        var all = new HashSet<>(interior); all.addAll(border);
        for (int u = -1; u <= 2; u++) for (int v = -1; v <= 3; v++)
            assertTrue(all.contains(new FrameGeometry.Cell(u, v)));
        assertEquals(20, all.size());
    }
    @Test void cornersAreOptionalButEveryOtherFrameBlockIsRequired() {
        assertEquals(10, FrameGeometry.requiredBorder().size());
        assertTrue(FrameGeometry.validBorder(c -> !FrameGeometry.corner(c)));
        for (var missing : FrameGeometry.requiredBorder())
            assertFalse(FrameGeometry.validBorder(c -> !c.equals(missing)));
        assertEquals(4, FrameGeometry.border().stream().filter(FrameGeometry::corner).count());
        for (var corner : FrameGeometry.border().stream().filter(FrameGeometry::corner).toList())
            assertTrue(ReturnPortalGeometry.edits().contains(new ReturnPortalGeometry.Cell(corner.u(), corner.v(), 0)));
    }
}
