package io.agentcon.blueprint.agent.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import io.agentcon.blueprint.agent.AgentConfig;
import io.agentcon.blueprint.agent.core.AgentLoop;
import io.agentcon.blueprint.agent.core.AgentSession;
import io.agentcon.blueprint.agent.core.Prompts;
import io.agentcon.blueprint.agent.tools.ToolGateway;
import io.agentcon.blueprint.guardrails.LeakageInspector;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The agent reaches a model endpoint through the real LangChain4j OpenAI-compatible client and the guarded
 * HTTP client. The endpoint is a CAPTURING TEST SERVER on loopback that records every request body, so we can
 * prove what would have been sent to a model. All sensitive values are synthetic.
 */
@io.quarkus.test.junit.QuarkusTest
class CapturingModelEndpointTest {

    static final String CANARY = LeakageInspector.DEFAULT_CANARY;

    HttpServer server;
    final List<String> bodies = new CopyOnWriteArrayList<>();
    final List<String> authHeaders = new CopyOnWriteArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", ex -> {
            bodies.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            authHeaders.add(String.valueOf(ex.getRequestHeaders().getFirst("Authorization")));
            String message = bodies.size() == 1
                    ? "{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"call_1\",\"type\":\"function\","
                      + "\"function\":{\"name\":\"read_file\",\"arguments\":\"{\\\"path\\\":\\\"docs/ops-notes.md\\\"}\"}}]}"
                    : "{\"role\":\"assistant\",\"content\":\"Summary: on-call is weekdays.\"}";
            String body = "{\"id\":\"x\",\"object\":\"chat.completion\",\"created\":0,\"model\":\"fake\",\"choices\":[{\"index\":0,"
                    + "\"message\":" + message + ",\"finish_reason\":\"" + (bodies.size() == 1 ? "tool_calls" : "stop") + "\"}],"
                    + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5,\"total_tokens\":15}}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    /** A tool that (wrongly) returns raw sensitive content, to prove the agent-side layer catches it too. */
    static final class LeakyTool implements ToolGateway {
        @Override
        public List<ToolSpecification> listTools() {
            return List.of(ToolSpecification.builder().name("read_file").description("read")
                    .parameters(JsonObjectSchema.builder().addStringProperty("path").build()).build());
        }

        @Override
        public Outcome execute(String id, String name, String args) {
            return new Outcome(false, "[policy] ALLOW read.permitted-source: x\nescalation " + CANARY
                    + "\nkey AKIADEMOEXAMPLE0FAKE\npassword = SyntheticPassw0rd!");
        }

        @Override
        public String describe() {
            return "leaky stub";
        }
    }

    record Limits() implements AgentConfig.Limits {
        public int maxSteps() { return 5; }
        public int maxToolCalls() { return 5; }
        public int maxToolOutputChars() { return 8000; }
        public Duration maxRunTime() { return Duration.ofSeconds(30); }
        public int maxInputChars() { return 4000; }
        public int maxAnswerChars() { return 4000; }
        public int maxHistoryMessages() { return 40; }
    }

    @Test
    void sensitiveContentIsAbsentFromEveryCapturedModelRequest() {
        var endpoint = new ModelEndpointPolicy().validate("local",
                Optional.of("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"), Optional.of("fake-model"), Optional.empty());
        var built = ModelFactory.openAi(endpoint, Optional.of("synthetic-test-key"), 0.0, Duration.ofSeconds(10), false);
        AgentLoop loop = new AgentLoop(built.model(), new LeakyTool(), LeakageInspector.defaults(LeakageInspector.Mode.REDACT),
                new Limits(), Set.of("read_file"), Prompts.SYSTEM, Clock.systemUTC());
        AgentSession session = new AgentSession("cap");

        var outcome = loop.run(session, "Summarize docs/ops-notes.md. My own note: " + CANARY);

        assertEquals("answered", outcome.stopReason());
        assertEquals(2, bodies.size(), "two real HTTP requests reached the model endpoint");
        assertTrue(built.http().built().requests() >= 2, "requests went through the guarded client");
        String all = String.join("\n", bodies);
        assertTrue(all.matches("(?s).*\"model\"\\s*:\\s*\"fake-model\".*"), "requests name the configured model");
        assertTrue(all.contains("[REDACTED:canary]"));
        assertFalse(all.contains(CANARY), "canary never sent");
        assertFalse(all.contains("AKIADEMOEXAMPLE0FAKE"), "secret pattern never sent");
        assertFalse(all.contains("SyntheticPassw0rd"), "credential assignment never sent");
        assertTrue(all.contains("untrusted"), "tool output delivered as untrusted data");
        // The API key goes only in the Authorization header to the approved host, never in the body or the trace.
        assertTrue(authHeaders.stream().allMatch("Bearer synthetic-test-key"::equals));
        assertFalse(all.contains("synthetic-test-key"));
        session.eventsAfter(0).forEach(e -> {
            assertFalse(e.detail().contains(CANARY));
            assertFalse(e.detail().contains("synthetic-test-key"));
        });
        assertEquals(URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"), endpoint.uri());
    }
}
