package io.agentcon.blueprint.agent.core;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.tool.ToolProviderResult;
import io.agentcon.blueprint.agent.AgentConfig;
import io.agentcon.blueprint.agent.tools.ToolGateway;
import io.agentcon.blueprint.guardrails.LeakageInspector;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Per-invocation enforcement around the declarative agent. The framework owns tool orchestration;
 * this class owns inspection, budgets, real MCP dispatch and presentation events.
 * The binding is inherited by bounded virtual-thread calls, never shared as mutable CDI singleton state.
 */
final class AgentInvocation {
    private static final InheritableThreadLocal<AgentInvocation> ACTIVE = new InheritableThreadLocal<>();
    final AgentSession session;
    final RunBudget budget;
    final AgentConfig.Limits limits;
    final String systemPrompt;
    final LeakageInspector inspector;
    private final ChatModel model;
    private final ToolGateway gateway;
    private final Set<String> allowed;
    private final List<ToolSpecification> specifications;
    private final Clock clock;
    private final long deadline;
    private String stopReason;
    private boolean nudged;
    private int proposals;

    AgentInvocation(AgentSession session, RunBudget budget, ChatModel model, ToolGateway gateway,
                    LeakageInspector inspector, AgentConfig.Limits limits, Set<String> allowed,
                    String systemPrompt, Clock clock, List<ToolSpecification> specifications) {
        this.session = session; this.budget = budget; this.model = model; this.gateway = gateway;
        this.inspector = inspector; this.limits = limits; this.allowed = allowed;
        this.systemPrompt = systemPrompt; this.clock = clock; this.specifications = specifications;
        this.deadline = clock.millis() + limits.maxRunTime().toMillis();
    }

    static AgentInvocation current() {
        AgentInvocation invocation = ACTIVE.get();
        if (invocation == null) throw new IllegalStateException("CodingAgent must run through the governed presenter service");
        return invocation;
    }

    String invoke(CodingAgent agent, String text) {
        AgentInvocation before = ACTIVE.get();
        ACTIVE.set(this);
        try { return agent.chat(session.id(), text); }
        finally { if (before == null) ACTIVE.remove(); else ACTIVE.set(before); }
    }

    ToolProviderResult tools() {
        var tools = ToolProviderResult.builder();
        specifications.forEach(spec -> tools.add(spec, (call, memoryId) -> execute(call)));
        return tools.build();
    }

    ChatMemory memory() { return new SessionMemory(session, systemPrompt); }

    private void check() {
        if (stopReason != null) throw new RunBudget.Stopped(stopReason);
        if (session.stopRequested() || Thread.currentThread().isInterrupted()) throw new RunBudget.Stopped("cancelled");
        if (clock.millis() >= deadline) throw new RunBudget.Stopped("time-limit");
    }

    ChatResponse chat(ChatRequest original) {
        ChatRequest request = original;
        while (true) {
            check();
            if (budget.steps >= limits.maxSteps()) throw new RunBudget.Stopped("step-limit");
            budget.steps++;
            long start = clock.millis();
            ChatRequest next = request;
            ChatResponse response = budget.call(() -> model.chat(next));
            AiMessage ai = response.aiMessage();
            String usage = response.tokenUsage() == null ? "" : " (" + response.tokenUsage().inputTokenCount()
                    + " in / " + response.tokenUsage().outputTokenCount() + " out tokens)";
            if (ai.hasToolExecutionRequests()) {
                session.emit(TraceEvent.Kind.MODEL, "Model step " + budget.steps,
                        "requests " + String.join(", ", ai.toolExecutionRequests().stream().map(ToolExecutionRequest::name).toList())
                                + usage, null, null, clock.millis() - start);
                return response.toBuilder().aiMessage(AiMessage.from(ai.toolExecutionRequests())).build();
            }
            String answer = AgentLoop.sanitize(ai.text());
            ToolExecutionRequest recovered = AgentLoop.recoverTextToolCall(answer);
            if (recovered != null) {
                session.emit(TraceEvent.Kind.INFO, "Recovered " + recovered.name() + " call from model text",
                        "Executing through the same allowlist, budgets, MCP policy and inspection.", null, null, clock.millis() - start);
                return response.toBuilder().aiMessage(AiMessage.from(recovered)).build();
            }
            if (!nudged && proposals == 0 && AgentLoop.looksLikeTextPatch(answer) && budget.steps < limits.maxSteps()) {
                nudged = true;
                session.emit(TraceEvent.Kind.INFO, "Agent: patch written as text, asking for propose_patch",
                        "Only propose_patch produces a reviewable, approvable diff.");
                var messages = new ArrayList<>(request.messages());
                messages.add(AiMessage.from(inspector.redactForLog(answer)));
                messages.add(UserMessage.from("[agent] Patches in text cannot be reviewed or approved. Call propose_patch now; "
                        + "find must be exact existing text, replace must be the new text."));
                request = request.toBuilder().messages(messages).build();
                continue;
            }
            if (answer.length() > limits.maxAnswerChars()) answer = answer.substring(0, limits.maxAnswerChars()) + " ... [truncated]";
            answer = inspector.redactForLog(answer);
            session.emit(TraceEvent.Kind.MODEL, "Model step " + budget.steps, "final answer" + usage,
                    null, null, clock.millis() - start);
            session.emit(TraceEvent.Kind.ANSWER, "Agent answer", answer.isBlank() ? "(the model returned no text)" : answer);
            return response.toBuilder().aiMessage(AiMessage.from(answer.isBlank() ? "(no answer)" : answer)).build();
        }
    }

