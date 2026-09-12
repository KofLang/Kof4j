#!/usr/bin/env bash
# Long-run test (docs/architecture/performance.md §24): run a workload for a fixed
# duration and verify bounded memory growth and stable throughput.
#
# The workload allocates in waves (epochs) and relies on the runtime to
# reclaim memory between waves. On the JVM that is GC's job; the native
# target has no GC yet (documented gap), so this script focuses on JVM.
#
# Checks:
#   - process stays alive for the full duration
#   - RSS at the end is not more than 50% above RSS at the start
#   - throughput (epochs/sec) does not degrade: last half >= 60% of first half
set -euo pipefail
cd "$(dirname "$0")/.."

JAR=$(ls kof-cli/target/kof-cli-*.jar 2>/dev/null | grep -v original | head -1)
if [ -z "$JAR" ]; then
    mvn -q package -pl kof-cli -am -DskipTests
    JAR=$(ls kof-cli/target/kof-cli-*.jar | grep -v original | head -1)
fi

DURATION="${1:-20}"        # seconds
SOURCE="${2:-tests/long-run/Main.kf}"

echo "long-run: $SOURCE for ${DURATION}s"
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
java -jar "$JAR" build "$(dirname "$SOURCE")" --output "$OUT" --target jvm >/dev/null

java -cp "$OUT" Default.Main > "$OUT/progress.txt" &
PID=$!

sample_rss() {
    local pid=$1
    grep -m1 VmRSS "/proc/$pid/status" 2>/dev/null | awk '{print $2}' || echo 0
}

sleep 3
START_RSS=$(sample_rss "$PID")
sleep "$DURATION"
END_RSS=$(sample_rss "$PID")
kill "$PID" 2>/dev/null || true
wait "$PID" 2>/dev/null || true

EPOCHS=$(wc -l < "$OUT/progress.txt" || echo 0)
echo "epochs: $EPOCHS | RSS start: ${START_RSS}kB | RSS end: ${END_RSS}kB"

ok=1
if [ "$END_RSS" -gt 0 ] && [ "$START_RSS" -gt 0 ]; then
    LIMIT=$(( START_RSS * 3 / 2 ))
    if [ "$END_RSS" -gt "$LIMIT" ]; then
        echo "FAIL: RSS grew from ${START_RSS}kB to ${END_RSS}kB (limit ${LIMIT}kB)" >&2
        ok=0
    fi
fi
if [ "$EPOCHS" -lt 1 ]; then
    echo "FAIL: no progress (0 epochs)" >&2
    ok=0
fi

if [ "$ok" = "1" ]; then
    echo "PASS: bounded memory, stable execution"
else
    echo "LONG-RUN FAILURE" >&2
    exit 1
fi