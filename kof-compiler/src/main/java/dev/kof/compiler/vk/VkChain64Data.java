package dev.kof.compiler.vk;

/** Fragmentos do .s x86-64 de Vulkan (vkchain64) — extraidos de VkChain64Asm (REFACTOR-500). O concatenador final e VkChain64Asm.source(); a ordem de chamada preserva o asm byte-a-byte. */
public final class VkChain64Data {
    private VkChain64Data() {}

    // ───────────────────────── .data + .bss ─────────────────────────
    static void source(StringBuilder sb) {
        sb.append("""
            // ── VkChain64Asm: estado global (tradução do vkchain64.c) ──
            .section .rodata
            .Lvkv_libname:  .asciz "libvulkan.so.1"
            .Lvkv_sov64:    .asciz "KOF_GPU_SPV64"
            .Lvkv_sovw32:   .asciz "KOF_GPU_SPV64_W32"
            .Lvkv_sovsplit: .asciz "KOF_GPU_SPV64_SPLIT"
            .Lvkv_sovmm:    .asciz "KOF_GPU_SPV64_MM"
            .Lvkv_trace:    .asciz "KOF_MV64_TRACE"
            .Lvkv_appname:  .asciz "kof"
            .Lvkv_main:     .asciz "main"
            .Lvkv_errfmt:   .asciz "%s (rc=%d)"
            .Lvkv_rb:       .asciz "rb"
            .Lvkv_e_instance: .asciz "instance"
            .Lvkv_e_enum0:    .asciz "enum0"
            .Lvkv_e_nodev:    .asciz "nenhum physical device"
            .Lvkv_e_enum:     .asciz "enum"
            .Lvkv_e_noqueue:  .asciz "sem compute queue"
            .Lvkv_e_device:   .asciz "device"
            .Lvkv_e_spvopen:  .asciz "spv nao abriu"
            .Lvkv_e_spvread:  .asciz "spv leitura falhou"
            .Lvkv_e_shader:   .asciz "shader module"
            .Lvkv_e_nbinds:   .asciz "nbinds?"
            .Lvkv_e_dsll:     .asciz "desc layout"
            .Lvkv_e_pll:      .asciz "pipe layout"
            .Lvkv_e_pipe:     .asciz "pipeline"
            .Lvkv_e_dpool:    .asciz "desc pool"
            .Lvkv_e_dset:     .asciz "desc set"
            .Lvkv_e_cpool:    .asciz "cmd pool"
            .Lvkv_e_cmdbuf:   .asciz "cmd buf"
            .Lvkv_e_fence:    .asciz "fence"
            .Lvkv_e_mem:      .asciz "sem mem host-visible"
            .Lvkv_e_allocmem: .asciz "alloc mem"
            .Lvkv_e_bindmem:  .asciz "bind mem"
            .Lvkv_e_map:      .asciz "map"
            .Lvkv_e_buffer:   .asciz "buffer"
            .Lvkv_e_begin:    .asciz "begin"
            .Lvkv_e_end:      .asciz "end"
            .Lvkv_e_submit:   .asciz "submit"
            .Lvkv_e_wait:     .asciz "wait"
            .Lvkv_ok:       .asciz "ok"
            .Lvkv_notinit:  .asciz "not initialized"
            // nomes da API vk* (dlsym)
            .Lvkv_vkCreateInstance: .asciz "vkCreateInstance"
            .Lvkv_vkEnumeratePhysicalDevices: .asciz "vkEnumeratePhysicalDevices"
            .Lvkv_vkGetPhysicalDeviceQueueFamilyProperties: .asciz "vkGetPhysicalDeviceQueueFamilyProperties"
            .Lvkv_vkGetPhysicalDeviceMemoryProperties: .asciz "vkGetPhysicalDeviceMemoryProperties"
            .Lvkv_vkCreateDevice: .asciz "vkCreateDevice"
            .Lvkv_vkGetDeviceQueue: .asciz "vkGetDeviceQueue"
            .Lvkv_vkCreateShaderModule: .asciz "vkCreateShaderModule"
            .Lvkv_vkCreateDescriptorSetLayout: .asciz "vkCreateDescriptorSetLayout"
            .Lvkv_vkCreatePipelineLayout: .asciz "vkCreatePipelineLayout"
            .Lvkv_vkCreateComputePipelines: .asciz "vkCreateComputePipelines"
            .Lvkv_vkCreateDescriptorPool: .asciz "vkCreateDescriptorPool"
            .Lvkv_vkAllocateDescriptorSets: .asciz "vkAllocateDescriptorSets"
            .Lvkv_vkCreateCommandPool: .asciz "vkCreateCommandPool"
            .Lvkv_vkAllocateCommandBuffers: .asciz "vkAllocateCommandBuffers"
            .Lvkv_vkCreateFence: .asciz "vkCreateFence"
            .Lvkv_vkCreateBuffer: .asciz "vkCreateBuffer"
            .Lvkv_vkGetBufferMemoryRequirements: .asciz "vkGetBufferMemoryRequirements"
            .Lvkv_vkAllocateMemory: .asciz "vkAllocateMemory"
            .Lvkv_vkBindBufferMemory: .asciz "vkBindBufferMemory"
            .Lvkv_vkMapMemory: .asciz "vkMapMemory"
            .Lvkv_vkUnmapMemory: .asciz "vkUnmapMemory"
            .Lvkv_vkDestroyBuffer: .asciz "vkDestroyBuffer"
            .Lvkv_vkFreeMemory: .asciz "vkFreeMemory"
            .Lvkv_vkUpdateDescriptorSets: .asciz "vkUpdateDescriptorSets"
            .Lvkv_vkBeginCommandBuffer: .asciz "vkBeginCommandBuffer"
            .Lvkv_vkCmdBindPipeline: .asciz "vkCmdBindPipeline"
            .Lvkv_vkCmdBindDescriptorSets: .asciz "vkCmdBindDescriptorSets"
            .Lvkv_vkCmdPushConstants: .asciz "vkCmdPushConstants"
            .Lvkv_vkCmdDispatch: .asciz "vkCmdDispatch"
            .Lvkv_vkEndCommandBuffer: .asciz "vkEndCommandBuffer"
            .Lvkv_vkQueueSubmit: .asciz "vkQueueSubmit"
            .Lvkv_vkWaitForFences: .asciz "vkWaitForFences"
            .Lvkv_vkResetFences: .asciz "vkResetFences"

            .section .bss
            .align 64
            // handles da cadeia (0 = vazio)
            .lcomm vk64_inst, 8
            .lcomm vk64_phys, 8
            .lcomm vk64_dev, 8
            .lcomm vk64_queue, 8
            .lcomm vk64_qfam, 4
            .lcomm vk64_inited, 4
            // pipelines + layouts (3 SSBOs / w32 usa o mesmo / split 5)
            .lcomm vk64_pipe, 8
            .lcomm vk64_pl, 8
            .lcomm vk64_dsl, 8
            .lcomm vk64_dpool, 8
            .lcomm vk64_dset, 8
            .lcomm vk64_pipe32, 8
            .lcomm vk64_pipeSplit, 8
            .lcomm vk64_pl5, 8
            .lcomm vk64_dsl5, 8
            .lcomm vk64_dpool5, 8
            .lcomm vk64_dset5, 8
            // cmd + fence
            .lcomm vk64_cpool, 8
            .lcomm vk64_cmd, 8
            .lcomm vk64_fence, 8
            // buffers clássicos (W central + x + y)
            .lcomm vk64_wbuf, 8
            .lcomm vk64_wmem, 8
            .lcomm vk64_wmap, 8
            .lcomm vk64_wcap, 8
            .lcomm vk64_xbuf, 8
            .lcomm vk64_xmem, 8
            .lcomm vk64_xmap, 8
            .lcomm vk64_xcap, 8
            .lcomm vk64_ybuf, 8
            .lcomm vk64_ymem, 8
            .lcomm vk64_ymap, 8
            .lcomm vk64_ycap, 8
            .lcomm vk64_curM, 4
            .lcomm vk64_curK, 4
            // W residente i64 por id (WMAX=192)
            .lcomm vk64_wbufs, 1536
            .lcomm vk64_wmems, 1536
            .lcomm vk64_wmaps, 1536
            .lcomm vk64_wcaps, 1536
            // W residente i32 por id
            .lcomm vk64_wbufs32, 1536
            .lcomm vk64_wmems32, 1536
            .lcomm vk64_wmaps32, 1536
            .lcomm vk64_wcaps32, 1536
            .lcomm vk64_xbuf32, 8
            .lcomm vk64_xmem32, 8
            .lcomm vk64_xmap32, 8
            .lcomm vk64_xcap32, 8
            .lcomm vk64_ybuf32, 8
            .lcomm vk64_ymem32, 8
            .lcomm vk64_ymap32, 8
            .lcomm vk64_ycap32, 8
            // split: wh/wl residentes i32 por id
            .lcomm vk64_whbufs, 1536
            .lcomm vk64_whmems, 1536
            .lcomm vk64_whmaps, 1536
            .lcomm vk64_whcaps, 1536
            .lcomm vk64_wlbufs, 1536
            .lcomm vk64_wlmems, 1536
            .lcomm vk64_wlmaps, 1536
            .lcomm vk64_wlcaps, 1536
            .lcomm vk64_xhbuf, 8
            .lcomm vk64_xhmem, 8
            .lcomm vk64_xhmap, 8
            .lcomm vk64_xhcap, 8
            .lcomm vk64_xlbuf, 8
            .lcomm vk64_xlmem, 8
            .lcomm vk64_xlmap, 8
            .lcomm vk64_xlcap, 8
            // errbuf 256 + flags
            .lcomm vk64_errbuf, 256
            .lcomm vk64_lib, 8
            // dlsym slots (um ponteiro por vk*)
            .lcomm g_vk64_vkCreateInstance, 8
            .lcomm g_vk64_vkEnumeratePhysicalDevices, 8
            .lcomm g_vk64_vkGetPhysicalDeviceQueueFamilyProperties, 8
            .lcomm g_vk64_vkGetPhysicalDeviceMemoryProperties, 8
            .lcomm g_vk64_vkCreateDevice, 8
            .lcomm g_vk64_vkGetDeviceQueue, 8
            .lcomm g_vk64_vkCreateShaderModule, 8
            .lcomm g_vk64_vkCreateDescriptorSetLayout, 8
            .lcomm g_vk64_vkCreatePipelineLayout, 8
            .lcomm g_vk64_vkCreateComputePipelines, 8
            .lcomm g_vk64_vkCreateDescriptorPool, 8
            .lcomm g_vk64_vkAllocateDescriptorSets, 8
            .lcomm g_vk64_vkCreateCommandPool, 8
            .lcomm g_vk64_vkAllocateCommandBuffers, 8
            .lcomm g_vk64_vkCreateFence, 8
            .lcomm g_vk64_vkCreateBuffer, 8
            .lcomm g_vk64_vkGetBufferMemoryRequirements, 8
            .lcomm g_vk64_vkAllocateMemory, 8
            .lcomm g_vk64_vkBindBufferMemory, 8
            .lcomm g_vk64_vkMapMemory, 8
            .lcomm g_vk64_vkUnmapMemory, 8
            .lcomm g_vk64_vkDestroyBuffer, 8
            .lcomm g_vk64_vkFreeMemory, 8
            .lcomm g_vk64_vkUpdateDescriptorSets, 8
            .lcomm g_vk64_vkBeginCommandBuffer, 8
            .lcomm g_vk64_vkCmdBindPipeline, 8
            .lcomm g_vk64_vkCmdBindDescriptorSets, 8
            .lcomm g_vk64_vkCmdPushConstants, 8
            .lcomm g_vk64_vkCmdDispatch, 8
            .lcomm g_vk64_vkEndCommandBuffer, 8
            .lcomm g_vk64_vkQueueSubmit, 8
            .lcomm g_vk64_vkWaitForFences, 8
            .lcomm g_vk64_vkResetFences, 8
            // dispatch64 (matmul64): buffers a/b/c próprios + dset12
            .lcomm vk64_d64abuf, 8
            .lcomm vk64_d64amem, 8
            .lcomm vk64_d64amap, 8
            .lcomm vk64_d64acap, 8
            .lcomm vk64_d64bbuf, 8
            .lcomm vk64_d64bmem, 8
            .lcomm vk64_d64bmap, 8
            .lcomm vk64_d64bcap, 8
            .lcomm vk64_d64cbuf, 8
            .lcomm vk64_d64cmem, 8
            .lcomm vk64_d64cmap, 8
            .lcomm vk64_d64ccap, 8
            .lcomm vk64_dpool12, 8
            .lcomm vk64_dset12, 8
            .lcomm vk64_pipe12, 8
            // scratch de structs Vulkan (align 64; os create infos +
            // VkPhysicalDeviceMemoryProperties ~4KB + dbi/wds)
            .align 64
            .lcomm vk64_scratch, 8192
            """);
    }
}
