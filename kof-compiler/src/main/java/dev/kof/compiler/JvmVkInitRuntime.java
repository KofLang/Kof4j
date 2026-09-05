package dev.kof.compiler;

/**
 * Fragmento do source do KofRuntime gerado (REFACTOR-500 Fase 8).
 * kof.vulkan FFM boot: handles, buffers, initAll (instance->device) - parte 1/3 de JvmVkRuntime. Concatenacao preserva byte-a-byte.
 */
final class JvmVkInitRuntime {

    private JvmVkInitRuntime() {}

    static String source() {
        return """
                // ── kof.vulkan — Vulkan compute 100% FFM (M36.4, sem libvkchain) ─
                // Toda a cadeia Vulkan vive aqui: structs byte a byte (offsets
                // AMD64 verificados contra vulkan_core.h), downcalls diretos da
                // libvulkan.so.1. Falha em qualquer passo → VK_OK=false e o
                // caller degrada p/ golden CPU. O programa nunca cai.
                private static volatile boolean VK_INITED = false;
                private static volatile boolean VK_OK = false;
                private static String VK_ERR = "not initialized";

                private static java.lang.invoke.MethodHandle vkBoot;
                private static java.lang.invoke.MethodHandle vkCreateShaderModule;
                private static java.lang.invoke.MethodHandle vkCreateDescriptorSetLayout;
                private static java.lang.invoke.MethodHandle vkCreatePipelineLayout;
                private static java.lang.invoke.MethodHandle vkCreateComputePipelines;
                private static java.lang.invoke.MethodHandle vkCreateDescriptorPool;
                private static java.lang.invoke.MethodHandle vkAllocateDescriptorSets;
                private static java.lang.invoke.MethodHandle vkCreateCommandPool;
                private static java.lang.invoke.MethodHandle vkAllocateCommandBuffers;
                private static java.lang.invoke.MethodHandle vkCreateFence;
                private static java.lang.invoke.MethodHandle vkCreateBuffer;
                private static java.lang.invoke.MethodHandle vkGetBufferMemoryRequirements;
                private static java.lang.invoke.MethodHandle vkGetPhysicalDeviceMemoryProperties;
                private static java.lang.invoke.MethodHandle vkAllocateMemory;
                private static java.lang.invoke.MethodHandle vkBindBufferMemory;
                private static java.lang.invoke.MethodHandle vkMapMemory;
                private static java.lang.invoke.MethodHandle vkUnmapMemory;
                private static java.lang.invoke.MethodHandle vkDestroyBuffer;
                private static java.lang.invoke.MethodHandle vkFreeMemory;
                private static java.lang.invoke.MethodHandle vkUpdateDescriptorSets;
                private static java.lang.invoke.MethodHandle vkBeginCommandBuffer;
                private static java.lang.invoke.MethodHandle vkCmdBindPipeline;
                private static java.lang.invoke.MethodHandle vkCmdBindDescriptorSets;
                private static java.lang.invoke.MethodHandle vkCmdPushConstants;
                private static java.lang.invoke.MethodHandle vkCmdDispatch;
                private static java.lang.invoke.MethodHandle vkEndCommandBuffer;
                private static java.lang.invoke.MethodHandle vkQueueSubmit2;
                private static java.lang.invoke.MethodHandle vkWaitForFences;
                private static java.lang.invoke.MethodHandle vkResetFences;

                private static java.lang.foreign.MemorySegment VK_INST;
                private static java.lang.foreign.MemorySegment VK_PHYS;
                private static java.lang.foreign.MemorySegment VK_DEV;
                private static java.lang.foreign.MemorySegment VK_QUEUE;
                private static java.lang.foreign.MemorySegment VK_CMD;
                private static java.lang.foreign.MemorySegment VK_FENCE;
                private static java.lang.foreign.MemorySegment VK_PL3;
                private static java.lang.foreign.MemorySegment VK_PL5;
                private static java.lang.foreign.MemorySegment VK_PL12;
                // DESCRIPTOR SET LAYOUTS (o vkMakeSet precisa do DSL —
                // passar o PL era o bug y=0: o set alocado com um
                // pipeline layout como pSetLayouts e o RADV (sem layer)
                // nao valida)
                private static java.lang.foreign.MemorySegment VK_DSL3;
                private static java.lang.foreign.MemorySegment VK_DSL5;
                private static java.lang.foreign.MemorySegment VK_DSL12;
                private static java.lang.foreign.MemorySegment VK_PIPE64;
                private static java.lang.foreign.MemorySegment VK_PIPE32;
                private static java.lang.foreign.MemorySegment VK_PIPESPL;
                private static java.lang.foreign.MemorySegment VK_PIPEMM;
                private static java.lang.foreign.MemorySegment VK_PIPEMM64;
                private static java.lang.foreign.MemorySegment VK_DSET3;
                private static java.lang.foreign.MemorySegment VK_DSET5;
                private static java.lang.foreign.MemorySegment VK_DSET12;
                private static java.lang.foreign.Arena VK_ARENA;

                // slots mutáveis: buf[0], mem[1], map[2] + cap
                private static final java.lang.foreign.MemorySegment[] S_X = new java.lang.foreign.MemorySegment[3];
                private static final java.lang.foreign.MemorySegment[] S_Y = new java.lang.foreign.MemorySegment[3];
                private static final java.lang.foreign.MemorySegment[] S_W = new java.lang.foreign.MemorySegment[3];
                private static final java.lang.foreign.MemorySegment[] S_X32 = new java.lang.foreign.MemorySegment[3];
                private static final java.lang.foreign.MemorySegment[] S_Y32 = new java.lang.foreign.MemorySegment[3];
                private static final java.lang.foreign.MemorySegment[] S_XH = new java.lang.foreign.MemorySegment[3];
                private static final java.lang.foreign.MemorySegment[] S_XL = new java.lang.foreign.MemorySegment[3];
                private static final java.lang.foreign.MemorySegment[] S_A = new java.lang.foreign.MemorySegment[3];
                private static final java.lang.foreign.MemorySegment[] S_B = new java.lang.foreign.MemorySegment[3];
                private static final java.lang.foreign.MemorySegment[] S_C = new java.lang.foreign.MemorySegment[3];
                private static final java.lang.foreign.MemorySegment[] S_A64 = new java.lang.foreign.MemorySegment[3];
                private static final java.lang.foreign.MemorySegment[] S_B64 = new java.lang.foreign.MemorySegment[3];
                private static final java.lang.foreign.MemorySegment[] S_C64 = new java.lang.foreign.MemorySegment[3];
                private static final long[] C_X = new long[1];
                private static final long[] C_Y = new long[1];
                private static final long[] C_W = new long[1];
                private static final long[] C_X32 = new long[1];
                private static final long[] C_Y32 = new long[1];
                private static final long[] C_XH = new long[1];
                private static final long[] C_XL = new long[1];
                private static final long[] C_A = new long[1];
                private static final long[] C_B = new long[1];
                private static final long[] C_C = new long[1];
                private static final long[] C_A64 = new long[1];
                private static final long[] C_B64 = new long[1];
                private static final long[] C_C64 = new long[1];

                // W residente por id: [id][0]=buf [id][1]=mem [id][2]=map
                private static final int VK_WMAX = 192;
                private static final java.lang.foreign.MemorySegment[][] W64 = new java.lang.foreign.MemorySegment[VK_WMAX][3];
                private static final java.lang.foreign.MemorySegment[][] W32 = new java.lang.foreign.MemorySegment[VK_WMAX][3];
                private static final java.lang.foreign.MemorySegment[][] WH = new java.lang.foreign.MemorySegment[VK_WMAX][3];
                private static final java.lang.foreign.MemorySegment[][] WL = new java.lang.foreign.MemorySegment[VK_WMAX][3];
                private static final long[][] W64CAP = new long[VK_WMAX][1];
                private static final long[][] W32CAP = new long[VK_WMAX][1];
                private static final long[][] WHCAP = new long[VK_WMAX][1];
                private static final long[][] WLCAP = new long[VK_WMAX][1];

                private static int VK_CURM;
                private static int VK_CURK;

                private static java.lang.foreign.MemorySegment vkAlloc(long bytes) {
                    // ZERAR: a arena ofAuto reutiliza memoria com lixo —
                    // structs Vulkan com flags/pNext/pad sujos falham em
                    // silencio (bug y=0: o pool do vkMakeSet era criado
                    // com flags aleatorios e o set ficava inutilizavel).
                    var seg = VK_ARENA.allocate(bytes);
                    seg.fill((byte) 0);
                    return seg;
                }

                private static void putI(java.lang.foreign.MemorySegment s, long off, int v) {
                    s.set(java.lang.foreign.ValueLayout.JAVA_INT, off, v);
                }

                private static void putL(java.lang.foreign.MemorySegment s, long off, long v) {
                    s.set(java.lang.foreign.ValueLayout.JAVA_LONG, off, v);
                }

                private static void putP(java.lang.foreign.MemorySegment s, long off,
                        java.lang.foreign.MemorySegment p) {
                    s.set(java.lang.foreign.ValueLayout.ADDRESS, off, p);
                }

                private static int getI(java.lang.foreign.MemorySegment s, long off) {
                    return s.get(java.lang.foreign.ValueLayout.JAVA_INT, off);
                }

                private static long getL(java.lang.foreign.MemorySegment s, long off) {
                    return s.get(java.lang.foreign.ValueLayout.JAVA_LONG, off);
                }

                private static java.lang.foreign.MemorySegment getP(java.lang.foreign.MemorySegment s, long off) {
                    return s.get(java.lang.foreign.ValueLayout.ADDRESS, off);
                }

                private static java.lang.foreign.MemorySegment vkNull() {
                    return java.lang.foreign.MemorySegment.NULL;
                }

                private static java.lang.foreign.MemorySegment vkOut() {
                    return vkAlloc(8);
                }

                private static java.lang.foreign.MemorySegment vkRes(java.lang.foreign.MemorySegment out) {
                    return out.get(java.lang.foreign.ValueLayout.ADDRESS, 0);
                }

                private static int vk(int rc, String what) throws Throwable {
                    if (rc != 0) {
                        VK_ERR = what + " (rc=" + (-rc) + ")";
                        throw new RuntimeException(VK_ERR);
                    }
                    return rc;
                }

                private static java.lang.invoke.MethodHandle vkFn(
                        java.lang.foreign.SymbolLookup lib, String name,
                        java.lang.foreign.FunctionDescriptor desc) throws Throwable {
                    var linker = java.lang.foreign.Linker.nativeLinker();
                    return linker.downcallHandle(
                            lib.find(name).orElseThrow(
                                    () -> new RuntimeException("simbolo ausente: " + name)),
                            desc);
                }

                public static boolean kof_vk_available() {
                    if (!VK_INITED) {
                        try {
                            VK_OK = vkInitAll();
                        } catch (Throwable t) {
                            VK_OK = false;
                            VK_ERR = t.getClass().getSimpleName() + ": " + t.getMessage();
                        }
                        VK_INITED = true;
                    }
                    return VK_OK;
                }

                public static String kof_vk_fail_reason() {
                    return VK_ERR;
                }

                private static boolean vkInitAll() {
                    if (VK_DEV != null) return true;
                    try {
                        VK_ARENA = java.lang.foreign.Arena.ofAuto();
                        var lib = java.lang.foreign.SymbolLookup.libraryLookup(
                                "libvulkan.so.1", VK_ARENA);
                        var I = java.lang.foreign.ValueLayout.JAVA_INT;
                        var L = java.lang.foreign.ValueLayout.JAVA_LONG;
                        var P = java.lang.foreign.ValueLayout.ADDRESS;
                        // bootstrap (instance/phys/dev/queue) via libvkboot:
                        // o vkCreateDevice via downcall FFM direto produz um
                        // device cujos SSBOs ficam invisiveis ao GPU de forma
                        // nao-deterministica (RADV/LVP, JDK 25.0.3; probes
                        // 18-23) — em C funciona e todo o resto e FFM.
                        var bootLib = java.util.stream.Stream.of(
                                "libvkboot.so", "./libvkboot.so")
                                .map(n2 -> {
                                    try {
                                        return java.lang.foreign.SymbolLookup.libraryLookup(
                                                n2, VK_ARENA);
                                    } catch (Throwable e) {
                                        return null;
                                    }
                                })
                                .filter(s2 -> s2 != null)
                                .findFirst()
                                .orElseThrow(() -> new RuntimeException(
                                        "libvkboot.so nao encontrada (instale em /usr/local/lib ou ./)"));
                        vkBoot = vkFn(bootLib, "vkboot",
                                java.lang.foreign.FunctionDescriptor.of(I, P));
                        vkCreateShaderModule = vkFn(lib, "vkCreateShaderModule", java.lang.foreign.FunctionDescriptor.of(I, P, P, P, P));
                        vkCreateDescriptorSetLayout = vkFn(lib, "vkCreateDescriptorSetLayout",
                                java.lang.foreign.FunctionDescriptor.of(I, P, P, P, P));
                        vkCreatePipelineLayout = vkFn(lib, "vkCreatePipelineLayout",
                                java.lang.foreign.FunctionDescriptor.of(I, P, P, P, P));
                        vkCreateComputePipelines = vkFn(lib, "vkCreateComputePipelines",
                                java.lang.foreign.FunctionDescriptor.of(I, P, P, I, P, P, P));
                        vkCreateDescriptorPool = vkFn(lib, "vkCreateDescriptorPool",
                                java.lang.foreign.FunctionDescriptor.of(I, P, P, P, P));
                        vkAllocateDescriptorSets = vkFn(lib, "vkAllocateDescriptorSets",
                                java.lang.foreign.FunctionDescriptor.of(I, P, P, P));
                        vkCreateCommandPool = vkFn(lib, "vkCreateCommandPool", java.lang.foreign.FunctionDescriptor.of(I, P, P, P, P));
                        vkAllocateCommandBuffers = vkFn(lib, "vkAllocateCommandBuffers",
                                java.lang.foreign.FunctionDescriptor.of(I, P, P, P));
                        vkCreateFence = vkFn(lib, "vkCreateFence", java.lang.foreign.FunctionDescriptor.of(I, P, P, P, P));
                        vkCreateBuffer = vkFn(lib, "vkCreateBuffer", java.lang.foreign.FunctionDescriptor.of(I, P, P, P, P));
                        vkGetBufferMemoryRequirements = vkFn(lib,
                                "vkGetBufferMemoryRequirements", java.lang.foreign.FunctionDescriptor.ofVoid(P, P, P));
                        vkGetPhysicalDeviceMemoryProperties = vkFn(lib,
                                "vkGetPhysicalDeviceMemoryProperties", java.lang.foreign.FunctionDescriptor.ofVoid(P, P));
                        vkAllocateMemory = vkFn(lib, "vkAllocateMemory", java.lang.foreign.FunctionDescriptor.of(I, P, P, P, P));
                        // (device, buffer, memory, offset, pAllocator)
                        vkBindBufferMemory = vkFn(lib, "vkBindBufferMemory", java.lang.foreign.FunctionDescriptor.of(I, P, P, P, L, P));
                        // (device, memory, offset, size, flags, ppData)
                        vkMapMemory = vkFn(lib, "vkMapMemory", java.lang.foreign.FunctionDescriptor.of(I, P, P, L, L, I, P));
                        vkUnmapMemory = vkFn(lib, "vkUnmapMemory", java.lang.foreign.FunctionDescriptor.ofVoid(P, P));
                        vkDestroyBuffer = vkFn(lib, "vkDestroyBuffer", java.lang.foreign.FunctionDescriptor.ofVoid(P, P, P));
                        vkFreeMemory = vkFn(lib, "vkFreeMemory", java.lang.foreign.FunctionDescriptor.ofVoid(P, P, P));
                        vkUpdateDescriptorSets = vkFn(lib, "vkUpdateDescriptorSets",
                                java.lang.foreign.FunctionDescriptor.ofVoid(P, I, P, I, P));
                        vkBeginCommandBuffer = vkFn(lib, "vkBeginCommandBuffer", java.lang.foreign.FunctionDescriptor.of(I, P, P));
                        vkCmdBindPipeline = vkFn(lib, "vkCmdBindPipeline", java.lang.foreign.FunctionDescriptor.ofVoid(P, I, P));
                        vkCmdBindDescriptorSets = vkFn(lib, "vkCmdBindDescriptorSets",
                                java.lang.foreign.FunctionDescriptor.ofVoid(P, I, P, I, I, P, I, P));
                        vkCmdPushConstants = vkFn(lib, "vkCmdPushConstants",
                                java.lang.foreign.FunctionDescriptor.ofVoid(P, P, I, I, I, P));
                        vkCmdDispatch = vkFn(lib, "vkCmdDispatch", java.lang.foreign.FunctionDescriptor.ofVoid(P, I, I, I));
                        vkEndCommandBuffer = vkFn(lib, "vkEndCommandBuffer", java.lang.foreign.FunctionDescriptor.of(I, P));
                        vkQueueSubmit2 = vkFn(lib, "vkQueueSubmit2", java.lang.foreign.FunctionDescriptor.of(I, P, I, P, P));
                        // (device, fenceCount, pFences, waitAll, timeout)
                        vkWaitForFences = vkFn(lib, "vkWaitForFences", java.lang.foreign.FunctionDescriptor.of(I, P, I, P, I, L));
                        vkResetFences = vkFn(lib, "vkResetFences", java.lang.foreign.FunctionDescriptor.of(I, P, I, P));
                        vkInitChain();
                        return true;
                    } catch (Throwable t) {
                        VK_ERR = t.getClass().getSimpleName() + ": " + t.getMessage();
                        return false;
                    }
                }

                private static String envSpvErr;

                private static String envSpv(String env, String fallback) {
                    String v = System.getenv(env);
                    if (v != null && !v.isBlank()) {
                        if (java.nio.file.Files.isRegularFile(java.nio.file.Path.of(v))) {
                            return v;
                        }
                        envSpvErr = env + " aponta p/ arquivo ausente: " + v;
                        return null;
                    }
                    if (java.nio.file.Files.isRegularFile(java.nio.file.Path.of(fallback))) {
                        return fallback;
                    }
                    envSpvErr = "sem " + env + " e sem " + fallback;
                    return null;
                }

                private static String envSpvOpt(String env, String fallback) {
                    String v = System.getenv(env);
                    if (v != null && !v.isBlank()
                            && java.nio.file.Files.isRegularFile(java.nio.file.Path.of(v))) {
                        return v;
                    }
                    return java.nio.file.Files.isRegularFile(java.nio.file.Path.of(fallback))
                            ? fallback : null;
                }

                private static void vkInitChain() throws Throwable {
                    // bootstrap C: {instance, phys, device, queue, qfam}
                    var bootOut = vkAlloc(40);
                    putL(bootOut, 0, 0);
                    int brc = (int) vkBoot.invoke(bootOut);
                    if (brc != 0) {
                        VK_ERR = "vkboot falhou";
                        throw new RuntimeException(VK_ERR);
                    }
                    VK_INST = getP(bootOut, 0);
                    VK_PHYS = getP(bootOut, 8);
                    VK_DEV = getP(bootOut, 16);
                    VK_QUEUE = getP(bootOut, 24);
                    int qfam = getI(bootOut, 32);

                    // layouts + pipelines (SPVs default gpu/shaders/*.spv ou env)
                    VK_PL3 = vkMakeLayout(3);
                    VK_PL5 = vkMakeLayout(5);
                    VK_PL12 = vkMakeLayoutPush(3, 12);   // matmul: push {m,n,k} 12B
                    String spv64 = envSpv("KOF_GPU_SPV64", "gpu/shaders/matvec64.spv");
                    VK_PIPE64 = (spv64 != null) ? vkMakePipe(spv64, VK_PL3, 24) : null;
                    if (VK_PIPE64 == null) {
                        VK_ERR = "SPIR-V matvec64: " + envSpvErr;
                        return;
                    }
                    VK_DSET3 = vkMakeSet(3, VK_DSL3);
                    String spvS = envSpvOpt("KOF_GPU_SPV64_SPLIT", "gpu/shaders/matvecsplit.spv");
                    if (spvS != null) {
                        VK_PIPESPL = vkMakePipe(spvS, VK_PL5, 24);
                        if (VK_PIPESPL != null) {
                            VK_DSET5 = vkMakeSet(5, VK_DSL5);
                        }
                    }
                    String spv32 = envSpvOpt("KOF_GPU_SPV64_W32", "gpu/shaders/matvecw32.spv");
                    if (spv32 != null) {
                        VK_PIPE32 = vkMakePipe(spv32, VK_PL3, 24);
                    }
                    String spvMM = envSpvOpt("KOF_GPU_SPV", "gpu/shaders/matmul.spv");
                    if (spvMM != null) {
                        VK_PIPEMM = vkMakePipe(spvMM, VK_PL12, 12);
                        if (VK_PIPEMM != null) {
                            VK_DSET12 = vkMakeSet(3, VK_DSL12);
                        }
                    }
                    // matmul64: KOF_GPU_SPV64_MM, senão o próprio SPV64
                    String spvMM64 = envSpvOpt("KOF_GPU_SPV64_MM", spv64);
                    if (spvMM64 != null) {
                        VK_PIPEMM64 = vkMakePipe(spvMM64, VK_PL12, 12);
                    }

                    // VkCommandPoolCreateInfo (24B): queueFamilyIndex@20
    """;
    }
}
