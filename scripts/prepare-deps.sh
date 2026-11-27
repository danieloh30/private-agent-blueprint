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
# Native Linux bind mounts retain host ownership. Match it instead of granting
# the container capabilities to override directory permissions.
identity_args=(-e HOME=/tmp)
if [[ "$(uname -s)" == Linux ]]; then
  identity_args+=(--user "$(id -u):$(id -g)")
  [[ "$SANDBOX_ENGINE" != podman ]] || identity_args+=(--userns=keep-id)
fi
$SANDBOX_ENGINE run --rm "${identity_args[@]}" --cap-drop=ALL --security-opt=no-new-privileges \
  -e MAVEN_CONFIG=/tmp/.m2 -e MAVEN_OPTS=-Duser.home=/tmp \
  -v "$prep/arcade:/work" -v "$DEMO/m2:/m2" -w /work "$SANDBOX_IMAGE" \
  mvn -B -ntp -Dmaven.repo.local=/m2 -Dmaven.test.failure.ignore=true test \
  2>&1 | tee "$DEMO/logs/prepare-deps.log"
ok "cache ready: $(du -sh "$DEMO/m2" | cut -f1) (acceptance failures here are expected: the fixture still has its bugs)"
rm -rf "$prep"
