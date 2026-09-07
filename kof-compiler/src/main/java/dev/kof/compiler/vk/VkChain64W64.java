package dev.kof.compiler.vk;

/** Fragmentos do .s x86-64 de Vulkan (vkchain64) — extraidos de VkChain64Asm (REFACTOR-500). O concatenador final e VkChain64Asm.source(); a ordem de chamada preserva o asm byte-a-byte. */
final class VkChain64W64 {
    private VkChain64W64() {}

    // kof_mv64_wput(rdi=id, rsi=w arr, edx=m, ecx=k)
    static void sourceWput(StringBuilder sb) {
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
    static void sourceWrun(StringBuilder sb) {
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
}
