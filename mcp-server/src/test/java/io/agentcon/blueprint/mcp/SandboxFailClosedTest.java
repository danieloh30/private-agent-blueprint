package io.agentcon.blueprint.mcp;

import static io.agentcon.blueprint.mcp.McpTestSupport.call;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.mcp.client.McpClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(FakeEngineProfiles.MissingEngine.class)
class SandboxFailClosedTest {

    @ConfigProperty(name = "quarkus.http.test-port")
    int port;

    @Inject
    WorkspaceManager workspaces;

    @Test
    void withoutAContainerEngineNothingRunsOnTheHost() throws Exception {
        try (McpClient client = McpTestSupport.client(port)) {
            String text = call(client, "run_verification", Map.of("task", "unit-tests")).resultText();
            assertTrue(text.contains("DENY exec.isolation-unavailable"), text);
            assertTrue(text.contains("NOT RUN"), text);
        }
        assertFalse(Files.exists(workspaces.current().runDir().resolve("verify")), "no scratch copy, no build");
        assertFalse(Files.exists(workspaces.current().workspace().resolve("target")), "workspace was not built on the host");
    }
}
