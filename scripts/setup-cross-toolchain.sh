#!/usr/bin/env bash
#
# setup-cross-toolchain.sh — prepara um prefixo SEM ROOT com binutils cross
# (aarch64/riscv64), qemu-user-static e a libc cross, para que ESTE host possa
# certificar a matriz de paridade (native.aarch64 / native.riscv64) sem depender
# de apt/sudo. Ate entao a matriz reportava os alvos cross como "sem toolchain"
# neste host e o gate 0.5.0 (condicao 1) ficava RED/NEEDS-MEASURE por ambiente.
#
# Provado na rodada 28: extraindo os .deb num prefixo local e apontando
# KOF_CROSS_SYSROOT para ele, a matriz certifica PARITY: 100% (jvm/x86_64/
# riscv64/aarch64/js/script byte-a-byte) e a condicao 1 vira GREEN.
#
# Requer rede (`apt-get download`). NAO requer root.
#
# Uso:
#   scripts/setup-cross-toolchain.sh                 # prepara e mostra os exports
#   eval "$(scripts/setup-cross-toolchain.sh --export)"   # exporta no shell atual
#   KOF_CROSS_PREFIX=/opt/kof-cross scripts/setup-cross-toolchain.sh
#
# Depois: scripts/target-matrix.sh  (com java 25 no PATH) -> parity GREEN.
# (A esteira de release 0.5.0 foi aposentada 28/09 — D-RELEASE-0.5.0-CLOSED.)
set -uo pipefail
cd "$(git rev-parse --show-toplevel)"

PREFIX="${KOF_CROSS_PREFIX:-/tmp/kof-cross}"
ARCHES="aarch64 riscv64"
PKGS="binutils-aarch64-linux-gnu binutils-riscv64-linux-gnu qemu-user-static
libc6-arm64-cross libc6-riscv64-cross libc6-dev-arm64-cross libc6-dev-riscv64-cross
libgcc-s1-arm64-cross libgcc-s1-riscv64-cross"

have=1
for a in $ARCHES; do
    [ -x "$PREFIX/usr/bin/$a-linux-gnu-as" ] || have=0
    [ -e "$PREFIX/usr/$a-linux-gnu/lib/libc.so" ] || have=0
    [ -x "$PREFIX/usr/bin/qemu-$a" ] || have=0
done

if [ "$have" -eq 0 ]; then
    command -v apt-get >/dev/null 2>&1 || { echo "ERRO: apt-get ausente (precisa para baixar os .deb)"; exit 1; }
    command -v dpkg-deb >/dev/null 2>&1 || { echo "ERRO: dpkg-deb ausente"; exit 1; }
    mkdir -p "$PREFIX" || { echo "ERRO: nao crio $PREFIX"; exit 1; }
    tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
    echo "setup-cross-toolchain: preparando $PREFIX (sem root)" >&2
    for p in $PKGS; do
        if ( cd "$tmp" && apt-get download "$p" >/dev/null 2>&1 ); then :; else
            echo "ERRO: apt-get download falhou para $p (rede?)" >&2; exit 1; fi
    done
    for d in "$tmp"/*.deb; do dpkg-deb -x "$d" "$PREFIX" 2>/dev/null || true; done
    for a in $ARCHES; do
        # qemu-user-static instala qemu-<arch>-static; a matriz chama qemu-<arch>.
        if [ -x "$PREFIX/usr/bin/qemu-$a-static" ] && [ ! -e "$PREFIX/usr/bin/qemu-$a" ]; then
            ln -sf "qemu-$a-static" "$PREFIX/usr/bin/qemu-$a"
        fi
    done
else
    echo "setup-cross-toolchain: $PREFIX ja preparado (nada a baixar)" >&2
fi

EXPORTS="export PATH=\"$PREFIX/usr/bin:\$PATH\"
export LD_LIBRARY_PATH=\"$PREFIX/usr/lib/x86_64-linux-gnu:\${LD_LIBRARY_PATH:-}\"
export KOF_CROSS_SYSROOT=\"$PREFIX\""

if [ "${1:-}" = "--export" ]; then
    printf '%s\n' "$EXPORTS"
    exit 0
fi

# verificacao honesta: sem os binarios funcionando, nao adianta exportar.
bad=0
for a in $ARCHES; do
    if ! LD_LIBRARY_PATH="$PREFIX/usr/lib/x86_64-linux-gnu" \
         "$PREFIX/usr/bin/$a-linux-gnu-as" --version >/dev/null 2>&1; then
        echo "ERRO: $a-linux-gnu-as nao executou (libs faltando?)" >&2; bad=1; fi
    [ -x "$PREFIX/usr/bin/qemu-$a" ] || { echo "ERRO: qemu-$a ausente" >&2; bad=1; }
done
[ "$bad" -eq 0 ] || exit 1

echo "setup-cross-toolchain: OK — rode no shell atual:"
printf '%s\n' "$EXPORTS"
