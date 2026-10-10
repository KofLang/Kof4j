#!/usr/bin/env bash
# gen-signatures-test.sh — structural test do detector de deriva do catalogo
# (LSP-A, §568). Red-first: o detector precisa capturar deriva plantada
# (--selftest) e continuar VERDE contra o estado real do repo.
set -u
cd "$(git rev-parse --show-toplevel)"
rc=0
if ! OUT="$(python3 scripts/gen_signatures.py --selftest 2>&1)"; then
    echo "FALHOU: selftest:"; echo "$OUT"; rc=1
else
    echo "ok  — deriva plantada capturada (rc1), catalogo limpo passa (rc0)"
fi
if ! OUT2="$(python3 scripts/gen_signatures.py check 2>&1)"; then
    echo "FALHOU: estado real do repo:"; echo "$OUT2"; rc=1
else
    echo "ok  — catalogo do repo == gen_signatures.py (idempotente)"
fi
exit $rc
