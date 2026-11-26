package io.agentcon.blueprint.mcp;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Trusted server-side configuration. Nothing here can be influenced by the model:
 * the workspace location, policy rules, limits and tokens come from config files,
 * environment variables or the start scripts.
 */
@ConfigMapping(prefix = "blueprint")
public interface ServerConfig {

    /** Directory holding disposable runs, audit logs and verification scratch copies. */
    Path stateDir();

    /** Pristine coding-target fixture that each run copies. Never written to. */
    Path fixtureDir();

    /** Presenter-owned acceptance tests. Outside every agent-writable location. */
    Path acceptanceDir();

    Security security();

    Policy policy();

    Limits limits();

    Leakage leakage();

    Approval approval();

    Sandbox sandbox();

    interface Security {
        /** Bearer token the agent process presents on /mcp. Supplied via env, never model-visible. */
        String agentToken();

        /** Token for /admin (approvals, acceptance). Held by the presenter UI backend only. */
        String approverToken();

        /** Identity bound to the agent token; recorded in the audit log. */
        @WithDefault("demo-agent")
        String agentIdentity();
    }

    interface Policy {
        List<String> readAllow();

        List<String> writeAllow();

        List<String> protectedPaths();
    }

    interface Limits {
        @WithDefault("65536")
        int maxReadBytes();

        @WithDefault("12000")
        int maxOutputChars();

        @WithDefault("40")
        int maxSearchHits();

        @WithDefault("200")
        int maxListedFiles();

        @WithDefault("200")
        int maxQueryLength();
    }

    interface Leakage {
        /** REDACT replaces matches; BLOCK withholds the whole tool result. */
        @WithDefault("REDACT")
        String mode();

        List<String> canaries();

        @WithDefault("AGENT-RESTRICTED")
        String restrictedMarker();
    }

    interface Approval {
        @WithDefault("15m")
        Duration proposalTtl();

        @WithDefault("5m")
        Duration approvalTtl();
    }

    interface Sandbox {
        /** Container engine CLI. Only engines that provide real isolation are accepted. */
        @WithDefault("podman")
        String engine();

        String image();

        /** Prepared Maven repository (filled by scripts/prepare-deps.sh), mounted read-only. */
        Path m2Dir();

        @WithDefault("2g")
        String memory();

        @WithDefault("2")
        String cpus();

        @WithDefault("512")
        int pidsLimit();

        @WithDefault("240s")
        Duration timeout();

        @WithDefault("6000")
        int maxOutputChars();

        /** Optional explicit path to the engine binary (otherwise resolved from PATH). */
        Optional<String> enginePath();
    }
}
