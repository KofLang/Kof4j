package dev.kof.compiler.vk;

/** Fragmentos do .s x86-64 de Vulkan (vkchain64) — extraidos de VkChain64Asm (REFACTOR-500). O concatenador final e VkChain64Asm.source(); a ordem de chamada preserva o asm byte-a-byte. */
final class VkChain64WSp {
    private VkChain64WSp() {}

    // kof_mv64_wputsp(rdi=id, rsi=wh arr, rdx=wl arr, ecx=m, r8d=k)
    static void sourceWputsp(StringBuilder sb) {
        sb.append("""
            .globl kof_mv64_wputsp
            .type kof_mv64_wputsp, @function
            kof_mv64_wputsp:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                subq $56, %rsp
                movl %edi, %ebx                    # id
                movq %rsi, 32(%rsp)                # wh arr
                movq %rdx, 40(%rsp)                # wl arr
                movl %ecx, %r12d                   # m
                movl %r8d, %r13d                   # k
                call vk64_ensure_init
                testl %eax, %eax
                jz .Lvk64_wps_1
                movl $-1, %eax
                jmp .Lvk64_wps_ret
            .Lvk64_wps_1:
                cmpq $0, vk64_pipeSplit(%rip)
                jne .Lvk64_wps_2
                movl $-6, %eax
                jmp .Lvk64_wps_ret
            .Lvk64_wps_2:
                testl %ebx, %ebx
                js .Lvk64_wps_bad
                cmpl $192, %ebx
                jl .Lvk64_wps_3
            .Lvk64_wps_bad:
                movl $-2, %eax
                jmp .Lvk64_wps_ret
            .Lvk64_wps_3:
                movl %r12d, %eax
                movl %r13d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                testq %rax, %rax
                jle .Lvk64_wps_neg
                // wh: 4B
                movslq %ebx, %rcx
                shlq $3, %rcx
                cmpq vk64_whcaps(%rcx), %rax
                jge .Lvk64_wps_wl
                movq vk64_whmaps(%rcx), %rdi
                testq %rdi, %rdi
                jz .Lvk64_wps_whu
                movq g_vk64_vkUnmapMemory(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_whmems(%rcx), %rsi
                call *%rax
            .Lvk64_wps_whu:
                movq vk64_whbufs(%rcx), %rdi
                testq %rdi, %rdi
                jz .Lvk64_wps_whd
                movq g_vk64_vkDestroyBuffer(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_whbufs(%rcx), %rsi
                xorl %edx, %edx
                call *%rax
            .Lvk64_wps_whd:
                movq vk64_whmems(%rcx), %rdi
                testq %rdi, %rdi
                jz .Lvk64_wps_whf
                movq g_vk64_vkFreeMemory(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_whmems(%rcx), %rsi
                xorl %edx, %edx
                call *%rax
            .Lvk64_wps_whf:
                movq $0, vk64_whbufs(%rcx)
                movq $0, vk64_whmems(%rcx)
                movq $0, vk64_whmaps(%rcx)
                movq $0, vk64_whcaps(%rcx)
                movl %r12d, %eax
                movl %r13d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                shlq $2, %rax
                movq %rax, %rdi
                movslq %ebx, %rax
                shlq $3, %rax
                leaq vk64_whbufs(%rax), %rsi
                leaq vk64_whmems(%rax), %rdx
                leaq vk64_whmaps(%rax), %rcx
                call vk64_alloc_buffer
                testl %eax, %eax
                jz .Lvk64_wps_whok
                movl $-4, %eax
                jmp .Lvk64_wps_ret
            .Lvk64_wps_whok:
                movl %r12d, %eax
                movl %r13d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                movslq %ebx, %rcx
                shlq $3, %rcx
                movq %rax, vk64_whcaps(%rcx)
            .Lvk64_wps_wl:
                movl %r12d, %eax
                movl %r13d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                movslq %ebx, %rcx
                shlq $3, %rcx
                cmpq vk64_wlcaps(%rcx), %rax
                jge .Lvk64_wps_copy
                movq vk64_wlmaps(%rcx), %rdi
                testq %rdi, %rdi
                jz .Lvk64_wps_wlu
                movq g_vk64_vkUnmapMemory(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_wlmems(%rcx), %rsi
                call *%rax
            .Lvk64_wps_wlu:
                movq vk64_wlbufs(%rcx), %rdi
                testq %rdi, %rdi
                jz .Lvk64_wps_wld
                movq g_vk64_vkDestroyBuffer(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_wlbufs(%rcx), %rsi
                xorl %edx, %edx
                call *%rax
            .Lvk64_wps_wld:
                movq vk64_wlmems(%rcx), %rdi
                testq %rdi, %rdi
                jz .Lvk64_wps_wlf
                movq g_vk64_vkFreeMemory(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_wlmems(%rcx), %rsi
                xorl %edx, %edx
                call *%rax
            .Lvk64_wps_wlf:
                movq $0, vk64_wlbufs(%rcx)
                movq $0, vk64_wlmems(%rcx)
                movq $0, vk64_wlmaps(%rcx)
                movq $0, vk64_wlcaps(%rcx)
                movl %r12d, %eax
                movl %r13d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                shlq $2, %rax
                movq %rax, %rdi
                movslq %ebx, %rax
                shlq $3, %rax
                leaq vk64_wlbufs(%rax), %rsi
                leaq vk64_wlmems(%rax), %rdx
                leaq vk64_wlmaps(%rax), %rcx
                call vk64_alloc_buffer
                testl %eax, %eax
                jz .Lvk64_wps_wlok
                movl $-4, %eax
                jmp .Lvk64_wps_ret
            .Lvk64_wps_wlok:
                movl %r12d, %eax
                movl %r13d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                movslq %ebx, %rcx
                shlq $3, %rcx
                movq %rax, vk64_wlcaps(%rcx)
            .Lvk64_wps_copy:
                // wh e wl: m*k*4 bytes cada
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
                movq vk64_whmaps(%rax), %rdi
                call memcpy@PLT
                movl %r12d, %eax
                movl %r13d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                shlq $2, %rax
                movq %rax, %rdx
                movq 40(%rsp), %rsi
                leaq 24(%rsi), %rsi
                movslq %ebx, %rax
                shlq $3, %rax
                movq vk64_wlmaps(%rax), %rdi
                call memcpy@PLT
                xorl %eax, %eax
            .Lvk64_wps_ret:
                addq $56, %rsp
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lvk64_wps_neg:
                movl $-3, %eax
                jmp .Lvk64_wps_ret
            """);
    }
    // kof_mv64_wrunsp(rdi=id, rsi=x arr, rdx=y arr, ecx=m, r8d=k, r9=div)
    // bindings: 0=wh[id] 1=wl[id] 2=xh 3=xl 4=y; host computa xh/xl.
    // Stack: 32(%rsp)=k, 40(%rsp)=x arr, 48(%rsp)=y arr, 56(%rsp)=m.
    static void sourceWrunsp(StringBuilder sb) {
        sb.append("""
            .globl kof_mv64_wrunsp
            .type kof_mv64_wrunsp, @function
            kof_mv64_wrunsp:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $64, %rsp
                movl %edi, %ebx                    # id
                movq %rsi, 40(%rsp)                # x arr
                movq %rdx, 48(%rsp)                # y arr
                movl %ecx, 56(%rsp)                # m
                movl %r8d, 32(%rsp)                # k
                movq %r9, %r14                     # div
                call vk64_ensure_init
                testl %eax, %eax
                jz .Lvk64_wrs1
                movl $-1, %eax
                jmp .Lvk64_wrsr
            .Lvk64_wrs1:
                cmpq $0, vk64_pipeSplit(%rip)
                je .Lvk64_wrs_nospv
                cmpq $0, vk64_pl5(%rip)
                je .Lvk64_wrs_nospv
                cmpq $0, vk64_dset5(%rip)
                jne .Lvk64_wrs2
            .Lvk64_wrs_nospv:
                movl $-6, %eax
                jmp .Lvk64_wrsr
            .Lvk64_wrs2:
                testl %ebx, %ebx
                js .Lvk64_wrs_bad
                cmpl $192, %ebx
                jl .Lvk64_wrs3
            .Lvk64_wrs_bad:
                movl $-2, %eax
                jmp .Lvk64_wrsr
            .Lvk64_wrs3:
                movl 32(%rsp), %eax
                movl 56(%rsp), %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                movslq %ebx, %rcx
                shlq $3, %rcx
                cmpq $0, vk64_whbufs(%rcx)
                je .Lvk64_wrs_nobuf
                cmpq $0, vk64_wlbufs(%rcx)
                je .Lvk64_wrs_nobuf
                cmpq vk64_whcaps(%rcx), %rax
                jg .Lvk64_wrs_nobuf
                cmpq vk64_wlcaps(%rcx), %rax
                jg .Lvk64_wrs_nobuf
                jmp .Lvk64_wrs_xh
            .Lvk64_wrs_nobuf:
                movl $-3, %eax
                jmp .Lvk64_wrsr
            // xh buffer (k elems i32)
            .Lvk64_wrs_xh:
                movl 32(%rsp), %eax
                movslq %eax, %rax
                cmpq vk64_xhcap(%rip), %rax
                jge .Lvk64_wrs_xl
                movq vk64_xhmap(%rip), %rdi
                testq %rdi, %rdi
                jz .Lvk64_wrs_xhu
                movq g_vk64_vkUnmapMemory(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_xhmem(%rip), %rsi
                call *%rax
            .Lvk64_wrs_xhu:
                movq vk64_xhbuf(%rip), %rdi
                testq %rdi, %rdi
                jz .Lvk64_wrs_xhd
                movq g_vk64_vkDestroyBuffer(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_xhbuf(%rip), %rsi
                xorl %edx, %edx
                call *%rax
            .Lvk64_wrs_xhd:
                movq vk64_xhmem(%rip), %rdi
                testq %rdi, %rdi
                jz .Lvk64_wrs_xhf
                movq g_vk64_vkFreeMemory(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_xhmem(%rip), %rsi
                xorl %edx, %edx
                call *%rax
            .Lvk64_wrs_xhf:
                movq $0, vk64_xhbuf(%rip)
                movq $0, vk64_xhmem(%rip)
                movq $0, vk64_xhmap(%rip)
                movl 32(%rsp), %eax
                movslq %eax, %rax
                shlq $2, %rax
                movq %rax, %rdi
                leaq vk64_xhbuf(%rip), %rsi
                leaq vk64_xhmem(%rip), %rdx
                leaq vk64_xhmap(%rip), %rcx
                call vk64_alloc_buffer
                testl %eax, %eax
                jz .Lvk64_wrs_xhok
                movl $-4, %eax
                jmp .Lvk64_wrsr
            .Lvk64_wrs_xhok:
                movl 32(%rsp), %eax
                movslq %eax, %rax
                movq %rax, vk64_xhcap(%rip)
            // xl buffer
            .Lvk64_wrs_xl:
                movl 32(%rsp), %eax
                movslq %eax, %rax
                cmpq vk64_xlcap(%rip), %rax
                jge .Lvk64_wrs_y
                movq vk64_xlmap(%rip), %rdi
                testq %rdi, %rdi
                jz .Lvk64_wrs_xlu
                movq g_vk64_vkUnmapMemory(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_xlmem(%rip), %rsi
                call *%rax
            .Lvk64_wrs_xlu:
                movq vk64_xlbuf(%rip), %rdi
                testq %rdi, %rdi
                jz .Lvk64_wrs_xld
                movq g_vk64_vkDestroyBuffer(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_xlbuf(%rip), %rsi
                xorl %edx, %edx
                call *%rax
            .Lvk64_wrs_xld:
                movq vk64_xlmem(%rip), %rdi
                testq %rdi, %rdi
                jz .Lvk64_wrs_xlf
                movq g_vk64_vkFreeMemory(%rip), %rax
                movq vk64_dev(%rip), %rdi
                movq vk64_xlmem(%rip), %rsi
                xorl %edx, %edx
                call *%rax
            .Lvk64_wrs_xlf:
                movq $0, vk64_xlbuf(%rip)
                movq $0, vk64_xlmem(%rip)
                movq $0, vk64_xlmap(%rip)
                movl 32(%rsp), %eax
                movslq %eax, %rax
                shlq $2, %rax
                movq %rax, %rdi
                leaq vk64_xlbuf(%rip), %rsi
                leaq vk64_xlmem(%rip), %rdx
                leaq vk64_xlmap(%rip), %rcx
                call vk64_alloc_buffer
                testl %eax, %eax
                jz .Lvk64_wrs_xlok
                movl $-4, %eax
                jmp .Lvk64_wrsr
            .Lvk64_wrs_xlok:
                movl 32(%rsp), %eax
                movslq %eax, %rax
                movq %rax, vk64_xlcap(%rip)
            // y (m elems i64): cresce via vk64_shape_xy
            .Lvk64_wrs_y:
                movl 56(%rsp), %eax
                movslq %eax, %rax
                cmpq vk64_ycap(%rip), %rax
                jle .Lvk64_wrs_split
                movl 56(%rsp), %edi
                movl 32(%rsp), %esi
                call vk64_shape_xy
                testl %eax, %eax
                jz .Lvk64_wrs_split
                movl $-4, %eax
                jmp .Lvk64_wrsr
            // host split: xh[i]=x/1e6, xl[i]=x%1e6 (trunc idiv)
            .Lvk64_wrs_split:
                movq 40(%rsp), %rax
                leaq 24(%rax), %r15                # x data
                movq vk64_xhmap(%rip), %r13        # xh
                movq vk64_xlmap(%rip), %r12        # xl
                xorl %r14d, %r14d                  # i
            .Lvk64_wrs_sp:
                cmpl 32(%rsp), %r14d
                jge .Lvk64_wrs_spdone
                movq (%r15,%r14,8), %rax           # x[i]
                movq $1000000, %rcx
                cqto
                idivq %rcx
                movl %eax, (%r13,%r14,4)
                movl %edx, (%r12,%r14,4)
                incl %r14d
                jmp .Lvk64_wrs_sp
            .Lvk64_wrs_spdone:
                movl 56(%rsp), %eax
                movslq %eax, %rax
                shlq $3, %rax
                movq %rax, %rdx
                xorl %esi, %esi
                movq vk64_ymap(%rip), %rdi
                call memset@PLT
                // dbi[5] @5424+320
                movslq %ebx, %rax
                shlq $3, %rax
                leaq 5424+320+vk64_scratch(%rip), %rdi
                movq vk64_whbufs(%rax), %rcx
                movq %rcx, 0(%rdi)
                movq $0, 8(%rdi)
                movq vk64_whcaps(%rax), %rcx
                shlq $2, %rcx
                movq %rcx, 16(%rdi)
                movq vk64_wlbufs(%rax), %rcx
                movq %rcx, 24(%rdi)
                movq $0, 32(%rdi)
                movq vk64_wlcaps(%rax), %rcx
                shlq $2, %rcx
                movq %rcx, 40(%rdi)
                movq vk64_xhbuf(%rip), %rcx
                movq %rcx, 48(%rdi)
                movq $0, 56(%rdi)
                movq vk64_xhcap(%rip), %rcx
                shlq $2, %rcx
                movq %rcx, 64(%rdi)
                movq vk64_xlbuf(%rip), %rcx
                movq %rcx, 72(%rdi)
                movq $0, 80(%rdi)
                movq vk64_xlcap(%rip), %rcx
                shlq $2, %rcx
                movq %rcx, 88(%rdi)
                movq vk64_ybuf(%rip), %rcx
                movq %rcx, 96(%rdi)
                movq $0, 104(%rdi)
                movq vk64_ycap(%rip), %rcx
                shlq $3, %rcx
                movq %rcx, 112(%rdi)
                movq vk64_dset5(%rip), %rdi
                leaq 5424+320+vk64_scratch(%rip), %rsi
                movl $5, %edx
                call vk64_write_desc
                // pcs[6] = {m, k, divId, 0, div}
                movl 56(%rsp), %eax
                movl %eax, 5296+vk64_scratch(%rip)
                movl 32(%rsp), %eax
                movl %eax, 5300+vk64_scratch(%rip)
                movl $2, 5304+vk64_scratch(%rip)
                movl $0, 5308+vk64_scratch(%rip)
                movq $1000000000, %rax
                cmpq %rax, %r14
                jne .Lvk64_wrs_d1
                movl $0, 5304+vk64_scratch(%rip)
                jmp .Lvk64_wrs_go
            .Lvk64_wrs_d1:
                movq $1000000, %rax
                cmpq %rax, %r14
                jne .Lvk64_wrs_go
                movl $1, 5304+vk64_scratch(%rip)
            .Lvk64_wrs_go:
                movq %r14, 5312+vk64_scratch(%rip)
                movq vk64_pipeSplit(%rip), %rdi
                movq vk64_pl5(%rip), %rsi
                movq vk64_dset5(%rip), %rdx
                leaq 5296+vk64_scratch(%rip), %rcx
                movl 56(%rsp), %r8d
                call vk64_submit
                testl %eax, %eax
                jnz .Lvk64_wrsr
                // y ← ymap
                movl 56(%rsp), %eax
                movslq %eax, %rax
                shlq $3, %rax
                movq %rax, %rdx
                movq vk64_ymap(%rip), %rsi
                movq 48(%rsp), %rdi
                addq $24, %rdi
                call memcpy@PLT
                xorl %eax, %eax
            .Lvk64_wrsr:
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
