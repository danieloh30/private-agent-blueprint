#!/usr/bin/env bash
# Deterministic guardrail probes (NO model involved): fixed requests through the real MCP + policy path.
source "$(dirname "$0")/lib.sh"
declare -a expect=(
  "secret:read.protected-path"
  "traversal:path.traversal"
  "canary:read.permitted-source"
  "restricted:leakage.blocked"
  "command:exec.not-a-named-task"
  "unknown-tool:agent.tool-not-allowed"
  "unapproved-write:write.apply-needs-approval")
failures=0
for e in "${expect[@]}"; do
  name="${e%%:*}"; want="${e#*:}"
  r="$(curl -s -m 60 -XPOST "$AGENT_URL/api/probes/$name")"
  got="$(echo "$r" | json 'd.get("rule")')"; verdict="$(echo "$r" | json 'd.get("verdict")')"; leak="$(echo "$r" | json 'd.get("leakage") or ""')"
  if [[ "$got" == "$want" ]]; then ok "$(printf '%-17s %-5s %-28s %s' "$name" "$verdict" "$got" "$leak")"
  else fail "$name: expected $want, got $got"; failures=$((failures + 1)); fi
done
(( failures == 0 )) || exit 1
