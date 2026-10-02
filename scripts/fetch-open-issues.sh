#!/usr/bin/env bash
#
# fetch-open-issues.sh — lista as issues ABERTAS no formato `numero<TAB>labels`,
# consumido pela triagem e pela esteira de release (`OPEN_ISSUES_TSV`).
#
# Por que existe: neste host o `gh` nao esta instalado e o `gh-as-agent.sh` exige
# um GitHub App configurado. A condicao 5 ficava `UNKNOWN` (= "nao medido") nao
# por falta de dados, mas por falta da ferramenta — a mesma classe de falso
# vermelho que a condicao 1 tinha. O repo e publico, entao a leitura honesta
# pode ser feita pela API publica.
#
# Preferencia:
#   0. `KOF_ISSUES_JSON` (fixture offline) — override explicito, sem rede;
#   1. `gh` autenticado (canonico, sem limite apertado);
#   2. `curl` na API do GitHub, com token (GH_TOKEN/GITHUB_TOKEN) se houver;
#   3. `curl` sem auth (limite ~60 req/h) — suficiente para uma corrida do gate.
#
# Falha ALTO (rc!=0, zero linhas) quando nao consegue medir. Isso e deliberado:
# o gate deve seguir `UNKNOWN`, nunca virar "0 bugs" falso (R6/Q5).
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"

REPO="${KOF_ISSUES_REPO:-KofLang/Kof4j}"
LIMIT="${KOF_ISSUES_LIMIT:-200}"

# 0) modo offline (teste/depuracao): um array JSON em KOF_ISSUES_JSON, sem rede.
# Um fixture explicito e um override explicito: tem precedencia sobre `gh`/API
# (sem isso o modo offline fica inalcancavel em host com `gh` autenticado e o
# teste deixa de ser hermetico — mesma classe do §390).
if [ -n "${KOF_ISSUES_JSON:-}" ]; then
    [ -r "$KOF_ISSUES_JSON" ] || { echo "ERRO: KOF_ISSUES_JSON ilegivel" >&2; exit 1; }
    python3 -c '
import json,sys
d=json.load(open(sys.argv[1]))
for i in d:
    if "pull_request" in i: continue      # gh issue list exclui PRs
    print("%d\t%s" % (i["number"], ",".join(l["name"] for l in i["labels"])))
' "$KOF_ISSUES_JSON" || exit 1
    exit 0
fi

# 1) gh canonico
if command -v gh >/dev/null 2>&1; then
    eval "$(scripts/gh-as-agent.sh token 2>/dev/null)" || true
    if out="$(gh issue list --repo "$REPO" --state open --limit "$LIMIT" \
            --json number,labels \
            --jq '.[] | "\(.number)\t\([.labels[].name]|join(","))"' 2>/dev/null)"; then
        printf '%s\n' "$out"
        exit 0
    fi
fi

# 2/3) API do GitHub via curl (publica).
command -v curl >/dev/null 2>&1 || { echo "ERRO: sem gh e sem curl" >&2; exit 1; }
token="${GH_TOKEN:-${GITHUB_TOKEN:-}}"
auth=(); [ -n "$token" ] && auth=(-H "Authorization: Bearer $token")

tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
per_page=100
page=1
rows="$(mktemp)"; trap 'rm -rf "$tmp" "$rows"' EXIT
while [ "$page" -le $(( (LIMIT + per_page - 1) / per_page )) ]; do
    url="https://api.github.com/repos/$REPO/issues?state=open&per_page=$per_page&page=$page"
    code="$(curl -sS -o "$tmp/p.json" -w '%{http_code}' "${auth[@]}" \
            -H 'Accept: application/vnd.github+json' "$url" 2>/dev/null)" || exit 1
    if [ "$code" != "200" ]; then
        echo "ERRO: API HTTP $code para $REPO (repo privado? limite? rede?)" >&2; exit 1
    fi
    n="$(python3 -c '
import json,sys
d=json.load(open(sys.argv[1]))
iss=[i for i in d if "pull_request" not in i]   # gh issue list exclui PRs
for i in iss:
    print("%d\t%s" % (i["number"], ",".join(l["name"] for l in i["labels"])))
' "$tmp/p.json")" || exit 1
    [ -n "$n" ] && printf '%s\n' "$n" >> "$rows"
    total="$(python3 -c 'import json,sys;print(len(json.load(open(sys.argv[1]))))' "$tmp/p.json")" || exit 1
    [ "$total" -lt "$per_page" ] && break
    page=$((page+1))
done

if [ -s "$rows" ]; then sort -n "$rows"; else echo ""; fi
exit 0
