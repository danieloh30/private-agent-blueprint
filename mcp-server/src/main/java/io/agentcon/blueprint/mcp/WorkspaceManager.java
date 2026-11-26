package io.agentcon.blueprint.mcp;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.jboss.logging.Logger;

/**
 * Creates disposable copies of the coding-target fixture and tracks which one is current.
 *
 * <pre>
 * .demo/runs/&lt;run-id&gt;/workspace   agent-writable copy (only via approved patches)
 * .demo/runs/&lt;run-id&gt;/baseline    pristine snapshot used for show_diff
 * .demo/runs/&lt;run-id&gt;/verify/...  scratch copies mounted into the sandbox
 * .demo/runs/&lt;run-id&gt;/audit.jsonl redacted audit records
 * </pre>
 *
 * The current run is trusted server state. Tools read it per request; the model cannot change it.
 */
@ApplicationScoped
public class WorkspaceManager {

    private static final Logger LOG = Logger.getLogger(WorkspaceManager.class);
    private static final Set<String> SKIP = Set.of("target", ".git", ".idea", ".vscode", "node_modules", ".quarkus");

    public record Run(String id, Path runDir, Path workspace, Path baseline) {
    }

    private final ServerConfig config;
    private final AtomicReference<Run> current = new AtomicReference<>();

    public WorkspaceManager(ServerConfig config) {
        this.config = config;
    }

    void onStart(@Observes StartupEvent event) {
        Run run = createRun();
        LOG.infof("Disposable workspace ready: run=%s path=%s", run.id(), run.workspace());
    }

    public Run current() {
        Run run = current.get();
        if (run == null) {
            throw new IllegalStateException("No workspace has been created");
        }
        return run;
    }

    /** Creates a fresh disposable run from the fixture and makes it current. Never touches the fixture. */
    public synchronized Run createRun() {
        String id = "run-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
        Path runDir = config.stateDir().toAbsolutePath().normalize().resolve("runs").resolve(id);
        Path workspace = runDir.resolve("workspace");
        Path baseline = runDir.resolve("baseline");
        try {
            Files.createDirectories(runDir);
            copyTree(config.fixtureDir(), workspace);
            copyTree(config.fixtureDir(), baseline);
            Files.writeString(config.stateDir().resolve("current-run"), id + System.lineSeparator());
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create workspace from " + config.fixtureDir(), e);
        }
        Run run = new Run(id, runDir, workspace, baseline);
        current.set(run);
        return run;
    }

    /** Copies regular files and directories; skips build output, VCS data and every symlink. */
    static void copyTree(Path source, Path target) throws IOException {
        Path src = source.toRealPath();
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!dir.equals(src) && SKIP.contains(dir.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (Files.isSymbolicLink(dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(target.resolve(src.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (attrs.isRegularFile() && !Files.isSymbolicLink(file)) {
                    Files.copy(file, target.resolve(src.relativize(file).toString()),
                            StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
