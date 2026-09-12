#!/usr/bin/env bash
#
# package.sh — builds the official Kof distribution layout and archives.
#
# Produces (in dist/):
#   kof-<version>-<os>-<arch>/
#     bin/kof, bin/kof.bat
#     lib/kof.jar
#     tooling/  editor/  docs/  LICENSE  VERSION
#     jdk/                  (only with --jdk: embedded OpenJDK 21)
#   kof-<version>-<os>-<arch>.tar.gz | .zip
#   SHA256SUMS
#
# Usage:
#   scripts/package.sh [--jdk] [--output <dir>] [--skip-build]
#
# The embedded JDK is downloaded from Adoptium (Eclipse Temurin 21) — the
# Tooling API baseline. It is only fetched when --jdk is passed, so a local
# package build stays fast.
set -euo pipefail

# Descoberta portátil do Python (OBS-006): o Windows pode expor apenas o
# launcher `py` (sem `python3`/`python` no PATH).
PYTHON=""
for cand in "python3" "python" "py -3"; do
    if command -v ${cand%% *} >/dev/null 2>&1; then
        PYTHON="$cand"
        break
    fi
done
if [ -z "$PYTHON" ]; then
    echo "package: python3/py not found — required for ZIP handling" >&2
    exit 1
fi
py() { $PYTHON "$@"; }

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION="$(cat "$ROOT/VERSION")"
OUT="${OUTPUT_DIR:-$ROOT/dist}"
SKIP_BUILD=false
WITH_JDK=false

while [ $# -gt 0 ]; do
    case "$1" in
        --jdk) WITH_JDK=true ;;
        --output) OUT="$2"; shift ;;
        --skip-build) SKIP_BUILD=true ;;
        *) echo "unknown option: $1" >&2; exit 1 ;;
    esac
    shift
done

OS="$(uname -s | tr '[:upper:]' '[:lower:]')"
case "$OS" in
    linux*) OS=linux ;;
    darwin*) OS=macos ;;
    mingw*|msys*|cygwin*) OS=windows ;;
esac

ARCH="$(uname -m)"
case "$ARCH" in
    x86_64|amd64) ARCH=x86_64 ;;
    aarch64|arm64) ARCH=arm64 ;;
esac

# Adoptium Tooling API usa x64 e aarch64 (não x86_64/arm64) — mapear
# para o download do JDK embarcado sem mudar o nome do target.
JDK_ARCH="$ARCH"
case "$JDK_ARCH" in
    x86_64) JDK_ARCH=x64 ;;
    arm64)  JDK_ARCH=aarch64 ;;
esac

TARGET="$OS-$ARCH"
DIST_NAME="kof-$VERSION-$TARGET"
DIST_DIR="$OUT/$DIST_NAME"

if [ "$SKIP_BUILD" = false ] && [ ! -f "$ROOT/kof-cli/target/kof-cli-$VERSION.jar" ]; then
    echo "package: building kof-cli-$VERSION.jar ..."
    (cd "$ROOT" && mvn -q package -DskipTests)
fi

echo "package: version=$VERSION target=$TARGET"

rm -rf "$DIST_DIR"
mkdir -p "$DIST_DIR/bin" "$DIST_DIR/lib" "$DIST_DIR/tooling" "$DIST_DIR/editor" "$DIST_DIR/docs"

cp "$ROOT/kof-cli/target/kof-cli-$VERSION.jar" "$DIST_DIR/lib/kof.jar"
cp "$ROOT/bin/kof" "$DIST_DIR/bin/kof"
cp "$ROOT/bin/kof.bat" "$DIST_DIR/bin/kof.bat"
chmod +x "$DIST_DIR/bin/kof"
if [ -x "$ROOT/bin/kof-webview" ]; then
    cp "$ROOT/bin/kof-webview" "$DIST_DIR/bin/kof-webview"
    echo "package: native webview shell included"
fi

printf '%s\n' "$VERSION" > "$DIST_DIR/VERSION"

# Tooling: reusable language definition + editor support shipped with the dist.
cp -r "$ROOT/editor/." "$DIST_DIR/editor/" 2>/dev/null || true
cp -r "$ROOT/tooling/." "$DIST_DIR/tooling/" 2>/dev/null || true

