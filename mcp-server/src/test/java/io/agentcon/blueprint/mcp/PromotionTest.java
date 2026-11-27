package io.agentcon.blueprint.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.agentcon.blueprint.guardrails.PolicyViolation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The promote rules, on temporary directories: copy what is safe, skip what is done, never clobber local edits. */
class PromotionTest {

    @TempDir
    Path tmp;
    Path workspace;
    Path baseline;
    Path target;

    @BeforeEach
    void setUp() throws Exception {
        workspace = Files.createDirectories(tmp.resolve("workspace/src"));
        baseline = Files.createDirectories(tmp.resolve("baseline/src"));
        target = Files.createDirectories(tmp.resolve("arcade/src"));
        workspace = workspace.getParent();
        baseline = baseline.getParent();
        target = target.getParent();
    }

    private void write(Path root, String rel, String text) throws Exception {
        Files.writeString(root.resolve(rel), text);
    }

    @Test
    void copiesAFileTheDeveloperHasNotTouched() throws Exception {
        write(baseline, "src/A.java", "old");
        write(target, "src/A.java", "old");
        write(workspace, "src/A.java", "fixed");

        List<Promotion.Item> plan = Promotion.plan(workspace, baseline, target, List.of("src/A.java"));
        assertEquals(Promotion.Status.PROMOTE, plan.get(0).status());
        assertEquals(List.of("src/A.java"), Promotion.apply(workspace, target, plan));
        assertEquals("fixed", Files.readString(target.resolve("src/A.java")));
    }

    @Test
    void neverOverwritesAFileChangedLocallySinceTheRunStarted() throws Exception {
        write(baseline, "src/Quiz.java", "old");
        write(target, "src/Quiz.java", "OpenCode's fix");
        write(workspace, "src/Quiz.java", "agent's fix");

        List<Promotion.Item> plan = Promotion.plan(workspace, baseline, target, List.of("src/Quiz.java"));
        assertEquals(Promotion.Status.CONFLICT, plan.get(0).status());
        assertEquals(List.of(), Promotion.apply(workspace, target, plan));
        assertEquals("OpenCode's fix", Files.readString(target.resolve("src/Quiz.java")));
    }

    @Test
    void skipsAFileThatIsAlreadyPromoted() throws Exception {
        write(baseline, "src/A.java", "old");
        write(target, "src/A.java", "fixed");
        write(workspace, "src/A.java", "fixed");

        List<Promotion.Item> plan = Promotion.plan(workspace, baseline, target, List.of("src/A.java"));
        assertEquals(Promotion.Status.ALREADY_PROMOTED, plan.get(0).status());
        assertEquals(List.of(), Promotion.apply(workspace, target, plan));
    }

    @Test
    void copiesANewFileOnlyIfItIsNewOnBothSides() throws Exception {
        write(workspace, "src/New.java", "new");
        assertEquals(Promotion.Status.PROMOTE,
                Promotion.plan(workspace, baseline, target, List.of("src/New.java")).get(0).status());

        write(target, "src/New.java", "someone else's file");
        assertEquals(Promotion.Status.CONFLICT,
                Promotion.plan(workspace, baseline, target, List.of("src/New.java")).get(0).status());
    }

    @Test
    void refusesToFollowASymlinkOutOfTheTarget() throws Exception {
        Path outside = Files.createDirectories(tmp.resolve("outside"));
        Files.createSymbolicLink(target.resolve("link"), outside);
        Files.createDirectories(workspace.resolve("link"));
        write(workspace, "link/Evil.java", "x");

        List<Promotion.Item> plan = List.of(new Promotion.Item("link/Evil.java", Promotion.Status.PROMOTE));
        assertThrows(PolicyViolation.class, () -> Promotion.apply(workspace, target, plan));
        assertEquals(false, Files.exists(outside.resolve("Evil.java")));
    }

    @Test
    void refusesTraversal() throws Exception {
        List<Promotion.Item> plan = List.of(new Promotion.Item("../escape.txt", Promotion.Status.PROMOTE));
        assertThrows(PolicyViolation.class, () -> Promotion.apply(workspace, target, plan));
    }
}
