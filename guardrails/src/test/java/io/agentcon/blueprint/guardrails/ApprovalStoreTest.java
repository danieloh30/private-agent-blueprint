package io.agentcon.blueprint.guardrails;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.agentcon.blueprint.guardrails.Decision.Verdict;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApprovalStoreTest {

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2027-02-03T10:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    MutableClock clock;
    ApprovalStore store;
    String base;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        store = new ApprovalStore(clock, Duration.ofMinutes(15), Duration.ofMinutes(5));
        base = Hashing.sha256("old content");
    }

    ApprovalStore.Proposal propose() {
        return store.propose("run-1", "src/A.java", base, "new content", "--- a\n+++ b\n");
    }

    @Test
    void applyWithoutApprovalAsks() {
        var p = propose();
        PolicyViolation v = assertThrows(PolicyViolation.class, () -> store.consumeForApply(p.id(), "run-1", base));
        assertEquals(Verdict.ASK, v.decision().verdict());
    }

    @Test
    void approvedPatchAppliesExactlyOnce() {
        var p = propose();
        store.approve(p.id(), p.patchSha256(), "presenter");
        assertEquals(ApprovalStore.State.APPLIED, store.consumeForApply(p.id(), "run-1", base).state());
        PolicyViolation replay = assertThrows(PolicyViolation.class, () -> store.consumeForApply(p.id(), "run-1", base));
        assertEquals("approval.replay", replay.decision().rule());
    }

    @Test
    void approvalOfDifferentContentIsRefused() {
        var p = propose();
        PolicyViolation v = assertThrows(PolicyViolation.class,
                () -> store.approve(p.id(), Hashing.sha256("something else"), "presenter"));
        assertEquals("approval.hash-mismatch", v.decision().rule());
    }

    @Test
    void changedTargetFileInvalidatesApproval() {
        var p = propose();
        store.approve(p.id(), p.patchSha256(), "presenter");
        PolicyViolation v = assertThrows(PolicyViolation.class,
                () -> store.consumeForApply(p.id(), "run-1", Hashing.sha256("edited meanwhile")));
        assertEquals("approval.file-changed", v.decision().rule());
    }

    @Test
    void approvalExpires() {
        var p = propose();
        store.approve(p.id(), p.patchSha256(), "presenter");
        clock.now = clock.now.plus(Duration.ofMinutes(6));
        PolicyViolation v = assertThrows(PolicyViolation.class, () -> store.consumeForApply(p.id(), "run-1", base));
        assertEquals("approval.expired", v.decision().rule());
    }

    @Test
    void approvalIsBoundToWorkspace() {
        var p = propose();
        store.approve(p.id(), p.patchSha256(), "presenter");
        PolicyViolation v = assertThrows(PolicyViolation.class, () -> store.consumeForApply(p.id(), "run-2", base));
        assertEquals("approval.wrong-workspace", v.decision().rule());
    }

    @Test
    void rejectedProposalCannotBeApplied() {
        var p = propose();
        store.reject(p.id(), "presenter");
        assertThrows(PolicyViolation.class, () -> store.approve(p.id(), p.patchSha256(), "presenter"));
        assertThrows(PolicyViolation.class, () -> store.consumeForApply(p.id(), "run-1", base));
    }
}
