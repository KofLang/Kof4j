package dev.kof.compiler.nat;

// G-1/G-2 (NATIVE002 face 1, 15/09): memória riscv64 — kof_alloc com free-list
// (port do RuntimeMemory.emitAlloc/emitFree do x86 sobre o header de bloco de
// 32B do G-0) + kof_free + kof_memstats (contadores + print) + G-2: cada bloco
// novo entra na gc-list global (`.Lkof_gc_head`, LIFO, flags=0) e kof_gc_dump
// despeja a lista (`gc <size> <flags>` por linha — a alavanca de observação do
// G-2/G-3). kof_alloc SAIU de Rt0 (que ficou ≤500) e vive aqui com o lock da
// free-list e os contadores. aarch64 herda linha-a-linha no tradutor
// (amoswap.w→swpal, amoadd.d→ldadd).
//
// ⚠️ Correção doc-vs-realidade (15/09): o plano do G-1 dizia "wire free into
// the log nodes (RtB0, mirroring RuntimeLog2:98)" — MEDIDO: o nó de log riscv
// (NativeRiscvAsmRtB0:242) escreve label+msg direto por write(), NÃO aloca, e
// NENHUMA peça riscv chama kof_free. Logo não há caminho Kof para exercitar o
// free: a prova do G-1 é um harness asm cru (NativeRiscvGcFreeListTest) que
// concatena o runtime de PRODUÇÃO e faz alloc/free/alloc exigindo o reuso.
public final class NativeRiscvAsmRtB42 {

    private NativeRiscvAsmRtB42() {}

