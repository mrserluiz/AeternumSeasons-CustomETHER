package io.github.mrserluiz.ethercraft;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MessagePolicyTest {
    @Test void operatorStatusAloneDoesNotEnableTestDiagnostics() {
        assertFalse(MessagePolicy.diagnostics(false, true));
        assertFalse(MessagePolicy.diagnostics(false, false));
    }
    @Test void enablingTestsStillDoesNotExposeDiagnosticsToNonOperators() {
        assertFalse(MessagePolicy.diagnostics(true, false));
        assertTrue(MessagePolicy.diagnostics(true, true));
    }
    @Test void invalidFeedbackSettingNeverFallsBackToChat() {
        assertEquals("ACTION_BAR", MessagePolicy.feedback("typo"));
        assertEquals("OFF", MessagePolicy.feedback("off"));
        assertEquals("CHAT", MessagePolicy.feedback("chat"));
    }
}
