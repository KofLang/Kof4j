#!/usr/bin/env bash
# provision-macho-toolchain.sh — instala, sem root, um ASSEMBLER/LINKER
# Mach-O (o clang com os alvos Darwin) num prefixo local — o padrão de
# provisionamento do repo (provision-wasmtime.sh, provision-esp8266.sh,
# provision-mcu-qemu.sh).
#
# POR QUE (#1135, D-NATIVE-MACOS, 22/11): a prova da etapa M-1 (o emissor de
# diretivas Mach-O) era ESTRUTURAL — o texto traduzido, sem um assembler que
# o ACEITASSE. Medido nesta frota: o texto do meu M-1 (commit 59edd37a3)
# TRAVAVA o compilador num loop infinito e ainda corrompia os imediatos
# (`movq $1` -> `movq _$1`) — defeitos que só o assembler real denuncia. Com o
# clang de verdade, a prova passa a ser "o assembler Mach-O ACEITA o texto
# traduzido" — o mesmo padrão do gate 1 do ESP8266 (o byte-order medido).
#
# O clang vem do pacote oficial do Zig (o Zig embute o clang 18 com os alvos
# Darwin; o `zig cc -target x86_64-macos-none` assembla e linka Mach-O).
# O checksum SHA-256 é medido e pinado.
#
# Uso: bash scripts/provision-macho-toolchain.sh
set -euo pipefail

PREFIX="${KOF_MACHO_HOME:-$HOME/.local/share/kof-macho}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

ZIG_VER="0.13.0"
TARBALL="zig-linux-x86_64-${ZIG_VER}.tar.xz"
URL="https://ziglang.org/download/${ZIG_VER}/${TARBALL}"
SHA256="d45312e61ebcc48032b77bc4cf7fd6915c11fa16e4aad116b66c9468211230ea"

echo "== prefix: $PREFIX"
mkdir -p "$PREFIX/bin"

cd "$WORK"
if [ ! -f "$TARBALL" ] || [ "$(sha256sum "$TARBALL" | awk '{print $1}')" != "$SHA256" ]; then
    echo "== baixando zig ${ZIG_VER} (o clang embutido)"
    curl -sSL "$URL" -o "$TARBALL"
    echo "$SHA256  $TARBALL" | sha256sum -c -
fi

if [ ! -x "$PREFIX/bin/zig" ]; then
    echo "== descompactando"
    tar xf "$TARBALL" -C "$PREFIX" --strip-components=1
    ln -sf "$PREFIX/zig" "$PREFIX/bin/zig"
fi

echo "== auto-teste: o assembler Mach-O aceita um texto Mach-O e produz um objeto"
cat > "$WORK/t.s" <<'ASM'
.section __TEXT,__text,regular,pure_instructions
.globl _kof_probe
_kof_probe:
    movq $1, %rdi
    leaq _kof_msg(%rip), %rsi
    movq $3, %rdx
    call _write
    movq $0, %rax
    ret
.section __TEXT,__const,regular,pure_instructions
_kof_msg:
    .asciz "hi\n"
ASM

if ! "$PREFIX/bin/zig" cc -target x86_64-macos-none -c "$WORK/t.s" -o "$WORK/t.o" 2>"$WORK/err"; then
    echo "== auto-teste FALHOU: o clang recusou o assembly Mach-O:"
    cat "$WORK/err"
    exit 1
fi
if ! file "$WORK/t.o" | grep -q "Mach-O 64-bit x86_64 object"; then
    echo "== auto-teste FALHOU: o objeto não é Mach-O:"
    file "$WORK/t.o"
    exit 1
fi

echo "== auto-teste OK: o assembler Mach-O está em $PREFIX/bin/zig"
echo "== (o alvo arm64 — o Apple silicon, a face do M-1) também:"
cat > "$WORK/a64.s" <<'ASM'
.section __TEXT,__text,regular,pure_instructions
.globl _kof_probe
_kof_probe:
    mov x0, #1
    ret
ASM
if "$PREFIX/bin/zig" cc -target aarch64-macos-none -c "$WORK/a64.s" -o "$WORK/a64.o" 2>"$WORK/err64" \
    && file "$WORK/a64.o" | grep -q "Mach-O 64-bit arm64 object"; then
    echo "== o alvo arm64 (Apple silicon) também assembla"
else
    echo "== AVISO: o alvo arm64 não assemblou:"
    cat "$WORK/err64"
fi
