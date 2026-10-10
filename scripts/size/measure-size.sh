#!/usr/bin/env bash
#
# measure-size.sh — D-SIZE-BUDGET Phase 1 (measurement only).
#
# Emits a reproducible TAB-separated baseline of the KOF distribution's weight:
#   meta    — commit, branch, date, version, environment (os/arch/java)
#   module  — each of the 5 reactor modules, keyed by module name (F1.1, #725):
#             the exact `<module>/target/<module>-$VERSION.jar` (kof-cli = the
#             thin `original-kof-cli-$VERSION.jar`); a missing one is
#             "unavailable" and the run exits non-zero — never omitted
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
# Format v2 (F1.1): `module` keys are module names, not jar file names, so
# compare-size.sh diffs the same module across versions. The historical v1
# baseline (jar-name keys) is kept as is; v1 x v2 module rows show gone/new.
#
# Env (tests): SIZE_ROOT (repo root), SIZE_CLI_JAR (override the CLI jar).
set -uo pipefail

# the reactor modules the size budget measures, in stable order; checked
# against the root pom.xml <modules> on every run (drift = error)
EXPECTED_MODULES=(kof-compiler kof-cli kof-runtime kof-script kof-c-compiler)

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

# ── selftest (guarda do guarda): F1.1 inventory + attribution + compare ─────
if [ "${SELFTEST:-0}" = 1 ]; then
    T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
    V=9.9.9-test
    fail() { echo "SELFTEST FALHOU: $*"; exit 1; }
    mk() { mkdir -p "$(dirname "$1")"; head -c "$2" /dev/zero > "$1"; }
    # fixture: VERSION + root pom with exactly the 5 modules + the exact jars
    fixture() {
        local r="$1"; rm -rf "$r"; mkdir -p "$r"
        echo "$V" > "$r/VERSION"
        { echo "<project><modules>"
          for m in "${EXPECTED_MODULES[@]}"; do echo "        <module>$m</module>"; done
          echo "</modules></project>"; } > "$r/pom.xml"
        mk "$r/kof-compiler/target/kof-compiler-$V.jar" 100
        mk "$r/kof-runtime/target/kof-runtime-$V.jar" 50
        mk "$r/kof-script/target/kof-script-$V.jar" 30
        mk "$r/kof-c-compiler/target/kof-c-compiler-$V.jar" 40
        mk "$r/kof-cli/target/original-kof-cli-$V.jar" 20
        # shaded CLI: a real zip with two package trees (dep attribution)
        python3 - "$r/kof-cli/target/kof-cli-$V.jar" <<'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1], "w") as z:
    z.writestr("org/example/A.class", "a" * 100)
    z.writestr("org/example/B.class", "b" * 50)
    z.writestr("com/other/C.class", "c" * 30)
PY
    }
    run() { SIZE_ROOT="$1" bash "$0" --no-hello 2>"$T/err"; }

    # A + E — valid set, VERSION is not 0.5.0-beta: rc 0, 5 module rows by name
    fixture "$T/a"; out="$(run "$T/a")"; rc=$?
    [ "$rc" -eq 0 ] || fail "A: conjunto valido rc=$rc ($(cat "$T/err"))"
    [ "$(echo "$out" | grep -c '^module')" -eq 5 ] || fail "A: esperava 5 linhas module"
    for m in kof-compiler:100 kof-cli:20 kof-runtime:50 kof-script:30 kof-c-compiler:40; do
        echo "$out" | grep -qx "module	${m%%:*}	${m##*:}" || fail "A: falta module ${m%%:*} ${m##*:}"
    done
    echo "$out" | grep -qx "meta	version	$V" || fail "A: falta meta version $V"
    echo "$out" | grep '^module' | grep -q "$V" && fail "A: versao vazou na chave module"
    # attribution: com = 30, org = 150 (top package of the shaded jar)
    echo "$out" | grep -qx "dep	com	30" || fail "dep com != 30"
    echo "$out" | grep -qx "dep	org	150" || fail "dep org != 150"

    # C — kof-cli module is the THIN jar; the shaded one stays in dist
    echo "$out" | grep -qx "dist	thin-cli	20" || fail "C: dist thin-cli != 20"
    shaded="$(stat -c%s "$T/a/kof-cli/target/kof-cli-$V.jar")"
    echo "$out" | grep -qx "dist	shaded-cli	$shaded" || fail "C: dist shaded-cli != $shaded"

    # B — stale runtime must not mask the missing current one; sources/extra stay out
    fixture "$T/b"
    rm "$T/b/kof-runtime/target/kof-runtime-$V.jar"
    mk "$T/b/kof-runtime/target/kof-runtime-0.5.0-beta.jar" 55
    mk "$T/b/kof-compiler/target/kof-compiler-$V-sources.jar" 7
    mk "$T/b/kof-extra/target/kof-extra-$V.jar" 11
    out="$(run "$T/b")"; rc=$?
    [ "$rc" -ne 0 ] || fail "B: modulo ausente terminou rc=0"
    echo "$out" | grep -qx "module	kof-runtime	unavailable" || fail "B: kof-runtime nao saiu unavailable"
    [ "$(echo "$out" | grep -c '^module')" -eq 5 ] || fail "B: relatorio incompleto (esperava 5 module)"
    echo "$out" | grep '^module' | grep -q '	55$' && fail "B: runtime stale (55) foi medido"
    echo "$out" | grep -q -e sources -e kof-extra && fail "B: sources/extra entraram"
    grep -q "required module artifact missing" "$T/err" || fail "B: sem diagnostico do artefato ausente"

    # D — pom drift: an extra module, or a removed one, is an error
    fixture "$T/d"; sed -i 's#</modules>#    <module>kof-extra</module></modules>#' "$T/d/pom.xml"
    run "$T/d" >/dev/null; rc=$?
    [ "$rc" -ne 0 ] || fail "D: drift do pom (modulo extra) rc=0"
    grep -q "module-set drift" "$T/err" || fail "D: sem 'module-set drift'"
    fixture "$T/d2"; sed -i '/<module>kof-script<\/module>/d' "$T/d2/pom.xml"
    run "$T/d2" >/dev/null; rc=$?
    [ "$rc" -ne 0 ] || fail "D: modulo removido do pom rc=0"

    # VERSION missing / invalid -> explicit failure
    fixture "$T/v"; rm "$T/v/VERSION"
    run "$T/v" >/dev/null; rc=$?
    [ "$rc" -ne 0 ] || fail "VERSION ausente rc=0"
    grep -q "VERSION not found" "$T/err" || fail "VERSION ausente sem diagnostico"
    echo "1.0 beta" > "$T/v/VERSION"
    run "$T/v" >/dev/null; rc=$?
    [ "$rc" -ne 0 ] || fail "VERSION invalido rc=0"
    grep -q "invalid VERSION" "$T/err" || fail "VERSION invalido sem diagnostico"

    # compare must flag a growth of the same semantic key across versions
    printf 'module\tkof-compiler\t100\n' > "$T/a.tsv"; printf 'module\tkof-compiler\t110\n' > "$T/b.tsv"
    d="$(bash "$(dirname "$0")/compare-size.sh" "$T/a.tsv" "$T/b.tsv" 2>/dev/null)"
    echo "$d" | grep -q $'module\tkof-compiler\t+10' || fail "compare nao detectou +10"
    echo "SELFTEST OK: inventario F1.1 (5 modulos, stale/sources/extra fora, ausente=unavailable+rc!=0, drift do pom, VERSION), dep (com=30, org=150), compare (+10)"
    exit 0
