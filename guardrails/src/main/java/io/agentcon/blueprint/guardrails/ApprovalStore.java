package io.agentcon.blueprint.guardrails;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Patch proposals and the human approvals bound to them.
 *
 * <p>An approval is bound to: the action ({@code apply_patch}), the workspace id, the target path,
 * the SHA-256 of the patch content the human saw, and the SHA-256 of the target file version the
 * patch was computed against. Approvals expire and are single use (no replay).
 *
 * <p>This store has its own lifecycle (in memory, per MCP server process). It holds no conversation
 * history; tool handlers stay stateless with respect to the model conversation.
 */
public final class ApprovalStore {

    public static final String ACTION = "apply_patch";

    public enum State { PENDING, APPROVED, APPLIED, REJECTED, FAILED }

    public record Proposal(String id,
                           String action,
                           String workspaceId,
                           String path,
                           String baseSha256,
                           String newContent,
                           String diff,
                           String patchSha256,
                           Instant createdAt,
                           Instant expiresAt,
                           State state,
                           String approvedBy,
                           Instant approvedAt) {

        Proposal with(State newState, String by, Instant at, Instant newExpiry) {
            return new Proposal(id, action, workspaceId, path, baseSha256, newContent, diff, patchSha256,
                    createdAt, newExpiry, newState, by, at);
        }
    }

    /** Hash that covers everything the approval is bound to. */
    public static String patchHash(String workspaceId, String path, String baseSha256, String newContent) {
        return Hashing.sha256(ACTION + "\n" + workspaceId + "\n" + path + "\n"
                + Objects.requireNonNullElse(baseSha256, "<new-file>") + "\n" + newContent);
    }

    private final Map<String, Proposal> proposals = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration proposalTtl;
    private final Duration approvalTtl;

    public ApprovalStore(Clock clock, Duration proposalTtl, Duration approvalTtl) {
        this.clock = clock;
        this.proposalTtl = proposalTtl;
        this.approvalTtl = approvalTtl;
    }

    public Proposal propose(String workspaceId, String path, String baseSha256, String newContent, String diff) {
        Instant now = clock.instant();
        String id = "p-" + UUID.randomUUID().toString().substring(0, 8);
        Proposal p = new Proposal(id, ACTION, workspaceId, path, baseSha256, newContent, diff,
                patchHash(workspaceId, path, baseSha256, newContent), now, now.plus(proposalTtl),
                State.PENDING, null, null);
        proposals.put(id, p);
        return p;
    }

    public Proposal get(String id) {
        Proposal p = proposals.get(id);
        if (p == null) {
            throw PolicyViolation.deny("approval.unknown-proposal", "No proposal with id '" + id + "'");
        }
        return p;
    }

    public List<Proposal> list() {
        Collection<Proposal> all = proposals.values();
        return all.stream().sorted(Comparator.comparing(Proposal::createdAt)).toList();
    }

    /**
     * Records a human approval. The approver must present the patch hash they reviewed; a mismatch
     * means the human saw different content and the approval is refused.
     */
    public synchronized Proposal approve(String id, String presentedPatchSha256, String approver) {
        Proposal p = get(id);
        Instant now = clock.instant();
        if (p.state() != State.PENDING) {
            throw PolicyViolation.deny("approval.not-pending", "Proposal " + id + " is " + p.state());
        }
        if (now.isAfter(p.expiresAt())) {
            throw PolicyViolation.deny("approval.expired", "Proposal " + id + " expired before approval");
        }
        if (!p.patchSha256().equals(presentedPatchSha256)) {
            throw PolicyViolation.deny("approval.hash-mismatch", "The approved diff does not match the proposal content");
        }
        Proposal approved = p.with(State.APPROVED, approver, now, now.plus(approvalTtl));
        proposals.put(id, approved);
        return approved;
    }

    public synchronized Proposal reject(String id, String approver) {
        Proposal p = get(id);
        if (p.state() != State.PENDING && p.state() != State.APPROVED) {
            throw PolicyViolation.deny("approval.not-pending", "Proposal " + id + " is " + p.state());
        }
        Proposal rejected = p.with(State.REJECTED, approver, clock.instant(), p.expiresAt());
        proposals.put(id, rejected);
        return rejected;
    }

    /**
     * Atomically checks and consumes an approval right before the write. Revalidates workspace,
     * expiry, patch integrity and the current file version. Succeeds at most once per proposal.
     *
     * @param currentBaseSha256 SHA-256 of the target file as it is on disk now ({@code null} if absent)
     */
    public synchronized Proposal consumeForApply(String id, String workspaceId, String currentBaseSha256) {
        Proposal p = get(id);
        Instant now = clock.instant();
        switch (p.state()) {
            case PENDING -> throw new PolicyViolation(Decision.ask("write.apply-needs-approval",
                    "Proposal " + id + " is waiting for a human approval in the presenter UI"));
            case APPLIED -> throw PolicyViolation.deny("approval.replay", "Proposal " + id + " was already applied; approvals are single use");
            case REJECTED -> throw PolicyViolation.deny("approval.rejected", "Proposal " + id + " was rejected");
            case FAILED -> throw PolicyViolation.deny("approval.failed", "Proposal " + id + " previously failed to apply");
            case APPROVED -> {
            }
        }
        if (now.isAfter(p.expiresAt())) {
            throw PolicyViolation.deny("approval.expired", "Approval for " + id + " has expired");
        }
        if (!p.workspaceId().equals(workspaceId)) {
            throw PolicyViolation.deny("approval.wrong-workspace", "Approval was granted for a different workspace");
        }
        if (!p.patchSha256().equals(patchHash(p.workspaceId(), p.path(), p.baseSha256(), p.newContent()))) {
            throw PolicyViolation.deny("approval.patch-changed", "Patch content changed after approval");
        }
        if (!Objects.equals(p.baseSha256(), currentBaseSha256)) {
            throw PolicyViolation.deny("approval.file-changed", "'" + p.path() + "' changed since the diff was proposed; propose again");
        }
        Proposal applied = p.with(State.APPLIED, p.approvedBy(), p.approvedAt(), p.expiresAt());
        proposals.put(id, applied);
        return applied;
    }

    public synchronized void markFailed(String id) {
        Proposal p = get(id);
        proposals.put(id, p.with(State.FAILED, p.approvedBy(), p.approvedAt(), p.expiresAt()));
    }

    public void clear() {
        proposals.clear();
    }
}
