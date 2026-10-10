#!/usr/bin/env bash
#
# check_javafx_absent.sh — guarda da frente graphics-gaming (D-GRAPHICS-SPIKE,
# adendos 2/3 de D-GRAPHICS-GAMING): o Kof NUNCA usa JavaFX. A ausencia e o
# contrato: a doc declara "0 ligacoes javafx"; este gate transforma a medicao
# manual em invariante de CI, para que a face grafica futura suba por R3/FFI e
# nao reintroduza JavaFX/Swing/AWT por engano.
#
# Escopo: APENAS codigo e build (`*.java`/`*.kt`/`*.kf`/`pom.xml`/`*.gradle`) —
# a prosa de docs/training PODE citar JavaFX (regras/anti-exemplos), logo fica
# fora. `target/` (gerado) fica fora.
#
# AUSENCIA e FALHA: qualquer ocorrencia case-insensitive de "javafx" no escopo
# retorna rc!=0 ate ser removida.
#
# Uso: scripts/check_javafx_absent.sh             # rc=0 limpo; rc=1 se houver
#      scripts/check_javafx_absent.sh --selftest  # planta caso RUIM e caso BOM
# Env (teste): JAVAFX_SCAN_ROOT (raiz alternativa com kof-*/src + pom.xml).
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"
ROOT="${JAVAFX_SCAN_ROOT:-$PWD}"

scan() {
    # apenas USO real: import/ref qualificada (`javafx.`) ou coordenada Maven
    # (`org.openjfx`). Prosa que cita "JavaFX launcher" (regra AGENTS: a
    # mensagem do launcher mascara um VerifyError) NAO e uso e fica de fora.
    grep -rinsE 'javafx[.]|org[.]openjfx' \
        --include='*.java' --include='*.kt' --include='*.kf' \
        --include='pom.xml' --include='*.gradle' \
        "$ROOT" 2>/dev/null | grep -v '/target/'
}

if [ "${1:-}" = "--selftest" ]; then
    T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
    mkdir -p "$T/kof-compiler/src/main/java/dev/kof"
    # caso BOM: arvore limpa -> gate deve passar
    printf 'package dev.kof;\nclass Foo {}\n' \
        > "$T/kof-compiler/src/main/java/dev/kof/Foo.java"
    if ! JAVAFX_SCAN_ROOT="$T" bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: arvore limpa devia passar"; exit 1; fi
    # caso RUIM: import javafx plantado -> gate deve FALHAR (RED-first)
    printf 'package dev.kof;\nimport javafx.scene.Scene;\nclass Foo {}\n' \
        > "$T/kof-compiler/src/main/java/dev/kof/Foo.java"
    if JAVAFX_SCAN_ROOT="$T" bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: 'import javafx' plantado passou (furo)"; exit 1; fi
    # caso RUIM 2: dependencia no pom -> gate deve FALHAR
    printf 'package dev.kof;\nclass Foo {}\n' \
        > "$T/kof-compiler/src/main/java/dev/kof/Foo.java"
    printf '<dependency><groupId>org.openjfx</groupId><artifactId>javafx</artifactId></dependency>\n' \
        > "$T/pom.xml"
    if JAVAFX_SCAN_ROOT="$T" bash "$0" >/dev/null 2>&1; then
        echo "SELFTEST FALHOU: dependencia javafx no pom passou (furo)"; exit 1; fi
    echo "SELFTEST OK: limpo passa; import javafx e dep javafx no pom falham"
    exit 0
fi

[ -d "$ROOT" ] || { echo "FALHA: raiz inexistente: $ROOT"; exit 1; }

HITS="$(scan)"
# guarda contra falso-verde: a varredura tem de enxergar fontes
NSRC="$(find "$ROOT" -path '*/src/*' \( -name '*.java' -o -name '*.kf' \) 2>/dev/null | grep -cv '/target/')"
if [ "${NSRC:-0}" -eq 0 ]; then
    echo "FALHA: nenhuma fonte Java/Kof encontrada sob $ROOT (nao certificavel)"; exit 1
fi

if [ -n "$HITS" ]; then
    echo "JAVAFX: FALHA — ${NSRC} fontes varridas, mas ha ocorrencia(s) de 'javafx' no codigo/build:"
    printf '%s\n' "$HITS" | head -n 20
    exit 1
fi
echo "JAVAFX: OK — 0 ocorrencias em ${NSRC} fonte(s) Java/Kof + build (doc/training fora do escopo)"
