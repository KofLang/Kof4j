#!/usr/bin/env bash
# check-javafx-absent-test.sh — structural test do gate JavaFX-ausente.
# Red-first: o gate precisa capturar 'import javafx' e uma dependencia javafx no
# pom (casos plantados) e continuar VERDE contra o estado real do repo.
set -u
cd "$(git rev-parse --show-toplevel)"
rc=0
if ! OUT="$(bash scripts/check_javafx_absent.sh --selftest 2>&1)"; then
    echo "FALHOU: selftest:"; echo "$OUT"; rc=1
else
    echo "ok  — casos plantados (import + pom) capturados"
fi
if ! OUT2="$(bash scripts/check_javafx_absent.sh 2>&1)"; then
    echo "FALHOU: estado real do repo:"; echo "$OUT2"; rc=1
else
    echo "ok  — estado real do repo sem JavaFX"
fi
exit $rc
