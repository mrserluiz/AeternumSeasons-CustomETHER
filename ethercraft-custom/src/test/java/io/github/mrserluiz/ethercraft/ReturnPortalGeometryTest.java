package io.github.mrserluiz.ethercraft;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReturnPortalGeometryTest {
    @Test void everyEditedCellIsIncludedOnceForProtectionAndRollback() {
        var edits = ReturnPortalGeometry.edits();
        assertEquals(20, edits.size());
        assertEquals(edits.size(), new HashSet<>(edits).size());
        assertEquals(20, edits.stream().filter(c -> c.side() == 0).count());
        assertTrue(Collections.disjoint(edits, ReturnPortalGeometry.clearance()));
    }
    @Test void verticalNeedsOnlyItsOwnSizeAndDoesNotRequireBothSideExits() {
        assertTrue(ReturnPortalGeometry.clearance().isEmpty());
        assertEquals(20, ReturnPortalGeometry.edits().size());
        assertTrue(ReturnPortalGeometry.edits().stream().allMatch(c -> c.side() == 0));
        assertEquals(2, GroundedPlacement.support(false).size());
    }
}
