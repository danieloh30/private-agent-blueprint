package io.agentcon.blueprint.mcp;

import static io.agentcon.blueprint.mcp.McpTestSupport.APPROVER_TOKEN;
import static io.agentcon.blueprint.mcp.McpTestSupport.adminPostJson;
import static io.agentcon.blueprint.mcp.McpTestSupport.call;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.mcp.client.McpClient;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The presenter's promote endpoint refuses everything except an exact, reviewed, acceptance-verified diff.
 * In tests it targets a scratch copy (target/test-promote-target), never the real arcade/.
 */
@QuarkusTest
class PromoteEndpointTest {

    static final String RPS = McpToolsTest.RPS;

    @ConfigProperty(name = "quarkus.http.test-port")
    int port;

    @ConfigProperty(name = "blueprint.promote.target-dir")
    Path targetDir;

    @Inject
    ServerConfig config;

    McpClient client;

    @BeforeEach
    void setUp() throws Exception {
        if (Files.exists(targetDir)) {
            try (var walk = Files.walk(targetDir)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
        WorkspaceManager.copyTree(config.fixtureDir(), targetDir);
        McpTestSupport.resetWorkspace();
        client = McpTestSupport.client(port);
    }

    @AfterEach
    void tearDown() throws Exception {
        client.close();
    }

    private void approveAndApplyRpsFix() {
        String text = call(client, "propose_patch", Map.of("path", RPS, "find", "playerChoice = null;",
                "replace", "throw new jakarta.ws.rs.BadRequestException(\"Invalid move\");")).resultText();
        String id = text.replaceAll("(?s).*PROPOSAL (p-[0-9a-f]+).*", "$1");
        JsonArray proposals = new JsonArray(given().header("X-Approver-Token", APPROVER_TOKEN)
                .get("/admin/proposals").then().statusCode(200).extract().asString());
        adminPostJson("/admin/proposals/" + id + "/approve",
                new JsonObject().put("patchSha256", proposals.getJsonObject(0).getString("patchSha256")));
        assertTrue(call(client, "apply_patch", Map.of("proposalId", id)).resultText().contains("ALLOW write.approved"));
    }

    private JsonObject preview() {
        return new JsonObject(given().header("X-Approver-Token", APPROVER_TOKEN)
                .get("/admin/promote").then().statusCode(200).extract().asString());
    }

    @Test
    void requiresTheApproverToken() {
        given().get("/admin/promote").then().statusCode(401);
        given().header("Authorization", "Bearer " + McpTestSupport.AGENT_TOKEN)
                .contentType("application/json").body("{}").post("/admin/promote").then().statusCode(401);
    }

    @Test
    void nothingToPromoteBeforeAnyApprovedChange() {
        JsonObject p = preview();
        assertEquals(0, p.getJsonArray("files").size());
        assertFalse(p.getBoolean("verified"));
        given().header("X-Approver-Token", APPROVER_TOKEN).contentType("application/json")
                .body(new JsonObject().put("diffSha256", p.getString("diffSha256")).encode())
                .post("/admin/promote").then().statusCode(409).body(containsString("promote.nothing"));
    }

    @Test
    void refusesAStaleReviewAndAnUnverifiedChange() throws Exception {
        String before = Files.readString(targetDir.resolve(RPS));
        approveAndApplyRpsFix();

        JsonObject p = preview();
        assertEquals("PROMOTE", p.getJsonArray("files").getJsonObject(0).getString("status"));
        assertEquals(RPS, p.getJsonArray("files").getJsonObject(0).getString("path"));
        assertFalse(p.getBoolean("verified"), "no acceptance run yet");

        given().header("X-Approver-Token", APPROVER_TOKEN).contentType("application/json")
                .body(new JsonObject().put("diffSha256", "0".repeat(64)).encode())
                .post("/admin/promote").then().statusCode(409).body(containsString("promote.stale"));
        given().header("X-Approver-Token", APPROVER_TOKEN).contentType("application/json")
                .body(new JsonObject().put("diffSha256", p.getString("diffSha256")).encode())
                .post("/admin/promote").then().statusCode(409).body(containsString("promote.needs-passing-acceptance"));

        assertEquals(before, Files.readString(targetDir.resolve(RPS)), "nothing copied");
    }
}
