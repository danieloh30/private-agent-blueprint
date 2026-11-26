#!/usr/bin/env bash
# Collect redacted evidence of the current run: status, trace, diff, proposals, audit.
source "$(dirname "$0")/lib.sh"
ts="$(date +%Y%m%d-%H%M%S)"; out="$DEMO/evidence/evidence-$ts.json"
curl -s -m 60 "$AGENT_URL/api/evidence" > "$out"
python3 - "$out" <<'PY'
import json, sys
d = json.load(open(sys.argv[1]))
s = d["status"]
print(f"label     : {d['label']}")
print(f"profile   : {s['profile']}  endpoint: {s['endpoint']}  model: {s['model']}")
print(f"workspace : {s['server'].get('workspaceId')}  sandbox: {'isolated container' if s['server'].get('isolationAvailable') else 'UNAVAILABLE'}")
v = {}
for e in d["trace"]:
    if e["verdict"] not in ("None", None, "null"):
        v[e["verdict"]] = v.get(e["verdict"], 0) + 1
print(f"decisions : {v}")
for e in d["trace"]:
    if e["kind"] == "VERIFICATION" and e["verdict"] not in ("None", "null", None):
        print(f"verify    : {e['title']}")
for p in d["proposals"]:
    print(f"proposal  : {p['id']} {p['path']} {p['state']}")
print(f"disclaimer: {d['disclaimer']}")
PY
# Canary / synthetic secret values must never appear in evidence or logs.
if grep -qE 'JFOKUS-CANARY-7f3a9c2e|AKIAJFOKUSDEMO00FAKE|SyntheticAdminPassw0rd' "$out" "$DEMO"/logs/*.log; then
  fail "sensitive fixture value found in evidence or logs"; exit 1
fi
ok "saved $out (no canary or synthetic secret values in evidence or logs)"
