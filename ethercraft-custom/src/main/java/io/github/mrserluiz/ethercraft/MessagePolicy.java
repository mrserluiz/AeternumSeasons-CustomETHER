package io.github.mrserluiz.ethercraft;

/** Diagnostics require both the test switch and actual operator status. */
final class MessagePolicy {
    static boolean diagnostics(boolean enabled, boolean operator) { return enabled && operator; }
    static String feedback(String configured) {
        return switch (configured.toUpperCase(java.util.Locale.ROOT)) {
            case "OFF", "CHAT", "ACTION_BAR" -> configured.toUpperCase(java.util.Locale.ROOT);
            default -> "ACTION_BAR";
        };
    }
}
