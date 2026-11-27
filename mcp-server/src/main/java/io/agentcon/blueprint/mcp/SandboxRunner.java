package io.agentcon.blueprint.mcp;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jboss.logging.Logger;

/**
 * Runs a named verification task inside a restricted container.
 *
 * <p>Maven builds execute workspace-controlled code (tests, plugins, annotation processors), so the
 * run happens in a container with: no network, all capabilities dropped, no-new-privileges, a read-only
 * root filesystem, a read-only pre-populated dependency cache, memory/CPU/PID limits, a wall-clock
 * timeout, no host credentials and no container-engine socket. Only a scratch COPY of the workspace is
 * mounted writable.
 *
 * <p>If no container engine is available the task fails closed with {@link Status#ISOLATION_UNAVAILABLE}.
 * There is deliberately no fallback to an ordinary host child process.
 */
@ApplicationScoped
public class SandboxRunner {

    private static final Logger LOG = Logger.getLogger(SandboxRunner.class);
    private static final Set<String> SUPPORTED_ENGINES = Set.of("podman", "docker");
    private static final Pattern ATTR = Pattern.compile("\\b(tests|errors|skipped|failures)=\"(\\d+)\"");

    public enum Status { PASSED, FAILED, TIMEOUT, ISOLATION_UNAVAILABLE, NOT_PREPARED, ERROR }

    public record Result(String task, Status status, int exitCode, long durationMs,
                         int testsRun, int failures, int errors, int skipped,
                         String outputTail, List<String> controls) {
        public String headline() {
            return switch (status) {
                case PASSED -> "PASSED: " + testsRun + " tests, 0 failures (" + durationMs + " ms)";
                case FAILED -> "FAILED: " + testsRun + " tests, " + failures + " failures, " + errors + " errors (" + durationMs + " ms)";
                case TIMEOUT -> "TIMEOUT after " + durationMs + " ms; container killed";
                case ISOLATION_UNAVAILABLE -> "NOT RUN: restricted execution environment unavailable (fails closed)";
                case NOT_PREPARED -> "NOT RUN: dependencies not prepared (run scripts/prepare-deps.sh)";
                case ERROR -> "ERROR: " + outputTail;
            };
        }
    }

    private final ServerConfig config;
    private final AtomicInteger counter = new AtomicInteger();

    public SandboxRunner(ServerConfig config) {
        this.config = config;
    }

    /** Isolation controls applied to every run; shown in the UI and the evidence bundle. */
    public List<String> controls() {
        ServerConfig.Sandbox s = config.sandbox();
        return List.of(
                "engine=" + s.engine() + " (restricted container)",
                "network=none",
                "cap-drop=ALL, no-new-privileges",
                "read-only root filesystem, tmpfs /tmp",
                "memory=" + s.memory() + ", cpus=" + s.cpus() + ", pids=" + s.pidsLimit(),
                "timeout=" + s.timeout().toSeconds() + "s",
                "mounts: scratch copy of workspace (rw), prepared Maven cache (ro); nothing else",
                "no host env, credentials, home directory or engine socket");
    }

    public String engineBinary() {
        return config.sandbox().enginePath().orElse(config.sandbox().engine());
    }

    /** Checks that the container engine exists and answers. Does not run workspace code. */
    public Availability availability() {
        return availability(java.time.Duration.ofSeconds(20));
    }

    Availability availability(java.time.Duration timeout) {
        String engine = config.sandbox().engine().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_ENGINES.contains(engine)) {
            return new Availability(false, "Unsupported engine '" + engine + "'; only podman or docker provide the required isolation");
        }
        try {
            String format = engine.equals("docker") ? "{{.OSType}}" : "{{.Host.OS}}";
            Process p = new ProcessBuilder(engineBinary(), "info", "--format", format)
                    .redirectErrorStream(true).start();
            TailReader output = new TailReader(p.getInputStream(), 400);
            Thread reader = Thread.ofVirtual().start(output);
            if (!p.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                p.descendants().forEach(ProcessHandle::destroyForcibly);
                p.destroyForcibly();
                reader.interrupt();
                return new Availability(false, engine + " info timed out");
            }
            reader.join(1000);
            String out = output.text();
            return p.exitValue() == 0
                    ? new Availability(true, engine + " available (" + out.trim() + ")")
                    : new Availability(false, engine + " not usable: " + out.trim());
        } catch (IOException e) {
            return new Availability(false, engine + " not found: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Availability(false, "interrupted");
        }
    }

