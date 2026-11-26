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

### 3. Reset for the next run

```bash
git checkout arcade/
```

```bash
scripts/reset-demo.sh
```

```bash
scripts/start.sh enterprise
```

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

- Both assistants fixed real bugs with the same private model.
- The Java agent turns "trust the tool" into checks you can show: approvals, denials, redaction, sandboxed proof.
- Everything you showed is reproducible from this repository with the scripts above.
