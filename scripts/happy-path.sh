#!/usr/bin/env bash
# Rehearse the primary demo end to end against the running stack (scripts/start.sh first).
#   scripts/happy-path.sh            primary task
#   scripts/happy-path.sh backup     backup task (Word Scramble)
# The script approves proposals AS THE PRESENTER would in the UI, printing each diff first.
source "$(dirname "$0")/lib.sh"
TASK="${1:-primary}"; ACCEPT="acceptance-rps"; [[ "$TASK" == "backup" ]] && ACCEPT="acceptance-scramble"
api() { curl -s -m 600 "$@"; }
t() { python3 -c 'import time; print(time.time())'; }

status="$(api "$AGENT_URL/api/status")" || { fail "agent not running (scripts/start.sh)"; exit 1; }
echo "$status" | json '"profile=%s endpoint=%s model=%s" % (d["profile"], d["endpoint"], d["model"])'
echo "$status" | json '"SCRIPTED FALLBACK - NOT A LIVE MODEL RUN" if d["scripted"] else "live model run"'

say "Fresh workspace"; api -XPOST "$AGENT_URL/api/reset" | json 'd.get("workspaceId")'
say "Baseline: $ACCEPT on the untouched workspace (expected FAILED)"
s0=$(t); api -XPOST "$AGENT_URL/api/verify/$ACCEPT" | json 'd["headline"]'; s1=$(t)

say "Agent: $TASK"
prompt="$(api "$AGENT_URL/api/presets" | python3 -c "import json,sys; print(json.dumps({'text':[p for p in json.load(sys.stdin) if p['id']=='$TASK'][0]['text']}))")"
a0=$(t); api -XPOST "$AGENT_URL/api/ask" -d "$prompt" >/dev/null; sleep 1
while api "$AGENT_URL/api/session?after=0" | python3 -c "import json,sys; sys.exit(0 if json.load(sys.stdin)['running'] else 1)"; do sleep 1; done; a1=$(t)
api "$AGENT_URL/api/session?after=0" | python3 -c "
import json,sys
for e in json.load(sys.stdin)['events']:
    if e['kind'] in ('TOOL','LIMIT','ERROR','INSPECTION','INFO'):
        print('  %-10s %-18s %-5s %s' % (e['kind'], e['title'][:18], e['verdict'] or '', e['rule'] or e['detail'][:60]))"

say "Proposals (presenter review)"
rows="$(api "$AGENT_URL/api/proposals" | python3 -c "
import json,sys
for p in json.load(sys.stdin):
    if p['state']=='PENDING':
        print(p['id']+','+p['patchSha256'])
        sys.stderr.write(p['diff']+'\n')")"
[[ -n "$rows" ]] || { fail "no proposals; see the UI trace. Fallback: scripts/start.sh scripted"; exit 1; }
p0=$(t)
for row in $rows; do api -XPOST "$AGENT_URL/api/proposals/${row%,*}/approve" -d "{\"patchSha256\":\"${row#*,}\"}" | json '"  " + d["result"].splitlines()[0]'; done
p1=$(t)

say "Independent acceptance: $ACCEPT (presenter-owned, sandboxed)"
v0=$(t); result="$(api -XPOST "$AGENT_URL/api/verify/$ACCEPT")"; v1=$(t)
echo "$result" | json 'd["headline"]'
say "Timings"
python3 - "$s0" "$s1" "$a0" "$a1" "$p0" "$p1" "$v0" "$v1" <<'PY'
import sys; v=list(map(float, sys.argv[1:]))
print("  baseline acceptance %5.1fs | agent run %5.1fs | approve+apply %4.1fs | acceptance %5.1fs" % (v[1]-v[0], v[3]-v[2], v[5]-v[4], v[7]-v[6]))
PY
echo "$result" | json '"PASSED" == d["status"]' | grep -q True && ok "happy path complete" || { fail "acceptance did not pass"; exit 1; }
