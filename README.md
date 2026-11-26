# Your Code Never Leaves: a private coding agent in Java

A small, runnable blueprint for a Claude Code-style coding agent built with **Quarkus**, **LangChain4j** and a
**Java MCP server**, using inference you control (a loopback model or an approved private gateway), with
guardrails enforced in code: secrets protection, data-leakage detection, unsafe-command blocking and
allow/ask/deny action control.

[![ci](https://github.com/danieloh30/private-agent-blueprint/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/danieloh30/private-agent-blueprint/actions/workflows/ci.yml) [![Deploy docs to GitHub Pages](https://github.com/danieloh30/private-agent-blueprint/actions/workflows/pages.yml/badge.svg)](https://github.com/danieloh30/private-agent-blueprint/actions/workflows/pages.yml)

**Tutorial:** <https://danieloh30.github.io/private-agent-blueprint/>: play the buggy arcade, fix a game with
OpenCode, then fix the rest with the Java agent and compare the two.

## Architecture

```mermaid
%%{init: {"look":"handDrawn","theme":"base","themeVariables":{"darkMode":false,"background":"#FFFCF5","primaryColor":"#D4E6F1","primaryBorderColor":"#2E6B8A","primaryTextColor":"#1F1F1F","secondaryColor":"#D8F0D8","secondaryTextColor":"#1F1F1F","tertiaryColor":"#E8DCC4","tertiaryTextColor":"#1F1F1F","textColor":"#1F1F1F","lineColor":"#4A4035","edgeLabelBackground":"#FFFCF5","clusterBkg":"#F5F5F0","clusterBorder":"#6B5B45","fontSize":"16px"},"flowchart":{"nodeSpacing":35,"rankSpacing":55,"padding":18,"curve":"basis"}}}%%
flowchart LR
    DEV([Presenter / developer]) -->|browser :8090| UI
    subgraph AGENT["agent (Quarkus 4.0.1 + LangChain4j 1.24)"]
        UI[Presenter UI + API] --> LOOP[Agent loop<br/>limits, tool allowlist,<br/>leakage inspection]
    end
    LOOP -->|"OpenAI-compatible HTTPS<br/>pinned host, no redirects"| MODEL([Private model<br/>enMaaS gateway or<br/>loopback Ollama])
    LOOP -->|"MCP Streamable HTTP :8091/mcp<br/>bearer token"| TOOLS
    UI -->|"/admin approvals, acceptance<br/>approver token"| TOOLS
    subgraph MCP["mcp-server (Quarkus 3.40 + quarkus-mcp-server)"]
        TOOLS[7 tools] --> POLICY[Policy engine<br/>allow / ask / deny]
        POLICY --> INSPECT[Output leakage guardrail]
    end
    TOOLS -->|request-scoped| WS[(Disposable workspace<br/>.demo/runs/run-…/workspace)]
    TOOLS -->|named tasks only| SANDBOX[[Restricted container<br/>network=none, read-only,<br/>limits, no host creds]]
    SANDBOX --> SCRATCH[(scratch copy +<br/>acceptance tests)]
    FIXTURE[(arcade/<br/>your working tree)] -. copied per run .-> WS
    WS -. promote-workspace.sh<br/>after approval .-> FIXTURE

    style DEV fill:#D4E6F1,stroke:#2E6B8A,color:#1F1F1F,stroke-width:2px
    style AGENT fill:#F5F5F0,stroke:#8B8070,color:#1F1F1F,stroke-width:2px
    style MCP fill:#F5F5F0,stroke:#8B8070,color:#1F1F1F,stroke-width:2px
    style UI fill:#D4E6F1,stroke:#2E6B8A,color:#1F1F1F,stroke-width:2px
    style LOOP fill:#D4E6F1,stroke:#2E6B8A,color:#1F1F1F,stroke-width:2px
    style MODEL fill:#D8F0D8,stroke:#3D7A3D,color:#1F1F1F,stroke-width:2px
    style TOOLS fill:#D8F0D8,stroke:#3D7A3D,color:#1F1F1F,stroke-width:2px
    style POLICY fill:#F8D7D0,stroke:#A34736,color:#1F1F1F,stroke-width:2px
    style INSPECT fill:#F8D7D0,stroke:#A34736,color:#1F1F1F,stroke-width:2px
    style WS fill:#E8E0F0,stroke:#6B5B8A,color:#1F1F1F,stroke-width:2px
    style SANDBOX fill:#E8E0F0,stroke:#6B5B8A,color:#1F1F1F,stroke-width:2px
    style SCRATCH fill:#E8E0F0,stroke:#6B5B8A,color:#1F1F1F,stroke-width:2px
    style FIXTURE fill:#E8DCC4,stroke:#6B5B45,color:#1F1F1F,stroke-width:2px
```

| Component | Directory | Role |
|-----------|-----------|------|
| Java coding agent | [`agent/`](agent) | Agent loop, conversation state, model client, MCP client, presenter UI. **Talks to the model; never touches files.** |
| Model configuration | [`agent/.../model`](agent/src/main/java/io/agentcon/blueprint/agent/model), [`profiles/`](profiles) | `enterprise`, `local` and `scripted` profiles; endpoint validation; guarded HTTP client. |
| Java MCP server | [`mcp-server/`](mcp-server) | Seven focused tools over MCP Streamable HTTP. **Touches the workspace; never talks to a model.** |
| Policy and output inspection | [`guardrails/`](guardrails) | Framework-free policy engine, path containment, leakage inspector, approval store. |
| Isolated target workspace | [`arcade/`](arcade) → `.demo/runs/<run>/workspace` | Fixture copied per run; the agent can only change it through approved patches. |
| Independent verification | [`acceptance/`](acceptance), `SandboxRunner` | Presenter-owned acceptance tests run in a restricted container. |
| Demo setup | [`scripts/`](scripts), [`docs/`](docs) | Doctor, dependency preparation, start/stop, rehearsal, probes, evidence, promotion of approved changes, reset. |

The agent cannot edit its own policies, credentials, acceptance checks or runtime: they live in other processes
and directories that no tool can reach.

## Quick start

Prerequisites: Java 25, Podman (or Docker) for restricted verification, and either an enMaaS API key or a local
OpenAI-compatible model server. macOS: `podman machine start`.

```bash
scripts/prepare-deps.sh
```

```bash
scripts/doctor.sh enterprise
```

```bash
scripts/start.sh enterprise
```

Open <http://127.0.0.1:8090>. Other profiles:

```bash
scripts/start.sh local
```

```bash
scripts/start.sh scripted
```

`enterprise` reads [`profiles/enterprise.env`](profiles/enterprise.env) (enMaaS gateway, model id) and the key from
`$ENMAAS_KEY`. `local` reads [`profiles/local-ollama.env`](profiles/local-ollama.env) (loopback Ollama).
`scripted` is a deterministic, clearly labelled fallback that performs **no inference**: tool calls are
pre-recorded, while MCP, policy and sandbox stay real. See [docs/configuration.md](docs/configuration.md).

Rehearse and stop:

```bash
scripts/happy-path.sh
```

```bash
scripts/probes.sh
```

```bash
scripts/evidence.sh
```

```bash
scripts/stop.sh
```

## What the agent can do

1. **Inspect** the target repository: `list_files`, `search_text`, `read_file` (bounded, policy-checked, inspected).
2. **Explain** a narrow change in its answer.
3. **Produce a reviewable diff**: `propose_patch` (exact find/replace on one file, returns a unified diff and a proposal id).
4. **Request approval**: the proposal is `ASK` until the presenter approves *that exact diff* in the UI.
5. **Apply** the approved change: `apply_patch` re-checks authorization, approval, patch hash and file version, once.
6. **Verify**: `run_verification` with a named task (`unit-tests`, `acceptance-rps`, `acceptance-scramble`).
7. **Report** the actual result; `show_diff` returns the real workspace diff.

Limits per request (configurable): 10 model steps, 12 tool calls, 8,000 tool-output characters delivered to the
model, 240 s wall-clock. Conversation state lives only in the agent (bounded to 40 messages).

## Guardrails (enforced in code, not in the prompt)

| Guardrail | Where | What it does |
|-----------|-------|--------------|
| Secrets protection | `PolicyEngine`, `WorkspacePaths` | Protected paths (`config/**`, `.env`, keys, `.git/**`, `.mvn/**`…) are denied for read and write; traversal, absolute paths and symlinks are rejected. |
| Data-leakage detection | `LeakageInspector` in the MCP output guardrail **and** the agent loop | Synthetic canary, representative secret patterns and the `AGENT-RESTRICTED` marker are redacted or blocked before content reaches a model request, the trace or a log. |
| Unsafe-command blocking | `VerificationTasks`, `SandboxRunner` | No shell tool. Only named tasks with fixed argument lists, executed in a restricted container; fails closed if isolation is unavailable. |
| Policy-based action control | `PolicyEngine`, `ApprovalStore` | Reads ALLOW, propose ALLOW, apply ASK until approved (bound to workspace, path, patch hash and file hash; expires; single use), everything else DENY. |

Details and remaining limitations: [docs/threat-model.md](docs/threat-model.md).

## Demo

Two acts on the same buggy arcade (`cd arcade && ../mvnw quarkus:dev`, then <http://localhost:8080>):

1. **OpenCode** fixes a game in place with the private model; reload the page to see it.
2. **The Java agent** fixes the others: it proposes diffs, you approve them in the console, acceptance tests pass in
   the sandbox, and `scripts/promote-workspace.sh` copies the approved change into `arcade/` so the page reload shows it.

Step by step: the [tutorial](docs/tutorials/index.md) (published at
<https://danieloh30.github.io/private-agent-blueprint/>). Timed stage script with exact prompts:
[docs/runbook.md](docs/runbook.md). Reset and troubleshooting: [docs/troubleshooting.md](docs/troubleshooting.md).

## Endpoints

| Process | Endpoint | Purpose |
|---------|----------|---------|
| agent `127.0.0.1:8090` | `/` | Presenter UI |
| | `GET /api/status`, `GET /api/presets`, `GET /api/session?after=n` | Profile, endpoint host, model, trace |
| | `POST /api/ask`, `GET /api/proposals`, `POST /api/proposals/{id}/approve\|reject` | Agent run and approvals |
| | `POST /api/verify/{task}`, `POST /api/probes/{name}`, `GET /api/evidence`, `POST /api/reset` | Checks, probes, evidence, fresh workspace |
| mcp-server `127.0.0.1:8091` | `/mcp` | MCP Streamable HTTP (agent bearer token) |
| | `/admin/*` | Approvals, audit, acceptance, reset (approver token; not an MCP tool) |
| arcade `localhost:8080` | `/`, `/rps`, `/scramble`, `/quiz` | Pristine fixture for the baseline (`scripts/arcade-baseline.sh`) |

## Tests

```bash
./mvnw verify
```

```bash
./mvnw -pl mcp-server test -Dsandbox.it=true
```

```bash
./mvnw verify -pl agent -am -Dreal-model.it=true -Dreal-model.profile=enterprise
```

| Suite | What it proves |
|-------|----------------|
| `guardrails` unit tests | Path traversal/symlink handling, policy decisions, leakage rules, approval binding, expiry and replay. |
| `mcp-server` `McpToolsTest` | Real MCP discovery and invocation over Streamable HTTP; protected files, escapes, canary redaction, approval integrity, command denial, audit redaction. |
| `mcp-server` sandbox tests | Container flags; timeout kills the container; no engine means fail closed with nothing run on the host. Opt-in `SandboxContainerTest` runs real containers: acceptance fails before and passes after an approved fix. |
| `agent` unit tests | Endpoint policy, redirect and destination pinning, loop limits, tool allowlist, untrusted wrapping, a **capturing model endpoint** proving sensitive values never reach model requests. Use a deterministic fake model, labelled as such. |
| `agent` `AgentEndToEndIT` | Packaged MCP server and agents as separate processes: approval flow, probes, captured model requests, sandboxed acceptance (skipped if no container engine), redacted logs and evidence. |
| `agent` `RealModelIT` (opt-in) | A configured real model drives real MCP tools and never sees the canary. |

## Versions

Quarkus 4.0.1 (agent, arcade), LangChain4j 1.24.0, langchain4j-mcp 1.24.0-beta34, Java 25. The MCP server stays on
Quarkus 3.40.1.2 with quarkus-mcp-server 2.0.4 because quarkus-mcp-server 2.1.0 (managed by the 4.0.1 platform)
still references the Jackson 2 `ObjectMapperCustomizer` that Quarkus 4 removed; move it to 4.x once a compatible
release ships. They are separate processes, so the mix is safe.

Guides: [Quarkus MCP server](https://docs.quarkiverse.io/quarkus-mcp-server/dev/),
[LangChain4j MCP](https://docs.langchain4j.dev/tutorials/mcp),
[LangChain4j OpenAI-compatible](https://docs.langchain4j.dev/integrations/language-models/open-ai),
[Quarkus REST](https://quarkus.io/guides/rest), [Quarkus config mappings](https://quarkus.io/guides/config-mappings),
[Quarkus testing](https://quarkus.io/guides/getting-started-testing).

The arcade's own documentation (games, seeded bugs, prompts for other assistants) is in
[arcade/README.md](arcade/README.md). `opencode.jsonc` configures OpenCode against the same enMaaS gateway.
