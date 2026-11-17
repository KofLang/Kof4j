#!/usr/bin/env bash
# check_macos_seam.sh — o GATE 2 do #1135 (D-NATIVE-MACOS, mantenedora
# 17/11): a INTEGRIDADE DA COSTURA kof_plat_* — todo acesso a sistema passa
# pela costura antes do perfil MACOS existir. O gate CONTA os sítios diretos
# de syscall/ecall por arquivo de runtime e nomeia a superfície de migração
# dos estágios M-1..M-8. INFORMATIVO (rc=0 sempre — o inventário é o
# deliverable; o corte por ele não é o contrato, o M-7 executa em runner
# macOS).
set -u
cd "$(git rev-parse --show-toplevel 2>/dev/null || echo .)"

RT=kof-compiler/src/main/java/dev/kof/compiler/runtime
NAT=kof-compiler/src/main/java/dev/kof/compiler/nat
SEAM_X86="$RT/RuntimePlat.java"
SEAM_RV="$NAT/NativeRiscvAsmRt0.java"

echo "== x86-64: sítios 'syscall' por arquivo (o seam = RuntimePlat) =="
total_x86=0
seam_x86=0
for f in "$RT"/*.java; do
    n=$(grep -cE '\bsyscall\b' "$f" 2>/dev/null || true)
    [ "$n" -eq 0 ] && continue
    base=$(basename "$f")
    total_x86=$((total_x86 + n))
    if [ "$base" = "RuntimePlat.java" ]; then
        seam_x86=$n
        echo "  SEAM  RuntimePlat.java: $n"
    else
        echo "  out   $base: $n"
    fi
done
out_x86=$((total_x86 - seam_x86))
echo "  x86-64: total=$total_x86 seam=$seam_x86 fora-da-costura=$out_x86"

echo "== riscv64: sítios 'ecall' (o seam = NativeRiscvAsmRt0) =="
total_rv=0
seam_rv=0
for f in "$NAT"/NativeRiscv*.java "$NAT"/NativeRiscv*.java; do
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
grep -oE 'kof_plat_[a-z_0-9]+:' "$SEAM_X86" | sed 's/:$//' | sort -u | sed 's/^/  /'

echo "== TLS (a face ELF local-exec — a migração Mach-O TLV):"
tls=$(grep -rcE '@tpoff|%fs:' "$RT"/*.java 2>/dev/null | awk -F: '{s+=$2} END {print s+0}')
echo "  sítios %fs:/@tpoff: $tls"

echo "== FUTEX (a face Linux — o macOS usa __ulock/os_unfair_lock):"
fx=$(grep -rcE '\bfutex\b' "$RT"/*.java 2>/dev/null | awk -F: '{s+=$2} END {print s+0}')
echo "  sítios futex: $fx"

echo "SEAM-CHECK: rc=0 — o inventário é o deliverable (a migração M-1..M-8 usa estes números como o ANTES)"
exit 0
