package io.agentcon.blueprint.agent.tools;

import io.agentcon.blueprint.agent.AgentConfig;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Presenter-side channel to the MCP server's /admin endpoints (approvals, audit, acceptance checks).
 * This is deliberately NOT exposed as a tool: the model cannot approve its own changes.
 */
@ApplicationScoped
public class AdminClient {

    public record Reply(int status, String body) {
        public JsonObject json() {
            return body == null || body.isBlank() ? new JsonObject() : new JsonObject(body);
        }

        public JsonArray array() {
            return body == null || body.isBlank() ? new JsonArray() : new JsonArray(body);
        }
    }

    private final AgentConfig config;
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .proxy(HttpClient.Builder.NO_PROXY)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public AdminClient(AgentConfig config) {
        this.config = config;
    }

    private URI uri(String path) {
        URI mcp = URI.create(config.mcp().url());
        return URI.create(mcp.getScheme() + "://" + mcp.getHost() + ":" + mcp.getPort() + "/admin" + path);
    }

    public Reply get(String path) {
        return send(HttpRequest.newBuilder(uri(path)).GET());
    }

    public Reply post(String path, JsonObject body) {
        return send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body.encode())));
    }

    private Reply send(HttpRequest.Builder builder) {
        try {
            HttpResponse<String> r = http.send(builder
                    .header("X-Approver-Token", config.mcp().approverToken())
                    .timeout(Duration.ofSeconds(330))
                    .build(), HttpResponse.BodyHandlers.ofString());
            return new Reply(r.statusCode(), r.body());
        } catch (IOException e) {
            return new Reply(503, new JsonObject().put("error", "MCP server unreachable: " + e.getMessage()).encode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Reply(503, new JsonObject().put("error", "interrupted").encode());
        }
    }
}
