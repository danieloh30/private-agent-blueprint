#!/usr/bin/env bash
# Run the PRISTINE arcade fixture on http://localhost:8080 to show the bug (trusted repository code).
#   scripts/arcade-baseline.sh        start
#   scripts/arcade-baseline.sh stop
source "$(dirname "$0")/lib.sh"
if [[ "${1:-}" == "stop" ]]; then
  [[ -f "$DEMO/arcade.pid" ]] && kill "$(cat "$DEMO/arcade.pid")" 2>/dev/null && rm -f "$DEMO/arcade.pid" && ok "arcade stopped"; exit 0
fi
port_in_use "$ARCADE_PORT" && { fail "port $ARCADE_PORT in use"; exit 1; }
(cd "$ROOT" && ./mvnw -B -q -f arcade/pom.xml package -DskipTests)
( cd "$ROOT/arcade"
  nohup java -jar target/quarkus-app/quarkus-run.jar > "$DEMO/logs/arcade.log" 2>&1 &
  echo $! > "$DEMO/arcade.pid" )
wait_http "http://localhost:$ARCADE_PORT/" 30 && ok "arcade: http://localhost:$ARCADE_PORT/rps?move=banana (shows a loss: the bug)"
