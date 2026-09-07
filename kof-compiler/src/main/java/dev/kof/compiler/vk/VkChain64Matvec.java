package dev.kof.compiler.vk;

/** Fragmentos do .s x86-64 de Vulkan (vkchain64) — extraidos de VkChain64Asm (REFACTOR-500). O concatenador final e VkChain64Asm.source(); a ordem de chamada preserva o asm byte-a-byte. */
final class VkChain64Matvec {
    private VkChain64Matvec() {}

    // kof_mv64_load_w(rdi=w arr, esi=m, edx=k) — memcpy p/ wmap
    static void sourceLoadW(StringBuilder sb) {
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
    static void sourceMatvec(StringBuilder sb) {
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
                // readback y ← ymap (preserva o rc do submit)
                movl %eax, %r12d
                movl %ebx, %eax
                movslq %eax, %rax
                shlq $3, %rax
                movq %rax, %rdx
                movq vk64_ymap(%rip), %rsi
                movq 32(%rsp), %rdi
                call memcpy@PLT
                movl %r12d, %eax
            .Lvk64_mv_ret:
                addq $40, %rsp
                popq %r12
                popq %rbx
                ret
            """);
    }
}
