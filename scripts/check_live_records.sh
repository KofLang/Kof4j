#!/usr/bin/env bash
#
# check_live_records.sh — integridade dos registros vivos da lane (docs/development):
#   A) contagem viva nos READMEs == classificador canonico (autoridade e o script,
#      nunca a prosa: numero cravado em dois lugares e promessa de drift);
#   B) paridade EN<->PT dos IDs de decisao em DECISIONS.md (o registro de governanca
#      nao pode ter secao so num idioma) e zero heading duplicado no mesmo arquivo.
#   C) numeracao de secoes (N.) em DECISIONS.md: mesmo conjunto de numeros E mesmo
#      nivel de heading nos dois idiomas (achou a secao 7 PT rebaixada a H2 vs H1 no EN).
#   D) a lista "Pending (condition 3)" do README sec.0 == o conjunto de loose docs que
#      o gate realmente marca (`ls docs/development/*.md` menos o ALLOWLIST do gate).
#      Fecha a divergencia silenciosa: o registro humano (README) nao pode discordar
#      da medicao (gate) — nem listar menos, nem listar a mais.
#   E) roadmap EG: mesmo conjunto de linhas EG-N E mesmo estado fechado/aberto EN<->PT,
#      pela MESMA regra do gate (linha contem DONE|FEITO). O gate de release so le o
#      roadmap EN: se o PT divergir, ninguem ve — e a condicao 6 do release depende disso.
#   F) numeracao/nivel de secoes (N.) em TODOS os pares de docs/development (nao so
#      DECISIONS): um doc EN com secao H1 e o PT com H2 e drift de leitura.
#   G) prep do release: a contagem "N live" da condicao 7 (bugs-and-gaps) == autoridade
#      (mesmo numero do classificador; o registro de aceitacao nao pode cravar outro).
#   H) prep do release: toda §NNN citada na secao "issues que viajam" tem de estar no
#      conjunto ABERTO — a prep nao pode mandar viajar um § ja FECHADO (a classe do item 1
#      stale: §371/§374/§378 pousaram e a lista seguia mandando-os viajar).
#   I) a fila oficial (README sec.1) tem de nomear TODO loose doc do gate: um doc
#      promovido para docs/development/ e ausente da fila e trabalho invisivel (a
#      sec.0 pode lista-lo como pendente, mas ninguem o poe em posicao). Achou o
#      type-system-extensions-plan.md promovido em 21/09 fora da sec.1 em EN+PT
#      (concluido X5+X6 e movido para docs/ em 22/09 — nao e mais loose).
#
# A classe (A) ja driftou duas vezes em 21/09; a classe (B) apareceu quando a lane
# irma adicionou 3 decisoes so no EN e um merge deixou um heading orfao + duplicado
# no PT (achados 21/09 e corrigidos na mesma rodada).
#
# AUSENCIA e FALHA, nao passe livre: se a prosa/regex mudar de forma, este gate tem
# de ser atualizado JUNTO (regra anti-neutering da lane).
#
# Uso: scripts/check_live_records.sh            # rc!=0 em drift
#      scripts/check_live_records.sh --selftest # casos bons e ruins plantados
# Env (teste): LR_EN, LR_PT, LR_COUNT, DEC_EN, DEC_PT, LR_DOCDIR, LR_GATE,
#              LR_RM_EN, LR_RM_PT, LR_RM_ON, LR_MDPAIRS, LR_PREP_EN, LR_PREP_PT, LR_PREP_ON,
#              LR_QUEUE_ON
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"

