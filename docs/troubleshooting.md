# Reset and troubleshooting

## Reset

| Scope | Command | Touches |
|-------|---------|---------|
| New disposable workspace, same processes | UI **Fresh workspace**, or `curl -XPOST http://127.0.0.1:8090/api/reset` | creates `.demo/runs/<new-run>` |
| Stop everything | `scripts/stop.sh` | the agent, MCP server and arcade processes; leftover `blueprint-verify-*` containers |
| Clean demo state | `scripts/reset-demo.sh` | deletes `.demo/runs`, `.demo/logs`, `.demo/evidence`; keeps `.demo/m2` and tokens |

None of these touch git, `arcade/`, `acceptance/` or any other file in your working tree. To regenerate the
dependency cache, delete `.demo/m2` and run `scripts/prepare-deps.sh`.

## Symptoms

| Symptom | Cause and fix |
|---------|---------------|
| `scripts/start.sh` says port in use | `scripts/stop.sh`; otherwise `lsof -nP -iTCP:8090 -sTCP:LISTEN` and stop that process. |
| Agent exits at startup: `blueprint.model.base-url is required` / `profile is required` | Expected without a profile. Use `scripts/start.sh enterprise|local|scripted`. |
| `Host '…' is not in blueprint.model.approved-hosts` | The base URL changed; update `BLUEPRINT_MODEL_APPROVED_HOSTS` deliberately. |
| `The local profile requires a loopback endpoint` | `local` only accepts 127.0.0.1, ::1 or names resolving only to loopback. Use `enterprise` for remote endpoints. |
| Trace shows `Model call failed … 401` | Key missing or expired: `scripts/doctor.sh enterprise`. Gateways may echo a masked key fragment; full `sk-` keys are redacted before display. |
| Trace shows `Model request to … refused` | The guarded HTTP client blocked a destination change (for example a redirect or a different base URL). |
| Verification says `NOT RUN: restricted execution environment unavailable` | No working container engine; the run fails closed. macOS: `podman machine start`. |
| Verification says `NOT RUN: dependencies not prepared` | `scripts/prepare-deps.sh`. |
| Verification `ERROR` mentioning SELinux or permissions on Linux | Add `:Z` handling or run rootless Podman; the default flags are tested on macOS with Podman 6.1. |
| `TIMEOUT after 240000 ms; container killed` | First offline run after changing dependencies, or an agent change that hangs tests. Inspect the diff. |
| Model writes code in prose instead of proposals | Small local models do this. The agent recovers printed tool calls and nudges once; otherwise ask again or switch to `enterprise`. |
| Local `qwen3:4b` takes minutes | That tag is a thinking-only build. Use `qwen3:4b-instruct` (see configuration.md). |
| `NoClassDefFoundError: io/quarkus/jackson/ObjectMapperCustomizer` building mcp-server | quarkus-mcp-server 2.1.0 on Quarkus 4. Keep `mcp-server` on Quarkus 3.40.1.2 (see the README). |
| UI shows "MCP server unreachable" | `tail .demo/logs/mcp-server.log`; restart with `scripts/stop.sh && scripts/start.sh …`. |

## Logs and evidence

- `.demo/logs/agent.log`, `.demo/logs/mcp-server.log`: one line per decision, no prompts, no file contents.
- `.demo/runs/<run>/audit.jsonl`: redacted audit records (tool, verdict, rule, path, hashes, sizes).
- `.demo/evidence/evidence-*.json`: produced by `scripts/evidence.sh`, which also fails if a canary or synthetic
  secret value appears in evidence or logs.
