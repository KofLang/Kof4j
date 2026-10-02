package dev.kof.compiler.nat;

// G-3 (NATIVE002 face 1, 15/09): mark CONSERVATIVO riscv64 — port do
// RuntimeGc.emitGc (kof_gc_try_mark / kof_gc_mark_transitive / kof_gc_mark)
// sobre o header de bloco de 32B do G-0 e a gc-list do G-2. O mark NÃO
// recupera nada (isso é o G-4, sweep); a alavanca de observação é o bit0
// (mark) nos flags @24, legível via kof_gc_dump (G-2) ANTES/DEPOIS do mark.
//
// Registrador ABI riscv: `s11` = frame pointer (mesmo do prologue Kof
// emitCrossMethodRiscv:78 — s11 = sp+16 na entrada). O mark usa s11 como
// limite alto da varredura de pilha, sp como limite baixo; sem fp válido
// (radical `_start`), cai no fallback sp..sp+4096, igual ao x86.
//
// Raízes ESTÁTICAS: riscv não tinha NENHUM marcador do intervalo de .data.
// O NativeArchEmitter (emitRiscv/emitAarch64) passa a emitir DOIS rótulos
// LOCAIS riscv-only (`.L`, não entram no .symtab — a lição do G-1 no
// ArtifactSizeTest) em volta do .data DO PROGRAMA:
//   `.Lkof_heap_root_start` (abertura, com sentinel .quad 0) …
//   `.Lkof_heap_root_end` (antes do .text dos métodos).
// O intervalo exclui a arena do bump (`_kof_heap`, em .bss) — ao contrário
// do x86, que varre até `_end` porque lá o heap é mmap (fora do .bss). Aqui
// o heap É .bss: varrer até `_end` incluiria a arena. NÃO dependemos do
// `kof_heap_root_end` x86 (pré-requisito da S-5 que não existe no riscv).
//
// O try_mark limita ponteiros ao intervalo de heap [_kof_heap,_kof_heap_end]
// (a arena fixa .bss do bump do G-0) e encontra o bloco-dono na gc-list do
// G-2; seta bit0. mark_transitive, ao marcar um bloco novo, varre os campos
// pelo tamanho total (size/8) e recorre — o mesmo fecho do x86 (sobre--varre
// 32B além do payload, conservador e inofensivo, idêntico ao RuntimeGc).
//
// aarch64 herda linha-a-linha no tradutor (mesmo caminho do G-1/G-2).
public final class NativeRiscvAsmRtB43 {

    private NativeRiscvAsmRtB43() {}