EN="${LR_EN:-docs/development/README.md}"
PT="${LR_PT:-docs/development/README.pt_BR.md}"
DEN="${DEC_EN:-docs/development/DECISIONS.md}"
DPT="${DEC_PT:-docs/development/DECISIONS.pt_BR.md}"
GATE="${LR_GATE:-scripts/check_release_050_gate.sh}"
# Parte D so roda na corrida real (ou quando o teste aponta LR_DOCDIR de proposito):
# fixtures antigas setam LR_EN e nao tem README/dir reais, entao DOCDIR fica vazio.
if [ -n "${LR_EN:-}" ]; then DOCDIR="${LR_DOCDIR-}"; else DOCDIR="${LR_DOCDIR:-docs/development}"; fi
RM_EN="${LR_RM_EN:-docs/development/roadmap.md}"
RM_PT="${LR_RM_PT:-docs/development/roadmap.pt_BR.md}"
if [ -n "${LR_EN:-}" ]; then RM_ON="${LR_RM_ON-}"; else RM_ON=1; fi
MDP="${LR_MDPAIRS-}"; [ -n "${LR_EN:-}" ] || MDP="${LR_MDPAIRS:-docs/development}"
PREP_EN="${LR_PREP_EN:-docs/development/release-beta-0.5.0-prep.md}"
PREP_PT="${LR_PREP_PT:-docs/development/release-beta-0.5.0-prep.pt_BR.md}"
# Parte G so roda na corrida real (ou quando o teste a liga de proposito).
if [ -n "${LR_EN:-}" ]; then PREP_ON="${LR_PREP_ON-}"; else PREP_ON=1; fi
# Parte I so roda na corrida real (ou quando o teste a liga de proposito).
if [ -n "${LR_EN:-}" ]; then LQ_ON="${LR_QUEUE_ON-}"; else LQ_ON=1; fi

if [ "${LR_COUNT:-}" != "" ]; then
    COUNT="$LR_COUNT"
else
    COUNT="$(bash scripts/check_known_bugs_status.sh \
        | sed -nE 's/^EN open[^(]*\(([0-9]+)\).*/\1/p' | head -1)"
fi
# conjunto de ids vivos (para a parte H: a prep nao pode mandar viajar uma § ja fechada)
OPEN_IDS="${LR_OPEN_IDS:-}"
if [ -z "$OPEN_IDS" ]; then
    OPEN_IDS="$(bash scripts/check_known_bugs_status.sh \
        | sed -nE 's/^EN open[^(]*\([0-9]+\): (.*)/\1/p' | head -1)"
fi

if [ "${1:-}" = "--selftest" ]; then
    T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
    mk() { printf '%s\n' "$2" > "$T/$1"; }
    # A) contagem: bom, errado, ausente
    mk r_en.md 'x **19 items in the open queue** y'; mk r_pt.md 'x **19 itens na fila aberta** y'
    mk d_en.md '## D-PROPERTY — x';               mk d_pt.md '## D-PROPERTY — x'
    if ! LR_EN="$T/r_en.md" LR_PT="$T/r_pt.md" DEC_EN="$T/d_en.md" DEC_PT="$T/d_pt.md" LR_COUNT=19 bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: caso bom devia passar"; exit 1; fi
    mk r_en.md 'x **18 items in the open queue** y'
    if LR_EN="$T/r_en.md" LR_PT="$T/r_pt.md" DEC_EN="$T/d_en.md" DEC_PT="$T/d_pt.md" LR_COUNT=19 bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: contagem errada passou"; exit 1; fi
    mk r_en.md 'sem contagem aqui'
    if LR_EN="$T/r_en.md" LR_PT="$T/r_pt.md" DEC_EN="$T/d_en.md" DEC_PT="$T/d_pt.md" LR_COUNT=19 bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: ausencia de contagem passou (neutering)"; exit 1; fi
    # B) paridade/id duplicado
    mk r_en.md 'x **19 items in the open queue** y'
    mk d_en.md '## D-ONLY-EN — x'; mk d_pt.md '## D-OUTRA — x'
    if LR_EN="$T/r_en.md" LR_PT="$T/r_pt.md" DEC_EN="$T/d_en.md" DEC_PT="$T/d_pt.md" LR_COUNT=19 bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: decisao so no EN passou"; exit 1; fi
    mk d_en.md '## D-DUP — a
## D-DUP — a'; mk d_pt.md '## D-DUP — a'
    if LR_EN="$T/r_en.md" LR_PT="$T/r_pt.md" DEC_EN="$T/d_en.md" DEC_PT="$T/d_pt.md" LR_COUNT=19 bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: heading duplicado passou"; exit 1; fi
    mk d_en.md '# 1. X'; mk d_pt.md '## 1. X'   # mesmo numero, nivel divergente
    if LR_EN="$T/r_en.md" LR_PT="$T/r_pt.md" DEC_EN="$T/d_en.md" DEC_PT="$T/d_pt.md" LR_COUNT=19 bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: nivel de secao divergente passou"; exit 1; fi
    # J) marcador de conflito plantado deve ser capturado (parte J roda
    # com LR_MARKER_ROOTS apontando so para o fixture, com o resto do
    # ambiente do caso bom ja montado)
    mk c_ruim.md 'intro
