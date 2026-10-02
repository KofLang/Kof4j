#!/usr/bin/env bash
#
# target-matrix.sh — PROPOSAL-1.0-EXIT-GATE §13/§14 / fila §23 item 10
# ("final target matrix"), a frente EG-5 do D-RELEASE-1.0.
#
# Um comando, no dia do RC, roda o MESMO programa Kof nos 8 alvos da
# superfície Stable 1.0 e prova a paridade byte-a-byte onde o contrato exige:
#
#   core (paridade obrigatória, oráculo = JVM):
#     jvm · native (x86-64) · native.riscv64 · native.aarch64 · js · script
#   gates próprios (delegados, nunca silenciosos — R6):
#     kofc    → EG-9  (gate próprio)
#     android → EG-10 (gate próprio, CI roda o APK)
#
# Honestidade (R6/R7): ferramenta ausente NUNCA vira verde falso.
#   - toolchain de build ausente num alvo core  → FAIL (o RC exige a matriz);
#   - qemu ausente para EXECUTAR o cross        → SKIP honesto → INCOMPLETE (rc=2);
#   - KofC/Android                              → linha DELEGATED nomeando o gate.
# Só o veredito `PASS` (rc=0) satisfaz a §8 para este item.
#
# Uso:
#   scripts/target-matrix.sh                 # usa o bin/kof da árvore
#   scripts/target-matrix.sh --dist DIR      # usa o pacote (o objeto do RC)
#   scripts/target-matrix.sh --keep          # não apaga a sandbox
#   scripts/target-matrix.sh --selftest      # RED-first offline (sem compilar)
#
# rc: 0 PASS · 1 FAIL · 2 INCOMPLETE (ferramenta de execução ausente) · 3 ambiente
#     (sem JDK 25, kof ausente, ou jar da árvore anterior à fonte = artefato velho).
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SELFTEST=false; KEEP=false; DIST_DIR=""
WORK_ROOT="${KOF_MATRIX_HOME:-$HOME}"
while [ $# -gt 0 ]; do
    case "$1" in
        --dist) DIST_DIR="$2"; shift ;;
        --work) WORK_ROOT="$2"; shift ;;
        --keep) KEEP=true ;;
        --selftest) SELFTEST=true ;;
        *) echo "uso: $0 [--dist DIR] [--work DIR] [--keep] [--selftest]" >&2; exit 2 ;;
    esac
    shift
done

# ── escopo da matriz (D-PARITY-050-SCOPE) ──────────────────────────────────
# Fonte UNICA dos alvos core: os lacos da matriz real e o --selftest leem as
# MESMAS listas. O selftest congela o conjunto em SEIS alvos; mudar o conjunto
# exige nova decisao da mantenedora (MCU/riscv32 ficam fora deste gate).
DIRECT_CORE_TARGETS=(script js native)
CROSS_CORE_ARCHES=(riscv64 aarch64)

release_050_targets() {
    printf '%s\n' jvm "${DIRECT_CORE_TARGETS[@]}"
    local arch
    for arch in "${CROSS_CORE_ARCHES[@]}"; do
        printf 'native.%s\n' "$arch"
    done
}

# ── utilidades de veredito ─────────────────────────────────────────────────
FAILURES=""; SKIPS=""
note() { echo "matrix: $*"; }
fail() { FAILURES="$FAILURES
  - $*"; note "FAIL: $*"; }
skip() { SKIPS="$SKIPS
  - $*"; note "SKIP: $*"; }

# paridade byte-a-byte contra o oráculo (comparador testável, RED-first no
# selftest): iguais → 0; divergente → 1 com as duas saídas nomeadas.
parity() { # rotulo oraculo alvo
    local label="$1" oracle="$2" target="$3"
    if cmp -s "$oracle" "$target"; then note "parity ok: $label"; return 0; fi
    fail "$label: stdout divergente do oraculo JVM — $(diff <(cat "$oracle") <(cat "$target") | head -6 | tr '\n' ' ')"
    return 1
}

# qemu_ld_prefix <arch> -> imprime o prefixo do loader ou nada.
qemu_ld_prefix() { # riscv64|aarch64
    local arch="$1" loader root
    case "$arch" in
        riscv64) loader=ld-linux-riscv64-lp64d.so.1 ;;
        aarch64) loader=ld-linux-aarch64.so.1 ;;
        *) return 0 ;;
    esac
    for root in "${KOF_CROSS_SYSROOT:-}" /usr /; do
        [ -n "$root" ] || continue
        if [ -e "$root/$arch-linux-gnu/lib/$loader" ]; then echo "$root/$arch-linux-gnu"; return 0; fi
        if [ -e "$root/usr/$arch-linux-gnu/lib/$loader" ]; then echo "$root/usr/$arch-linux-gnu"; return 0; fi
    done
    return 0
}