    static  String RISCV_RUNTIME_ASM_B_43 = """
            .section .text
            # kof_gc_try_mark(ptr@a0): se ptr aponta para o PAYLOAD de um
            # bloco da gc-list e está dentro do heap, seta bit0 (mark).
            # Nenhum callee-saved clobberado. Restrito a [_kof_heap,_kof_heap_end).
            .globl kof_gc_try_mark
            kof_gc_try_mark:
                # §540: lookup O(1) pelo bitmap de inícios-de-bloco
                # (_kof_block_bm) em vez da varredura LINEAR da gc-list (era
                # O(N) por ponte e capada em 10000 -> blocos vivos além disso
                # não eram marcados e o sweep os liberava: use-after-free).
                # Um objeto Kof aponta SEMPRE p/ o início do payload; o início
                # do bloco é a0-32, e o bit confirma que a0 é payload de fato.
                li   t0, 4096
                bltu a0, t0, .Ltm_done
                andi t0, a0, 7
                bnez t0, .Ltm_done
                la   t1, _kof_heap
                addi t0, t1, 32
                bltu a0, t0, .Ltm_done
                la   t1, _kof_heap_end
                bgeu a0, t1, .Ltm_done
                addi t2, a0, -32         # header
                la   t0, _kof_heap
                sub  t3, t2, t0
                srli t4, t3, 10
                slli t4, t4, 3
                la   t5, _kof_block_bm
                add  t5, t5, t4
                ld   t6, 0(t5)
                srli t1, t3, 4
                andi t1, t1, 63
                srl  t6, t6, t1
                andi t6, t6, 1
                beqz t6, .Ltm_done
                lbu  t0, 24(t2)
                andi t1, t0, 1
                bnez t1, .Ltm_done
                ori  t0, t0, 1
                sb   t0, 24(t2)
            .Ltm_done:
                ret

            # kof_gc_mark_transitive(ptr@a0): like try_mark, mas se marcou um
            # bloco NOVO, varre seus campos (8 em 8 pelo size total) e recorre
            # — fecho conservador idêntico ao RuntimeGc:134.
            .globl kof_gc_mark_transitive
            kof_gc_mark_transitive:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                # §540: bitmap O(1) idêntico ao try_mark (a varredura linear da
                # gc-list capada em 10000 foi removida).
                li   t0, 4096
                bltu a0, t0, .Lmt_done
                andi t0, a0, 7
                bnez t0, .Lmt_done
                la   t1, _kof_heap
                addi t0, t1, 32
                bltu a0, t0, .Lmt_done
                la   t1, _kof_heap_end
                bgeu a0, t1, .Lmt_done
                addi s0, a0, -32         # header
                la   t0, _kof_heap
                sub  t1, s0, t0
                srli t2, t1, 10
                slli t2, t2, 3
                la   t3, _kof_block_bm
                add  t3, t3, t2
                ld   t4, 0(t3)
                srli t5, t1, 4
                andi t5, t5, 63
                srl  t4, t4, t5
                andi t4, t4, 1
                beqz t4, .Lmt_done       # a0 não é payload de um bloco real
                lbu  t0, 24(s0)
                andi t1, t0, 1
                bnez t1, .Lmt_done       # já marcado (ou sendo) — pára fecho
                ori  t0, t0, 1
                sb   t0, 24(s0)
                # varre os campos do bloco recém-marcado.
                ld   t0, 0(s0)           # size total
                addi t0, t0, -32         # payload size
                addi s1, s0, 32          # cur = payload start
                add  s2, s1, t0          # end (callee-saved: o call clobbera a*)
                beqz t0, .Lmt_done
            .Lmt_fields:
                bgeu s1, s2, .Lmt_done
                ld   a0, 0(s1)
                beqz a0, .Lmt_fnext
                call kof_gc_mark_transitive
            .Lmt_fnext:
                addi s1, s1, 8
                j    .Lmt_fields
            .Lmt_done:
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_gc_mark(): marca raízes DE PILHA (sp..s11, fallback 4KB) e
            # ESTÁTICAS (.Lkof_heap_root_start../.Lkof_heap_root_end, emitidos
            # pelo NativeArchEmitter no .data do programa) chamando
            # mark_transitive. Salva s0-s11 num buffer local para que
            # ponteiros guardados em registrador apareçam na varredura de
            # pilha (mesma intenção do pushq %rbx,%r12-15,%rbp do x86).
            .globl kof_gc_mark
            kof_gc_mark:
                addi sp, sp, -128
                sd   ra, 120(sp)
                sd   s0, 112(sp)
                sd   s1, 104(sp)
                sd   s2, 96(sp)
                sd   s3, 88(sp)
                sd   s4, 80(sp)
                sd   s5, 72(sp)
                sd   s6, 64(sp)
                sd   s7, 56(sp)
                sd   s8, 48(sp)
                sd   s9, 40(sp)
                sd   s10, 32(sp)
                sd   s11, 24(sp)
                mv   s2, sp              # loop ptr (limite baixo)
                mv   s1, s11             # frame pointer do caller (limite alto)
                beqz s1, .Lgm_fallback
                bgeu s2, s1, .Lgm_fallback
                sub  t0, s1, s2
                li   t1, 1048576
                bltu t1, t0, .Lgm_fallback
                j    .Lgm_stack
            .Lgm_fallback:
                li   t0, 4096
                add  s1, s2, t0
            .Lgm_stack:
                bgeu s2, s1, .Lgm_static
                ld   a0, 0(s2)
                call kof_gc_mark_transitive
                addi s2, s2, 8
                j    .Lgm_stack
            .Lgm_static:
                la   s2, .Lkof_heap_root_start
                la   s1, .Lkof_heap_root_end
            .Lgm_bss:
                bgeu s2, s1, .Lgm_done
                ld   a0, 0(s2)
                call kof_gc_mark_transitive
                addi s2, s2, 8
                j    .Lgm_bss
            .Lgm_done:
                ld   s11, 24(sp)
                ld   s10, 32(sp)
                ld   s9, 40(sp)
                ld   s8, 48(sp)
                ld   s7, 56(sp)
                ld   s6, 64(sp)
                ld   s5, 72(sp)
                ld   s4, 80(sp)
                ld   s3, 88(sp)
                ld   s2, 96(sp)
                ld   s1, 104(sp)
                ld   s0, 112(sp)
                ld   ra, 120(sp)
                addi sp, sp, 128
                ret
            """;
}
