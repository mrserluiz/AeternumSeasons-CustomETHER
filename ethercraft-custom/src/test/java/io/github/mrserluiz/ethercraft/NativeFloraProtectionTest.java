package io.github.mrserluiz.ethercraft;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeFloraProtectionTest {
    // Mirrors the audited native exemption/purge contract, including silent AIR replacement.
    private static final class FloraController {
        private boolean enabled = true, protectPlayerPlaced = true;
        private final Set<String> marks = new HashSet<>();
        private void markPlayerPlaced(String block) { marks.add(block); }
        private void unmarkPlayerPlaced(String block) { marks.remove(block); }
        private boolean isProtectedByPlayer(String block) { return protectPlayerPlaced && marks.contains(block); }
        String winterPurge(String block) { return isProtectedByPlayer(block) ? "FLOWER" : "AIR"; }
    }
    @Test void programmaticallyPlacedPortalFlowerReceivesNativeExemptionInsteadOfSilentPurge() throws Exception {
        var nativeController = new FloraController();
        assertEquals("AIR", nativeController.winterPurge("portal-flower"));
        var bridge = new NativeFloraProtection<>(nativeController, String.class);
        bridge.refresh(Map.of("position", "portal-flower"));
        assertEquals("FLOWER", nativeController.winterPurge("portal-flower"));
        assertEquals("AIR", nativeController.winterPurge("natural-flower"));
    }
    @Test void removingPortalReleasesOnlyMarksOwnedByAddon() throws Exception {
        var nativeController = new FloraController(); nativeController.markPlayerPlaced("player-flower");
        var bridge = new NativeFloraProtection<>(nativeController, String.class);
        bridge.refresh(Map.of("player", "player-flower", "portal", "portal-flower"));
        bridge.refresh(Map.of());
        assertEquals(Set.of("player-flower"), nativeController.marks);
    }
    @Test void genuinePlayerReplacementTransfersMarkOwnershipWithoutMakingFlowerUnbreakable() throws Exception {
        var nativeController = new FloraController(); var bridge = new NativeFloraProtection<>(nativeController, String.class);
        bridge.refresh(Map.of("position", "portal-flower"));
        // Native BlockBreak handling still removes the marker.
        nativeController.unmarkPlayerPlaced("portal-flower"); bridge.forget("position");
        assertFalse(nativeController.isProtectedByPlayer("portal-flower"));
        // Later BlockPlace belongs to the player, not to the addon.
        nativeController.markPlayerPlaced("new-player-flower");
        bridge.refresh(Map.of("position", "new-player-flower")); bridge.release();
        assertTrue(nativeController.isProtectedByPlayer("new-player-flower"));
    }
    @Test void replacementControllerAndClearedNativeMarksCanBeReconciledAfterReload() throws Exception {
        var original = new FloraController(); var bridge = new NativeFloraProtection<>(original, String.class);
        bridge.refresh(Map.of("position", "portal-flower"));
        original.marks.clear(); bridge.refresh(Map.of("position", "portal-flower"));
        assertTrue(original.isProtectedByPlayer("portal-flower"));
        var replacement = new FloraController(); var reloaded = new NativeFloraProtection<>(replacement, String.class);
        reloaded.refresh(Map.of("position", "portal-flower"));
        assertTrue(replacement.isProtectedByPlayer("portal-flower"));
    }
    @Test void disabledNativeProtectionIsReportedWithoutChangingGlobalConfiguration() throws Exception {
        var nativeController = new FloraController(); nativeController.protectPlayerPlaced = false;
        var bridge = new NativeFloraProtection<>(nativeController, String.class);
        assertFalse(bridge.available()); bridge.refresh(Map.of("position", "portal-flower"));
        assertFalse(nativeController.protectPlayerPlaced); assertTrue(nativeController.marks.isEmpty());
    }
    @Test void unknownNativeVersionFailsExplicitlyWithoutTouchingUnrelatedObject() {
        assertThrows(ReflectiveOperationException.class, () -> new NativeFloraProtection<>(new Object(), String.class));
    }
}
