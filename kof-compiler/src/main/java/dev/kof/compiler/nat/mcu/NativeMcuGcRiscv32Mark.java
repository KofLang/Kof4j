package dev.kof.compiler.nat.mcu;

/**
 * B4-GC-2 fatia do coletor MCU RV32I: mark conservador (try_mark /
 * mark_transitive / mark de pilha+estaticas) — extraido de
 * {@link NativeMcuGcRiscv32} pelo gate check_500 (§506). Instrucoes, rotulos
 * e ordem IDENTICOS; a unica diferenca no .s e whitespace: o bloco original
 * tinha um comentario stray na coluna 0 que forcava o incidental-indent do
 * text block a 0, imprimindo 16 espacos acidentais em cada linha — agora tudo
 * sai na coluna 0 (invisivel a as/ld/qemu; prova = bateria MCU + E2E que
 * linkam e rodam a imagem).
 */
public final class NativeMcuGcRiscv32Mark {

    private NativeMcuGcRiscv32Mark() {
    }

    public static String runtimeAsm() {
        return """
                # --- B4-GC-2: mark conservador (port do G-3 p/ RV32I) ---
                # kof_gc_try_mark(ptr@a0): se ptr cai no PAYLOAD de um bloco da
                # gc-list e está no heap, seta bit0 (mark). Sem clobber de s*.
                .globl kof_gc_try_mark
                kof_gc_try_mark:
                    li   t0, 4096
                    bltu a0, t0, .Ltm_done
                    andi t0, a0, 3
                    bnez t0, .Ltm_done
                    la   t1, _kof_heap_start
                    bltu a0, t1, .Ltm_done
                    la   t1, _kof_heap_end
                    bgeu a0, t1, .Ltm_done
                    la   t1, .Lkof_gc_head
                    lw   t1, 0(t1)
                    li   t2, 100000
                .Ltm_loop:
                    beqz t1, .Ltm_done
                    addi t2, t2, -1
                    beqz t2, .Ltm_done
                    addi t3, t1, 16          # payload start
                    lw   t4, 0(t1)           # size total
                    addi t4, t4, -16         # payload size
                    bltu a0, t3, .Ltm_next
                    add  t3, t3, t4          # payload end (exclusivo)
                    bgeu a0, t3, .Ltm_next
                    j    .Ltm_found
                .Ltm_next:
                    lw   t1, 8(t1)           # gc_next
                    j    .Ltm_loop
                .Ltm_found:
                    lbu  t0, 12(t1)
                    andi t0, t0, 1
                    bnez t0, .Ltm_done
                    li   t0, 1
                    sb   t0, 12(t1)
                .Ltm_done:
                    ret

                # kof_gc_mark_transitive(ptr@a0): como try_mark, mas ao marcar um
                # bloco NOVO varre os campos (4 em 4 pelo payload) e recorre.
                .globl kof_gc_mark_transitive
                kof_gc_mark_transitive:
                    addi sp, sp, -32
                    sw   ra, 28(sp)
                    sw   s0, 24(sp)
                    sw   s1, 20(sp)
                    sw   s2, 16(sp)
                    li   t0, 4096
                    bltu a0, t0, .Lmt_done
                    andi t0, a0, 3
                    bnez t0, .Lmt_done
                    la   t0, _kof_heap_start
                    bltu a0, t0, .Lmt_done
                    la   t0, _kof_heap_end
                    bgeu a0, t0, .Lmt_done
                    la   t1, .Lkof_gc_head
                    lw   t1, 0(t1)
                    li   t2, 100000
                .Lmt_loop:
                    beqz t1, .Lmt_done
                    addi t2, t2, -1
                    beqz t2, .Lmt_done
                    addi t3, t1, 16
                    lw   t4, 0(t1)
                    addi t4, t4, -16
                    bltu a0, t3, .Lmt_next
                    add  t3, t3, t4
                    bgeu a0, t3, .Lmt_next
                    j    .Lmt_found
                .Lmt_next:
                    lw   t1, 8(t1)
                    j    .Lmt_loop
                .Lmt_found:
                    lbu  t0, 12(t1)
                    andi t0, t0, 1
                    bnez t0, .Lmt_done       # já marcado — pára o fecho
                    li   t0, 1
                    sb   t0, 12(t1)
                    mv   s0, t1
                    lw   t0, 0(s0)
                    addi t0, t0, -16         # payload size
                    addi s1, s0, 16          # cur
                    add  s2, s1, t0          # end (callee-saved: call clobbera a*)
                    beqz t0, .Lmt_done
                .Lmt_fields:
                    bgeu s1, s2, .Lmt_done
                    lw   a0, 0(s1)
                    beqz a0, .Lmt_fnext
                    call kof_gc_mark_transitive
                .Lmt_fnext:
                    addi s1, s1, 4
                    j    .Lmt_fields
                .Lmt_done:
                    lw   s2, 16(sp)
                    lw   s1, 20(sp)
                    lw   s0, 24(sp)
                    lw   ra, 28(sp)
                    addi sp, sp, 32
                    ret

                # kof_gc_mark(): marca raízes de PILHA [sp,_stack_top) e ESTÁTICAS
                # (.Lkof_heap_root_start..end), com os s0-s11 derramados no frame
                # para que temporários em registrador apareçam na varredura.
                .globl kof_gc_mark
                kof_gc_mark:
                    addi sp, sp, -64
                    sw   ra, 60(sp)
                    sw   s0, 56(sp)
                    sw   s1, 52(sp)
                    sw   s2, 48(sp)
                    sw   s3, 44(sp)
                    sw   s4, 40(sp)
                    sw   s5, 36(sp)
                    sw   s6, 32(sp)
                    sw   s7, 28(sp)
                    sw   s8, 24(sp)
                    sw   s9, 20(sp)
                    sw   s10, 16(sp)
                    sw   s11, 12(sp)
                    mv   s2, sp              # limite baixo
                    la   s1, _stack_top      # limite alto (fim da região de pilha)
                .Lgm_stack:
                    bgeu s2, s1, .Lgm_static
                    lw   a0, 0(s2)
                    call kof_gc_mark_transitive
                    addi s2, s2, 4
                    j    .Lgm_stack
                .Lgm_static:
                    la   s2, .Lkof_heap_root_start
                    la   s1, .Lkof_heap_root_end
                .Lgm_roots:
                    bgeu s2, s1, .Lgm_done
                    lw   a0, 0(s2)
                    call kof_gc_mark_transitive
                    addi s2, s2, 4
                    j    .Lgm_roots
                .Lgm_done:
                    lw   s11, 12(sp)
                    lw   s10, 16(sp)
                    lw   s9, 20(sp)
                    lw   s8, 24(sp)
                    lw   s7, 28(sp)
                    lw   s6, 32(sp)
                    lw   s5, 36(sp)
                    lw   s4, 40(sp)
                    lw   s3, 44(sp)
                    lw   s2, 48(sp)
                    lw   s1, 52(sp)
                    lw   s0, 56(sp)
                    lw   ra, 60(sp)
                    addi sp, sp, 64
                    ret

                """;
    }
}
