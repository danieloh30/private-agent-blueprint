#!/usr/bin/env bash
# Stop the demo processes started by scripts/start.sh (and any leftover verification containers).
source "$(dirname "$0")/lib.sh"
for name in agent mcp-server arcade; do
  f="$DEMO/$name.pid"
  if [[ -f "$f" ]]; then
    pid="$(cat "$f")"
    if kill "$pid" 2>/dev/null; then
      for _ in $(seq 1 40); do kill -0 "$pid" 2>/dev/null || break; sleep 0.25; done
      kill -9 "$pid" 2>/dev/null || true
      ok "stopped $name ($pid)"
    fi
    rm -f "$f"
  fi
done
if command -v "$SANDBOX_ENGINE" >/dev/null 2>&1; then
  ids="$($SANDBOX_ENGINE ps -q --filter name=blueprint-verify- 2>/dev/null || true)"
  [[ -n "$ids" ]] && $SANDBOX_ENGINE rm -f $ids >/dev/null && ok "removed leftover verification containers"
fi
true
