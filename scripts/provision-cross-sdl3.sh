#!/usr/bin/env bash
#
# provision-cross-sdl3.sh — venda o SDL3 (3.4.x) + o fecho de runtime dele no
# sysroot cross (aarch64 + riscv64) SEM root, a partir dos repositorios
# openSUSE Ports.
#
# Por que: a fatia 3.1 do graphics/gaming (`D-GRAPHICS-GAMING` + `G1` SDL3 em
# `D-MAINT-BATCH-0510`) precisa das libs+headers da stack escolhida presentes no
# sysroot cross ANTES de a API de janela/loop pousar honestamente nos alvos
# cross. O `scripts/setup-cross-toolchain.sh` traz so a libc; este script
# adiciona a stack de midia do SDL3 por cima.
#
# O SDL3 e ligado dinamicamente com um fecho grande (audio/X11/wayland/drm...).
# O fecho abaixo foi MEDIDO (ldd da libSDL3 do host, 05-06/10) e e resolvido por
# NOME BASE contra o listing do Ports, entao as versoes acompanham o repo.
#
# Layout aplicado (layout Debian multiarch que o runtime cross ja usa):
#   <sysroot>/usr/<arch>-linux-gnu/include/SDL3/   (headers)
#   <sysroot>/usr/<arch>-linux-gnu/lib/            (libSDL3.so*, deps)
#
# Uso:
#   bash scripts/provision-cross-sdl3.sh
#   KOF_CROSS_SYSROOT=/opt/kof-cross bash scripts/provision-cross-sdl3.sh
#
# Depois: o E2E `Sdl3FfiCrossE2ETest` (JVM + Native x86-64 + riscv64 + aarch64).
set -uo pipefail

SYSROOT="${KOF_CROSS_SYSROOT:-$HOME/.local/share/kof-cross}"
PORTS_BASE="https://download.opensuse.org/ports"

# SDL3 direto + o fecho de runtime medido (nomes de PACOTE openSUSE).
SDL3_PKGS="libSDL3-0 SDL3-devel"
# glibc do Ports: o SDL3 aarch64 exige GLIBC_2.43; a libc do sysroot Debian e
# 2.41. A glibc do Ports (2.44) e superconjunto de simbolos — forward-compatible
# com os binarios ligados contra a 2.41.
GLIBC_PKG="glibc"
DEP_PKGS="
libasound2 libdbus-1-3 libdecor-0-0 libdrm2 libffi8 libFLAC14 libgbm1
libgcc_s1 libogg0 libopus0 libpipewire-0_3-0 libpulse0 libsndfile1
libsystemd0 libvorbis0 libvorbisenc2
libwayland-client0 libwayland-cursor0 libwayland-egl1
libX11-6 libXau6 libxcb1 libXcursor1 libXext6 libXfixes3 libXi6
libxkbcommon0 libXrandr2 libXrender1 libXss1 libXtst6
"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

fail() { echo "ERRO: $*" >&2; exit 1; }

for tool in rpm2cpio cpio curl; do
    command -v "$tool" >/dev/null 2>&1 || fail "$tool ausente"
done

[ -d "$SYSROOT" ] || fail "sysroot '$SYSROOT' nao existe — rode scripts/setup-cross-toolchain.sh primeiro"

resolve_arch() { [ "$1" = riscv64 ] && echo riscv || echo aarch64; }

# Baixa o listing do Ports uma vez por arch e resolve <base>-<ver>.<arch>.rpm.
fetch_listing() {
    local arch="$1" dir
    dir="$(resolve_arch "$arch")"
    local out="$WORK/listing-$arch.html"
    curl -sSL --max-time 180 -o "$out" \
        "$PORTS_BASE/$dir/tumbleweed/repo/oss/$arch/" || fail "listing $arch inacessivel"
    echo "$out"
}

# Resolve um pacote por nome base no listing (primeiro match). Ecoa vazio se nao achar.
resolve_pkg() {
    local listing="$1" base="$2" arch="$3"
    grep -oE "${base}-[0-9][^\"<>]*\.${arch}\.rpm" "$listing" | sort -u | head -1
}

# Baixa + extrai um rpm em <destino>.
fetch_extract() {
    local arch="$1" pkg="$2" dest="$3"
    [ -n "$pkg" ] || fail "pacote vazio ($arch)"
    local dir; dir="$(resolve_arch "$arch")"
    ( cd "$WORK" && curl -sSL --max-time 240 -O "$PORTS_BASE/$dir/tumbleweed/repo/oss/$arch/$pkg" ) \
        || fail "download $pkg"
    ( cd "$dest" && rpm2cpio "$WORK/$pkg" | cpio -idm --quiet --no-absolute-filenames ) 2>/dev/null || true
}

