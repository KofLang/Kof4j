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
        emitLoadW(sb);
        emitMatvec(sb);
        emitWput(sb);
        emitWrun(sb);
        emitWput32(sb);
        emitWrun32(sb);
        emitWputsp(sb);
        emitWrunsp(sb);
        emitDispatch64(sb);
        return sb.toString();
    }













    // kof_mv64_load_w(rdi=w arr, esi=m, edx=k) — memcpy p/ wmap
    private static void emitLoadW(StringBuilder sb) {
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
    private static void emitMatvec(StringBuilder sb) {
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

    // kof_mv64_wput(rdi=id, rsi=w arr, edx=m, ecx=k)
    private static void emitWput(StringBuilder sb) {
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
    private static void emitWrun(StringBuilder sb) {
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


    // kof_mv64_wput32(rdi=id, rsi=w i32 arr, edx=m, ecx=k)
    private static void emitWput32(StringBuilder sb) {
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
    private static void emitWrun32(StringBuilder sb) {
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

    // kof_mv64_wputsp(rdi=id, rsi=wh arr, rdx=wl arr, ecx=m, r8d=k)
    private static void emitWputsp(StringBuilder sb) {
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
    private static void emitWrunsp(StringBuilder sb) {
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