    static  String RISCV_RUNTIME_ASM_B_42 = """
            .section .bss
            .align 2
            .Lkof_alloc_lock: .word 0
            .align 3
            .Lkof_free_head: .quad 0
            .Lkof_gc_head: .quad 0
            .Lkof_alloc_count: .quad 0
            .Lkof_free_count: .quad 0
            .Lkof_alloc_bytes: .quad 0
            .Lkof_free_bytes: .quad 0

            .section .text
            # kof_alloc(size@a0) -> ptr de uso.
            # G-1: procura primeiro na free list (first-fit, LIFO) um bloco com
            # size >= align16(size)+32; achando, desenfileira e reusa (flags=0);
            # senão bump atômico (amoadd.d) no .bss com guard OOM (G-0).
            # Lock (spin amoswap.w): a free list e os contadores são estado
            # GLOBAL compartilhado entre main e workers do spawn (o bump era
            # atômico; a busca na lista não). Custo ~0 com a lista vazia.
            .globl kof_alloc
            kof_alloc:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                sd   s4, 16(sp)
                addi s0, a0, 15
                andi s0, s0, -16
                addi s0, s0, 32          # total = align16(size)+header
                # G-4: tick do coletor na ENTRADA (antes do lock; o kof_gc_collect
                # toma o lock ele mesmo no sweep). s0 é callee-saved e sobrevive.
                li   s4, 0               # s4 = já tentou collect_now no OOM?
                call kof_gc_collect
                la   s1, .Lkof_alloc_lock
            .Lkof_alloc_spin:
                li   t0, 1
                amoswap.w t1, t0, (s1)
                bnez t1, .Lkof_alloc_spin
            .Lkof_alloc_fsearch:
                la   s2, .Lkof_free_head
                ld   s3, 0(s2)           # cur
                li   t5, 0               # prev
            .Lkof_alloc_fsearch_loop:
                beqz s3, .Lkof_alloc_bump
                ld   t2, 0(s3)           # block size
                bltu t2, s0, .Lkof_alloc_fnext
                ld   t3, 8(s3)           # next
                beqz t5, .Lkof_alloc_fhead
                sd   t3, 8(t5)
                j    .Lkof_alloc_found
            .Lkof_alloc_fhead:
                sd   t3, 0(s2)
            .Lkof_alloc_found:
                sd   zero, 24(s3)        # fora da free list (flags=0)
                mv   a0, s3              # §540: registra início-de-bloco
                call kof_bm_set          #        no bitmap O(1) do GC
                la   t4, .Lkof_alloc_count
                ld   t2, 0(t4)
                addi t2, t2, 1
                sd   t2, 0(t4)
                la   t4, .Lkof_alloc_bytes
                ld   t2, 0(t4)
                add  t2, t2, s0
                sd   t2, 0(t4)
                addi a0, s3, 32
                j    .Lkof_alloc_unlock
            .Lkof_alloc_fnext:
                mv   t5, s3
                ld   s3, 8(s3)
                j    .Lkof_alloc_fsearch_loop
            .Lkof_alloc_bump:
                la   t2, kof_alloc_ptr
                amoadd.d t0, s0, (t2)    # t0 = base do bloco
                # R6: bump com bounds-check (G-0); o header triplica o consumo.
                la   t3, _kof_heap_end
                add  t4, t0, s0
                bltu t4, t3, .Lkof_alloc_bok
                beq  t4, t3, .Lkof_alloc_bok
                # G-4: arena esgotada. Solta o lock e roda o coletor UMA vez;
                # se ele devolver blocos à free-list, re-tenta a busca (o bump
                # já avançou, mas a busca acha o reuso). Só panic se ainda faltar.
                sw   zero, 0(s1)         # unlock
                bnez s4, .Lkof_alloc_oom
                li   s4, 1
                call kof_gc_collect_now
                la   s1, .Lkof_alloc_lock
            .Lkof_alloc_respin:
                li   t0, 1
                amoswap.w t1, t0, (s1)
                bnez t1, .Lkof_alloc_respin
                j    .Lkof_alloc_fsearch
            .Lkof_alloc_oom:
                la   a0, .Lstr_oom
                call kof_panic
            .Lkof_alloc_bok:
                sd   s0, 0(t0)           # size total
                sd   zero, 8(t0)         # free_next = 0
                mv   a0, t0              # §540: registra início-de-bloco
                call kof_bm_set          #        no bitmap O(1) do GC
                mv   t0, a0              # a0 preservado por kof_bm_set
                # G-2: entra na gc-list global (LIFO) com flags=0.
                la   t2, .Lkof_gc_head
                ld   t3, 0(t2)
                sd   t3, 16(t0)          # gc_next = cabeca antiga
                sd   t0, 0(t2)           # head = bloco novo
                sd   zero, 24(t0)        # flags = 0 (bit0 mark, bit1 free-list)
                la   t4, .Lkof_alloc_count
                ld   t2, 0(t4)
                addi t2, t2, 1
                sd   t2, 0(t4)
                la   t4, .Lkof_alloc_bytes
                ld   t2, 0(t4)
                add  t2, t2, s0
                sd   t2, 0(t4)
                addi a0, t0, 32
            .Lkof_alloc_unlock:
                sw   zero, 0(s1)         # unlock
                ld   s0, 48(sp)
                ld   s1, 40(sp)
                ld   s2, 32(sp)
                ld   s3, 24(sp)
                ld   s4, 16(sp)
                ld   ra, 56(sp)
                addi sp, sp, 64
                ret

            # §540: kof_bm_set(block_start@a0) — seta o bit de início-de-bloco
            # no bitmap global (_kof_block_bm, 1 bit por 16B), para o GC achar
            # um bloco em O(1) (antes: varredura linear da gc-list capada em
            # 10000 -> use-after-free). Não escreve a0 (o chamador o recupera).
            # Clobbers apenas t0-t6.
            .globl kof_bm_set
            kof_bm_set:
                la   t0, _kof_heap
                sub  t1, a0, t0
                srli t2, t1, 10          # índice da palavra do bitmap
                slli t2, t2, 3
                la   t3, _kof_block_bm
                add  t3, t3, t2
                ld   t4, 0(t3)
                srli t5, t1, 4
                andi t5, t5, 63          # bit na palavra
                li   t6, 1
                sll  t6, t6, t5
                or   t4, t4, t6
                sd   t4, 0(t3)
                ret

            # kof_free(ptr@a0) — devolve o bloco à free list (LIFO), marca
            # flags bit1 (na lista) e conta. Port do RuntimeMemory.emitFree.
            .globl kof_free
            kof_free:
                beqz a0, .Lkof_free_done
                addi t0, a0, -32
                ld   t1, 0(t0)           # size
                li   t2, 2
                sd   t2, 24(t0)          # flags = 2 (bit1: na free list)
                la   t3, .Lkof_alloc_lock
            .Lkof_free_spin:
                li   t4, 1
                amoswap.w t5, t4, (t3)
                bnez t5, .Lkof_free_spin
                la   t4, .Lkof_free_head
                ld   t5, 0(t4)
                sd   t5, 8(t0)           # node.next = head
                sd   t0, 0(t4)           # head = node
                la   t4, .Lkof_free_count
                ld   t5, 0(t4)
                addi t5, t5, 1
                sd   t5, 0(t4)
                la   t4, .Lkof_free_bytes
                ld   t5, 0(t4)
                add  t5, t5, t1
                sd   t5, 0(t4)
                sw   zero, 0(t3)         # unlock
            .Lkof_free_done:
                ret

            # kof_memstats() — imprime allocs/frees/live bytes (paridade de
            # forma com o x86). A leitura dos contadores é feita ANTES do
            # kof_long_to_string que aloca, então os números refletem o estado
            # no momento da chamada (não o das strings de saída).
            .globl kof_memstats
            kof_memstats:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                la   a0, .Lms_lbl_alloc
                call .Lms_puts
                la   t0, .Lkof_alloc_count
                ld   a0, 0(t0)
                call kof_long_to_string
                call kof_print_string
                la   a0, .Lms_nl
                call .Lms_puts
                la   a0, .Lms_lbl_free
                call .Lms_puts
                la   t0, .Lkof_free_count
                ld   a0, 0(t0)
                call kof_long_to_string
                call kof_print_string
                la   a0, .Lms_nl
                call .Lms_puts
                la   a0, .Lms_lbl_live
                call .Lms_puts
                la   t0, .Lkof_alloc_bytes
                ld   s0, 0(t0)
                la   t0, .Lkof_free_bytes
                ld   t0, 0(t0)
                sub  a0, s0, t0
                call kof_long_to_string
                call kof_print_string
                la   a0, .Lms_nl
                call .Lms_puts
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            # kof_gc_dump() — G-2 (NATIVE002 face 1): despeja a gc-list na
            # saída padrão, uma linha por bloco: `gc <size_total> <flags>`.
            # É a alavanca de observação do G-2 (listagem) e do G-3 (mark bit
            # aparece no <flags>). NÃO aloca (usa .Lgc_put_uint em buffer de
            # pilha), então não perturba a lista que percorre. O plano chama
            # isto de "KOF_GC_DEBUG dump" — no asm riscv puro o gatilho é a
            # chamada explícita (não há env-var), honesto e testável.
            .globl kof_gc_dump
            kof_gc_dump:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                la   s0, .Lkof_gc_head
                ld   s1, 0(s0)
            .Lgcd_loop:
                beqz s1, .Lgcd_done
                la   a0, .Lms_lbl_gc
                call .Lms_puts
                ld   a0, 0(s1)
                call .Lgc_put_uint
                la   a0, .Lms_sp
                call .Lms_puts
                ld   a0, 24(s1)
                call .Lgc_put_uint
                la   a0, .Lms_nl
                call .Lms_puts
                ld   s1, 16(s1)          # gc_next
                j    .Lgcd_loop
            .Lgcd_done:
                ld   s1, 8(sp)
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            # helper local: a0 = uint -> write(1, decimal). Buffer de 24B na
            # pilha (sem alloc); clobbera t0..t3, a0/a1/a2.
            .Lgc_put_uint:
                addi sp, sp, -48
                sd   ra, 32(sp)
                addi t0, sp, 31
                sb   zero, 0(t0)
                mv   t1, a0
                li   t2, 10
                beqz t1, .Lgpu_zero
            .Lgpu_loop:
                rem  t3, t1, t2
                addi t3, t3, 48
                addi t0, t0, -1
                sb   t3, 0(t0)
                div  t1, t1, t2
                bnez t1, .Lgpu_loop
                j    .Lgpu_write
            .Lgpu_zero:
                addi t0, t0, -1
                li   t3, 48
                sb   t3, 0(t0)
            .Lgpu_write:
                addi a2, sp, 31
                sub  a2, a2, t0
                mv   a1, t0
                li   a0, 1
                call kof_plat_write
                ld   ra, 32(sp)
                addi sp, sp, 48
                ret

            # helper local: a0 = asciz -> write(1, ...). Não faz call; clobbera
            # t0..t3 e a0/a1/a2 (caller-saved).
            .Lms_puts:
                addi sp, sp, -16
                sd   ra, 8(sp)
                mv   t0, a0
                li   t1, 0
            .Lms_puts_len:
                add  t2, t0, t1
                lbu  t3, 0(t2)
                beqz t3, .Lms_puts_w
                addi t1, t1, 1
                j    .Lms_puts_len
            .Lms_puts_w:
                mv   a1, t0
                mv   a2, t1
                li   a0, 1
                call kof_plat_write
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            .section .rodata
            .Lms_lbl_alloc: .asciz "allocs: "
            .Lms_lbl_free: .asciz "frees: "
            .Lms_lbl_live: .asciz "live bytes: "
            .Lms_lbl_gc: .asciz "gc "
            .Lms_sp: .asciz " "
            .Lms_nl: .asciz "\\n"

            .section .text
            """;
}
