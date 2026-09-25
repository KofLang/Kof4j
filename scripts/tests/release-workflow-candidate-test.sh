#!/usr/bin/env bash
# release-workflow-candidate-test.sh — D-ARTIFACT-TRUST queue (b) / R1.
#
# Part 1 (RED-1..RED-8): structural assertions against the REAL
# .github/workflows/release.yml — the workflow must not self-mutate the
# repository (no push-to-main auto-trigger, no bump-version.sh, no `git
# commit`, no `git push`, no `bump_sha`) and must derive/propagate/verify a
# single candidate SHA mechanically.
#
# Part 2 (RED-9 / T1-T10): behavioral tests of
# scripts/validate-release-candidate.sh against real temporary git repos —
# no GitHub Actions needed.
#
# Uso: scripts/tests/release-workflow-candidate-test.sh   (exit 0 = tudo verde)
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"

WF=".github/workflows/release.yml"
V="scripts/validate-release-candidate.sh"
FAILED=0

ok()   { echo "ok  — $1"; }
fail() { echo "!!! FALHOU: $1"; FAILED=1; }

# ---------------------------------------------------------------------------
# Part 1 — structural (grep the real workflow file)
# ---------------------------------------------------------------------------

[ -f "$WF" ] || { echo "!!! $WF nao existe"; exit 1; }

# RED-1: release nao pode auto-disparar por push em main.
if awk '/^on:/{f=1;next} /^[a-zA-Z]/{f=0} f' "$WF" | grep -A3 '^\s*push:' | grep -q 'main'; then
    fail "RED-1: release.yml ainda dispara em push para main"
else
    ok "RED-1: sem auto-disparo por push em main"
fi

# RED-2: release nao pode executar bump.
if grep -q 'bump-version\.sh' "$WF"; then
    fail "RED-2: release.yml ainda chama scripts/bump-version.sh"
else
    ok "RED-2: sem chamada a bump-version.sh"
fi

# RED-3: release nao pode criar commit.
if grep -Eq '(^|[^#[:alnum:]])git commit\b' "$WF"; then
    fail "RED-3: release.yml ainda executa git commit"
else
    ok "RED-3: sem git commit"
fi

# RED-4: release nao pode fazer push.
if grep -Eq '(^|[^#[:alnum:]])git push\b' "$WF"; then
    fail "RED-4: release.yml ainda executa git push"
else
    ok "RED-4: sem git push"
fi

# RED-5: nao existe bump_sha.
if grep -q 'bump_sha' "$WF"; then
    fail "RED-5: release.yml ainda referencia bump_sha"
else
    ok "RED-5: sem bump_sha"
fi

# RED-6: candidato precisa ser capturado mecanicamente.
for needle in 'git rev-parse HEAD' 'GITHUB_SHA' 'candidate_sha'; do
    grep -q -- "$needle" "$WF" || fail "RED-6: release.yml nao contem '$needle'"
done
ok "RED-6: candidate_sha/GITHUB_SHA/git rev-parse HEAD presentes"

# RED-7: package deve fazer checkout do candidato.
if grep -q 'needs\.validate-candidate\.outputs\.candidate_sha' "$WF"; then
    ok "RED-7: package-and-release referencia needs.validate-candidate.outputs.candidate_sha"
else
    fail "RED-7: package-and-release nao referencia needs.validate-candidate.outputs.candidate_sha"
fi

# RED-8: tag publicada aponta explicitamente ao candidato.
if grep -q 'target_commitish' "$WF" && grep -q 'candidate_sha' "$WF"; then
    ok "RED-8: target_commitish + candidate_sha presentes"
else
    fail "RED-8: target_commitish/candidate_sha ausentes na criacao da release"
fi

# Validador extraido precisa existir (design recomendado no doc, §13).
[ -x "$V" ] || [ -f "$V" ] || fail "validador $V nao existe"