<<<<<<< Updated upstream
a
=======
b
>>>>>>> Stashed changes'
    mk c_bom.md 'intro limpo'

    # D) README sec.0 pending <-> loose do gate (docdir/allowlist controlados)
    D="$T/dd"; mkdir -p "$D"
    printf 'ALLOWLIST="README.md README.pt_BR.md"\n' > "$T/gate.sh"
    : > "$D/work.md"
    cat > "$D/README.md" << 'EOF'
x **19 items in the open queue**
- **Pending (the release gate's condition 3):** `work.md`
- **Living records:** x
EOF
    cat > "$D/README.pt_BR.md" << 'EOF'
x **19 itens na fila aberta**
- **Pendentes (condição 3 do gate de release):** `work.md`
- **Registros vivos:** x
EOF
    mk d_en.md '# 1. X'; mk d_pt.md '# 1. X'
    if ! LR_EN="$D/README.md" LR_PT="$D/README.pt_BR.md" LR_DOCDIR="$D" LR_GATE="$T/gate.sh" \
         DEC_EN="$T/d_en.md" DEC_PT="$T/d_pt.md" LR_COUNT=19 bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: README pending == loose do gate devia passar"; exit 1; fi
    cat > "$D/README.md" << 'EOF'
x **19 items in the open queue**
- **Pending (the release gate's condition 3):** `outro.md`
- **Living records:** x
EOF
    if LR_EN="$D/README.md" LR_PT="$D/README.pt_BR.md" LR_DOCDIR="$D" LR_GATE="$T/gate.sh" \
         DEC_EN="$T/d_en.md" DEC_PT="$T/d_pt.md" LR_COUNT=19 bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: README pending divergente do gate passou"; exit 1; fi
    # E) roadmap EG: estado fechado/aberto EN<->PT (regra DONE|FEITO do gate)
    cat > "$T/rm_en.md" << 'EOF'
| EG-1 | x | DONE |
| EG-2 | y | OPEN |
EOF
    cat > "$T/rm_pt.md" << 'EOF'
| EG-1 | x | FEITO |
| EG-2 | y | ABERTO |
EOF
    if ! LR_EN="$T/r_en.md" LR_PT="$T/r_pt.md" DEC_EN="$T/d_en.md" DEC_PT="$T/d_pt.md" \
         LR_COUNT=19 LR_RM_EN="$T/rm_en.md" LR_RM_PT="$T/rm_pt.md" LR_RM_ON=1 bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: EG EN<->PT em paridade devia passar"; exit 1; fi
    cat > "$T/rm_pt.md" << 'EOF'
| EG-1 | x | ABERTO |
| EG-2 | y | ABERTO |
EOF
    if LR_EN="$T/r_en.md" LR_PT="$T/r_pt.md" DEC_EN="$T/d_en.md" DEC_PT="$T/d_pt.md" \
         LR_COUNT=19 LR_RM_EN="$T/rm_en.md" LR_RM_PT="$T/rm_pt.md" LR_RM_ON=1 bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: EG divergente EN<->PT passou"; exit 1; fi
    # F) numeracao/nivel em todos os pares EN<->PT do dir
    D2="$T/dp"; mkdir -p "$D2"
    printf '## 1. X\n' > "$D2/g.md"; printf '## 1. X\n' > "$D2/g.pt_BR.md"; printf '## 2. Y\n' > "$D2/h.md"
    if ! LR_EN="$T/r_en.md" LR_PT="$T/r_pt.md" DEC_EN="$T/d_en.md" DEC_PT="$T/d_pt.md" \
         LR_COUNT=19 LR_MDPAIRS="$D2" bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: pares EN<->PT em paridade deviam passar"; exit 1; fi
    printf '# 1. X\n' > "$D2/g.pt_BR.md"   # nivel divergente (H1 vs H2)
    if LR_EN="$T/r_en.md" LR_PT="$T/r_pt.md" DEC_EN="$T/d_en.md" DEC_PT="$T/d_pt.md" \
         LR_COUNT=19 LR_MDPAIRS="$D2" bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: nivel de secao divergente entre pares passou"; exit 1; fi
    # G) prep: contagem "N live" da cond.7 == autoridade
    # H) prep: § citada na secao "issues que viajam" tem de estar aberta
    PHE='## Open issues that travel to `beta-0.5.0`'
    PHP='## Issues abertas que viajam para `beta-0.5.0`'
    printf '| 7 | x | RED (19 live at the tip) |\n%s\n#550 (§371).\n' "$PHE" > "$T/prep_en.md"
    printf '| 7 | x | RED (19 live no tip) |\n%s\n#550 (§371).\n' "$PHP" > "$T/prep_pt.md"
    if ! LR_EN="$T/r_en.md" LR_PT="$T/r_pt.md" DEC_EN="$T/d_en.md" DEC_PT="$T/d_pt.md" \
         LR_COUNT=19 LR_PREP_EN="$T/prep_en.md" LR_PREP_PT="$T/prep_pt.md" LR_PREP_ON=1 \
         LR_OPEN_IDS="371" bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: prep cond.7 == autoridade + travel § aberta deviam passar"; exit 1; fi
    printf '| 7 | x | RED (18 live no tip) |\n%s\n#550 (§371).\n' "$PHP" > "$T/prep_pt.md"
    if LR_EN="$T/r_en.md" LR_PT="$T/r_pt.md" DEC_EN="$T/d_en.md" DEC_PT="$T/d_pt.md" \
         LR_COUNT=19 LR_PREP_EN="$T/prep_en.md" LR_PREP_PT="$T/prep_pt.md" LR_PREP_ON=1 \
         LR_OPEN_IDS="371" bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: prep cond.7 divergente da autoridade passou"; exit 1; fi
    printf '| 7 | x | RED (19 live no tip) |\n%s\n#550 (§999).\n' "$PHP" > "$T/prep_pt.md"
    if LR_EN="$T/r_en.md" LR_PT="$T/r_pt.md" DEC_EN="$T/d_en.md" DEC_PT="$T/d_pt.md" \
         LR_COUNT=19 LR_PREP_EN="$T/prep_en.md" LR_PREP_PT="$T/prep_pt.md" LR_PREP_ON=1 \
         LR_OPEN_IDS="371" bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: prep mandando viajar § fechada passou"; exit 1; fi
    mk prep_en.md 'sem contagem de live'
    if LR_EN="$T/r_en.md" LR_PT="$T/r_pt.md" DEC_EN="$T/d_en.md" DEC_PT="$T/d_pt.md" \
         LR_COUNT=19 LR_PREP_EN="$T/prep_en.md" LR_PREP_PT="$T/prep_pt.md" LR_PREP_ON=1 \
         LR_OPEN_IDS="371" bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: prep sem contagem passou (neutering)"; exit 1; fi
    # I) fila oficial sec.1 nomeia todo loose doc do gate
    QI="$T/qi"; mkdir -p "$QI"
    printf 'ALLOWLIST="README.md README.pt_BR.md"\n' > "$T/gate_i.sh"
    : > "$QI/work.md"; mk d_en.md '# 1. X'; mk d_pt.md '# 1. X'
    cat > "$QI/README.md" << 'EOF'
x **19 items in the open queue**
- **Pending (the release gate's condition 3):** `work.md`
## 1. Plan execution order
| 1 | `work.md` | IN DEV |
EOF
    cat > "$QI/README.pt_BR.md" << 'EOF'
x **19 itens na fila aberta**
- **Pendentes (condição 3 do gate de release):** `work.md`
## 1. Ordem de execução dos planos
| 1 | `work.md` | EM DEV |
EOF
    if ! LR_EN="$QI/README.md" LR_PT="$QI/README.pt_BR.md" LR_DOCDIR="$QI" LR_GATE="$T/gate_i.sh" \
         LR_QUEUE_ON=1 DEC_EN="$T/d_en.md" DEC_PT="$T/d_pt.md" LR_COUNT=19 bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: loose doc nomeado na fila sec.1 devia passar"; exit 1; fi
    cat > "$QI/README.pt_BR.md" << 'EOF'
x **19 itens na fila aberta**
- **Pendentes (condição 3 do gate de release):** `work.md`
## 1. Ordem de execução dos planos
| 1 | `outro.md` | EM DEV |
EOF
    if LR_EN="$QI/README.md" LR_PT="$QI/README.pt_BR.md" LR_DOCDIR="$QI" LR_GATE="$T/gate_i.sh" \
         LR_QUEUE_ON=1 DEC_EN="$T/d_en.md" DEC_PT="$T/d_pt.md" LR_COUNT=19 bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: loose doc ausente da fila PT passou"; exit 1; fi
    cat > "$QI/README.pt_BR.md" << 'EOF'
x **19 itens na fila aberta**
- **Pendentes (condição 3 do gate de release):** `work.md`
sem secao 1 aqui
EOF
    if LR_EN="$QI/README.md" LR_PT="$QI/README.pt_BR.md" LR_DOCDIR="$QI" LR_GATE="$T/gate_i.sh" \
         LR_QUEUE_ON=1 DEC_EN="$T/d_en.md" DEC_PT="$T/d_pt.md" LR_COUNT=19 bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: README sem a fila sec.1 passou (neutering)"; exit 1; fi
    cat > "$QI/README.pt_BR.md" << 'EOF'
x **19 itens na fila aberta**
- **Pendentes (condição 3 do gate de release):** `work.md`
## 1. Ordem de execução dos planos
| 1 | `work.md` | EM DEV |
EOF
    # J) marcador de conflito plantado deve ser SEMPRE capturado; fixture
    #    limpo deve passar (mut-test da classe 794aa4721: markers commitados)
    if LR_EN="$QI/README.md" LR_PT="$QI/README.pt_BR.md" LR_DOCDIR="$QI" LR_GATE="$T/gate_i.sh" \
         LR_QUEUE_ON=1 DEC_EN="$T/d_en.md" DEC_PT="$T/d_pt.md" LR_COUNT=19 \
         LR_MARKER_ROOTS="$T/c_ruim.md" bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: marcador de conflito plantado passou (parte J cega)"; exit 1; fi
    if ! LR_EN="$QI/README.md" LR_PT="$QI/README.pt_BR.md" LR_DOCDIR="$QI" LR_GATE="$T/gate_i.sh" \
         LR_QUEUE_ON=1 DEC_EN="$T/d_en.md" DEC_PT="$T/d_pt.md" LR_COUNT=19 \
         LR_MARKER_ROOTS="$T/c_bom.md" bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: fixture limpo reprovado pela parte J"; exit 1; fi
    echo "SELFTEST OK: contagem + paridade + duplicata + numeracao + pending<->gate + EG + pares + prep(G/H) + fila(I) + marcadores(J)"
    exit 0
fi

[ -n "${COUNT:-}" ] || { echo "FALHA: nao extrai a contagem da autoridade (formato mudou?)"; exit 1; }

python3 - "$EN" "$PT" "$COUNT" "$DEN" "$DPT" "$GATE" "$DOCDIR" "$RM_EN" "$RM_PT" "$RM_ON" "$MDP" \
          "$PREP_EN" "$PREP_PT" "$PREP_ON" "$OPEN_IDS" "$LQ_ON" << 'PYEOF'
import re, sys, os, glob
en, pt, count, den, dpt, gate, docdir = sys.argv[1:8]
rme, rmpt, rmon = sys.argv[8:11]
mdp = sys.argv[11] if len(sys.argv) > 11 else ""
prep_en = sys.argv[12] if len(sys.argv) > 12 else ""
prep_pt = sys.argv[13] if len(sys.argv) > 13 else ""
prep_on = sys.argv[14] if len(sys.argv) > 14 else ""
open_ids = set((sys.argv[15].split() if len(sys.argv) > 15 else []))
lq_on = sys.argv[16] if len(sys.argv) > 16 else ""
bad = 0

# ---- A) contagem viva x autoridade -----------------------------------------
pat = re.compile(r"\*\*([0-9]+) (?:items?|live|itens?|vivos?)\b[^*]*\*\*")
seen = 0
for path in (en, pt):
    try:
        text = open(path, encoding="utf-8").read()
    except OSError:
        print(f"FALHA: nao consigo ler {path}"); bad = 1; continue
    hits = pat.findall(text)
    if not hits:
        print(f"FALHA: nenhuma declaracao de contagem viva em {path} "
              "(regex nao casa mais — atualize o gate junto com a prosa)")
        bad = 1; continue
    seen += len(hits)
    for n in hits:
        if n != count:
            print(f"DRIFT: {path} declara '{n}' mas a autoridade conta '{count}'"); bad = 1

# ---- B) DECISIONS: paridade EN<->PT + heading duplicado --------------------
IDPAT = re.compile(r"(?m)^#{2,3} (D-[A-Z0-9][A-Z0-9.-]*|R[0-9][A-Z0-9-]*)")
TEMPLATE = {"D-XXXX"}
sets = {}
for lang, path in (("EN", den), ("PT", dpt)):
    try:
        text = open(path, encoding="utf-8").read()
    except OSError:
        print(f"FALHA: nao consigo ler {path}"); bad = 1; continue
    ids = {m for m in IDPAT.findall(text) if m not in TEMPLATE}
    sets[lang] = ids
    heads = re.findall(r"(?m)^## .*$", text)  # so nivel 2: ### Context/Decision repetem de proposito
    dups = sorted({h for h in heads if heads.count(h) > 1})
    for h in dups:
        print(f"DUPLICATA: heading de secao repetido em {path}: {h[:70]}"); bad = 1
if sets.get("EN") is not None and sets.get("PT") is not None:
    only_en = sorted(sets["EN"] - sets["PT"])
    only_pt = sorted(sets["PT"] - sets["EN"])
    for d in only_en:
        print(f"PARIDADE: decisao so no EN (sem espelho PT): {d}"); bad = 1
    for d in only_pt:
        print(f"PARIDADE: decisao so no PT (sem espelho EN): {d}"); bad = 1

# ---- C) DECISIONS: numeracao de secoes (N.) — numero E nivel iguais EN<->PT
NUMPAT = re.compile(r"(?m)^(#{1,6}) ([0-9]+)\. ")
lvl = {}
for lang, path in (("EN", den), ("PT", dpt)):
    try:
        text = open(path, encoding="utf-8").read()
    except OSError:
        continue
    lvl[lang] = {n: len(h) for h, n in NUMPAT.findall(text)}
if "EN" in lvl and "PT" in lvl:
    for n in sorted(set(lvl["EN"]) | set(lvl["PT"]), key=int):
        a, b = lvl["EN"].get(n), lvl["PT"].get(n)
        if a != b:
            print(f"NUMERACAO: secao {n}. tem nivel EN={a} PT={b} (numeracao e nivel devem bater)")
            bad = 1

# ---- D) README sec.0 "Pending (cond. 3)" <-> loose set medido pelo gate ----
if docdir:
    try:
        gtext = open(gate, encoding="utf-8").read()
    except OSError:
        print(f"FALHA: nao consigo ler o gate {gate}"); bad = 1; gtext = ""
    ms = re.findall(r'ALLOWLIST="([^"]*)"', gtext)
    if not ms:
        print(f"FALHA: ALLOWLIST nao encontrado em {gate} (formato mudou — atualize o gate)")
        bad = 1
    else:
        allow = set()
        for m in ms:
            allow |= set(m.split())
        try:
            on_disk = os.listdir(docdir)
        except OSError:
            print(f"FALHA: nao consigo listar {docdir}"); bad = 1; on_disk = []
        loose = sorted(f for f in on_disk
                       if f.endswith(".md") and not f.endswith(".pt_BR.md") and f not in allow)
        for lang, path, marker in (
                ("EN", en, "Pending (the release gate's condition 3)"),
                ("PT", pt, "Pendentes (condi\u00e7\u00e3o 3 do gate de release)")):
            try:
                lines = open(path, encoding="utf-8").read().splitlines()
            except OSError:
                print(f"FALHA: nao consigo ler {path}"); bad = 1; continue
            idx = next((i for i, l in enumerate(lines) if marker in l), None)
            if idx is None:
                print(f"FALHA: {path} sem a lista de pendentes da cond. 3 "
                      "(prosa mudou — atualize o gate junto com o README)"); bad = 1; continue
            bullet = []
            for l in lines[idx:]:
                if bullet and (not l.strip() or l.lstrip().startswith("- ")):
                    break
                bullet.append(l)
            named = sorted(set(re.findall(r"`([A-Za-z0-9._-]+\.md)`", "\n".join(bullet))))
            missing = [d for d in loose if d not in named]
            stale = [d for d in named if d not in loose]
            if missing:
                print(f"DRIFT ({lang}): loose marcado pelo gate mas ausente do README sec.0: {missing}")
                bad = 1
            if stale:
                print(f"DRIFT ({lang}): README sec.0 lista como pendente mas o gate nao marca: {stale}")
                bad = 1

# ---- E) roadmap EG: estado fechado/aberto EN<->PT (regra do gate) -----------
if rmon:
    def eg_closed(path):
        try:
            text = open(path, encoding="utf-8").read()
        except OSError:
            return None
        d = {}
        for line in text.splitlines():
            m = re.match(r"\|\s*(EG-[0-9]+)\s*\|", line)
            if m:
                d[m.group(1)] = bool(re.search(r"DONE|FEITO", line))
        return d
    key = lambda x: int(x.split("-")[1])
    a, b = eg_closed(rme), eg_closed(rmpt)
    if not a or not b:
        print("FALHA: tabela EG ilegivel/ausente no roadmap (EN ou PT) — prosa mudou?")
        bad = 1
    else:
        only_a = sorted(set(a) - set(b), key=key)
        only_b = sorted(set(b) - set(a), key=key)
        if only_a or only_b:
            print(f"PARIDADE (EG): linhas so no EN={only_a} so no PT={only_b}"); bad = 1
        for k in sorted(set(a) & set(b), key=key):
            if a[k] != b[k]:
                print(f"PARIDADE (EG): {k} fechado EN={a[k]} PT={b[k]} "
                      "(mesma regra DONE|FEITO do gate)"); bad = 1

# ---- F) TODOS os pares EN<->PT: numeracao/nivel de secoes (N.) -------------
if mdp:
    NP = re.compile(r"(?m)^(#{1,6}) ([0-9]+)[.)] ")
    def numbered(path):
        try:
            text = open(path, encoding="utf-8").read()
        except OSError:
            return None
        return {n: len(h) for h, n in NP.findall(text)}
    for enp in sorted(glob.glob(os.path.join(mdp, "*.md"))):
        if enp.endswith(".pt_BR.md"):
            continue
        ptp = enp[:-3] + ".pt_BR.md"
        if not os.path.exists(ptp):
            continue
        a, b = numbered(enp), numbered(ptp)
        if a is None or b is None:
            continue
        for n in sorted(set(a) | set(b), key=int):
            if a.get(n) != b.get(n):
                print(f"NUMERACAO: {os.path.basename(enp)} secao {n}. "
                      f"nivel EN={a.get(n)} PT={b.get(n)} (numeracao e nivel devem bater)")
                bad = 1

# ---- G) prep do release: contagem "N live" da cond.7 == autoridade ----------
if prep_on:
    PP = re.compile(r"([0-9]+) live (?:at the tip|no tip)")
    for lang, path in (("EN", prep_en), ("PT", prep_pt)):
        try:
            text = open(path, encoding="utf-8").read()
        except OSError:
            print(f"FALHA: nao consigo ler a prep do release ({lang}) {path}")
            bad = 1; continue
        hits = PP.findall(text)
        if not hits:
            print(f"FALHA: prep {lang} sem a contagem 'N live ... tip' "
                  "(regex nao casa mais — atualize o gate junto com a prosa)")
            bad = 1; continue
        for n in hits:
            if n != count:
                print(f"DRIFT ({lang}): prep declara '{n} live' mas a autoridade conta '{count}'")
                bad = 1

# ---- H) prep: § da secao "issues que viajam" tem de estar ABERTA -----------
if prep_on:
    HDR = re.compile(r"(?m)^##+ .*(?:travel to|viajam para).*$")
    for lang, path in (("EN", prep_en), ("PT", prep_pt)):
        try:
            text = open(path, encoding="utf-8").read()
        except OSError:
            print(f"FALHA: nao consigo ler a prep do release ({lang}) {path}")
            bad = 1; continue
        m = HDR.search(text)
        if not m:
            print(f"FALHA: prep {lang} sem a secao 'issues que viajam' "
                  "(header mudou? atualize o gate junto com a prosa)")
            bad = 1; continue
        rest = text[m.end():]
        nxt = re.search(r"(?m)^## ", rest)
        section = rest[:nxt.start()] if nxt else rest
        for n in re.findall(r"§([0-9]+)", section):
            if n not in open_ids:
                print(f"DRIFT ({lang}): prep manda viajar §{n}, que NAO esta no conjunto aberto")
                bad = 1

# ---- I) fila oficial (README sec.1) nomeia TODO loose doc do gate -----------
if lq_on and docdir:
    try:
        gtext_i = open(gate, encoding="utf-8").read()
    except OSError:
        print(f"FALHA: nao consigo ler o gate {gate} (parte I)"); bad = 1; gtext_i = ""
    ms_i = re.findall(r'ALLOWLIST="([^"]*)"', gtext_i)
    if not ms_i:
        print(f"FALHA: ALLOWLIST nao encontrado em {gate} (parte I — atualize o gate)")
        bad = 1
    else:
        allow_i = set()
        for m in ms_i:
            allow_i |= set(m.split())
        try:
            loose_i = sorted(f for f in os.listdir(docdir)
                             if f.endswith(".md") and not f.endswith(".pt_BR.md")
                             and f not in allow_i)
        except OSError:
            print(f"FALHA: nao consigo listar {docdir} (parte I)"); bad = 1; loose_i = []
        QHDR = re.compile(r"^## 1[.)] ")
        for lang, path in (("EN", en), ("PT", pt)):
            try:
                q = open(path, encoding="utf-8").read().splitlines()
            except OSError:
                print(f"FALHA: nao consigo ler {path} (parte I)"); bad = 1; continue
            qi = next((i for i, l in enumerate(q) if QHDR.match(l)), None)
            if qi is None:
                print(f"FALHA: {path} sem a secao '1.' da fila oficial "
                      "(prosa mudou — atualize o gate junto com o README)"); bad = 1; continue
            qj = next((j for j in range(qi + 1, len(q))
                       if q[j].startswith("## ")), len(q))
            qsec = "\n".join(q[qi:qj])
            missing_i = [d for d in loose_i if d not in qsec]
            if missing_i:
                print(f"DRIFT ({lang}): loose doc do gate ausente da fila oficial sec.1 "
                      f"do README: {missing_i}")
                bad = 1

# ---- J) NENHUM arquivo de registro pode conter marcadores de conflito ------
# (794aa4721 commitou '<<<<<<< Updated upstream' nos dois DECISIONS e nenhum
# gate pegou; a politica da casa exige resolver conflito na hora, nunca
# commitar por cima - este bloco e a trava permanente dessa classe.)
import re as _re
_MARKER = _re.compile(r'^(<{7,}|>{7,}|={7,})', _re.M)
_roots = [x for x in os.environ.get("LR_MARKER_ROOTS", "").split(":") if x]
if not _roots:
    _roots = ["docs", "DOING.md", "AGENTS.md", "AGENTS.pt_BR.md",
              "CHANGELOG.md", "CHANGELOG.pt_BR.md"]
_scanned = 0
for root in _roots:
    if os.path.isdir(root):
        for dirpath, _d, files in os.walk(root):
            for f in sorted(files):
                if f.endswith(".md"):
                    _scanned += 1
                    fp = os.path.join(dirpath, f)
                    try:
                        txt = open(fp, encoding="utf-8", errors="replace").read()
                    except OSError:
                        print(f"FALHA: nao consigo ler {fp} (parte J)"); bad = 1; continue
                    for mm in _MARKER.finditer(txt):
                        ln = txt.count("\n", 0, mm.start()) + 1
                        print(f"FALHA: marcador de conflito commitado em {fp}:{ln} "
                              f"('{mm.group(1)}') - resolva preservando os dois lados")
                        bad = 1
    elif os.path.isfile(root):
        _scanned += 1
        txt = open(root, encoding="utf-8", errors="replace").read()
        for mm in _MARKER.finditer(txt):
            ln = txt.count("\n", 0, mm.start()) + 1
            print(f"FALHA: marcador de conflito em {root}:{ln} - politica da casa")
            bad = 1

if not bad:
    print(f"OK: contagem viva {count} consistente ({seen} declaracoes); "
          f"DECISIONS EN<->PT com {len(sets.get('EN', ()))} IDs em paridade, 0 duplicatas, "
          "numeracao/nivel em paridade"
          + ("; pendentes sec.0 == loose do gate" if docdir else "")
          + ("; fila sec.1 cobre todo loose doc do gate" if (lq_on and docdir) else "")
          + ("; roadmap EG EN<->PT em paridade" if rmon else "")
          + ("; numeracao/nivel de todos os pares EN<->PT" if mdp else "")
          + ("; prep cond.7 == autoridade + travel § aberta" if prep_on else "")
          + f"; 0 marcadores de conflito em {_scanned} arquivos")
sys.exit(1 if bad else 0)
PYEOF
