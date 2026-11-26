package io.agentcon.blueprint.mcp;

import static io.agentcon.blueprint.mcp.McpTestSupport.adminPostJson;
import static io.agentcon.blueprint.mcp.McpTestSupport.call;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.mcp.client.McpClient;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * OPT-IN: runs real restricted containers (needs podman + scripts/prepare-deps.sh).
 * Enable with -Dsandbox.it=true. Reported as SKIPPED otherwise, never as passed.
 *
 * Proves the independent acceptance check for the primary task: FAILS on the seeded bug,
 * PASSES after an approved fix is applied through MCP.
 */
@QuarkusTest
@EnabledIfSystemProperty(named = "sandbox.it", matches = "true")
class SandboxContainerTest {

    static final String RPS = "src/main/java/io/agentcon/games/rps/RpsResource.java";

    @ConfigProperty(name = "quarkus.http.test-port")
    int port;

    @Test
    void acceptanceFailsBeforeAndPassesAfterApprovedFix() throws Exception {
        McpTestSupport.resetWorkspace();
        JsonObject before = verify("acceptance-rps");
        assertEquals("FAILED", before.getString("status"), before.encodePrettily());
        assertTrue(before.getInteger("tests") > 0, "acceptance tests actually ran");

        try (McpClient client = McpTestSupport.client(port)) {
            String fix = "throw new jakarta.ws.rs.WebApplicationException(jakarta.ws.rs.core.Response.status(400)"
                    + ".type(jakarta.ws.rs.core.MediaType.TEXT_PLAIN)"
                    + ".entity(\"Invalid move '\" + move + \"'. Choose ROCK, PAPER or SCISSORS.\").build());";
            String proposed = call(client, "propose_patch", Map.of("path", RPS,
                    "find", "playerChoice = null;", "replace", fix)).resultText();
            String id = proposed.replaceAll("(?s).*PROPOSAL (p-[0-9a-f]+).*", "$1");
            JsonObject p = new JsonArray(given().header("X-Approver-Token", McpTestSupport.APPROVER_TOKEN)
                    .get("/admin/proposals").asString()).getJsonObject(0);
            adminPostJson("/admin/proposals/" + id + "/approve", new JsonObject().put("patchSha256", p.getString("patchSha256")));
            assertTrue(call(client, "apply_patch", Map.of("proposalId", id)).resultText().contains("ALLOW write.approved"));
        }

        JsonObject after = verify("acceptance-rps");
        assertEquals("PASSED", after.getString("status"), after.encodePrettily());
    }

    private JsonObject verify(String task) {
        return new JsonObject(given().header("X-Approver-Token", McpTestSupport.APPROVER_TOKEN)
                .post("/admin/verify/" + task).then().statusCode(200).extract().asString());
    }
}