# source_hash <srcdir>... -> sha256 do CONTEUDO das fontes (independe de mtime).
# Ordena os caminhos (-z, nome nulo) antes de hashear: o resultado nao depende
# da ordem que o find devolve.
source_hash() {
    find "$@" -name '*.java' -type f -print0 2>/dev/null \
        | LC_ALL=C sort -z \
        | xargs -0 -r sha256sum 2>/dev/null \
        | sha256sum | cut -d' ' -f1
}

# jar_stamp <jar> -> caminho do sidecar gravado no build (mtime do jar + hash
# das fontes). Sem sidecar, a guarda cai na heuristica de mtime (conservadora).
jar_stamp() { printf '%s.stamp\n' "$1"; }

# jar_stale <jar> <srcdir>... -> imprime a CAUSA se o jar NAO corresponde ao
# tip, ou nada se corresponde. Guarda de honestidade (R6): o bin/kof da arvore
# roda lib/kof.jar; a matriz nao pode medir um binario fantasma (PARITY falso).
#   - com `lib/kof.jar.stamp` (gravado por scripts/build-kof-jar.sh no build):
#     certifica por CONTEUDO — hash das fontes + mtime do jar. Um rebase/checkout
#     que so REESCREVE mtimes (conteudo igual) deixa de acusar artefato velho.
#   - sem stamp: heuristica antiga por mtime (qualquer fonte mais nova = stale).
# Testavel no selftest.
jar_stale() {
    local jar="$1"; shift
    [ -f "$jar" ] || return 0
    local stamp; stamp="$(jar_stamp "$jar")"
    if [ -f "$stamp" ]; then
        local s_mtime s_hash j_mtime
        s_mtime="$(sed -n '1p' "$stamp")"
        s_hash="$(sed -n '2p' "$stamp")"
        j_mtime="$(stat -c %Y "$jar" 2>/dev/null || stat -f %m "$jar" 2>/dev/null)"
        if [ -n "$s_hash" ] && [ "$s_hash" = "$(source_hash "$@")" ] && [ "$s_mtime" = "$j_mtime" ]; then
            return 0
        fi
        printf 'lib/kof.jar.stamp desatualizado (hash das fontes ou mtime do jar mudou desde o build)'
        return 0
    fi
    find "$@" -name '*.java' -newer "$jar" 2>/dev/null | head -1
}

# ── selftest RED-first (offline: sem compilar, sem tocar a árvore) ─────────
if [ "$SELFTEST" = true ]; then
    ST="$(mktemp -d)"; trap 'rm -rf "$ST"' EXIT
    # D-PARITY-050-SCOPE: o conjunto de alvos core e exatamente estes seis
    expected_targets="jvm script js native native.riscv64 native.aarch64"
    actual_targets="$(release_050_targets | tr '\n' ' ' | sed 's/ $//')"
    [ "$actual_targets" = "$expected_targets" ] || {
        echo "SELFTEST FAIL: D-PARITY-050-SCOPE drift: expected [$expected_targets], got [$actual_targets]"; exit 1
    }
    target_count="$(release_050_targets | wc -l | tr -d ' ')"
    printf 'a\nb\n' > "$ST/oracle"; printf 'a\nb\n' > "$ST/same"; printf 'a\nX\n' > "$ST/diff"
    FAILURES=""
    if ! parity "controle-igual" "$ST/oracle" "$ST/same" >/dev/null 2>&1; then
        echo "SELFTEST FAIL: comparador reprovou saidas iguais (falso vermelho)"; exit 1
    fi
    FAILURES=""
    if parity "controle-divergente" "$ST/oracle" "$ST/diff" >/dev/null 2>&1; then
        echo "SELFTEST FAIL: comparador aceitou saida divergente (falso verde)"; exit 1
    fi
    [ -n "$FAILURES" ] || { echo "SELFTEST FAIL: divergencia nao registrou FAIL"; exit 1; }
    # prefixo de qemu inexistente nao pode inventar caminho
    if p="$(qemu_ld_prefix archnenhuma)" && [ -n "$p" ]; then
        echo "SELFTEST FAIL: qemu_ld_prefix inventou prefixo '$p'"; exit 1
    fi
    # guarda de artefato velho: jar anterior a fonte = stale (nao mede); posterior = fresco
    mkdir -p "$ST/src"
    printf 'x\n' > "$ST/jar"; sleep 1; printf 'y\n' > "$ST/src/A.java"
    [ -n "$(jar_stale "$ST/jar" "$ST/src")" ] || { echo "SELFTEST FAIL: jar velho nao foi detectado como stale"; exit 1; }
    sleep 1; touch "$ST/jar"
    [ -z "$(jar_stale "$ST/jar" "$ST/src")" ] || { echo "SELFTEST FAIL: jar fresco acusado como stale (falso vermelho)"; exit 1; }
    # com stamp: certifica por CONTEUDO (hash das fontes + mtime do jar)
    stamp_w() { { stat -c %Y "$1" 2>/dev/null || stat -f %m "$1"; source_hash "$2"; } > "$(jar_stamp "$1")"; }
    printf 'x\n' > "$ST/jar"; printf 'y\n' > "$ST/src/A.java"; stamp_w "$ST/jar" "$ST/src"
    [ -z "$(jar_stale "$ST/jar" "$ST/src")" ] || { echo "SELFTEST FAIL: stamp fresco acusado como stale (falso vermelho)"; exit 1; }
    # caso real do rebase: mtime novo, CONTEUDO igual -> continua fresco
    sleep 1; touch "$ST/src/A.java"
    [ -z "$(jar_stale "$ST/jar" "$ST/src")" ] || { echo "SELFTEST FAIL: rebase (mtime novo, conteudo igual) virou stale"; exit 1; }
    # fonte realmente alterada -> stale
    printf 'z\n' > "$ST/src/A.java"
    [ -n "$(jar_stale "$ST/jar" "$ST/src")" ] || { echo "SELFTEST FAIL: fonte alterada nao foi detectada como stale (stamp)"; exit 1; }
    # jar reconstruido sem atualizar o stamp -> stale
    stamp_w "$ST/jar" "$ST/src"; sleep 1; touch "$ST/jar"
    [ -n "$(jar_stale "$ST/jar" "$ST/src")" ] || { echo "SELFTEST FAIL: jar refeito sem atualizar o stamp nao foi detectado"; exit 1; }
    rm -f "$(jar_stamp "$ST/jar")"
    echo "SELFTEST: ok — D-PARITY-050-SCOPE=$target_count targets; comparador reprova divergencia, aceita igualdade, sem prefixo falso, staleness por mtime e por stamp (rebase nao acusa falso)"
    exit 0
