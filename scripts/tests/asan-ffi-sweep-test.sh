#!/usr/bin/env bash
# asan-ffi-sweep-test.sh — structural test do sweep §7 (a OPERAÇÃO: sanitizers).
# O --selftest do sweep planta o controle de leak; este teste prova os DOIS
# vereditos: (1) a varredura roda e sai bem-formada (as 4 faces + o controle);
# (2) o controle é PEGO (o report LeakSanitizer sai no run.log — nunca um
# falso-verde). O estado REAL depende do gcc+ASan no host (a ausência é um
# skip NOMEADO, o §10).
set -u
cd "$(git rev-parse --show-toplevel)"
rc=0

command -v gcc >/dev/null 2>&1 || { echo "PULO NOMEADO: gcc ausente — a face ASan fica para o host com toolchain (§10)"; exit 0; }
printf 'int main(void) { return 0; }\n' | gcc -fsanitize=address -x c -o /tmp/asan-selftest-probe - 2>/dev/null \
    || { echo "PULO NOMEADO: gcc sem -fsanitize=address (a libasan ausente) — §10"; exit 0; }
rm -f /tmp/asan-selftest-probe

D="$(mktemp -d)"; trap 'rm -rf "$D"' EXIT
if ! OUT="$(ASAN_SWEEP_DIR="$D" bash scripts/asan-ffi-sweep.sh 2>&1)"; then
    echo "FALHOU: a varredura RED:"; echo "$OUT" | tail -12; rc=1
elif ! printf '%s' "$OUT" | grep -q "ASAN-FFI-SWEEP: GREEN"; then
    echo "FALHOU: veredito mal-formado:"; echo "$OUT" | tail -6; rc=1
elif ! printf '%s' "$OUT" | grep -q "CONTROL leak-control: CAUGHT"; then
    echo "FALHOU: o controle de leak NÃO foi pego (falso-verde da varredura):"; echo "$OUT" | tail -6; rc=1
else
    echo "ok  — sweep GREEN: $(printf '%s' "$OUT" | grep -c 'FACE .*: CLEAN') faces CLEAN + o controle CAUGHT (o report LeakSanitizer)"
fi

exit $rc
