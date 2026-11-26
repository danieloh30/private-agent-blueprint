package io.agentcon.blueprint.guardrails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WorkspacePathsTest {

    @TempDir
    Path tmp;
    Path ws;
    Path outside;
    WorkspacePaths paths;

    @BeforeEach
    void setUp() throws Exception {
        ws = Files.createDirectories(tmp.resolve("workspace"));
        outside = Files.createDirectories(tmp.resolve("outside"));
        Files.createDirectories(ws.resolve("src/main/java"));
        Files.writeString(ws.resolve("src/main/java/A.java"), "class A {}");
        Files.writeString(outside.resolve("host-secret.txt"), "not for the agent");
        paths = new WorkspacePaths(ws);
    }

    @Test
    void readsPermittedFileInsideWorkspace() {
        String rel = paths.normalize("./src/main/java/A.java");
        assertEquals("src/main/java/A.java", rel);
        Path resolved = paths.resolveExistingFile(rel);
        org.junit.jupiter.api.Assertions.assertTrue(resolved.startsWith(paths.root()), "resolves inside the workspace");
    }

    @ParameterizedTest
    @ValueSource(strings = {"../outside/host-secret.txt", "src/../../outside/host-secret.txt", "/etc/passwd",
            "~/.ssh/id_rsa", "C:/Windows/win.ini", "src\\main\\java\\A.java", "", "  "})
    void rejectsEscapesAndAbsolutePaths(String attempt) {
        assertThrows(PolicyViolation.class, () -> paths.resolveExistingFile(paths.normalize(attempt)));
    }

    @Test
    void rejectsSymlinkEscape() throws Exception {
        Files.createSymbolicLink(ws.resolve("src/link.txt"), outside.resolve("host-secret.txt"));
        PolicyViolation v = assertThrows(PolicyViolation.class,
                () -> paths.resolveExistingFile(paths.normalize("src/link.txt")));
        assertEquals("path.symlink", v.decision().rule());
    }

    @Test
    void rejectsSymlinkedDirectory() throws Exception {
        Files.createSymbolicLink(ws.resolve("src/out"), outside);
        assertThrows(PolicyViolation.class, () -> paths.resolveWriteTarget(paths.normalize("src/out/new.txt")));
    }

    @Test
    void newFileWriteTargetRequiresExistingParent() {
        paths.resolveWriteTarget(paths.normalize("src/main/java/B.java"));
        PolicyViolation v = assertThrows(PolicyViolation.class,
                () -> paths.resolveWriteTarget(paths.normalize("src/main/java/newpkg/C.java")));
        assertEquals("path.parent-missing", v.decision().rule());
    }

    @Test
    void missingFileIsReported() {
        PolicyViolation v = assertThrows(PolicyViolation.class,
                () -> paths.resolveExistingFile(paths.normalize("src/Nope.java")));
        assertEquals("path.not-found", v.decision().rule());
    }
}
