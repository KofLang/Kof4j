package dev.kof.compiler;

/**
 * M36.5: tradução do vkchain64.c (C) para assembly x86-64 emitido
 * aqui — o native ganha o GPU path int64 (matvec residente, w32 e
 * split) sem lib C compilada do gcc. O .s gerado dlopena a
 * libvulkan.so.1 e chama a API vk* via dlsym (mesma cadeia do C:
 * instance → physical 0 → device → shader module → desc layouts
 * 3/5 → pipelines → buffers host-visible coherent mapeados →
 * descriptor sets → cmd buffer → dispatch + fence).
 *
 * Contrato do C preservado (os símbolos kof_mv64_* são o que o
 * programa kf chama; arrays kf = KofArray* com dados inline em +24):
 *   kof_mv64_set_shape(m, k)                       → 0 ok / rc
 *   kof_mv64_load_w(w*, m, k)                      → 0 ok
 *   kof_mv64_matvec(x*, y*, m, k)                  → 0 ok
 *   kof_mv64_wput(id, w*, m, k)                    → 0 ok
 *   kof_mv64_wrun(id, x*, y*, m, k, div)           → 0 ok
 *   kof_mv64_wput32(id, w*, m, k)                  → 0 ok
 *   kof_mv64_wrun32(id, x*, y*, m, k, div)         → 0 ok
 *   kof_mv64_wputsp(id, wh*, wl*, m, k)            → 0 ok
 *   kof_mv64_wrunsp(id, x*, y*, m, k, div)         → 0 ok
 *   kof_vk_dispatch64(a*, b*, c*, m, n, k)         → 0 ok
 * Qualquer falha → rc != 0 e o caller degrada p/ golden CPU
 * (nunca derruba o programa).
 *
 * SPVs: env KOF_GPU_SPV64 (matvec64, obrigatório p/ o mv64),
 * KOF_GPU_SPV64_W32 e KOF_GPU_SPV64_SPLIT (opcionais; pipeline
 * ausente → wrun32/wrunsp retornam rc != 0 e o caller usa CPU).
 */
final class VkChain64Asm {
    private VkChain64Asm() {}

    /** Injeta o .s no runtime nativo (substitui os stubs kof_mv64_*). */
    static String source() {
        StringBuilder sb = new StringBuilder(64 * 1024);
        VkChain64Data.source(sb);
        VkChain64Loader.source(sb);
        VkChain64Helpers.source(sb);
        VkChain64Init.source(sb);
        VkChain64Alloc.source(sb);
        VkChain64Submit.sourceSubmit(sb);
        VkChain64Submit.sourceWriteDesc(sb);
        VkChain64Shape.sourceShapeXy(sb);
        VkChain64Shape.sourceSetShape(sb);
        VkChain64Matvec.sourceLoadW(sb);
        VkChain64Matvec.sourceMatvec(sb);
        VkChain64W64.sourceWput(sb);
        VkChain64W64.sourceWrun(sb);
        VkChain64W32.sourceWput32(sb);
        VkChain64W32.sourceWrun32(sb);
        VkChain64WSp.sourceWputsp(sb);
        VkChain64WSp.sourceWrunsp(sb);
        emitDispatch64(sb);
        return sb.toString();
    }























