#!/usr/bin/env bash
# build.sh — compila e instala a libvkchain.so (M32.3) + os SPVs do matvec
# (M36: matvec64 / matvecw32 / matvecsplit).
# Requer: gcc, libvulkan-dev (headers vulkan/vulkan.h), glslc (shaderc)
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$DIR/../.." && pwd)"
SHADERS="$ROOT/gpu/shaders"
mkdir -p "$SHADERS"
gcc -O1 -shared -fPIC -o "$DIR/libvkchain.so" "$DIR/vkchain.c" -lvulkan
gcc -O1 -shared -fPIC -o "$DIR/libvkchain64.so" "$DIR/vkchain64.c" -lvulkan
for f in matvec64 matvecw32 matvecsplit matmul matmul64; do
    glslc --target-spv=spv1.0 -o "$SHADERS/$f.spv" "$DIR/$f.comp"
    echo "spv: $SHADERS/$f.spv"
done
if [ -w /usr/local/lib ] || sudo -n true 2>/dev/null; then
    sudo cp "$DIR/libvkchain.so" "$DIR/libvkchain64.so" /usr/local/lib/ && sudo ldconfig
    echo "instalada em /usr/local/lib/libvkchain.so"
else
    echo "libvkchain.so gerada em $DIR — copie para /usr/local/lib e rode ldconfig"
    echo "(ou deixe ./libvkchain.so ao lado do binário nativo; KOF_GPU_SPV aponta o .spv)"
fi
