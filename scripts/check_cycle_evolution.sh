#!/usr/bin/env bash
# check_cycle_evolution.sh — a REGRA DE EVOLUCAO VERDADEIRA (ordem da
# mantenedora, 07/11): "cada ciclo precisa evoluir de verdade, nao apenas
# mentir pra mim".
#
# Why: a long run of TRIAGE ticks that only say "verified-unchanged" is
# indistinguishable from an agent that stopped working. This gate MEASURES
# whether the last cycle produced real change, and refuses a cycle that
# closed with nothing but a DOING tick.
#
# What counts as evolution (any one):
#   (a) a commit since the last tick that touches CODE, TESTS or NORMATIVE
#       DOCS (libs/, kof-compiler/src/, scripts/, docs/ outside DOING/status/
#       CHANGELOG) — the real work;
#   (b) a NEW measured finding recorded in the DOING (a "MEASURED"/"MEDIDO"
#       line with a file:line or a command result) — a real investigation;
#   (c) an explicit BLOCKED-BY-DECISION line naming the decision — the honest
#       stop, which is NOT a lie.
#
# What does NOT count: a tick whose only content is "verified-unchanged".
#
# Usage: scripts/check_cycle_evolution.sh [N]   # N = commits to inspect (default 5)
# Exit: 0 = the last cycle evolved (or is honestly blocked); 1 = NO-EVOLUTION.
set -u
cd "$(dirname "$0")/.."
N="${1:-5}"

# (a) real change in the last N commits (excluding pure DOING/status/CHANGELOG ticks)
real=$(git log -n "$N" --format='%H' | while read -r c; do
    files=$(git show --name-only --format='' "$c")
    if printf '%s\n' "$files" | grep -qE '^(libs/|kof-compiler/src/|scripts/|docs/[^/]+\.md$|docs/(architecture|stdlib|language-reference|bugs-and-gaps)/)'; then
        # a docs-only commit that is ONLY DOING/status/CHANGELOG does not count
        if printf '%s\n' "$files" | grep -qvE '^(DOING|DOING\.pt_BR|docs/status|docs/status\.pt_BR|CHANGELOG|CHANGELOG\.pt_BR)'; then
            echo "$c"; break
        fi
    fi
done)

if [ -n "$real" ]; then
    # A mudanca real conta como EVOLUCAO DA LINGUAGEM quando toca o nucleo:
    # o dispatch de stdlib, o emissor de qualquer alvo, o runtime, o parser,
    # o typer ou o catalogo. Uma lib em Kof puro sobre primitivos existentes
    # e trabalho legitimo, mas NAO e evolucao da linguagem (ordem 07/11).
    lang=$(git log -n "$N" --format='%H' | while read -r c; do
        files=$(git show --name-only --format='' "$c")
        if printf '%s\n' "$files" | grep -qE '^(kof-compiler/src/main/java/dev/kof/compiler/(Kof[A-Z][A-Za-z]*|nat/|wasm/|jvm/|js/|runtime/|backend/|lang/)|kof-compiler/src/main/antlr|kof-compiler/src/main/resources/dev/kof/)'; then
            echo "$c"; break
        fi
    done)
    if [ -n "$lang" ]; then
        echo "LANGUAGE-EVOLUTION: the language core changed in $(git log -1 --format='%h %s' "$lang" | cut -c1-100)"
        exit 0
    fi
    echo "EVOLUTION: real change in $(git log -1 --format='%h %s' "$real" | cut -c1-100)"
    echo "  NOTE: this cycle did NOT touch the language core (dispatch/emitter/runtime/parser/typer)."
    echo "  A library over existing primitives is legitimate work, but the NEXT cycle MUST attack the"
    echo "  missing primitive it exposed (maintainer order 07/11: every cycle must advance the language)."
    exit 0
fi

# (b)/(c) the newest DOING entry: a measured finding or an honest block
newest=$(grep -m1 '^> ' DOING.md || true)
if printf '%s' "$newest" | grep -qiE 'MEASURED|MEDIDO|BLOCKED BY DECISION|BLOQUEAD[OA] POR DECIS|D-\*|decision required'; then
    echo "EVOLUTION: the newest DOING entry carries a measured finding or an honest block"
    exit 0
fi

echo "NO-EVOLUTION: the last $N commits carry no code/test/normative change and the newest DOING entry is a bare tick."
echo "  A cycle MUST NOT close like this (maintainer order 07/11). Pick a real front or record the blocking decision."
exit 1