    String execute(ToolExecutionRequest call) {
        check();
        if (budget.toolCalls >= limits.maxToolCalls()) {
            stopReason = "tool-limit";
            session.emit(TraceEvent.Kind.LIMIT, "Tool-call limit reached", limits.maxToolCalls() + " tool calls per request",
                    "DENY", "limit.tool-calls", 0);
            return AgentLoop.wrapUntrusted(call.name(), "[agent] DENY limit.tool-calls: tool-call budget exhausted");
        }
        budget.toolCalls++;
        String args = call.arguments() == null ? "{}" : call.arguments();
        String safeArgs = inspector.redactForLog(args);
        String shown = safeArgs.length() > 600 ? safeArgs.substring(0, 600) + "..." : safeArgs;
        if (!allowed.contains(call.name())) {
            String reason = "Tool '" + call.name() + "' is not in the agent's explicit tool set";
            session.emit(TraceEvent.Kind.TOOL, call.name(), shown + "\n" + reason, "DENY", "agent.tool-not-allowed", 0);
            return AgentLoop.wrapUntrusted(call.name(), "[policy] DENY agent.tool-not-allowed: " + reason);
        }
        if (call.name().equals("propose_patch")) proposals++;
        long start = clock.millis();
        ToolGateway.Outcome outcome;
        try { outcome = budget.call(() -> gateway.execute(call.id(), call.name(), args)); }
        catch (RunBudget.Stopped stopped) { throw stopped; }
        catch (RuntimeException error) { outcome = new ToolGateway.Outcome(true, "[policy] DENY tool.transport-error: " + AgentLoop.rootMessage(error)); }
        var inspected = inspector.inspect(outcome.text() == null ? "" : outcome.text());
        if (!inspected.clean()) session.emit(TraceEvent.Kind.INSPECTION, "Tool output inspected in agent before model delivery",
                inspected.summary(), inspected.blocked() ? "DENY" : "ALLOW",
                inspected.blocked() ? "leakage.blocked" : "leakage.redacted", 0);
        String text = inspected.text();
        if (text.length() > limits.maxToolOutputChars()) text = text.substring(0, limits.maxToolOutputChars())
                + "\n... [truncated to " + limits.maxToolOutputChars() + " characters]";
        var decision = Decisions.parse(text);
        if (decision.leakage() != null) session.emit(TraceEvent.Kind.INSPECTION,
                "MCP server inspected " + call.name() + " output before delivery", decision.leakage(),
                decision.leakage().startsWith("BLOCKED") ? "DENY" : "ALLOW",
                decision.leakage().startsWith("BLOCKED") ? "leakage.blocked" : "leakage.redacted", 0);
        session.emit(TraceEvent.Kind.TOOL, call.name(), shown + "\n" + (text.length() > 4000 ? text.substring(0, 4000) + "..." : text),
                decision.verdict(), decision.rule(), clock.millis() - start);
        return AgentLoop.wrapUntrusted(call.name(), text);
    }

    /** Session history remains presenter-owned and is never stored in the MCP server. */
    private record SessionMemory(AgentSession session, String systemPrompt) implements ChatMemory {
        public Object id() { return session.id(); }
        public void add(ChatMessage message) {
            if (!(message instanceof SystemMessage)) session.history().add(message);
        }
        public List<ChatMessage> messages() {
            var messages = new ArrayList<ChatMessage>();
            messages.add(SystemMessage.from(systemPrompt));
            synchronized (session.history()) { messages.addAll(session.history()); }
            return messages;
        }
        public void clear() { session.history().clear(); }
    }
}
