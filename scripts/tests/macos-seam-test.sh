#!/usr/bin/env bash
# macos-seam-test.sh — structural test do GATE 2 do #1135 (a integridade da
# costura kof_plat_*): o inventário do ANTES medido é ESTÁVEL — (1) o seam
# RuntimePlat existe e nomeia as funções kof_plat_*; (2) o total x86 de
# sítios syscall é >= o seam (a costura é subconjunto); (3) a costura riscv
# NativeRiscvAsmRt0 existe com ecall. A mudança silenciosa na superfície de
# migração M-1..M-8 (o número do ANTES) fica visível na saída do gate.
set -u
cd "$(git rev-parse --show-toplevel)"
rc=0

OUT="$(bash scripts/check_macos_seam.sh 2>&1)"
echo "$OUT" > /tmp/seam-out.txt

RT=kof-compiler/src/main/java/dev/kof/compiler/runtime
[ -f "$RT/RuntimePlat.java" ] || { echo "FALHOU: RuntimePlat ausente (a costura sumiu?)"; rc=1; }
[ -f kof-compiler/src/main/java/dev/kof/compiler/nat/NativeRiscvAsmRt0.java ] || { echo "FALHOU: NativeRiscvAsmRt0 ausente (a costura riscv sumiu?)"; rc=1; }

X86_LINE="$(printf '%s\n' "$OUT" | grep -E 'x86-64: total=' | head -1)"
RV_LINE="$(printf '%s\n' "$OUT" | grep -E 'riscv64: total=' | head -1)"
[ -n "$X86_LINE" ] || { echo "FALHOU: a contagem x86 ausente na saída"; rc=1; }
[ -n "$RV_LINE" ] || { echo "FALHOU: a contagem riscv ausente na saída"; rc=1; }

X86_TOTAL="$(printf '%s' "$X86_LINE" | grep -oE 'total=[0-9]+' | grep -oE '[0-9]+')"
X86_SEAM="$(printf '%s' "$X86_LINE" | grep -oE 'seam=[0-9]+' | grep -oE '[0-9]+')"
RV_TOTAL="$(printf '%s' "$RV_LINE" | grep -oE 'total=[0-9]+' | grep -oE '[0-9]+')"
[ -n "$X86_TOTAL" ] && [ "$X86_TOTAL" -ge "$X86_SEAM" ] && [ "$X86_SEAM" -gt 0 ] && [ -n "$RV_TOTAL" ] && [ "$RV_TOTAL" -gt 0 ] \
    && grep -q "kof_plat_write" /tmp/seam-out.txt \
    || { echo "FALHOU: o inventário mal-formado ($X86_LINE / $RV_LINE)"; rc=1; }

if [ $rc -eq 0 ]; then
    echo "ok  — seam inventory: x86 total=$X86_TOTAL seam=$X86_SEAM, riscv total=$RV_TOTAL, as kof_plat_* nomeadas"
fi
exit $rc
