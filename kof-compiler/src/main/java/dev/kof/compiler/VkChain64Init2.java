package dev.kof.compiler;

/** Fragmentos do .s x86-64 de Vulkan (vkchain64) — extraidos de VkChain64Asm (REFACTOR-500). O concatenador final e VkChain64Asm.source(); a ordem de chamada preserva o asm byte-a-byte. */
final class VkChain64Init2 {
    private VkChain64Init2() {}

    // pipe32 opcional: mesmo layout pl, SPV de KOF_GPU_SPV64_W32.
    // Falha é não-fatal (wrun32 → rc != 0 → CPU).
    static void sourcePartC(StringBuilder sb) {
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
    static void sourcePartD(StringBuilder sb) {
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
}
