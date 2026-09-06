package dev.kof.compiler;

/** Fragmentos do .s x86-64 de Vulkan (vkchain64) — extraidos de VkChain64Asm (REFACTOR-500). O concatenador final e VkChain64Asm.source(); a ordem de chamada preserva o asm byte-a-byte. */
final class VkChain64W32 {
    private VkChain64W32() {}

    // kof_mv64_wput32(rdi=id, rsi=w i32 arr, edx=m, ecx=k)
    static void sourceWput32(StringBuilder sb) {
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
    static void sourceWrun32(StringBuilder sb) {
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
}
