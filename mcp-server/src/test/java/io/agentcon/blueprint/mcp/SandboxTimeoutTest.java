package io.agentcon.blueprint.mcp;

import static io.agentcon.blueprint.mcp.McpTestSupport.call;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.mcp.client.McpClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(FakeEngineProfiles.HangingEngine.class)
class SandboxTimeoutTest {

    @ConfigProperty(name = "quarkus.http.test-port")
    int port;

    @Test
    void hangingVerificationIsKilledAtTheTimeout() throws Exception {
        long start = System.currentTimeMillis();
        try (McpClient client = McpTestSupport.client(port)) {
            String text = call(client, "run_verification", Map.of("task", "unit-tests")).resultText();
            long elapsed = System.currentTimeMillis() - start;
            assertTrue(text.contains("TIMEOUT"), text);
            assertTrue(elapsed < 20_000, "returned after " + elapsed + " ms");
        }
        String calls = Files.readString(Path.of("target/fake-engine-hang.log"));
        assertTrue(calls.contains("kill blueprint-verify-"), "container was killed: " + calls);
    }
}
