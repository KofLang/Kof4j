package dev.kof.compiler;

/** Fragmentos do .s x86-64 de Vulkan (vkchain64) — extraidos de VkChain64Asm (REFACTOR-500). O concatenador final e VkChain64Asm.source(); a ordem de chamada preserva o asm byte-a-byte. */
final class VkChain64InitPools {
    private VkChain64InitPools() {}

    // dpool/dset (3 binds) + cpool/cmd/fence + inited=1.
    static void sourcePartE(StringBuilder sb) {
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
}
