#!/usr/bin/env bash
# check_version_consistency.sh — a versão tem UMA fonte de verdade e o
# compilador tem que Reportar exatamente ela (#814, ordem da mantenedora 11/10:
# "a tag tem que corresponder à versão informada pelo compilador em si").
#
# Verifica (falha fechada, rc=1 em qualquer divergência):
#   1. VERSION (raiz) == pom.xml <revision> == CADA componente de
#      kof-compiler/src/main/resources/dev/kof/version.properties
#      (kof.version, kof.compiler.version, kof.runtime.version, kof.stdlib.version).
#   2. Face de RUNTIME: se houver um kof-cli jar empacotado em target, `kof
#      version` tem que imprimir "kof <VERSION>" — o que o compilador INFORMA
#      tem que ser a versão da árvore. Jar ausente = pulo NOMEADO (a face
#      estática acima já certifica a fonte; a release re-verifica no empacote).
#
# Uso: scripts/check_version_consistency.sh   (rc=0 ok / rc=1 divergência)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FAIL=0

say() { echo "check_version_consistency: $*"; }
die() { say "RED: $*"; FAIL=1; }

# --selftest (o teste estrutural): prova que o veredito RED tem DENTES — a
# planta DIVERGENTE (VERSION != pom) falha; a CONSISTENTE passa a face
# estática. Lê uma árvore temporária via os env ROOT/VERSION/POM/PROPS (o
# default é o repo). A árvore REAL não precisa estar GREEN aqui.
if [ "${1:-}" = "--selftest" ]; then
    ST="$(mktemp -d)"; trap 'rm -rf "$ST"' EXIT
    mkdir -p "$ST/kof-compiler/src/main/resources/dev/kof"
    sed 's|<revision>[^<]*</revision>|<revision>0.9.9</revision>|' "$ROOT/pom.xml" > "$ST/pom.xml"
    printf 'kof.version=0.9.9\nkof.compiler.version=0.9.9\nkof.runtime.version=0.9.9\nkof.stdlib.version=0.9.9\n' > "$ST/kof-compiler/src/main/resources/dev/kof/version.properties"
    printf '0.9.9\n' > "$ST/VERSION"
    if VERSION="$ST/VERSION" POM="$ST/pom.xml" PROPS="$ST/kof-compiler/src/main/resources/dev/kof/version.properties" ROOT="$ST" bash "$0" --planted >/dev/null 2>&1; then
        echo "SELFTEST: ok — a planta CONSISTENTE passa a face estática"
    else
        echo "SELFTEST FAIL: a planta consistente foi reprovada (falso vermelho)"; exit 2
    fi
    sed -i 's|<revision>0.9.9</revision>|<revision>0.9.8</revision>|' "$ST/pom.xml"
    if VERSION="$ST/VERSION" POM="$ST/pom.xml" PROPS="$ST/kof-compiler/src/main/resources/dev/kof/version.properties" ROOT="$ST" bash "$0" --planted >/dev/null 2>&1; then
        echo "SELFTEST FAIL: a DIVERGÊNCIA plantada não foi pega (falso verde)"; exit 2
    fi
    echo "SELFTEST: ok — a divergente RED; a consistente GREEN"
    exit 0
fi

# --planted: as fontes vêm do env (a árvore do selftest), não do repo
if [ "${1:-}" != "--planted" ]; then
    ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fi
SRC_VERSION="${VERSION:-$ROOT/VERSION}"
SRC_POM="${POM:-$ROOT/pom.xml}"
SRC_PROPS="${PROPS:-$ROOT/kof-compiler/src/main/resources/dev/kof/version.properties}"

[ -f "$SRC_VERSION" ] || { die "VERSION ausente na raiz"; say "rc=1"; exit 1; }
VERSION="$(cat "$SRC_VERSION")"
[ -n "$VERSION" ] || { die "VERSION vazio"; say "rc=1"; exit 1; }

POM_REV="$(sed -n 's:.*<revision>\(.*\)</revision>.*:\1:p' "$SRC_POM" | head -1)"
[ "$POM_REV" = "$VERSION" ] || die "pom.xml <revision>='$POM_REV' != VERSION='$VERSION'"