    public record Availability(boolean available, String detail) {
    }

    public Result run(String taskName, WorkspaceManager.Run run) {
        VerificationTasks.Task task = VerificationTasks.TASKS.get(taskName);
        if (task == null) {
            throw new IllegalArgumentException("Unknown task " + taskName);
        }
        long start = System.currentTimeMillis();
        Availability availability = availability();
        if (!availability.available()) {
            return new Result(taskName, Status.ISOLATION_UNAVAILABLE, -1, 0, 0, 0, 0, 0, availability.detail(), controls());
        }
        Path m2 = config.sandbox().m2Dir().toAbsolutePath().normalize();
        if (!Files.isDirectory(m2.resolve("io/quarkus"))) {
            return new Result(taskName, Status.NOT_PREPARED, -1, 0, 0, 0, 0, 0,
                    "Prepared Maven cache not found at " + m2, controls());
        }
        Path scratch;
        try {
            scratch = prepareScratch(task, run);
        } catch (IOException e) {
            return new Result(taskName, Status.ERROR, -1, 0, 0, 0, 0, 0, "Cannot prepare scratch copy: " + e.getMessage(), controls());
        }

        String containerName = "blueprint-verify-" + run.id().replaceAll("[^a-zA-Z0-9-]", "") + "-" + counter.incrementAndGet();
        LOG.infof("Sandboxed verification %s in %s", taskName, containerName);
        try {
            List<String> command = containerCommand(containerName, scratch, m2, task);
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            TailReader tail = new TailReader(process.getInputStream(), config.sandbox().maxOutputChars());
            Thread reader = Thread.ofVirtual().start(tail);
            boolean finished = process.waitFor(config.sandbox().timeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                kill(containerName);
                process.destroyForcibly();
                reader.join(2000);
                return new Result(taskName, Status.TIMEOUT, -1, System.currentTimeMillis() - start, 0, 0, 0, 0,
                        tail.text(), controls());
            }
            reader.join(5000);
            int exit = process.exitValue();
            int[] counts = surefireCounts(scratch, task);
            boolean ran = counts[0] > 0;
            Status status = exit == 0 && ran && counts[1] == 0 && counts[2] == 0 ? Status.PASSED : Status.FAILED;
            String output = tail.text();
            if (!ran && task.acceptanceTest().isPresent()) {
                output = "Acceptance tests did not run (no surefire report). " + output;
            }
            return new Result(taskName, status, exit, System.currentTimeMillis() - start,
                    counts[0], counts[1], counts[2], counts[3], condense(output), controls());
        } catch (IOException e) {
            return new Result(taskName, Status.ISOLATION_UNAVAILABLE, -1, 0, 0, 0, 0, 0,
                    "Container engine could not be started: " + e.getMessage(), controls());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            kill(containerName);
            return new Result(taskName, Status.ERROR, -1, 0, 0, 0, 0, 0, "interrupted", controls());
        }
    }

