package dev.kof.compiler;

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
        emitDataAndBss(sb);
        emitLoader(sb);
        emitHelpers(sb);
        emitInitCommon(sb);
        emitAllocBuffer(sb);
        emitSubmitHelper(sb);
        emitWriteDesc(sb);
        emitShapeXy(sb);
        emitSetShape(sb);
        emitLoadW(sb);
        emitMatvec(sb);
        emitWput(sb);
        emitWrun(sb);
        emitWput32(sb);
        emitWrun32(sb);
        emitWputsp(sb);
        emitWrunsp(sb);
        emitDispatch64(sb);
        return sb.toString();
    }

    // ───────────────────────── .data + .bss ─────────────────────────
    private static void emitDataAndBss(StringBuilder sb) {
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

    // ───────────────────────── loader (dlopen/dlsym) ─────────────────
    private static void emitLoader(StringBuilder sb) {
        sb.append("""
            .section .rodata
            .align 8
            // tabela vk*: (ptr nome, ptr slot) — termina com 0,0
            vk64_symtab:
                .quad .Lvkv_vkCreateInstance, g_vk64_vkCreateInstance
                .quad .Lvkv_vkEnumeratePhysicalDevices, g_vk64_vkEnumeratePhysicalDevices
                .quad .Lvkv_vkGetPhysicalDeviceQueueFamilyProperties, g_vk64_vkGetPhysicalDeviceQueueFamilyProperties
                .quad .Lvkv_vkGetPhysicalDeviceMemoryProperties, g_vk64_vkGetPhysicalDeviceMemoryProperties
                .quad .Lvkv_vkCreateDevice, g_vk64_vkCreateDevice
                .quad .Lvkv_vkGetDeviceQueue, g_vk64_vkGetDeviceQueue
                .quad .Lvkv_vkCreateShaderModule, g_vk64_vkCreateShaderModule
                .quad .Lvkv_vkCreateDescriptorSetLayout, g_vk64_vkCreateDescriptorSetLayout
                .quad .Lvkv_vkCreatePipelineLayout, g_vk64_vkCreatePipelineLayout
                .quad .Lvkv_vkCreateComputePipelines, g_vk64_vkCreateComputePipelines
                .quad .Lvkv_vkCreateDescriptorPool, g_vk64_vkCreateDescriptorPool
                .quad .Lvkv_vkAllocateDescriptorSets, g_vk64_vkAllocateDescriptorSets
                .quad .Lvkv_vkCreateCommandPool, g_vk64_vkCreateCommandPool
                .quad .Lvkv_vkAllocateCommandBuffers, g_vk64_vkAllocateCommandBuffers
                .quad .Lvkv_vkCreateFence, g_vk64_vkCreateFence
                .quad .Lvkv_vkCreateBuffer, g_vk64_vkCreateBuffer
                .quad .Lvkv_vkGetBufferMemoryRequirements, g_vk64_vkGetBufferMemoryRequirements
                .quad .Lvkv_vkAllocateMemory, g_vk64_vkAllocateMemory
                .quad .Lvkv_vkBindBufferMemory, g_vk64_vkBindBufferMemory
                .quad .Lvkv_vkMapMemory, g_vk64_vkMapMemory
                .quad .Lvkv_vkUnmapMemory, g_vk64_vkUnmapMemory
                .quad .Lvkv_vkDestroyBuffer, g_vk64_vkDestroyBuffer
                .quad .Lvkv_vkFreeMemory, g_vk64_vkFreeMemory
                .quad .Lvkv_vkUpdateDescriptorSets, g_vk64_vkUpdateDescriptorSets
                .quad .Lvkv_vkBeginCommandBuffer, g_vk64_vkBeginCommandBuffer
                .quad .Lvkv_vkCmdBindPipeline, g_vk64_vkCmdBindPipeline
                .quad .Lvkv_vkCmdBindDescriptorSets, g_vk64_vkCmdBindDescriptorSets
                .quad .Lvkv_vkCmdPushConstants, g_vk64_vkCmdPushConstants
                .quad .Lvkv_vkCmdDispatch, g_vk64_vkCmdDispatch
                .quad .Lvkv_vkEndCommandBuffer, g_vk64_vkEndCommandBuffer
                .quad .Lvkv_vkQueueSubmit, g_vk64_vkQueueSubmit
                .quad .Lvkv_vkWaitForFences, g_vk64_vkWaitForFences
                .quad .Lvkv_vkResetFences, g_vk64_vkResetFences
                .quad 0, 0

            .section .text
            // vk64_load: dlopen(libvulkan.so.1) + dlsym da tabela.
            // → 0 ok / 1 falha. Preserva regs callee-saved.
            .globl vk64_load
            .type vk64_load, @function
            vk64_load:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                subq $8, %rsp                      # alinha (4 pushes)
                leaq .Lvkv_libname(%rip), %rdi
                movl $2, %esi                      # RTLD_NOW
                call dlopen@PLT
                testq %rax, %rax
                jz .Lvk64_load_fail
                movq %rax, vk64_lib(%rip)
                leaq vk64_symtab(%rip), %r12
            .Lvk64_loop:
                movq 0(%r12), %r13                 # nome
                testq %r13, %r13
                jz .Lvk64_load_ok
                movq 8(%r12), %r14                 # slot
                movq vk64_lib(%rip), %rdi
                movq %r13, %rsi
                call dlsym@PLT
                testq %rax, %rax
                jz .Lvk64_load_fail
                movq %rax, (%r14)
                addq $16, %r12
                jmp .Lvk64_loop
            .Lvk64_load_ok:
                xorl %eax, %eax
                addq $8, %rsp
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lvk64_load_fail:
                movl $1, %eax
                addq $8, %rsp
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            // vk64_fail(errmsg rdi): snprintf(errbuf, "%s (rc=%d)", msg, rax)
            // e retorna 1 em eax — o padrão CK do C. O rc vem em rsi (int).
            .type vk64_fail, @function
            vk64_fail:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx                    # msg
                movl %esi, %r12d                   # rc
                leaq vk64_errbuf(%rip), %rdi
                movl $256, %esi
                leaq .Lvkv_errfmt(%rip), %rdx
                movq %rbx, %rcx
                movl %r12d, %r8d
                xorl %eax, %eax
                call snprintf@PLT
                movl $1, %eax
                popq %r13
                popq %r12
                popq %rbx
                ret

            // vk64_fail0(errmsg rdi): snprintf(errbuf, "%s", msg) → 1 em eax
            .type vk64_fail0, @function
            vk64_fail0:
                pushq %rbx
                movq %rdi, %rbx
                leaq vk64_errbuf(%rip), %rdi
                movl $256, %esi
                movq %rbx, %rdx
                xorl %eax, %eax
                call snprintf@PLT
                movl $1, %eax
                popq %rbx
                ret
            """);
    }

    // ───────────────────────── helpers ───────────────────────────────
    private static void emitHelpers(StringBuilder sb) {
        sb.append("""
            // vk64_read_file(rdi=path) → rax=ptr malloc, rdx=size, 0/0 falha
            .type vk64_read_file, @function
            vk64_read_file:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                leaq .Lvkv_rb(%rip), %rsi
                call fopen@PLT
                testq %rax, %rax
                jz .Lvk64_rf0
                movq %rax, %r12
                movq %r12, %rdi
                xorl %esi, %esi                    # offset 0
                movl $2, %edx                      # SEEK_END
                call fseek@PLT
                movq %r12, %rdi
                call ftell@PLT
                movq %rax, %r13                    # size
                movq %r12, %rdi
                xorl %esi, %esi                    # offset 0
                xorl %edx, %edx                    # SEEK_SET
                call fseek@PLT
                testq %r13, %r13
                jz .Lvk64_rf_close
                movq %r13, %rdi
                call malloc@PLT
                testq %rax, %rax
                jz .Lvk64_rf_close
                movq %rax, %rbx                    # buf
                movq %rbx, %rdi
                movq $1, %rsi
                movq %r13, %rdx
                movq %r12, %rcx
                call fread@PLT
                cmpq %r13, %rax
                jne .Lvk64_rf_free
                movq %r12, %rdi
                call fclose@PLT
                movq %rbx, %rax
                movq %r13, %rdx
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lvk64_rf_free:
                movq %rbx, %rdi
                call free@PLT
            .Lvk64_rf_close:
                movq %r12, %rdi
                call fclose@PLT
            .Lvk64_rf0:
                xorl %eax, %eax
                xorl %edx, %edx
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }

    // vk64_alloc_buffer(rdi=bytes, rsi=bufOut, rdx=memOut, rcx=mapOut)
    // → 0 ok / 1 falha (errbuf setado). Tradução do allocBuffer do C.
    private static void emitAllocBuffer(StringBuilder sb) {
        sb.append("""
            .type vk64_alloc_buffer, @function
            vk64_alloc_buffer:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $48, %rsp
                movq %rdi, %rbx                    # bytes
                movq %rsi, %r12                    # bufOut
                movq %rdx, 16(%rsp)                # memOut (stack; r13 vira t)
                movq %rcx, %r14                    # mapOut
                // VkBufferCreateInfo @scratch+0: sType=12, pNext=0, flags=0,
                // size, usage=VK_BUFFER_USAGE_STORAGE_BUFFER_BIT(32),
                // sharingMode=VK_SHARING_MODE_EXCLUSIVE(0), qfamCount=0, p=0
                leaq vk64_scratch(%rip), %rdi
                movl $12, 0(%rdi)
                movq $0, 8(%rdi)
                movl $0, 16(%rdi)
                movq %rbx, 24(%rdi)
                movl $32, 32(%rdi)
                movl $0, 36(%rdi)
                movl $0, 40(%rdi)
                movq $0, 48(%rdi)
                movl $0, 0(%rsp)                   # buf local (out vkCreateBuffer)
                movq g_vk64_vkCreateBuffer(%rip), %rax
                testq %rax, %rax
                jz .Lvk64_ab_bad
                movq vk64_dev(%rip), %rdi
                leaq vk64_scratch(%rip), %rsi
                xorl %edx, %edx
                movq %rsp, %rcx
                call *%rax
                testl %eax, %eax
                jnz .Lvk64_ab_ck_buffer
                movq 0(%rsp), %r15
                movq %r15, (%r12)                  # *bufOut
                // VkMemoryRequirements @scratch+64: vkGetBufferMemoryRequirements
                movq g_vk64_vkGetBufferMemoryRequirements(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq %r15, %rsi
                leaq vk64_scratch+64(%rip), %rdx
                call *%rax
                // memoryTypeBits @64+16 = scratch+80
                // VkPhysicalDeviceMemoryProperties @scratch+128
                movq g_vk64_vkGetPhysicalDeviceMemoryProperties(%rip), %rax
                movq vk64_phys(%rip), %rdi
                leaq vk64_scratch+128(%rip), %rsi
                call *%rax
                // memoryTypeCount @+128; memoryTypes[32] @+132
                // (8B cada: propertyFlags @0, heapIndex @4)
                movl 128+vk64_scratch(%rip), %r15d # count
                movl $0, 8(%rsp)                   # t = 0 (stack: r13 é memOut)
            .Lvk64_ab_mtloop:
                movl 8(%rsp), %r13d
                cmpl %r15d, %r13d
                jge .Lvk64_ab_nomem
                // (1u<<t) & req.memoryTypeBits
                movl $1, %eax
                movl %r13d, %ecx
                shll %cl, %eax
                andl 80+vk64_scratch(%rip), %eax   # req.memoryTypeBits @64+16
                jz .Lvk64_ab_mtnext
                // memoryTypes[t].propertyFlags @128+4+32t
                movl %r13d, %eax
                shll $3, %eax                      # t*8 (VkMemoryType = 8B)
                cltq
                leaq 128+4+vk64_scratch(%rax), %rcx   # memoryTypes[t].propertyFlags @+4+8t
                movl (%rcx), %edx
                // HOST_VISIBLE(1) | HOST_COHERENT(4) = 5
                andl $5, %edx
                cmpl $5, %edx
                jne .Lvk64_ab_mtnext
                // achou: mi = t (r13d já contém t)
                jmp .Lvk64_ab_mtfound
            .Lvk64_ab_mtnext:
                incl 8(%rsp)
                jmp .Lvk64_ab_mtloop
            .Lvk64_ab_mtfound:
                // VkMemoryAllocateInfo @scratch+4224: sType=10, size=req.size,
                // memoryTypeIndex=mi
                leaq 4224+vk64_scratch(%rip), %rdi
                movl $5, 0(%rdi)
                movq $0, 8(%rdi)
                movq 64+vk64_scratch(%rip), %rax   # req.size @64+0
                movq %rax, 16(%rdi)
                movl %r13d, 24(%rdi)
                movl $0, 0(%rsp)                   # mem out
                movq g_vk64_vkAllocateMemory(%rip), %rax
                testq %rax, %rax
                jz .Lvk64_ab_bad
                movq vk64_dev(%rip), %rdi
                leaq 4224+vk64_scratch(%rip), %rsi
                xorl %edx, %edx
                movq %rsp, %rcx
                call *%rax
                testl %eax, %eax
                jnz .Lvk64_ab_ck_allocmem
                movq 0(%rsp), %r15
                movq %r15, 16(%rsp)                # *memOut (direto no slot)
                movq g_vk64_vkBindBufferMemory(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq (%r12), %rsi
                movq %r15, %rdx
                xorl %ecx, %ecx
                call *%rax
                // (bind ok: mem = 16(%rsp))
                testl %eax, %eax
                jnz .Lvk64_ab_ck_bindmem
                movl $0, 0(%rsp)                   # map out
                movq g_vk64_vkMapMemory(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq 16(%rsp), %rsi                # mem
                xorl %edx, %edx                    # offset
                movq %rbx, %rcx                    # size = bytes
                xorl %r8d, %r8d                    # flags
                movq %rsp, %r9
                call *%rax
                testl %eax, %eax
                jnz .Lvk64_ab_ck_map
                movq 0(%rsp), %rax
                movq %rax, (%r14)                  # *mapOut
                xorl %eax, %eax                    # ok
                addq $48, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lvk64_ab_ck_buffer:
                leaq .Lvkv_e_buffer(%rip), %rdi
                movl %eax, %esi
                call vk64_fail
                jmp .Lvk64_ab_ret1
            .Lvk64_ab_ck_allocmem:
                leaq .Lvkv_e_allocmem(%rip), %rdi
                movl %eax, %esi
                call vk64_fail
                jmp .Lvk64_ab_ret1
            .Lvk64_ab_ck_bindmem:
                leaq .Lvkv_e_bindmem(%rip), %rdi
                movl %eax, %esi
                call vk64_fail
                jmp .Lvk64_ab_ret1
            .Lvk64_ab_ck_map:
                leaq .Lvkv_e_map(%rip), %rdi
                movl %eax, %esi
                call vk64_fail
                jmp .Lvk64_ab_ret1
            .Lvk64_ab_nomem:
                leaq .Lvkv_e_mem(%rip), %rdi
                call vk64_fail0
            .Lvk64_ab_bad:
            .Lvk64_ab_ret1:
                movl $1, %eax
                addq $48, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }

    // ───────────────────────── init_common ───────────────────────────
    // Tradução do init_common do C. Stack frame (após prólogo):
    //   0(%rsp): n / out genérico
    //   8(%rsp): spv size
    //   16(%rsp): prio f32
    //   32(%rsp): physv[4] (32B)
    //   64(%rsp): qf[8] (VkQueueFamilyProperties = flags 4, queueCount 4,
    //     timestampValidBits 4, minImageTransferGranularity 12 → 24B; 8×24=192B)
    //   256(%rsp): sm (shader module)
    // Scratch: ver mapa no cabeçalho da classe.
    private static void emitInitCommon(StringBuilder sb) {
        sb.append("""
            // vk64_init_common(rdi=spv_path, esi=nbinds) → 0 ok / 1 falha
            .type vk64_init_common, @function
            vk64_init_common:
                cmpl $0, vk64_inited(%rip)
                jne .Lvk64_ic_ok0
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $288, %rsp
                movl %esi, %r13d                   # nbinds
                call vk64_load
                # features: tudo 0, shaderInt64 = 1
                leaq 5904+vk64_scratch(%rip), %rdi
                movq $0, 0(%rdi)
                movq $0, 8(%rdi)
                movq $0, 16(%rdi)
                movq $0, 24(%rdi)
                movq $0, 32(%rdi)
                movq $0, 40(%rdi)
                movq $0, 48(%rdi)
                movq $0, 56(%rdi)
                movq $0, 64(%rdi)
                movq $0, 72(%rdi)
                movq $0, 80(%rdi)
                movq $0, 88(%rdi)
                movq $0, 96(%rdi)
                movq $0, 104(%rdi)
                movq $0, 112(%rdi)
                movq $0, 120(%rdi)
                movq $0, 128(%rdi)
                movq $0, 136(%rdi)
                movq $0, 144(%rdi)
                movq $0, 152(%rdi)
                movl $1, 160(%rdi)                 # shaderInt64
                testl %eax, %eax
                jnz .Lvk64_ic_fail
                // VkApplicationInfo @+4288: sType=0, pNext=0, pApplicationName
                // ="kof", appVer=0, engineVer=0, apiVersion=VK_API_VERSION_1_3
                // (4202623 = variante0 major1 minor3 patch3 → (1<<22)|(3<<12)|3)
                leaq 4288+vk64_scratch(%rip), %rdi
                movl $0, 0(%rdi)
                movq $0, 8(%rdi)
                leaq .Lvkv_appname(%rip), %rax
                movq %rax, 16(%rdi)
                movl $0, 24(%rdi)
                movl $0, 28(%rdi)
                movl $4206592, 32(%rdi)                  # VK_API_VERSION_1_3 = (1<<22)|(3<<12)
                // VkInstanceCreateInfo @+4352: sType=1, pNext=0, flags=0,
                // pApplicationInfo=@4288, 0,0,0,0
                leaq 4352+vk64_scratch(%rip), %rdi
                movl $1, 0(%rdi)
                movq $0, 8(%rdi)
                movl $0, 16(%rdi)
                leaq 4288+vk64_scratch(%rip), %rax
                movq %rax, 24(%rdi)
                movl $0, 32(%rdi)
                movq $0, 40(%rdi)
                movl $0, 48(%rdi)
                movq $0, 56(%rdi)
                movl $0, 0(%rsp)                   # inst out
                movq g_vk64_vkCreateInstance(%rip), %rax
                leaq 4352+vk64_scratch(%rip), %rdi # pCreateInfo
                xorl %esi, %esi                    # pAllocator
                movq %rsp, %rdx                    # pInstance
                call *%rax
                testl %eax, %eax
                jnz .Lvk64_ic_ck_instance
                movq 0(%rsp), %rax
                movq %rax, vk64_inst(%rip)
                // vkEnumeratePhysicalDevices(inst, &n, 0)
                movq g_vk64_vkEnumeratePhysicalDevices(%rip), %rax
                movq vk64_inst(%rip), %rdi
                movq %rsp, %rsi
                xorl %edx, %edx
                call *%rax
                testl %eax, %eax
                jnz .Lvk64_ic_ck_enum0
                cmpl $0, 0(%rsp)
                jne .Lvk64_ic_havephys
                leaq .Lvkv_e_nodev(%rip), %rdi
                call vk64_fail0
                jmp .Lvk64_ic_ret1
            .Lvk64_ic_havephys:
                // vkEnumeratePhysicalDevices(inst, &n, physv) — n ≤ 4
                movq g_vk64_vkEnumeratePhysicalDevices(%rip), %rax
                movq vk64_inst(%rip), %rdi
                movq %rsp, %rsi
                leaq 32(%rsp), %rdx
                call *%rax
                testl %eax, %eax
                jnz .Lvk64_ic_ck_enum
                movq 32(%rsp), %rax
                movq %rax, vk64_phys(%rip)
                // queue families: 2× vkGetPhysicalDeviceQueueFamilyProperties
                movq g_vk64_vkGetPhysicalDeviceQueueFamilyProperties(%rip), %rax
                movq vk64_phys(%rip), %rdi
                leaq 0(%rsp), %rsi                 # &n (reuso)
                xorl %edx, %edx
                call *%rax
                movq g_vk64_vkGetPhysicalDeviceQueueFamilyProperties(%rip), %rax
                movq vk64_phys(%rip), %rdi
                leaq 0(%rsp), %rsi
                leaq 64(%rsp), %rdx                # qf[8]
                call *%rax
                // achar a family com VK_QUEUE_COMPUTE_BIT (4)
                movl $0, %r14d                     # qfam
            .Lvk64_ic_qloop:
                movl 0(%rsp), %eax                 # qn
                cmpl %eax, %r14d
                jge .Lvk64_ic_noqueue
                movl %r14d, %eax
                imull $24, %eax, %eax
                cltq                               # extende p/ rax 64
                leaq 64(%rsp,%rax), %rcx
                movl (%rcx), %edx                  # queueFlags
                andl $4, %edx
                jnz .Lvk64_ic_qfound
                incl %r14d
                jmp .Lvk64_ic_qloop
            .Lvk64_ic_qfound:
                movl %r14d, vk64_qfam(%rip)
                // VkDeviceQueueCreateInfo @+4416: sType=2, flags=0,
                // queueFamilyIndex=qfam, queueCount=1, pQueuePriorities=&prio
                leaq 4416+vk64_scratch(%rip), %rdi
                movl $2, 0(%rdi)
                movq $0, 8(%rdi)
                movl $0, 16(%rdi)
                movl %r14d, 20(%rdi)
                movl $1, 24(%rdi)
                leaq 16(%rsp), %rax
                movq %rax, 32(%rdi)
                movl $0x3f800000, 16(%rsp)         # prio = 1.0f
                // VkDeviceCreateInfo @+4464: sType=3, flags=0, count=1,
                // pQueueCreateInfos=@4416, 0,0,0,0
                leaq 4464+vk64_scratch(%rip), %rdi
                movl $3, 0(%rdi)
                movq $0, 8(%rdi)
                movl $0, 16(%rdi)
                movl $1, 20(%rdi)
                leaq 4416+vk64_scratch(%rip), %rax
                movq %rax, 24(%rdi)
                movl $0, 32(%rdi)
                movq $0, 40(%rdi)
                movl $0, 48(%rdi)
                movq $0, 56(%rdi)
                movq $0, 64(%rdi)
                # pEnabledFeatures @72: VkPhysicalDeviceFeatures com
                # shaderInt64 (@+160) = 1 (o matvec64.spv usa Int64)
                leaq 5904+vk64_scratch(%rip), %rax
                movq %rax, 72(%rdi)
                movl $0, 0(%rsp)                   # dev out
                movq g_vk64_vkCreateDevice(%rip), %rax
                movq vk64_phys(%rip), %rdi
                leaq 4464+vk64_scratch(%rip), %rsi
                xorl %edx, %edx
                movq %rsp, %rcx
                call *%rax
                testl %eax, %eax
                jnz .Lvk64_ic_ck_device
                movq 0(%rsp), %rax
                movq %rax, vk64_dev(%rip)
                movq g_vk64_vkGetDeviceQueue(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movl %r14d, %esi
                xorl %edx, %edx
                leaq vk64_queue(%rip), %rcx
                call *%rax
                jmp .Lvk64_ic_spv
            .Lvk64_ic_noqueue:
                leaq .Lvkv_e_noqueue(%rip), %rdi
                call vk64_fail0
                jmp .Lvk64_ic_ret1
            """);
        emitInitCommonSpv(sb);
        sb.append("""
            .Lvk64_ic_ok0:
                xorl %eax, %eax
                ret
            .Lvk64_ic_fail:
                movl $1, %eax
                addq $288, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lvk64_ic_ret1:
                movl $1, %eax
                addq $288, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }

    // Parte B: spv → shader module → dsl/pl (3 binds) → pipe principal
    // → pipe32 (opcional, KOF_GPU_SPV64_W32) → split (opcional,
    // KOF_GPU_SPV64_SPLIT: dsl5/pl5/pipe/dpool5/dset5) → dpool/dset
    // → cpool/cmd/fence. r13d = nbinds.
    private static void emitInitCommonSpv(StringBuilder sb) {
        sb.append("""
            .Lvk64_ic_spv:
                // ler o SPV principal: KOF_GPU_SPV64 (r12 = path)
                leaq .Lvkv_sov64(%rip), %rdi
                call getenv@PLT
                testq %rax, %rax
                jz .Lvk64_ic_spvopen
                movq %rax, %rdi
                call vk64_read_file
                testq %rax, %rax
                jz .Lvk64_ic_spvopen
                movq %rax, %rbx                    # code
                movq %rdx, 8(%rsp)                 # sz
                // VkShaderModuleCreateInfo @+4544: sType=15, pNext=0,
                // flags=0, codeSize=sz, pCode=code
                leaq 4544+vk64_scratch(%rip), %rdi
                movl $16, 0(%rdi)
                movq $0, 8(%rdi)
                movl $0, 16(%rdi)
                movq 8(%rsp), %rax
                movq %rax, 24(%rdi)
                movq %rbx, 32(%rdi)
                movl $0, 256(%rsp)                 # sm out
                movq g_vk64_vkCreateShaderModule(%rip), %rax
                movq vk64_dev(%rip), %rdi
                leaq 4544+vk64_scratch(%rip), %rsi
                xorl %edx, %edx
                leaq 256(%rsp), %rcx
                call *%rax
                testl %eax, %eax
                jnz .Lvk64_ic_ck_shader
                movq %rbx, %rdi
                call free@PLT                      # o driver copia o code
                // dsl: nbinds binds @+4608 (24B cada: binding=b,
                // type=6 STORAGE_BUFFER, count=1, stage=COMPUTE(32), p=0)
                leaq 4608+vk64_scratch(%rip), %rdi
                xorl %eax, %eax
            .Lvk64_ic_binds:
                cmpl %r13d, %eax
                jge .Lvk64_ic_binds_done
                movl %eax, %ecx
                imull $24, %ecx, %ecx              # stride 24B (sizeof binding)
                movslq %ecx, %rcx
                leaq 4608+vk64_scratch(%rcx), %rdx
                movl %eax, 0(%rdx)                 # binding
                movl $7, 4(%rdx)                   # STORAGE_BUFFER
                movl $1, 8(%rdx)                   # count
                movl $32, 12(%rdx)                 # COMPUTE stage
                movq $0, 16(%rdx)
                incl %eax
                jmp .Lvk64_ic_binds
            .Lvk64_ic_binds_done:
                // VkDescriptorSetLayoutCreateInfo @+4736: sType=19, count=nbinds,
                // pBindings=@4608
                leaq 4736+vk64_scratch(%rip), %rdi
                movl $32, 0(%rdi)
                movq $0, 8(%rdi)
                movl $0, 16(%rdi)
                movl %r13d, 20(%rdi)
                leaq 4608+vk64_scratch(%rip), %rax
                movq %rax, 24(%rdi)
                movl $0, 0(%rsp)                   # dsl out
                movq g_vk64_vkCreateDescriptorSetLayout(%rip), %rax
                movq vk64_dev(%rip), %rdi
                leaq 4736+vk64_scratch(%rip), %rsi
                xorl %edx, %edx
                movq %rsp, %rcx
                call *%rax
                testl %eax, %eax
                jnz .Lvk64_ic_ck_dsll
                movq 0(%rsp), %rax
                movq %rax, vk64_dsl(%rip)
                // VkPushConstantRange @+4768: stage=COMPUTE(32), offset=0, size=24
                leaq 4768+vk64_scratch(%rip), %rdi
                movl $32, 0(%rdi)
                movl $0, 4(%rdi)
                movl $24, 8(%rdi)
                // VkPipelineLayoutCreateInfo @+4800: sType=20, count=1,
                // pSetLayouts=&dsl, rangeCount=1, pRanges=@4768
                leaq 4800+vk64_scratch(%rip), %rdi
                movl $30, 0(%rdi)
                movq $0, 8(%rdi)
                movl $0, 16(%rdi)
                movl $1, 20(%rdi)
                leaq vk64_dsl(%rip), %rax
                movq %rax, 24(%rdi)
                movl $1, 32(%rdi)
                leaq 4768+vk64_scratch(%rip), %rax
                movq %rax, 40(%rdi)
                movl $0, 0(%rsp)
                movq g_vk64_vkCreatePipelineLayout(%rip), %rax
                movq vk64_dev(%rip), %rdi
                leaq 4800+vk64_scratch(%rip), %rsi
                xorl %edx, %edx
                movq %rsp, %rcx
                call *%rax
                testl %eax, %eax
                jnz .Lvk64_ic_ck_pll
                movq 0(%rsp), %rax
                movq %rax, vk64_pl(%rip)
                // VkPipelineShaderStageCreateInfo @+4864: sType=5, stage=COMPUTE,
                // module=sm, pName="main"
                leaq 4864+vk64_scratch(%rip), %rdi
                movl $18, 0(%rdi)
                movq $0, 8(%rdi)
                movl $0, 16(%rdi)
                movl $32, 20(%rdi)
                movq 256(%rsp), %rax
                movq %rax, 24(%rdi)
                leaq .Lvkv_main(%rip), %rax
                movq %rax, 32(%rdi)
                movq $0, 40(%rdi)
                // VkComputePipelineCreateInfo @+4928: sType=24, flags=0,
                // stage=embed(48B)@+24, layout=pl, base 0,0
                leaq 4928+vk64_scratch(%rip), %rdi
                movl $29, 0(%rdi)
                movq $0, 8(%rdi)
                movl $0, 16(%rdi)
                leaq 4864+vk64_scratch(%rip), %rax
                movq %rax, %rsi
                movq 0(%rsi), %rcx                 # 48B do stage → +24
                movq 8(%rsi), %rdx
                movq %rcx, 24(%rdi)
                movq %rdx, 32(%rdi)
                movq 16(%rsi), %rcx
                movq 24(%rsi), %rdx
                movq %rcx, 40(%rdi)
                movq %rdx, 48(%rdi)
                movq 32(%rsi), %rcx
                movq 40(%rsi), %rdx
                movq %rcx, 56(%rdi)
                movq %rdx, 64(%rdi)
                movq vk64_pl(%rip), %rax
                movq %rax, 72(%rdi)
                movq $0, 80(%rdi)
                movq $0, 88(%rdi)
                movl $0, 0(%rsp)                   # pipe out
                movq g_vk64_vkCreateComputePipelines(%rip), %rax
                movq vk64_dev(%rip), %rdi
                xorl %esi, %esi                    # pipelineCache = 0
                movl $1, %edx
                leaq 4928+vk64_scratch(%rip), %rcx
                xorl %r8d, %r8d
                movq %rsp, %r9
                call *%rax
                testl %eax, %eax
                jnz .Lvk64_ic_ck_pipe
                movq 0(%rsp), %rax
                movq %rax, vk64_pipe(%rip)
                jmp .Lvk64_ic_pipe32
            .Lvk64_ic_spvopen:
                leaq .Lvkv_e_spvopen(%rip), %rdi
                call vk64_fail0
                jmp .Lvk64_ic_ret1
            .Lvk64_ic_ck_instance:
                leaq .Lvkv_e_instance(%rip), %rdi
                movl %eax, %esi
                call vk64_fail
                jmp .Lvk64_ic_ret1
            .Lvk64_ic_ck_enum0:
                leaq .Lvkv_e_enum0(%rip), %rdi
                movl %eax, %esi
                call vk64_fail
                jmp .Lvk64_ic_ret1
            .Lvk64_ic_ck_enum:
                leaq .Lvkv_e_enum(%rip), %rdi
                movl %eax, %esi
                call vk64_fail
                jmp .Lvk64_ic_ret1
            .Lvk64_ic_ck_device:
                leaq .Lvkv_e_device(%rip), %rdi
                movl %eax, %esi
                call vk64_fail
                jmp .Lvk64_ic_ret1
            .Lvk64_ic_ck_shader:
                leaq .Lvkv_e_shader(%rip), %rdi
                movl %eax, %esi
                call vk64_fail
                jmp .Lvk64_ic_ret1
            .Lvk64_ic_ck_dsll:
                leaq .Lvkv_e_dsll(%rip), %rdi
                movl %eax, %esi
                call vk64_fail
                jmp .Lvk64_ic_ret1
            .Lvk64_ic_ck_pll:
                leaq .Lvkv_e_pll(%rip), %rdi
                movl %eax, %esi
                call vk64_fail
                jmp .Lvk64_ic_ret1
            .Lvk64_ic_ck_pipe:
                leaq .Lvkv_e_pipe(%rip), %rdi
                movl %eax, %esi
                call vk64_fail
                jmp .Lvk64_ic_ret1
            """);
        emitInitCommonPipe32(sb);
        emitInitCommonSplit(sb);
        emitInitCommonPools(sb);
    }

    // pipe32 opcional: mesmo layout pl, SPV de KOF_GPU_SPV64_W32.
    // Falha é não-fatal (wrun32 → rc != 0 → CPU).
    private static void emitInitCommonPipe32(StringBuilder sb) {
        sb.append("""
            .Lvk64_ic_pipe32:
                movq $0, vk64_pipe32(%rip)
                leaq .Lvkv_sovw32(%rip), %rdi
                call getenv@PLT
                testq %rax, %rax
                jz .Lvk64_ic_split
                movq %rax, %rdi
                call vk64_read_file
                testq %rax, %rax
                jz .Lvk64_ic_split
                movq %rax, %rbx
                movq %rdx, 8(%rsp)
                leaq 4544+vk64_scratch(%rip), %rdi
                movl $16, 0(%rdi)
                movq $0, 8(%rdi)
                movl $0, 16(%rdi)
                movq 8(%rsp), %rax
                movq %rax, 24(%rdi)
                movq %rbx, 32(%rdi)
                movl $0, 0(%rsp)                   # sm32 out (reuso 0(%rsp))
                movq g_vk64_vkCreateShaderModule(%rip), %rax
                movq vk64_dev(%rip), %rdi
                leaq 4544+vk64_scratch(%rip), %rsi
                xorl %edx, %edx
                movq %rsp, %rcx
                call *%rax
                movq %rbx, %rdi
                call free@PLT
                testl %eax, %eax
                jnz .Lvk64_ic_split                # não-fatal
                // stage32 @+4864 (reuso) + pci32 @+4928 (reuso)
                leaq 4864+vk64_scratch(%rip), %rdi
                movl $18, 0(%rdi)
                movq $0, 8(%rdi)
                movl $0, 16(%rdi)
                movl $32, 20(%rdi)
                movq 0(%rsp), %rax
                movq %rax, 24(%rdi)
                leaq .Lvkv_main(%rip), %rax
                movq %rax, 32(%rdi)
                movq $0, 40(%rdi)
                leaq 4928+vk64_scratch(%rip), %rdi
                movl $29, 0(%rdi)
                movq $0, 8(%rdi)
                movl $0, 16(%rdi)
                leaq 4864+vk64_scratch(%rip), %rax
                movq %rax, %rsi
                movq 0(%rsi), %rcx
                movq 8(%rsi), %rdx
                movq %rcx, 24(%rdi)
                movq %rdx, 32(%rdi)
                movq 16(%rsi), %rcx
                movq 24(%rsi), %rdx
                movq %rcx, 40(%rdi)
                movq %rdx, 48(%rdi)
                movq 32(%rsi), %rcx
                movq 40(%rsi), %rdx
                movq %rcx, 56(%rdi)
                movq %rdx, 64(%rdi)
                movq vk64_pl(%rip), %rax
                movq %rax, 72(%rdi)
                movq $0, 80(%rdi)
                movq $0, 88(%rdi)
                movq $0, 264(%rsp)                 # pipe32 out
                movq g_vk64_vkCreateComputePipelines(%rip), %rax
                movq vk64_dev(%rip), %rdi
                xorl %esi, %esi
                movl $1, %edx
                leaq 4928+vk64_scratch(%rip), %rcx
                xorl %r8d, %r8d
                leaq 264(%rsp), %r9
                call *%rax
                testl %eax, %eax
                jnz .Lvk64_ic_split                # não-fatal
                movq 264(%rsp), %rax
                movq %rax, vk64_pipe32(%rip)
                // pipe12: matmul64 (KOF_GPU_SPV64_MM, fallback SPV64)
                movq $0, vk64_pipe12(%rip)
                leaq .Lvkv_sovmm(%rip), %rdi
                call getenv@PLT
                testq %rax, %rax
                jnz .Lvk64_ic_mmhave
                leaq .Lvkv_sov64(%rip), %rdi
                call getenv@PLT
                testq %rax, %rax
                jz .Lvk64_ic_split
            .Lvk64_ic_mmhave:
                movq %rax, %rdi
                call vk64_read_file
                testq %rax, %rax
                jz .Lvk64_ic_split
                movq %rax, %rbx
                movq %rdx, 8(%rsp)
                leaq 4544+vk64_scratch(%rip), %rdi
                movl $16, 0(%rdi)
                movq $0, 8(%rdi)
                movl $0, 16(%rdi)
                movq 8(%rsp), %rax
                movq %rax, 24(%rdi)
                movq %rbx, 32(%rdi)
                movl $0, 0(%rsp)
                movq g_vk64_vkCreateShaderModule(%rip), %rax
                movq vk64_dev(%rip), %rdi
                leaq 4544+vk64_scratch(%rip), %rsi
                xorl %edx, %edx
                movq %rsp, %rcx
                call *%rax
                movq %rbx, %rdi
                call free@PLT
                testl %eax, %eax
                jnz .Lvk64_ic_split
                leaq 4864+vk64_scratch(%rip), %rdi
                movl $18, 0(%rdi)
                movq $0, 8(%rdi)
                movl $0, 16(%rdi)
                movl $32, 20(%rdi)
                movq 0(%rsp), %rax
                movq %rax, 24(%rdi)
                leaq .Lvkv_main(%rip), %rax
                movq %rax, 32(%rdi)
                movq $0, 40(%rdi)
                leaq 4928+vk64_scratch(%rip), %rdi
                movl $29, 0(%rdi)
                movq $0, 8(%rdi)
                movl $0, 16(%rdi)
                leaq 4864+vk64_scratch(%rip), %rax
                movq %rax, %rsi
                movq 0(%rsi), %rcx
                movq 8(%rsi), %rdx
                movq %rcx, 24(%rdi)
                movq %rdx, 32(%rdi)
                movq 16(%rsi), %rcx
                movq 24(%rsi), %rdx
                movq %rcx, 40(%rdi)
                movq %rdx, 48(%rdi)
                movq 32(%rsi), %rcx
                movq 40(%rsi), %rdx
                movq %rcx, 56(%rdi)
                movq %rdx, 64(%rdi)
                movq vk64_pl(%rip), %rax
                movq %rax, 72(%rdi)
                movq $0, 80(%rdi)
                movq $0, 88(%rdi)
                movq $0, 264(%rsp)
                movq g_vk64_vkCreateComputePipelines(%rip), %rax
                movq vk64_dev(%rip), %rdi
                xorl %esi, %esi
                movl $1, %edx
                leaq 4928+vk64_scratch(%rip), %rcx
                xorl %r8d, %r8d
                leaq 264(%rsp), %r9
                call *%rax
                testl %eax, %eax
                jnz .Lvk64_ic_split
                movq 264(%rsp), %rax
                movq %rax, vk64_pipe12(%rip)
            """);
    }

    // split opcional: dsl5/pl5/pipeSplit/dpool5/dset5 (5 SSBOs).
    private static void emitInitCommonSplit(StringBuilder sb) {
        sb.append("""
            .Lvk64_ic_split:
                movq $0, vk64_pipeSplit(%rip)
                movq $0, vk64_pl5(%rip)
                movq $0, vk64_dset5(%rip)
                leaq .Lvkv_sovsplit(%rip), %rdi
                call getenv@PLT
                testq %rax, %rax
                jz .Lvk64_ic_pools
                movq %rax, %rdi
                call vk64_read_file
                testq %rax, %rax
                jz .Lvk64_ic_pools
                movq %rax, %rbx
                movq %rdx, 8(%rsp)
                leaq 4544+vk64_scratch(%rip), %rdi
                movl $16, 0(%rdi)
                movq $0, 8(%rdi)
                movl $0, 16(%rdi)
                movq 8(%rsp), %rax
                movq %rax, 24(%rdi)
                movq %rbx, 32(%rdi)
                movl $0, 0(%rsp)                   # smSplit out
                movq g_vk64_vkCreateShaderModule(%rip), %rax
                movq vk64_dev(%rip), %rdi
                leaq 4544+vk64_scratch(%rip), %rsi
                xorl %edx, %edx
                movq %rsp, %rcx
                call *%rax
                movq %rbx, %rdi
                call free@PLT
                testl %eax, %eax
                jnz .Lvk64_ic_pools                # não-fatal
                // binds5 @+4608 (5×24B)
                leaq 4608+vk64_scratch(%rip), %rdi
                xorl %eax, %eax
            .Lvk64_ic_b5:
                cmpl $5, %eax
                jge .Lvk64_ic_b5d
                movl %eax, %ecx
                imull $24, %ecx, %ecx
                movslq %ecx, %rcx
                leaq 4608+vk64_scratch(%rcx), %rdx
                movl %eax, 0(%rdx)
                movl $7, 4(%rdx)
                movl $1, 8(%rdx)
                movl $32, 12(%rdx)
                movq $0, 16(%rdx)
                incl %eax
                jmp .Lvk64_ic_b5
            .Lvk64_ic_b5d:
                leaq 4736+vk64_scratch(%rip), %rdi
                movl $32, 0(%rdi)
                movq $0, 8(%rdi)
                movl $0, 16(%rdi)
                movl $5, 20(%rdi)
                leaq 4608+vk64_scratch(%rip), %rax
                movq %rax, 24(%rdi)
                movl $0, 0(%rsp)
                movq g_vk64_vkCreateDescriptorSetLayout(%rip), %rax
                movq vk64_dev(%rip), %rdi
                leaq 4736+vk64_scratch(%rip), %rsi
                xorl %edx, %edx
                movq %rsp, %rcx
                call *%rax
                testl %eax, %eax
                jnz .Lvk64_ic_pools                # não-fatal
                movq 0(%rsp), %rax
                movq %rax, vk64_dsl5(%rip)
                // pl5: mesmo pcr @4768
                leaq 4800+vk64_scratch(%rip), %rdi
                movl $30, 0(%rdi)
                movq $0, 8(%rdi)
                movl $0, 16(%rdi)
                movl $1, 20(%rdi)
                leaq vk64_dsl5(%rip), %rax
                movq %rax, 24(%rdi)
                movl $1, 32(%rdi)
                leaq 4768+vk64_scratch(%rip), %rax
                movq %rax, 40(%rdi)
                movl $0, 0(%rsp)
                movq g_vk64_vkCreatePipelineLayout(%rip), %rax
                movq vk64_dev(%rip), %rdi
                leaq 4800+vk64_scratch(%rip), %rsi
                xorl %edx, %edx
                movq %rsp, %rcx
                call *%rax
                testl %eax, %eax
                jnz .Lvk64_ic_pools
                movq 0(%rsp), %rax
                movq %rax, vk64_pl5(%rip)
                // stage/pci (reuso 4864/4928) — smSplit em 0(%rsp)
                leaq 4864+vk64_scratch(%rip), %rdi
                movl $18, 0(%rdi)
                movq $0, 8(%rdi)
                movl $0, 16(%rdi)
                movl $32, 20(%rdi)
                movq 0(%rsp), %rax
                movq %rax, 24(%rdi)
                leaq .Lvkv_main(%rip), %rax
                movq %rax, 32(%rdi)
                movq $0, 40(%rdi)
                leaq 4928+vk64_scratch(%rip), %rdi
                movl $29, 0(%rdi)
                movq $0, 8(%rdi)
                movl $0, 16(%rdi)
                leaq 4864+vk64_scratch(%rip), %rax
                movq %rax, %rsi
                movq 0(%rsi), %rcx
                movq 8(%rsi), %rdx
                movq %rcx, 24(%rdi)
                movq %rdx, 32(%rdi)
                movq 16(%rsi), %rcx
                movq 24(%rsi), %rdx
                movq %rcx, 40(%rdi)
                movq %rdx, 48(%rdi)
                movq 32(%rsi), %rcx
                movq 40(%rsi), %rdx
                movq %rcx, 56(%rdi)
                movq %rdx, 64(%rdi)
                movq vk64_pl5(%rip), %rax
                movq %rax, 72(%rdi)
                movq $0, 80(%rdi)
                movq $0, 88(%rdi)
                movq $0, 264(%rsp)
                movq g_vk64_vkCreateComputePipelines(%rip), %rax
                movq vk64_dev(%rip), %rdi
                xorl %esi, %esi
                movl $1, %edx
                leaq 4928+vk64_scratch(%rip), %rcx
                xorl %r8d, %r8d
                leaq 264(%rsp), %r9
                call *%rax
                testl %eax, %eax
                jnz .Lvk64_ic_pools
                movq 264(%rsp), %rax
                movq %rax, vk64_pipeSplit(%rip)
                // dpool5: 1 poolSize {STORAGE_BUFFER(6), 5}, maxSets=1
                leaq 5024+vk64_scratch(%rip), %rdi
                movl $7, 0(%rdi)
                movl $5, 4(%rdi)
                leaq 5032+vk64_scratch(%rip), %rdi
                movl $33, 0(%rdi)
                movq $0, 8(%rdi)
                movl $0, 16(%rdi)
                movl $1, 20(%rdi)
                movl $1, 24(%rdi)
                leaq 5024+vk64_scratch(%rip), %rax
                movq %rax, 32(%rdi)
                movl $0, 0(%rsp)
                movq g_vk64_vkCreateDescriptorPool(%rip), %rax
                movq vk64_dev(%rip), %rdi
                leaq 5032+vk64_scratch(%rip), %rsi
                xorl %edx, %edx
                movq %rsp, %rcx
                call *%rax
                testl %eax, %eax
                jnz .Lvk64_ic_pools
                movq 0(%rsp), %rax
                movq %rax, vk64_dpool5(%rip)
                // dset5: allocate {dpool5, 1, &dsl5}
                leaq 5072+vk64_scratch(%rip), %rdi
                movl $34, 0(%rdi)
                movq $0, 8(%rdi)
                movq vk64_dpool5(%rip), %rax
                movq %rax, 16(%rdi)
                movl $1, 24(%rdi)
                leaq vk64_dsl5(%rip), %rax
                movq %rax, 32(%rdi)
                movq $0, 264(%rsp)                 # dset5 out
                movq g_vk64_vkAllocateDescriptorSets(%rip), %rax
                movq vk64_dev(%rip), %rdi
                leaq 5072+vk64_scratch(%rip), %rsi
                leaq 264(%rsp), %rdx
                call *%rax
                // (rc ignorado: o wrunsp checa dset5 != 0)
                movq 264(%rsp), %rax
                movq %rax, vk64_dset5(%rip)
            """);
    }

    // dpool/dset (3 binds) + cpool/cmd/fence + inited=1.
    private static void emitInitCommonPools(StringBuilder sb) {
        sb.append("""
            .Lvk64_ic_pools:
                // dpool: poolSize {6, nbinds}
                leaq 5024+vk64_scratch(%rip), %rdi
                movl $7, 0(%rdi)
                movl %r13d, 4(%rdi)
                leaq 5032+vk64_scratch(%rip), %rdi
                movl $33, 0(%rdi)
                movq $0, 8(%rdi)
                movl $0, 16(%rdi)
                movl $1, 20(%rdi)
                movl $1, 24(%rdi)
                leaq 5024+vk64_scratch(%rip), %rax
                movq %rax, 32(%rdi)
                movl $0, 0(%rsp)
                movq g_vk64_vkCreateDescriptorPool(%rip), %rax
                movq vk64_dev(%rip), %rdi
                leaq 5032+vk64_scratch(%rip), %rsi
                xorl %edx, %edx
                movq %rsp, %rcx
                call *%rax
                testl %eax, %eax
                jnz .Lvk64_ic_ck_dpool
                movq 0(%rsp), %rax
                movq %rax, vk64_dpool(%rip)
                leaq 5072+vk64_scratch(%rip), %rdi
                movl $34, 0(%rdi)
                movq $0, 8(%rdi)
                movq vk64_dpool(%rip), %rax
                movq %rax, 16(%rdi)
                movl $1, 24(%rdi)
                leaq vk64_dsl(%rip), %rax
                movq %rax, 32(%rdi)
                movl $0, 0(%rsp)
                movq g_vk64_vkAllocateDescriptorSets(%rip), %rax
                movq vk64_dev(%rip), %rdi
                leaq 5072+vk64_scratch(%rip), %rsi
                movq %rsp, %rdx
                call *%rax
                testl %eax, %eax
                jnz .Lvk64_ic_ck_dset
                movq 0(%rsp), %rax
                movq %rax, vk64_dset(%rip)
                // cpool
                leaq 5120+vk64_scratch(%rip), %rdi
                movl $39, 0(%rdi)
                movq $0, 8(%rdi)
                movl $0, 16(%rdi)
                movl vk64_qfam(%rip), %eax
                movl %eax, 20(%rdi)
                movl $0, 0(%rsp)
                movq g_vk64_vkCreateCommandPool(%rip), %rax
                movq vk64_dev(%rip), %rdi
                leaq 5120+vk64_scratch(%rip), %rsi
                xorl %edx, %edx
                movq %rsp, %rcx
                call *%rax
                testl %eax, %eax
                jnz .Lvk64_ic_ck_cpool
                movq 0(%rsp), %rax
                movq %rax, vk64_cpool(%rip)
                // cmd buffer (PRIMARY=0, count=1)
                leaq 5152+vk64_scratch(%rip), %rdi
                movl $40, 0(%rdi)
                movq $0, 8(%rdi)
                movq vk64_cpool(%rip), %rax
                movq %rax, 16(%rdi)
                movl $0, 24(%rdi)
                movl $1, 28(%rdi)
                movl $0, 0(%rsp)
                leaq 0(%rsp), %rax
                movq %rax, 32(%rdi)                # pCommandBuffers = &out
                movq g_vk64_vkAllocateCommandBuffers(%rip), %rax
                movq vk64_dev(%rip), %rdi
                leaq 5152+vk64_scratch(%rip), %rsi
                movq %rsp, %rdx
                call *%rax
                testl %eax, %eax
                jnz .Lvk64_ic_ck_cmdbuf
                movq 0(%rsp), %rax
                movq %rax, vk64_cmd(%rip)
                // fence
                leaq 5200+vk64_scratch(%rip), %rdi
                movl $8, 0(%rdi)
                movq $0, 8(%rdi)
                movl $0, 16(%rdi)
                movl $0, 0(%rsp)
                movq g_vk64_vkCreateFence(%rip), %rax
                movq vk64_dev(%rip), %rdi
                leaq 5200+vk64_scratch(%rip), %rsi
                xorl %edx, %edx
                movq %rsp, %rcx
                call *%rax
                testl %eax, %eax
                jnz .Lvk64_ic_ck_fence
                movq 0(%rsp), %rax
                movq %rax, vk64_fence(%rip)
                // dset12: 2º pool com o mesmo dsl de 3 binds (o
                // dispatch64 usa buffers a/b/c próprios)
                leaq 5024+vk64_scratch(%rip), %rdi
                movl $7, 0(%rdi)
                movl $3, 4(%rdi)
                leaq 5032+vk64_scratch(%rip), %rdi
                movl $33, 0(%rdi)
                movq $0, 8(%rdi)
                movl $0, 16(%rdi)
                movl $1, 20(%rdi)
                movl $1, 24(%rdi)
                leaq 5024+vk64_scratch(%rip), %rax
                movq %rax, 32(%rdi)
                movl $0, 0(%rsp)
                movq g_vk64_vkCreateDescriptorPool(%rip), %rax
                movq vk64_dev(%rip), %rdi
                leaq 5032+vk64_scratch(%rip), %rsi
                xorl %edx, %edx
                movq %rsp, %rcx
                call *%rax
                testl %eax, %eax
                jnz .Lvk64_ic_ck_dpool
                movq 0(%rsp), %rax
                movq %rax, vk64_dpool12(%rip)
                leaq 5072+vk64_scratch(%rip), %rdi
                movl $34, 0(%rdi)
                movq $0, 8(%rdi)
                movq vk64_dpool12(%rip), %rax
                movq %rax, 16(%rdi)
                movl $1, 24(%rdi)
                leaq vk64_dsl(%rip), %rax
                movq %rax, 32(%rdi)
                movl $0, 0(%rsp)
                movq g_vk64_vkAllocateDescriptorSets(%rip), %rax
                movq vk64_dev(%rip), %rdi
                leaq 5072+vk64_scratch(%rip), %rsi
                movq %rsp, %rdx
                call *%rax
                testl %eax, %eax
                jnz .Lvk64_ic_ck_dset
                movq 0(%rsp), %rax
                movq %rax, vk64_dset12(%rip)
                movl $1, vk64_inited(%rip)
                leaq .Lvkv_ok(%rip), %rax
                movq %rax, %rdi
                call vk64_fail0                    # errbuf="ok" (retorna 1, ignora)
                xorl %eax, %eax                    # ok!
                addq $288, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lvk64_ic_ck_dpool:
                leaq .Lvkv_e_dpool(%rip), %rdi
                movl %eax, %esi
                call vk64_fail
                jmp .Lvk64_ic_ret1
            .Lvk64_ic_ck_dset:
                leaq .Lvkv_e_dset(%rip), %rdi
                movl %eax, %esi
                call vk64_fail
                jmp .Lvk64_ic_ret1
            .Lvk64_ic_ck_cpool:
                leaq .Lvkv_e_cpool(%rip), %rdi
                movl %eax, %esi
                call vk64_fail
                jmp .Lvk64_ic_ret1
            .Lvk64_ic_ck_cmdbuf:
                leaq .Lvkv_e_cmdbuf(%rip), %rdi
                movl %eax, %esi
                call vk64_fail
                jmp .Lvk64_ic_ret1
            .Lvk64_ic_ck_fence:
                leaq .Lvkv_e_fence(%rip), %rdi
                movl %eax, %esi
                call vk64_fail
                jmp .Lvk64_ic_ret1
            """);
    }

    // ───────────────────── submit de dispatch ────────────────────────
    // vk64_submit(rdi=pipe, rsi=pl, rdx=dset, rcx=pcs(24B), r8d=wgX)
    // → 0 ok / -5 (errbuf). Usa o cmd/fence globais.
    private static void emitSubmitHelper(StringBuilder sb) {
        sb.append("""
            .type vk64_submit, @function
            vk64_submit:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $112, %rsp
                movq %rdi, %rbx                    # pipe
                movq %rsi, %r12                    # pl
                movq %rdx, %r13                    # dset
                movq %rcx, %r14                    # pcs
                movl %r8d, %r15d                   # wgX
                // VkCommandBufferBeginInfo @+5232: sType=42, flags=ONE_TIME(1)
                leaq 5232+vk64_scratch(%rip), %rdi
                movl $42, 0(%rdi)
                movq $0, 8(%rdi)
                movl $1, 16(%rdi)
                movq $0, 24(%rdi)
                movq g_vk64_vkBeginCommandBuffer(%rip), %rax
                movq vk64_cmd(%rip), %rdi
                leaq 5232+vk64_scratch(%rip), %rsi
                call *%rax
                testl %eax, %eax
                jnz .Lvk64_sub_err5
                movq g_vk64_vkCmdBindPipeline(%rip), %rax
                movq vk64_cmd(%rip), %rdi
                movl $1, %esi                      # VK_PIPELINE_BIND_POINT_COMPUTE
                movq %rbx, %rdx
                call *%rax
                movq g_vk64_vkCmdBindDescriptorSets(%rip), %rax
                movq vk64_cmd(%rip), %rdi
                movl $1, %esi                      # COMPUTE bind point
                movq %r12, %rdx                    # layout (pl)
                xorl %ecx, %ecx                    # firstSet = 0
                movl $1, %r8d                      # descriptorSetCount = 1
                leaq vk64_dset(%rip), %r9          # pDescriptorSets = &global
                pushq $0                           # pDynamicOffsets (8º arg)
                pushq $0
                call *%rax
                addq $16, %rsp
                // push 24B: copia pcs p/ +5296 e chama
                movq 0(%r14), %rax
                movq %rax, 5296+vk64_scratch(%rip)
                movq 8(%r14), %rax
                movq %rax, 5304+vk64_scratch(%rip)
                movq 16(%r14), %rax
                movq %rax, 5312+vk64_scratch(%rip)
                movq g_vk64_vkCmdPushConstants(%rip), %rax
                movq vk64_cmd(%rip), %rdi
                movq %r12, %rsi
                movl $32, %edx                     # COMPUTE stage
                xorl %ecx, %ecx                    # offset 0
                movl $24, %r8d
                leaq 5296+vk64_scratch(%rip), %r9
                call *%rax
                movq g_vk64_vkCmdDispatch(%rip), %rax
                movq vk64_cmd(%rip), %rdi
                movl %r15d, %esi
                movl $1, %edx
                movl $1, %ecx
                call *%rax
                movq g_vk64_vkEndCommandBuffer(%rip), %rax
                movq vk64_cmd(%rip), %rdi
                call *%rax
                testl %eax, %eax
                jnz .Lvk64_sub_err5
                // VkSubmitInfo @+5328: sType=4, commandBufferCount=1,
                // pCommandBuffers=&cmd
                leaq 5328+vk64_scratch(%rip), %rdi
                movl $4, 0(%rdi)
                movq $0, 8(%rdi)
                movl $0, 16(%rdi)
                movq $0, 24(%rdi)
                movq $0, 32(%rdi)
                movl $1, 40(%rdi)
                leaq vk64_cmd(%rip), %rax
                movq %rax, 48(%rdi)
                movl $0, 56(%rdi)
                movq $0, 64(%rdi)
                movq g_vk64_vkQueueSubmit(%rip), %rax
                movq vk64_queue(%rip), %rdi
                movl $1, %esi
                leaq 5328+vk64_scratch(%rip), %rdx
                movq vk64_fence(%rip), %rcx
                call *%rax
                testl %eax, %eax
                jnz .Lvk64_sub_err5
                movq g_vk64_vkWaitForFences(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movl $1, %esi
                leaq vk64_fence(%rip), %rdx
                movl $1, %ecx                      # VK_TRUE
                movq $60000000000, %r8             # 60s
                call *%rax
                testl %eax, %eax
                jnz .Lvk64_sub_err5
                movq g_vk64_vkResetFences(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movl $1, %esi
                leaq vk64_fence(%rip), %rdx
                call *%rax
                xorl %eax, %eax
                addq $112, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lvk64_sub_err5:
                leaq .Lvkv_e_begin(%rip), %rdi     # "begin" genérico (rc no errbuf)
                movl %eax, %esi
                call vk64_fail
                movl $-5, %eax
                addq $112, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }

    // vk64_write_desc(rdi=dset, rsi=dbi(24B×n), edx=n) — wds @+5424
    // (64B cada), dbi já preenchido pelo caller em +5424+320.
    private static void emitWriteDesc(StringBuilder sb) {
        sb.append("""
            .type vk64_write_desc, @function
            vk64_write_desc:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx                    # dset
                movq %rsi, %r12                    # dbi base
                movl %edx, %r13d                   # n
                leaq 5424+vk64_scratch(%rip), %rdi
                xorl %eax, %eax
            .Lvk64_wd_loop:
                cmpl %r13d, %eax
                jge .Lvk64_wd_done
                movl %eax, %ecx
                shll $6, %ecx                      # i*64
                movslq %ecx, %rcx
                leaq 5424+vk64_scratch(%rcx), %rdx
                movl $35, 0(%rdx)                  # sType WRITE_DESCRIPTOR_SET
                movq $0, 8(%rdx)
                movq %rbx, 16(%rdx)                # dstSet
                movl %eax, 24(%rdx)                # dstBinding = i
                movl $0, 28(%rdx)
                movl $1, 32(%rdx)                  # count
                movl $7, 36(%rdx)                  # STORAGE_BUFFER
                movq $0, 40(%rdx)
                # pBufferInfo = dbi + i*24 (rax = i: usar rcx 64-bit)
                movl %eax, %ecx
                imull $24, %ecx, %ecx
                movslq %ecx, %rcx
                addq %rcx, %r12
                movq %r12, 48(%rdx)
                movq $0, 56(%rdx)
                subq %rcx, %r12                   # restaura o dbi base
                incl %eax
                jmp .Lvk64_wd_loop
            .Lvk64_wd_done:
                movq g_vk64_vkUpdateDescriptorSets(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movl %r13d, %esi
                leaq 5424+vk64_scratch(%rip), %rdx
                xorl %ecx, %ecx
                xorl %r8d, %r8d
                call *%rax
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }

    // ───────────────────── funções exportadas ────────────────────────
    // kof_mv64_set_shape(rdi=m, rsi=k) — dimensiona W[m×k] + x + y e
    // escreve o desc set (3 binds). Retorna 0/-1/-2.
    private static void emitSetShape(StringBuilder sb) {
        sb.append("""
            .globl kof_mv64_set_shape
            .type kof_mv64_set_shape, @function

            // vk64_ensure_init: inicia a cadeia (3 binds, SPV64) se
            // ainda não iniciada. → 0 ok / 1 falha
            .type vk64_ensure_init, @function
            vk64_ensure_init:
                cmpl $0, vk64_inited(%rip)
                je .Lvk64_ens_go
                xorl %eax, %eax
                ret
            .Lvk64_ens_ret_bad:
                movl $1, %eax
                addq $8, %rsp
                ret
            .Lvk64_ens_go:
                subq $8, %rsp                      # alinha (2 pushes antes)
                leaq .Lvkv_sov64(%rip), %rdi
                call getenv@PLT
                testq %rax, %rax
                jz .Lvk64_ens_bad
                movq %rax, %rdi
                movl $3, %esi
                call vk64_init_common
                addq $8, %rsp
                ret
            .Lvk64_ens_bad:
                leaq .Lvkv_e_spvopen(%rip), %rdi
                call vk64_fail0
                movl $1, %eax
                addq $8, %rsp
                ret

            kof_mv64_set_shape:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                subq $56, %rsp
                movl %edi, %r12d                   # m
                movl %esi, %r13d                   # k
                call vk64_ensure_init
                testl %eax, %eax
                jz .Lvk64_ss_inited
                movl $-1, %eax
                jmp .Lvk64_ss_ret
            .Lvk64_ss_inited:
                movl %r12d, vk64_curM(%rip)
                movl %r13d, vk64_curK(%rip)
                // (long)m*k vs wcap
                movl %r12d, %eax
                movl %r13d, %ecx
                imull %ecx, %eax
                cltq                               # sign-extend eax→rax? m*k i64
                movl %r12d, %eax
                imull %r13d, %eax
                movslq %eax, %rax                  # elems (int m*k pode estourar p/ shapes gigantes — kf cuida)
                cmpq vk64_wcap(%rip), %rax
                jle .Lvk64_ss_wok
                // free + realloc W
                movq vk64_wmap(%rip), %rdi
                testq %rdi, %rdi
                jz .Lvk64_ss_wskip_unmap
                movq g_vk64_vkUnmapMemory(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_wmem(%rip), %rsi
                call *%rax
            .Lvk64_ss_wskip_unmap:
                movq vk64_wbuf(%rip), %rdi
                testq %rdi, %rdi
                jz .Lvk64_ss_wskip_buf
                movq g_vk64_vkDestroyBuffer(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_wbuf(%rip), %rsi
                xorl %edx, %edx
                call *%rax
            .Lvk64_ss_wskip_buf:
                movq vk64_wmem(%rip), %rdi
                testq %rdi, %rdi
                jz .Lvk64_ss_wskip_mem
                movq g_vk64_vkFreeMemory(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_wmem(%rip), %rsi
                xorl %edx, %edx
                call *%rax
            .Lvk64_ss_wskip_mem:
                movq $0, vk64_wbuf(%rip)
                movq $0, vk64_wmem(%rip)
                movq $0, vk64_wmap(%rip)
                movl %r12d, %eax
                imull %r13d, %eax
                movslq %eax, %rax
                shlq $3, %rax
                movq %rax, 0(%rsp)                 # bytes
                movq %rax, %rdi
                leaq vk64_wbuf(%rip), %rsi
                leaq vk64_wmem(%rip), %rdx
                leaq vk64_wmap(%rip), %rcx
                call vk64_alloc_buffer
                testl %eax, %eax
                jz .Lvk64_ss_walloc_ok
                movl $-2, %eax
                jmp .Lvk64_ss_ret
            .Lvk64_ss_walloc_ok:
                movl %r12d, %eax
                imull %r13d, %eax
                movslq %eax, %rax
                movq %rax, vk64_wcap(%rip)
            .Lvk64_ss_wok:
                // x: k elems i64
                movl %r13d, %eax
                movslq %eax, %rax
                cmpq vk64_xcap(%rip), %rax
                jle .Lvk64_ss_xok
                movq vk64_xmap(%rip), %rdi
                testq %rdi, %rdi
                jz .Lvk64_ss_xskip0
                movq g_vk64_vkUnmapMemory(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_xmem(%rip), %rsi
                call *%rax
            .Lvk64_ss_xskip0:
                movq vk64_xbuf(%rip), %rdi
                testq %rdi, %rdi
                jz .Lvk64_ss_xskip1
                movq g_vk64_vkDestroyBuffer(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_xbuf(%rip), %rsi
                xorl %edx, %edx
                call *%rax
            .Lvk64_ss_xskip1:
                movq vk64_xmem(%rip), %rdi
                testq %rdi, %rdi
                jz .Lvk64_ss_xskip2
                movq g_vk64_vkFreeMemory(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_xmem(%rip), %rsi
                xorl %edx, %edx
                call *%rax
            .Lvk64_ss_xskip2:
                movq $0, vk64_xbuf(%rip)
                movq $0, vk64_xmem(%rip)
                movq $0, vk64_xmap(%rip)
                movl %r13d, %eax
                movslq %eax, %rax
                shlq $3, %rax
                movq %rax, %rdi
                leaq vk64_xbuf(%rip), %rsi
                leaq vk64_xmem(%rip), %rdx
                leaq vk64_xmap(%rip), %rcx
                call vk64_alloc_buffer
                testl %eax, %eax
                jz .Lvk64_ss_xalloc_ok
                movl $-2, %eax
                jmp .Lvk64_ss_ret
            .Lvk64_ss_xalloc_ok:
                movl %r13d, %eax
                movslq %eax, %rax
                movq %rax, vk64_xcap(%rip)
            .Lvk64_ss_xok:
                // y: m elems i64
                movl %r12d, %eax
                movslq %eax, %rax
                cmpq vk64_ycap(%rip), %rax
                jle .Lvk64_ss_yok
                movq vk64_ymap(%rip), %rdi
                testq %rdi, %rdi
                jz .Lvk64_ss_yskip0
                movq g_vk64_vkUnmapMemory(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_ymem(%rip), %rsi
                call *%rax
            .Lvk64_ss_yskip0:
                movq vk64_ybuf(%rip), %rdi
                testq %rdi, %rdi
                jz .Lvk64_ss_yskip1
                movq g_vk64_vkDestroyBuffer(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_ybuf(%rip), %rsi
                xorl %edx, %edx
                call *%rax
            .Lvk64_ss_yskip1:
                movq vk64_ymem(%rip), %rdi
                testq %rdi, %rdi
                jz .Lvk64_ss_yskip2
                movq g_vk64_vkFreeMemory(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_ymem(%rip), %rsi
                xorl %edx, %edx
                call *%rax
            .Lvk64_ss_yskip2:
                movq $0, vk64_ybuf(%rip)
                movq $0, vk64_ymem(%rip)
                movq $0, vk64_ymap(%rip)
                movl %r12d, %eax
                movslq %eax, %rax
                shlq $3, %rax
                movq %rax, %rdi
                leaq vk64_ybuf(%rip), %rsi
                leaq vk64_ymem(%rip), %rdx
                leaq vk64_ymap(%rip), %rcx
                call vk64_alloc_buffer
                testl %eax, %eax
                jz .Lvk64_ss_yalloc_ok
                movl $-2, %eax
                jmp .Lvk64_ss_ret
            .Lvk64_ss_yalloc_ok:
                movl %r12d, %eax
                movslq %eax, %rax
                movq %rax, vk64_ycap(%rip)
            .Lvk64_ss_yok:
                // desc set: dbi[3] @+5424+320, wds via helper
                leaq 5424+320+vk64_scratch(%rip), %rdi
                movq vk64_wbuf(%rip), %rax
                movq %rax, 0(%rdi)
                movq $0, 8(%rdi)
                movq vk64_wcap(%rip), %rax
                shlq $3, %rax
                movq %rax, 16(%rdi)
                movq vk64_xbuf(%rip), %rax
                movq %rax, 24(%rdi)
                movq $0, 32(%rdi)
                movq vk64_xcap(%rip), %rax
                shlq $3, %rax
                movq %rax, 40(%rdi)
                movq vk64_ybuf(%rip), %rax
                movq %rax, 48(%rdi)
                movq $0, 56(%rdi)
                movq vk64_ycap(%rip), %rax
                shlq $3, %rax
                movq %rax, 64(%rdi)
                movq vk64_dset(%rip), %rdi
                leaq 5424+320+vk64_scratch(%rip), %rsi
                movl $3, %edx
                call vk64_write_desc
                xorl %eax, %eax
            .Lvk64_ss_ret:
                addq $56, %rsp
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }

    // kof_mv64_load_w(rdi=w arr, esi=m, edx=k) — memcpy p/ wmap
    private static void emitLoadW(StringBuilder sb) {
        sb.append("""
            .globl kof_mv64_load_w
            .type kof_mv64_load_w, @function
            kof_mv64_load_w:
                cmpl $0, vk64_inited(%rip)
                jne .Lvk64_lw1
                movl $-1, %eax
                ret
            .Lvk64_lw1:
                cmpq $0, vk64_wmap(%rip)
                jne .Lvk64_lw2
                movl $-2, %eax
                ret
            .Lvk64_lw2:
                movl %esi, %eax
                movl %edx, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                cmpq vk64_wcap(%rip), %rax
                jle .Lvk64_lw3
                movl $-3, %eax
                ret
            .Lvk64_lw3:
                shlq $3, %rax                      # bytes = m*k*8
                movq %rax, %rdx
                leaq 24(%rdi), %rsi                # w data
                movq vk64_wmap(%rip), %rdi
                call memcpy@PLT
                xorl %eax, %eax
                ret
            """);
    }

    // kof_mv64_matvec(rdi=x arr, rsi=y arr, edx=m, ecx=k)
    private static void emitMatvec(StringBuilder sb) {
        sb.append("""
            .globl kof_mv64_matvec
            .type kof_mv64_matvec, @function
            kof_mv64_matvec:
                pushq %rbx
                pushq %r12
                subq $40, %rsp
                movl %edx, %ebx                    # m
                movl %ecx, %r12d                   # k
                movq %rsi, 32(%rsp)                # y (rsi clobberado)
                cmpl $0, vk64_inited(%rip)
                jne .Lvk64_mv1
                movl $-1, %eax
                jmp .Lvk64_mv_ret
            .Lvk64_mv1:
                movl vk64_curM(%rip), %eax
                cmpl %ebx, %eax
                jne .Lvk64_mv_shape
                movl vk64_curK(%rip), %eax
                cmpl %r12d, %eax
                je .Lvk64_mv2
            .Lvk64_mv_shape:
                movl $-2, %eax
                jmp .Lvk64_mv_ret
            .Lvk64_mv2:
                cmpq $0, vk64_wmap(%rip)
                jne .Lvk64_mv3
                movl $-3, %eax
                jmp .Lvk64_mv_ret
            .Lvk64_mv3:
                // x → xmap; ymap = 0
                movl %r12d, %eax
                movslq %eax, %rax
                shlq $3, %rax
                movq %rax, %rdx
                leaq 24(%rdi), %rsi
                movq vk64_xmap(%rip), %rdi
                call memcpy@PLT
                movl %ebx, %eax
                movslq %eax, %rax
                shlq $3, %rax
                movq %rax, %rdx
                xorl %esi, %esi
                movq vk64_ymap(%rip), %rdi
                call memset@PLT
                // pcs[2] = {m, k} (8B — os shaders antigos: push 8B)
                movl %ebx, 5296+vk64_scratch(%rip)
                movl %r12d, 5300+vk64_scratch(%rip)
                // push custom (8B) — o helper faz 24B: ok, o shader lê 8
                movq vk64_pipe(%rip), %rdi
                movq vk64_pl(%rip), %rsi
                movq vk64_dset12(%rip), %rdx      # TESTE: dset12
                leaq 5296+vk64_scratch(%rip), %rcx
                movl %ebx, %r8d
                call vk64_submit
                // readback y ← ymap
                movl %ebx, %eax
                movslq %eax, %rax
                shlq $3, %rax
                movq %rax, %rdx
                movq vk64_ymap(%rip), %rsi
                movq 32(%rsp), %rdi
                call memcpy@PLT
            .Lvk64_mv_ret:
                addq $40, %rsp
                popq %r12
                popq %rbx
                ret
            """);
    }

    // kof_mv64_wput(rdi=id, rsi=w arr, edx=m, ecx=k)
    private static void emitWput(StringBuilder sb) {
        sb.append("""
            .globl kof_mv64_wput
            .type kof_mv64_wput, @function
            kof_mv64_wput:
                pushq %rbx
                pushq %r12
                pushq %r13
                subq $48, %rsp
                movl %edi, %ebx                    # id
                movl %edx, %r12d                   # m
                movl %ecx, %r13d                   # k
                movq %rsi, 32(%rsp)                # w arr (rsi é clobberado)
                call vk64_ensure_init
                testl %eax, %eax
                jz .Lvk64_wp1
                movl $-1, %eax
                jmp .Lvk64_wp_ret
            .Lvk64_wp1:
                testl %ebx, %ebx
                js .Lvk64_wp_badid
                cmpl $192, %ebx
                jl .Lvk64_wp_idok
            .Lvk64_wp_badid:
                movl $-2, %eax
                jmp .Lvk64_wp_ret
            .Lvk64_wp_idok:
                movl %r12d, %eax
                movl %r13d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                testq %rax, %rax
                jle .Lvk64_wp_neg
                movslq %ebx, %rcx
                shlq $3, %rcx
                cmpq vk64_wcaps(%rcx), %rax        # wcap[id] < m*k?
                jge .Lvk64_wp_copy
                // (re)aloca wbufs[id]
                movq vk64_wbufs(%rcx), %rdi
                testq %rdi, %rdi
                jz .Lvk64_wp_alloc
                movq vk64_wmaps(%rcx), %rdi
                testq %rdi, %rdi
                jz .Lvk64_wp_unmap_done
                movq g_vk64_vkUnmapMemory(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_wmems(%rcx), %rsi
                call *%rax
            .Lvk64_wp_unmap_done:
                movq vk64_wbufs(%rcx), %rdi
                testq %rdi, %rdi
                jz .Lvk64_wp_destroy_done
                movq g_vk64_vkDestroyBuffer(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_wbufs(%rcx), %rsi
                xorl %edx, %edx
                call *%rax
            .Lvk64_wp_destroy_done:
                movq vk64_wmems(%rcx), %rdi
                testq %rdi, %rdi
                jz .Lvk64_wp_free_done
                movq g_vk64_vkFreeMemory(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_wmems(%rcx), %rsi
                xorl %edx, %edx
                call *%rax
            .Lvk64_wp_free_done:
                movq $0, vk64_wbufs(%rcx)
                movq $0, vk64_wmems(%rcx)
                movq $0, vk64_wmaps(%rcx)
                movq $0, vk64_wcaps(%rcx)
            .Lvk64_wp_alloc:
                movl %r12d, %eax
                movl %r13d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                shlq $3, %rax
                movq %rax, %rdi
                movslq %ebx, %rax
                shlq $3, %rax
                leaq vk64_wbufs(%rax), %rsi
                leaq vk64_wmems(%rax), %rdx
                leaq vk64_wmaps(%rax), %rcx
                call vk64_alloc_buffer
                testl %eax, %eax
                jz .Lvk64_wp_alloc_ok
                movl $-4, %eax
                jmp .Lvk64_wp_ret
            .Lvk64_wp_alloc_ok:
                movl %r12d, %eax
                movl %r13d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                movslq %ebx, %rcx
                shlq $3, %rcx
                movq %rax, vk64_wcaps(%rcx)
            .Lvk64_wp_copy:
                movl %r12d, %eax
                movl %r13d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                shlq $3, %rax
                movq %rax, %rdx
                movq 32(%rsp), %rsi                # w arr
                leaq 24(%rsi), %rsi                # w data
                movslq %ebx, %rax
                shlq $3, %rax
                movq vk64_wmaps(%rax), %rdi
                call memcpy@PLT
                xorl %eax, %eax
            .Lvk64_wp_ret:
                addq $48, %rsp
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lvk64_wp_neg:
                movl $-3, %eax
                jmp .Lvk64_wp_ret
            """);
    }

    // kof_mv64_wrun(rdi=id, rsi=x arr, rdx=y arr, ecx=m, r8d=k, r9=div)
    private static void emitWrun(StringBuilder sb) {
        sb.append("""
            .globl kof_mv64_wrun
            .type kof_mv64_wrun, @function
            kof_mv64_wrun:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $64, %rsp
                movl %edi, %ebx                    # id
                movq %rsi, 40(%rsp)                # x arr
                movq %rdx, 48(%rsp)                # y arr
                movl %ecx, %r12d                   # m
                movl %r8d, %r13d                   # k
                movq %r9, %r14                     # div
                call vk64_ensure_init
                testl %eax, %eax
                jz .Lvk64_wr1
                movl $-1, %eax
                jmp .Lvk64_wr_ret
            .Lvk64_wr1:
                testl %ebx, %ebx
                js .Lvk64_wr_badid
                cmpl $192, %ebx
                jl .Lvk64_wr2
            .Lvk64_wr_badid:
                movl $-2, %eax
                jmp .Lvk64_wr_ret
            .Lvk64_wr2:
                movslq %ebx, %rcx
                shlq $3, %rcx
                // wbufs[id] != 0 && wcap[id] >= m*k
                cmpq $0, vk64_wbufs(%rcx)
                je .Lvk64_wr_nobuf
                movl %r12d, %eax
                movl %r13d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                movslq %ebx, %rcx
                shlq $3, %rcx
                cmpq vk64_wcaps(%rcx), %rax
                jg .Lvk64_wr_nobuf
                jmp .Lvk64_wr_shape
            .Lvk64_wr_nobuf:
                movl $-3, %eax
                jmp .Lvk64_wr_ret
            .Lvk64_wr_shape:
                movl %r13d, %eax
                movslq %eax, %rax
                cmpq vk64_xcap(%rip), %rax
                jle .Lvk64_wr_xok
                movl %r12d, %edi
                movl %r13d, %esi
                call vk64_shape_xy                 # set_shape_internal
                testl %eax, %eax
                jz .Lvk64_wr_xok
                movl $-4, %eax
                jmp .Lvk64_wr_ret
            .Lvk64_wr_xok:
                // x → xmap; ymap = 0
                movl %r13d, %eax
                movslq %eax, %rax
                shlq $3, %rax
                movq %rax, %rdx
                movq 40(%rsp), %rsi
                leaq 24(%rsi), %rsi
                movq vk64_xmap(%rip), %rdi
                call memcpy@PLT
                movl %r12d, %eax
                movslq %eax, %rax
                shlq $3, %rax
                movq %rax, %rdx
                xorl %esi, %esi
                movq vk64_ymap(%rip), %rdi
                call memset@PLT
                // trace opcional
                leaq .Lvkv_trace(%rip), %rdi
                call getenv@PLT
                testq %rax, %rax
                jz .Lvk64_wr_rebind
                // (trace simplificado: o C imprimia x0/x1 — omitido)
            .Lvk64_wr_rebind:
                // dbi[3]: w=buffer[id], x, y
                movslq %ebx, %rax
                shlq $3, %rax
                leaq 5424+320+vk64_scratch(%rip), %rdi
                movq vk64_wbufs(%rax), %rcx
                movq %rcx, 0(%rdi)
                movq $0, 8(%rdi)
                movq vk64_wcaps(%rax), %rcx
                shlq $3, %rcx
                movq %rcx, 16(%rdi)
                movq vk64_xbuf(%rip), %rcx
                movq %rcx, 24(%rdi)
                movq $0, 32(%rdi)
                movq vk64_xcap(%rip), %rcx
                shlq $3, %rcx
                movq %rcx, 40(%rdi)
                movq vk64_ybuf(%rip), %rcx
                movq %rcx, 48(%rdi)
                movq $0, 56(%rdi)
                movq vk64_ycap(%rip), %rcx
                shlq $3, %rcx
                movq %rcx, 64(%rdi)
                movq vk64_dset(%rip), %rdi
                leaq 5424+320+vk64_scratch(%rip), %rsi
                movl $3, %edx
                call vk64_write_desc
                // pcs[6] = {m, k, divId, 0, div lo/hi}
                movl %r12d, 5296+vk64_scratch(%rip)
                movl %r13d, 5300+vk64_scratch(%rip)
                movl $2, 5304+vk64_scratch(%rip)   # divId default
                movl $0, 5308+vk64_scratch(%rip)
                movq $1000000000, %rax
                cmpq %rax, %r14
                jne .Lvk64_wr_d1
                movl $0, 5304+vk64_scratch(%rip)
                jmp .Lvk64_wr_dset
            .Lvk64_wr_d1:
                movq $1000000, %rax
                cmpq %rax, %r14
                jne .Lvk64_wr_dset
                movl $1, 5304+vk64_scratch(%rip)
            .Lvk64_wr_dset:
                movq %r14, %rax
                movq %rax, 5312+vk64_scratch(%rip) # div i64 @pcs+16
                movq vk64_pipe(%rip), %rdi
                movq vk64_pl(%rip), %rsi
                movq vk64_dset(%rip), %rdx
                leaq 5296+vk64_scratch(%rip), %rcx
                movl %r12d, %r8d
                call vk64_submit
                testl %eax, %eax
                jnz .Lvk64_wr_ret
                // y ← ymap
                movl %r12d, %eax
                movslq %eax, %rax
                shlq $3, %rax
                movq %rax, %rdx
                movq vk64_ymap(%rip), %rsi
                movq 48(%rsp), %rdi
                addq $24, %rdi
                call memcpy@PLT
                xorl %eax, %eax
            .Lvk64_wr_ret:
                addq $64, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }

    // vk64_shape_xy(rdi=m, esi=k): dimensiona x[k], y[m] i64 → 0 ok
    private static void emitShapeXy(StringBuilder sb) {
        sb.append("""
            .type vk64_shape_xy, @function
            vk64_shape_xy:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                subq $40, %rsp
                movl %edi, %r12d                   # m
                movl %esi, %r13d                   # k
                movl %r13d, %eax
                movslq %eax, %rax
                cmpq vk64_xcap(%rip), %rax
                jle .Lvk64_sxy_xok
                movq vk64_xmap(%rip), %rdi
                testq %rdi, %rdi
                jz .Lvk64_sxy_xu
                movq g_vk64_vkUnmapMemory(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_xmem(%rip), %rsi
                call *%rax
            .Lvk64_sxy_xu:
                movq vk64_xbuf(%rip), %rdi
                testq %rdi, %rdi
                jz .Lvk64_sxy_xd
                movq g_vk64_vkDestroyBuffer(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_xbuf(%rip), %rsi
                xorl %edx, %edx
                call *%rax
            .Lvk64_sxy_xd:
                movq vk64_xmem(%rip), %rdi
                testq %rdi, %rdi
                jz .Lvk64_sxy_xf
                movq g_vk64_vkFreeMemory(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_xmem(%rip), %rsi
                xorl %edx, %edx
                call *%rax
            .Lvk64_sxy_xf:
                movq $0, vk64_xbuf(%rip)
                movq $0, vk64_xmem(%rip)
                movq $0, vk64_xmap(%rip)
                movl %r13d, %eax
                movslq %eax, %rax
                shlq $3, %rax
                movq %rax, %rdi
                leaq vk64_xbuf(%rip), %rsi
                leaq vk64_xmem(%rip), %rdx
                leaq vk64_xmap(%rip), %rcx
                call vk64_alloc_buffer
                testl %eax, %eax
                jz .Lvk64_sxy_xok2
                movl $-2, %eax
                jmp .Lvk64_sxy_ret
            .Lvk64_sxy_xok2:
                movl %r13d, %eax
                movslq %eax, %rax
                movq %rax, vk64_xcap(%rip)
            .Lvk64_sxy_xok:
                movl %r12d, %eax
                movslq %eax, %rax
                cmpq vk64_ycap(%rip), %rax
                jle .Lvk64_sxy_ok
                movq vk64_ymap(%rip), %rdi
                testq %rdi, %rdi
                jz .Lvk64_sxy_yu
                movq g_vk64_vkUnmapMemory(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_ymem(%rip), %rsi
                call *%rax
            .Lvk64_sxy_yu:
                movq vk64_ybuf(%rip), %rdi
                testq %rdi, %rdi
                jz .Lvk64_sxy_yd
                movq g_vk64_vkDestroyBuffer(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_ybuf(%rip), %rsi
                xorl %edx, %edx
                call *%rax
            .Lvk64_sxy_yd:
                movq vk64_ymem(%rip), %rdi
                testq %rdi, %rdi
                jz .Lvk64_sxy_yf
                movq g_vk64_vkFreeMemory(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_ymem(%rip), %rsi
                xorl %edx, %edx
                call *%rax
            .Lvk64_sxy_yf:
                movq $0, vk64_ybuf(%rip)
                movq $0, vk64_ymem(%rip)
                movq $0, vk64_ymap(%rip)
                movl %r12d, %eax
                movslq %eax, %rax
                shlq $3, %rax
                movq %rax, %rdi
                leaq vk64_ybuf(%rip), %rsi
                leaq vk64_ymem(%rip), %rdx
                leaq vk64_ymap(%rip), %rcx
                call vk64_alloc_buffer
                testl %eax, %eax
                jz .Lvk64_sxy_yok2
                movl $-2, %eax
                jmp .Lvk64_sxy_ret
            .Lvk64_sxy_yok2:
                movl %r12d, %eax
                movslq %eax, %rax
                movq %rax, vk64_ycap(%rip)
            .Lvk64_sxy_ok:
                xorl %eax, %eax
            .Lvk64_sxy_ret:
                addq $40, %rsp
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }

    // kof_mv64_wput32(rdi=id, rsi=w i32 arr, edx=m, ecx=k)
    private static void emitWput32(StringBuilder sb) {
        sb.append("""
            .globl kof_mv64_wput32
            .type kof_mv64_wput32, @function
            kof_mv64_wput32:
                pushq %rbx
                pushq %r12
                pushq %r13
                subq $48, %rsp
                movl %edi, %ebx                    # id
                movl %edx, %r12d                   # m
                movl %ecx, %r13d                   # k
                movq %rsi, 32(%rsp)                # w arr
                call vk64_ensure_init
                testl %eax, %eax
                jz .Lvk64_wp32_1
                movl $-1, %eax
                jmp .Lvk64_wp32_ret
            .Lvk64_wp32_1:
                testl %ebx, %ebx
                js .Lvk64_wp32_badid
                cmpl $192, %ebx
                jl .Lvk64_wp32_2
            .Lvk64_wp32_badid:
                movl $-2, %eax
                jmp .Lvk64_wp32_ret
            .Lvk64_wp32_2:
                movl %r12d, %eax
                movl %r13d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                testq %rax, %rax
                jle .Lvk64_wp32_neg
                movslq %ebx, %rcx
                shlq $3, %rcx
                cmpq vk64_wcaps32(%rcx), %rax
                jge .Lvk64_wp32_copy
                // (re)aloca
                movq vk64_wmaps32(%rcx), %rdi
                testq %rdi, %rdi
                jz .Lvk64_wp32_alloc
                movq g_vk64_vkUnmapMemory(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_wmems32(%rcx), %rsi
                call *%rax
            .Lvk64_wp32_alloc:
                movq vk64_wbufs32(%rcx), %rdi
                testq %rdi, %rdi
                jz .Lvk64_wp32_alloc2
                movq g_vk64_vkDestroyBuffer(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_wbufs32(%rcx), %rsi
                xorl %edx, %edx
                call *%rax
            .Lvk64_wp32_alloc2:
                movq vk64_wmems32(%rcx), %rdi
                testq %rdi, %rdi
                jz .Lvk64_wp32_alloc3
                movq g_vk64_vkFreeMemory(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_wmems32(%rcx), %rsi
                xorl %edx, %edx
                call *%rax
            .Lvk64_wp32_alloc3:
                movq $0, vk64_wbufs32(%rcx)
                movq $0, vk64_wmems32(%rcx)
                movq $0, vk64_wmaps32(%rcx)
                movq $0, vk64_wcaps32(%rcx)
                movl %r12d, %eax
                movl %r13d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                shlq $2, %rax                      # 4B/elem
                movq %rax, %rdi
                movslq %ebx, %rax
                shlq $3, %rax
                leaq vk64_wbufs32(%rax), %rsi
                leaq vk64_wmems32(%rax), %rdx
                leaq vk64_wmaps32(%rax), %rcx
                call vk64_alloc_buffer
                testl %eax, %eax
                jz .Lvk64_wp32_alloc4
                movl $-4, %eax
                jmp .Lvk64_wp32_ret
            .Lvk64_wp32_alloc4:
                movl %r12d, %eax
                movl %r13d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                movslq %ebx, %rcx
                shlq $3, %rcx
                movq %rax, vk64_wcaps32(%rcx)
            .Lvk64_wp32_copy:
                movl %r12d, %eax
                movl %r13d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                shlq $2, %rax
                movq %rax, %rdx
                movq 32(%rsp), %rsi
                leaq 24(%rsi), %rsi
                movslq %ebx, %rax
                shlq $3, %rax
                movq vk64_wmaps32(%rax), %rdi
                call memcpy@PLT
                xorl %eax, %eax
            .Lvk64_wp32_ret:
                addq $48, %rsp
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lvk64_wp32_neg:
                movl $-3, %eax
                jmp .Lvk64_wp32_ret
            """);
    }

    // kof_mv64_wrun32(rdi=id, rsi=x arr, rdx=y arr, ecx=m, r8d=k, r9=div)
    private static void emitWrun32(StringBuilder sb) {
        sb.append("""
            .globl kof_mv64_wrun32
            .type kof_mv64_wrun32, @function
            kof_mv64_wrun32:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $64, %rsp
                movl %edi, %ebx                    # id
                movq %rsi, 40(%rsp)                # x arr
                movq %rdx, 48(%rsp)                # y arr
                movl %ecx, %r12d                   # m
                movl %r8d, %r13d                   # k
                movq %r9, %r14                     # div
                call vk64_ensure_init
                testl %eax, %eax
                jz .Lvk64_wr32_1
                movl $-1, %eax
                jmp .Lvk64_wr32_ret
            .Lvk64_wr32_1:
                cmpq $0, vk64_pipe32(%rip)
                jne .Lvk64_wr32_2
                movl $-6, %eax                     # SPV w32 ausente
                jmp .Lvk64_wr32_ret
            .Lvk64_wr32_2:
                testl %ebx, %ebx
                js .Lvk64_wr32_bad
                cmpl $192, %ebx
                jl .Lvk64_wr32_3
            .Lvk64_wr32_bad:
                movl $-2, %eax
                jmp .Lvk64_wr32_ret
            .Lvk64_wr32_3:
                movslq %ebx, %rcx
                shlq $3, %rcx
                cmpq $0, vk64_wbufs32(%rcx)
                je .Lvk64_wr32_nobuf
                movl %r12d, %eax
                movl %r13d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                movslq %ebx, %rcx
                shlq $3, %rcx
                cmpq vk64_wcaps32(%rcx), %rax
                jg .Lvk64_wr32_nobuf
                jmp .Lvk64_wr32_x
            .Lvk64_wr32_nobuf:
                movl $-3, %eax
                jmp .Lvk64_wr32_ret
            .Lvk64_wr32_x:
                // xbuf32/ymap32 com dim k/m
                movl %r13d, %eax
                movslq %eax, %rax
                cmpq vk64_xcap32(%rip), %rax
                jle .Lvk64_wr32_xok
                movq vk64_xmap32(%rip), %rdi
                testq %rdi, %rdi
                jz .Lvk64_wr32_xu
                movq g_vk64_vkUnmapMemory(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_xmem32(%rip), %rsi
                call *%rax
            .Lvk64_wr32_xu:
                movq vk64_xbuf32(%rip), %rdi
                testq %rdi, %rdi
                jz .Lvk64_wr32_xd
                movq g_vk64_vkDestroyBuffer(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_xbuf32(%rip), %rsi
                xorl %edx, %edx
                call *%rax
            .Lvk64_wr32_xd:
                movq vk64_xmem32(%rip), %rdi
                testq %rdi, %rdi
                jz .Lvk64_wr32_xf
                movq g_vk64_vkFreeMemory(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_xmem32(%rip), %rsi
                xorl %edx, %edx
                call *%rax
            .Lvk64_wr32_xf:
                movq $0, vk64_xbuf32(%rip)
                movq $0, vk64_xmem32(%rip)
                movq $0, vk64_xmap32(%rip)
                movl %r13d, %eax
                movslq %eax, %rax
                shlq $3, %rax
                movq %rax, %rdi
                leaq vk64_xbuf32(%rip), %rsi
                leaq vk64_xmem32(%rip), %rdx
                leaq vk64_xmap32(%rip), %rcx
                call vk64_alloc_buffer
                testl %eax, %eax
                jz .Lvk64_wr32_xok2
                movl $-4, %eax
                jmp .Lvk64_wr32_ret
            .Lvk64_wr32_xok2:
                movl %r13d, %eax
                movslq %eax, %rax
                movq %rax, vk64_xcap32(%rip)
            .Lvk64_wr32_xok:
                movl %r12d, %eax
                movslq %eax, %rax
                cmpq vk64_ycap32(%rip), %rax
                jle .Lvk64_wr32_yok
                movq vk64_ymap32(%rip), %rdi
                testq %rdi, %rdi
                jz .Lvk64_wr32_yu
                movq g_vk64_vkUnmapMemory(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_ymem32(%rip), %rsi
                call *%rax
            .Lvk64_wr32_yu:
                movq vk64_ybuf32(%rip), %rdi
                testq %rdi, %rdi
                jz .Lvk64_wr32_yd
                movq g_vk64_vkDestroyBuffer(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_ybuf32(%rip), %rsi
                xorl %edx, %edx
                call *%rax
            .Lvk64_wr32_yd:
                movq vk64_ymem32(%rip), %rdi
                testq %rdi, %rdi
                jz .Lvk64_wr32_yf
                movq g_vk64_vkFreeMemory(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_ymem32(%rip), %rsi
                xorl %edx, %edx
                call *%rax
            .Lvk64_wr32_yf:
                movq $0, vk64_ybuf32(%rip)
                movq $0, vk64_ymem32(%rip)
                movq $0, vk64_ymap32(%rip)
                movl %r12d, %eax
                movslq %eax, %rax
                shlq $3, %rax
                movq %rax, %rdi
                leaq vk64_ybuf32(%rip), %rsi
                leaq vk64_ymem32(%rip), %rdx
                leaq vk64_ymap32(%rip), %rcx
                call vk64_alloc_buffer
                testl %eax, %eax
                jz .Lvk64_wr32_yok2
                movl $-4, %eax
                jmp .Lvk64_wr32_ret
            .Lvk64_wr32_yok2:
                movl %r12d, %eax
                movslq %eax, %rax
                movq %rax, vk64_ycap32(%rip)
            .Lvk64_wr32_yok:
                movl %r13d, %eax
                movslq %eax, %rax
                shlq $3, %rax
                movq %rax, %rdx
                movq 40(%rsp), %rsi
                leaq 24(%rsi), %rsi
                movq vk64_xmap32(%rip), %rdi
                call memcpy@PLT
                movl %r12d, %eax
                movslq %eax, %rax
                shlq $3, %rax
                movq %rax, %rdx
                xorl %esi, %esi
                movq vk64_ymap32(%rip), %rdi
                call memset@PLT
                // dbi[3]: w32[id] (range ×4), x32, y32
                movslq %ebx, %rax
                shlq $3, %rax
                leaq 5424+320+vk64_scratch(%rip), %rdi
                movq vk64_wbufs32(%rax), %rcx
                movq %rcx, 0(%rdi)
                movq $0, 8(%rdi)
                movq vk64_wcaps32(%rax), %rcx
                shlq $2, %rcx
                movq %rcx, 16(%rdi)
                movq vk64_xbuf32(%rip), %rcx
                movq %rcx, 24(%rdi)
                movq $0, 32(%rdi)
                movq vk64_xcap32(%rip), %rcx
                shlq $3, %rcx
                movq %rcx, 40(%rdi)
                movq vk64_ybuf32(%rip), %rcx
                movq %rcx, 48(%rdi)
                movq $0, 56(%rdi)
                movq vk64_ycap32(%rip), %rcx
                shlq $3, %rcx
                movq %rcx, 64(%rdi)
                movq vk64_dset(%rip), %rdi
                leaq 5424+320+vk64_scratch(%rip), %rsi
                movl $3, %edx
                call vk64_write_desc
                // pcs
                movl %r12d, 5296+vk64_scratch(%rip)
                movl %r13d, 5300+vk64_scratch(%rip)
                movl $2, 5304+vk64_scratch(%rip)
                movl $0, 5308+vk64_scratch(%rip)
                movq $1000000000, %rax
                cmpq %rax, %r14
                jne .Lvk64_wr32_d1
                movl $0, 5304+vk64_scratch(%rip)
                jmp .Lvk64_wr32_go
            .Lvk64_wr32_d1:
                movq $1000000, %rax
                cmpq %rax, %r14
                jne .Lvk64_wr32_go
                movl $1, 5304+vk64_scratch(%rip)
            .Lvk64_wr32_go:
                movq %r14, 5312+vk64_scratch(%rip)
                movq vk64_pipe32(%rip), %rdi
                movq vk64_pl(%rip), %rsi
                movq vk64_dset(%rip), %rdx
                leaq 5296+vk64_scratch(%rip), %rcx
                movl %r12d, %r8d
                call vk64_submit
                testl %eax, %eax
                jnz .Lvk64_wr32_ret
                movl %r12d, %eax
                movslq %eax, %rax
                shlq $3, %rax
                movq %rax, %rdx
                movq vk64_ymap32(%rip), %rsi
                movq 48(%rsp), %rdi
                addq $24, %rdi
                call memcpy@PLT
                xorl %eax, %eax
            .Lvk64_wr32_ret:
                addq $64, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }

    // kof_mv64_wputsp(rdi=id, rsi=wh arr, rdx=wl arr, ecx=m, r8d=k)
    private static void emitWputsp(StringBuilder sb) {
        sb.append("""
            .globl kof_mv64_wputsp
            .type kof_mv64_wputsp, @function
            kof_mv64_wputsp:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                subq $56, %rsp
                movl %edi, %ebx                    # id
                movq %rsi, 32(%rsp)                # wh arr
                movq %rdx, 40(%rsp)                # wl arr
                movl %ecx, %r12d                   # m
                movl %r8d, %r13d                   # k
                call vk64_ensure_init
                testl %eax, %eax
                jz .Lvk64_wps_1
                movl $-1, %eax
                jmp .Lvk64_wps_ret
            .Lvk64_wps_1:
                cmpq $0, vk64_pipeSplit(%rip)
                jne .Lvk64_wps_2
                movl $-6, %eax
                jmp .Lvk64_wps_ret
            .Lvk64_wps_2:
                testl %ebx, %ebx
                js .Lvk64_wps_bad
                cmpl $192, %ebx
                jl .Lvk64_wps_3
            .Lvk64_wps_bad:
                movl $-2, %eax
                jmp .Lvk64_wps_ret
            .Lvk64_wps_3:
                movl %r12d, %eax
                movl %r13d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                testq %rax, %rax
                jle .Lvk64_wps_neg
                // wh: 4B
                movslq %ebx, %rcx
                shlq $3, %rcx
                cmpq vk64_whcaps(%rcx), %rax
                jge .Lvk64_wps_wl
                movq vk64_whmaps(%rcx), %rdi
                testq %rdi, %rdi
                jz .Lvk64_wps_whu
                movq g_vk64_vkUnmapMemory(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_whmems(%rcx), %rsi
                call *%rax
            .Lvk64_wps_whu:
                movq vk64_whbufs(%rcx), %rdi
                testq %rdi, %rdi
                jz .Lvk64_wps_whd
                movq g_vk64_vkDestroyBuffer(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_whbufs(%rcx), %rsi
                xorl %edx, %edx
                call *%rax
            .Lvk64_wps_whd:
                movq vk64_whmems(%rcx), %rdi
                testq %rdi, %rdi
                jz .Lvk64_wps_whf
                movq g_vk64_vkFreeMemory(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_whmems(%rcx), %rsi
                xorl %edx, %edx
                call *%rax
            .Lvk64_wps_whf:
                movq $0, vk64_whbufs(%rcx)
                movq $0, vk64_whmems(%rcx)
                movq $0, vk64_whmaps(%rcx)
                movq $0, vk64_whcaps(%rcx)
                movl %r12d, %eax
                movl %r13d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                shlq $2, %rax
                movq %rax, %rdi
                movslq %ebx, %rax
                shlq $3, %rax
                leaq vk64_whbufs(%rax), %rsi
                leaq vk64_whmems(%rax), %rdx
                leaq vk64_whmaps(%rax), %rcx
                call vk64_alloc_buffer
                testl %eax, %eax
                jz .Lvk64_wps_whok
                movl $-4, %eax
                jmp .Lvk64_wps_ret
            .Lvk64_wps_whok:
                movl %r12d, %eax
                movl %r13d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                movslq %ebx, %rcx
                shlq $3, %rcx
                movq %rax, vk64_whcaps(%rcx)
            .Lvk64_wps_wl:
                movl %r12d, %eax
                movl %r13d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                movslq %ebx, %rcx
                shlq $3, %rcx
                cmpq vk64_wlcaps(%rcx), %rax
                jge .Lvk64_wps_copy
                movq vk64_wlmaps(%rcx), %rdi
                testq %rdi, %rdi
                jz .Lvk64_wps_wlu
                movq g_vk64_vkUnmapMemory(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_wlmems(%rcx), %rsi
                call *%rax
            .Lvk64_wps_wlu:
                movq vk64_wlbufs(%rcx), %rdi
                testq %rdi, %rdi
                jz .Lvk64_wps_wld
                movq g_vk64_vkDestroyBuffer(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_wlbufs(%rcx), %rsi
                xorl %edx, %edx
                call *%rax
            .Lvk64_wps_wld:
                movq vk64_wlmems(%rcx), %rdi
                testq %rdi, %rdi
                jz .Lvk64_wps_wlf
                movq g_vk64_vkFreeMemory(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_wlmems(%rcx), %rsi
                xorl %edx, %edx
                call *%rax
            .Lvk64_wps_wlf:
                movq $0, vk64_wlbufs(%rcx)
                movq $0, vk64_wlmems(%rcx)
                movq $0, vk64_wlmaps(%rcx)
                movq $0, vk64_wlcaps(%rcx)
                movl %r12d, %eax
                movl %r13d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                shlq $2, %rax
                movq %rax, %rdi
                movslq %ebx, %rax
                shlq $3, %rax
                leaq vk64_wlbufs(%rax), %rsi
                leaq vk64_wlmems(%rax), %rdx
                leaq vk64_wlmaps(%rax), %rcx
                call vk64_alloc_buffer
                testl %eax, %eax
                jz .Lvk64_wps_wlok
                movl $-4, %eax
                jmp .Lvk64_wps_ret
            .Lvk64_wps_wlok:
                movl %r12d, %eax
                movl %r13d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                movslq %ebx, %rcx
                shlq $3, %rcx
                movq %rax, vk64_wlcaps(%rcx)
            .Lvk64_wps_copy:
                // wh e wl: m*k*4 bytes cada
                movl %r12d, %eax
                movl %r13d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                shlq $2, %rax
                movq %rax, %rdx
                movq 32(%rsp), %rsi
                leaq 24(%rsi), %rsi
                movslq %ebx, %rax
                shlq $3, %rax
                movq vk64_whmaps(%rax), %rdi
                call memcpy@PLT
                movl %r12d, %eax
                movl %r13d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                shlq $2, %rax
                movq %rax, %rdx
                movq 40(%rsp), %rsi
                leaq 24(%rsi), %rsi
                movslq %ebx, %rax
                shlq $3, %rax
                movq vk64_wlmaps(%rax), %rdi
                call memcpy@PLT
                xorl %eax, %eax
            .Lvk64_wps_ret:
                addq $56, %rsp
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lvk64_wps_neg:
                movl $-3, %eax
                jmp .Lvk64_wps_ret
            """);
    }


    // kof_mv64_wrunsp(rdi=id, rsi=x arr, rdx=y arr, ecx=m, r8d=k, r9=div)
    // bindings: 0=wh[id] 1=wl[id] 2=xh 3=xl 4=y; host computa xh/xl.
    // Stack: 32(%rsp)=k, 40(%rsp)=x arr, 48(%rsp)=y arr, 56(%rsp)=m.
    private static void emitWrunsp(StringBuilder sb) {
        sb.append("""
            .globl kof_mv64_wrunsp
            .type kof_mv64_wrunsp, @function
            kof_mv64_wrunsp:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $64, %rsp
                movl %edi, %ebx                    # id
                movq %rsi, 40(%rsp)                # x arr
                movq %rdx, 48(%rsp)                # y arr
                movl %ecx, 56(%rsp)                # m
                movl %r8d, 32(%rsp)                # k
                movq %r9, %r14                     # div
                call vk64_ensure_init
                testl %eax, %eax
                jz .Lvk64_wrs1
                movl $-1, %eax
                jmp .Lvk64_wrsr
            .Lvk64_wrs1:
                cmpq $0, vk64_pipeSplit(%rip)
                je .Lvk64_wrs_nospv
                cmpq $0, vk64_pl5(%rip)
                je .Lvk64_wrs_nospv
                cmpq $0, vk64_dset5(%rip)
                jne .Lvk64_wrs2
            .Lvk64_wrs_nospv:
                movl $-6, %eax
                jmp .Lvk64_wrsr
            .Lvk64_wrs2:
                testl %ebx, %ebx
                js .Lvk64_wrs_bad
                cmpl $192, %ebx
                jl .Lvk64_wrs3
            .Lvk64_wrs_bad:
                movl $-2, %eax
                jmp .Lvk64_wrsr
            .Lvk64_wrs3:
                movl 32(%rsp), %eax
                movl 56(%rsp), %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                movslq %ebx, %rcx
                shlq $3, %rcx
                cmpq $0, vk64_whbufs(%rcx)
                je .Lvk64_wrs_nobuf
                cmpq $0, vk64_wlbufs(%rcx)
                je .Lvk64_wrs_nobuf
                cmpq vk64_whcaps(%rcx), %rax
                jg .Lvk64_wrs_nobuf
                cmpq vk64_wlcaps(%rcx), %rax
                jg .Lvk64_wrs_nobuf
                jmp .Lvk64_wrs_xh
            .Lvk64_wrs_nobuf:
                movl $-3, %eax
                jmp .Lvk64_wrsr
            // xh buffer (k elems i32)
            .Lvk64_wrs_xh:
                movl 32(%rsp), %eax
                movslq %eax, %rax
                cmpq vk64_xhcap(%rip), %rax
                jge .Lvk64_wrs_xl
                movq vk64_xhmap(%rip), %rdi
                testq %rdi, %rdi
                jz .Lvk64_wrs_xhu
                movq g_vk64_vkUnmapMemory(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_xhmem(%rip), %rsi
                call *%rax
            .Lvk64_wrs_xhu:
                movq vk64_xhbuf(%rip), %rdi
                testq %rdi, %rdi
                jz .Lvk64_wrs_xhd
                movq g_vk64_vkDestroyBuffer(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_xhbuf(%rip), %rsi
                xorl %edx, %edx
                call *%rax
            .Lvk64_wrs_xhd:
                movq vk64_xhmem(%rip), %rdi
                testq %rdi, %rdi
                jz .Lvk64_wrs_xhf
                movq g_vk64_vkFreeMemory(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_xhmem(%rip), %rsi
                xorl %edx, %edx
                call *%rax
            .Lvk64_wrs_xhf:
                movq $0, vk64_xhbuf(%rip)
                movq $0, vk64_xhmem(%rip)
                movq $0, vk64_xhmap(%rip)
                movl 32(%rsp), %eax
                movslq %eax, %rax
                shlq $2, %rax
                movq %rax, %rdi
                leaq vk64_xhbuf(%rip), %rsi
                leaq vk64_xhmem(%rip), %rdx
                leaq vk64_xhmap(%rip), %rcx
                call vk64_alloc_buffer
                testl %eax, %eax
                jz .Lvk64_wrs_xhok
                movl $-4, %eax
                jmp .Lvk64_wrsr
            .Lvk64_wrs_xhok:
                movl 32(%rsp), %eax
                movslq %eax, %rax
                movq %rax, vk64_xhcap(%rip)
            // xl buffer
            .Lvk64_wrs_xl:
                movl 32(%rsp), %eax
                movslq %eax, %rax
                cmpq vk64_xlcap(%rip), %rax
                jge .Lvk64_wrs_y
                movq vk64_xlmap(%rip), %rdi
                testq %rdi, %rdi
                jz .Lvk64_wrs_xlu
                movq g_vk64_vkUnmapMemory(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_xlmem(%rip), %rsi
                call *%rax
            .Lvk64_wrs_xlu:
                movq vk64_xlbuf(%rip), %rdi
                testq %rdi, %rdi
                jz .Lvk64_wrs_xld
                movq g_vk64_vkDestroyBuffer(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_xlbuf(%rip), %rsi
                xorl %edx, %edx
                call *%rax
            .Lvk64_wrs_xld:
                movq vk64_xlmem(%rip), %rdi
                testq %rdi, %rdi
                jz .Lvk64_wrs_xlf
                movq g_vk64_vkFreeMemory(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_xlmem(%rip), %rsi
                xorl %edx, %edx
                call *%rax
            .Lvk64_wrs_xlf:
                movq $0, vk64_xlbuf(%rip)
                movq $0, vk64_xlmem(%rip)
                movq $0, vk64_xlmap(%rip)
                movl 32(%rsp), %eax
                movslq %eax, %rax
                shlq $2, %rax
                movq %rax, %rdi
                leaq vk64_xlbuf(%rip), %rsi
                leaq vk64_xlmem(%rip), %rdx
                leaq vk64_xlmap(%rip), %rcx
                call vk64_alloc_buffer
                testl %eax, %eax
                jz .Lvk64_wrs_xlok
                movl $-4, %eax
                jmp .Lvk64_wrsr
            .Lvk64_wrs_xlok:
                movl 32(%rsp), %eax
                movslq %eax, %rax
                movq %rax, vk64_xlcap(%rip)
            // y (m elems i64): cresce via vk64_shape_xy
            .Lvk64_wrs_y:
                movl 56(%rsp), %eax
                movslq %eax, %rax
                cmpq vk64_ycap(%rip), %rax
                jle .Lvk64_wrs_split
                movl 56(%rsp), %edi
                movl 32(%rsp), %esi
                call vk64_shape_xy
                testl %eax, %eax
                jz .Lvk64_wrs_split
                movl $-4, %eax
                jmp .Lvk64_wrsr
            // host split: xh[i]=x/1e6, xl[i]=x%1e6 (trunc idiv)
            .Lvk64_wrs_split:
                movq 40(%rsp), %rax
                leaq 24(%rax), %r15                # x data
                movq vk64_xhmap(%rip), %r13        # xh
                movq vk64_xlmap(%rip), %r12        # xl
                xorl %r14d, %r14d                  # i
            .Lvk64_wrs_sp:
                cmpl 32(%rsp), %r14d
                jge .Lvk64_wrs_spdone
                movq (%r15,%r14,8), %rax           # x[i]
                movq $1000000, %rcx
                cqto
                idivq %rcx
                movl %eax, (%r13,%r14,4)
                movl %edx, (%r12,%r14,4)
                incl %r14d
                jmp .Lvk64_wrs_sp
            .Lvk64_wrs_spdone:
                movl 56(%rsp), %eax
                movslq %eax, %rax
                shlq $3, %rax
                movq %rax, %rdx
                xorl %esi, %esi
                movq vk64_ymap(%rip), %rdi
                call memset@PLT
                // dbi[5] @5424+320
                movslq %ebx, %rax
                shlq $3, %rax
                leaq 5424+320+vk64_scratch(%rip), %rdi
                movq vk64_whbufs(%rax), %rcx
                movq %rcx, 0(%rdi)
                movq $0, 8(%rdi)
                movq vk64_whcaps(%rax), %rcx
                shlq $2, %rcx
                movq %rcx, 16(%rdi)
                movq vk64_wlbufs(%rax), %rcx
                movq %rcx, 24(%rdi)
                movq $0, 32(%rdi)
                movq vk64_wlcaps(%rax), %rcx
                shlq $2, %rcx
                movq %rcx, 40(%rdi)
                movq vk64_xhbuf(%rip), %rcx
                movq %rcx, 48(%rdi)
                movq $0, 56(%rdi)
                movq vk64_xhcap(%rip), %rcx
                shlq $2, %rcx
                movq %rcx, 64(%rdi)
                movq vk64_xlbuf(%rip), %rcx
                movq %rcx, 72(%rdi)
                movq $0, 80(%rdi)
                movq vk64_xlcap(%rip), %rcx
                shlq $2, %rcx
                movq %rcx, 88(%rdi)
                movq vk64_ybuf(%rip), %rcx
                movq %rcx, 96(%rdi)
                movq $0, 104(%rdi)
                movq vk64_ycap(%rip), %rcx
                shlq $3, %rcx
                movq %rcx, 112(%rdi)
                movq vk64_dset5(%rip), %rdi
                leaq 5424+320+vk64_scratch(%rip), %rsi
                movl $5, %edx
                call vk64_write_desc
                // pcs[6] = {m, k, divId, 0, div}
                movl 56(%rsp), %eax
                movl %eax, 5296+vk64_scratch(%rip)
                movl 32(%rsp), %eax
                movl %eax, 5300+vk64_scratch(%rip)
                movl $2, 5304+vk64_scratch(%rip)
                movl $0, 5308+vk64_scratch(%rip)
                movq $1000000000, %rax
                cmpq %rax, %r14
                jne .Lvk64_wrs_d1
                movl $0, 5304+vk64_scratch(%rip)
                jmp .Lvk64_wrs_go
            .Lvk64_wrs_d1:
                movq $1000000, %rax
                cmpq %rax, %r14
                jne .Lvk64_wrs_go
                movl $1, 5304+vk64_scratch(%rip)
            .Lvk64_wrs_go:
                movq %r14, 5312+vk64_scratch(%rip)
                movq vk64_pipeSplit(%rip), %rdi
                movq vk64_pl5(%rip), %rsi
                movq vk64_dset5(%rip), %rdx
                leaq 5296+vk64_scratch(%rip), %rcx
                movl 56(%rsp), %r8d
                call vk64_submit
                testl %eax, %eax
                jnz .Lvk64_wrsr
                // y ← ymap
                movl 56(%rsp), %eax
                movslq %eax, %rax
                shlq $3, %rax
                movq %rax, %rdx
                movq vk64_ymap(%rip), %rsi
                movq 48(%rsp), %rdi
                addq $24, %rdi
                call memcpy@PLT
                xorl %eax, %eax
            .Lvk64_wrsr:
                addq $64, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }

    // kof_vk_dispatch64(rdi=a arr, rsi=b arr, rdx=c arr, ecx=m, r8d=n, r9d=k)
    // matmul64.spv: 3 SSBOs + push 12B {m,n,k}; 1 WG por elemento c.
    private static void emitDispatch64(StringBuilder sb) {
        sb.append("""
            .globl kof_vk_dispatch64
            .type kof_vk_dispatch64, @function
            kof_vk_dispatch64:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $72, %rsp
                movq %rdi, 32(%rsp)                # a arr
                movq %rsi, 40(%rsp)                # b arr
                movq %rdx, 48(%rsp)                # c arr
                movl %ecx, %ebx                    # m
                movl %r8d, %r12d                   # n
                movl %r9d, %r13d                   # k
                // lazy init (mesma cadeia do wrun, mas com dset12:
                // um segundo dpool/dset sobre o dsl de 3 binds)
                cmpl $0, vk64_inited(%rip)
                jne .Lvk64_d64_1
                leaq .Lvkv_sov64(%rip), %rdi
                call getenv@PLT
                testq %rax, %rax
                jz .Lvk64_d64_fb
                movq %rax, %rdi
                call vk64_init_common              # init com 3 binds
                testl %eax, %eax
                jnz .Lvk64_d64_fb
            .Lvk64_d64_1:
                // a: m*k*8, b: k*n*8, c: m*n*8 — buffers persistentes
                // abuf/bbuf/cbuf no scratch externo? usar globals extras:
                // (alocados no final do .bss)
                movl %ebx, %eax
                movl %r13d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                shlq $3, %rax
                cmpq vk64_d64acap(%rip), %rax
                jle .Lvk64_d64_b
                movq %rax, %rdi
                leaq vk64_d64abuf(%rip), %rsi
                leaq vk64_d64amem(%rip), %rdx
                leaq vk64_d64amap(%rip), %rcx
                call vk64_alloc_buffer
                testl %eax, %eax
                jnz .Lvk64_d64_fb
                movl %ebx, %eax
                movl %r13d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                movq %rax, vk64_d64acap(%rip)
            .Lvk64_d64_b:
                movl %r13d, %eax
                movl %r12d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                shlq $3, %rax
                cmpq vk64_d64bcap(%rip), %rax
                jle .Lvk64_d64_c
                movq %rax, %rdi
                leaq vk64_d64bbuf(%rip), %rsi
                leaq vk64_d64bmem(%rip), %rdx
                leaq vk64_d64bmap(%rip), %rcx
                call vk64_alloc_buffer
                testl %eax, %eax
                jnz .Lvk64_d64_fb
                movl %r13d, %eax
                movl %r12d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                movq %rax, vk64_d64bcap(%rip)
            .Lvk64_d64_c:
                movl %ebx, %eax
                movl %r12d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                shlq $3, %rax
                cmpq vk64_d64ccap(%rip), %rax
                jle .Lvk64_d64_copy
                movq %rax, %rdi
                leaq vk64_d64cbuf(%rip), %rsi
                leaq vk64_d64cmem(%rip), %rdx
                leaq vk64_d64cmap(%rip), %rcx
                call vk64_alloc_buffer
                testl %eax, %eax
                jnz .Lvk64_d64_fb
                movl %ebx, %eax
                movl %r12d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                movq %rax, vk64_d64ccap(%rip)
            .Lvk64_d64_copy:
                // a e b → maps (c: só ymap=0? o shader escreve c todo:
                // memset c)
                movl %ebx, %eax
                movl %r13d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                shlq $3, %rax
                movq %rax, %rdx
                movq 32(%rsp), %rsi
                leaq 24(%rsi), %rsi
                movq vk64_d64amap(%rip), %rdi
                call memcpy@PLT
                movl %r13d, %eax
                movl %r12d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                shlq $3, %rax
                movq %rax, %rdx
                movq 40(%rsp), %rsi
                leaq 24(%rsi), %rsi
                movq vk64_d64bmap(%rip), %rdi
                call memcpy@PLT
                // dbi[3] (a, b, c) + write_desc no dset12
                leaq 5424+320+vk64_scratch(%rip), %rdi
                movq vk64_d64abuf(%rip), %rcx
                movq %rcx, 0(%rdi)
                movq $0, 8(%rdi)
                movq vk64_d64acap(%rip), %rcx
                shlq $3, %rcx
                movq %rcx, 16(%rdi)
                movq vk64_d64bbuf(%rip), %rcx
                movq %rcx, 24(%rdi)
                movq $0, 32(%rdi)
                movq vk64_d64bcap(%rip), %rcx
                shlq $3, %rcx
                movq %rcx, 40(%rdi)
                movq vk64_d64cbuf(%rip), %rcx
                movq %rcx, 48(%rdi)
                movq $0, 56(%rdi)
                movq vk64_d64ccap(%rip), %rcx
                shlq $3, %rcx
                movq %rcx, 64(%rdi)
                movq vk64_dset12(%rip), %rdi
                leaq 5424+320+vk64_scratch(%rip), %rsi
                movl $3, %edx
                call vk64_write_desc
                // pcs {m, n, k}
                movl %ebx, 5296+vk64_scratch(%rip)
                movl %r12d, 5300+vk64_scratch(%rip)
                movl %r13d, 5304+vk64_scratch(%rip)
                // dispatch: 1 WG por elemento c → (m*n+63)/64 WGs 1D
                movl %ebx, %eax
                movl %r12d, %ecx
                imull %ecx, %eax
                movl %eax, %r14d
                addl $63, %r14d
                shrl $6, %r14d
                cmpq $0, vk64_pipe12(%rip)
                je .Lvk64_d64_fb
                movq vk64_pipe12(%rip), %rdi
                movq vk64_pl(%rip), %rsi
                movq vk64_dset12(%rip), %rdx
                leaq 5296+vk64_scratch(%rip), %rcx
                movl %r14d, %r8d
                call vk64_submit
                testl %eax, %eax
                jnz .Lvk64_d64_fb
                // c ← cmap
                movl %ebx, %eax
                movl %r12d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                shlq $3, %rax
                movq %rax, %rdx
                movq vk64_d64cmap(%rip), %rsi
                movq 48(%rsp), %rdi
                addq $24, %rdi
                call memcpy@PLT
                xorl %eax, %eax
                jmp .Lvk64_d64_ret
            .Lvk64_d64_fb:
                movl $-1, %eax
            .Lvk64_d64_ret:
                addq $72, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }
}
