package io.agentcon.blueprint.mcp;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;

/** Test profiles that replace the container engine binary. Labelled fakes: they never claim isolation. */
final class FakeEngineProfiles {

    private FakeEngineProfiles() {
    }

    static Path fakeM2() {
        try {
            Path m2 = Path.of("target/fake-m2").toAbsolutePath();
            Files.createDirectories(m2.resolve("io/quarkus"));
            return m2;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** An "engine" whose `run` hangs, to prove the wall-clock timeout kills the run. */
    public static class HangingEngine implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            try {
                Path script = Path.of("target/fake-engine-hang.sh").toAbsolutePath();
                Path log = Path.of("target/fake-engine-hang.log").toAbsolutePath();
                Files.writeString(script, """
                        #!/bin/sh
                        echo "$@" >> %s
                        case "$1" in
                          info) echo linux; exit 0 ;;
                          run) sleep 60 ;;
                          *) exit 0 ;;
                        esac
                        """.formatted(log));
                Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
                Files.deleteIfExists(log);
                return Map.of("blueprint.sandbox.engine-path", script.toString(),
                        "blueprint.sandbox.timeout", "3s",
                        "blueprint.sandbox.m2-dir", fakeM2().toString(),
                        "blueprint.state-dir", "target/test-demo-hang");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /** No container engine at all: verification must fail closed, never fall back to the host. */
    public static class MissingEngine implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("blueprint.sandbox.engine-path", "/nonexistent/podman",
                    "blueprint.sandbox.m2-dir", fakeM2().toString(),
                    "blueprint.state-dir", "target/test-demo-missing");
        }
    }

    /** Engine discovery itself hangs without closing stdout; availability must still time out. */
    public static class HangingInfo implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            try {
                Path script = Path.of("target/fake-engine-info-hang.sh").toAbsolutePath();
                Files.writeString(script, "#!/bin/sh\nsleep 60\n");
                Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
                return Map.of("blueprint.sandbox.engine-path", script.toString(),
                        "blueprint.state-dir", "target/test-demo-info-hang");
            } catch (IOException e) { throw new UncheckedIOException(e); }
        }
    }
}
