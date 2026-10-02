#!/usr/bin/env bash
#
# target-matrix-test.sh — prova local (sem rede, sem compilar) do gate EG-5 do
# EXIT GATE 1.0 (D-RELEASE-1.0; §13/§14 da PROPOSAL, fila §23 item 10).
# Cobre a RED-first do mecanismo com um `kof` FAKE e ferramentas fake:
#   (a) o --selftest do gate reprova saida divergente e aceita igualdade;
#   (b) preflight sem JDK sai 3 alto (ausencia de ambiente nunca vira verde);
#   (c) matriz inteira com saidas iguais → PASS (rc=0);
#   (d) um alvo core divergente → FAIL (rc=1) nomeando o alvo (nenhum falso verde);
#   (e) D-PARITY-050-SCOPE: remover/adicionar/trocar alvo numa copia → selftest reprova;
#   (f) o conjunto que a matriz EXECUTA = os seis alvos (laco literal na copia → detectado).
# A matriz REAL (6 alvos + qemu) roda no release-prep, nao aqui (~2min).
#
# Uso: scripts/tests/target-matrix-test.sh   (exit 0 = verde)
set -u
cd "$(dirname "${BASH_SOURCE[0]}")/../.." || exit 1

TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
FB="$TMP/bin"; mkdir -p "$FB" "$TMP/dist/bin" "$TMP/work"

# ── ferramentas fake ───────────────────────────────────────────────────────
cat > "$FB/java" <<'FAKE'
#!/usr/bin/env bash
echo 'openjdk version "25.0.4" 2025-01-01' >&2
FAKE
for t in node as ld riscv64-linux-gnu-as aarch64-linux-gnu-as; do
    printf '#!/usr/bin/env bash\nexit 0\n' > "$FB/$t"
done
for a in riscv64 aarch64; do
    printf '#!/usr/bin/env bash\ncat "$1"\n' > "$FB/qemu-$a"
done
cat > "$TMP/dist/bin/kof" <<'FAKE'
#!/usr/bin/env bash
sub="$1"; target=""; i=1
while [ $i -le $# ]; do
    case "${!i}" in --target) i=$((i+1)); target="${!i}" ;; esac
    i=$((i+1))
done
[ -n "${KOF_FAKE_LOG:-}" ] && echo "$target" >> "$KOF_FAKE_LOG"
case "$sub" in
    run)
        echo "matrix:start"; echo "sum=10"; echo "Hello, Kof!"; echo "matrix:end"
        [ "${FAKE_DIVERGE:-}" = "$target" ] && echo "WRONG"
        ;;
    build)
        mkdir -p build/classes/Default
        { echo "matrix:start"; echo "sum=10"; echo "Hello, Kof!"; echo "matrix:end"; } > build/classes/Default/Main
        ;;
