package io.agentcon.blueprint.mcp;

import io.agentcon.blueprint.guardrails.WorkspacePaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Copies approved, verified changes from a disposable workspace back into the developer's tree.
 *
 * <p>This is the presenter's promote step, the same rules as {@code scripts/promote-workspace.sh}:
 * a file is copied only if the target still matches the snapshot the run started from (or both are new);
 * a file the developer changed since then is never overwritten; a file already identical is skipped.
 * Plain Java with no framework, so it is tested against temporary directories.
 */
public final class Promotion {

    public enum Status { PROMOTE, ALREADY_PROMOTED, CONFLICT }

    public record Item(String path, Status status) {
    }

    private Promotion() {
    }

    /** Decides, per changed workspace file, whether it can be copied into {@code target}. */
    public static List<Item> plan(Path workspace, Path baseline, Path target, List<String> changed) throws IOException {
        List<Item> items = new ArrayList<>();
        for (String rel : changed) {
            Path ws = workspace.resolve(rel);
            Path base = baseline.resolve(rel);
            Path dest = target.resolve(rel);
            boolean destExists = Files.exists(dest, LinkOption.NOFOLLOW_LINKS);
            boolean baseExists = Files.exists(base, LinkOption.NOFOLLOW_LINKS);
            Status status;
            if (destExists && same(dest, ws)) {
                status = Status.ALREADY_PROMOTED;
            } else if ((!destExists && !baseExists) || (destExists && baseExists && same(dest, base))) {
                status = Status.PROMOTE;
            } else {
                status = Status.CONFLICT;
            }
            items.add(new Item(rel, status));
        }
        return items;
    }

    /**
     * Copies the PROMOTE items. Paths are resolved through {@link WorkspacePaths} rooted at the target,
     * so traversal and symlinks are rejected there too. Returns the copied paths.
     */
    public static List<String> apply(Path workspace, Path target, List<Item> plan) throws IOException {
        WorkspacePaths dest = new WorkspacePaths(target);
        List<String> copied = new ArrayList<>();
        for (Item item : plan) {
            if (item.status() != Status.PROMOTE) {
                continue;
            }
            String rel = dest.normalize(item.path());
            Path out = dest.resolveWriteTarget(rel);
            Files.copy(workspace.resolve(rel), out, StandardCopyOption.REPLACE_EXISTING);
            copied.add(rel);
        }
        return copied;
    }

    private static boolean same(Path a, Path b) throws IOException {
        return Files.isRegularFile(a, LinkOption.NOFOLLOW_LINKS) && Files.isRegularFile(b, LinkOption.NOFOLLOW_LINKS)
                && Files.mismatch(a, b) == -1;
    }
}
