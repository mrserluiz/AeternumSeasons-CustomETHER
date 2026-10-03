package io.github.mrserluiz.ethercraft;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReturnPortalGeometryTest {
    @Test void everyEditedCellIsIncludedOnceForProtectionAndRollback() {
        var edits = ReturnPortalGeometry.edits();
        assertEquals(24, edits.size());
        assertEquals(edits.size(), new HashSet<>(edits).size());
        assertEquals(20, edits.stream().filter(c -> c.side() == 0).count());
        assertTrue(Collections.disjoint(edits, ReturnPortalGeometry.clearance()));
    }
    @Test void bothSidesHaveTwoLandingCellsWithFeetAndHeadClearance() {
        for (int side : new int[]{-1, 1}) for (int u = 0; u < 2; u++) {
            assertTrue(ReturnPortalGeometry.edits().contains(new ReturnPortalGeometry.Cell(u, -1, side)));
            assertTrue(ReturnPortalGeometry.clearance().contains(new ReturnPortalGeometry.Cell(u, 0, side)));
            assertTrue(ReturnPortalGeometry.clearance().contains(new ReturnPortalGeometry.Cell(u, 1, side)));
        }
        assertEquals(8, ReturnPortalGeometry.clearance().size());
    }
}
