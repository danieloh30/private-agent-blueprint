package io.agentcon.blueprint.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusTest
class SandboxCommandTest {

    @Inject
    SandboxRunner sandbox;

    @Test
    void containerCommandAppliesRestrictionsAndOnlyFixedArguments() {
        List<String> cmd = sandbox.containerCommand("c1", Path.of("/scratch"), Path.of("/m2"),
                VerificationTasks.TASKS.get("acceptance-rps"));
        String joined = String.join(" ", cmd);
        for (String flag : List.of("--network=none", "--cap-drop=ALL", "--security-opt=no-new-privileges", "--read-only",
                "--pids-limit=512", "--memory=2g", "--memory-swap=2g", "--pull=never", "/m2:/m2:ro", "/scratch:/work:rw")) {
            assertTrue(joined.contains(flag), flag + " missing in " + joined);
        }
        assertEquals(2, cmd.stream().filter("-v"::equals).count(), "exactly two mounts");
        assertFalse(joined.contains(".sock"), "no engine socket");
        assertFalse(joined.contains("--privileged"));
        assertFalse(joined.contains("--env-host"));
        assertTrue(joined.endsWith("-Dtest=acceptance.RpsAcceptanceTest test"), joined);
    }
}
