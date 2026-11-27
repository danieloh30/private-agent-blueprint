package io.agentcon.blueprint.mcp;

import static org.junit.jupiter.api.Assertions.*;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.time.Duration;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(FakeEngineProfiles.HangingInfo.class)
class SandboxAvailabilityTimeoutTest {
    @Inject SandboxRunner sandbox;

    @Test
    void hangingEngineInfoCannotBlockTheTimedWait() {
        long start = System.nanoTime();
        var result = sandbox.availability(Duration.ofMillis(150));
        assertFalse(result.available());
        assertTrue(result.detail().contains("timed out"));
        assertTrue(Duration.ofNanos(System.nanoTime() - start).toSeconds() < 3);
    }
}
