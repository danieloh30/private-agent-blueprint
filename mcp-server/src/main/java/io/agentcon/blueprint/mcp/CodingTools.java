package io.agentcon.blueprint.mcp;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import io.agentcon.blueprint.guardrails.ApprovalStore;
import io.agentcon.blueprint.guardrails.Decision;
import io.agentcon.blueprint.guardrails.Hashing;
import io.agentcon.blueprint.guardrails.PolicyEngine;
import io.agentcon.blueprint.guardrails.PolicyViolation;
import io.agentcon.blueprint.guardrails.WorkspacePaths;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolGuardrails;
import io.quarkiverse.mcp.server.ToolResponse;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The agent's complete tool set. Seven focused tools, no shell, no network, no other directories.
 *
 * <p>Every tool:
 * <ol>
 *   <li>resolves the workspace from trusted server state (request-scoped; nothing from the model),</li>
 *   <li>asks the {@link PolicyEngine} for an allow / ask / deny decision,</li>
 *   <li>writes a redacted audit record,</li>
 *   <li>returns text whose first line states the decision.</li>
 * </ol>
 * Tool results then pass through {@link LeakageOutputGuardrail} before leaving the server.
 *
 * <p>Stateless with respect to the conversation: handlers keep no chat history. Proposals and
 * approvals live in {@link ApprovalStore}, which has its own lifecycle.
 */
public class CodingTools {

    private final WorkspaceManager workspaces;
    private final PolicyEngine policy;
    private final ApprovalStore approvals;
    private final SandboxRunner sandbox;
    private final AuditLog audit;
    private final ServerConfig config;

    public CodingTools(WorkspaceManager workspaces, PolicyEngine policy, ApprovalStore approvals,
                       SandboxRunner sandbox, AuditLog audit, ServerConfig config) {
        this.workspaces = workspaces;
        this.policy = policy;
        this.approvals = approvals;
        this.sandbox = sandbox;
        this.audit = audit;
        this.config = config;
    }

    // ---------------------------------------------------------------- read-only tools

    @ToolGuardrails(output = LeakageOutputGuardrail.class)
    @Tool(name = "list_files", description = "List files the agent is permitted to read, optionally under a directory "
            + "(relative to the repository root, e.g. 'src/main/java').",
            annotations = @Tool.Annotations(readOnlyHint = true, openWorldHint = false))
    ToolResponse listFiles(@ToolArg(description = "Relative directory; empty for the repository root", required = false) String directory) {
        return guarded("list_files", Map.of("directory", String.valueOf(directory)), () -> {
            WorkspaceManager.Run run = workspaces.current();
            WorkspacePaths paths = new WorkspacePaths(run.workspace());
            Path start = directory == null || directory.isBlank() || directory.equals(".")
                    ? paths.root() : paths.root().resolve(paths.normalize(directory));
            if (!Files.isDirectory(start, LinkOption.NOFOLLOW_LINKS) || !start.toRealPath().startsWith(paths.root())) {
                throw PolicyViolation.deny("path.not-found", "Not a directory inside the workspace: " + directory);
            }
            List<String> files = permittedFiles(paths, start);
            int max = config.limits().maxListedFiles();
            StringBuilder sb = new StringBuilder();
            files.stream().limit(max).forEach(f -> sb.append(f).append('\n'));
            if (files.size() > max) {
                sb.append("... ").append(files.size() - max).append(" more not shown\n");
            }
            Decision d = Decision.allow("read.list", files.size() + " permitted files (protected and unlisted paths omitted)");
            return new Outcome(d, sb.toString(), Map.of("count", files.size()));
        });
    }

