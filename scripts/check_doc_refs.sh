#!/usr/bin/env bash
#
# check_doc_refs.sh — integridade das REFERENCIAS nos docs:
#   1) todo `docs/**/*.md` citado (em QUALQUER .md rastreado do repositorio) existe
#      — pega movimentos/renames que deixaram a referencia para tras
#      (native-multiarch.md, language/types.md na lane; workflow-plan,
#      ecosystem-coverage, PHASE_F runtime e CONFORMANCE_MATRIX no corpus, 21/09;
#      learn/38 e learn/39 apontando planos movidos, #648/#654).
#      Escopo da varredura (#654, contrato != implementacao): todo `.md` rastreado
#      no git, MENOS historia explicita: docs/history/, docs/audits/, dist/,
#      CHANGELOG*, DOING* (registros datados — mesma classe dos waivers do
#      doc-impact; estado, nao historia). learn/, training/, raiz (AGENTS/README/
#      CONTRIBUTING) e .github/ ENTRAM — eram o angulo morto.
#   2) todo SHA hex entre crases (8..40) citado nos docs da lane (docs/development)
#      existe no git — pega prova orfa (o repair de git de 21/09 reescreveu historico
#      e deixou 11 SHAs citados sem objeto correspondente).
# Referencias legitimas que nao resolvem (nota historica de rename, outro repo,
# destino de movimento futuro, doc prospectivo) ficam explicitas em
# scripts/doc-refs-waivers.txt, datadas — a mesma classe dos changelog-ledger-waivers.
#
# AUSENCIA e FALHA: um path/sha novo que nao resolve e rc!=0 ate ser corrigido ou
# justificado no waiver (regra anti-neutering da lane).
#
# Uso: scripts/check_doc_refs.sh            # rc!=0 se houver referencia orfa
#      scripts/check_doc_refs.sh --selftest # casos bons e ruins plantados
# Env (teste): DOCREF_SCAN_LIST (arquivo com um .md por linha — substitui o
#              git ls-files), DOCREF_SHA_DIR, DOCREF_WAIVERS
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
SDIR="${DOCREF_SHA_DIR:-docs/development}"
WAV="${DOCREF_WAIVERS:-scripts/doc-refs-waivers.txt}"

