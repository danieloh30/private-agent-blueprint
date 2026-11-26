#!/usr/bin/env bash
# Pre-flight check: Java, build, container isolation, dependency cache, ports, model endpoint and model.
#   scripts/doctor.sh [enterprise|local|scripted] [--models]
source "$(dirname "$0")/lib.sh"
PROFILE="${1:-enterprise}"; LIST_MODELS="${2:-}"
load_profile_env "$PROFILE"; load_model_env
problems=0; bad() { fail "$*"; problems=$((problems + 1)); }

say "Java and build"
if java -version 2>&1 | grep -qE 'version "(2[5-9]|[3-9][0-9])'; then ok "$(java -version 2>&1 | head -1)"; else bad "Java 25+ required"; fi
[[ -x "$ROOT/mvnw" ]] && ok "Maven wrapper present" || bad "mvnw missing"
for m in mcp-server agent; do
  [[ -f "$ROOT/$m/target/quarkus-app/quarkus-run.jar" ]] && ok "$m built" || warn "$m not built yet (scripts/start.sh builds it)"
done

say "Restricted execution (sandbox)"
if command -v "$SANDBOX_ENGINE" >/dev/null 2>&1 && $SANDBOX_ENGINE info >/dev/null 2>&1; then
  ok "$SANDBOX_ENGINE available"
  if $SANDBOX_ENGINE image exists "$SANDBOX_IMAGE" 2>/dev/null || $SANDBOX_ENGINE image inspect "$SANDBOX_IMAGE" >/dev/null 2>&1; then
    ok "image $SANDBOX_IMAGE present"
    out="$($SANDBOX_ENGINE run --rm --network=none --cap-drop=ALL --pull=never "$SANDBOX_IMAGE" bash -c 'exec 3<>/dev/tcp/1.1.1.1/443 && echo EGRESS_OPEN || echo EGRESS_BLOCKED' 2>/dev/null | tail -1)"
    [[ "$out" == "EGRESS_BLOCKED" ]] && ok "network=none blocks egress inside the sandbox" || bad "egress probe: '$out'"
  else bad "image $SANDBOX_IMAGE missing (scripts/prepare-deps.sh)"; fi
else
  bad "$SANDBOX_ENGINE not usable: verification will FAIL CLOSED (no host fallback). macOS: podman machine start"
fi
[[ -d "$DEMO/m2/io/quarkus" ]] && ok "prepared Maven cache: $(du -sh "$DEMO/m2" | cut -f1)" || bad "dependency cache missing (scripts/prepare-deps.sh)"

say "Ports"
for p in "$AGENT_PORT" "$MCP_PORT" "$ARCADE_PORT"; do
  if port_in_use "$p"; then warn "port $p in use ($(lsof -nP -iTCP:$p -sTCP:LISTEN | awk 'NR==2{print $1" pid "$2}'))"; else ok "port $p free"; fi
done

say "Model ($PROFILE)"
if [[ "$PROFILE" == "scripted" ]]; then
  ok "scripted fallback: no endpoint, no inference (always labelled in the UI)"
else
  base="${BLUEPRINT_MODEL_BASE_URL:-}"; model="${BLUEPRINT_MODEL_NAME:-}"
  [[ -n "$base" ]] || bad "BLUEPRINT_MODEL_BASE_URL not set"
  [[ -n "$model" ]] || bad "BLUEPRINT_MODEL_NAME not set"
  auth=()
  if [[ "$PROFILE" == "enterprise" ]]; then
    keyvar="${BLUEPRINT_MODEL_API_KEY_ENV:-ENMAAS_KEY}"; key="${BLUEPRINT_MODEL_API_KEY:-${!keyvar:-}}"
    [[ -n "$key" ]] && ok "API key found in \$$keyvar (${#key} chars, not shown)" || bad "API key missing: export $keyvar"
    auth=(-H "Authorization: Bearer $key")
  fi
  if [[ -n "$base" ]]; then
    host="$(python3 -c "import sys,urllib.parse; print(urllib.parse.urlparse(sys.argv[1]).hostname)" "$base")"
    ok "endpoint host: $host"
    models="$(curl -s -m 15 ${auth[@]+"${auth[@]}"} "$base/models" || true)"
    ids="$(printf '%s' "$models" | python3 -c "import json,sys; print('\n'.join(m['id'] for m in json.load(sys.stdin).get('data', [])))" 2>/dev/null || true)"
    if [[ -z "$ids" ]]; then bad "cannot list models at $host (unreachable or unauthorized)"
    elif grep -qxF "$model" <<<"$ids"; then ok "model '$model' is available"
    else bad "model '$model' not offered by $host"; fi
    [[ "$LIST_MODELS" == "--models" && -n "$ids" ]] && sed 's/^/      /' <<<"$ids"
  fi
fi

echo
if (( problems == 0 )); then ok "ready"; else fail "$problems problem(s)"; exit 1; fi