    List<String> containerCommand(String containerName, Path scratch, Path m2, VerificationTasks.Task task) throws IOException {
        ServerConfig.Sandbox s = config.sandbox();
        List<String> cmd = new ArrayList<>(List.of(
                engineBinary(), "run", "--rm", "--name", containerName,
                "--pull=never",
                "--network=none",
                "--cap-drop=ALL",
                "--security-opt=no-new-privileges",
                "--read-only",
                "--tmpfs", "/tmp:rw,size=512m",
                "--memory=" + s.memory(), "--memory-swap=" + s.memory(),
                "--cpus=" + s.cpus(),
                "--pids-limit=" + s.pidsLimit(),
                "-e", "HOME=/tmp",
                "-e", "MAVEN_CONFIG=/tmp/.m2",
                "-e", "MAVEN_OPTS=-Xmx768m -Duser.home=/tmp",
                "-v", scratch + ":/work:rw",
                "-v", m2 + ":/m2:ro",
                "-w", "/work",
                s.image()));
        // With every capability dropped, even container root cannot write a
        // Linux bind mount owned by the runner. Use the scratch directory owner.
        if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("linux")) {
            int imageIndex = cmd.size() - 1;
            List<String> identity = new ArrayList<>(List.of("--user",
                    Files.getAttribute(scratch, "unix:uid") + ":" + Files.getAttribute(scratch, "unix:gid")));
            if (config.sandbox().engine().equals("podman")) identity.add("--userns=keep-id");
            cmd.addAll(imageIndex, identity);
        }
        cmd.addAll(VerificationTasks.command(task));
        return cmd;
    }

    private Path prepareScratch(VerificationTasks.Task task, WorkspaceManager.Run run) throws IOException {
        Path scratch = run.runDir().resolve("verify").resolve(task.name() + "-" + System.currentTimeMillis());
        WorkspaceManager.copyTree(run.workspace(), scratch);
        if (task.acceptanceTest().isPresent()) {
            Path source = config.acceptanceDir().resolve("src/test/java/acceptance/" + task.acceptanceTest().get() + ".java");
            Path target = scratch.resolve("src/test/java/acceptance");
            Files.createDirectories(target);
            Files.copy(source, target.resolve(source.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        }
        return scratch;
    }

    /** tests, failures, errors, skipped from surefire XML reports (only the acceptance class for acceptance tasks). */
    static int[] surefireCounts(Path scratch, VerificationTasks.Task task) {
        int[] c = new int[4];
        Path reports = scratch.resolve("target/surefire-reports");
        if (!Files.isDirectory(reports)) {
            return c;
        }
        String prefix = task.acceptanceTest().map(t -> "TEST-acceptance." + t).orElse("TEST-");
        try (Stream<Path> files = Files.list(reports)) {
            for (Path f : files.filter(p -> p.getFileName().toString().startsWith(prefix)
                    && p.getFileName().toString().endsWith(".xml")).toList()) {
                String xml = Files.readString(f, StandardCharsets.UTF_8);
                int idx = xml.indexOf("<testsuite");
                if (idx < 0) continue;
                String head = xml.substring(idx, xml.indexOf('>', idx));
                Matcher m = ATTR.matcher(head);
                while (m.find()) {
                    int v = Integer.parseInt(m.group(2));
                    switch (m.group(1)) {
                        case "tests" -> c[0] += v;
                        case "failures" -> c[1] += v;
                        case "errors" -> c[2] += v;
                        case "skipped" -> c[3] += v;
                        default -> { }
                    }
                }
            }
        } catch (IOException e) {
            LOG.warnf("Cannot read surefire reports: %s", e.getMessage());
        }
        return c;
    }

    /** Keeps the lines a presenter cares about: test summaries, failures, build result. */
    static String condense(String output) {
        List<String> keep = new ArrayList<>();
        for (String line : output.split("\n")) {
            String l = line.strip();
            if (l.contains("Tests run:") || l.contains("FAIL") || l.contains("BUILD") || l.contains("expected")
                    || l.startsWith("[ERROR]   ") || l.contains("AssertionError") || l.contains("Status code")) {
                keep.add(l.replace("[INFO] ", "").replace("[ERROR] ", ""));
            }
        }
        String condensed = String.join("\n", keep.size() > 40 ? keep.subList(keep.size() - 40, keep.size()) : keep);
        return condensed.isBlank() ? output : condensed;
    }

    private void kill(String containerName) {
        try {
            Process k = new ProcessBuilder(engineBinary(), "kill", containerName).redirectErrorStream(true).start();
            k.waitFor(15, TimeUnit.SECONDS);
            new ProcessBuilder(engineBinary(), "rm", "-f", containerName).redirectErrorStream(true).start().waitFor(15, TimeUnit.SECONDS);
        } catch (IOException e) {
            LOG.warnf("Could not kill container %s: %s", containerName, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String read(InputStream in, int max) throws IOException {
        byte[] b = in.readNBytes(max);
        return new String(b, StandardCharsets.UTF_8);
    }

    /** Drains process output, keeping only the last {@code max} characters. */
    static final class TailReader implements Runnable {
        private final InputStream in;
        private final int max;
        private final StringBuilder buffer = new StringBuilder();

        TailReader(InputStream in, int max) {
            this.in = in;
            this.max = max * 4;
        }

        @Override
        public void run() {
            byte[] chunk = new byte[8192];
            try {
                int n;
                while ((n = in.read(chunk)) > 0) {
                    synchronized (buffer) {
                        buffer.append(new String(chunk, 0, n, StandardCharsets.UTF_8));
                        if (buffer.length() > max) {
                            buffer.delete(0, buffer.length() - max);
                        }
                    }
                }
            } catch (IOException ignored) {
                // process ended
            }
        }

        String text() {
            synchronized (buffer) {
                return buffer.toString();
            }
        }
    }
}
