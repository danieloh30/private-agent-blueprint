package io.agentcon.blueprint.agent.core;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import io.agentcon.blueprint.agent.AgentConfig;
import io.agentcon.blueprint.agent.tools.ToolGateway;
import io.agentcon.blueprint.guardrails.LeakageInspector;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Test doubles for ORCHESTRATION tests only.
 * FakeModel: deterministic, scripted, performs no inference.
 * StubTools: in-memory tool responses, NOT MCP (real MCP is covered by mcp-server tests and AgentEndToEndIT).
 */
final class TestSupport {

    static final Set<String> TOOLS = Set.of("list_files", "search_text", "read_file", "show_diff", "propose_patch",
            "apply_patch", "run_verification");

    record Limits(int maxSteps, int maxToolCalls, int maxToolOutputChars, Duration maxRunTime) implements AgentConfig.Limits {
        static Limits defaults() {
            return new Limits(10, 12, 8000, Duration.ofSeconds(240));
        }

        public int maxInputChars() { return 4000; }
        public int maxAnswerChars() { return 4000; }
        public int maxHistoryMessages() { return 40; }
    }

    /** Deterministic fake model: each call returns the next scripted message and records the request. */
    static final class FakeModel implements ChatModel {
        final List<ChatRequest> requests = new ArrayList<>();
        final Function<Integer, AiMessage> script;

        FakeModel(Function<Integer, AiMessage> script) {
            this.script = script;
        }

        @Override
        public ChatResponse doChat(ChatRequest request) {
            requests.add(request);
            return ChatResponse.builder().aiMessage(script.apply(requests.size())).build();
        }

        String allRequestText() {
            StringBuilder sb = new StringBuilder();
            requests.forEach(r -> r.messages().forEach(m -> sb.append(m.toString()).append('\n')));
            return sb.toString();
        }
    }

    static AiMessage call(String tool, String json) {
        return AiMessage.from(ToolExecutionRequest.builder().id("c" + System.nanoTime()).name(tool).arguments(json).build());
    }

    /** In-memory stub, not MCP. */
    static final class StubTools implements ToolGateway {
        final List<String> executed = new ArrayList<>();
        final Function<String, String> responder;

        StubTools(Function<String, String> responder) {
            this.responder = responder;
        }

        @Override
        public List<ToolSpecification> listTools() {
            return TOOLS.stream().map(n -> ToolSpecification.builder().name(n).description(n)
                    .parameters(JsonObjectSchema.builder().build()).build()).toList();
        }

        @Override
        public Outcome execute(String id, String name, String argumentsJson) {
            executed.add(name);
            return new Outcome(false, responder.apply(name));
        }

        @Override
        public String describe() {
            return "stub";
        }
    }

    static AgentLoop loop(ChatModel model, ToolGateway tools, AgentConfig.Limits limits, Clock clock) {
        return new AgentLoop(model, tools, LeakageInspector.defaults(LeakageInspector.Mode.REDACT), limits, TOOLS,
                Prompts.SYSTEM, clock);
    }

    private TestSupport() {
    }
}
