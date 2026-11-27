#!/usr/bin/env bash
# Dependency preparation (WITH network), kept separate from the restricted verification run (WITHOUT network).
# It builds the PRISTINE fixture plus the acceptance tests, never an agent-modified workspace,
# and fills .demo/m2, which verification later mounts read-only.
source "$(dirname "$0")/lib.sh"

say "Pulling sandbox image $SANDBOX_IMAGE"
$SANDBOX_ENGINE pull "$SANDBOX_IMAGE" >/dev/null && ok "image ready"

say "Building the blueprint (host)"
(cd "$ROOT" && ./mvnw -B -q package -DskipTests) && ok "jars built"

say "Filling the Maven cache from the pristine fixture (network ON, this step only)"
prep="$DEMO/prep"; rm -rf "$prep"; mkdir -p "$prep" "$DEMO/m2"
cp -R "$ROOT/arcade" "$prep/arcade"; rm -rf "$prep/arcade/target"
mkdir -p "$prep/arcade/src/test/java/acceptance"
cp "$ROOT"/acceptance/src/test/java/acceptance/*.java "$prep/arcade/src/test/java/acceptance/"
$SANDBOX_ENGINE run --rm --cap-drop=ALL --security-opt=no-new-privileges \
  -v "$prep/arcade:/work" -v "$DEMO/m2:/m2" -w /work "$SANDBOX_IMAGE" \
  mvn -B -ntp -Dmaven.repo.local=/m2 -Dmaven.test.failure.ignore=true test \
  2>&1 | tee "$DEMO/logs/prepare-deps.log"
ok "cache ready: $(du -sh "$DEMO/m2" | cut -f1) (acceptance failures here are expected: the fixture still has its bugs)"
rm -rf "$prep"
