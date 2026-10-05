#!/bin/bash
# Kof Integration Tests — exercises the real CLI end-to-end:
#   kof build (jvm + native), kof run, kof check, kof serve (HTTP round trip).
set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="$(cat "$ROOT/VERSION")"
KOF_JAR="$ROOT/kof-cli/target/kof-cli-$VERSION.jar"
WORK="$(mktemp -d)"
PASS=0
FAIL=0

pass() { echo "PASS: $1"; PASS=$((PASS + 1)); }
fail() { echo "FAIL: $1"; FAIL=$((FAIL + 1)); }

trap 'rm -rf "$WORK"' EXIT

if [ ! -f "$KOF_JAR" ]; then
    echo "ERROR: jar not found: $KOF_JAR"
    exit 1
fi

# ── fixture ────────────────────────────────────────────────────────
mkdir -p "$WORK/src" "$WORK/srv"
cat > "$WORK/src/Main.kf" <<'EOF'
Int soma(Int a, Int b) {
    return a + b
}
main() {
    println("integration:" + soma(20, 22))
}
EOF

cat > "$WORK/srv/server.kf" <<'EOF'
handle(String method, String path, String body): String {
    if (path == "/ping") {
        return "{\"pong\": true}"
    }
    return null
}
EOF

echo "=== kof integration tests ==="

# ── kof build --target jvm ─────────────────────────────────────────
if java -jar "$KOF_JAR" build "$WORK/src" --target jvm --output "$WORK/jvm" >/dev/null 2>&1 \
        && [ -f "$WORK/jvm/Default/Main.class" ]; then
    pass "build jvm"
else
    fail "build jvm"
fi

# ── kof build --target native ──────────────────────────────────────
if java -jar "$KOF_JAR" build "$WORK/src" --target native --output "$WORK/native" >/dev/null 2>&1 \
        && [ -x "$WORK/native/Default/Main" ]; then
    pass "build native"
else
    fail "build native"
fi

# ── kof run (jvm) ──────────────────────────────────────────────────
OUTPUT=$(java -jar "$KOF_JAR" run "$WORK/src/Main.kf" 2>/dev/null || true)
if [ "$OUTPUT" = "integration:42" ]; then
    pass "run"
else
    fail "run (got: $OUTPUT)"
fi

# ── native binary output ───────────────────────────────────────────
OUTPUT=$("$WORK/native/Default/Main")
if [ "$OUTPUT" = "integration:42" ]; then
    pass "native output"
else
    fail "native output (got: $OUTPUT)"
fi

# ── kof build --target js (guarded: node may be absent) ────────────
if command -v node >/dev/null 2>&1; then
    if java -jar "$KOF_JAR" build "$WORK/src" --target js --output "$WORK/js" >/dev/null 2>&1 \
            && [ -f "$WORK/js/Default.mjs" ]; then
        pass "build js"
    else
        fail "build js"
    fi
    JS_OUTPUT=$(node "$WORK/js/Default.mjs" 2>/dev/null || true)
    if [ "$JS_OUTPUT" = "integration:42" ]; then
        pass "js output"
    else
        fail "js output (got: $JS_OUTPUT)"
    fi
else
    echo "SKIP: build js / js output (node ausente)"
fi

# ── kof run --target script (direct IR interpretation) ─────────────
SCRIPT_OUTPUT=$(java -jar "$KOF_JAR" run --target script "$WORK/src/Main.kf" 2>/dev/null || true)
if [ "$SCRIPT_OUTPUT" = "integration:42" ]; then
    pass "run script"
else
    fail "run script (got: $SCRIPT_OUTPUT)"
fi

# ── kof check ──────────────────────────────────────────────────────
if java -jar "$KOF_JAR" check "$WORK/src/Main.kf" >/dev/null 2>&1; then
    pass "check (valid file)"
else
    fail "check (valid file)"
fi

cat > "$WORK/src/broken.kf" <<'EOF'
main() {
    println(undefinedVar)
}
EOF
if java -jar "$KOF_JAR" check "$WORK/src/broken.kf" >/dev/null 2>&1; then
    fail "check (broken file should fail)"
else
    pass "check (broken file rejected)"
fi

# ── kof serve (HTTP round trip) ────────────────────────────────────
PORT=8877
java -jar "$KOF_JAR" serve "$WORK/srv/server.kf" --port $PORT >"$WORK/serve.log" 2>&1 &
SERVER_PID=$!
sleep 6

PING=$(curl -s --max-time 3 "http://127.0.0.1:$PORT/ping" || true)
if [ "$PING" = '{"pong": true}' ]; then
    pass "serve /ping"
else
    fail "serve /ping (got: $PING)"
fi

STATUS=$(curl -s --max-time 3 -o /dev/null -w "%{http_code}" "http://127.0.0.1:$PORT/missing" || true)
if [ "$STATUS" = "404" ]; then
    pass "serve 404"
else
    fail "serve 404 (got status: $STATUS)"
fi

kill $SERVER_PID 2>/dev/null || true
wait $SERVER_PID 2>/dev/null || true

# ── kof test (assert suite) ────────────────────────────────────────
mkdir -p "$WORK/tests"
cat > "$WORK/tests/math.kf" <<'EOF'
main() {
    assert(2 + 2 == 4)
    assert("kof" == "kof")
    println("math ok")
}
EOF
cat > "$WORK/tests/broken.kf" <<'EOF'
main() {
    assert(1 + 1 == 3)
}
EOF

TEST_OUTPUT=$(java -jar "$KOF_JAR" test "$WORK/tests" --target jvm 2>&1 || true)
if echo "$TEST_OUTPUT" | grep -q "1 passed, 1 failed"; then
    pass "kof test (pass + fail)"
else
    fail "kof test (got: $TEST_OUTPUT)"
fi

echo ""
echo "=== INTEGRATION TESTS: $PASS passed, $FAIL failed ==="
[ "$FAIL" -eq 0 ]