package dev.kof.compiler;

/**
 * Fragmento do source do KofRuntime gerado (REFACTOR-500 Fase 8).
 * kof.vulkan buffers+submit+matvec+matmul (vkGrow..dispatch64) - parte 3/3 de JvmVkRuntime. Concatenacao preserva byte-a-byte.
 */
final class JvmVkDispatchRuntime {

    private JvmVkDispatchRuntime() {}

    static String source() {
        return """
    // (re)dimensiona buffer com capacidade: idempotente
    private static void vkGrow(java.lang.foreign.MemorySegment[] slot, long[] cap,
            long bytes) throws Throwable {
        if (slot[2] != null && cap[0] >= bytes) return;
        vkDrop(slot);
        vkHostBuffer(bytes, slot);
        cap[0] = bytes;
    }

                // slot W residente por id: aloca se cresceu; devolve o mapped
                private static java.lang.foreign.MemorySegment vkSlot(
                        java.lang.foreign.MemorySegment[] slot, long[] cap, long bytes)
                        throws Throwable {
                    if (slot[2] != null && cap[0] >= bytes) return slot[2];
                    vkDrop(slot);
                    vkHostBuffer(bytes, slot);
                    cap[0] = bytes;
                    return slot[2];
                }

                private static void putLongs(java.lang.foreign.MemorySegment map,
                        long[] v, int n) {
                    for (int i = 0; i < n; i++) {
                        map.setAtIndex(java.lang.foreign.ValueLayout.JAVA_LONG, i, v[i]);
                    }
                }

                private static void getLongs(java.lang.foreign.MemorySegment map,
                        long[] out, int n) {
                    for (int i = 0; i < n; i++) {
                        out[i] = map.getAtIndex(java.lang.foreign.ValueLayout.JAVA_LONG, i);
                    }
                }

                private static void putInts(java.lang.foreign.MemorySegment map,
                        int[] v, int n) {
                    for (int i = 0; i < n; i++) {
                        map.setAtIndex(java.lang.foreign.ValueLayout.JAVA_INT, i, v[i]);
                    }
                }

                // VkDescriptorBufferInfo (24B): buffer@0, offset@8, range@16 =
                // WHOLE_SIZE. VkWriteDescriptorSet (64B): dstSet@8,
                // dstBinding@16, descriptorCount@20, descriptorType@24,
                // pBufferInfo@48.
                // VkDescriptorBufferInfo (24B): buffer@0, offset@8, range@16 =
                // WHOLE_SIZE. VkWriteDescriptorSet (64B): dstSet@16,
                // dstBinding@24, dstArrayElement@28, descriptorCount@32,
                // descriptorType@36, pImageInfo@40, pBufferInfo@48.
                private static void vkBindBuf(java.lang.foreign.MemorySegment dset,
                        int binding, java.lang.foreign.MemorySegment buf) throws Throwable {
                    var dbi = vkAlloc(24);
                    putP(dbi, 0, buf);
                    putL(dbi, 8, 0L);
                    putL(dbi, 16, -1L);
                    var w = vkAlloc(64);
                    putI(w, 0, 35);
                    putP(w, 16, dset);
                    putI(w, 24, binding);
                    putI(w, 28, 0);
                    putI(w, 32, 1);
                    putI(w, 36, 7);
                    putP(w, 48, dbi);
                    vkUpdateDescriptorSets.invoke(VK_DEV, 1, w, 0, vkNull());
                }

                // N writes em UMA chamada de vkUpdateDescriptorSets
                // (o padrao que funciona nos probes; updates separados
                // por binding mostraram SSBOs invisiveis ao GPU)
                private static void vkBindBufAll(java.lang.foreign.MemorySegment dset,
                        java.lang.foreign.MemorySegment[] bufs) throws Throwable {
                    int n = bufs.length;
                    var wds = vkAlloc(64L * n);
                    for (int b = 0; b < n; b++) {
                        long o = (long) b * 64;
                        var dbi = vkAlloc(24);
                        putP(dbi, 0, bufs[b]);
                        putL(dbi, 8, 0L);
                        putL(dbi, 16, -1L);
                        putI(wds, o, 35);
                        putP(wds, o + 16, dset);
                        putI(wds, o + 24, b);
                        putI(wds, o + 28, 0);
                        putI(wds, o + 32, 1);
                        putI(wds, o + 36, 7);
                        putP(wds, o + 48, dbi);
                    }
                    vkUpdateDescriptorSets.invoke(VK_DEV, n, wds, 0, vkNull());
                }

                // dispatch matvec (push 24B {m:k32, k:k32, divId, pad, div:i64},
                // layout do matvec64.comp/w32/split), fence + wait
                private static void vkRunMV(java.lang.foreign.MemorySegment pipe,
                        java.lang.foreign.MemorySegment dset, boolean five,
                        java.lang.foreign.MemorySegment[] bufs, int m, int k, long div)
                        throws Throwable {
                    var layout = five ? VK_PL5 : VK_PL3;
                    vkBindBufAll(dset, bufs);
                    vkSubmit(pipe, layout, dset, m, k, div, 24);
                }

                // dispatch matmul (push 12B {m,n,k}; 1 WG por ELEMENTO c)
                private static void vkRunMM(java.lang.foreign.MemorySegment pipe,
                        java.lang.foreign.MemorySegment dset, int m, int n, int k)
                        throws Throwable {
                    vkBindBufAll(dset, new java.lang.foreign.MemorySegment[]{S_A[0], S_B[0], S_C[0]});
                    vkSubmitMM(pipe, VK_PL12, dset, m, n, k);
                }

                private static void vkSubmit(java.lang.foreign.MemorySegment pipe,
                        java.lang.foreign.MemorySegment layout,
                        java.lang.foreign.MemorySegment dset, int m, int k, long div,
                        int pushSize) throws Throwable {
                    // VkCommandBufferBeginInfo (32B): flags@16 =
                    // USAGE_ONE_TIME_SUBMIT(1), pInheritanceInfo@24
                    var bbi = vkAlloc(32);
                    putI(bbi, 0, 42);
                    putI(bbi, 16, 1);
                    vk((int) vkBeginCommandBuffer.invoke(VK_CMD, bbi), "begin");
                    vkCmdBindPipeline.invoke(VK_CMD, 1, pipe);
                    var dsets = vkAlloc(8);
                    putP(dsets, 0, dset);
                    vkCmdBindDescriptorSets.invoke(VK_CMD, 1, layout, 0, 1, dsets, 0, vkNull());
                    int divId = (div == 1_000_000_000L) ? 0 : ((div == 1_000_000L) ? 1 : 2);
                    var push = vkAlloc(24);
                    putI(push, 0, m);
                    putI(push, 4, k);
                    putI(push, 8, divId);
                    putI(push, 12, 0);
                    putL(push, 16, div);
                    vkCmdPushConstants.invoke(VK_CMD, layout, 0x20, 0, pushSize, push);
                    vkCmdDispatch.invoke(VK_CMD, m, 1, 1);
                    vkEndAndWait();
                }

                private static void vkSubmitMM(java.lang.foreign.MemorySegment pipe,
                        java.lang.foreign.MemorySegment layout,
                        java.lang.foreign.MemorySegment dset, int m, int n, int k)
                        throws Throwable {
                    var bbi = vkAlloc(32);
                    putI(bbi, 0, 42);
                    putI(bbi, 16, 1);
                    vk((int) vkBeginCommandBuffer.invoke(VK_CMD, bbi), "begin");
                    vkCmdBindPipeline.invoke(VK_CMD, 1, pipe);
                    var dsets = vkAlloc(8);
                    putP(dsets, 0, dset);
                    vkCmdBindDescriptorSets.invoke(VK_CMD, 1, layout, 0, 1, dsets, 0, vkNull());
                    var push = vkAlloc(12);
                    putI(push, 0, m);
                    putI(push, 4, n);
                    putI(push, 8, k);
                    vkCmdPushConstants.invoke(VK_CMD, layout, 0x20, 0, 12, push);
                    vkCmdDispatch.invoke(VK_CMD, m * n, 1, 1);
                    vkEndAndWait();
                }

                private static void vkEndAndWait() throws Throwable {
                    vk((int) vkEndCommandBuffer.invoke(VK_CMD), "end");
                    // vkQueueSubmit2: VkCommandBufferSubmitInfo (32B, cmd@16)
                    // + VkSubmitInfo2 (64B: flags@16, cmdCount@32, pCmdInfos@40)
                    var cbsi = vkAlloc(32);
                    putI(cbsi, 0, 1000314006);
                    putP(cbsi, 16, VK_CMD);
                    var si = vkAlloc(64);
                    putI(si, 0, 1000314004);
                    putI(si, 32, 1);
                    putP(si, 40, cbsi);
                    vk((int) vkQueueSubmit2.invoke(VK_QUEUE, 1, si, VK_FENCE), "submit");
                    // pFences é pointer p/ array de fences
                    var rf = vkAlloc(8);
                    putP(rf, 0, VK_FENCE);
                    vk((int) vkWaitForFences.invoke(VK_DEV, 1, rf, 1, 5_000_000_000L),
                            "wait fence");
                    vkResetFences.invoke(VK_DEV, 1, rf);
                }

                // ── matvec residente (contrato do vkchain64.c) ────────────────

                public static int kof_mv64_set_shape(int m, int k) {
                    if (!kof_vk_available() || VK_PIPE64 == null) return -1;
                    try {
                        vkGrow(S_X, C_X, (long) k * 8);
                        vkGrow(S_Y, C_Y, (long) m * 8);
                        VK_CURM = m;
                        VK_CURK = k;
                        return 0;
                    } catch (Throwable t) {
                        return -1;
                    }
                }

                public static int kof_mv64_load_w(long[] w, int m, int k) {
                    if (!kof_vk_available() || VK_PIPE64 == null) return -1;
                    try {
                        var map = vkSlot(S_W, C_W, (long) m * k * 8);
                        putLongs(map, w, m * k);
                        VK_CURM = m;
                        VK_CURK = k;
                        return 0;
                    } catch (Throwable t) {
                        return -1;
                    }
                }

                public static int kof_mv64_matvec(long[] x, long[] y, int m, int k) {
                    if (!kof_vk_available() || VK_PIPE64 == null || S_W[2] == null) return -1;
                    if (VK_CURM != m || VK_CURK != k) return -2;
                    try {
                        vkGrow(S_X, C_X, (long) k * 8);
                        vkGrow(S_Y, C_Y, (long) m * 8);
                        putLongs(S_X[2], x, k);
                        vkRunMV(VK_PIPE64, VK_DSET3, false,
                                new java.lang.foreign.MemorySegment[]{S_W[0], S_X[0], S_Y[0]},
                                m, k, 0L);
                        getLongs(S_Y[2], y, m);
                        return 0;
                    } catch (Throwable t) {
                        return -1;
                    }
                }

                public static int kof_mv64_wput(int id, long[] w, int m, int k) {
                    if (!kof_vk_available() || VK_PIPE64 == null) return -1;
                    if (id < 0 || id >= VK_WMAX || (long) m * k <= 0) return -2;
                    try {
                        var map = vkSlot(W64[id], W64CAP[id], (long) m * k * 8);
                        putLongs(map, w, m * k);
                        return 0;
                    } catch (Throwable t) {
                        return -1;
                    }
                }

                public static int kof_mv64_wrun(int id, long[] x, long[] y, int m, int k,
                        long div) {
                    if (!kof_vk_available() || VK_PIPE64 == null) return -1;
                    if (id < 0 || id >= VK_WMAX || W64[id][2] == null
                            || W64CAP[id][0] < (long) m * k * 8) return -2;
                    try {
                        vkGrow(S_X, C_X, (long) k * 8);
                        vkGrow(S_Y, C_Y, (long) m * 8);
                        putLongs(S_X[2], x, k);
                        vkRunMV(VK_PIPE64, VK_DSET3, false,
                                new java.lang.foreign.MemorySegment[]{W64[id][0], S_X[0], S_Y[0]},
                                m, k, div);
                        getLongs(S_Y[2], y, m);
                        return 0;
                    } catch (Throwable t) {
                        return -1;
                    }
                }

                public static int kof_mv64_wput32(int id, int[] w, int m, int k) {
                    if (!kof_vk_available() || VK_PIPE32 == null) return -6;
                    if (id < 0 || id >= VK_WMAX || (long) m * k <= 0) return -2;
                    try {
                        var map = vkSlot(W32[id], W32CAP[id], (long) m * k * 4);
                        putInts(map, w, m * k);
                        return 0;
                    } catch (Throwable t) {
                        return -1;
                    }
                }

                public static int kof_mv64_wrun32(int id, long[] x, long[] y, int m, int k,
                        long div) {
                    if (!kof_vk_available() || VK_PIPE32 == null) return -6;
                    if (id < 0 || id >= VK_WMAX || W32[id][2] == null
                            || W32CAP[id][0] < (long) m * k * 4) return -2;
                    try {
                        vkGrow(S_X32, C_X32, (long) k * 8);
                        vkGrow(S_Y32, C_Y32, (long) m * 8);
                        putLongs(S_X32[2], x, k);
                        vkRunMV(VK_PIPE32, VK_DSET3, false,
                                new java.lang.foreign.MemorySegment[]{W32[id][0], S_X32[0], S_Y32[0]},
                                m, k, div);
                        getLongs(S_Y32[2], y, m);
                        return 0;
                    } catch (Throwable t) {
                        return -1;
                    }
                }

                public static int kof_mv64_wputsp(int id, int[] wh, int[] wl, int m, int k) {
                    if (!kof_vk_available() || VK_PIPESPL == null) return -6;
                    if (id < 0 || id >= VK_WMAX || (long) m * k <= 0) return -2;
                    try {
                        var mh = vkSlot(WH[id], WHCAP[id], (long) m * k * 4);
                        putInts(mh, wh, m * k);
                        var ml = vkSlot(WL[id], WLCAP[id], (long) m * k * 4);
                        putInts(ml, wl, m * k);
                        return 0;
                    } catch (Throwable t) {
                        return -1;
                    }
                }

                public static int kof_mv64_wrunsp(int id, long[] x, long[] y, int m, int k,
                        long div) {
                    if (!kof_vk_available() || VK_PIPESPL == null || VK_DSET5 == null) return -6;
                    if (id < 0 || id >= VK_WMAX || WH[id][2] == null
                            || WHCAP[id][0] < (long) m * k * 4
                            || WL[id][2] == null || WLCAP[id][0] < (long) m * k * 4) return -2;
                    try {
                        vkGrow(S_XH, C_XH, (long) k * 4);
                        vkGrow(S_XL, C_XL, (long) k * 4);
                        vkGrow(S_Y, C_Y, (long) m * 8);
                        // xh/xl no host (trunc, igual Kof): xh = x/1e6, xl = x%1e6
                        var xhmap = S_XH[2];
                        var xlmap = S_XL[2];
                        for (int i = 0; i < k; i++) {
                            long xi = x[i];
                            xhmap.setAtIndex(java.lang.foreign.ValueLayout.JAVA_INT, i,
                                    (int) (xi / 1000000));
                            xlmap.setAtIndex(java.lang.foreign.ValueLayout.JAVA_INT, i,
                                    (int) (xi % 1000000));
                        }
                        vkRunMV(VK_PIPESPL, VK_DSET5, true,
                                new java.lang.foreign.MemorySegment[]{WH[id][0], WL[id][0],
                                        S_XH[0], S_XL[0], S_Y[0]},
                                m, k, div);
                        getLongs(S_Y[2], y, m);
                        return 0;
                    } catch (Throwable t) {
                        return -1;
                    }
                }

                // ── matmul (gpu.dispatchMatmul / dispatchMatmul64) ────────────

                public static int kof_vk_dispatch(int[] a, int[] b, int[] c,
                        int m, int n, int k) {
                    if (!kof_vk_available() || VK_PIPEMM == null || VK_DSET12 == null) return -1;
                    try {
                        vkGrow(S_A, C_A, (long) m * k * 4);
                        vkGrow(S_B, C_B, (long) k * n * 4);
                        vkGrow(S_C, C_C, (long) m * n * 4);
                        putInts(S_A[2], a, m * k);
                        putInts(S_B[2], b, k * n);
                        vkRunMM(VK_PIPEMM, VK_DSET12, m, n, k);
                        for (int i = 0; i < m * n; i++) {
                            c[i] = S_C[2].getAtIndex(java.lang.foreign.ValueLayout.JAVA_INT, i);
                        }
                        return 0;
                    } catch (Throwable t) {
                        VK_ERR = "dispatch32: " + t;
                        return -1;
                    }
                }

                public static int kof_vk_dispatch64(long[] a, long[] b, long[] c,
                        int m, int n, int k) {
                    if (!kof_vk_available() || VK_PIPEMM64 == null || VK_DSET12 == null) return -1;
                    try {
                        vkGrow(S_A64, C_A64, (long) m * k * 8);
                        vkGrow(S_B64, C_B64, (long) k * n * 8);
                        vkGrow(S_C64, C_C64, (long) m * n * 8);
                        putLongs(S_A64[2], a, m * k);
                        putLongs(S_B64[2], b, k * n);
                        vkBindBufAll(VK_DSET12, new java.lang.foreign.MemorySegment[]{S_A64[0], S_B64[0], S_C64[0]});
                        var bbi = vkAlloc(32);
                        putI(bbi, 0, 42);
                        putI(bbi, 16, 1);
                        vk((int) vkBeginCommandBuffer.invoke(VK_CMD, bbi), "begin");
                        vkCmdBindPipeline.invoke(VK_CMD, 1, VK_PIPEMM64);
                        var dsets = vkAlloc(8);
                        putP(dsets, 0, VK_DSET12);
                        vkCmdBindDescriptorSets.invoke(VK_CMD, 1, VK_PL12, 0, 1, dsets, 0, vkNull());
                        var push = vkAlloc(12);
                        putI(push, 0, m);
                        putI(push, 4, n);
                        putI(push, 8, k);
                        vkCmdPushConstants.invoke(VK_CMD, VK_PL12, 0x20, 0, 12, push);
                        vkCmdDispatch.invoke(VK_CMD, m * n, 1, 1);
                        vkEndAndWait();
                        getLongs(S_C64[2], c, m * n);
                        return 0;
                    } catch (Throwable t) {
                        VK_ERR = "dispatch64: " + t;
                        return -1;
                    }
                }
            }""";
    }
}
