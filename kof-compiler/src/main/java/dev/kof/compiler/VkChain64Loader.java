package dev.kof.compiler;

/** Fragmentos do .s x86-64 de Vulkan (vkchain64) — extraidos de VkChain64Asm (REFACTOR-500). O concatenador final e VkChain64Asm.source(); a ordem de chamada preserva o asm byte-a-byte. */
final class VkChain64Loader {
    private VkChain64Loader() {}

    // ───────────────────────── loader (dlopen/dlsym) ─────────────────
    static void source(StringBuilder sb) {
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
}
