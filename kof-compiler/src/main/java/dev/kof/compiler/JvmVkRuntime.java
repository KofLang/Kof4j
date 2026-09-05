package dev.kof.compiler;

/**
 * M36.4: Vulkan compute 100% FFM (java.lang.foreign) — substitui a
 * libvkchain64.c/vkchain.c. O text block VK_SOURCE é injetado
 * dentro da classe KofRuntime gerada quando o programa usa kof.vk /
 * kof_mv64_* — o runtime fica autocontido (sem lib C, sem jar extra).
 *
 * Cadeia idêntica ao vkchain64.c: instance → physical 0 → device (queue
 * compute) → shader module (SPV) → desc layouts (3/5 SSBOs) → pipelines →
 * buffers host-visible coherent mapeados → descriptor sets → cmd buffer →
 * dispatch + fence. Structs Vulkan escritas byte a byte (offsets AMD64).
 *
 * Pipelines: matvec64 (W i64), matvecw32 (W i32 — metade do PCIe),
 * matvecsplit (wh/wl/xh/xl i32 → y i64 bit-exato com o CPU). SPVs: env
 * KOF_GPU_SPV64/_W32/_SPLIT, default gpu/shaders/*.spv. Pipeline ausente →
 * degrada (rc != 0) e o caller usa o golden CPU — nunca derruba o programa.
 *
 * dispatch32/dispatch64 (gpu.dispatchMatmul*) usam 1 WG por ELEMENTO c
 * (matmul.spv/matmul64.spv, push 12B {m,n,k}) — buffers a/b/c próprios.
 * REFACTOR-500 Fase 8: o source foi dividido em fragmentos (classes
 * Jvm*Part) no mesmo pacote; a concatenacao preserva byte-a-byte.
 */
final class JvmVkRuntime {

    private JvmVkRuntime() {}

    static String source() {
        return JvmVkInitRuntime.source() + JvmVkBuildRuntime.source() + JvmVkDispatchRuntime.source();
    }
}
