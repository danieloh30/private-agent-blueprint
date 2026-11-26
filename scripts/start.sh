#!/usr/bin/env bash
# Start the MCP server and the agent (presenter UI) as two separate processes.
#   scripts/start.sh enterprise  # approved private gateway (default): profiles/enterprise.env (enMaaS) + API key
#   scripts/start.sh local       # loopback model: profiles/local-ollama.env
#   scripts/start.sh scripted    # deterministic fallback, NO inference (clearly labelled)
# Overrides: .demo/model.env (not committed) or BLUEPRINT_MODEL_* variables in your shell.
# Ports: AGENT_PORT (default 8090) and MCP_PORT (default 8091), e.g. to run a second stack.
source "$(dirname "$0")/lib.sh"
PROFILE="${1:-enterprise}"
ensure_tokens
load_profile_env "$PROFILE"
load_model_env
if [[ "$PROFILE" == "enterprise" ]]; then
  keyvar="${BLUEPRINT_MODEL_API_KEY_ENV:-ENMAAS_KEY}"
  export BLUEPRINT_MODEL_API_KEY="${BLUEPRINT_MODEL_API_KEY:-${!keyvar:-}}"
  [[ -n "$BLUEPRINT_MODEL_API_KEY" ]] || { fail "enterprise profile needs \$$keyvar (or BLUEPRINT_MODEL_API_KEY) in your shell"; exit 1; }
fi

for jar in mcp-server agent; do
  if [[ ! -f "$ROOT/$jar/target/quarkus-app/quarkus-run.jar" ]]; then
    say "Building ($jar jar missing)"; (cd "$ROOT" && ./mvnw -B -q package -DskipTests); break
  fi
done

for p in "$MCP_PORT" "$AGENT_PORT"; do
  if port_in_use "$p"; then fail "port $p is in use (scripts/stop.sh, or lsof -iTCP:$p)"; exit 1; fi
done

say "Starting MCP server on 127.0.0.1:$MCP_PORT (workspace tools, policy, sandbox)"
( cd "$ROOT/mcp-server"
  BLUEPRINT_STATE_DIR="$DEMO" BLUEPRINT_FIXTURE_DIR="$ROOT/arcade" BLUEPRINT_ACCEPTANCE_DIR="$ROOT/acceptance" \
  BLUEPRINT_M2_DIR="$DEMO/m2" \
  nohup java -Dquarkus.http.port="$MCP_PORT" -jar target/quarkus-app/quarkus-run.jar > "$DEMO/logs/mcp-server.log" 2>&1 &
  echo $! > "$DEMO/mcp-server.pid" )
wait_http "http://127.0.0.1:$MCP_PORT/" 60 || true

say "Starting agent + presenter UI on 127.0.0.1:$AGENT_PORT (profile: $PROFILE)"
# The agent process gets the model settings; it never gets workspace paths.
( cd "$ROOT/agent"
  unset BLUEPRINT_STATE_DIR BLUEPRINT_FIXTURE_DIR BLUEPRINT_ACCEPTANCE_DIR BLUEPRINT_M2_DIR
  BLUEPRINT_MCP_URL="http://127.0.0.1:$MCP_PORT/mcp" \
  nohup java -Dquarkus.profile="$PROFILE" -Dquarkus.http.port="$AGENT_PORT" -jar target/quarkus-app/quarkus-run.jar > "$DEMO/logs/agent.log" 2>&1 &
  echo $! > "$DEMO/agent.pid" )

if wait_http "$AGENT_URL/api/status" 60; then
  curl -s "$AGENT_URL/api/status" | json '"profile=%s endpoint=%s model=%s workspace=%s sandbox=%s" % (d["profile"], d["endpoint"], d["model"], d["server"].get("workspaceId"), d["server"].get("isolationAvailable"))'
  ok "Presenter UI: $AGENT_URL"
else
  fail "agent did not start; see $DEMO/logs/agent.log"; tail -20 "$DEMO/logs/agent.log"; exit 1
fi