    // kof_vk_dispatch64(rdi=a arr, rsi=b arr, rdx=c arr, ecx=m, r8d=n, r9d=k)
    // matmul64.spv: 3 SSBOs + push 12B {m,n,k}; 1 WG por elemento c.
    private static void emitDispatch64(StringBuilder sb) {
        sb.append("""
            .globl kof_vk_dispatch64
            .type kof_vk_dispatch64, @function
            kof_vk_dispatch64:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $72, %rsp
                movq %rdi, 32(%rsp)                # a arr
                movq %rsi, 40(%rsp)                # b arr
                movq %rdx, 48(%rsp)                # c arr
                movl %ecx, %ebx                    # m
                movl %r8d, %r12d                   # n
                movl %r9d, %r13d                   # k
                // lazy init (mesma cadeia do wrun, mas com dset12:
                // um segundo dpool/dset sobre o dsl de 3 binds)
                cmpl $0, vk64_inited(%rip)
                jne .Lvk64_d64_1
                leaq .Lvkv_sov64(%rip), %rdi
                call getenv@PLT
                testq %rax, %rax
                jz .Lvk64_d64_fb
                movq %rax, %rdi
                call vk64_init_common              # init com 3 binds
                testl %eax, %eax
                jnz .Lvk64_d64_fb
            .Lvk64_d64_1:
                // a: m*k*8, b: k*n*8, c: m*n*8 — buffers persistentes
                // abuf/bbuf/cbuf no scratch externo? usar globals extras:
                // (alocados no final do .bss)
                movl %ebx, %eax
                movl %r13d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                shlq $3, %rax
                cmpq vk64_d64acap(%rip), %rax
                jle .Lvk64_d64_b
                movq %rax, %rdi
                leaq vk64_d64abuf(%rip), %rsi
                leaq vk64_d64amem(%rip), %rdx
                leaq vk64_d64amap(%rip), %rcx
                call vk64_alloc_buffer
                testl %eax, %eax
                jnz .Lvk64_d64_fb
                movl %ebx, %eax
                movl %r13d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                movq %rax, vk64_d64acap(%rip)
            .Lvk64_d64_b:
                movl %r13d, %eax
                movl %r12d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                shlq $3, %rax
                cmpq vk64_d64bcap(%rip), %rax
                jle .Lvk64_d64_c
                movq %rax, %rdi
                leaq vk64_d64bbuf(%rip), %rsi
                leaq vk64_d64bmem(%rip), %rdx
                leaq vk64_d64bmap(%rip), %rcx
                call vk64_alloc_buffer
                testl %eax, %eax
                jnz .Lvk64_d64_fb
                movl %r13d, %eax
                movl %r12d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                movq %rax, vk64_d64bcap(%rip)
            .Lvk64_d64_c:
                movl %ebx, %eax
                movl %r12d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                shlq $3, %rax
                cmpq vk64_d64ccap(%rip), %rax
                jle .Lvk64_d64_copy
                movq %rax, %rdi
                leaq vk64_d64cbuf(%rip), %rsi
                leaq vk64_d64cmem(%rip), %rdx
                leaq vk64_d64cmap(%rip), %rcx
                call vk64_alloc_buffer
                testl %eax, %eax
                jnz .Lvk64_d64_fb
                movl %ebx, %eax
                movl %r12d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                movq %rax, vk64_d64ccap(%rip)
            .Lvk64_d64_copy:
                // a e b → maps (c: só ymap=0? o shader escreve c todo:
                // memset c)
                movl %ebx, %eax
                movl %r13d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                shlq $3, %rax
                movq %rax, %rdx
                movq 32(%rsp), %rsi
                leaq 24(%rsi), %rsi
                movq vk64_d64amap(%rip), %rdi
                call memcpy@PLT
                movl %r13d, %eax
                movl %r12d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                shlq $3, %rax
                movq %rax, %rdx
                movq 40(%rsp), %rsi
                leaq 24(%rsi), %rsi
                movq vk64_d64bmap(%rip), %rdi
                call memcpy@PLT
                // dbi[3] (a, b, c) + write_desc no dset12
                leaq 5424+320+vk64_scratch(%rip), %rdi
                movq vk64_d64abuf(%rip), %rcx
                movq %rcx, 0(%rdi)
                movq $0, 8(%rdi)
                movq vk64_d64acap(%rip), %rcx
                shlq $3, %rcx
                movq %rcx, 16(%rdi)
                movq vk64_d64bbuf(%rip), %rcx
                movq %rcx, 24(%rdi)
                movq $0, 32(%rdi)
                movq vk64_d64bcap(%rip), %rcx
                shlq $3, %rcx
                movq %rcx, 40(%rdi)
                movq vk64_d64cbuf(%rip), %rcx
                movq %rcx, 48(%rdi)
                movq $0, 56(%rdi)
                movq vk64_d64ccap(%rip), %rcx
                shlq $3, %rcx
                movq %rcx, 64(%rdi)
                movq vk64_dset12(%rip), %rdi
                leaq 5424+320+vk64_scratch(%rip), %rsi
                movl $3, %edx
                call vk64_write_desc
                // pcs {m, n, k}
                movl %ebx, 5296+vk64_scratch(%rip)
                movl %r12d, 5300+vk64_scratch(%rip)
                movl %r13d, 5304+vk64_scratch(%rip)
                // dispatch: 1 WG por elemento c → (m*n+63)/64 WGs 1D
                movl %ebx, %eax
                movl %r12d, %ecx
                imull %ecx, %eax
                movl %eax, %r14d
                addl $63, %r14d
                shrl $6, %r14d
                cmpq $0, vk64_pipe12(%rip)
                je .Lvk64_d64_fb
                movq vk64_pipe12(%rip), %rdi
                movq vk64_pl(%rip), %rsi
                movq vk64_dset12(%rip), %rdx
                leaq 5296+vk64_scratch(%rip), %rcx
                movl %r14d, %r8d
                call vk64_submit
                testl %eax, %eax
                jnz .Lvk64_d64_fb
                // c ← cmap
                movl %ebx, %eax
                movl %r12d, %ecx
                imull %ecx, %eax
                movslq %eax, %rax
                shlq $3, %rax
                movq %rax, %rdx
                movq vk64_d64cmap(%rip), %rsi
                movq 48(%rsp), %rdi
                addq $24, %rdi
                call memcpy@PLT
                xorl %eax, %eax
                jmp .Lvk64_d64_ret
            .Lvk64_d64_fb:
                movl $-1, %eax
            .Lvk64_d64_ret:
                addq $72, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }
}
