package io.agentcon.blueprint.mcp;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The complete list of things that can be executed. Each task has a FIXED argument list;
 * nothing the model supplies is ever appended to a command line.
 */
public final class VerificationTasks {

    public record Task(String name, String description, Optional<String> acceptanceTest, List<String> mavenGoals) {
    }

    private static final List<String> OFFLINE = List.of("mvn", "-o", "-B", "-ntp", "-Dmaven.repo.local=/m2");

    public static final Map<String, Task> TASKS = Map.of(
            "unit-tests", new Task("unit-tests",
                    "Run the workspace's own unit tests (agent-editable, so not proof of correctness)",
                    Optional.empty(),
                    List.of("test")),
            "acceptance-rps", new Task("acceptance-rps",
                    "Presenter-owned acceptance checks for Rock Paper Scissors input validation",
                    Optional.of("RpsAcceptanceTest"),
                    List.of("-DskipTests=false", "-Dmaven.test.skip=false", "-Dsurefire.failIfNoSpecifiedTests=true",
                            "-Dtest=acceptance.RpsAcceptanceTest", "test")),
            "acceptance-scramble", new Task("acceptance-scramble",
                    "Presenter-owned acceptance checks for case-insensitive Word Scramble guesses",
                    Optional.of("ScrambleAcceptanceTest"),
                    List.of("-DskipTests=false", "-Dmaven.test.skip=false", "-Dsurefire.failIfNoSpecifiedTests=true",
                            "-Dtest=acceptance.ScrambleAcceptanceTest", "test")));

    public static final Set<String> NAMES = TASKS.keySet();

    public static List<String> command(Task task) {
        return java.util.stream.Stream.concat(OFFLINE.stream(), task.mavenGoals().stream()).toList();
    }

    private VerificationTasks() {
    }
}
