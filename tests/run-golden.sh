#!/bin/bash
# Kof Golden Tests — compiles each case for every requested target, runs it,
# and compares stdout against the expected output AND the exit code.
#
# Layout:
#   tests/golden/<case>/Main.kf        — the program
#   tests/golden/<case>/expected.txt   — expected stdout
#
# Targets (default: all):
#   jvm     `kof build --target jvm`    then `java -cp <out> Default.Main`
#   native  `kof build --target native` then `<out>/Default/Main`
#   js      `kof build --target js`     then `node <out>/Default.mjs`
#   script  `kof run --target script`   (direct IR interpretation)
#
# External tools are guarded honestly (R6): a target whose runtime is absent is
# SKIPPED with the reason, never silently passed.
#
# Usage:
#   tests/run-golden.sh [--target jvm|native|js|script]... [case...]
set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION="$(cat "$ROOT/VERSION")"
KOF_JAR="$ROOT/kof-cli/target/kof-cli-$VERSION.jar"
GOLDEN_DIR="$ROOT/tests/golden"

ALL_TARGETS=(jvm native js script)
REQUESTED_TARGETS=()
CASES=()
while [ $# -gt 0 ]; do
    case "$1" in
        --target) REQUESTED_TARGETS+=("$2"); shift ;;
        --target=*) REQUESTED_TARGETS+=("${1#--target=}") ;;
        -*) echo "uso: tests/run-golden.sh [--target jvm|native|js|script]... [case...]" >&2; exit 2 ;;
        *) CASES+=("$1") ;;
    esac
    shift
done
if [ ${#REQUESTED_TARGETS[@]} -eq 0 ]; then
    REQUESTED_TARGETS=("${ALL_TARGETS[@]}")
fi

if [ ! -f "$KOF_JAR" ]; then
    echo "ERROR: jar not found: $KOF_JAR"
    echo "Run: mvn clean install -DskipTests"
    exit 1
fi

has() { command -v "$1" >/dev/null 2>&1; }

PASS=0
FAIL=0
SKIP=0

run_case_target() {
    local case_dir="$1" case_name="$2" target="$3"
    local out_dir output="" ec=0
    out_dir="$(mktemp -d)"

    if [ "$target" = "script" ]; then
        output="$(java -jar "$KOF_JAR" run --target script "$case_dir/Main.kf" 2>&1)" && ec=0 || ec=$?
    else
        if ! java -jar "$KOF_JAR" build "$case_dir" --target "$target" --output "$out_dir" >/dev/null 2>&1; then
            echo "FAIL [$case_name/$target] compilation"
            FAIL=$((FAIL + 1)); rm -rf "$out_dir"; return
        fi
        case "$target" in
            jvm)
                output="$(java -cp "$out_dir" Default.Main 2>&1)" && ec=0 || ec=$? ;;
            native)
                if [ -x "$out_dir/Default/Main" ]; then
                    output="$("$out_dir/Default/Main" 2>&1)" && ec=0 || ec=$?
                else
                    echo "FAIL [$case_name/$target] binary not found"
                    FAIL=$((FAIL + 1)); rm -rf "$out_dir"; return
                fi ;;
            js)
                output="$(node "$out_dir/Default.mjs" 2>&1)" && ec=0 || ec=$? ;;
        esac
    fi
    rm -rf "$out_dir"

    local expected; expected="$(cat "$case_dir/expected.txt")"
    if [ "$output" = "$expected" ] && [ "$ec" -eq 0 ]; then
        echo "PASS [$case_name/$target]"
        PASS=$((PASS + 1))
    else
        echo "FAIL [$case_name/$target] exit=$ec"
        echo "  expected: $expected"
        echo "  got:      $output"
        FAIL=$((FAIL + 1))
    fi
}

for case_dir in "$GOLDEN_DIR"/*/; do
    case_name="$(basename "$case_dir")"
    if [ ${#CASES[@]} -gt 0 ]; then
        want=0
        for c in "${CASES[@]}"; do
            if [ "$c" = "$case_name" ]; then want=1; fi
        done
        if [ "$want" -eq 0 ]; then continue; fi
    fi
    for target in "${REQUESTED_TARGETS[@]}"; do
        case "$target" in
            jvm|script) ;;
            native)
                if ! has as || ! has ld; then
                    echo "SKIP [$case_name/native] binutils (as/ld) ausente"
                    SKIP=$((SKIP + 1)); continue
                fi ;;
            js)
                if ! has node; then
                    echo "SKIP [$case_name/js] node ausente"
                    SKIP=$((SKIP + 1)); continue
                fi ;;
            *) echo "ERROR: alvo desconhecido: $target" >&2; exit 2 ;;
        esac
        echo "--- [$case_name] $target ---"
        run_case_target "$case_dir" "$case_name" "$target"
    done
done

echo ""
echo "=== GOLDEN TESTS: $PASS passed, $FAIL failed, $SKIP skipped ==="
[ "$FAIL" -eq 0 ]
