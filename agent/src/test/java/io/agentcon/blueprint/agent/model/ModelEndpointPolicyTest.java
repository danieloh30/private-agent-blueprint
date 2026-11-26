package io.agentcon.blueprint.agent.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ModelEndpointPolicyTest {

    /** Deterministic resolver: no DNS lookups in tests. */
    static final ModelEndpointPolicy POLICY = new ModelEndpointPolicy(host -> switch (host) {
        case "localhost" -> new InetAddress[] {InetAddress.getByName("127.0.0.1")};
        case "sneaky.local" -> new InetAddress[] {InetAddress.getByName("127.0.0.1"), InetAddress.getByName("10.1.2.3")};
        default -> new InetAddress[] {InetAddress.getByName(host.matches("[0-9.:]+") ? host : "203.0.113.10")};
    });

    static ModelEndpointPolicy.Endpoint validate(String profile, String url, String model, List<String> approved) {
        return POLICY.validate(profile, Optional.ofNullable(url), Optional.ofNullable(model), Optional.ofNullable(approved));
    }

    @Test
    void localAcceptsLoopbackOnly() {
        assertEquals("127.0.0.1", validate("local", "http://127.0.0.1:11434/v1", "m", null).host());
        assertEquals("localhost", validate("local", "http://localhost:11434/v1", "m", null).host());
        assertEquals("[::1]", validate("local", "http://[::1]:8000/v1", "m", null).host());
        assertThrows(IllegalStateException.class, () -> validate("local", "https://api.openai.com/v1", "m", null));
        assertThrows(IllegalStateException.class, () -> validate("local", "http://192.168.1.20:11434/v1", "m", null));
        assertThrows(IllegalStateException.class, () -> validate("local", "http://sneaky.local:11434/v1", "m", null));
    }

    @Test
    void enterpriseRequiresHttpsAndAnApprovedHost() {
        var ok = validate("enterprise", "https://llm.internal.example/v1", "qwen", List.of("llm.internal.example"));
        assertEquals("https://llm.internal.example:443", ok.display());
        assertThrows(IllegalStateException.class,
                () -> validate("enterprise", "http://llm.internal.example/v1", "qwen", List.of("llm.internal.example")));
        assertThrows(IllegalStateException.class,
                () -> validate("enterprise", "https://api.openai.com/v1", "gpt", List.of("llm.internal.example")));
        assertThrows(IllegalStateException.class, () -> validate("enterprise", "https://llm.internal.example/v1", "qwen", null));
    }

    @Test
    void endpointAndModelAreRequiredWithoutFallback() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> validate("local", null, "m", null));
        assertTrue(e.getMessage().contains("no public fallback"));
        assertThrows(IllegalStateException.class, () -> validate("local", "http://127.0.0.1:11434/v1", null, null));
        assertThrows(IllegalStateException.class, () -> validate("local", "http://127.0.0.1:11434/v1", " ", null));
        assertThrows(IllegalStateException.class, () -> validate("", "http://127.0.0.1:11434/v1", "m", null));
        assertThrows(IllegalStateException.class, () -> validate("openai", "http://127.0.0.1:11434/v1", "m", null));
    }

    @Test
    void credentialsAndOddUrlsAreRejected() {
        assertThrows(IllegalStateException.class, () -> validate("local", "http://user:pw@127.0.0.1:11434/v1", "m", null));
        assertThrows(IllegalStateException.class, () -> validate("local", "http://127.0.0.1:11434/v1?key=x", "m", null));
        assertThrows(IllegalStateException.class, () -> validate("local", "ftp://127.0.0.1/v1", "m", null));
    }

    @Test
    void scriptedHasNoEndpoint() {
        var e = validate("scripted", null, null, null);
        assertEquals(ModelEndpointPolicy.Profile.SCRIPTED, e.profile());
        assertTrue(e.display().contains("scripted"));
    }
}
