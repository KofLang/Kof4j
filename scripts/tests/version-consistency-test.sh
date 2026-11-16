#!/usr/bin/env bash
# version-consistency-test.sh — structural test do gate #814 (a versão UMA
# fonte de verdade). Red-first: o --selftest planta a divergência (RED) e a
# planta consistente (GREEN); o estado REAL é rc in {0,1} (o jar empacotado
# pode ser de outra versão durante o ciclo — o gate reporta, o teste valida a
# lógica plantada).
set -u
cd "$(git rev-parse --show-toplevel)"
rc=0
if ! OUT="$(bash scripts/check_version_consistency.sh --selftest 2>&1)"; then
    echo "FALHOU: selftest (os vereditos plantados não batem):"; echo "$OUT"; rc=1
else
    echo "ok  — selftest: $(printf '%s' "$OUT" | tail -1)"
fi
REAL="$(bash scripts/check_version_consistency.sh 2>&1)"; rrc=$?
if [ "$rrc" -ne 0 ] && [ "$rrc" -ne 1 ]; then
    echo "FALHOU: veredito real com rc inesperado ($rrc):"; echo "$REAL"; rc=1
else
    echo "ok  — real: $(printf '%s\n' "$REAL" | tail -1) (rc=$rrc)"
fi
exit $rc
