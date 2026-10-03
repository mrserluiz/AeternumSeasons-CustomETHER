package io.github.mrserluiz.ethercraft;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.mrserluiz.ethercraft.PortalTypeSpec.ActivationMode.*;

class PortalTypeSpecTest {
    private PortalTypeSpec type(String id, boolean enabled, String source, String destination) {
        return new PortalTypeSpec(id, enabled, "BLUE_ICE", "SNOWBALL", PROJECTILE, List.of(source), destination);
    }
    @Test void routesAreBidirectionalButDoNotJoinTwoSources() {
        var spec = new PortalTypeSpec("frost", true, "BLUE_ICE", "SNOWBALL", PROJECTILE,
            List.of("world", "survival"), "ethercraft_frost");
        assertTrue(spec.permitsPair("world", "ethercraft_frost"));
        assertTrue(spec.permitsPair("ethercraft_frost", "survival"));
        assertFalse(spec.permitsPair("world", "survival"));
        assertFalse(spec.acceptsWorld("world_nether"));
        assertFalse(spec.permitsPair("ethercraft_frost", "ethercraft_frost"));
    }
    @Test void disabledTypeNeverActivatesOrRoutes() {
        var spec = type("frost", false, "world", "ethercraft_frost");
        assertFalse(spec.acceptsWorld("world"));
        assertFalse(spec.permitsPair("world", "ethercraft_frost"));
    }
    @Test void overlappingActivationIsRejectedIncludingAtDestination() {
        var frost = type("frost", true, "world", "ethercraft_frost");
        assertThrows(IllegalArgumentException.class, () -> PortalTypeSpec.validateDistinct(
            List.of(frost, type("custom", true, "world", "another"))));
        assertThrows(IllegalArgumentException.class, () -> PortalTypeSpec.validateDistinct(
            List.of(frost, type("custom", true, "other", "ethercraft_frost"))));
        assertDoesNotThrow(() -> PortalTypeSpec.validateDistinct(
            List.of(frost, type("custom", true, "other", "another"))));
        assertDoesNotThrow(() -> PortalTypeSpec.validateDistinct(
            List.of(frost, type("custom", false, "world", "another"))));
    }
    @Test void invalidIdsDuplicateSourcesAndSelfRoutesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> type("bad.id", true, "world", "frost"));
        assertThrows(IllegalArgumentException.class, () -> type("frost", true, "world", "world"));
        assertThrows(IllegalArgumentException.class, () -> new PortalTypeSpec("frost", true, "BLUE_ICE", "SNOWBALL",
            PROJECTILE, List.of("world", "world"), "frost"));
        var spec = type("frost", true, "world", "frost");
        assertThrows(IllegalArgumentException.class, () -> PortalTypeSpec.validateDistinct(List.of(spec, spec)));
    }
    @Test void loadedDimensionKeysWorkAlongsideLegacyNames() {
        var spec = type("frost", true, "minecraft:overworld", "aeternum:frost");
        assertTrue(spec.acceptsWorld("aeternum_frost", "aeternum:frost"));
        assertTrue(spec.permitsPair("world", "minecraft:overworld", "aeternum_frost", "aeternum:frost"));
        assertTrue(spec.permitsPair("aeternum_frost", "aeternum:frost", "world", "minecraft:overworld"));
        assertFalse(spec.acceptsWorld("aeternum_heat", "aeternum:heat"));
    }
    @Test void unlinkedPortalsTravelToConfiguredWorldAndReturnToSingleSource() {
        var spec = type("frost", true, "world", "aeternum_frost");
        assertEquals("aeternum_frost", spec.unlinkedTarget("world", "minecraft:overworld"));
        assertEquals("world", spec.unlinkedTarget("aeternum_frost", "aeternum:frost"));
        assertNull(spec.unlinkedTarget("world_nether", "minecraft:the_nether"));
    }
    @Test void ambiguousReturnAndAliasedSelfRouteAreBlocked() {
        var spec = new PortalTypeSpec("frost", true, "BLUE_ICE", "SNOWBALL", PROJECTILE,
            List.of("world", "survival"), "aeternum_frost");
        assertNull(spec.unlinkedTarget("aeternum_frost", "aeternum:frost"));
        var self = type("frost", true, "aeternum_frost", "aeternum:frost");
        assertNull(self.unlinkedTarget("aeternum_frost", "aeternum:frost"));
    }
    @Test void disabledOrUnauthorisedPortalsHaveNoSpawnFallback() {
        assertNull(type("heat", false, "world", "aeternum_heat").unlinkedTarget("world", "minecraft:overworld"));
        var empty = new PortalTypeSpec("heat", true, "NETHER_WART_BLOCK", "FLINT_AND_STEEL", INTERACT, List.of(), "aeternum_heat");
        assertNull(empty.unlinkedTarget("aeternum_heat", "aeternum:heat"));
    }
    @Test void reloadRoutingChangesInvalidateOldPairsButItemChangesDoNot() {
        var before = type("heat", true, "world", "aeternum_heat");
        assertTrue(PortalTypeSpec.routingChanged(before, type("heat", true, "survival", "aeternum_heat")));
        assertTrue(PortalTypeSpec.routingChanged(before, type("heat", true, "world", "new_heat")));
        assertTrue(PortalTypeSpec.routingChanged(before, type("heat", false, "world", "aeternum_heat")));
        assertTrue(PortalTypeSpec.routingChanged(before, null));
        var newItem = new PortalTypeSpec("heat", true, "BLUE_ICE", "EGG", PROJECTILE, List.of("world"), "aeternum_heat");
        assertFalse(PortalTypeSpec.routingChanged(before, newItem));
    }
}
