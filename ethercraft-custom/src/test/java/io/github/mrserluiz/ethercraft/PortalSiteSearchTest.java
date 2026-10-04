package io.github.mrserluiz.ethercraft;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PortalSiteSearchTest {
    @Test void searchContinuesBeyondOldEightBlockLimitAndReturnsClosestSitesFirst() {
        var offsets = PortalSiteSearch.offsets(20);
        assertEquals(new PortalSiteSearch.Offset(0,0), offsets.getFirst());
        assertTrue(offsets.contains(new PortalSiteSearch.Offset(20,0)));
        assertFalse(offsets.contains(new PortalSiteSearch.Offset(20,20)));
        for (int i=1; i<offsets.size(); i++) assertTrue(offsets.get(i-1).distanceSquared() <= offsets.get(i).distanceSquared());
        assertEquals(offsets.size(), new HashSet<>(offsets).size());
    }
    @Test void defaultExpansionAndConfiguredLimitsStayBounded() {
        assertEquals(128, PortalSiteSearch.radius(8,128));
        assertEquals(32, PortalSiteSearch.radius(8,32));
        assertEquals(128, PortalSiteSearch.radius(1000,1000));
        assertEquals(0, PortalSiteSearch.radius(-1,-1));
        assertEquals(List.of(new PortalSiteSearch.Offset(0,0)), PortalSiteSearch.offsets(0));
    }
}
