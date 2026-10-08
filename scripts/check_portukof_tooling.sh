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

# ---- F7.2 — RENDERIZAÇÃO DE SUPERFÍCIE (ponte única) + imports `.ptkf` ----

# 8) A ponte CANONICAL→SURFACE vive em UM lugar (lang/SurfaceNames) e é a única
#    fonte pedida pelo tooling; nenhum arquivo de tooling re-implementa alias.
LANGPKG=$COMP/lang
must_have "$LANGPKG/SurfaceNames.java" 'class SurfaceNames' \
    "SurfaceNames deve existir como ponte única canônico→superfície"
must_have "$LANGPKG/SurfaceNames.java" 'PortuKofVocabulary' \
    "SurfaceNames delega ao vocabulário gateado (fonte única)"
must_have "$LANGPKG/SurfaceNames.java" 'PortuKofStdlibMembers' \
    "SurfaceNames delega aos membros de stdlib gateados (fonte única)"
must_have "$LANGPKG/SurfaceNames.java" 'PortuKofMethodAliases' \
    "SurfaceNames delega aos aliases receiver-aware gateados (fonte única)"

# 9) Tooling PERGUNTA ao perfil via SurfaceNames — nunca regex/replacement textual.
for f in "$CLI/LspHover.java" "$CLI/LspServer.java" "$CLI/LspSymbols.java" \
         "$CLI/LspSignatureHelp.java" "$CLI/LspRename.java" "$CLI/LspProject.java"; do
    must_have "$f" 'SurfaceNames|LanguageProfile' \
        "tooling deve resolver superfície pela ponte do perfil"
done
# proibido: substituição textual de uma superfície por outra (transpile no tooling)
must_not "$CLI/LspHover.java" 'replace\("print"|replace\("println"|replace\("main"' \
    "hover NÃO pode traduzir por replace textual (regra de ouro §4)"
must_not "$CLI/LspServer.java" 'replace\("print"|replace\("println"|replace\("main"' \
    "completion/hover NÃO pode traduzir por replace textual (regra de ouro §4)"

# 10) KOF = identidade (zero regressão): SurfaceNames devolve o próprio canônico
#     quando o perfil é KOF — travado por contrato nos métodos com early-return.
if grep -qE 'if \(p == LanguageProfile\.KOF\) return (canonical|surfaceMember|surface|canonicalMember)' \
        "$LANGPKG/SurfaceNames.java"; then
    :
else
    note "FALTA: SurfaceNames deve manter identidade para KOF (zero regressão)"; fail=1
fi

# 11) IMPORTS `.ptkf` — o MESMO `CompilerImports` (sem resolver paralelo):
#     a extensão é a autoridade e o perfil é escolhido por nome de arquivo.
must_have "$COMP/CompilerImports.java" '\.ptkf' \
    "CompilerImports deve reconhecer .ptkf (resolução por extensão)"
must_have "$COMP/CompilerImports.java" 'LanguageProfile\.forFileName' \
    "CompilerImports deve escolher o perfil pelo nome do arquivo (nunca conteúdo)"
if grep -rqE 'class PortuKof(Import|ImportResolver)' "$COMP" "$CLI" 2>/dev/null; then
    note "PROIBIDO: import resolver PortuKof paralelo (golden rule §4)"; fail=1
fi

# 12) A bateria de SUPERFÍCIE existe (completion/hover/sig/def/refs/symbols/rename/import)
if [ ! -f kof-cli/src/test/java/dev/kof/cli/PortuKofToolingSurfaceE2ETest.java ]; then
    note "FALTA: PortuKofToolingSurfaceE2ETest (prova da renderização de superfície F7.2)"; fail=1
fi
# exemplo oficial de import .ptkf versionado (estrutura que resolve ponta-a-ponta)
if [ ! -f examples/portukof/imports/main.ptkf ] || [ ! -f examples/portukof/imports/util/Mat.ptkf ]; then
    note "FALTA: examples/portukof/imports/*.ptkf (prova oficial do import .ptkf)"; fail=1
fi

[ $fail -eq 0 ] && { note "F7 OK — .ptkf first-class no tooling, superfície pela ponte única, imports .ptkf, sem segundo engine, contrato de máquina intacto"; exit 0; }
note "FALHOU"; exit 1
