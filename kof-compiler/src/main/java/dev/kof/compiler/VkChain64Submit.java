package dev.kof.compiler;

/** Fragmentos do .s x86-64 de Vulkan (vkchain64) — extraidos de VkChain64Asm (REFACTOR-500). O concatenador final e VkChain64Asm.source(); a ordem de chamada preserva o asm byte-a-byte. */
final class VkChain64Submit {
    private VkChain64Submit() {}

    // ───────────────────── submit de dispatch ────────────────────────
    // vk64_submit(rdi=pipe, rsi=pl, rdx=dset, rcx=pcs(24B), r8d=wgX)
    // → 0 ok / -5 (errbuf). Usa o cmd/fence globais.
    static void sourceSubmit(StringBuilder sb) {
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
    static void sourceWriteDesc(StringBuilder sb) {
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
}
