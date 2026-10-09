#!/usr/bin/env bash
# lab-stability-test.sh — structural test do gate agregador D-LAB-STABILITY.
# Red-first como os vizinhos: o --selftest planta os cinco casos de veredito
# (GREEN, gate-fail SLIPS, rc-gate SLIPS, suite ausente => NAO-AVALIADO SLIPS,
# suite com falha plantada => SLIPS) e prova que a decisao tem dentes,
# incluindo o no-false-green (ausencia de suite report nunca e verde).
# O estado REAL do repo NAO precisa ser GREEN (o lab pode estar legitimamente
# SLIPS enquanto ha 1.0-blocks abertos ou suite sem report fresco) — o teste
# exige apenas que o veredito real seja bem-formado e rc in {0,1}.
set -u
cd "$(git rev-parse --show-toplevel)"
rc=0

if ! OUT="$(bash scripts/check_lab_stability.sh --selftest 2>&1)"; then
    echo "FALHOU: selftest (vereditos plantados nao batem):"; echo "$OUT"; rc=1
else
    echo "ok  — selftest: $(printf '%s' "$OUT" | tail -1)"
fi

REAL="$(bash scripts/check_lab_stability.sh 2>&1)"; rrc=$?
if [ "$rrc" -ne 0 ] && [ "$rrc" -ne 1 ]; then
    echo "FALHOU: veredito real com rc inesperado ($rrc):"; echo "$REAL"; rc=1
elif ! printf '%s\n' "$REAL" | grep -Eq "CUT: (GREEN|SLIPS)"; then
    echo "FALHOU: veredito real mal-formado:"; echo "$REAL"; rc=1
else
    echo "ok  — real: $(printf '%s\n' "$REAL" | grep -E "CUT: " | tail -1)"
fi

exit $rc
