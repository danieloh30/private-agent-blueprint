# Threat model (short)

## What we protect

1. **Source code and secrets** in the developer's repository: they must reach only the approved inference
   destination, and secret material should not reach it at all.
2. **The developer's machine**: the agent must not run arbitrary commands, reach other directories or use host
   credentials.
3. **Integrity of changes**: nothing is written without a human approving that exact change.
4. **The agent's own controls**: policies, credentials, acceptance checks and runtime are out of the agent's reach.

## Adversaries and failure modes considered

- A model that is wrong, overconfident or manipulated (prompt injection in repository files, tool output or
  user-pasted content).
- Workspace-controlled code executing during a build or test (Maven plugins, tests, annotation processors).
- Accidental leakage through logs, traces or misconfiguration (wrong endpoint, redirects, proxies).

Out of scope: a malicious local user with shell access, a compromised JVM or OS, a compromised inference provider,
and supply-chain attacks on dependencies (the dependency cache is filled from Maven Central by
`scripts/prepare-deps.sh`).

## Trust boundaries

```
presenter ──UI──> agent process ──HTTPS (pinned)──> inference endpoint
                     │  (no file access)
                     └──MCP + bearer──> MCP server ──> disposable workspace (.demo/runs/<run>/workspace)
                                            └──fixed argv──> container: network=none, read-only, limits
```

## Controls

### Application-enforced (Java code in this repository)

| Control | Implementation | Test |
|---------|----------------|------|
| Explicit tool set, no shell | 7 MCP tools; agent allowlist `blueprint.allowed-tools`; unknown tools DENY | `McpToolsTest.discoversExactlyTheExplicitToolSet`, `AgentLoopTest.toolsOutsideTheExplicitSetAreDeniedWithoutExecution` |
| Workspace containment | `WorkspacePaths`: relative paths only, no `..`, no absolute or drive paths, no backslashes or NUL, no symlinks on the path, real-path check, existing parent for writes | `WorkspacePathsTest`, `McpToolsTest.traversalAbsoluteAndSymlinkEscapesAreDenied` |
| Protected paths | `PolicyEngine`: protected globs checked before any allow rule, for reads and writes | `PolicyEngineTest`, `McpToolsTest.protectedSecretFileIsDenied` |
| Size limits | 64 KB per read, 12,000 characters per tool result (server), 8,000 delivered to the model (agent), 40 search hits | `AgentLoopTest.toolOutputIsWrappedAsUntrustedAndTruncated` |
| Leakage inspection | `LeakageInspector`: canary, AWS key ids, `sk-` keys, GitHub tokens, bearer tokens, private keys, credential assignments, `AGENT-RESTRICTED` marker; REDACT or BLOCK; applied to user input (agent), every tool result (MCP output guardrail **and** agent), traces, audit and logs | `LeakageInspectorTest`, `CapturingModelEndpointTest`, `AgentEndToEndIT.capturedModelRequestsContainNoSensitiveContent` |
| Approval binding | `ApprovalStore`: bound to action, workspace id, path, patch SHA-256 the human saw, base-file SHA-256; expires (15 min proposal, 5 min approval); single use; re-authorized at apply | `ApprovalStoreTest`, `McpToolsTest.writeWaitsForApprovalAndApprovalIsSingleUse`, `…approvalIsInvalidatedWhenTargetFileChanges` |
| Approval channel separation | Approvals arrive on `/admin` with an approver token held by the presenter backend; no MCP tool can approve | `McpToolsTest.adminEndpointRequiresApproverToken` |
| Promotion into the developer's tree | The only write path outside the workspace. `/admin/promote` (approver token, not an MCP tool) copies files only if the request carries the SHA-256 of the diff the presenter reviewed and that exact diff passed a presenter acceptance check; files changed locally since the run started are never overwritten; paths go through `WorkspacePaths` rooted at the target (no traversal, no symlinks); every attempt is audited. Can be turned off with `BLUEPRINT_PROMOTE_ENABLED=false`, which disables both console and terminal promotion | `PromotionTest`, `PromoteEndpointTest` |
| Authentication and identity | `/mcp` requires the agent bearer token; identity (`demo-agent`) and workspace are bound server-side, never taken from the model | `McpToolsTest.mcpEndpointRequiresAgentToken` |
| Untrusted content handling | Tool output wrapped as `<tool_output trust="untrusted">` (closing tag escaped); the prompt says not to follow it; controls do not depend on the model obeying | `AgentLoopTest` |
| Model destination | `ModelEndpointPolicy`: profile required, no default URL, local = loopback only (resolved), enterprise = HTTPS + exact approved host; no credentials in the URL. `GuardedHttpClient`: every request must match scheme/host/port/path prefix; redirects never followed; no proxy unless enabled | `ModelEndpointPolicyTest`, `GuardedHttpClientTest` |
| Execution limits | Per request: model steps, tool calls, wall-clock; bounded history | `AgentLoopTest.stopsAt*` |
| Logging hygiene | LangChain4j request/response logging off; MCP traffic logging off; audit stores metadata, not contents, all redacted; API key only in the `Authorization` header | `McpToolsTest.auditRecordsDecisionsWithoutSecretValues`, `AgentEndToEndIT.logsAndEvidenceAreRedacted` |
| No hidden reasoning | Thinking is not requested (`returnThinking(false)`), `<think>` text is stripped, narration attached to tool calls is dropped | `AgentLoopTest.thinkingTextIsStripped` |