esac
exit 0
FAKE
chmod +x "$FB"/* "$TMP/dist/bin/kof"

# ── (a) selftest do comparador ─────────────────────────────────────────────
out="$(bash scripts/target-matrix.sh --selftest 2>&1)"; rc=$?
if [ $rc -ne 0 ] || ! printf '%s' "$out" | grep -q 'SELFTEST: ok'; then
    echo "FAIL: --selftest nao provou o comparador (rc=$rc): $out" >&2; exit 1
fi

# ── (b) preflight sem java → rc=3, com causa nomeada ───────────────────────
out="$(env -i PATH=/nonexistent HOME=/nonexistent "$(command -v bash)" scripts/target-matrix.sh 2>&1)"; rc=$?
if [ $rc -ne 3 ]; then echo "FAIL: preflight sem java deveria sair 3, saiu $rc: $out" >&2; exit 1; fi
case "$out" in *"SEM java"*) : ;; *) echo "FAIL: preflight sem causa nomeada: $out" >&2; exit 1 ;; esac

# ── (c) matriz fake coerente → PASS ────────────────────────────────────────
out="$(PATH="$FB:/usr/bin:/bin" bash scripts/target-matrix.sh --dist "$TMP/dist" --work "$TMP/work" 2>&1)"; rc=$?
if [ $rc -ne 0 ] || ! printf '%s' "$out" | grep -q 'TARGET-MATRIX: PASS'; then
    echo "FAIL: matriz coerente deveria PASSAR (rc=$rc): $out" >&2; exit 1
fi

# ── (d) um alvo core divergente → FAIL nomeando o alvo ─────────────────────
out="$(PATH="$FB:/usr/bin:/bin" FAKE_DIVERGE=script bash scripts/target-matrix.sh --dist "$TMP/dist" --work "$TMP/work" 2>&1)"; rc=$?
if [ $rc -ne 1 ]; then echo "FAIL: divergencia deveria sair 1, saiu $rc: $out" >&2; exit 1; fi
case "$out" in *"script"*) : ;; *) echo "FAIL: FAIL nao nomeou o alvo divergente: $out" >&2; exit 1 ;; esac

# ── (e) D-PARITY-050-SCOPE: drift no conjunto de alvos → selftest reprova ──
# Fixture = copia do script (a arvore nao e tocada). O controle prova que a copia
# passa; cada drift tem de mudar a copia (sed que nao casa = fixture morta).
FX="$TMP/fixture/scripts/target-matrix.sh"; mkdir -p "$(dirname "$FX")"
cp scripts/target-matrix.sh "$FX"
out="$(bash "$FX" --selftest 2>&1)"; rc=$?
if [ $rc -ne 0 ]; then echo "FAIL: copia intacta deveria passar o selftest (rc=$rc): $out" >&2; exit 1; fi
drift() { # rotulo expr-sed
    sed -E "$2" scripts/target-matrix.sh > "$FX"
    if cmp -s scripts/target-matrix.sh "$FX"; then echo "FAIL: fixture '$1' nao alterou a copia (sed nao casou)" >&2; exit 1; fi
    out="$(bash "$FX" --selftest 2>&1)"; rc=$?
    if [ $rc -ne 1 ]; then echo "FAIL: drift '$1' deveria reprovar o selftest (rc=$rc): $out" >&2; exit 1; fi
    case "$out" in *"D-PARITY-050-SCOPE drift"*) : ;; *) echo "FAIL: drift '$1' sem causa nomeada: $out" >&2; exit 1 ;; esac
}
drift "js removido"      's/^DIRECT_CORE_TARGETS=\(script js native\)$/DIRECT_CORE_TARGETS=(script native)/'
drift "riscv32 incluido" 's/^CROSS_CORE_ARCHES=\(riscv64 aarch64\)$/CROSS_CORE_ARCHES=(riscv64 aarch64 riscv32)/'
drift "aarch64 trocado"  's/^CROSS_CORE_ARCHES=\(riscv64 aarch64\)$/CROSS_CORE_ARCHES=(riscv64 riscv32)/'

# ── (f) conjunto EXECUTADO = conjunto declarado (os seis alvos) ────────────
# O kof fake registra cada --target invocado. Um laco literal que ignore a lista
# passa no selftest (a funcao continua com seis) — so esta prova o pega.
executed() { # script -> alvos invocados, na ordem
    : > "$TMP/kof.log"
    PATH="$FB:/usr/bin:/bin" KOF_FAKE_LOG="$TMP/kof.log" bash "$1" --dist "$TMP/dist" --work "$TMP/work" >/dev/null 2>&1
    tr '\n' ' ' < "$TMP/kof.log" | sed 's/ $//'
}
six="jvm script js native native.riscv64 native.aarch64"
got="$(executed scripts/target-matrix.sh)"
if [ "$got" != "$six" ]; then echo "FAIL: matriz executou [$got], esperado [$six]" >&2; exit 1; fi
sed -E 's/^for t in "\$\{DIRECT_CORE_TARGETS\[@\]\}"; do$/for t in script native; do/' scripts/target-matrix.sh > "$FX"
if cmp -s scripts/target-matrix.sh "$FX"; then echo "FAIL: fixture do laco literal nao alterou a copia (sed nao casou)" >&2; exit 1; fi
got="$(executed "$FX")"
if [ "$got" = "$six" ]; then echo "FAIL: laco literal sem js nao foi detectado (executou [$got])" >&2; exit 1; fi

echo "target-matrix-test: ok — comparador RED-first, preflight alto sem JDK, PASS coerente, FAIL nomeia o alvo, D-PARITY-050-SCOPE (drift reprova; executado = 6 alvos)"
