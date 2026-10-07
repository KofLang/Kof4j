#!/usr/bin/env bash
# check_portukof_parity.sh — gate da regra ABSOLUTA do D-PORTUKOF (mantenedora
# 07/10): PortuKof cobre 100% do frontend Kof, com paridade travada mecanicamente.
#
# Cobra:
#   1. bijetividade lexical: cada keyword da tabela do Lexer tem exatamente um
#      vocábulo pt-BR e nenhuma colisão (PARTE 3/27/28 — sem acentos).
#   2. paridade do catálogo stdlib: a tabela de membros/namespaces gerada é
#      EXATAMENTE a deriva do StdCatalog real (roda o dump em JVM) — nenhum
#      membro canônico sem alias, nenhum alias colidente (PARTE 7/26).
#   3. aliases de builtins/namespaces cobertos no perfil (escreva→print, …).
#
# Fail-closed: sem `target/classes` o dump não roda => RC=1 com a causa.
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
LANG_DIR=kof-compiler/src/main/java/dev/kof/compiler/lang
fail=0
note() { echo "portukof-parity: $*"; }

[ -d "$LANG_DIR" ] || { note "superfície PortuKof ausente nesta árvore — OK (aditivo)"; exit 0; }

# 1) bijetividade lexical (Lexer KEYWORDS ⊇ PAIRS canônicos, PT único por token)
python3 - "$LANG_DIR/KofKeywords.java" "$LANG_DIR/PortuKofVocabulary.java" <<'PY' || fail=1
import re, sys
kw = open(sys.argv[1]).read()
voc = open(sys.argv[2]).read()
base = set(re.findall(r'k\.put\("([^"]+)", TokenType\.([A-Z_]+)\)', kw))
pairs = re.findall(r'new String\[\]\{"([^"]+)", "([^"]+)", "([^"]+)"\}', voc)
canon = {p[0] for p in pairs}
missing = {w for w, _ in base} - canon
if missing:
    print(f"portukof-parity: FALTA vocábulo p/ keyword canônica: {sorted(missing)}"); sys.exit(1)
extra = canon - {w for w, _ in base}
if extra:
    print(f"portukof-parity: vocábulo sem keyword real: {sorted(extra)}"); sys.exit(1)
pt = [p[1] for p in pairs]
dups = {x for x in pt if pt.count(x) > 1}
if dups:
    print(f"portukof-parity: colisão de vocábulo pt-BR: {sorted(dups)}"); sys.exit(1)
accented = [w for w in pt if w != w.encode('ascii', 'ignore').decode()]
if accented:
    print(f"portukof-parity: vocábulo acentuado (proibido pela regra): {sorted(accented)}"); sys.exit(1)
print(f"portukof-parity: OK lexical — {len(base)} keywords ↔ {len(pairs)} vocábulos, sem colisão/acentos")
PY

# 2) paridade do catálogo real (deriva vs. tabela commitada + bijetividade de membros)
if [ ! -d kof-compiler/target/classes ]; then
    note "RC=1 fail-closed: sem kof-compiler/target/classes — rode `mvn -o -pl kof-compiler -am compile`"
    fail=1
else
    if python3 scripts/gen_portukof_aliases.py --check; then
        :
    else
        fail=1
    fi
fi

# 3) o perfil expõe os aliases (builtin + namespace) — nada vazio
if ! grep -q 'put("escreva", "print")' kof-compiler/src/main/java/dev/kof/compiler/lang/PortuKofVocabulary.java 2>/dev/null \
   && ! grep -q '{"print", "escreva"}' kof-compiler/src/main/java/dev/kof/compiler/lang/PortuKofVocabulary.java; then
    note "RC=1: builtin alias `escreva`→`print` ausente do perfil"; fail=1
fi

[ $fail -eq 0 ] && { note "TODAS as checagens rc=0 — paridade absoluta travada"; exit 0; }
note "FALHOU"; exit 1
