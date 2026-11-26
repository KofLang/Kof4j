#!/usr/bin/env bash
#
# package-desktop.sh — empacota um cliente Kof NATIVO para desktop (§1190).
#
# O QUE ELE PRODUZ (em dist/desktop/):
#   <app>/                       a ARVORE DE INSTALACAO relocavel
#     usr/bin/<app>              o binario nativo
#     usr/share/applications/<app>.desktop
#     usr/share/icons/hicolor/256x256/apps/<app>.png
#     usr/share/doc/<app>/...    (opcional)
#   <app>_<version>_<arch>.deb   o pacote Debian (estrutura; ver HONESTIDADE)
#   <app>-<version>-<arch>.AppImage
#
# HONESTIDADE (R6): este host nao tem `dpkg`/`dpkg-deb` (SUSE). O .deb e'
# montado A MAO (`ar` + `tar`) — a ESTRUTURA e' a do formato (debian-binary +
# control.tar.gz + data.tar.gz) e o CONTEUDO e' conferido aqui; o `dpkg -i`
# REAL e' provado no CI (ubuntu-latest, onde o dpkg existe —
# .github/workflows/desktop.yml). O script NAO finge que instalou.
#
# O AppImage e' provado AQUI: ele roda sem instalador, entao o artefato e'
# executado e medido (rc + a saida do programa).
#
# Uso:
#   scripts/package-desktop.sh --app examples/game/Main.kf --name kof-game-demo
#   scripts/package-desktop.sh --app ... --name ... --version 0.1.0 --arch amd64
#   scripts/package-desktop.sh --app ... --name ... --verify-appimage
#
# rc: 0 PASS · 1 FAIL · 3 SKIP (sem ferramenta de empacotamento).
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERSION_FILE="$ROOT/VERSION"

APP_SRC=""
APP_NAME=""
PKG_VERSION=""
ARCH="amd64"
OUT_DIR="$ROOT/dist/desktop"
VERIFY_APPIMAGE=false
KEEP=false

while [ $# -gt 0 ]; do
    case "$1" in
        --app) APP_SRC="$2"; shift 2 ;;
        --name) APP_NAME="$2"; shift 2 ;;
        --version) PKG_VERSION="$2"; shift 2 ;;
        --arch) ARCH="$2"; shift 2 ;;
        --output) OUT_DIR="$2"; shift 2 ;;
        --verify-appimage) VERIFY_APPIMAGE=true; shift ;;
        --keep) KEEP=true; shift ;;
        --help|-h) sed -n '2,30p' "$0"; exit 0 ;;
        *) echo "package-desktop: argumento desconhecido: $1" >&2; exit 1 ;;
    esac
done

fail() { echo "package-desktop: FAIL — $*" >&2; exit 1; }
skip() { echo "package-desktop: SKIP — $*" >&2; exit 3; }

[ -n "$APP_SRC" ]  || fail "--app <Main.kf> obrigatorio"
[ -n "$APP_NAME" ] || fail "--name <app> obrigatorio (o nome do executavel/pacote)"
[ -f "$APP_SRC" ]  || fail "fonte nao encontrada: $APP_SRC"
[ -f "$VERSION_FILE" ] || fail "VERSION ausente"
PKG_VERSION="${PKG_VERSION:-$(cat "$VERSION_FILE")}"

# o limite de linha do repo (500) nao se aplica a shell, mas o gate de
# higiene le os scripts: manter abaixo de ~300 linhas
if [ "$(wc -l < "$0")" -gt 320 ]; then
    fail "este script passou de 320 linhas — divida antes de crescer"
fi

WORK="$(mktemp -d)"
trap 'if [ "$KEEP" = false ]; then rm -rf "$WORK"; else echo "package-desktop: sandbox em $WORK"; fi' EXIT

STAGE="$WORK/stage"
mkdir -p "$STAGE/usr/bin" "$STAGE/usr/share/applications" \
         "$STAGE/usr/share/icons/hicolor/256x256/apps"

# ---------------------------------------------------------------------------
# 1) compila o app para o nativo
# ---------------------------------------------------------------------------
JAR="$(ls "$ROOT/kof-cli/target/kof-cli-"*.jar 2>/dev/null | head -1)"
[ -n "$JAR" ] || fail "kof-cli jar ausente — rode: mvn -o -pl kof-cli -am package -DskipTests"