fi

# ── pré-condições honestas ─────────────────────────────────────────────────
command -v java >/dev/null 2>&1 || { echo "matrix: SEM java no PATH (o kof exige JDK 25) — ambiente, nao bug" >&2; exit 3; }
JMAJOR="$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d. -f1)"
[ "${JMAJOR:-0}" -ge 25 ] 2>/dev/null || { echo "matrix: java $JMAJOR no PATH (o kof exige 25) — ambiente" >&2; exit 3; }

if [ -n "$DIST_DIR" ]; then
    KOF="$DIST_DIR/bin/kof"
else
    KOF="$ROOT/bin/kof"
fi
[ -x "$KOF" ] || { echo "matrix: kof nao executavel em $KOF" >&2; exit 3; }

# ── guarda de artefato velho (R6) ──────────────────────────────────────────
# Sem --dist, o bin/kof da arvore roda lib/kof.jar. Se a fonte for mais nova, o
# jar NAO corresponde ao tip e a matriz mediria um binario fantasma (foi assim
# que um jar de 02:03, anterior ao fix do #550/§371, produziu PARITY 0% falso no
# cross: prune do slice DB ausente -> -lsqlite3 forcado). Recusa com causa
# nomeada em vez de mentir; `--dist` de uma dist fresca e o caminho correto.
if [ -z "$DIST_DIR" ] && [ "${KOF_MATRIX_ALLOW_STALE:-0}" != "1" ]; then
    stale="$(jar_stale "$ROOT/lib/kof.jar" "$ROOT"/kof-*/src/main 2>/dev/null)"
    if [ -n "$stale" ]; then
        echo "matrix: ARTEFATO VELHO — lib/kof.jar nao corresponde a fonte: $stale" >&2
        echo "matrix: o bin/kof da arvore mediria um binario que nao corresponde ao tip (PARITY falso)." >&2
        echo "matrix: reconstrua com scripts/build-kof-jar.sh (builda + copia + grava o stamp) ou use --dist de uma dist fresca." >&2
        echo "matrix: override consciente: KOF_MATRIX_ALLOW_STALE=1 (nao recomendado — mede artefato velho)." >&2
        exit 3
    fi
fi

# ── sandbox FORA do repo (regra 9: nunca /tmp) ─────────────────────────────
SANDBOX="$(mktemp -d "$WORK_ROOT/.kof-matrix.XXXXXX")"
cleanup() { [ "$KEEP" = false ] && rm -rf "$SANDBOX"; return 0; }
trap cleanup EXIT
cd "$SANDBOX" || exit 3
case "$PWD" in "$ROOT"*) echo "matrix: IMPOSSIVEL — sandbox dentro do repo" >&2; exit 3;; esac

