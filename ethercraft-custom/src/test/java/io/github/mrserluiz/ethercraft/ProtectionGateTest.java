package io.github.mrserluiz.ethercraft;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProtectionGateTest {
    @Test void allowedRegionChecksEveryInteriorAndBorderPoint() {
        var cells = new ArrayList<>(FrameGeometry.interior()); cells.addAll(FrameGeometry.border());
        List<FrameGeometry.Cell> visited = new ArrayList<>();
        var provider = new ProtectionGate.Provider<FrameGeometry.Cell>("WorldGuard", c -> { visited.add(c); return null; });
        assertNull(ProtectionGate.firstDenial(cells, List.of(provider)));
        assertEquals(20, visited.size()); assertEquals(new HashSet<>(cells), new HashSet<>(visited));
    }
    @Test void denialAtLastBorderPointBlocksWholeOperation() {
        var cells = new ArrayList<>(FrameGeometry.interior()); cells.addAll(FrameGeometry.border());
        var last = cells.get(cells.size() - 1);
        var provider = new ProtectionGate.Provider<FrameGeometry.Cell>("WorldGuard", c -> c.equals(last) ? "região protegida" : null);
        assertEquals("WorldGuard: região protegida", ProtectionGate.firstDenial(cells, List.of(provider)));
    }
    @Test void anotherProviderCanDenyAfterWorldGuardAllows() {
        List<ProtectionGate.Provider<Integer>> providers = List.of(
            new ProtectionGate.Provider<>("WorldGuard", p -> null),
            new ProtectionGate.Provider<>("GriefPrevention", p -> "sem trust"));
        assertEquals("GriefPrevention: sem trust", ProtectionGate.firstDenial(List.of(1), providers));
    }
    @Test void apiFailureDoesNotGrantPermission() {
        var failure = new ProtectionGate.Provider<Integer>("WorldGuard", p -> { throw new NoClassDefFoundError(); });
        assertTrue(ProtectionGate.firstDenial(List.of(1), List.of(failure)).startsWith("WorldGuard:"));
        var reflectiveFailure = new ProtectionGate.Provider<Integer>("GriefPrevention", p -> { throw new ReflectiveOperationException(); });
        assertTrue(ProtectionGate.firstDenial(List.of(1), List.of(reflectiveFailure)).startsWith("GriefPrevention:"));
    }
    @Test void absentProtectionProvidersPermitNormalActivation() {
        assertNull(ProtectionGate.firstDenial(FrameGeometry.interior(), List.of()));
    }
}