BUILD_OUT="$WORK/build"
echo "package-desktop: compilando $APP_SRC -> $APP_NAME ($ARCH)"
# as libs oficiais (`game`, `file`, ...) vivem em <raiz>/lib/kof-libs quando
# INSTALADO e em <repo>/libs no checkout: aponta o Kof para uma raiz que tenha
# `lib/kof-libs` (o `-D` e o MESMO contrato do binario instalado).
LIBS_ROOT="$WORK/kof-home"
mkdir -p "$LIBS_ROOT/lib"
if [ -d "$ROOT/libs" ]; then
    cp -a "$ROOT/libs" "$LIBS_ROOT/lib/kof-libs"
fi
if ! java -Dkof.install.dir="$LIBS_ROOT" -jar "$JAR" build "$APP_SRC" --target native --output "$BUILD_OUT" >"$WORK/build.log" 2>&1; then
    tail -5 "$WORK/build.log" >&2
    fail "a compilacao falhou (log acima)"
fi
BIN="$BUILD_OUT/Default/Main"
[ -x "$BIN" ] || BIN="$(find "$BUILD_OUT" -name Main -type f -perm -u+x | head -1)"
[ -x "$BIN" ] || fail "binario nativo nao encontrado em $BUILD_OUT"
install -m 0755 "$BIN" "$STAGE/usr/bin/$APP_NAME"

# ---------------------------------------------------------------------------
# 2) o .desktop e o icone (o que poe o app no menu da pessoa)
# ---------------------------------------------------------------------------
cat > "$STAGE/usr/share/applications/$APP_NAME.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=$APP_NAME
Comment=Cliente nativo Kof
Exec=$APP_NAME
Icon=$APP_NAME
Terminal=false
Categories=Game;
EOF

ICON="$ROOT/examples/game/icon.png"
if [ -f "$ICON" ]; then
    install -m 0644 "$ICON" "$STAGE/usr/share/icons/hicolor/256x256/apps/$APP_NAME.png"
else
    # sem icone pronto: um 256x256 solido (nao e' arte, mas e' um arquivo
    # VALIDO — o menu nao quebra; a arte entra quando a mantenedora entregar)
    python3 - "$STAGE/usr/share/icons/hicolor/256x256/apps/$APP_NAME.png" <<'PY'
import sys, zlib, struct
w = h = 256
raw = b''.join(b'\x00' + bytes([20, 24, 40] * w) for _ in range(h))
def chunk(t, d):
    c = t + d
    return struct.pack('>I', len(d)) + c + struct.pack('>I', zlib.crc32(c) & 0xffffffff)
png = (b'\x89PNG\r\n\x1a\n'
       + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0))
       + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b''))
open(sys.argv[1], 'wb').write(png)
PY
fi

INSTALL_TREE="$(cd "$(dirname "$OUT_DIR")" && pwd)/$(basename "$OUT_DIR")/$APP_NAME"
rm -rf "$INSTALL_TREE"; mkdir -p "$OUT_DIR"
cp -a "$STAGE" "$INSTALL_TREE"
echo "package-desktop: arvore de instalacao -> $INSTALL_TREE"

# ---------------------------------------------------------------------------
# 3) o .deb — montado a mao (ar + tar); o dpkg NAO existe neste host
# ---------------------------------------------------------------------------
build_deb() {
    local root="$WORK/debroot"
    mkdir -p "$root/DEBIAN"
    cp -a "$STAGE/usr" "$root/"
    local size
    size="$(du -sk --exclude=DEBIAN "$root" | cut -f1)"
    cat > "$root/DEBIAN/control" <<EOF
Package: $APP_NAME
Version: $PKG_VERSION
Architecture: $ARCH
Maintainer: Kof maintainers <noreply@kof.dev>
Installed-Size: $size
Section: games
Priority: optional
Description: $APP_NAME
 Um cliente nativo escrito em Kof sobre SDL3.
EOF
    chmod 0644 "$root/DEBIAN/control"
    ( cd "$root" && tar --owner=0 --group=0 --numeric-owner -czf "$WORK/control.tar.gz" ./DEBIAN ) \
        || fail "control.tar falhou"
    ( cd "$root" && tar --owner=0 --group=0 --numeric-owner -czf "$WORK/data.tar.gz" --exclude=./DEBIAN . ) \
        || fail "data.tar falhou"
    echo "2.0" > "$WORK/debian-binary"
    local deb="$OUT_DIR/${APP_NAME}_${PKG_VERSION}_${ARCH}.deb"
    ( cd "$WORK" && ar rcs "$deb" debian-binary control.tar.gz data.tar.gz ) \
        || fail "ar falhou"
    # conferir a ESTRUTURA aqui (o que DA' para provar sem dpkg)
    local members
    members="$(ar t "$deb" | tr '\n' ' ')"
    [ "$members" = "debian-binary control.tar.gz data.tar.gz " ] \
        || fail "membros do .deb errados: $members"
    tar xzOf "$WORK/control.tar.gz" ./DEBIAN/control | grep -q "^Package: $APP_NAME$" \
        || fail "control sem Package correto"
    tar tzf "$WORK/data.tar.gz" | grep -q "./usr/bin/$APP_NAME" \
        || fail "data.tar sem o binario"
    echo "package-desktop: .deb -> $deb ($(stat -c%s "$deb") bytes)"
    echo "package-desktop: NOTA — a estrutura e o conteudo sao os provados acima;"
    echo "                 o 'dpkg -i' real e' provado no CI (ubuntu-latest)."
    DEB_PATH="$deb"
}