    @ToolGuardrails(output = LeakageOutputGuardrail.class)
    @Tool(name = "search_text", description = "Search permitted files for a literal text (case-insensitive). "
            + "Returns up to 40 matching lines as path:line: text.",
            annotations = @Tool.Annotations(readOnlyHint = true, openWorldHint = false))
    ToolResponse searchText(@ToolArg(description = "Literal text to search for") String query,
                            @ToolArg(description = "Relative directory to search; empty for everything", required = false) String directory) {
        return guarded("search_text", Map.of("query", String.valueOf(query), "directory", String.valueOf(directory)), () -> {
            if (query == null || query.isBlank() || query.length() > config.limits().maxQueryLength()) {
                throw PolicyViolation.deny("search.invalid-query", "Query must be 1-" + config.limits().maxQueryLength() + " characters");
            }
            WorkspacePaths paths = new WorkspacePaths(workspaces.current().workspace());
            Path start = directory == null || directory.isBlank() ? paths.root() : paths.root().resolve(paths.normalize(directory));
            if (!Files.isDirectory(start, LinkOption.NOFOLLOW_LINKS)) {
                throw PolicyViolation.deny("path.not-found", "Not a directory inside the workspace: " + directory);
            }
            String needle = query.toLowerCase();
            List<String> hits = new ArrayList<>();
            int max = config.limits().maxSearchHits();
            for (String rel : permittedFiles(paths, start)) {
                Path file = paths.root().resolve(rel);
                if (Files.size(file) > config.limits().maxReadBytes()) continue;
                List<String> lines;
                try {
                    lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                } catch (CharacterCodingException e) {
                    continue; // binary
                }
                for (int i = 0; i < lines.size() && hits.size() < max; i++) {
                    if (lines.get(i).toLowerCase().contains(needle)) {
                        String line = lines.get(i).strip();
                        hits.add(rel + ":" + (i + 1) + ": " + (line.length() > 200 ? line.substring(0, 200) + "..." : line));
                    }
                }
                if (hits.size() >= max) break;
            }
            Decision d = Decision.allow("read.search", hits.size() + " matching lines in permitted files");
            return new Outcome(d, hits.isEmpty() ? "No matches." : String.join("\n", hits), Map.of("hits", hits.size()));
        });
    }

    @ToolGuardrails(output = LeakageOutputGuardrail.class)
    @Tool(name = "read_file", description = "Read one permitted text file (max 64 KB). Path is relative to the repository root.",
            annotations = @Tool.Annotations(readOnlyHint = true, openWorldHint = false))
    ToolResponse readFile(@ToolArg(description = "Relative file path, e.g. src/main/java/io/agentcon/games/rps/RpsResource.java") String path) {
        return guarded("read_file", Map.of("path", String.valueOf(path)), () -> {
            WorkspacePaths paths = new WorkspacePaths(workspaces.current().workspace());
            String rel = paths.normalize(path);
            Decision d = policy.decideRead(rel);
            if (!d.allowed()) {
                throw new PolicyViolation(d);
            }
            Path file = paths.resolveExistingFile(rel);
            long size = Files.size(file);
            if (size > config.limits().maxReadBytes()) {
                throw PolicyViolation.deny("read.too-large", rel + " is " + size + " bytes; limit is " + config.limits().maxReadBytes());
            }
            byte[] bytes = Files.readAllBytes(file);
            String content;
            try {
                content = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            } catch (CharacterCodingException e) {
                throw PolicyViolation.deny("read.binary", rel + " is not UTF-8 text");
            }
            return new Outcome(d, "FILE " + rel + " (" + size + " bytes, sha256 " + Hashing.shortHash(Hashing.sha256(bytes)) + ")\n"
                    + content, Map.of("path", rel, "bytes", size, "sha256", Hashing.sha256(bytes)));
        });
    }

    @ToolGuardrails(output = LeakageOutputGuardrail.class)
    @Tool(name = "show_diff", description = "Show the unified diff of all changes applied to the workspace so far.",
            annotations = @Tool.Annotations(readOnlyHint = true, openWorldHint = false))
    ToolResponse showDiff() {
        return guarded("show_diff", Map.of(), () -> {
            String diff = workspaceDiff();
            Decision d = Decision.allow("read.diff", diff.isBlank() ? "No changes applied yet" : "Diff of applied changes");
            return new Outcome(d, diff.isBlank() ? "No changes." : diff, Map.of("chars", diff.length()));
        });
    }

