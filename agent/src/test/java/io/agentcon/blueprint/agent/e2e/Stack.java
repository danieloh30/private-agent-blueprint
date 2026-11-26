package io.agentcon.blueprint.agent.e2e;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Starts the packaged MCP server and agent jars as separate OS processes, exactly like scripts/start.sh. */
final class Stack implements AutoCloseable {

    static final Path REPO = Path.of(System.getProperty("e2e.repo.dir", "..")).toAbsolutePath().normalize();

    final Path state;
    final String agentToken = UUID.randomUUID().toString();
    final String approverToken = UUID.randomUUID().toString();
    final int mcpPort;
    private final List<Process> processes = new ArrayList<>();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    Stack(int mcpPort) throws IOException {
        this.mcpPort = mcpPort;
        this.state = Files.createTempDirectory("blueprint-e2e");
    }

    static Path jar(String module) {
        Path jar = REPO.resolve(module).resolve("target/quarkus-app/quarkus-run.jar");
        if (!Files.exists(jar)) {
            throw new IllegalStateException("Missing " + jar + ": run ./mvnw verify (packages modules before integration tests)");
        }
        return jar;
    }

    void startMcpServer() throws Exception {
        Path m2 = Path.of(System.getProperty("e2e.m2.dir", REPO.resolve(".demo/m2").toString()));
        start("mcp-server", List.of("-Dquarkus.http.port=" + mcpPort), Map.of(
                "BLUEPRINT_STATE_DIR", state.toString(),
                "BLUEPRINT_FIXTURE_DIR", REPO.resolve("arcade").toString(),
                "BLUEPRINT_ACCEPTANCE_DIR", REPO.resolve("acceptance").toString(),
                "BLUEPRINT_M2_DIR", m2.toString()));
        waitFor("http://127.0.0.1:" + mcpPort + "/admin/status", Map.of("X-Approver-Token", approverToken));
    }

    void startAgent(int port, String profile, Map<String, String> env) throws Exception {
        Map<String, String> e = new java.util.HashMap<>(env);
        e.put("BLUEPRINT_MCP_URL", "http://127.0.0.1:" + mcpPort + "/mcp");
        start("agent", List.of("-Dquarkus.http.port=" + port, "-Dquarkus.profile=" + profile), e);
        waitFor("http://127.0.0.1:" + port + "/api/presets", Map.of());
    }

    private void start(String module, List<String> sysProps, Map<String, String> env) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.addAll(sysProps);
        cmd.add("-jar");
        cmd.add(jar(module).toString());
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(REPO.resolve(module).toFile())
                .redirectErrorStream(true).redirectOutput(state.resolve(module + "-" + processes.size() + ".log").toFile());
        pb.environment().keySet().removeIf(k -> k.startsWith("BLUEPRINT_") || k.equals("LITEMAAS_API_KEY"));
        pb.environment().putAll(env);
        pb.environment().put("BLUEPRINT_AGENT_TOKEN", agentToken);
        pb.environment().put("BLUEPRINT_APPROVER_TOKEN", approverToken);
        processes.add(pb.start());
    }

    private void waitFor(String url, Map<String, String> headers) throws Exception {
        long deadline = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(2));
                headers.forEach(b::header);
                if (http.send(b.build(), HttpResponse.BodyHandlers.discarding()).statusCode() == 200) return;
            } catch (IOException ignored) {
                // not up yet
            }
            Thread.sleep(300);
        }
        throw new IllegalStateException("Timed out waiting for " + url + "; logs in " + state);
    }

    String get(String url) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(6)).build(),
                HttpResponse.BodyHandlers.ofString()).body();
    }

    String post(String url, String body) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(6))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body)).build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    /** Sends a prompt and waits until the agent run finishes. Returns the events of this run. */
    JsonArray ask(String agentUrl, String text) throws Exception {
        long after = lastSeq(agentUrl);
        post(agentUrl + "/api/ask", new JsonObject().put("text", text).encode());
        long deadline = System.currentTimeMillis() + 300_000;
        Thread.sleep(300);
        while (new JsonObject(get(agentUrl + "/api/session?after=0")).getBoolean("running")) {
            if (System.currentTimeMillis() > deadline) throw new IllegalStateException("agent run timed out");
            Thread.sleep(300);
        }
        return new JsonObject(get(agentUrl + "/api/session?after=" + after)).getJsonArray("events");
    }

    private long lastSeq(String agentUrl) throws Exception {
        JsonArray e = new JsonObject(get(agentUrl + "/api/session?after=0")).getJsonArray("events");
        return e.isEmpty() ? 0 : e.getJsonObject(e.size() - 1).getLong("seq");
    }

    String logs() throws IOException {
        StringBuilder sb = new StringBuilder();
        for (File f : state.toFile().listFiles((d, n) -> n.endsWith(".log"))) {
            sb.append(Files.readString(f.toPath()));
        }
        return sb.toString();
    }

    @Override
    public void close() {
        processes.forEach(Process::destroy);
        for (Process p : processes) {
            try {
                if (!p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
