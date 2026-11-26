package io.agentcon.blueprint.guardrails;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.Set;

/**
 * Allow / ask / deny decisions for every action the agent can request.
 *
 * <ul>
 *   <li>Reads of permitted source: ALLOW</li>
 *   <li>Proposing a patch to a writable path: ALLOW (it only produces a diff)</li>
 *   <li>Applying a patch: ASK, until a human approval bound to that exact patch exists</li>
 *   <li>Named verification tasks: ALLOW</li>
 *   <li>Protected paths, unknown tools, unsupported actions, arbitrary commands: DENY</li>
 * </ul>
 *
 * Protected paths are checked first, so no allow rule can override them.
 */
public final class PolicyEngine {

    public record Rules(List<String> readAllow,
                        List<String> writeAllow,
                        List<String> protectedPaths,
                        Set<String> tasks) {
    }

    private final Rules rules;
    private final List<PathMatcher> readAllow;
    private final List<PathMatcher> writeAllow;
    private final List<PathMatcher> protectedPaths;

    public PolicyEngine(Rules rules) {
        this.rules = rules;
        this.readAllow = compile(rules.readAllow());
        this.writeAllow = compile(rules.writeAllow());
        this.protectedPaths = compile(rules.protectedPaths());
    }

    public Rules rules() {
        return rules;
    }

    private static List<PathMatcher> compile(List<String> globs) {
        return globs.stream()
                .map(String::trim)
                .filter(g -> !g.isEmpty())
                .map(g -> FileSystems.getDefault().getPathMatcher("glob:" + g))
                .toList();
    }

    private static boolean matches(List<PathMatcher> matchers, String relative) {
        Path p = Path.of(relative);
        return matchers.stream().anyMatch(m -> m.matches(p));
    }

    public boolean isProtected(String relative) {
        return matches(protectedPaths, relative);
    }

    /** Decide whether a (normalised, workspace-relative) path may be read and sent towards the model. */
    public Decision decideRead(String relative) {
        if (isProtected(relative)) {
            return Decision.deny("read.protected-path", "'" + relative + "' is a protected path (secrets, VCS metadata or build wrapper)");
        }
        if (matches(readAllow, relative)) {
            return Decision.allow("read.permitted-source", "'" + relative + "' is permitted source");
        }
        return Decision.deny("read.not-permitted", "'" + relative + "' is outside the readable paths");
    }

    /** Proposing creates a reviewable diff only; it never touches the workspace. */
    public Decision decideProposeWrite(String relative) {
        if (isProtected(relative)) {
            return Decision.deny("write.protected-path", "'" + relative + "' is a protected path");
        }
        if (matches(writeAllow, relative)) {
            return Decision.allow("write.propose", "A diff may be proposed for '" + relative + "'; applying it requires approval");
        }
        return Decision.deny("write.not-permitted", "'" + relative + "' is outside the writable paths");
    }

    /**
     * Applying always needs a human approval. Callers turn ASK into ALLOW only after
     * {@link ApprovalStore#consume} succeeds for the exact patch and file versions.
     */
    public Decision decideApply(String relative) {
        Decision propose = decideProposeWrite(relative);
        if (!propose.allowed()) {
            return propose;
        }
        return Decision.ask("write.apply-needs-approval", "Applying a change to '" + relative + "' requires a human approval of this exact diff");
    }

    /** Only named tasks with fixed arguments exist. Anything else is denied, not pattern-matched. */
    public Decision decideTask(String taskName) {
        if (taskName != null && rules.tasks().contains(taskName)) {
            return Decision.allow("exec.named-task", "'" + taskName + "' is a named verification task with fixed arguments");
        }
        return Decision.deny("exec.not-a-named-task",
                "Only named verification tasks can run " + rules.tasks() + "; arbitrary commands are not supported");
    }
}
