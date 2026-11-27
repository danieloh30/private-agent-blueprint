package io.agentcon.blueprint.mcp;

import static io.agentcon.blueprint.mcp.McpTestSupport.APPROVER_TOKEN;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

/** Competing HTTP and MCP operations cannot replace or mutate a workspace being tested/promoted. */
@QuarkusTest
class WorkspaceTransactionTest {
    @Inject WorkspaceManager workspaces;
    @ConfigProperty(name = "quarkus.http.test-port") int port;

    @Test
    void workspaceLeaseRefusesConcurrentResetVerificationPromotionAndTools() throws Exception {
        try (var client = McpTestSupport.client(port); var lease = workspaces.acquire()) {
            for (String route : new String[]{"/workspace/reset", "/verify/acceptance-rps", "/promote"}) {
                given().header("X-Approver-Token", APPROVER_TOKEN).contentType("application/json")
                        .body("{}").post("/admin" + route).then().statusCode(409)
                        .body(containsString("workspace.busy"));
            }
            assertTrue(McpTestSupport.call(client, "list_files", java.util.Map.of()).resultText()
                    .contains("DENY workspace.busy"));
        }
        given().header("X-Approver-Token", APPROVER_TOKEN)
                .post("/admin/workspace/reset").then().statusCode(200);
    }
}
