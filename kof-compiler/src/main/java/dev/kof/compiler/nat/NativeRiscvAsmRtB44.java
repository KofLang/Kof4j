package dev.kof.compiler.nat;

// G-4 (NATIVE002 face 1, 15/09): SWEEP + COLLECT riscv64 — o degrau que de fato
// RECUPERA. Port do RuntimeGc.emitGc x86 (kof_gc_sweep/kof_gc_collect_now/
// kof_gc_collect/kof_gc_tick) sobre o header de 32B do G-0, a gc-list do G-2 e
// o mark do G-3. O sweep percorre a gc-list: mark==1 -> limpa bit0 (sobreviveu);
// mark==0 e !free -> insere na free-list (bit1) e conta (frees/free_bytes).
// `kof_gc_collect` é tick-guarded (tick & 4095) e é chamado na ENTRADA de
// `kof_alloc` (ver B42) — assim um programa longo reclama periodicamente.
//
// ⚠️ Diferença x86 (medida, e a razão do G-4 ser seguro aqui): o x86 DESLIGA o
// collect dentro de kof_alloc (RuntimeMemory:122-131) porque o ponteiro do
// bloco livre vive num REGISTRADOR no momento do alloc e a mark conservadora
// não o vê — o sweep o enfileira e o alloc o reusa DUPLO (corrupção). No riscv
// a value-stack É a pilha de máquina (`pushRiscv`: `addi sp,-8; sd`) e o
// kof_gc_mark (G-3) ainda derrama s0-s11 do caller: TODO temporário vivo está
// na pilha varrida. Logo o collect-a-partir-do-alloc é seguro AQUI.
//
// OOM (B42): quando o bump estoura a arena (.bss 256KB), em vez de panicar
// direto o kof_alloc faz UM `kof_gc_collect_now` (mark+sweep incondicional) e
// re-tenta a free-list; só panic (`out of memory`) se ainda faltar. Isso é o
// que fecha o vazamento do `.bss`: sem coletor, um laço de allocs mortos
// estourava a arena (prova: NativeRiscvGcSweepTest, via kof_memstats do G-1).
//
// aarch64 herda linha-a-linha no tradutor (mesmo caminho do G-1/G-2/G-3).
public final class NativeRiscvAsmRtB44 {

    private NativeRiscvAsmRtB44() {}

    static  String RISCV_RUNTIME_ASM_B_44 = """
            .section .data
            .align 3
            .Lkof_gc_tick: .quad 0
            # §544/§552: stack-bottom + tid da thread main, gravados no _start
            # (NativeArchEmitter); o mark so faz full-scan na thread main (gate
            # kof_plat_thread_id == kof_main_tid).
            .globl kof_main_stack_bottom
            kof_main_stack_bottom: .quad 0
            .globl kof_main_tid
            kof_main_tid: .quad 0
            .section .text

            # kof_gc_sweep(): percorre a gc-list e recupera mortos (G-4).
            # mark bit0==1 -> sobreviveu: limpa o bit.
            # mark bit0==0 e bit1==0 -> morto: insere na free-list (bit1) e
            #   conta em .Lkof_free_count/.Lkof_free_bytes (G-1).
            # Toma o MESMO lock da free-list do G-1 (o sweep escreve a lista;
            # o alloc a lê). O kof_alloc SOLTA o lock antes de chamar o collect.
            .globl kof_gc_sweep
            kof_gc_sweep:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                la   s0, .Lkof_alloc_lock
            .Lg4sw_spin:
                li   t0, 1
                amoswap.w t1, t0, (s0)
                bnez t1, .Lg4sw_spin
                la   t0, .Lkof_gc_head
                ld   s1, 0(t0)
            .Lg4sw_loop:
                beqz s1, .Lg4sw_done
                lbu  t0, 24(s1)
                andi t1, t0, 1
                beqz t1, .Lg4sw_free
                andi t0, t0, -2          # limpa bit0 (mark)
                sb   t0, 24(s1)
                j    .Lg4sw_next
            .Lg4sw_free:
                andi t1, t0, 2
                bnez t1, .Lg4sw_next       # já está na free-list
                ld   t2, 0(s1)           # size total
                la   t3, .Lkof_free_head
                ld   t4, 0(t3)
                sd   t4, 8(s1)           # next_free = head antiga
                sd   s1, 0(t3)           # head = bloco morto
                ori  t0, t0, 2           # bit1: na free-list
                sb   t0, 24(s1)
                la   t3, .Lkof_free_count
                ld   t4, 0(t3)
                addi t4, t4, 1
                sd   t4, 0(t3)
                la   t3, .Lkof_free_bytes
                ld   t4, 0(t3)
                add  t4, t4, t2
                sd   t4, 0(t3)
            .Lg4sw_next:
                ld   s1, 16(s1)          # gc_next
                j    .Lg4sw_loop
            .Lg4sw_done:
                sw   zero, 0(s0)         # unlock
                ld   s1, 8(sp)
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            # kof_gc_collect_now(): mark + sweep incondicional (sem tick).
            # Derrama s0-s11 para que ponteiros vivos em registrador do caller
            # (ex.: o handle em s-regs de kof_spawn_handle_new) apareçam na
            # varredura de pilha do mark — mesma intenção do pushq do x86.
            .globl kof_gc_collect_now
            kof_gc_collect_now:
                addi sp, sp, -112
                sd   ra, 104(sp)
                sd   s0, 96(sp)
                sd   s1, 88(sp)
                sd   s2, 80(sp)
                sd   s3, 72(sp)
                sd   s4, 64(sp)
                sd   s5, 56(sp)
                sd   s6, 48(sp)
                sd   s7, 40(sp)
                sd   s8, 32(sp)
                sd   s9, 24(sp)
                sd   s10, 16(sp)
                sd   s11, 8(sp)
                call kof_gc_mark
                call kof_gc_sweep
                ld   s11, 8(sp)
                ld   s10, 16(sp)
                ld   s9, 24(sp)
                ld   s8, 32(sp)
                ld   s7, 40(sp)
                ld   s6, 48(sp)
                ld   s5, 56(sp)
                ld   s4, 64(sp)
                ld   s3, 72(sp)
                ld   s2, 80(sp)
                ld   s1, 88(sp)
                ld   s0, 96(sp)
                ld   ra, 104(sp)
                addi sp, sp, 112
                ret

            # kof_gc_collect(): versão tick-guarded (tick & 4095 == 0), mesma
            # ordem do x86 (checa ANTES de incrementar; tick=0 coleta). É o
            # gancho chamado na entrada do kof_alloc; a maioria das chamadas só
            # incrementa o tick e volta (barato).
            .globl kof_gc_collect
            kof_gc_collect:
                la   t0, .Lkof_gc_tick
                ld   t1, 0(t0)
                li   t2, 4095
                and  t2, t1, t2
                bnez t2, .Lg4cc_skip
                addi sp, sp, -16
                sd   ra, 8(sp)
                call kof_gc_collect_now
                ld   ra, 8(sp)
                addi sp, sp, 16
            .Lg4cc_skip:
                la   t0, .Lkof_gc_tick
                ld   t1, 0(t0)
                addi t1, t1, 1
                sd   t1, 0(t0)
            .Lg4cc_ret:
                ret

            # kof_gc_tick() -> a0 = tick atual (observabilidade, paridade x86).
            .globl kof_gc_tick
            kof_gc_tick:
                la   t0, .Lkof_gc_tick
                ld   a0, 0(t0)
                ret
            """;
}