if [ "${1:-}" = "--selftest" ]; then
    T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
    mkdir -p "$T/d" "$T/d/learn"
    HEAD_SHA="$(git rev-parse HEAD)"
    printf 'ref %s\nsha `%s`\n' 'docs/development/README.md' "$HEAD_SHA" > "$T/d/learn/a.md"
    printf '%s\n' "$T/d/learn/a.md" > "$T/scan"
    if ! DOCREF_SCAN_LIST="$T/scan" DOCREF_SHA_DIR="$T/d" DOCREF_WAIVERS="$T/w" bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: ref valida em learn/ (fora de docs/) devia passar"; exit 1; fi
    printf 'ref %s\n' 'docs/development/nao-existe-xyz.md' > "$T/d/learn/a.md"
    if DOCREF_SCAN_LIST="$T/scan" DOCREF_SHA_DIR="$T/d" DOCREF_WAIVERS="$T/w" bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: path inexistente em learn/ passou (cenario #654)"; exit 1; fi
    printf 'ref OK\n' > "$T/d/learn/a.md"
    printf 'sha `deadbeefdeadbeef`\n' > "$T/d/b.md"
    printf '%s\n%s\n' "$T/d/learn/a.md" "$T/d/b.md" > "$T/scan"
    if DOCREF_SCAN_LIST="$T/scan" DOCREF_SHA_DIR="$T/d" DOCREF_WAIVERS="$T/w" bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: SHA inexistente passou"; exit 1; fi
    printf 'path\tdocs/development/nao-existe-xyz.md\tfixture\nsha\tdeadbeefdeadbeef\tfixture\n' > "$T/w"
    printf 'ref %s\nsha `deadbeefdeadbeef`\n' 'docs/development/nao-existe-xyz.md' > "$T/d/learn/a.md"
    printf '%s\n' "$T/d/learn/a.md" > "$T/scan"
    if ! DOCREF_SCAN_LIST="$T/scan" DOCREF_SHA_DIR="$T/d" DOCREF_WAIVERS="$T/w" bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: path+sha no waiver deviam passar"; exit 1; fi
    # angulo morto #654: ref RELATIVA (nao comeca com docs/) tem de ser varrida
    mkdir -p "$T/empty"
    printf 'ref future/nao-existe-xyz.md\n' > "$T/d/learn/a.md"
    if DOCREF_SCAN_LIST="$T/scan" DOCREF_SHA_DIR="$T/empty" DOCREF_WAIVERS="$T/w" bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: ref relativa morta (future/...) passou"; exit 1; fi
    printf 'ref development/README.md\n' > "$T/d/learn/a.md"
    if ! DOCREF_SCAN_LIST="$T/scan" DOCREF_SHA_DIR="$T/empty" DOCREF_WAIVERS="$T/w" bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: ref relativa viva (development/README.md) barrada"; exit 1; fi
    echo "SELFTEST OK: learn/ varrido (cenario #654) + path/SHA valido/inexistente + ref relativa morta/viva + waiver"
    exit 0
fi
[ -d "$SDIR" ] || { echo "FALHA: diretorio de docs (SHAs) inexistente: $SDIR"; exit 1; }

python3 - "$SDIR" "$WAV" << 'PYEOF'
import re, sys, os, glob, subprocess
sdir, wav = sys.argv[1], sys.argv[2]

def scan_files():
    # contrato #654: qualquer .md rastreado; historia explicita fica de fora
    lst = os.environ.get("DOCREF_SCAN_LIST")
    if lst:
        return [l.strip() for l in open(lst, encoding="utf-8") if l.strip()]
    out = subprocess.run(["git", "ls-files", "-z", "--", "*.md"],
                         capture_output=True, text=False).stdout.decode()
    hist = ("docs/history/", "docs/audits/", "dist/", "CHANGELOG", "DOING.")
    return [f for f in out.split("\0")
            if f.endswith(".md") and not f.startswith(hist) and "/target/" not in f]
wpath, wsha = set(), set()
try:
    for line in open(wav, encoding="utf-8"):
        line = line.split("#", 1)[0].rstrip("\n")
        if not line.strip():
            continue
        parts = line.split("\t")
        if len(parts) < 2:
            continue
        kind, tok = parts[0].strip(), parts[1].strip()
        if kind == "path":
            wpath.add(tok)
        elif kind == "sha":
            wsha.add(tok)
except OSError:
    pass  # sem arquivo de waiver != passe livre: so nada esta dispensado

bad = 0
cp = cs = 0
# diretorios reais sob docs/ (angulo morto do contrato #654: `future/x.md`,
# `bugs-and-gaps/y.md` etc. NAO comecam com `docs/`, entao o regex abaixo os
# ignora e um movimento deixa a referencia apodrecendo em silencio — exatamente
# o que a lane docs-audit varreu a mao em 30/09; agora e pego por maquina).
BARE_DIRS = ("future", "development", "bugs-and-gaps", "stdlib", "architecture",
             "spec", "tooling", "decisions", "testing", "ui", "debugging")
bare_re = re.compile(r"(?<![\w./-])(" + "|".join(BARE_DIRS) + r")/[A-Za-z0-9/_.-]+\.md")
cb = 0
for f in sorted(scan_files()):
    text = open(f, encoding="utf-8").read()
    text = re.sub(r"https?://\S+", "", text)  # URLs nao sao paths do repo
    base = os.path.basename(f)
    fdir = os.path.dirname(f)
    for p in sorted(set(re.findall(r"docs/[A-Za-z0-9/_.-]+\.md", text))):
        cp += 1
        if not os.path.exists(p) and p not in wpath:
            print(f"REF QUEBRADA ({base}): {p} nao existe no repo"); bad = 1
    # refs relativas ao doc (padrao markdown) ou a docs/: resolve contra o
    # diretorio do arquivo, depois docs/, depois a raiz.
    for p in sorted(set(m.group(0) for m in bare_re.finditer(text))):
        cb += 1
        cands = (os.path.normpath(os.path.join(fdir, p)), os.path.join("docs", p), p)
        if any(os.path.exists(c) for c in cands) or p in wpath:
            continue
        print(f"REF QUEBRADA (rel) ({base}): {p} nao resolve (de {fdir}/ nem de docs/)"); bad = 1
for f in sorted(glob.glob(os.path.join(sdir, "*.md"))):
    base = os.path.basename(f)
    text = re.sub(r"https?://\S+", "", open(f, encoding="utf-8").read())
    for s in sorted(set(re.findall(r"`([0-9a-f]{8,40})`", text))):
        cs += 1
        if s in wsha:
            continue
        if subprocess.run(["git", "cat-file", "-e", s], capture_output=True).returncode != 0:
            print(f"SHA FANTASMA ({base}): {s} nao existe no repo"); bad = 1
if not bad:
    print(f"OK: {cp} refs de path em TODO .md rastreado (menos historia) + {cb} refs relativas "
          f"(future/, bugs-and-gaps/, ...) + {cs} SHAs (development) integros "
          f"({len(wpath)} paths + {len(wsha)} shas no waiver)")
sys.exit(1 if bad else 0)
PYEOF
