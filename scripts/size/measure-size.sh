#!/usr/bin/env bash
#
# measure-size.sh — D-SIZE-BUDGET Phase 1 (measurement only).
#
# Emits a reproducible TAB-separated baseline of the KOF distribution's weight:
#   meta    — commit, branch, date, environment (os/arch/java)
#   module  — each module jar under kof-*/target
#   dist    — the shaded CLI jar, the thin ("original-") jar, and a packed tar.gz
#   dep     — uncompressed bytes attributed per top-level package inside the
#             shaded jar, top 20 (directional attribution, NOT an exact accounting)
#   hello   — the artifact bytes of a hello-world built by the CLI per target
#
# Phase 1 does NOT change behavior, dependencies, packaging or CI. It only reads.
# A target whose toolchain is absent is emitted with value "unavailable" — never
# a fake 0 (no silent fallback).
#
# Usage:
#   scripts/size/measure-size.sh [--out FILE] [--no-hello]
#   scripts/size/measure-size.sh --selftest
#
# Env (tests): SIZE_ROOT (repo root), SIZE_CLI_JAR (override the CLI jar).
set -uo pipefail

ROOT="${SIZE_ROOT:-$(cd "$(dirname "$(readlink -f "$0")")/../.." && pwd)}"
OUT=""
HELLO=1

while [ $# -gt 0 ]; do
    case "$1" in
        --out) OUT="${2:-}"; shift 2;;
        --no-hello) HELLO=0; shift;;
        --selftest) SELFTEST=1; shift;;
        *) echo "uso: $0 [--out FILE] [--no-hello] [--selftest]" >&2; exit 2;;
    esac
done

# ── selftest (guarda do guarda): attribution + compare must have teeth ──────
if [ "${SELFTEST:-0}" = 1 ]; then
    T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
    # a tiny jar with two package trees, built with python's zipfile (no JDK needed)
    python3 - "$T/x.jar" <<'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1], "w") as z:
    z.writestr("org/example/A.class", "a" * 100)
    z.writestr("org/example/B.class", "b" * 50)
    z.writestr("com/other/C.class", "c" * 30)
PY
    out="$(SIZE_ROOT="$T" SIZE_CLI_JAR="$T/x.jar" bash "$0" --no-hello 2>/dev/null)"
    # dep attribution: com = 30, org = 150 (top package `com`/`org`)
    echo "$out" | grep -q "dep	com	30" || { echo "SELFTEST FALHOU: dep com != 30"; exit 1; }
    echo "$out" | grep -q "dep	org	150" || { echo "SELFTEST FALHOU: dep org != 150"; exit 1; }
    # compare must flag a growth
    printf 'x\tv\t1\n' > "$T/a.tsv"; printf 'x\tv\t2\n' > "$T/b.tsv"
    d="$(bash "$(dirname "$0")/compare-size.sh" "$T/a.tsv" "$T/b.tsv" 2>/dev/null)"
    echo "$d" | grep -q $'x\tv\t+1' || { echo "SELFTEST FALHOU: compare nao detectou +1"; exit 1; }
    echo "SELFTEST OK: dep attribution (com=30, org=150) e compare (+1) tem dentes"
    exit 0
fi

emit() { printf '%s\t%s\t%s\n' "$1" "$2" "$3"; }

collect() {
    emit "#" "kof-size-baseline" "v1"
    emit meta commit "$(git -C "$ROOT" rev-parse HEAD 2>/dev/null || echo unknown)"
    emit meta branch "$(git -C "$ROOT" rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"
    emit meta date "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    emit meta os "$(uname -srm 2>/dev/null || echo unknown)"
    emit meta java "$(java -version 2>&1 | head -n1 | tr -d '\n' || echo unknown)"

    # module jars (exclude the shaded/original pair which is the dist section)
    for j in "$ROOT"/kof-*/target/*.jar; do
        [ -f "$j" ] || continue
        case "$(basename "$j")" in original-*) continue;; esac
        [ "$(basename "$j")" = "kof-cli-0.5.0-beta.jar" ] && continue
        emit module "$(basename "$j")" "$(stat -c%s "$j")"
    done

    # distribution: shaded CLI + thin jar + packed tar.gz
    local shaded thin
    shaded="${SIZE_CLI_JAR:-}"
    if [ -z "$shaded" ]; then
        shaded="$(ls "$ROOT"/kof-cli/target/kof-cli-*.jar 2>/dev/null | grep -v original- | head -n1)"
    fi
    thin="$(ls "$ROOT"/kof-cli/target/original-kof-cli-*.jar 2>/dev/null | head -n1)"
    if [ -n "$shaded" ] && [ -f "$shaded" ]; then
        emit dist shaded-cli "$(stat -c%s "$shaded")"
        local tgz; tgz="$(mktemp -u).tar.gz"
        tar -czf "$tgz" -C "$(dirname "$shaded")" "$(basename "$shaded")" 2>/dev/null \
            && emit dist packed-cli.tar.gz "$(stat -c%s "$tgz")"
        rm -f "$tgz"
    fi
    [ -n "$thin" ] && [ -f "$thin" ] && emit dist thin-cli "$(stat -c%s "$thin")"

    # dependency attribution: top-level package -> uncompressed bytes (top 20)
    if [ -n "$shaded" ] && [ -f "$shaded" ] && command -v python3 >/dev/null; then
        python3 - "$shaded" <<'PY' | while IFS=$'\t' read -r pkg bytes; do emit dep "$pkg" "$bytes"; done
import sys, zipfile, collections
tot = collections.Counter()
with zipfile.ZipFile(sys.argv[1]) as z:
    for info in z.infolist():
        name = info.filename
        if name.endswith('/'):
            continue
        top = name.split('/', 1)[0] if '/' in name else '(root)'
        tot[top] += info.file_size
for pkg, b in tot.most_common(20):
    print(f"{pkg}\t{b}")
PY
    fi

    # hello-world per target (artifact bytes); toolchain absent -> "unavailable"
    [ "$HELLO" = 1 ] || return 0
    local hdir; hdir="$(mktemp -d)"
    printf 'main() {\n    println("hi")\n}\n' > "$hdir/Main.kf"
    for t in jvm js native native.risc native.arm; do
        local o="$hdir/out-$t" b
        rm -rf "$o"
        if [ -z "$shaded" ] || [ ! -f "$shaded" ]; then
            emit hello "$t" "unavailable"; continue
        fi
        if java -jar "$shaded" build "$hdir/Main.kf" --target "$t" --output "$o" >/dev/null 2>&1; then
            b="$(find "$o" -type f -printf '%s\n' 2>/dev/null | awk '{s+=$1} END{print s+0}')"
            emit hello "$t" "$b"
        else
            emit hello "$t" "unavailable"
        fi
    done
    rm -rf "$hdir"
}

if [ -n "$OUT" ]; then
    collect > "$OUT"
    echo "measure-size: baseline em $OUT ($(grep -c . "$OUT") linhas)" >&2
else
    collect
fi