PROPS="$SRC_PROPS"
[ -f "$PROPS" ] || { die "version.properties ausente"; say "rc=1"; exit 1; }
for KEY in kof.version kof.compiler.version kof.runtime.version kof.stdlib.version; do
    VAL="$(sed -n "s:^$KEY=::p" "$PROPS" | head -1 | tr -d '[:space:]')"
    [ "$VAL" = "$VERSION" ] || die "version.properties $KEY='$VAL' != VERSION='$VERSION'"
done

# face de runtime: o que o compilador INFORMA (KofVersion lê o
# version.properties EMPACOTADO — é exatamente o que vai no jar)
JAR="$(ls "$ROOT/kof-cli/target/kof-cli-$VERSION.jar" 2>/dev/null | head -1 || true)"
if [ -z "$JAR" ]; then
    # sem jar da versão corrente: o mais RECENTE por mtime (um jar velho de
    # release anterior não certifica a árvore — lição do bump 0.6.2)
    JAR="$(ls -t "$ROOT"/kof-cli/target/kof-cli-*.jar 2>/dev/null | head -1 || true)"
fi
if [ -n "$JAR" ] && [ "${1:-}" != "--planted" ]; then
    # java resolution with CANDIDATES and a real usability check: the first
    # java that actually runs the jar wins. The old form always used sdkman
    # as JAVA_BIN (the "${JAVA_BIN:-java}" fallback never fired) and died
    # with an opaque exit 127 on hosts without sdkman (measured 12/10);
    # even after fixing the order, a PATH java older than the compiled
    # classes (21 vs 69 here) produced an EMPTY version silently. So:
    # gather candidates in the standard order (JAVA_HOME, sdkman, JVM
    # locations, PATH) and pick the first whose run prints a "kof " line.
    CANDIDATES=""
    if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
        CANDIDATES="${JAVA_HOME}/bin/java"
    fi
    if [ -x "$HOME/.sdkman/candidates/java/current/bin/java" ]; then
        CANDIDATES="$CANDIDATES $HOME/.sdkman/candidates/java/current/bin/java"
    fi
    for jvm in /usr/lib/jvm/java-*; do
        if [ -x "$jvm/bin/java" ]; then
            CANDIDATES="$CANDIDATES $jvm/bin/java"
        fi
    done 2>/dev/null
    if [ -d "$HOME/tools" ]; then
        for jdk in "$HOME"/tools/jdk-*; do
            if [ -x "$jdk/bin/java" ]; then
                CANDIDATES="$CANDIDATES $jdk/bin/java"
            fi
        done 2>/dev/null
    fi
    if command -v java >/dev/null 2>&1; then
        CANDIDATES="$CANDIDATES $(command -v java)"
    fi
    if [ -z "$CANDIDATES" ]; then
        die "nenhum java encontrado (JAVA_HOME, sdkman, /usr/lib/jvm, ~/tools/jdk-*, PATH) para executar a face do jar"
    fi
    REPORTED=""
    TRIED=""
    for CAND in $CANDIDATES; do
        TRIED="$TRIED $CAND"
        OUT="$("$CAND" -jar "$JAR" version 2>&1 | head -1 || true)"
        case "$OUT" in
            "kof "*)
                REPORTED="$OUT"
                JAVA_BIN="$CAND"
                break
                ;;
        esac
    done
    if [ -z "$REPORTED" ]; then
        die "nenhum java consegue executar o jar (candidatos:$TRIED) - a ultima saida foi: $OUT"
    fi
    [ "$REPORTED" = "kof $VERSION" ] || die "o compilador empacotado informa '$REPORTED' != 'kof $VERSION' (recompile antes de empacotar)"
else
    [ "${1:-}" = "--planted" ] && say "PULO nomeado (--planted): a face do jar fica para o empacote real"
    say "PULO nomeado: sem jar empacotado em kof-cli/target — face de runtime fica para o empacote (a face estática acima certifica as fontes)"
fi

if [ "$FAIL" -ne 0 ]; then
    say "rc=1 — dessincronização de versão (#814); rode scripts/bump-version.sh <versão> para sincronizar TUDO"
    exit 1
fi
say "OK — VERSION/pom/version.properties$([ -n "$JAR" ] && echo "/compilador empacotado" || echo "") = $VERSION"
exit 0
