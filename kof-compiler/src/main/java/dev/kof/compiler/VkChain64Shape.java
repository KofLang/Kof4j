package dev.kof.compiler;

/** Fragmentos do .s x86-64 de Vulkan (vkchain64) — extraidos de VkChain64Asm (REFACTOR-500). O concatenador final e VkChain64Asm.source(); a ordem de chamada preserva o asm byte-a-byte. */
final class VkChain64Shape {
    private VkChain64Shape() {}

    // ───────────────────── funções exportadas ────────────────────────
    // kof_mv64_set_shape(rdi=m, rsi=k) — dimensiona W[m×k] + x + y e
    // escreve o desc set (3 binds). Retorna 0/-1/-2.
    static void sourceSetShape(StringBuilder sb) {
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
    // vk64_shape_xy(rdi=m, esi=k): dimensiona x[k], y[m] i64 → 0 ok
    static void sourceShapeXy(StringBuilder sb) {
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
}
