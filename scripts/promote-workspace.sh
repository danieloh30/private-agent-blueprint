#!/usr/bin/env bash
# Promote the agent's APPROVED changes from the disposable workspace into your arcade/ working tree,
# so a running dev-mode arcade (cd arcade && ../mvnw quarkus:dev) reloads and you can verify the fix
# on the game page. This is a human step: the agent itself never writes outside its workspace.
#   scripts/promote-workspace.sh           show the diff, ask, then apply it to arcade/
#   scripts/promote-workspace.sh --yes     apply without asking (rehearsal)
# Undo: git checkout arcade/
source "$(dirname "$0")/lib.sh"
ensure_tokens
patch="$DEMO/promote.patch"
curl -s -f -m 30 -H "X-Approver-Token: $BLUEPRINT_APPROVER_TOKEN" "http://127.0.0.1:$MCP_PORT/admin/diff" > "$patch" \
  || { fail "MCP server not reachable on :$MCP_PORT (scripts/start.sh first)"; exit 1; }
if [[ ! -s "$patch" ]]; then warn "no applied changes in the current workspace yet (approve a proposal first)"; exit 0; fi
if grep -q '\[REDACTED:' "$patch"; then fail "diff contains redacted content; refusing to promote it"; exit 1; fi

say "Approved changes in the agent workspace"
cat "$patch"
cd "$ROOT"
if ! git apply --check -p1 --directory=arcade "$patch" 2>/dev/null; then
  fail "the diff does not apply to arcade/ (already promoted, or arcade/ changed). Undo with: git checkout arcade/"
  exit 1
fi
if [[ "${1:-}" != "--yes" ]]; then
  read -r -p "Apply these changes to arcade/? [y/N] " answer
  [[ "$answer" == "y" || "$answer" == "Y" ]] || { warn "not applied"; exit 0; }
fi
git apply -p1 --directory=arcade "$patch"
ok "applied to arcade/ ($(git diff --stat -- arcade | tail -1 | sed 's/^ *//'))"
ok "reload the game page (dev mode picks the change up); undo with: git checkout arcade/"
