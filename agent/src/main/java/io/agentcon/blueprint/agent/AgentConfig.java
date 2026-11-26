package io.agentcon.blueprint.agent;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/** Agent-side configuration. The model endpoint and model id have NO defaults outside named profiles. */
@ConfigMapping(prefix = "blueprint")
public interface AgentConfig {

    Model model();

    Mcp mcp();

    Limits limits();

    Leakage leakage();

    /** Explicit tool allowlist. Tools the MCP server offers but that are not listed here are never shown to the model. */
    List<String> allowedTools();

    interface Model {
        /** local | enterprise | scripted. Required. */
        String profile();

        /** OpenAI-compatible base URL. Required for local and enterprise. */
        Optional<String> baseUrl();

        /** Model identifier as the endpoint names it. Required for local and enterprise. */
        Optional<String> name();

        /** Read from the environment only (BLUEPRINT_MODEL_API_KEY). Never logged, never model-visible. */
        Optional<String> apiKey();

        /** Enterprise profile: exact host names that are approved inference destinations. */
        Optional<List<String>> approvedHosts();

        @WithDefault("120s")
        Duration timeout();

        @WithDefault("0.0")
        double temperature();

        /** Prepended to the system prompt, e.g. "/no_think" for Qwen3 to disable thinking output. */
        Optional<String> systemPrefix();

        /** Use the JVM proxy settings for the model connection (enterprise gateways behind a proxy). */
        @WithDefault("false")
        boolean useSystemProxy();
    }

    interface Mcp {
        /** Streamable HTTP endpoint of the Java MCP server. */
        @WithDefault("http://127.0.0.1:8091/mcp")
        String url();

        String agentToken();

        String approverToken();

        @WithDefault("300s")
        Duration toolTimeout();
    }

    interface Limits {
        /** Maximum model calls per user request. */
        @WithDefault("10")
        int maxSteps();

        /** Maximum tool executions per user request. */
        @WithDefault("12")
        int maxToolCalls();

        /** Tool output delivered to the model is truncated to this many characters. */
        @WithDefault("8000")
        int maxToolOutputChars();

        /** Wall-clock budget per user request. */
        @WithDefault("240s")
        Duration maxRunTime();

        @WithDefault("4000")
        int maxInputChars();

        @WithDefault("4000")
        int maxAnswerChars();

        /** Conversation messages kept per session (oldest tool exchanges dropped first). */
        @WithDefault("40")
        int maxHistoryMessages();
    }

    interface Leakage {
        /** REDACT or BLOCK, applied to user-provided context before it reaches the model. */
        @WithDefault("REDACT")
        String mode();

        List<String> canaries();

        @WithDefault("AGENT-RESTRICTED")
        String restrictedMarker();
    }
}
