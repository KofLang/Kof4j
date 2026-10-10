#!/usr/bin/env bash
# size-budget-test.sh — D-SIZE-BUDGET F1.1 (#725): o medidor conhece os 5 modulos
# do reactor, mede o jar exato da VERSION e nunca esconde um modulo ausente.
# Red-first: o --selftest planta jar stale, sources, modulo extra, drift do pom e
# VERSION invalido, e cada caso tem de ser capturado. Sem build, sem rede.
set -u
cd "$(git rev-parse --show-toplevel)"
if OUT="$(bash scripts/size/measure-size.sh --selftest 2>&1)"; then
    echo "ok  — $OUT"
else
    echo "FALHOU: selftest:"; echo "$OUT"; exit 1
fi
