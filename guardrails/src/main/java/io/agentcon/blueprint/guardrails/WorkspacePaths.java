package io.agentcon.blueprint.guardrails;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * Resolves model-supplied relative paths inside the disposable workspace.
 *
 * <p>Rejects: absolute paths, drive letters, {@code ..} escapes, NUL bytes, backslashes,
 * symlinks anywhere on the path, and targets whose real location is outside the workspace.
 * The workspace root itself comes from trusted server configuration, never from the model.
 */
public final class WorkspacePaths {

    private final Path root;

    public WorkspacePaths(Path root) {
        try {
            this.root = root.toRealPath();
        } catch (IOException e) {
            throw new IllegalStateException("Workspace root does not exist: " + root, e);
        }
        if (!Files.isDirectory(this.root)) {
            throw new IllegalStateException("Workspace root is not a directory: " + root);
        }
    }

    public Path root() {
        return root;
    }

    /** Normalises and validates a relative path string. Returns the canonical relative form ({@code a/b/C.java}). */
    public String normalize(String requested) {
        if (requested == null || requested.isBlank()) {
            throw PolicyViolation.deny("path.empty", "A relative path is required");
        }
        String p = requested.trim();
        if (p.indexOf('\0') >= 0) {
            throw PolicyViolation.deny("path.invalid", "Path contains a NUL byte");
        }
        if (p.contains("\\")) {
            throw PolicyViolation.deny("path.invalid", "Backslashes are not accepted in paths");
        }
        if (p.startsWith("/") || p.startsWith("~") || p.matches("^[A-Za-z]:.*")) {
            throw PolicyViolation.deny("path.absolute", "Absolute paths are not allowed; use a path relative to the workspace");
        }
        if (p.startsWith("./")) {
            p = p.substring(2);
        }
        Path rel;
        try {
            rel = Path.of(p).normalize();
        } catch (InvalidPathException e) {
            throw PolicyViolation.deny("path.invalid", "Invalid path");
        }
        if (rel.isAbsolute() || rel.startsWith("..") || rel.toString().isEmpty()) {
            throw PolicyViolation.deny("path.traversal", "Path escapes the workspace");
        }
        for (Path part : rel) {
            if (part.toString().equals("..")) {
                throw PolicyViolation.deny("path.traversal", "Path escapes the workspace");
            }
        }
        return rel.toString().replace('\\', '/');
    }

    /** Resolves an existing regular file for reading. */
    public Path resolveExistingFile(String relative) {
        Path target = root.resolve(relative);
        rejectSymlinks(relative);
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw PolicyViolation.deny("path.not-found", "File does not exist: " + relative);
        }
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw PolicyViolation.deny("path.not-regular", "Not a regular file: " + relative);
        }
        ensureInside(target);
        return target;
    }

    /**
     * Resolves a write target. The parent directory must already exist inside the workspace;
     * the file may be new. Symlinked targets or parents are rejected.
     */
    public Path resolveWriteTarget(String relative) {
        Path target = root.resolve(relative);
        rejectSymlinks(relative);
        Path parent = target.getParent();
        if (parent == null || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            throw PolicyViolation.deny("path.parent-missing", "Parent directory must already exist: " + relative);
        }
        ensureInside(parent);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw PolicyViolation.deny("path.not-regular", "Not a regular file: " + relative);
        }
        return target;
    }

    private void rejectSymlinks(String relative) {
        Path current = root;
        for (Path part : Path.of(relative)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) {
                throw PolicyViolation.deny("path.symlink", "Symbolic links are not followed: " + relative);
            }
        }
    }

    private void ensureInside(Path existing) {
        try {
            Path real = existing.toRealPath();
            if (!real.startsWith(root)) {
                throw PolicyViolation.deny("path.escape", "Resolved path is outside the workspace");
            }
        } catch (IOException e) {
            throw PolicyViolation.deny("path.invalid", "Cannot resolve path");
        }
    }

    /** Returns the workspace-relative form of an absolute path inside the workspace. */
    public String relativize(Path absolute) {
        return root.relativize(absolute).toString().replace('\\', '/');
    }
}
