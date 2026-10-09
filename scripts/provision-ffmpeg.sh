#!/usr/bin/env bash
#
# provision-ffmpeg.sh — vendor the upstream LGPL FFmpeg build (decision F row in
# D-MAINT-BATCH-0510, ORDERED 08/10 by the maintainer) + the MJPEG probe asset
# for the slice 3.4 backend E2E.
#
# Why: decision F — the distro GPL build is NOT taken as-is
# (avcodec_license() = GPLv3+ on the distro stack). The upstream LGPL-2.1+
# build is compiled from source with NO --enable-gpl, so the FFI probe license
# is LGPL before any decode lands.
#
# Layout (mirrors kof-cross):
#   ~/.local/share/kof-ffmpeg/usr/{bin,lib,include}
#   ~/.local/share/kof-ffmpeg/test/kof-probe.avi   (MJPEG 64x64, 4 frames)
#
# Uso:
#   bash scripts/provision-ffmpeg.sh
#   KOF_FFMPEG=/opt/kof-ffmpeg/usr bash scripts/provision-ffmpeg.sh   (override)
#
# Depois: o E2E `FfmpegFfiProbeE2ETest` (JVM + Native x86-64; cross faces
# esperam builds per-arch — provision-cross-ffmpeg.sh, ainda não pousada).
set -uo pipefail

PREFIX="${KOF_FFMPEG_PREFIX:-$HOME/.local/share/kof-ffmpeg/usr}"
FFMPEG_VER="${KOF_FFMPEG_VERSION:-9.0.2}"
URL="https://ffmpeg.org/releases/ffmpeg-${FFMPEG_VER}.tar.xz"

fail() { echo "ERRO: $*" >&2; exit 1; }

for tool in curl tar make gcc pkg-config; do
    command -v "$tool" >/dev/null 2>&1 || fail "$tool ausente"
done

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo ">> baixando ffmpeg-${FFMPEG_VER} (fonte upstream, LGPL)"
curl -sL "$URL" -o "$WORK/ffmpeg.tar.xz" || fail "download falhou: $URL"
tar -xf "$WORK/ffmpeg.tar.xz" -C "$WORK" || fail "tar falhou"
SRC="$WORK/ffmpeg-${FFMPEG_VER}"
[ -f "$SRC/configure" ] || fail "fonte inválida em $SRC"

echo ">> configure (LGPL: SEM --enable-gpl; sem x86asm — nasm ausente é aceitável)"
(cd "$SRC" && ./configure --prefix="$PREFIX" \
    --enable-shared --disable-static \
    --disable-doc --disable-htmlpages --disable-manpages --disable-podpages --disable-txtpages \
    --disable-avdevice --disable-network --disable-autodetect --disable-x86asm \
    --disable-bzlib --disable-lzma --disable-iconv --disable-sdl2 --disable-schannel --disable-sndio \
    > "$WORK/configure.log" 2>&1) || fail "configure falhou — log: $WORK/configure.log"

echo ">> make -j$(nproc) && make install"
(cd "$SRC" && make -j"$(nproc)" > "$WORK/make.log" 2>&1 && make install >> "$WORK/make.log" 2>&1) \
    || fail "make falhou — log: $WORK/make.log"

echo ">> probe da licença (decisão F: NÃO pode ser GPLv3+)"
LICENSE="$("$PREFIX/bin/ffmpeg" -hide_banner -version 2>/dev/null | grep -o "LGPL version [0-9.]* or later" | head -1)"
[ -n "$LICENSE" ] || fail "licença LGPL NÃO confirmada — a build não é aceitável (decisão F)"
echo "   licença: $LICENSE"

echo ">> asset de probe (MJPEG 64x64, 4 frames: red/green/blue/white)"
TEST_DIR="$PREFIX/../test"
mkdir -p "$TEST_DIR"
python3 -c "
frames = [(255,0,0),(0,255,0),(0,0,255),(255,255,255)]
out = bytearray()
for (r,g,b) in frames:
    out += bytes([r,g,b]) * (64*64)
open('$TEST_DIR/frames.rgb','wb').write(out)" || fail "raw frames falhou"
LD_LIBRARY_PATH="$PREFIX/lib" "$PREFIX/bin/ffmpeg" -y -hide_banner -loglevel error \
    -f rawvideo -pix_fmt rgb24 -s 64x64 -r 4 -i "$TEST_DIR/frames.rgb" \
    -c:v mjpeg -pix_fmt yuvj420p "$TEST_DIR/kof-probe.avi" || fail "encode MJPEG falhou"
rm -f "$TEST_DIR/frames.rgb"

echo "OK: FFmpeg LGPL vendido em $PREFIX + asset em $TEST_DIR/kof-probe.avi"