fi

emit() { printf '%s\t%s\t%s\n' "$1" "$2" "$3"; }

# VERSION is the single source of the artifact version (same rule as
# scripts/bump-version.sh); absent or invalid -> explicit failure
VERSION_FILE="$ROOT/VERSION"
if [ ! -f "$VERSION_FILE" ]; then
    echo "size: VERSION not found: $VERSION_FILE" >&2; exit 1
fi
VERSION="$(tr -d '\r\n' < "$VERSION_FILE")"
case "$VERSION" in
    *[!0-9a-zA-Z.-]*|""|"."|"-") echo "size: invalid VERSION: '$VERSION'" >&2; exit 1;;
esac

# root pom.xml <modules> must be exactly EXPECTED_MODULES (count + order):
# a new or dropped module would otherwise become a blind spot
read_pom_modules() {
    awk '/<modules>/,/<\/modules>/' "$ROOT/pom.xml" 2>/dev/null \
        | sed -n 's:.*<module>\([^<]*\)</module>.*:\1:p'
}
POM_MODULES="$(read_pom_modules | tr '\n' ' ' | sed 's/ $//')"
if [ "$POM_MODULES" != "${EXPECTED_MODULES[*]}" ]; then
    { echo "size: module-set drift"
      echo "expected: ${EXPECTED_MODULES[*]}"
      echo "actual:   $POM_MODULES"; } >&2
    exit 1
