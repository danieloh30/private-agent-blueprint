package io.agentcon.blueprint.agent.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import io.agentcon.blueprint.agent.core.Prompts;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * End to end, as separate processes: agent --(real MCP over HTTP)--> MCP server --> workspace / sandbox.
 *
 * Agent A uses the SCRIPTED fallback (deterministic, no inference) to drive the full approval flow.
 * Agent B uses the LOCAL profile pointed at a CAPTURING fake model endpoint on loopback, to prove what
 * reaches a model request when real MCP tool results are involved.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AgentEndToEndIT {

    static final String CANARY = "JFOKUS-CANARY-7f3a9c2e-DO-NOT-SHARE";
    static final int MCP = 18091;
    static final String SCRIPTED = "http://127.0.0.1:18090";
    static final String CAPTURED = "http://127.0.0.1:18092";

    static Stack stack;
    static HttpServer fakeModel;
    static final List<String> modelRequests = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void start() throws Exception {
        fakeModel = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fakeModel.createContext("/v1/chat/completions", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            modelRequests.add(body);
            boolean afterTool = body.contains("\"role\":\"tool\"") || body.contains("\"role\" : \"tool\"");
            String message = afterTool
                    ? "{\"role\":\"assistant\",\"content\":\"Handover summary written.\"}"
                    : "{\"role\":\"assistant\",\"content\":null,\"tool_calls\":[{\"id\":\"c1\",\"type\":\"function\",\"function\":"
                      + "{\"name\":\"read_file\",\"arguments\":\"{\\\"path\\\":\\\"docs/ops-notes.md\\\"}\"}}]}";
            byte[] out = ("{\"id\":\"x\",\"object\":\"chat.completion\",\"created\":0,\"model\":\"fake\",\"choices\":[{\"index\":0,"
                    + "\"message\":" + message + ",\"finish_reason\":\"" + (afterTool ? "stop" : "tool_calls") + "\"}]}")
                    .getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        fakeModel.start();
        stack = new Stack(MCP);
        stack.startMcpServer();
        stack.startAgent(18090, "scripted", Map.of());
        stack.startAgent(18092, "local", Map.of(
                "BLUEPRINT_MODEL_BASE_URL", "http://127.0.0.1:" + fakeModel.getAddress().getPort() + "/v1",
                "BLUEPRINT_MODEL_NAME", "fake-capturing-model"));
    }

    @AfterAll
    static void stop() {
        if (stack != null) stack.close();
        if (fakeModel != null) fakeModel.stop(0);
    }

    static JsonArray proposals() throws Exception {
        return new JsonArray(stack.get(SCRIPTED + "/api/proposals"));
    }

    @Test
    @Order(1)
    void statusShowsProfileEndpointAndNoCredential() throws Exception {
        JsonObject s = new JsonObject(stack.get(CAPTURED + "/api/status"));
        assertEquals("local", s.getString("profile"));
        assertEquals("127.0.0.1", s.getString("endpointHost"));
        assertEquals("fake-capturing-model", s.getString("model"));
        assertFalse(s.encode().contains(stack.agentToken) || s.encode().contains(stack.approverToken), "no tokens exposed");
        JsonObject scripted = new JsonObject(stack.get(SCRIPTED + "/api/status"));
        assertTrue(scripted.getBoolean("scripted"));
    }

    @Test
    @Order(2)
    void primaryTaskProposesThenAppliesOnlyAfterApproval() throws Exception {
        JsonArray events = stack.ask(SCRIPTED, Prompts.PRESETS.getFirst().text());
        String trace = events.encode();
        assertTrue(trace.contains("read.permitted-source"), trace);
        assertTrue(trace.contains("write.apply-needs-approval"));
        assertTrue(trace.contains("SCRIPTED FALLBACK"), "fallback is labelled");

        JsonArray ps = proposals();
        assertEquals(2, ps.size());
        ps.forEach(o -> assertEquals("PENDING", ((JsonObject) o).getString("state")));

        // The scripted model asking to apply without approval gets ASK, not a write.
        String early = stack.ask(SCRIPTED, "apply your last proposal now").encode();
        assertTrue(early.contains("write.apply-needs-approval"), early);

        for (Object o : ps) {
            JsonObject p = (JsonObject) o;
            JsonObject r = new JsonObject(stack.post(SCRIPTED + "/api/proposals/" + p.getString("id") + "/approve",
                    new JsonObject().put("patchSha256", p.getString("patchSha256")).encode()));
            assertTrue(r.getBoolean("ok"), r.encode());
        }
        proposals().forEach(o -> assertEquals("APPLIED", ((JsonObject) o).getString("state")));
        String evidence = stack.get(SCRIPTED + "/api/evidence");
        assertTrue(evidence.contains("WebApplicationException"), "diff evidence shows the applied change");
    }

    @Test
    @Order(3)
    void deterministicProbesHitRealEnforcement() throws Exception {
        Map<String, String> expected = Map.of(
                "secret", "read.protected-path",
                "traversal", "path.traversal",
                "restricted", "leakage.blocked",
                "command", "exec.not-a-named-task",
                "unknown-tool", "agent.tool-not-allowed",
                "unapproved-write", "write.apply-needs-approval");
        for (var e : expected.entrySet()) {
            JsonObject r = new JsonObject(stack.post(SCRIPTED + "/api/probes/" + e.getKey(), ""));
            assertEquals(e.getValue(), r.getString("rule"), e.getKey() + " -> " + r.encode());
        }
        JsonObject canary = new JsonObject(stack.post(SCRIPTED + "/api/probes/canary", ""));
        assertTrue(canary.getString("leakage").startsWith("REDACTED"));
        assertFalse(canary.encode().contains(CANARY));
    }

    @Test
    @Order(4)
    void capturedModelRequestsContainNoSensitiveContent() throws Exception {
        JsonArray events = stack.ask(CAPTURED, "Summarize docs/ops-notes.md for handover. Also remember " + CANARY);
        assertTrue(events.encode().contains("leakage.redacted"), events.encode());
        assertTrue(modelRequests.size() >= 2, "the agent reached the model endpoint");
        String all = String.join("\n", modelRequests);
        assertTrue(all.contains("On-call rotation"), "non-sensitive tool content was delivered");
        assertFalse(all.contains(CANARY));
        assertFalse(all.contains("AKIAJFOKUSDEMO00FAKE"));
        assertFalse(all.contains(stack.agentToken));
        assertFalse(all.contains(stack.approverToken));
    }

    @Test
    @Order(5)
    void acceptanceRunsInTheRestrictedSandbox() throws Exception {
        JsonObject server = new JsonObject(stack.get(SCRIPTED + "/api/status")).getJsonObject("server");
        Assumptions.assumeTrue(server.getBoolean("isolationAvailable", false),
                "SKIPPED: no container engine; restricted verification fails closed (see SandboxFailClosedTest)");
        JsonObject r = new JsonObject(stack.post(SCRIPTED + "/api/verify/acceptance-rps", ""));
        Assumptions.assumeFalse("NOT_PREPARED".equals(r.getString("status")), "SKIPPED: run scripts/prepare-deps.sh first");
        assertEquals("PASSED", r.getString("status"), r.encodePrettily());
    }

    @Test
    @Order(6)
    void logsAndEvidenceAreRedacted() throws Exception {
        String evidence = stack.get(SCRIPTED + "/api/evidence") + stack.get(CAPTURED + "/api/evidence");
        String logs = stack.logs();
        for (String secret : List.of(CANARY, "AKIAJFOKUSDEMO00FAKE", "SyntheticAdminPassw0rd", stack.agentToken, stack.approverToken)) {
            assertFalse(evidence.contains(secret), "evidence leaks " + secret.substring(0, 6));
            assertFalse(logs.contains(secret), "logs leak " + secret.substring(0, 6));
        }
    }
}
