#!/usr/bin/env bash
# Human promotion through the SAME server-side acceptance/hash/path checks as the console.
# --yes skips only the confirmation prompt, never acceptance or policy.
source "$(dirname "$0")/lib.sh"
ensure_tokens
url="http://127.0.0.1:$MCP_PORT/admin/promote"
preview="$(curl -fsS -m 30 -H "X-Approver-Token: $BLUEPRINT_APPROVER_TOKEN" "$url")" \
  || { fail "MCP server not reachable (scripts/start.sh first)"; exit 1; }
if [[ "$(printf '%s' "$preview" | json "d.get('enabled', False)")" != True ]]; then
  fail "Promotion is disabled on the MCP server"; exit 1
fi
if [[ "$(printf '%s' "$preview" | json "len(d.get('files', []))")" == 0 ]]; then
  warn "No changes to promote"; exit 0
fi
say "Review the workspace changes"
printf '%s' "$preview" | json "d['diff']"
if [[ "$(printf '%s' "$preview" | json "d.get('verified', False)")" != True ]]; then
  fail "Run a passing presenter acceptance check on these exact changes before promotion"; exit 1
fi
sha="$(printf '%s' "$preview" | json "d['diffSha256']")"
if [[ "${1:-}" != "--yes" ]]; then
  read -r -p "Promote this verified diff into arcade/? [y/N] " answer
  [[ "$answer" == y || "$answer" == Y ]] || { warn "Not promoted"; exit 0; }
fi
body="$(python3 -c 'import json,sys; print(json.dumps({"diffSha256":sys.argv[1]}))' "$sha")"
result="$(curl -sS --fail-with-body -m 30 -H "X-Approver-Token: $BLUEPRINT_APPROVER_TOKEN" \
  -H 'Content-Type: application/json' -d "$body" "$url")" \
  || { fail "Promotion refused (workspace changed, acceptance missing, or server busy): $result"; exit 1; }
printf '%s' "$result" | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d.get("reason", "")); [print("copied arcade/"+f) for f in d.get("promoted",[])]; [print("KEPT arcade/"+f+" (local changes)") for f in d.get("conflicts",[])]; sys.exit(1 if d.get("conflicts") else 0)'
ok "Reload the game page; dev mode picks up the promoted files"
