#!/usr/bin/env bash
#
# gen-enois-text.sh — aponta o `libs/enois/Text.kf` para o shim do FreeType
# (known-bugs §1192: o `library()` de um `extern` so' resolve no caminho do
# sistema, entao a lib DO APP entra por caminho absoluto).
#
# O fonte versionado guarda o marcador `@KOF_TEXT_SHIM@`; este script compila o
# shim (se faltar) e troca o marcador (ou o caminho anterior) pelo real.
#
# Uso: scripts/gen-enois-text.sh [<caminho-da-libkof_text.so>]
#      KOF_TEXT_KF=<outro Text.kf> para reapontar uma copia.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LIB="${1:-$ROOT/libs/enois/libkof_text.so}"

if [ ! -f "$LIB" ]; then
    echo "gen-enois-text: compilando o shim..."
    FT="${KOF_FREETYPE_SRC:-$ROOT/.build/freetype-2.13.3}"
    if [ ! -d "$FT/include" ]; then
        mkdir -p "$ROOT/.build"
        curl -sL "https://download.savannah.gnu.org/releases/freetype/freetype-2.13.3.tar.xz" \
            | tar xJ -C "$ROOT/.build"
    fi
    gcc -shared -fPIC -O2 -I "$FT/include" \
        -o "$LIB" "$ROOT/libs/enois/kof_text_shim.c" -L/usr/lib64 -l:libfreetype.so.6
fi

# O script e IDEMPOTENTE: depois da primeira rodada o marcador virou um caminho
# absoluto, e um segundo run sem isto dizia "ok" sem mudar nada (MEDIDO).
python3 - "${KOF_TEXT_KF:-$ROOT/libs/enois/Text.kf}" "$LIB" <<'PY'
import re, sys
path, lib = sys.argv[1], sys.argv[2]
src = open(path).read()
if '"@KOF_TEXT_SHIM@"' in src:
    src = src.replace('"@KOF_TEXT_SHIM@"', '"' + lib + '"')
else:
    # ja' apontado: troca o caminho ABSOLUTO anterior de qualquer libkof_text.so
    src, n = re.subn(r'"[^"\n]*libkof_text\.so"', '"' + lib + '"', src)
    if n == 0:
        sys.exit("gen-enois-text: nenhum marcador nem caminho de libkof_text.so em " + path)
open(path, 'w').write(src)
print("gen-enois-text: Text.kf -> " + lib)
PY
