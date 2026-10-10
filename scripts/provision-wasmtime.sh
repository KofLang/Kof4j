#!/usr/bin/env bash
# provision-wasmtime.sh — baixa e instala o host WebAssembly (wasmtime) e o
# conjunto de ferramentas de validação (wasm-tools) em prefixo local sem root,
# espelhando o padrão de provisionamento do repo (~/.local/share/kof-wasm).
#
# Provê `wasmtime` (49.0.2) e `wasm-tools` (1.261.0) para x86_64 Linux a partir
# dos releases oficiais da Bytecode Alliance, conferindo checksums SHA-256
# medidos e pinados. Ao final executa um auto-teste compilando e executando
# uma função escalar WASM simples.
#
# Uso: bash scripts/provision-wasmtime.sh
set -euo pipefail

PREFIX="${KOF_WASM_HOME:-$HOME/.local/share/kof-wasm}"
mkdir -p "$PREFIX/bin"

WASMTIME_VER="v49.0.2"
WASMTIME_TAR="wasmtime-${WASMTIME_VER}-x86_64-linux.tar.xz"
WASMTIME_URL="https://github.com/bytecodealliance/wasmtime/releases/download/${WASMTIME_VER}/${WASMTIME_TAR}"
WASMTIME_SHA="a4d6e9e3a5a60f527cf7793d674c48930c80c2e8977995b8a275cad3254b9322"

WASMTOOLS_VER="1.261.0"
WASMTOOLS_TAR="wasm-tools-${WASMTOOLS_VER}-x86_64-linux.tar.gz"
WASMTOOLS_URL="https://github.com/bytecodealliance/wasm-tools/releases/download/v${WASMTOOLS_VER}/${WASMTOOLS_TAR}"
WASMTOOLS_SHA="ad62b2176037e93e1348cb65d6212d128ca9f097b63d155569f25215818ff7b1"

echo "== prefix: $PREFIX"

cd "$PREFIX"

if [ ! -f "$WASMTIME_TAR" ] || [ "$(sha256sum "$WASMTIME_TAR" | awk '{print $1}')" != "$WASMTIME_SHA" ]; then
    echo "== baixando wasmtime ${WASMTIME_VER}"
    curl -sSL "$WASMTIME_URL" -o "$WASMTIME_TAR"
    echo "$WASMTIME_SHA  $WASMTIME_TAR" | sha256sum -c -
fi

if [ ! -x "$PREFIX/bin/wasmtime" ]; then
    echo "== descompactando wasmtime"
    tar xJf "$WASMTIME_TAR"
    ln -sf "$PREFIX/wasmtime-${WASMTIME_VER}-x86_64-linux/wasmtime" "$PREFIX/bin/wasmtime"
fi

if [ ! -f "$WASMTOOLS_TAR" ] || [ "$(sha256sum "$WASMTOOLS_TAR" | awk '{print $1}')" != "$WASMTOOLS_SHA" ]; then
    echo "== baixando wasm-tools ${WASMTOOLS_VER}"
    curl -sSL "$WASMTOOLS_URL" -o "$WASMTOOLS_TAR"
    echo "$WASMTOOLS_SHA  $WASMTOOLS_TAR" | sha256sum -c -
fi

if [ ! -x "$PREFIX/bin/wasm-tools" ]; then
    echo "== descompactando wasm-tools"
    tar xzf "$WASMTOOLS_TAR"
    ln -sf "$PREFIX/wasm-tools-${WASMTOOLS_VER}-x86_64-linux/wasm-tools" "$PREFIX/bin/wasm-tools"
fi

echo "== auto-teste wasmtime + wasm-tools"
TEST_WAT=$(mktemp --suffix=.wat)
trap 'rm -f "$TEST_WAT"' EXIT
cat <<'WAT' > "$TEST_WAT"
(module
  (func (export "add") (param i64 i64) (result i64)
    local.get 0
    local.get 1
    i64.add
  )
)
WAT

"$PREFIX/bin/wasm-tools" validate "$TEST_WAT"
OUT=$("$PREFIX/bin/wasmtime" run --invoke add "$TEST_WAT" 40 2 2>&1 | tail -n 1)

if [ "$OUT" = "42" ]; then
    echo "== auto-teste OK: 40 + 2 = 42"
    echo "== WASM host pronto em $PREFIX/bin"
else
    echo "== auto-teste FALHOU: saída inesperada: $OUT"
    exit 1
fi
