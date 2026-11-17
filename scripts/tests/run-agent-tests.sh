#!/usr/bin/env bash
#
# run-agent-tests.sh — roda a suíte de scripts da automação de agentes
# (Onda 1). Cada teste usa fakes de gh/opencode/curl e um XDG_STATE_HOME
# temporário: sem rede, sem writes no GitHub, sem chamadas ao modelo.
#
# Uso: scripts/tests/run-agent-tests.sh [nome-parcial ...]   (exit 0 = tudo verde)
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"

# O java do sistema é só JRE (sem `jar`) — o harness AUTO-PROVISIONA o JDK
# (o mesmo padrão do codeql-gate; os gates que exigem java/jar/25 deixam de
# depender do ambiente do operador; o CI tem o JDK no PATH nativamente).
if [ -z "${JAVA_HOME:-}" ]; then
  for jdk in "$HOME"/.local/jdks/jdk-*; do
    [ -x "$jdk/bin/javac" ] && export JAVA_HOME="$jdk" && break
  done
fi
[ -n "${JAVA_HOME:-}" ] && export PATH="$JAVA_HOME/bin:$PATH"

TESTS=(
    scripts/tests/issue-watcher-test.sh
    scripts/tests/auto-loop-test.sh
)
# testes que existirem no disco entram (cada commit da Onda 1 acrescenta os seus)
    for extra in kof-issues-agent-script verify-release-identity release-evidence agent-state-fingerprint agent-dispatch-gate agent-risk agent-evidence agent-verify agent-close-issue check-release-blockers test-package-outside-repo test-kofc-gate test-android-gate stability-report codeql-gate target-matrix changelog-ledger ledger-anchors workflow-pins workflow-permissions agent-verify-wiring live-records fetch-open-issues setup-cross-toolchain audit-stubs doc-refs release-workflow-candidate debt-scout-config debt-scout-fingerprint debt-scout-branch-discovery debt-scout-schema debt-scout-satd debt-scout-scan debt-scout-workflow pipeline-state promotion-gate test-suite-profile test-suite-audit test-test-hygiene check-javafx-absent size-budget gen-signatures lab-stability skip-inventory asan-ffi-sweep version-consistency macos-seam; do
    [ -f "scripts/tests/$extra-test.sh" ] && TESTS+=("scripts/tests/$extra-test.sh")
done

FAILED=0
for t in "${TESTS[@]}"; do
    if [ $# -gt 0 ]; then
        skip=1; for f in "$@"; do case "$t" in *"$f"*) skip=0;; esac; done
        [ "$skip" -eq 1 ] && continue
    fi
    echo "=== $t"
    if bash "$t"; then :; else FAILED=1; echo "!!! FALHOU: $t"; fi
done
[ "$FAILED" -eq 0 ] && echo "== SUÍTE DE AGENTES: VERDE" || echo "== SUÍTE DE AGENTES: VERMELHA"
exit "$FAILED"