# ---------------------------------------------------------------------------
# Part 2 — behavioral (RED-9): scripts/validate-release-candidate.sh
# ---------------------------------------------------------------------------

if [ ! -f "$V" ]; then
    echo "!!! pulando Parte 2: $V nao existe ainda (esperado no estado RED)"
    FAILED=1
else
    expect() { # descricao, rc esperado, rc real
        if [ "$2" != "$3" ]; then fail "$1 (esperado rc=$2, veio rc=$3)"; else ok "$1 (rc=$3)"; fi
    }
    has() { # descricao, texto, saida
        printf '%s\n' "$3" | grep -q -- "$2" || { fail "$1: nao achou '$2' na saida"; return 1; }
        return 0
    }

    T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT

    # Hermeticidade ao ambiente do runner (licao §390): o script sob teste pula
    # o "newer gate" quando GITHUB_REF_NAME != main (perna pre-release, que e
    # comportamento de producao correto). No job "Structural quality gates" o
    # CI exporta GITHUB_REF_NAME=beta-0.5.0 e T3 passou a medir a outra perna
    # (rc=0 no lugar de rc=1 — vermelho por ambiente, nao por logica). Fixar
    # a perna main para os cenarios de comparacao; a perna pre-release passa a
    # ter cenario proprio (T3b), ancorada explicitamente.
    GITHUB_REF_NAME=main
    export GITHUB_REF_NAME

    # newrepo <dir>: git repo novo e vazio, pronto para commits.
    newrepo() {
        rm -rf "$1"; mkdir -p "$1"
        git -C "$1" init -q
        git -C "$1" config user.email test@example.com
        git -C "$1" config user.name test
    }

    # commit_version <dir> <version> -> imprime o SHA do commit.
    commit_version() {
        printf '%s' "$2" > "$1/VERSION"
        git -C "$1" add VERSION >/dev/null
        git -C "$1" commit -q -m "version $2" --allow-empty
        git -C "$1" rev-parse HEAD
    }

    # T1: current 0.5.0-beta / last 0.4.9-beta / nenhum tag da versao atual -> PASS
    newrepo "$T/t1"
    OLD_SHA="$(commit_version "$T/t1" "0.4.9-beta")"
    git -C "$T/t1" tag "kof-0.4.9-beta-linux-x86_64" "$OLD_SHA"
    NEW_SHA="$(commit_version "$T/t1" "0.5.0-beta")"
    out="$(cd "$T/t1" && bash "$OLDPWD/$V" "$NEW_SHA" 2>&1)"; rc=$?
    expect "T1 versao nova sem tag propria -> PASS" 0 "$rc"
    has "T1" "version=0.5.0-beta" "$out"; has "T1" "sha=$NEW_SHA" "$out"

    # T2: current == last -> FAIL. Same version as the last release means its
    # own target tag already exists (single anchor tag family), so the tag
    # check fires first — still rc=1, still never a false PASS.
    newrepo "$T/t2"
    S="$(commit_version "$T/t2" "0.4.9-beta")"
    git -C "$T/t2" tag "kof-0.4.9-beta-linux-x86_64" "$S"
    out="$(cd "$T/t2" && bash "$OLDPWD/$V" "$S" 2>&1)"; rc=$?
    expect "T2 current == last -> FAIL" 1 "$rc"
    has "T2" "release tag already exists" "$out"

    # T3: current < last -> FAIL, reaches the version-comparison message
    # (the current version's own tag does not exist in this fixture).
    newrepo "$T/t3"
    OLD_SHA="$(commit_version "$T/t3" "0.5.0-beta")"
    git -C "$T/t3" tag "kof-0.5.0-beta-linux-x86_64" "$OLD_SHA"
    NEW_SHA="$(commit_version "$T/t3" "0.4.9-beta")"
    out="$(cd "$T/t3" && bash "$OLDPWD/$V" "$NEW_SHA" 2>&1)"; rc=$?
    expect "T3 current < last -> FAIL" 1 "$rc"
    has "T3" "not newer than last release" "$out"

    # T3b: a OUTRA perna do mesmo gate (validate-release-candidate.sh:72) —
    # em branch de pre-release o "is newer" e pulado por design. O fixture e o
    # MESMO downgrade de T3; o que muda e so GITHUB_REF_NAME. Ancorar as duas
    # pernas na INTERFACE (rc + mensagem) torna a suite imune ao ambiente do
    # runner — o vermelho de CI (job "Structural quality gates", step 7, run
    # 36192220794) foi exatamente o vazamento de GITHUB_REF_NAME=beta-0.5.0 no
    # T3 (mesma aula do §390/EG-2 no codeql-gate-test).
    out="$(cd "$T/t3" && GITHUB_REF_NAME=beta-9.9.9 bash "$OLDPWD/$V" "$NEW_SHA" 2>&1)"; rc=$?
    expect "T3b pre-release branch -> skip newer gate -> PASS" 0 "$rc"
    has "T3b" "skip newer gate" "$out"

    # T4/T5/T6: tag do alvo atual ja existe -> FAIL
    for TARGET in linux-x86_64 windows-x86_64 macos-arm64; do
        newrepo "$T/tag-$TARGET"
        S="$(commit_version "$T/tag-$TARGET" "0.5.0-beta")"
        git -C "$T/tag-$TARGET" tag "kof-0.5.0-beta-$TARGET" "$S"
        out="$(cd "$T/tag-$TARGET" && bash "$OLDPWD/$V" "$S" 2>&1)"; rc=$?
        expect "T4/5/6 tag $TARGET ja existe -> FAIL" 1 "$rc"
        has "T4/5/6 $TARGET" "release tag already exists" "$out"
    done

    # T7: HEAD != SHA esperado -> FAIL
    newrepo "$T/t7"
    S="$(commit_version "$T/t7" "0.5.0-beta")"
    out="$(cd "$T/t7" && bash "$OLDPWD/$V" "0000000000000000000000000000000000000000" 2>&1)"; rc=$?
    expect "T7 HEAD != SHA esperado -> FAIL" 1 "$rc"
    has "T7" "differs from release candidate" "$out"

    # T8: VERSION vazia -> FAIL
    newrepo "$T/t8"
    S="$(commit_version "$T/t8" "")"
    out="$(cd "$T/t8" && bash "$OLDPWD/$V" "$S" 2>&1)"; rc=$?
    expect "T8 VERSION vazia -> FAIL" 1 "$rc"
    has "T8" "VERSION is empty" "$out"

    # T9: VERSION com caracteres invalidos -> FAIL
    newrepo "$T/t9"
    S="$(commit_version "$T/t9" '0.5.0 beta!')"
    out="$(cd "$T/t9" && bash "$OLDPWD/$V" "$S" 2>&1)"; rc=$?
    expect "T9 VERSION invalida -> FAIL" 1 "$rc"
    has "T9" "invalid VERSION" "$out"

    # T10: sem release anterior (nenhuma tag) -> PASS para versao valida
    newrepo "$T/t10"
    S="$(commit_version "$T/t10" "0.1.0-beta")"
    out="$(cd "$T/t10" && bash "$OLDPWD/$V" "$S" 2>&1)"; rc=$?
    expect "T10 sem release anterior -> PASS" 0 "$rc"
    has "T10" "version=0.1.0-beta" "$out"

    # Adversarial (secao 17 do doc): B/C/D ja cobertos por T7/T2/T4-T6.
    # Uso incorreto (0 ou 2+ argumentos) -> exit 2.
    out="$(bash "$V" 2>&1)"; rc=$?
    expect "uso sem argumento -> exit 2" 2 "$rc"
fi

if [ "$FAILED" -eq 0 ]; then
    echo "== release-workflow-candidate: VERDE"
else
    echo "== release-workflow-candidate: VERMELHA"
fi
exit "$FAILED"
