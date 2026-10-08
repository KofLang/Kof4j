#!/usr/bin/env bash
# check_portukof_tooling.sh — gate F7 (D-PORTUKOF): `.ptkf` é CIDADÃO DE PRIMEIRA
# CLASSE do tooling Kof, sem segundo motor. Falha (rc=1) se a superfície PT deixar
# de ser tratada como superfície do MESMO core, ou se alguém duplicar lógica.
#
# Princípios travados mecanicamente:
#   * a EXTENSÃO `.ptkf` é a autoridade do perfil em toda a cadeia (CLI/LSP/fmt);
#   * o formatter NUNCA transpila PT→EN (guard anti-transpile presente);
#   * o CONTRATO DE MÁQUINA (LSP/`--json`) usa a mensagem canônica EN, nunca PT;
#   * NÃO existe segundo engine PortuKof no tooling (golden rule §4): a paridade
#     vem do `LanguageProfile` + catálogo canônico, nunca de uma classe paralela.
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
CLI=kof-cli/src/main/java/dev/kof/cli
COMP=kof-compiler/src/main/java/dev/kof/compiler
fail=0
note() { echo "portukof-tooling: $*"; }

must_have() { # file pattern human
    local f="$1" pat="$2" msg="$3"
    if [ -f "$f" ] && grep -qE "$pat" "$f"; then :; else
        note "FALTA: $msg ($f)"; fail=1
    fi
}
must_not() { # file pattern human
    local f="$1" pat="$2" msg="$3"
    if [ -f "$f" ] && grep -qE "$pat" "$f"; then
        note "PROIBIDO: $msg ($f)"; fail=1
    fi
}

# 1) CLI discovery — a extensão .ptkf é fonte primária
must_have "$CLI/KofCliSupport.java" '"\.ptkf"' \
    "KofCliSupport.isKofSource deve reconhecer .ptkf"

# 2) LSP authority — fileNameOf preserva .ptkf (não colapsa para LspMain.kf)
must_have "$CLI/LspProject.java" '"\.ptkf"' \
    "LspProject.fileNameOf deve preservar a extensão .ptkf"
must_have "$CLI/LspProject.java" 'endsWith\("\.ptkf"\)|n\.endsWith\("\.ptkf"\)' \
    "LspProject deve tratar .ptkf como fonte canônica no espelho/irmãos"

# 3) Formatter anti-transpile — AST-printer recusa .ptkf ANTES de reimprimir EN
must_have "$COMP/KofFormatter.java" 'LanguageProfile\.PORTUKOF == LanguageProfile\.forFileName\(fileName\)' \
    "KofFormatter deve ter o guard anti-transpile para PortuKof"
must_have "$CLI/Fmt.java" 'format\(in, f\.getFileName\(\)\.toString\(\)\)' \
    "Fmt deve passar o nome real do arquivo ao formatter (não Main.kf fixo)"

# 4) Editor manifests registram .ptkf (mesma language Kof, superfície PT)
must_have "$CLI/editor/VscodeExtensionContent.java" '"\.ptkf"' \
    "manifesto vscode deve registrar .ptkf"
must_have "$CLI/editor/KofEditorContent.java" 'ptkf' \
    "filetype IntelliJ deve registrar ptkf"

# 5) CONTRATO DE MÁQUINA intacto — LSP/JSON usam a mensagem canônica EN, nunca PT
must_have "$CLI/LspDiagnostics.java" '\.message\(\)' \
    "LspDiagnostics deve publicar d.message() (EN canônico)"
must_not "$CLI/LspDiagnostics.java" 'localizedMessage' \
    "LSP NÃO pode publicar mensagem localizada (contrato de máquina)"
must_not "$CLI/CmdCheck.java" 'localizedMessage' \
    "--json NÃO pode publicar mensagem localizada (contrato de máquina)"

# 6) GOLDEN RULE — nenhum segundo engine PortuKof no tooling (paridade vem do core)
if ls "$CLI"/PortuKof*.java >/dev/null 2>&1; then
    note "PROIBIDO: classe PortuKof* duplicada no tooling kof-cli (golden rule §4)"; fail=1
fi
if grep -rqE 'class PortuKof(Completion|Hover|Signature|Symbols|Definition|References|Rename|Formatter|Semantic|Token)' "$CLI" 2>/dev/null; then
    note "PROIBIDO: engine de tooling PortuKof paralelo ao canônico"; fail=1
fi

# 7) A bateria de paridade de tooling existe (prova executada pelo mvn)
if [ ! -f kof-cli/src/test/java/dev/kof/cli/PortuKofToolingE2ETest.java ]; then
    note "FALTA: PortuKofToolingE2ETest (prova da paridade de tooling)"; fail=1
fi

[ $fail -eq 0 ] && { note "F7 OK — .ptkf first-class no tooling, sem segundo engine, contrato de máquina intacto"; exit 0; }
note "FALHOU"; exit 1
