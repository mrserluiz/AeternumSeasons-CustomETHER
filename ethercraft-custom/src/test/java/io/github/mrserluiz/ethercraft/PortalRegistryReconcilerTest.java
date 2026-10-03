package io.github.mrserluiz.ethercraft;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PortalRegistryReconcilerTest {
    @Test void breakingOneEndpointDropsBothLinksButPreservesPartnerAndUnrelatedPair() {
        Map<String, String> frames = new LinkedHashMap<>(Map.of("a", "broken", "b", "partner", "c", "other", "d", "otherPartner"));
        Map<String, String> links = new HashMap<>(Map.of("a", "b", "b", "a", "c", "d", "d", "c"));
        assertEquals(List.of("broken"), PortalRegistryReconciler.prune(frames, links, "broken"::equals, (a, b) -> true));
        assertFalse(frames.containsKey("a")); assertEquals("partner", frames.get("b"));
        assertEquals(Map.of("c", "d", "d", "c"), links);
    }
    @Test void changedWorldAuthorisationUnlinksSurvivingFrames() {
        Map<String, String> frames = new LinkedHashMap<>(Map.of("a", "world", "b", "oldHeat"));
        Map<String, String> links = new HashMap<>(Map.of("a", "b", "b", "a"));
        assertTrue(PortalRegistryReconciler.prune(frames, links, frame -> false, (a, b) -> false).isEmpty());
        assertEquals(2, frames.size()); assertTrue(links.isEmpty());
    }
    @Test void disabledTypeRemovesBothRecordsWithoutDanglingLinks() {
        Map<String, String> frames = new LinkedHashMap<>(Map.of("a", "heat", "b", "heat"));
        Map<String, String> links = new HashMap<>(Map.of("a", "b", "b", "a"));
        assertEquals(2, PortalRegistryReconciler.prune(frames, links, "heat"::equals, (a, b) -> true).size());
        assertTrue(frames.isEmpty()); assertTrue(links.isEmpty());
    }
    @Test void intactPairIsPreservedAndRepeatedCleanupIsIdempotent() {
        Map<String, String> frames = new LinkedHashMap<>(Map.of("a", "world", "b", "heat"));
        Map<String, String> links = new HashMap<>(Map.of("a", "b", "b", "a"));
        for (int i = 0; i < 2; i++) assertTrue(PortalRegistryReconciler.prune(frames, links, frame -> false, (a, b) -> true).isEmpty());
        assertEquals(Map.of("a", "b", "b", "a"), links);
    }
}
