package dev.kof.compiler;

/**
 * Fragmento do source do KofRuntime gerado (REFACTOR-500 Fase 8).
 * kof.vulkan build: layouts/pipelines/desc-sets/host-buffers - parte 2/3 de JvmVkRuntime. Concatenacao preserva byte-a-byte.
 */
final class JvmVkBuildRuntime {

    private JvmVkBuildRuntime() {}

    static String source() {
        return """
                    var cpi = vkAlloc(24);
                    putI(cpi, 0, 39);
                    putI(cpi, 20, qfam);
                    var cpOut = vkOut();
                    vk((int) vkCreateCommandPool.invoke(VK_DEV, cpi, vkNull(), cpOut), "cmd pool");
                    var cpool = vkRes(cpOut);

                    // VkCommandBufferAllocateInfo (32B): commandPool@16,
                    // level@24 = PRIMARY(0), commandBufferCount@28
                    var cbai = vkAlloc(32);
                    putI(cbai, 0, 40);
                    putP(cbai, 16, cpool);
                    putI(cbai, 24, 0);
                    putI(cbai, 28, 1);
                    var cbOut = vkOut();
                    vk((int) vkAllocateCommandBuffers.invoke(VK_DEV, cbai, cbOut), "cmd buf");
                    VK_CMD = vkRes(cbOut);

                    // VkFenceCreateInfo (24B)
                    var fci = vkAlloc(24);
                    putI(fci, 0, 8);
                    var fOut = vkOut();
                    vk((int) vkCreateFence.invoke(VK_DEV, fci, vkNull(), fOut), "fence");
                    VK_FENCE = vkRes(fOut);
                    VK_ERR = "ok";
                }


                // VkDescriptorSetLayoutBinding (24B): binding@0,
                // descriptorType@4 = STORAGE_BUFFER(7), descriptorCount@8,
                // stageFlags@12 = COMPUTE(0x20), pImmutableSamplers@16
                private static java.lang.foreign.MemorySegment vkMakeLayout(int nbinds)
                        throws Throwable {
                    return vkMakeLayoutPush(nbinds, 24);
                }

                // VkPipelineLayoutCreateInfo (48B): flags@16, setLayoutCount@20,
                // pSetLayouts@24, pushConstantRangeCount@32, pPushConstantRanges@40
                private static java.lang.foreign.MemorySegment vkMakeLayoutPush(int nbinds,
                        int pushSize) throws Throwable {
                    var binds = vkAlloc(24L * nbinds);
                    for (int i = 0; i < nbinds; i++) {
                        long o = (long) i * 24;
                        putI(binds, o, i);
                        putI(binds, o + 4, 7);
                        putI(binds, o + 8, 1);
                        putI(binds, o + 12, 0x20);
                    }
                    var li = vkAlloc(32);
                    putI(li, 0, 32);
                    putI(li, 20, nbinds);
                    putP(li, 24, binds);
                    var lo = vkOut();
                    vk((int) vkCreateDescriptorSetLayout.invoke(VK_DEV, li, vkNull(), lo),
                            "desc layout");
                    var dsl = vkRes(lo);
                    // guardar o DSL p/ o vkMakeSet (o set tem de ser
                    // alocado com o DESCRIPTOR SET LAYOUT, nunca com o PL)
                    if (nbinds == 3 && pushSize == 24) VK_DSL3 = dsl;
                    if (nbinds == 5 && pushSize == 24) VK_DSL5 = dsl;
                    if (nbinds == 3 && pushSize == 12) VK_DSL12 = dsl;

                    // VkPushConstantRange (12B): stageFlags@0, offset@4, size@8
                    var pcr = vkAlloc(12);
                    putI(pcr, 0, 0x20);         // SHADER_STAGE_COMPUTE
                    putI(pcr, 4, 0);
                    putI(pcr, 8, pushSize);
                    // pSetLayouts é pointer P/ ARRAY de layouts (o handle NÃO
                    // vai embutido no campo — lição do debug RADV/LVP)
                    var setArr = vkAlloc(8);
                    putP(setArr, 0, dsl);
                    var pli = vkAlloc(48);
                    putI(pli, 0, 30);
                    putI(pli, 20, 1);
                    putP(pli, 24, setArr);
                    putI(pli, 32, 1);
                    putP(pli, 40, pcr);
                    var plo = vkOut();
                    vk((int) vkCreatePipelineLayout.invoke(VK_DEV, pli, vkNull(), plo),
                            "pipeline layout");
                    return vkRes(plo);
                }

                // VkPipelineShaderStageCreateInfo (48B): flags@16,
                // stage@20 = COMPUTE(5), module@24, pName@32 — INLINE no
                // ComputePipelineInfo @24 (lição do debug: sem array separado).
                private static java.lang.foreign.MemorySegment vkMakePipe(String spvPath,
                        java.lang.foreign.MemorySegment layout, int pushSize) throws Throwable {
                    byte[] spv = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(spvPath));
                    // VkShaderModuleCreateInfo (40B): flags@16,
                    // codeSize@24 (size_t), pCode@32
                    var smci = vkAlloc(40);
                    putI(smci, 0, 16);
                    putL(smci, 24, spv.length);
                    var code = vkAlloc(spv.length);
                    java.lang.foreign.MemorySegment.copy(
                            java.lang.foreign.MemorySegment.ofArray(spv), 0, code, 0, spv.length);
                    putP(smci, 32, code);
                    var smOut = vkOut();
                    vk((int) vkCreateShaderModule.invoke(VK_DEV, smci, vkNull(), smOut),
                            "shader module");
                    var sm = vkRes(smOut);

                    // O PIPE DEVE USAR O LAYOUT PASSADO — criar um
                    // segundo PipelineLayout aqui deixava o cmd binding
                    // com VK_PL* DIFERENTE do layout do pipe =
                    // comportamento indefinido (o RADV nao valida e o
                    // GPU lia os SSBOs como zerados: bug y=0).
                    var stage = vkAlloc(48);
                    putI(stage, 0, 18);         // PIPELINE_SHADER_STAGE_CREATE_INFO
                    putI(stage, 20, 0x20);      // VK_SHADER_STAGE_COMPUTE_BIT
                    putP(stage, 24, sm);
                    putP(stage, 32, vkCstr("main"));
                    var cpci = vkAlloc(96);
                    putI(cpci, 0, 29);
                    java.lang.foreign.MemorySegment.copy(stage, 0, cpci, 24, 48);
                    putP(cpci, 72, layout);
                    var po = vkOut();
                    // (device, cache, createInfoCount, pCreateInfos, pAlloc, pOut)
                    vk((int) vkCreateComputePipelines.invoke(VK_DEV, vkNull(), 1, cpci,
                            vkNull(), po), "pipeline");
                    return vkRes(po);
                }

                private static java.lang.foreign.MemorySegment vkCstr(String s) {
                    // NUL real (\\0 num text block viraria barra+zero)
                    byte[] b = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    var seg = VK_ARENA.allocate(b.length + 1);
                    java.lang.foreign.MemorySegment.copy(
                            java.lang.foreign.MemorySegment.ofArray(b), 0, seg, 0, b.length);
                    seg.set(java.lang.foreign.ValueLayout.JAVA_BYTE, b.length, (byte) 0);
                    return seg;
                }

                // VkDescriptorPoolSize (8B): type@0 = STORAGE_BUFFER(7), count@4.
                // VkDescriptorPoolCreateInfo (40B): flags@16, maxSets@20,
                // poolSizeCount@24, pPoolSizes@32. VkDescriptorSetAllocateInfo
                // (40B): descriptorPool@16, descriptorSetCount@24, pSetLayouts@32.
                private static java.lang.foreign.MemorySegment vkMakeSet(int nbinds,
                        java.lang.foreign.MemorySegment layout) throws Throwable {
                    var psize = vkAlloc(8);
                    putI(psize, 0, 7);
                    putI(psize, 4, nbinds);
                    var dpi = vkAlloc(40);
                    putI(dpi, 0, 33);
                    putI(dpi, 20, nbinds);
                    putI(dpi, 24, 1);
                    putP(dpi, 32, psize);
                    var dpo = vkOut();
                    vk((int) vkCreateDescriptorPool.invoke(VK_DEV, dpi, vkNull(), dpo),
                            "desc pool");
                    var pool = vkRes(dpo);

                    var dsai = vkAlloc(40);
                    putI(dsai, 0, 34);
                    putP(dsai, 16, pool);
                    putI(dsai, 24, 1);
                    // pSetLayouts é pointer p/ array de layouts
                    var layArr = vkAlloc(8);
                    putP(layArr, 0, layout);
                    putP(dsai, 32, layArr);
                    var dso = vkAlloc(8);
                    vk((int) vkAllocateDescriptorSets.invoke(VK_DEV, dsai, dso), "desc set");
                    return getP(dso, 0);
                }

                // buffer host-visible|coherent: create → requirements → mem type
                // → alloc → bind → map. slot = {buf, mem, map}
                private static void vkHostBuffer(long bytes, java.lang.foreign.MemorySegment[] slot)
                        throws Throwable {
                    // VkBufferCreateInfo (56B): flags@16, size@24 (DeviceSize),
                    // usage@32 = STORAGE_BUFFER(0x80), sharingMode@36 = EXCLUSIVE(0)
                    var bci = vkAlloc(56);
                    putI(bci, 0, 12);
                    putL(bci, 24, bytes);
                    putI(bci, 32, 0x80);
                    putI(bci, 36, 0);
                    var bo = vkOut();
                    vk((int) vkCreateBuffer.invoke(VK_DEV, bci, vkNull(), bo), "buffer");
                    slot[0] = vkRes(bo);

                    var mr = vkAlloc(24);       // VkMemoryRequirements: size@0,
                                                // memoryTypeBits@16
                    vkGetBufferMemoryRequirements.invoke(VK_DEV, slot[0], mr);
                    long size = getL(mr, 0);
                    int memTypeBits = getI(mr, 16);

                    var pdmp = vkAlloc(520);    // VkPhysicalDeviceMemoryProperties:
                                                // memoryTypeCount@0, types@4 (32×8B:
                                                // propertyFlags@0+o, heapIndex@4+o)
                    vkGetPhysicalDeviceMemoryProperties.invoke(VK_PHYS, pdmp);
                    int nTypes = Math.min(getI(pdmp, 0), 32);
                    int memIdx = -1;
                    for (int t = 0; t < nTypes; t++) {
                        long o = 4L + (long) t * 8;
                        int props = getI(pdmp, o);
                        if ((memTypeBits & (1 << t)) != 0
                                && (props & 0x6) == 0x6) {  // HOST_VISIBLE|COHERENT
                            memIdx = t;
                            break;
                        }
                    }
                    if (memIdx < 0) {
                        VK_ERR = "sem mem host-visible";
                        throw new RuntimeException(VK_ERR);
                    }

                    var mai = vkAlloc(32);
                    putI(mai, 0, 5);
                    putL(mai, 16, size);
                    putI(mai, 24, memIdx);
                    var mo = vkOut();
                    vk((int) vkAllocateMemory.invoke(VK_DEV, mai, vkNull(), mo), "alloc mem");
                    slot[1] = vkRes(mo);
                    vk((int) vkBindBufferMemory.invoke(VK_DEV, slot[0], slot[1], 0L, vkNull()),
                            "bind mem");
                    var mpOut = vkOut();
                    vk((int) vkMapMemory.invoke(VK_DEV, slot[1], 0L, size, 0, mpOut), "map");
                    // o mapped sai zero-length do downcall — reinterpret com
                    // o size real (a memória é nossa até o unmap)
                    slot[2] = vkRes(mpOut).reinterpret(size);
                }

                private static void vkDrop(java.lang.foreign.MemorySegment[] slot) throws Throwable {
                    if (slot[1] != null) {
                        vkUnmapMemory.invoke(VK_DEV, slot[1]);
                        vkDestroyBuffer.invoke(VK_DEV, slot[0], vkNull());
                        vkFreeMemory.invoke(VK_DEV, slot[1], vkNull());
                        slot[0] = null;
                        slot[1] = null;
                        slot[2] = null;
                    }
                }

    """;
    }
}
