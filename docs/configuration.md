# Configuration

No configuration file contains a credential. Keys come from your shell environment and are passed only to the
agent process, which sends them only in the `Authorization` header to the validated endpoint.

## Profiles

| Profile | Endpoint rule | Defaults file | Key |
|---------|---------------|---------------|-----|
| `enterprise` (talk default) | HTTPS, host must be in `BLUEPRINT_MODEL_APPROVED_HOSTS` | [`profiles/enterprise.env`](../profiles/enterprise.env) (enMaaS) | `$ENMAAS_KEY` (name set by `BLUEPRINT_MODEL_API_KEY_ENV`) |
| `local` | Host must resolve only to loopback | [`profiles/local-ollama.env`](../profiles/local-ollama.env) | none |
| `scripted` | No endpoint; deterministic fallback, no inference | none | none |

Starting the agent without a profile, endpoint or model fails at startup. There is no public default.

### Enterprise: enMaaS (default)

```bash
# ~/.zshrc (already present on the presenter laptop)
export ENMAAS_KEY=...            # never commit
```

`profiles/enterprise.env`:

```properties
BLUEPRINT_MODEL_BASE_URL=https://api.enmaas.devshift.net/v1
BLUEPRINT_MODEL_APPROVED_HOSTS=api.enmaas.devshift.net
BLUEPRINT_MODEL_NAME=rits/deepseek-ai/DeepSeek-V4.1-Flash-1017
BLUEPRINT_MODEL_API_KEY_ENV=ENMAAS_KEY
```

List the models the gateway offers (key not printed):

```bash
scripts/doctor.sh enterprise --models
```

Models measured with tool calling on 26 Nov 2026: `rits/deepseek-ai/DeepSeek-V4.1-Flash-1017` (open weights,
2.3 s single tool call), `rits/zai-org/glm-5-3` (1.5 s; returns `reasoning_content`, which the agent ignores),
`gpt-5.4-mini` (1.0 s). Prefer open-weights models served on infrastructure your organisation approves if the
point is that code stays in your control.

### Enterprise: LiteMaaS (alternative)

```bash
BLUEPRINT_ENTERPRISE_ENV=profiles/enterprise-litemaas.env scripts/start.sh enterprise
```

Uses `$LITEMAAS_API_KEY` and `Qwen3.8-27B`. (On 26 Nov 2026 the key in the presenter's shell was rejected by
that gateway; refresh it before using this profile.)

### Local: Ollama

```bash
ollama pull qwen3:4b-instruct
```

```bash
scripts/start.sh local
```

Use a **non-thinking** instruct model with tool calling. `qwen3:4b` on Ollama is the 2507 thinking build: it
ignores `/no_think` and `think:false` and spends 30 to 100 s per step on hidden reasoning. With
`qwen3:4b-instruct` the primary task takes 8 to 11 s; the model sometimes prints tool calls as text (recovered
and labelled) and usually needs the *Follow-up* prompt for the test update.

For hybrid Qwen3 models that honour it, set `BLUEPRINT_MODEL_SYSTEM_PREFIX=/no_think`.

## Overrides

Shell variables win over profile files. Machine-local overrides go in `.demo/model.env` (gitignored):

```properties
BLUEPRINT_MODEL_NAME=rits/zai-org/glm-5-3
```

## Agent settings (`agent/src/main/resources/application.properties`)

| Property | Default | Meaning |
|----------|---------|---------|
| `blueprint.model.profile` | none (required) | `local`, `enterprise`, `scripted` (set by `-Dquarkus.profile`) |
| `blueprint.model.base-url`, `.name` | none (required) | Endpoint and model id |
| `blueprint.model.approved-hosts` | none | Enterprise allowlist (exact host names) |
| `blueprint.model.use-system-proxy` | `false` | Use JVM proxy settings for the model connection |
| `blueprint.model.timeout` | `120s` | Per model call |
| `blueprint.allowed-tools` | the 7 tools | Explicit tool allowlist |
| `blueprint.limits.max-steps` / `max-tool-calls` / `max-tool-output-chars` / `max-run-time` | 10 / 12 / 8000 / 240s | Per request |
| `blueprint.leakage.mode` | `REDACT` | `REDACT` or `BLOCK` for user input |

## MCP server settings (`mcp-server/src/main/resources/application.properties`)

| Property | Default | Meaning |
|----------|---------|---------|
| `blueprint.policy.read-allow` | `src/**,docs/**,pom.xml,README.md` | Readable paths (workspace-relative globs) |
| `blueprint.policy.write-allow` | `src/main/java/**,src/test/java/**,src/main/resources/templates/**` | Paths a proposal may target |
| `blueprint.policy.protected-paths` | secrets, keys, `.git`, `.mvn`, `mvnw` | Always denied; checked first |
| `blueprint.leakage.mode` | `REDACT` | `REDACT` or `BLOCK` for tool output |
| `blueprint.leakage.canaries` | `JFOKUS-CANARY-7f3a9c2e-DO-NOT-SHARE` | Synthetic canary values |
| `blueprint.leakage.restricted-marker` | `AGENT-RESTRICTED` | Content with this marker is always blocked |
| `blueprint.approval.proposal-ttl` / `approval-ttl` | `15m` / `5m` | Approval lifetime |
| `blueprint.sandbox.engine` / `image` | `podman` / `maven:3.9-eclipse-temurin-25` | Restricted execution |
| `blueprint.sandbox.memory` / `cpus` / `pids-limit` / `timeout` | `2g` / `2` / `512` / `240s` | Container limits |

Tokens between the processes (`BLUEPRINT_AGENT_TOKEN`, `BLUEPRINT_APPROVER_TOKEN`) are generated per machine by
`scripts/start.sh` into `.demo/tokens.env` (mode 600).

## OpenCode

[`opencode.jsonc`](../opencode.jsonc) points OpenCode at the same enMaaS gateway and default model, reading the key
from `{env:ENMAAS_KEY}`, so the "Claude Code-style" comparison uses the same private inference.
