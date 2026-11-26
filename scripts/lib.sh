#!/usr/bin/env bash
# Shared helpers for the demo scripts. Everything these scripts create lives under .demo/ (gitignored).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEMO="$ROOT/.demo"
AGENT_PORT="${AGENT_PORT:-8090}"
MCP_PORT="${MCP_PORT:-8091}"
ARCADE_PORT="${ARCADE_PORT:-8080}"
AGENT_URL="http://127.0.0.1:$AGENT_PORT"
SANDBOX_ENGINE="${BLUEPRINT_SANDBOX_ENGINE:-podman}"
SANDBOX_IMAGE="${BLUEPRINT_SANDBOX_IMAGE:-docker.io/library/maven:3.9-eclipse-temurin-25}"

mkdir -p "$DEMO/logs" "$DEMO/evidence"

say()  { printf '\033[1m==> %s\033[0m\n' "$*"; }
ok()   { printf '  \033[32mOK\033[0m   %s\n' "$*"; }
warn() { printf '  \033[33mWARN\033[0m %s\n' "$*"; }
fail() { printf '  \033[31mFAIL\033[0m %s\n' "$*"; }

# Per-machine random tokens for agent->MCP and presenter->MCP admin. Never committed, never model-visible.
ensure_tokens() {
  local f="$DEMO/tokens.env"
  if [[ ! -f "$f" ]]; then
    umask 077
    {
      echo "BLUEPRINT_AGENT_TOKEN=$(openssl rand -hex 24)"
      echo "BLUEPRINT_APPROVER_TOKEN=$(openssl rand -hex 24)"
    } > "$f"
  fi
  set -a; source "$f"; set +a
}

# Optional local overrides (model name, endpoint, etc.): .demo/model.env, see docs/configuration.md
load_model_env() {
  if [[ -f "$DEMO/model.env" ]]; then set -a; source "$DEMO/model.env"; set +a; fi
}

# Committed, credential-free defaults per profile; shell variables win.
load_profile_env() {
  local f=""
  case "$1" in
    enterprise) f="$ROOT/${BLUEPRINT_ENTERPRISE_ENV:-profiles/enterprise.env}" ;;
    local) f="$ROOT/profiles/local-ollama.env" ;;
  esac
  [[ -n "$f" && -f "$f" ]] || return 0
  while IFS='=' read -r k v; do
    [[ -z "$k" || "$k" == \#* ]] && continue
    [[ -z "${!k:-}" ]] && export "$k=$v"
  done < "$f"
}

port_in_use() { lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1; }

wait_http() { # url, seconds
  local url="$1" t="${2:-60}"
  for ((i = 0; i < t * 2; i++)); do
    if curl -s -o /dev/null -m 2 "$url"; then return 0; fi
    sleep 0.5
  done
  return 1
}

json() { python3 -c "import json,sys; d=json.load(sys.stdin); print(eval(sys.argv[1], {'d': d}))" "$1"; }
