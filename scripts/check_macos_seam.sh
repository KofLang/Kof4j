#!/usr/bin/env bash
# check_macos_seam.sh — o GATE 2 do #1135 (D-NATIVE-MACOS, mantenedora
# 17/11): a INTEGRIDADE DA COSTURA kof_plat_* — todo acesso a sistema passa
# pela costura antes do perfil MACOS existir. O gate CONTA os sítios diretos
# de syscall/ecall por arquivo de runtime e nomeia a superfície de migração
# dos estágios M-1..M-8.
#
# MODO RATCHET (19/11, lane issues/tooling): a issue pede um gate que
# "ratchets it to zero" — o CONTROLE era só informativo (rc=0 sempre), então
# um acesso a sistema novo FORA da costura passava despercebido. Agora os
# números medidos são o BASELINE (scripts/macos-seam-baseline.txt) e o gate
# FALHA (rc=1) quando QUALQUER um CRESCE:
#   - fora-da-costura x86-64 (syscall) e riscv64 (ecall) — a superfície que
#     os estágios M-1..M-8 migram para a costura/libSystem;
#   - os sítios %fs:/@tpoff (a face ELF local-exec que o macOS troca por TLV);
#   - os sítios futex (que o macOS troca por __ulock/os_unfair_lock).
# DIMINUIR é sempre verde (o split feito é o objetivo) — o baseline é
# regenerável com `./scripts/check_macos_seam.sh --update-baseline`.
# O gate roda em LINUX (o inventário é o ANTES); o M-7 executa em runner macOS.
#
# Uso: scripts/check_macos_seam.sh [--update-baseline] [--selftest]
set -uo pipefail
cd "$(git rev-parse --show-toplevel 2>/dev/null || echo .)"

ROOT="${MACOS_SEAM_ROOT:-$(pwd)}"
RT="$ROOT/kof-compiler/src/main/java/dev/kof/compiler/runtime"
NAT="$ROOT/kof-compiler/src/main/java/dev/kof/compiler/nat"
BASELINE="${MACOS_SEAM_BASELINE:-$ROOT/scripts/macos-seam-baseline.txt}"
SEAM_X86="$RT/RuntimePlat.java"
SEAM_X86_MACOS="$RT/RuntimePlatMacos.java"
SEAM_RV="$NAT/NativeRiscvAsmRt0.java"