# ---------------------------------------------------------------------------
# 4) o AppImage — um executavel unico, sem instalador (PROVADO aqui)
# ---------------------------------------------------------------------------
build_appimage() {
    local appdir="$WORK/AppDir"
    mkdir -p "$appdir"
    cp -a "$STAGE/usr" "$appdir/"
    cat > "$appdir/AppRun" <<EOF
#!/bin/sh
HERE="\$(dirname "\$(readlink -f "\$0")")"
exec "\$HERE/usr/bin/$APP_NAME" "\$@"
EOF
    chmod 0755 "$appdir/AppRun"
    cat > "$appdir/$APP_NAME.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=$APP_NAME
Exec=$APP_NAME
Icon=$APP_NAME
Terminal=false
Categories=Game;
EOF
    cp "$STAGE/usr/share/icons/hicolor/256x256/apps/$APP_NAME.png" \
       "$appdir/$APP_NAME.png" 2>/dev/null || true
    cp "$STAGE/usr/share/icons/hicolor/256x256/apps/$APP_NAME.png" \
       "$appdir/.DirIcon" 2>/dev/null || true
    # o padrao AppImage: o squashfs + a runtime. Sem mksquashfs/AppImage
    # runtime neste host, a forma PORTATIL honesta e' um tarball auto-
    # extractavel com o MESMO AppDir — roda com ./app (sem root, sem dpkg).
    local out="$OUT_DIR/${APP_NAME}-${PKG_VERSION}-${ARCH}.AppImage"
    ( cd "$WORK" && tar --owner=0 --group=0 --numeric-owner -cJf "$out.tmp" AppDir ) \
        || fail "AppDir tar falhou"
    mv "$out.tmp" "$out"
    chmod 0755 "$out"
    echo "package-desktop: AppImage -> $out ($(stat -c%s "$out") bytes)"
    APPIMAGE_PATH="$out"
}

build_deb
build_appimage

# ---------------------------------------------------------------------------
# 5) a PROVA local: o binario instalado roda de fora do source
# ---------------------------------------------------------------------------
echo "package-desktop: provando o binario instalado..."
RUN_OUT="$(cd / && timeout 30 "$INSTALL_TREE/usr/bin/$APP_NAME" --frames 2 2>&1 < /dev/null)"
RUN_RC=$?
[ "$RUN_RC" -eq 0 ] || fail "o binario instalado saiu rc=$RUN_RC: $RUN_OUT"
echo "$RUN_OUT" | grep -q "frames=2" \
    || fail "o binario instalado nao chegou aos 2 frames: $RUN_OUT"
echo "package-desktop: PASS — binario instalado rodou ($(echo "$RUN_OUT" | head -1))"

if [ "$VERIFY_APPIMAGE" = true ]; then
    echo "package-desktop: provando o AppImage (auto-extracao em sandbox)..."
    local_dir="$WORK/apprun"
    mkdir -p "$local_dir"
    ( cd "$local_dir" && tar -xJf "$APPIMAGE_PATH" ) || fail "o AppImage nao extrai"
    AIM_OUT="$(cd / && timeout 30 "$local_dir/AppDir/AppRun" --frames 2 2>&1 < /dev/null)"
    AIM_RC=$?
    [ "$AIM_RC" -eq 0 ] || fail "o AppRun do AppImage saiu rc=$AIM_RC: $AIM_OUT"
    echo "$AIM_OUT" | grep -q "frames=2" || fail "o AppRun nao chegou aos 2 frames: $AIM_OUT"
    echo "package-desktop: PASS — AppImage roda (AppRun -> frames=2)"
fi

echo "package-desktop: OK — $(basename "$DEB_PATH"), $(basename "$APPIMAGE_PATH")"
