#!/usr/bin/env bash
# skip-inventory-test.sh — structural test do gate §10 (a OPERAÇÃO: "não
# permita skips silenciosos"). Red-first como os vizinhos: o --selftest do
# gate planta os quatro casos de veredito (o assumeTrue 1-arg silencioso RED,
# o @Disabled nu RED, os dois nomeados — literal e concatenação — GREEN) e
# prova que a recusa tem dentes com identificação file:line. O estado REAL do
# repo tem que rc=0 (o inventário cobre os 5 módulos e NENHUM skip silencioso
# sobrevive — se um aparecer, o gate falha e o inventário manda nomeá-lo).
set -u
cd "$(git rev-parse --show-toplevel)"
rc=0

if ! OUT="$(bash scripts/check_skip_inventory.sh --selftest 2>&1)"; then
    echo "FALHOU: selftest (os vereditos plantados não batem):"; echo "$OUT"; rc=1
else
    echo "ok  — selftest: $(printf '%s' "$OUT" | tail -1)"
fi

REAL="$(bash scripts/check_skip_inventory.sh 2>&1)"; rrc=$?
if [ "$rrc" -ne 0 ]; then
    echo "FALHOU: o inventário REAL tem skips silenciosos (nomeie cada um):"
    echo "$REAL" | grep -A50 "SILENT SKIPS" | head -20
    rc=1
elif ! printf '%s\n' "$REAL" | grep -q "SKIP-INVENTORY: OK"; then
    echo "FALHOU: veredito real mal-formado:"; echo "$REAL"; rc=1
else
    echo "ok  — real: $(printf '%s\n' "$REAL" | grep "SITES:" | head -1) — SKIP-INVENTORY: OK"
fi

exit $rc