echo "== x86-64: sítios 'syscall' por arquivo (o seam = RuntimePlat + a face macOS RuntimePlatMacos) =="
total_x86=0
seam_x86=0
for f in "$RT"/*.java; do
    n=$(grep -cE '\bsyscall\b' "$f" 2>/dev/null || true)
    [ "$n" -eq 0 ] && continue
    base=$(basename "$f")
    total_x86=$((total_x86 + n))
    case "$base" in
    RuntimePlat*)
        # a COSTURA (M-2/M-4/M-6): RuntimePlat + as faces macOS por família
        # (RuntimePlatMacos, RuntimePlatMacosNet, RuntimePlatMacosTls). São a
        # superfície da migração — não dívida fora dela.
        seam_x86=$((seam_x86 + n))
        echo "  SEAM  $base: $n"
        ;;
    *)
        echo "  out   $base: $n"
        ;;
    esac
done
out_x86=$((total_x86 - seam_x86))
echo "  x86-64: total=$total_x86 seam=$seam_x86 fora-da-costura=$out_x86"

echo "== riscv64: sítios 'ecall' (o seam = NativeRiscvAsmRt0) =="
total_rv=0
seam_rv=0
for f in "$NAT"/NativeRiscv*.java; do
    [ -f "$f" ] || continue
    n=$(grep -cE '\becall\b' "$f" 2>/dev/null || true)
    [ "$n" -eq 0 ] && continue
    base=$(basename "$f")
    total_rv=$((total_rv + n))
    if [ "$base" = "NativeRiscvAsmRt0.java" ]; then
        seam_rv=$n
        echo "  SEAM  NativeRiscvAsmRt0.java: $n"
    else
        echo "  out   $base: $n"
    fi
done
out_rv=$((total_rv - seam_rv))
echo "  riscv64: total=$total_rv seam=$seam_rv fora-da-costura=$out_rv"

echo "== a costura (as funções kof_plat_*):"
grep -ohE 'kof_plat_[a-z_0-9]+:' "$SEAM_X86" "$SEAM_X86_MACOS" 2>/dev/null \
    | sed 's/:$//' | sort -u | sed 's/^/  /'

echo "== TLS (a face ELF local-exec — a migração Mach-O TLV):"
tls=$(grep -rcE '@tpoff|%fs:' "$RT"/*.java 2>/dev/null | awk -F: '{s+=$2} END {print s+0}')
echo "  sítios %fs:/@tpoff: $tls"

echo "== FUTEX (a face Linux — o macOS usa __ulock/os_unfair_lock):"
# M-5 (#1135): a COSTURA (RuntimePlat + a face macOS RuntimePlatMacos) é a
# superfície da migração — os futex dela são os que viram __ulock, então não
# contam como "sítio fora da costura" (o mesmo critério do syscall acima).
fx=0
for f in "$RT"/*.java; do
    base=$(basename "$f")
    case "$base" in RuntimePlat*) continue ;; esac
    n=$(grep -cE '\bfutex\b' "$f" 2>/dev/null || true)
    fx=$((fx + n))
done
echo "  sítios futex: $fx"

# ---------------------------------------------------------------------
# RATCHET: os números medidos são o baseline; CRESCER falha.
# ---------------------------------------------------------------------
current=$(printf 'x86_out_seam\t%s\nriscv_out_seam\t%s\ntls_sites\t%s\nfutex_sites\t%s\n' \
    "$out_x86" "$out_rv" "$tls" "$fx")


# ---------------------------------------------------------------------
# SELFTEST (guarda do guarda): prova que o ratchet distingue
# CRESCER (falha) de DIMINUIR/IGUAL (verde), num sandbox que não toca o
# repo real.
# ---------------------------------------------------------------------
if [ "${1:-}" = "--selftest" ]; then
    T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
    mkdir -p "$T/kof-compiler/src/main/java/dev/kof/compiler/runtime"
    mkdir -p "$T/kof-compiler/src/main/java/dev/kof/compiler/nat"
    RT="$T/kof-compiler/src/main/java/dev/kof/compiler/runtime"
    NAT="$T/kof-compiler/src/main/java/dev/kof/compiler/nat"
    SEAM_X86="$RT/RuntimePlat.java"
    SEAM_X86_MACOS="$RT/RuntimePlatMacos.java"
    SEAM_RV="$NAT/NativeRiscvAsmRt0.java"
    mk_syscalls() {  # $1=file $2=count
        : > "$1"
        local i=0
        while [ "$i" -lt "$2" ]; do echo '  syscall' >> "$1"; i=$((i+1)); done
    }
    mk_ecalls() {
        : > "$1"
        local i=0
        while [ "$i" -lt "$2" ]; do echo '  ecall' >> "$1"; i=$((i+1)); done
    }
    run() {  # roda o gate no sandbox com um baseline dado
        printf '%s\n' "$1" > "$T/base.txt"
        MACOS_SEAM_BASELINE="$T/base.txt" MACOS_SEAM_ROOT="$T" bash "$0" >/dev/null 2>&1
        echo $?
    }
    mk_syscalls "$RT/RuntimePlat.java" 2
    mk_syscalls "$RT/RuntimeOther.java" 3
    mk_ecalls "$NAT/NativeRiscvAsmRt0.java" 1
    mk_ecalls "$NAT/NativeRiscvAsmOther.java" 4
    # 1. baseline igual ao medido -> verde
    [ "$(run "$(printf 'x86_out_seam\t3\nriscv_out_seam\t4\ntls_sites\t0\nfutex_sites\t0')")" = 0 ] \
        || { echo "SELFTEST FALHOU: igual ao baseline devia passar"; exit 1; }
    # 2. baseline MENOR que o medido (crescimento) -> falha
    [ "$(run "$(printf 'x86_out_seam\t2\nriscv_out_seam\t4\ntls_sites\t0\nfutex_sites\t0')")" = 1 ] \
        || { echo "SELFTEST FALHOU: crescimento devia falhar"; exit 1; }
    # 3. baseline MAIOR que o medido (melhora) -> verde
    [ "$(run "$(printf 'x86_out_seam\t9\nriscv_out_seam\t9\ntls_sites\t9\nfutex_sites\t9')")" = 0 ] \
        || { echo "SELFTEST FALHOU:baseline maior devia passar (a divisao diminuiu)"; exit 1; }
    echo "check_macos_seam: selftest OK (3/3 — igual verde, crescimento VERMELHO, melhora verde)."
    exit 0
fi

if [ "${1:-}" = "--update-baseline" ]; then
    printf '%s\n' "$current" > "$BASELINE"
    echo "check_macos_seam: baseline atualizado ($(printf '%s\n' "$current" | tr '\n' ' '))."
    exit 0
fi

if [ ! -f "$BASELINE" ]; then
    echo "check_macos_seam: FALHOU — baseline da costura ausente ($BASELINE)."
    echo "  crie-o com: ./scripts/check_macos_seam.sh --update-baseline"
    exit 1
fi

growth=0
while IFS=$'\t' read -r key limit; do
    [ -n "$key" ] || continue
    now=$(printf '%s\n' "$current" | awk -v k="$key" -F'\t' '$1==k {print $2}')
    [ -n "$now" ] || continue
    if [ "$now" -gt "$limit" ]; then
        echo "check_macos_seam: FALHOU — '$key' CRESCEU ($limit -> $now)."
        echo "  a costura kof_plat_* é a superfície que os estágios M-1..M-8 migram;"
        echo "  um acesso a sistema novo fora dela quebra o port para o macOS."
        growth=1
    elif [ "$now" -lt "$limit" ]; then
        echo "  $key: $now (melhor que o baseline $limit — regenere o baseline)"
    fi
done < "$BASELINE"

if [ "$growth" != 0 ]; then
    exit 1
fi

echo "SEAM-CHECK: rc=0 — a costura não cresceu (o inventário é o ANTES da migração M-1..M-8)"
exit 0