cat > G.kf <<'KF'
main() {
    println("matrix:start")
    var total = 0
    for (var i in listOf(1, 2, 3, 4)) { total = total + i }
    println("sum=" + total)
    println("Hello, Kof!")
    println("matrix:end")
}
KF
GOLDEN="G.kf"

# roda um alvo core e grava o stdout em $2; rc do kof em $?
run_core() { # target outfile
    local t="$1" out="$2"
    "$KOF" run "$GOLDEN" --target "$t" >"$out" 2>"$out.err"
}

# ── core: JVM é o oráculo; os demais têm de bater byte-a-byte ──────────────
ORACLE="jvm.out"
if ! run_core jvm "$ORACLE"; then fail "jvm (oraculo): $(tail -2 "$ORACLE.err" | tr '\n' ' ')"; fi
if ! grep -q "matrix:end" "$ORACLE"; then fail "jvm: oraculo sem o marcador final (saida: $(tr '\n' ' ' <"$ORACLE"))"; fi

for t in "${DIRECT_CORE_TARGETS[@]}"; do
    case "$t" in
        js)     command -v node >/dev/null 2>&1 || { fail "js: sem node no PATH"; continue; } ;;
        native) { command -v as >/dev/null 2>&1 && command -v ld >/dev/null 2>&1; } || { fail "native(x86_64): sem as/ld no PATH"; continue; } ;;
    esac
    if run_core "$t" "$t.out"; then
        parity "$t" "$ORACLE" "$t.out"
    else
        fail "$t: run falhou — $(tail -2 "$t.out.err" | tr '\n' ' ')"
    fi
done

# cross: build com a toolchain; exec sob qemu (paridade) ou skip honesto.
for arch in "${CROSS_CORE_ARCHES[@]}"; do
    t="native.$arch"; tc="${arch}-linux-gnu-as"
    if ! command -v "$tc" >/dev/null 2>&1; then fail "$t: sem $tc (toolchain de build obrigatoria)"; continue; fi
    if ! "$KOF" build "$GOLDEN" --target "$t" >"$t.build" 2>&1; then
        fail "$t: build falhou — $(tail -2 "$t.build" | tr '\n' ' ')"; continue
    fi
    elf="build/classes/Default/Main"
    [ -f "$elf" ] || { fail "$t: build nao produziu $elf"; continue; }
    cp "$elf" "$arch.elf"
    if ! command -v "qemu-$arch" >/dev/null 2>&1; then
        skip "$t: build OK, sem qemu-$arch para EXECUTAR (matrix INCOMPLETE no RC)"
        continue
    fi
    pfx="$(qemu_ld_prefix "$arch")"
    if [ -n "$pfx" ]; then
        QEMU_LD_PREFIX="$pfx" "qemu-$arch" "$arch.elf" >"$t.out" 2>"$t.out.err"
    else
        "qemu-$arch" "$arch.elf" >"$t.out" 2>"$t.out.err"
    fi
    if [ $? -ne 0 ]; then fail "$t: exec sob qemu falhou — $(tail -2 "$t.out.err" | tr '\n' ' ')"; continue; fi
    parity "$t" "$ORACLE" "$t.out"
done

# ── gates próprios (EG-9/EG-10): nunca silenciosos ─────────────────────────
note "DELEGATED kofc    → EG-9 (gate próprio; ver scripts/ e CI kof-c)"
note "DELEGATED android → EG-10 (gate próprio; CI android.yml roda o APK)"

# ── veredito ───────────────────────────────────────────────────────────────
# Linha de paridade machine-readable (consumida pelo gate 0.5.0 / §14):
#   PARITY: 100%   → todos os alvos core batem o oráculo JVM
#   PARITY: 0%     → há divergência (FAIL)
#   PARITY: unknown→ não certificou (ferramenta de execução ausente)
if [ -n "$FAILURES" ]; then
    echo "TARGET-MATRIX: FAIL — alvos fora do contrato:" >&2
    echo "$FAILURES" >&2
    echo "PARITY: 0%"
    exit 1
fi
if [ -n "$SKIPS" ]; then
    echo "TARGET-MATRIX: INCOMPLETE — execucao ausente (nao certifica o RC):" >&2
    echo "$SKIPS" >&2
    echo "PARITY: unknown"
    exit 2
fi
echo "PARITY: 100%"
echo "TARGET-MATRIX: PASS — jvm/x86_64/riscv64/aarch64/js/script com paridade byte-a-byte (oraculo JVM); kofc=EG-9, android=EG-10"
exit 0