### Operating-system / container-enforced

| Control | Implementation |
|---------|----------------|
| Workspace code runs only in a container | `SandboxRunner`: `podman run --rm --network=none --cap-drop=ALL --security-opt=no-new-privileges --read-only --tmpfs /tmp --memory=2g --memory-swap=2g --cpus=2 --pids-limit=512`, wall-clock timeout with `kill` |
| Mounts | Only a scratch copy of the workspace (rw) and the prepared Maven cache (ro). No home directory, no host credentials, no engine socket, no host environment |
| Dependency preparation separated | `scripts/prepare-deps.sh` (network on) builds the **pristine** fixture; verification runs offline (`mvn -o`) with the cache read-only |
| Fail closed | If the engine is missing or broken, tasks report `ISOLATION_UNAVAILABLE` and nothing runs on the host (`SandboxFailClosedTest`) |
| Loopback binding | Agent and MCP server bind to `127.0.0.1` |

### Network-enforced: not provided by this repository

The model destination checks are **application configuration**, not a network isolation boundary. A bug,
a dependency or another process on the machine could still open connections. For a real deployment add:
an egress firewall or proxy allowing only the inference host, network policy (Kubernetes) or a host firewall,
DNS controls, and TLS inspection or private endpoints as your organisation requires.

## Remaining limitations

- **Pattern-based detection is incomplete.** Encoded, split, paraphrased or unknown secret formats will pass.
  The canary proves the pipeline works; it does not prove absence of leakage.
- **Source code does reach the model.** That is the point; "never leaves" means it goes only to the
  inference endpoint you chose. With enMaaS, what happens behind the gateway (which provider serves the model,
  retention, region) is governed by the gateway and your agreement, not by this code. Choose a model and
  gateway accordingly; the local profile keeps everything on the machine.
- **Presenter UI shares the agent process.** It holds the approver token, so a compromised agent process could
  approve its own proposals. A production design moves approval to a separate, authenticated service.
- **Approved patches still run workspace code** in the sandbox. Container escapes, kernel bugs and resource
  exhaustion inside limits are not addressed. On macOS the container runs inside the Podman VM, which also
  mounts the user's home directory into the VM (not into the container).
- **The agent's own tests are not proof.** The agent can edit `src/test`; correctness is judged by the
  presenter-owned acceptance tests, which an adversarial patch could still try to game (for example by
  detecting the test). Review diffs.
- **Recovered text tool calls**: for small local models, a tool call printed as text is executed through the
  same enforcement path and labelled in the trace. It widens what the model can trigger only to the same tools.
- **In-memory state**: proposals, approvals and sessions are lost on restart (by design for a demo).
- **Single user, loopback only**: no TLS between local processes, no multi-tenant isolation.

### Verification transaction

A workspace lease excludes concurrent MCP operations, reset, approval and promotion while a presenter verification runs. The captured run/diff, not a later current workspace, receives the acceptance result. The shell promotion helper calls the same server endpoint and cannot skip acceptance. This excludes application-mediated concurrent mutation; a malicious local user remains out of scope. Stop/cancellation bounds the agent's wait and planning, not an already dispatched server-side action.