fi

# exact artifact of a module for this VERSION (kof-cli = the thin jar)
module_jar_path() {
    case "$1" in
        kof-cli) printf '%s\n' "$ROOT/kof-cli/target/original-kof-cli-$VERSION.jar";;
        *)       printf '%s\n' "$ROOT/$1/target/$1-$VERSION.jar";;
    esac
}

collect() {
    emit "#" "kof-size-baseline" "v2"
    emit meta commit "$(git -C "$ROOT" rev-parse HEAD 2>/dev/null || echo unknown)"
    emit meta branch "$(git -C "$ROOT" rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"
    emit meta date "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    emit meta version "$VERSION"
    emit meta os "$(uname -srm 2>/dev/null || echo unknown)"
    emit meta java "$(java -version 2>&1 | head -n1 | tr -d '\n' || echo unknown)"

    # module jars: the expected inventory, exact path per VERSION; a missing
    # artifact is reported "unavailable" and the run ends non-zero (R6)
    local incomplete=0 module jar
    for module in "${EXPECTED_MODULES[@]}"; do
        jar="$(module_jar_path "$module")"
        if [ -f "$jar" ]; then
            emit module "$module" "$(stat -c%s "$jar")"
        else
            emit module "$module" "unavailable"
            echo "size: required module artifact missing: $jar" >&2
            incomplete=1
        fi
    done

    # distribution: shaded CLI + thin jar + packed tar.gz
    local shaded thin
    shaded="${SIZE_CLI_JAR:-$ROOT/kof-cli/target/kof-cli-$VERSION.jar}"
    thin="$(module_jar_path kof-cli)"
    if [ -n "$shaded" ] && [ -f "$shaded" ]; then
        emit dist shaded-cli "$(stat -c%s "$shaded")"
        local tgz; tgz="$(mktemp -u).tar.gz"
        tar -czf "$tgz" -C "$(dirname "$shaded")" "$(basename "$shaded")" 2>/dev/null \
            && emit dist packed-cli.tar.gz "$(stat -c%s "$tgz")"
        rm -f "$tgz"
    fi
    [ -f "$thin" ] && emit dist thin-cli "$(stat -c%s "$thin")"

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
    [ "$HELLO" = 1 ] || return "$incomplete"
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
    return "$incomplete"
}

if [ -n "$OUT" ]; then
    collect > "$OUT"; rc=$?
    echo "measure-size: baseline em $OUT ($(grep -c . "$OUT") linhas)" >&2
else
    collect; rc=$?
fi
[ "$rc" -eq 0 ] || echo "measure-size: medicao INCOMPLETA (modulo obrigatorio ausente)" >&2
exit "$rc"
