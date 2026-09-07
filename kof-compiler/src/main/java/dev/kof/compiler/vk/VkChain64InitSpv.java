package dev.kof.compiler.vk;

/** Fragmentos do .s x86-64 de Vulkan (vkchain64) — extraidos de VkChain64Asm (REFACTOR-500). O concatenador final e VkChain64Asm.source(); a ordem de chamada preserva o asm byte-a-byte. */
public final class VkChain64InitSpv {
    private VkChain64InitSpv() {}

    // Parte B: spv → shader module → dsl/pl (3 binds) → pipe principal
    // → pipe32 (opcional, KOF_GPU_SPV64_W32) → split (opcional,
    // KOF_GPU_SPV64_SPLIT: dsl5/pl5/pipe/dpool5/dset5) → dpool/dset
    // → cpool/cmd/fence. r13d = nbinds.
    static void sourcePartB(StringBuilder sb) {
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
        VkChain64Init2.sourcePartC(sb);
        VkChain64Init2.sourcePartD(sb);
        VkChain64InitPools.sourcePartE(sb);
    }
}