for arch in aarch64 riscv64; do
    echo "== provisionando SDL3 cross para $arch"
    listing="$(fetch_listing "$arch")"
    stage="$WORK/root-$arch"
    mkdir -p "$stage"

    # glibc + SDL3 + fecho de runtime.
    for base in $GLIBC_PKG $SDL3_PKGS $DEP_PKGS; do
        pkg="$(resolve_pkg "$listing" "$base" "$arch")"
        if [ -z "$pkg" ]; then
            echo "  AVISO: '$base' nao encontrado no Ports $arch — pulando" >&2
            continue
        fi
        fetch_extract "$arch" "$pkg" "$stage"
    done

    # Destino no sysroot.
    prefix="$SYSROOT/usr/$arch-linux-gnu"
    mkdir -p "$prefix/include" "$prefix/lib"

    # Headers.
    [ -d "$stage/usr/include/SDL3" ] && cp -a "$stage/usr/include/SDL3" "$prefix/include/"

    # Libs: os RPMs openSUSE poe em usr/lib64; a glibc tambem em usr/lib64 (e o
    # loader em usr/lib). Achatamos tudo em <prefix>/lib (o `QEMU_LD_PREFIX`
    # resolve `/lib/<soname>` la).
    for d in "$stage/usr/lib64" "$stage/usr/lib"; do
        [ -d "$d" ] || continue
        find "$d" -maxdepth 1 -type f \( -name '*.so' -o -name '*.so.*' \) \
            -exec cp -a {} "$prefix/lib/" \;
    done

    # Libs privadas (ex.: libpulsecommon em lib/pulseaudio/) — symlink no lib/
    # para o loader achar pelo soname (o RUNPATH do openSUSE e /usr/lib64/...).
    find "$prefix/lib" -mindepth 2 -name '*.so*' -type f 2>/dev/null | while read -r f; do
        b="$(basename "$f")"
        [ -e "$prefix/lib/$b" ] || ln -sf "$f" "$prefix/lib/$b"
    done

    # O loader do openSUSE (riscv64 em especial) busca em /usr/lib64, nao em
    # /lib64: popula <prefix>/usr/lib64 com TODAS as libs (symlink) para o
    # `QEMU_LD_PREFIX` resolver tanto /lib/<soname> quanto /usr/lib64/<soname>.
    # Tambem recria o RUNPATH absoluto /usr/lib64 das libs privadas.
    mkdir -p "$prefix/usr/lib64"
    find "$prefix/lib" -maxdepth 1 \( -name '*.so' -o -name '*.so.*' \) -type f 2>/dev/null | while read -r f; do
        b="$(basename "$f")"
        [ -e "$prefix/usr/lib64/$b" ] || ln -sf "$f" "$prefix/usr/lib64/$b"
    done
    find "$prefix/lib" -mindepth 2 -name '*.so*' -type f 2>/dev/null | while read -r f; do
        b="$(basename "$f")"
        [ -e "$prefix/usr/lib64/$b" ] || ln -sf "$f" "$prefix/usr/lib64/$b"
    done

    # Layout multiarch: lib64 -> lib (o binario cross procura /lib64/<soname>
    # no runtime porque a libc do openSUSE foi construida com prefixo /usr).
    [ -e "$prefix/lib64" ] || ln -sf lib "$prefix/lib64"

    # O loader riscv64 do openSUSE busca em /lib64/lp64d e /usr/lib64/lp64d
    # (triple Debian). Espelha essas pastas para o prefixo, senao o loader nao
    # acha a libc por default (so com --library-path).
    case "$arch" in
        riscv64) triples="lp64d riscv64-linux-gnu" ;;
        aarch64) triples="aarch64-linux-gnu" ;;
        *) triples="" ;;
    esac
    for t in $triples; do
        [ -e "$prefix/lib/$t" ] || ln -sfn . "$prefix/lib/$t"
        [ -e "$prefix/usr/lib64/$t" ] || ln -sfn ../../lib "$prefix/usr/lib64/$t"
    done

    # Verificacao honesta: toda NEEDED de toda lib vendada resolve no prefixo.
    echo "== verificando fecho $arch"
    missing=""
    while read -r f; do
        while read -r need; do
            [ -z "$need" ] && continue
            [ -e "$prefix/lib/$need" ] || missing="$missing $need"
        done < <(readelf -d "$f" 2>/dev/null | sed -n 's/.*NEEDED.*\[\(.*\)\]/\1/p')
    done < <(find "$prefix/lib" -maxdepth 1 -name '*.so*' -type f)
    missing="$(echo "$missing" | tr ' ' '\n' | sort -u | grep -v '^$' || true)"
    if [ -n "$missing" ]; then
        echo "  AVISO: NEEDED nao resolvido no prefixo:$missing" >&2
    else
        echo "  fecho OK ($arch)"
    fi

    echo "  SDL3 em $prefix/lib/libSDL3.so.0; headers em $prefix/include/SDL3"
done

echo
echo "Prefix pronto: $SYSROOT"
echo "Para os testes cross: export KOF_CROSS_SYSROOT=$SYSROOT"
echo "                       export KOF_CROSS_PREFIX=$SYSROOT/usr/bin"
