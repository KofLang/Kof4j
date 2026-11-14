#!/usr/bin/env bash
# provision-esp8266.sh — instala, sem root, o toolchain Xtensa com o backend
# esp8266 (kof-esp8266 gate 1), num prefixo local — o padrão do
# provision-mcu-qemu.sh (B-4). As fontes são os releases OFICIAIS da Espressif
# no GitHub: o crosstool-NG base (x86_64) + o overlay multilib esp8266.
#
# O interruptor de byte-order (MEDIDO no gate 0): sem o dynconfig
# `xtensa_esp8266.so` os objetos saem BIG-endian e o crt0 do esp8266 se recusa
# a linkar; com XTENSA_GNU_CONFIG apontado ao .so da toolchain, a saída é
# elf32-xtensa-le (a ordem real do ESP8266).
#
# Uso: bash scripts/provision-esp8266.sh
set -euo pipefail

PREFIX="${KOF_ESP_HOME:-$HOME/.local/share/kof-esp8266}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

BASE_URL="https://github.com/espressif/crosstool-NG/releases/download/esp-16.2.0_20261109"
BASE_TGZ="xtensa-esp-elf-16.2.0_20261109-x86_64-linux-gnu.tar.xz"
OVERLAY_TGZ="xtensa-esp-elf-16.2.0_20261109-esp8266-multilib.tar.xz"

echo "== prefix: $PREFIX"
mkdir -p "$PREFIX"

echo "== baixando a toolchain base (92 MB) + o overlay esp8266 (8 MB)"
( cd "$WORK" \
  && curl -sL -o base.tar.xz  "$BASE_URL/$BASE_TGZ" \
  && curl -sL -o overlay.tar.xz "$BASE_URL/$OVERLAY_TGZ" \
  && ls -la base.tar.xz overlay.tar.xz )

echo "== extraindo (o overlay vem DEPOIS: as libs esp8266 sobrescrevem as genéricas)"
tar xf "$WORK/base.tar.xz" -C "$PREFIX" --strip-components=1
tar xf "$WORK/overlay.tar.xz" -C "$PREFIX" --strip-components=1

export XTENSA_GNU_CONFIG="$PREFIX/lib/xtensa_esp8266.so"
[ -f "$XTENSA_GNU_CONFIG" ] || { echo "FALHOU: o dynconfig não está em $XTENSA_GNU_CONFIG"; exit 1; }

echo "== self-test: o byte-order (o gate-0 medido) — o objeto tem de sair LE"
TMPSELF="$(mktemp -d)"
cat > "$TMPSELF/hello.s" <<'ASM'
    .text
    .literal_position
    .globl main
    .align 4
main:
    movi a2, msg
    movi a3, 11
    call0 kof_plat_write
    movi a2, 0
    call0 kof_plat_exit
    .section .rodata
msg:
    .string "KO ESP8266\n"
ASM
"$PREFIX/bin/xtensa-esp-elf-as" -o "$TMPSELF/hello.o" "$TMPSELF/hello.s"
FMT="$("$PREFIX/bin/xtensa-esp-elf-objdump" -f "$TMPSELF/hello.o" | grep 'file format')"
case "$FMT" in
  *le*) echo "== self-test OK: $FMT (little-endian, a ordem real do ESP8266)" ;;
  *)    echo "FALHOU: $FMT — o dynconfig não está ativo (XTENSA_GNU_CONFIG)"; exit 1 ;;
esac

echo "== pronto. Para o compiler usar:"
echo "   export KOF_ESP_HOME=$PREFIX"
echo "   (o emitter descobre o par as/ld e o XTENSA_GNU_CONFIG dele mesmo;"
echo "    rodar a IMAGEM exige hardware real ou o fork qemu da Espressif —"
echo "    o qemu-system-xtensa não existe upstream)"
