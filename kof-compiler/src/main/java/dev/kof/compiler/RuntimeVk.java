package dev.kof.compiler;

/**
 * Emissão do ASM de stubs Vulkan (kof_vk_*) do runtime nativo. Domínio isolado
 * do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeVk {

    private RuntimeVk() {}

    static void emitVkStubs(StringBuilder sb) {
        sb.append("""
            .section .rodata
            .Lvklib1:  .asciz "libvkchain.so"
            .Lvklib2:  .asciz "./libvkchain.so"
            .Lvksinit: .asciz "vkchain_init"
            .Lvksdisp: .asciz "vkchain_dispatch"
            .Lvksr:    .asciz "vkchain_fail_reason"
            .Lvkspv:   .asciz "KOF_GPU_SPV"
            .Lvkspvd:  .asciz "gpu/shaders/matmul.spv"
            .Lvkna:    .asciz "libvkchain.so nao encontrada (GPU002)"
            .Lvksym:   .asciz "libvkchain: simbolo faltando (GPU003)"
            .Lvkif:    .asciz "libvkchain: init falhou (GPU004)"
            .Lvkok:    .asciz "libvkchain carregada"

            .section .bss
            .lcomm g_vk_lib, 8
            .lcomm g_vk_finit, 8
            .lcomm g_vk_fdisp, 8
            .lcomm g_vk_fr, 8
            .lcomm g_vk_ok, 4
            .lcomm g_vk_err, 8

            .section .text
            .globl kof_vk_available
            .type kof_vk_available, @function
            kof_vk_available:
                cmpl $0, g_vk_ok(%rip)
                jne 9f
                # lazy init: dlopen(RTLD_NOW) nos 2 nomes + dlsym*3 + vkchain_init(spv)
                pushq %rbx
                leaq .Lvklib1(%rip), %rdi
                movl $2, %esi
                call dlopen@PLT
                testq %rax, %rax
                jnz 1f
                leaq .Lvklib2(%rip), %rdi
                movl $2, %esi
                call dlopen@PLT
                testq %rax, %rax
                jnz 1f
                leaq .Lvkna(%rip), %rax
                jmp .Lvkf
            1:  movq %rax, g_vk_lib(%rip)
                leaq .Lvksinit(%rip), %rsi
                movq %rax, %rdi
                call dlsym@PLT
                testq %rax, %rax
                jz .Lvkf1
                movq %rax, g_vk_finit(%rip)
            .Lvkf1:
                leaq .Lvksdisp(%rip), %rsi
                movq g_vk_lib(%rip), %rdi
                call dlsym@PLT
                testq %rax, %rax
                jz .Lvkfsym
                movq %rax, g_vk_fdisp(%rip)
            .Lvkf1b:
                leaq .Lvksr(%rip), %rsi
                movq g_vk_lib(%rip), %rdi
                call dlsym@PLT
                testq %rax, %rax
                jz .Lvkfsym
                movq %rax, g_vk_fr(%rip)
            .Lvkf2b:
                cmpq $0, g_vk_finit(%rip)
                je .Lvkfsym
                cmpq $0, g_vk_fdisp(%rip)
                je .Lvkfsym
                cmpq $0, g_vk_fr(%rip)
                je .Lvkfsym
                leaq .Lvkspv(%rip), %rdi
                call getenv@PLT
                testq %rax, %rax
                jnz 2f
                leaq .Lvkspvd(%rip), %rax
            2:  movq %rax, %rdi
                call *g_vk_finit(%rip)
                testl %eax, %eax
                jnz .Lvkf2
                movl $1, g_vk_ok(%rip)
                leaq .Lvkok(%rip), %rax
                jmp .Lvkf
            .Lvkfsym:
                leaq .Lvksym(%rip), %rax
                jmp .Lvkf
            .Lvkf2:
                leaq .Lvkif(%rip), %rax
            .Lvkf:
                movq %rax, g_vk_err(%rip)
                movl g_vk_ok(%rip), %eax
                popq %rbx
                ret
            9:  movl g_vk_ok(%rip), %eax
                ret
            .globl kof_vk_fail_reason
            .type kof_vk_fail_reason, @function
            # retorna KofString* (converte o char* da lib via kof_io_make_string)
            kof_vk_fail_reason:
                pushq %rbx
                pushq %r12
                movq g_vk_err(%rip), %rax
                testq %rax, %rax
                jnz 1f
                leaq .Lvkok(%rip), %rax
            1:  movq %rax, %rdi
                movq %rax, %rbx
                call strlen@PLT
                movq %rbx, %rdi
                movq %rax, %rsi
                call kof_io_make_string
                popq %r12
                popq %rbx
                ret
            # kof_vk_dispatch(rdi=a, rsi=b, rdx=c, rcx=m, r8d=n, r9d=k)
            # args Kof: KofArray* (dados INLINE em +24) -- o C quer int* → lea +24. Lazy init.
            .globl kof_vk_dispatch
            .type kof_vk_dispatch, @function
            kof_vk_dispatch:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $32, %rsp
                movq %rdi, %rbx
                movq %rsi, %r12
                movq %rdx, %r13
                movl %ecx, %r14d
                movl %r8d, %r15d
                movl %r9d, 0(%rsp)
            .Lvk_d_lazy:
                cmpl $0, g_vk_ok(%rip)
                jne 1f
                movq %rdi, 8(%rsp)
                call kof_vk_available
                movq 8(%rsp), %rdi
                testl %eax, %eax
                jz .Lvk_d_fb
            1:  leaq 24(%rbx), %rdi
                leaq 24(%r12), %rsi
                leaq 24(%r13), %rdx
                movl %r14d, %ecx
                movl %r15d, %r8d
                movl 0(%rsp), %r9d
                call *g_vk_fdisp(%rip)
                addq $32, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lvk_d_fb:
                movl $-1, %eax
                addq $32, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            """);
        // M36.5: cadeia Vulkan int64 real (tradução asm do vkchain64.c)
        // -- substitui os stubs kof_mv64_* / kof_vk_dispatch64.
        sb.append(VkChain64Asm.source());
    }

}