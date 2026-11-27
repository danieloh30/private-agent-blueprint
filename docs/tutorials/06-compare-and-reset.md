# 06 · Compare, collect evidence and reset

## Goal

Close the demo with evidence of what happened, compare the two acts honestly, and reset everything for the next run.

## Steps

### 1. Collect evidence

```bash
scripts/evidence.sh
```

It saves `.demo/evidence/evidence-<time>.json` and prints the run label (`LIVE MODEL RUN` or `SCRIPTED FALLBACK`), the
endpoint and model, decision counts, the FAILED → PASSED acceptance pair, the applied proposals, and a disclaimer. It
fails if a canary or synthetic secret appears in the evidence or the logs.

### 2. Compare the two acts

| | Act 1: OpenCode | Act 2: Java agent |
| --- | --- | --- |
| Time to a fix | about 30 s per game | about 10 to 17 s agent run, plus your review |
| Scope | Your working tree, a shell | Seven tools on a disposable copy |
| Review | `git diff` after the change | Approve the exact diff before the change |
| Tests | Your machine, with network | Sandbox: no network, read-only, limits |
| Proof | The page reloads fixed | Presenter-owned acceptance tests, then promote and reload |
| Secrets | Sent to the model if read | Denied (protected paths) or redacted (patterns, canary) |
| Audit | Terminal scrollback | Redacted audit log and evidence bundle |

Say what this does **not** prove: pattern-based detection misses encoded or novel secrets, the model endpoint check is
application configuration (not a network firewall), and source code does reach the model you chose. See the
[threat model](../threat-model.md).

### 3. Decide which one fits your use case

They solve different problems, and most teams end up with both. **Act 1 is a tool a developer uses. Act 2 is a
system a team builds** when an agent has to work somewhere it can't simply be trusted.

**Use a general-purpose assistant (Act 1, OpenCode)** when the developer is present, in control, and responsible
for each change:

- Day-to-day coding on your own machine: fixing bugs, writing tests, refactoring, explaining unfamiliar code.
- Exploration and prototyping: spikes, proofs of concept, learning a new framework.
- Repositories without sensitive data, where reviewing with `git diff` afterwards is enough.
- Fast iteration, where a shell and access to any file matter more than control.
- Code that can't go to a public API: point it at a private model such as enMaaS or Ollama.

*Trust model: I trust the tool because I'm watching it, and I'm accountable for what I commit.*

**Build a governed agent (Act 2, Java and Quarkus)** when the agent acts for someone, unattended, or near things
that need protecting:

- **Agents that run without a developer watching:** fixing CI failures, opening dependency-upgrade PRs, turning bug
  reports into patches, nightly code-health tasks. The demo still requires explicit human approval before applying a patch and a separate promotion decision. Unattended deployment is a future adaptation requiring its own narrowly scoped approval policy.
- **Regulated or sensitive codebases** (finance, healthcare, government, partner code under NDA), where auditors want
  evidence of what the agent could reach, what it was denied, and who approved each change.
- **An internal coding-agent service for many teams:** one agent with central policy, instead of trusting each
  laptop's settings.
- **An agent inside your own product or pipeline:** a Java service that receives a ticket, proposes a fix, runs your
  acceptance tests and asks a human to approve. That's a backend system, not a terminal tool.
- **Strict data boundaries:** an air-gapped or on-premises model, secrets and customer data that must never reach the
  model, a network egress policy.
- **Policies specific to your organization:** your protected paths, approved verification tasks and approval rules,
  in code your team can test and review.

*Trust model: I trust the system because of how it's built, not because someone is watching.*

| Question | If yes, lean toward |
| --- | --- |
| Is a developer watching every step? | Act 1 |
| Does it run unattended, or for other people? | Act 2 |
| Is reviewing with `git diff` afterwards enough? | Act 1 |
| Do you need proof of what was allowed, denied and approved? | Act 2 |
| Is it a personal productivity tool? | Act 1 |
| Is it part of your platform or product? | Act 2 |

> **Note:** general-purpose assistants can be configured to ask before edits, deny reads and block commands, and some
> offer sandboxing or admin-managed settings. In this demo OpenCode runs with the repository's `opencode.jsonc`,
> which sets no permission rules. The difference Act 2 shows is **where the controls live**: in a separate server
> process that never talks to the model, written as Java code your team owns, tests and audits, and working with any
> private model.

### 4. Reset for the next run

```bash
git checkout arcade/
```

```bash
scripts/reset-demo.sh
```

```bash
scripts/start.sh enterprise
```

To rehearse again without restarting anything, click **Start over** in the console: a fresh workspace, all
checkmarks and results cleared, back to step 1. For a full reset between sessions, use the commands above.

`git checkout arcade/` restores all three bugs (and drops OpenCode's and the promoted changes). `reset-demo.sh` stops
the agent and MCP server and removes only `.demo/runs`, `.demo/logs` and `.demo/evidence`; the Maven cache and tokens
stay. Restart dev mode in its own terminal (`cd arcade && ../mvnw quarkus:dev`) if you stopped it.

## If something goes wrong on stage

| Problem | Do this |
| --- | --- |
| Venue network is slow | `scripts/stop.sh`, then `scripts/start.sh local`; in OpenCode pick **Ollama (local)** |
| No model at all | `scripts/start.sh scripted`: a clearly labelled replay of the tool plan with no inference; MCP, policy and sandbox stay real |
| The agent proposes a wrong diff | Reject it, say why, ask again: that is the point of review |
| Port in use | `scripts/stop.sh`; a second stack can run with `AGENT_PORT=18090 MCP_PORT=18091 scripts/start.sh …` |

More: [reset and troubleshooting](../troubleshooting.md) and the timed [presenter runbook](../runbook.md).

## Key takeaways

- Both assistants fixed real bugs with the same private model and comparable fixes.
- Use general-purpose assistants to make developers faster; build a governed agent when the agent becomes part of
  your system.
- The Java agent turns "trust the tool" into checks you can show: approvals, denials, redaction, sandboxed proof.
- Everything you showed is reproducible from this repository with the scripts above.
