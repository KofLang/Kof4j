#!/usr/bin/env bash
#
# build-kof-jar.sh — rebuilds the TREE jar (lib/kof.jar), the one bin/kof runs.
#
# bin/kof runs lib/kof.jar from the working tree; when a source file is newer
# than the jar, scripts/target-matrix.sh (EG-5 parity) refuses to measure
# ("ARTEFATO VELHO") because the jar would not correspond to the tip. This
# helper does the whole recovery in one command:
#   1. builds kof-cli (which pulls its reactor deps);
#   2. copies the fresh jar to lib/kof.jar;
#   3. writes lib/kof.jar.stamp (jar mtime + sha256 of the source CONTENT), so
#      target-matrix.sh certifies by CONTENT: a later rebase that only rewrites
#      source mtimes (same content) no longer looks stale.
#
# Uso: scripts/build-kof-jar.sh [--skip-build]
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 3
SKIP=false
[ "${1:-}" = "--skip-build" ] && SKIP=true

JAR="$ROOT/lib/kof.jar"

if [ "$SKIP" = false ]; then
    echo "build-kof-jar: mvn -o -pl kof-cli -am package -DskipTests"
    # O shade 3.6 tem checagem up-to-date ("Archive ... is uptodate") que
    # PULA o re-empacotamento quando o jar sombra é mais novo que os inputs —
    # preservando conteúdo VELHO do kof-compiler (medido 25/09: jar saía com
    # a classe antiga mesmo com reactor+~/.m2 frescos). Remover os jars força
    # o shade sempre; o custo é segundos.
    rm -f "$ROOT"/kof-cli/target/kof-cli-*.jar
    mvn -o -q -pl kof-cli -am package -DskipTests || { echo "build-kof-jar: build falhou" >&2; exit 1; }
fi

src_jar="$(ls -1t "$ROOT"/kof-cli/target/kof-cli-*.jar 2>/dev/null | grep -v -e '-sources' -e '-javadoc' | head -1)"
[ -n "$src_jar" ] || { echo "build-kof-jar: nenhum kof-cli-*.jar em kof-cli/target (rode o build)" >&2; exit 1; }
cp "$src_jar" "$JAR" || exit 1

source_hash() {
    find "$@" -name '*.java' -type f -print0 2>/dev/null \
        | LC_ALL=C sort -z \
        | xargs -0 -r sha256sum 2>/dev/null \
        | sha256sum | cut -d' ' -f1
}
jar_mtime() { stat -c %Y "$1" 2>/dev/null || stat -f %m "$1"; }

{ jar_mtime "$JAR"; source_hash "$ROOT"/kof-*/src/main; } > "$JAR.stamp" || exit 1

echo "build-kof-jar: $JAR ($(du -h "$JAR" | cut -f1))"
echo "build-kof-jar: stamp gravado em $JAR.stamp — a matriz passa a certificar por conteudo"
