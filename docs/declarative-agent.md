# The declarative Java coding agent

The demo uses `quarkus-langchain4j-agentic`, managed by the Quarkus platform BOM. `CodingAgent` combines
LangChain4j `@Agent` with Quarkus `@RegisterAiService`; Quarkus creates its injectable implementation.
The framework drives repeated model calls and tool execution until an answer or a limit.

## Code map

- [`CodingAgent`](../agent/src/main/java/io/agentcon/blueprint/agent/core/CodingAgent.java): the agent interface,
  system prompt, explicit memory ID, governed suppliers, unknown-tool strategy and framework safety ceilings.
- [`GovernedChatModel`](../agent/src/main/java/io/agentcon/blueprint/agent/core/GovernedChatModel.java): the CDI
  model boundary. It delegates to the current invocation, which calls the private model created by `ModelFactory`.
  No second default model provider or public fallback is configured.
- [`AgentInvocation`](../agent/src/main/java/io/agentcon/blueprint/agent/core/AgentInvocation.java): binds each
  invocation to its session, model, budgets and MCP gateway. It supplies allowlisted tool specifications and executors,
  inspects tool results before model delivery, wraps them as untrusted data, and emits the console trace.
- [`AgentLoop`](../agent/src/main/java/io/agentcon/blueprint/agent/core/AgentLoop.java): presenter lifecycle adapter,
  input inspection, bounded discovery, deadline/cancellation handling and conversation cleanup. Despite its retained
  name, it no longer implements the tool-calling loop.
- [`CodingTools`](../mcp-server/src/main/java/io/agentcon/blueprint/mcp/CodingTools.java): the separate Java MCP
  server's seven `@Tool` methods and `@ToolGuardrails` output inspection. Policy, approval, workspace containment,
  restricted verification and promotion remain server responsibilities.

The suppliers are shared CDI beans. Invocation state is bound only around an agent call and inherited by its
bounded virtual-thread operations, with cleanup in `finally`. Session memory stays in the agent process, separate
from the MCP server. The presenter serializes runs within each session. Tests also exercise two simultaneous sessions
with separate fake models to check that requests and answers do not cross sessions.

## Presenter flow

No new buttons, endpoints, credentials or model settings are required. Keep the existing tutorial sequence:

1. OpenCode fixes Trivia Quiz in the working tree with the selected private model.
2. Start the Java stack and inspect the disposable arcade workspace.
3. Ask for the RPS fix (or Word Scramble backup), review and approve each narrow proposal.
4. Run presenter-owned acceptance checks in the restricted container.
5. Promote the verified diff and reload the arcade.
6. Trigger the existing unsafe-action and leakage probes; inspect denied actions and redacted evidence.

`enterprise`, `local` and `scripted` still use the same model factory. The scripted fallback performs no inference;
its MCP calls and policy decisions are real. Capturing-endpoint tests use a fake model endpoint, not a real LLM.

## Controls that remain explicit

The model HTTP client pins the approved destination, refuses redirects and disables request/response logging.
The same run deadline covers discovery, inference and client waits. Step/tool/output/history limits still apply;
framework round-trip and sequential-tool ceilings provide an additional bound. Stop interrupts the run but cannot
roll back a server action that was already dispatched. Interrupted multi-tool exchanges are removed from memory
before a subsequent turn.

Small-model recovery for tool calls printed as text and a one-time request to turn a textual patch into a proposal
remain in the governed model boundary. They still pass through the allowlist, budgets, MCP policy and inspection.
No model response grants approval.

## Why no `@AgentLoop`?

The supported workflow annotation is `@LoopAgent`. It repeats a workflow of sub-agents; the normal model/tool
conversation already runs within `@Agent`. This demo is one coding agent with external tools, so adding a multi-agent
workflow would change the architecture without helping the existing demonstration.

The agentic API is experimental in the pinned extension. This migration is tested on the JVM; it does not claim
native-image compatibility for the blueprint. The pinned LangChain4j agentic runtime emits a standalone-agent
advisory recommending AI Services for a single-agent application. It still executes the generated agent and tools;
the advisory is not suppressed. `@RegisterAiService` alone would be sufficient for this single-agent demo; `@Agent`
is retained here to demonstrate the requested agentic programming model, without adding a redundant multi-agent loop.

## Verification

`./mvnw verify` runs the agent tests through the Quarkus-generated bean, including limits, redaction, unknown-tool
denials, conversation continuity, interrupted-tool recovery, concurrent session isolation and the capturing endpoint.
Packaged-process integration tests cover real MCP, approval, probes and captured model traffic. Prepare dependencies
with `scripts/prepare-deps.sh` to include restricted-container acceptance; real-model tests remain opt-in.

References: [Quarkus agentic AI](https://docs.quarkiverse.io/quarkus-langchain4j/dev/agentic.html),
[Quarkus AI services](https://docs.quarkiverse.io/quarkus-langchain4j/dev/ai-services.html),
[Quarkus MCP server](https://docs.quarkiverse.io/quarkus-mcp-server/dev/).
