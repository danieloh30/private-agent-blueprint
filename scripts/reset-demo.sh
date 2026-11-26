#!/usr/bin/env bash
# Reset ONLY disposable demo state under .demo/ (runs, logs, evidence). Never touches git or your working tree.
# Keeps the prepared Maven cache (.demo/m2) and the generated tokens.
source "$(dirname "$0")/lib.sh"
"$ROOT/scripts/stop.sh"
case "$DEMO" in */.demo) ;; *) fail "refusing: unexpected demo dir $DEMO"; exit 1 ;; esac
rm -rf "$DEMO/runs" "$DEMO/logs" "$DEMO/evidence" "$DEMO/prep" "$DEMO/current-run"
mkdir -p "$DEMO/logs" "$DEMO/evidence"
ok "demo state reset (kept .demo/m2 and .demo/tokens.env)"
