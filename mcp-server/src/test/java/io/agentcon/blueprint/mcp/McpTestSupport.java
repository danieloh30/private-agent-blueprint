package io.agentcon.blueprint.mcp;

import static io.restassured.RestAssured.given;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.service.tool.ToolExecutionResult;
import io.vertx.core.json.JsonObject;
import java.time.Duration;
import java.util.Map;

/** Real MCP client over Streamable HTTP: the same client library and transport the agent uses. */
final class McpTestSupport {

    static final String AGENT_TOKEN = "test-agent-token";
    static final String APPROVER_TOKEN = "test-approver-token";

    private McpTestSupport() {
    }

    static McpClient client(int port) {
        StreamableHttpMcpTransport transport = new StreamableHttpMcpTransport.Builder()
                .url("http://127.0.0.1:" + port + "/mcp")
                .customHeaders(Map.of("Authorization", "Bearer " + AGENT_TOKEN))
                .timeout(Duration.ofMinutes(6))
                .followRedirects(false)
                .logRequests(false)
                .logResponses(false)
                .build();
        return new DefaultMcpClient.Builder()
                .key("arcade-test")
                .clientName("mcp-server-test")
                .transport(transport)
                .toolExecutionTimeout(Duration.ofMinutes(6))
                .build();
    }

    /**
     * DefaultMcpClient throws ToolExecutionException for results flagged isError (policy denials);
     * the agent treats both the same way, so the tests normalise them into one result type.
     */
    static ToolExecutionResult call(McpClient client, String tool, Map<String, Object> args) {
        try {
            return client.executeTool(ToolExecutionRequest.builder()
                    .id("t-" + System.nanoTime())
                    .name(tool)
                    .arguments(new JsonObject(args).encode())
                    .build());
        } catch (dev.langchain4j.exception.ToolExecutionException e) {
            return ToolExecutionResult.builder().isError(true).resultText(e.getMessage()).build();
        }
    }

    static JsonObject adminPostJson(String path, JsonObject body) {
        return new JsonObject(given().header("X-Approver-Token", APPROVER_TOKEN)
                .contentType("application/json").body(body.encode())
                .post(path).then().extract().asString());
    }

    static void resetWorkspace() {
        given().header("X-Approver-Token", APPROVER_TOKEN).post("/admin/workspace/reset").then().statusCode(200);
    }
}
