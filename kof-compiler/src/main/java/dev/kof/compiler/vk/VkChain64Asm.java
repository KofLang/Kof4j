package dev.kof.compiler.vk;

/**
 * M36.5: tradução do vkchain64.c (C) para assembly x86-64 emitido
 * aqui — o native ganha o GPU path int64 (matvec residente, w32 e
 * split) sem lib C compilada do gcc. O .s gerado dlopena a
 * libvulkan.so.1 e chama a API vk* via dlsym (mesma cadeia do C:
 * instance → physical 0 → device → shader module → desc layouts
 * 3/5 → pipelines → buffers host-visible coherent mapeados →
 * descriptor sets → cmd buffer → dispatch + fence).
 *
 * Contrato do C preservado (os símbolos kof_mv64_* são o que o
 * programa kf chama; arrays kf = KofArray* com dados inline em +24):
 *   kof_mv64_set_shape(m, k)                       → 0 ok / rc
 *   kof_mv64_load_w(w*, m, k)                      → 0 ok
 *   kof_mv64_matvec(x*, y*, m, k)                  → 0 ok
 *   kof_mv64_wput(id, w*, m, k)                    → 0 ok
 *   kof_mv64_wrun(id, x*, y*, m, k, div)           → 0 ok
 *   kof_mv64_wput32(id, w*, m, k)                  → 0 ok
 *   kof_mv64_wrun32(id, x*, y*, m, k, div)         → 0 ok
 *   kof_mv64_wputsp(id, wh*, wl*, m, k)            → 0 ok
 *   kof_mv64_wrunsp(id, x*, y*, m, k, div)         → 0 ok
 *   kof_vk_dispatch64(a*, b*, c*, m, n, k)         → 0 ok
 * Qualquer falha → rc != 0 e o caller degrada p/ golden CPU
 * (nunca derruba o programa).
 *
 * SPVs: env KOF_GPU_SPV64 (matvec64, obrigatório p/ o mv64),
 * KOF_GPU_SPV64_W32 e KOF_GPU_SPV64_SPLIT (opcionais; pipeline
 * ausente → wrun32/wrunsp retornam rc != 0 e o caller usa CPU).
 */
final class VkChain64Asm {
    private VkChain64Asm() {}

    /** Injeta o .s no runtime nativo (substitui os stubs kof_mv64_*). */
    static String source() {
        StringBuilder sb = new StringBuilder(64 * 1024);
        VkChain64Data.source(sb);
        VkChain64Loader.source(sb);
        VkChain64Helpers.source(sb);
        VkChain64Init.source(sb);
        VkChain64Alloc.source(sb);
        VkChain64Submit.sourceSubmit(sb);
        VkChain64Submit.sourceWriteDesc(sb);
        VkChain64Shape.sourceShapeXy(sb);
        VkChain64Shape.sourceSetShape(sb);
        VkChain64Matvec.sourceLoadW(sb);
        VkChain64Matvec.sourceMatvec(sb);
        VkChain64W64.sourceWput(sb);
        VkChain64W64.sourceWrun(sb);
        VkChain64W32.sourceWput32(sb);
        VkChain64W32.sourceWrun32(sb);
        VkChain64WSp.sourceWputsp(sb);
        VkChain64WSp.sourceWrunsp(sb);
        VkChain64Dispatch.source(sb);
        return sb.toString();
    }
}