    // ---------------------------------------------------------------- write tools

    @ToolGuardrails(output = LeakageOutputGuardrail.class)
    @Tool(name = "propose_patch", description = "Propose a change to ONE file by replacing an exact text snippet. "
            + "'find' must occur exactly once in the file (copy it verbatim from read_file, keep it short). "
            + "Returns a reviewable diff and a proposal id. Nothing is written until a human approves.",
            annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false))
    ToolResponse proposePatch(@ToolArg(description = "Relative file path") String path,
                              @ToolArg(description = "Exact existing text to replace (must be unique in the file)") String find,
                              @ToolArg(description = "Replacement text") String replace) {
        return guarded("propose_patch", Map.of("path", String.valueOf(path)), () -> {
            WorkspaceManager.Run run = workspaces.current();
            WorkspacePaths paths = new WorkspacePaths(run.workspace());
            String rel = paths.normalize(path);
            Decision d = policy.decideProposeWrite(rel);
            if (!d.allowed()) {
                throw new PolicyViolation(d);
            }
            if (find == null || find.isEmpty()) {
                throw PolicyViolation.deny("patch.invalid", "'find' must contain the exact text to replace");
            }
            Path file = paths.resolveExistingFile(rel);
            String original = Files.readString(file, StandardCharsets.UTF_8);
            String effectiveFind = find;
            int first = original.indexOf(effectiveFind);
            if (first < 0) {
                // Tolerate a common small-model mistake: surrounding whitespace / CRLF differences.
                effectiveFind = find.strip().replace("\r\n", "\n");
                first = effectiveFind.isEmpty() ? -1 : original.indexOf(effectiveFind);
            }
            if (first < 0) {
                // Then line-by-line, ignoring indentation; the matched ORIGINAL lines are what gets replaced.
                String located = locateIgnoringIndentation(original, find);
                if (located != null) {
                    effectiveFind = located;
                    first = original.indexOf(located);
                }
            }
            if (first < 0) {
                throw PolicyViolation.deny("patch.find-not-found", "'find' text does not occur in " + rel + "; read_file again and copy it exactly");
            }
            if (original.indexOf(effectiveFind, first + 1) >= 0) {
                throw PolicyViolation.deny("patch.find-ambiguous", "'find' text occurs more than once in " + rel + "; include more context");
            }
            String updated = original.substring(0, first) + (replace == null ? "" : replace)
                    + original.substring(first + effectiveFind.length());
            if (updated.equals(original)) {
                throw PolicyViolation.deny("patch.no-op", "The replacement does not change the file");
            }
            if (updated.length() > config.limits().maxReadBytes()) {
                throw PolicyViolation.deny("patch.too-large", "Resulting file exceeds the size limit");
            }
            String diff = unifiedDiff(rel, original, updated);
            ApprovalStore.Proposal p = approvals.propose(run.id(), rel, Hashing.sha256(original), updated, diff);
            Decision ask = Decision.ask("write.apply-needs-approval",
                    "Proposal " + p.id() + " created. A human must approve this exact diff before it can be applied");
            String text = "PROPOSAL " + p.id() + " for " + rel + " (status: waiting for human approval)\n" + diff;
            return new Outcome(ask, text, Map.of("path", rel, "proposal", p.id(), "patchSha256", p.patchSha256(),
                    "baseSha256", p.baseSha256()));
        });
    }

    @ToolGuardrails(output = LeakageOutputGuardrail.class)
    @Tool(name = "apply_patch", description = "Apply a proposal AFTER a human approved it in the presenter UI. "
            + "Fails if it is not approved, expired, already applied, or the file changed.",
            annotations = @Tool.Annotations(readOnlyHint = false, destructiveHint = true, idempotentHint = false, openWorldHint = false))
    ToolResponse applyPatch(@ToolArg(description = "Proposal id returned by propose_patch, e.g. p-1a2b3c4d") String proposalId) {
        return guarded("apply_patch", Map.of("proposalId", String.valueOf(proposalId)), () -> {
            WorkspaceManager.Run run = workspaces.current();
            ApprovalStore.Proposal p = approvals.get(String.valueOf(proposalId).trim());
            // Re-check authorization at execution time; policy or workspace may have changed since proposal.
            Decision base = policy.decideApply(p.path());
            if (base.verdict() == Decision.Verdict.DENY) {
                throw new PolicyViolation(base);
            }
            WorkspacePaths paths = new WorkspacePaths(run.workspace());
            Path file = paths.resolveWriteTarget(paths.normalize(p.path()));
            String currentSha = Files.exists(file, LinkOption.NOFOLLOW_LINKS) ? Hashing.sha256(Files.readAllBytes(file)) : null;
            ApprovalStore.Proposal consumed = approvals.consumeForApply(p.id(), run.id(), currentSha);
            try {
                writeAtomically(file, consumed.newContent());
            } catch (IOException e) {
                approvals.markFailed(p.id());
                throw e;
            }
            Decision d = Decision.allow("write.approved", "Applied proposal " + p.id() + " approved by " + consumed.approvedBy());
            return new Outcome(d, "APPLIED " + p.id() + " to " + p.path() + "\n" + consumed.diff(),
                    Map.of("path", p.path(), "proposal", p.id(), "patchSha256", p.patchSha256()));
        });
    }

    // ---------------------------------------------------------------- execution

    @ToolGuardrails(output = LeakageOutputGuardrail.class)
    @Tool(name = "run_verification", description = "Run a NAMED verification task in a restricted sandbox. "
            + "Available tasks: unit-tests, acceptance-rps, acceptance-scramble. Arbitrary commands are not supported.",
            annotations = @Tool.Annotations(readOnlyHint = true, openWorldHint = false))
    ToolResponse runVerification(@ToolArg(description = "Task name, e.g. unit-tests") String task) {
        return guarded("run_verification", Map.of("task", String.valueOf(task)), () -> {
            Decision d = policy.decideTask(task);
            if (!d.allowed()) {
                throw new PolicyViolation(d);
            }
            SandboxRunner.Result r = sandbox.run(task, workspaces.current());
            String text = "VERIFICATION " + task + ": " + r.headline() + "\n" + r.outputTail();
            Decision reported = r.status() == SandboxRunner.Status.ISOLATION_UNAVAILABLE
                    ? Decision.deny("exec.isolation-unavailable", "Restricted execution environment unavailable; task not run")
                    : d;
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("task", task);
            details.put("status", r.status().name());
            details.put("tests", r.testsRun());
            details.put("failures", r.failures() + r.errors());
            details.put("durationMs", r.durationMs());
            return new Outcome(reported, text, details);
        });
    }

    // ---------------------------------------------------------------- helpers

    record Outcome(Decision decision, String body, Map<String, ?> details) {
    }

    @FunctionalInterface
    interface Action {
        Outcome run() throws Exception;
    }

    /** Uniform decision reporting and auditing for every tool. */
    private ToolResponse guarded(String tool, Map<String, String> args, Action action) {
        String actor = config.security().agentIdentity();
        try (var ignored = workspaces.acquire()) {
            Outcome o = action.run();
            Map<String, Object> details = new LinkedHashMap<>(o.details());
            details.put("args", args.toString());
            audit.record(actor, tool, o.decision(), details);
            String text = header(o.decision()) + "\n" + truncate(o.body());
            return ToolResponse.success(text);
        } catch (PolicyViolation v) {
            audit.record(actor, tool, v.decision(), Map.of("args", args.toString()));
            return ToolResponse.error(header(v.decision()));
        } catch (Exception e) {
            Decision d = Decision.deny("tool.error", e.getClass().getSimpleName() + ": " + e.getMessage());
            audit.record(actor, tool, d, Map.of("args", args.toString()));
            return ToolResponse.error(header(d));
        }
    }

    static String header(Decision d) {
        return "[policy] " + d.verdict() + " " + d.rule() + ": " + d.reason();
    }

    private String truncate(String body) {
        int max = config.limits().maxOutputChars();
        return body.length() <= max ? body : body.substring(0, max) + "\n... [truncated at " + max + " characters]";
    }

    private List<String> permittedFiles(WorkspacePaths paths, Path start) throws IOException {
        try (Stream<Path> walk = Files.walk(start)) {
            return walk.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                    .map(paths::relativize)
                    .filter(rel -> policy.decideRead(rel).allowed())
                    .sorted()
                    .toList();
        }
    }


    /**
     * Finds a unique run of original lines that equals {@code find} line by line after stripping each line.
     * Returns the original text of those lines, or null if there is no match or more than one.
     */
    static String locateIgnoringIndentation(String original, String find) {
        List<String> needle = find.strip().lines().map(String::strip).toList();
        if (needle.isEmpty() || needle.stream().allMatch(String::isEmpty)) {
            return null;
        }
        List<String> lines = original.lines().toList();
        int found = -1;
        for (int i = 0; i + needle.size() <= lines.size(); i++) {
            boolean match = true;
            for (int j = 0; j < needle.size() && match; j++) {
                match = lines.get(i + j).strip().equals(needle.get(j));
            }
            if (match) {
                if (found >= 0) {
                    return null; // ambiguous
                }
                found = i;
            }
        }
        if (found < 0) {
            return null;
        }
        String block = String.join("\n", lines.subList(found, found + needle.size()));
        // When the needle starts mid-line, replace from the first non-blank character to keep indentation.
        String firstLine = lines.get(found);
        int indent = firstLine.length() - firstLine.stripLeading().length();
        return block.substring(indent);
    }

    static String unifiedDiff(String rel, String original, String updated) {
        List<String> a = original.lines().toList();
        List<String> b = updated.lines().toList();
        List<String> lines = UnifiedDiffUtils.generateUnifiedDiff("a/" + rel, "b/" + rel, a, DiffUtils.diff(a, b), 3);
        return String.join("\n", lines) + "\n";
    }

    String workspaceDiff() throws IOException {
        WorkspaceManager.Run run = workspaces.current();
        StringBuilder sb = new StringBuilder();
        for (String rel : changedFiles()) {
            Path base = run.baseline().resolve(rel);
            String now = Files.readString(run.workspace().resolve(rel), StandardCharsets.UTF_8);
            String before = Files.exists(base) ? Files.readString(base, StandardCharsets.UTF_8) : "";
            sb.append(unifiedDiff(rel, before, now));
        }
        return sb.toString();
    }

    /** Workspace files (that policy lets the agent read) whose content differs from the run's baseline. */
    List<String> changedFiles() throws IOException {
        WorkspaceManager.Run run = workspaces.current();
        WorkspacePaths ws = new WorkspacePaths(run.workspace());
        List<String> changed = new java.util.ArrayList<>();
        for (String rel : permittedFiles(ws, ws.root())) {
            Path base = run.baseline().resolve(rel);
            String now = Files.readString(ws.root().resolve(rel), StandardCharsets.UTF_8);
            String before = Files.exists(base) ? Files.readString(base, StandardCharsets.UTF_8) : "";
            if (!now.equals(before)) {
                changed.add(rel);
            }
        }
        return changed;
    }

    private static void writeAtomically(Path file, String content) throws IOException {
        Path tmp = file.resolveSibling("." + file.getFileName() + ".agent-tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
