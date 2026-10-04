package io.github.mrserluiz.ethercraft;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GroundedPlacementTest {
    @Test void floatingSitesAndPartialGroundAreRejected() {
        for (boolean horizontal : new boolean[]{false, true}) {
            assertFalse(GroundedPlacement.grounded(horizontal, c -> false));
            assertTrue(GroundedPlacement.grounded(horizontal, c -> true));
            for (var gap : GroundedPlacement.support(horizontal))
                assertFalse(GroundedPlacement.grounded(horizontal, c -> !c.equals(gap)));
        }
    }
    @Test void poolMustBeEmbeddedInSoilAndHaveAnExistingFloor() {
        assertEquals(32, GroundedPlacement.support(true).size());
        assertFalse(GroundedPlacement.grounded(true, c -> c.height() == -1));
        assertFalse(GroundedPlacement.grounded(true, c -> c.height() == 0));
    }
    @Test void searchFindsGroundOutsideOldSixteenBlockVerticalWindowAndRespectsLimits() {
        assertEquals(List.of(64), GroundedPlacement.heights(-62, 315, 200, y -> y == 64));
        assertEquals(List.of(5, -5), GroundedPlacement.heights(-10, 10, 0, y -> y == -5 || y == 5));
        assertTrue(GroundedPlacement.heights(0, 10, 3, y -> y == 11 || y == -1).isEmpty());
        assertTrue(GroundedPlacement.heights(0, 10, 3, y -> false).isEmpty());
    }
}
