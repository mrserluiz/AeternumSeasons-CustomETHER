package io.github.mrserluiz.ethercraft;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PortalLinksTest {
    @Test void newestEntranceTakesOverReturnWithoutStaleLinks() {
        var links = new HashMap<>(Map.of("old", "return", "return", "old", "other", "elsewhere", "elsewhere", "other"));
        PortalLinks.rebind(links, "new", "return");
        assertEquals("return", links.get("new"));
        assertEquals("new", links.get("return"));
        assertFalse(links.containsKey("old"));
        assertEquals("elsewhere", links.get("other"));
        assertEquals("other", links.get("elsewhere"));
    }
    @Test void rebindingTwoPairsDetachesBothPreviousPartners() {
        var links = new HashMap<>(Map.of("a", "b", "b", "a", "c", "d", "d", "c"));
        PortalLinks.rebind(links, "a", "c");
        assertEquals(Map.of("a", "c", "c", "a"), links);
    }
    @Test void selfLinkLeavesExistingPairUntouched() {
        var links = new HashMap<>(Map.of("a", "b", "b", "a"));
        assertThrows(IllegalArgumentException.class, () -> PortalLinks.rebind(links, "a", "a"));
        assertEquals(Map.of("a", "b", "b", "a"), links);
    }
}
