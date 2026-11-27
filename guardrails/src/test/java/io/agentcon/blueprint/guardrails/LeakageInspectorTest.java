package io.agentcon.blueprint.guardrails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.agentcon.blueprint.guardrails.LeakageInspector.Mode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** All values below are synthetic test fixtures. */
class LeakageInspectorTest {

    static final String CANARY = LeakageInspector.DEFAULT_CANARY;

    @Test
    void redactsCanaryAndKeepsTheRest() {
        var r = LeakageInspector.defaults(Mode.REDACT).inspect("support token: " + CANARY + " end");
        assertFalse(r.blocked());
        assertFalse(r.text().contains(CANARY));
        assertTrue(r.text().contains("[REDACTED:canary]"));
        assertTrue(r.text().contains("support token:"));
        assertEquals("canary", r.findings().getFirst().rule());
    }

    @Test
    void blockModeWithholdsEverything() {
        var r = LeakageInspector.defaults(Mode.BLOCK).inspect("x " + CANARY);
        assertTrue(r.blocked());
        assertFalse(r.text().contains(CANARY));
        assertFalse(r.text().contains("x "));
    }

    @Test
    void detectsRepresentativeSecretPatterns() {
        String text = """
                aws_key_id=AKIADEMOEXAMPLE0FAKE
                token ghp_abcdefghijklmnopqrstuvwxyz0123456789AB
                gateway key sk-SYNTHETICabcdefghijklmnop0123
                db.password = SyntheticPassw0rd!
                Authorization: Bearer abcdefghijklmnopqrstuvwxyz.0123456789
                -----BEGIN PRIVATE KEY-----
                MIIsynthetic
                -----END PRIVATE KEY-----
                """;
        var r = LeakageInspector.defaults(Mode.REDACT).inspect(text);
        var rules = r.findings().stream().map(LeakageInspector.Finding::rule).toList();
        assertTrue(rules.containsAll(List.of("aws-access-key-id", "api-key-sk", "github-token", "credential-assignment",
                "bearer-token", "private-key")), rules.toString());
        assertFalse(r.text().contains("AKIADEMOEXAMPLE0FAKE"));
        assertFalse(r.text().contains("SyntheticPassw0rd"));
        assertFalse(r.text().contains("MIIsynthetic"));
    }

    @Test
    void restrictedMarkerAlwaysBlocksEvenInRedactMode() {
        var r = LeakageInspector.defaults(Mode.REDACT).inspect("# AGENT-RESTRICTED\npartner pricing: 42");
        assertTrue(r.blocked());
        assertFalse(r.text().contains("partner pricing"));
    }

    @Test
    void cleanTextPassesUnchanged() {
        String src = "public enum RpsChoice { ROCK, PAPER, SCISSORS }";
        var r = LeakageInspector.defaults(Mode.REDACT).inspect(src);
        assertTrue(r.clean());
        assertEquals(src, r.text());
    }

    @Test
    void customCanaryAndPatternAreConfigurable() {
        var inspector = new LeakageInspector(Mode.REDACT, List.of("ACME-CANARY-1"), "ACME-NO-AI",
                Map.of("employee-id", "EMP-\\d{6}"));
        var r = inspector.inspect("ACME-CANARY-1 EMP-123456");
        assertEquals("[REDACTED:canary] [REDACTED:employee-id]", r.text());
        assertTrue(inspector.inspect("ACME-NO-AI").blocked());
    }
}