# Docs: a compact subset that travels with the distribution.
cp "$ROOT/README.md" "$DIST_DIR/docs/" 2>/dev/null || true
cp "$ROOT/LICENSE" "$DIST_DIR/docs/" 2>/dev/null || true
cp "$ROOT/docs/architecture/architecture.md" "$DIST_DIR/docs/" 2>/dev/null || true
cp -r "$ROOT/docs/tooling/." "$DIST_DIR/docs/tooling/" 2>/dev/null || true
cp -r "$ROOT/docs/distribution/." "$DIST_DIR/docs/distribution/" 2>/dev/null || true
cp "$ROOT/docs/distribution/LICENSING.md" "$DIST_DIR/docs/" 2>/dev/null || true

if [ "$WITH_JDK" = true ]; then
    echo "package: fetching embedded OpenJDK 21 (Temurin) for $TARGET ..."
    case "$OS" in
        linux)  JDK_OS="linux"; JDK_EXT="tar.gz" ;;
        macos)  JDK_OS="mac";   JDK_EXT="tar.gz" ;;
        windows) JDK_OS="windows"; JDK_EXT="zip" ;;
        *) echo "package: no JDK mapping for $OS — skipping embedded JDK" >&2 ;;
    esac
    if [ -n "${JDK_OS:-}" ]; then
        URL="https://api.adoptium.net/v3/binary/latest/21/ga/$JDK_OS/$JDK_ARCH/jdk/hotspot/normal/eclipse"
        TMP_JDK="$OUT/.jdk-download.$JDK_EXT"
        curl -fL --retry 3 -o "$TMP_JDK" "$URL"
        mkdir -p "$DIST_DIR/jdk"
        case "$JDK_EXT" in
            tar.gz) tar -xzf "$TMP_JDK" -C "$DIST_DIR/jdk" --strip-components=1 ;;
            zip)    # o Python no Windows não entende paths MSYS (/d/a/...)
                    # — converter com cygpath (Git Bash) antes de extrair
                    TMP_WIN="$(cygpath -w "$TMP_JDK" 2>/dev/null || echo "$TMP_JDK")"
                    JDK_WIN="$(cygpath -w "$DIST_DIR/jdk" 2>/dev/null || echo "$DIST_DIR/jdk")"
                    py -c "import zipfile,sys; zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])" "$TMP_WIN" "$JDK_WIN"
                    SUB="$(ls -d "$DIST_DIR"/jdk/jdk-* 2>/dev/null | head -1 || true)"
                    if [ -n "$SUB" ] && [ "$SUB" != "$DIST_DIR/jdk" ]; then
                        mv "$SUB"/* "$DIST_DIR/jdk/"
                        rmdir "$SUB" 2>/dev/null || true
                    fi ;;

        esac
        # Layout padronizado: bin/java em todas as plataformas. O tarball do
        # macOS tem a estrutura jdk-*/Contents/Home/... — aplainar.
        if [ -d "$DIST_DIR/jdk/Contents/Home" ]; then
            mv "$DIST_DIR"/jdk/Contents/Home/* "$DIST_DIR/jdk/"
            rm -rf "$DIST_DIR/jdk/Contents"
        fi
        rm -f "$TMP_JDK"
        # Verificação explícita: o JDK embarcado tem que existir com o
        # layout esperado — nunca engolir falha de extração (Windows).
        if [ ! -x "$DIST_DIR/jdk/bin/java" ] && [ ! -f "$DIST_DIR/jdk/bin/java.exe" ]; then
            echo "package: ERROR — embedded JDK extraction failed (jdk/bin/java[.exe] not found)" >&2
            find "$DIST_DIR/jdk" -maxdepth 2 2>/dev/null | head -15 >&2
            exit 1
        fi
        echo "package: embedded JDK ready at jdk/"
    fi
fi

# Archive + checksums.
cd "$OUT"
if [ "$OS" = windows ]; then
    if command -v zip >/dev/null 2>&1; then
        zip -qr "$DIST_NAME.zip" "$DIST_NAME"
    else
        py -c "import zipfile,sys,os; z=zipfile.ZipFile(sys.argv[1],'w',zipfile.ZIP_DEFLATED); [z.write(os.path.join(r,f), os.path.join(r,f)) for r,d,fs in os.walk(sys.argv[2]) for f in fs]; z.close()" "$DIST_NAME.zip" "$DIST_NAME"
    fi
    ARCHIVE="$DIST_NAME.zip"
else
    tar -czf "$DIST_NAME.tar.gz" "$DIST_NAME"
    ARCHIVE="$DIST_NAME.tar.gz"
fi

if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$ARCHIVE" > SHA256SUMS
else
    shasum -a 256 "$ARCHIVE" > SHA256SUMS
fi

echo "package: $OUT/$ARCHIVE"
echo "package: $OUT/SHA256SUMS"
echo "package: done"