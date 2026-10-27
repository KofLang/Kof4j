#!/usr/bin/env bash
# check_index_dup.sh — a tabela §1 do `docs/development/README.md`+PT lista cada
# chave de plano (0a..0z) NO MAXIMO UMA VEZ.
#
# Motivo (lane docs/lab, 22/10): a linha `| 0f |` (image-vision) apareceu
# DUPLICADA byte-a-byte em EN e PT. Nenhum gate pegou — `check_plan_index` le a
# PRIMEIRA linha `^| 0f |` e valida o que ela diz, nunca a MULTIPLICIDADE;
# `check_live_records` idem. E a mesma classe da triplicacao do paragrafo
# `Authority =` da §2 (`4b048d805`), onde o proprio registro anotou que
# "nenhum gate pega multiplicidade de linha". O duplicado nao e cosmetico: um
# leitor (humano ou agente) que procura o dono do plano 0f recebe duas
# respostas, e um prepend futuro pode divergir as duas.
#
# Regiao: so as linhas de tabela `| 0<letra> |` da §1. A comparacao e por chave,
# nao por conteudo — duas linhas para a MESMA chave sao o defeito, mesmo que o
# texto difira (nesse caso e pior: os leitores divergem).
#
# Uso: scripts/check_index_dup.sh   (rc=0 ok / rc=1 duplicata)
set -u
cd "$(dirname "$0")/.."

ok=1
for f in docs/development/README.md docs/development/README.pt_BR.md; do
  [ -f "$f" ] || { echo "INDEX-DUP: FAIL — $f ausente"; ok=0; continue; }
  # chave = o identificador 0a..0z; conta por LINHA inteira da tabela (grep -cF
  # sobre a linha completa erraria por substring, ex. '0f' dentro de outro texto)
  dups="$(grep -oE '^\| 0[a-z] \|' "$f" | awk '{print $2}' | sort | uniq -d)"
  if [ -n "$dups" ]; then
    while IFS= read -r key; do
      n=$(grep -cE "^\| ${key} \|" "$f")
      echo "INDEX-DUP: FAIL — $f repete a chave '$key' ($n linhas; cada plano tem UMA linha na §1)"
    done <<< "$dups"
    ok=0
  fi
done

if [ "$ok" = 1 ]; then
  echo "INDEX-DUP: OK — cada chave 0a..0z aparece uma vez em EN e PT"
fi
exit $((1 - ok))
