#!/usr/bin/env bash
# Promote the agent's APPROVED changes from the disposable workspace into your arcade/ working tree,
# so a running dev-mode arcade (cd arcade && ../mvnw quarkus:dev) reloads and you can verify the fix
# on the game page. This is a human step: the agent itself never writes outside its workspace.
#   scripts/promote-workspace.sh           show the changes, ask, then copy them into arcade/
#   scripts/promote-workspace.sh --yes     copy without asking (rehearsal)
# Safe to run again after more approvals: already-promoted files are skipped, and a file you changed
# in arcade/ since the run started (for example with OpenCode) is never overwritten.
# Undo: git checkout arcade/
source "$(dirname "$0")/lib.sh"
ensure_tokens

run="$(tr -d '[:space:]' < "$DEMO/current-run" 2>/dev/null || true)"
[[ -n "$run" && -d "$DEMO/runs/$run/workspace" ]] || { fail "no current workspace (scripts/start.sh first)"; exit 1; }
ws="$DEMO/runs/$run/workspace"; base="$DEMO/runs/$run/baseline"

diff="$(curl -s -f -m 30 -H "X-Approver-Token: $BLUEPRINT_APPROVER_TOKEN" "http://127.0.0.1:$MCP_PORT/admin/diff")" \
  || { fail "MCP server not reachable on :$MCP_PORT (scripts/start.sh first)"; exit 1; }
files="$(printf '%s\n' "$diff" | sed -n 's#^+++ b/##p')"
if [[ -z "$files" ]]; then warn "no approved changes in workspace $run yet (approve a proposal first)"; exit 0; fi

say "Approved changes in workspace $run"
printf '%s\n' "$diff"

todo=(); conflicts=0
while IFS= read -r f; do
  [[ -n "$f" ]] || continue
  case "$f" in /*|*..*) fail "unexpected path $f"; exit 1 ;; esac
  target="$ROOT/arcade/$f"
  if [[ -f "$target" ]] && cmp -s "$target" "$ws/$f"; then
    ok "already promoted: $f"
  elif { [[ ! -e "$target" && ! -e "$base/$f" ]]; } || { [[ -f "$target" ]] && cmp -s "$target" "$base/$f"; }; then
    todo+=("$f")
  else
    fail "arcade/$f changed since the run started; not overwriting it (resolve by hand or git checkout arcade/)"
    conflicts=$((conflicts + 1))
  fi
done <<< "$files"

if (( ${#todo[@]} == 0 )); then
  (( conflicts == 0 )) && ok "nothing new to promote" || exit 1
  exit 0
fi
if [[ "${1:-}" != "--yes" ]]; then
  printf 'Copy into arcade/: %s\n' "${todo[*]}"
  read -r -p "Promote these approved changes? [y/N] " answer
  [[ "$answer" == "y" || "$answer" == "Y" ]] || { warn "not promoted"; exit 0; }
fi
for f in "${todo[@]}"; do
  mkdir -p "$(dirname "$ROOT/arcade/$f")"
  cp "$ws/$f" "$ROOT/arcade/$f"
  ok "promoted: arcade/$f"
done
ok "reload the game page (dev mode picks the change up); undo with: git checkout arcade/"
(( conflicts == 0 )) || exit 1
