package dev.kof.compiler.nat.mcu;

/**
 * B4-GC-1 (PLAN-BAREMETAL-BOOT B-4 + {@code D-BAREMETAL-MCU-GC}): alocador do
 * MCU RV32I — o primeiro degrau do coletor G-4/G-5 portado para 32-bit.
 *
 * <p>Port do {@code NativeRiscvAsmRtB42} (riscv64) para RV32I puro (sem
 * {@code div}/{@code rem} — a M-extension não existe no alvo MCU; a impressão
 * decimal usa subtração repetida). Diferenças de arquitetura, explícitas:
 * ponteiros de 32 bits e header de <b>16 B</b> (4 words: size, free_next,
 * gc_next, flags) em vez dos 32 B/quad do riscv64; a arena vem do
 * <b>linker script</b> ({@code _kof_heap_start}..{@code _kof_heap_end},
 * escala de KB), não da arena fixa de 256 KB em {@code .bss}.
 *
 * <p>Fatias entregues: <b>B4-GC-1</b> (alloc + free + gc-list + dump +
 * memstats), <b>B4-GC-2</b> (mark conservador: {@code kof_gc_try_mark} /
 * {@code kof_gc_mark_transitive} / {@code kof_gc_mark}, raízes estáticas +
 * pilha {@code [sp,_stack_top)}) e <b>B4-GC-3</b> (sweep + reuso: o
 * {@code kof_gc_collect_now} vive em {@link NativeMcuGcRiscv32Sweep}; o
 * {@code kof_alloc} em OOM roda um collect e re-tenta UMA vez antes de panicar
 * — o que recicla o heap ao longo do tempo). Prova: {@code NativeMcuGcTest},
 * harness asm cru sob {@code qemu-system-riscv32 -M virt}, no mesmo padrão do
 * {@code NativeRiscvGcSweepTest} do cross.
 */
public final class NativeMcuGcRiscv32 {

    private NativeMcuGcRiscv32() {}

    /** Blocos de header da ABI MCU: 4 words de 32 bits. */
    public static final int HEADER_BYTES = 16;

    public static String runtimeAsm() {
        return """
                .section .data
                .align 2
                .globl kof_alloc_ptr
                kof_alloc_ptr: .word _kof_heap_start

                .section .bss
                .align 2
                .Lkof_free_head: .word 0
                .Lkof_gc_head: .word 0
                .Lkof_alloc_count: .word 0
                .Lkof_free_count: .word 0
                .Lkof_alloc_bytes: .word 0
                .Lkof_free_bytes: .word 0

                .section .text
                # kof_alloc(size@a0) -> user ptr. Header de 16B (size/free_next/
                # gc_next/flags); free-list first-fit (LIFO), senão bump com guard
                # OOM em _kof_heap_end. Bloco novo entra na gc-list (LIFO, flags=0).
                .globl kof_alloc
                kof_alloc:
                    addi sp, sp, -32
                    sw   ra, 28(sp)
                    sw   s0, 24(sp)
                    sw   s1, 20(sp)
                    sw   s2, 16(sp)
                    sw   s3, 12(sp)
                    addi s0, a0, 15
                    andi s0, s0, -16
                    addi s0, s0, 16          # total = align16(size)+header
                    li   s3, 0               # já coletou nesta chamada?
                .Lalloc_retry:
                    la   s1, .Lkof_free_head
                    lw   s2, 0(s1)           # cur
                    mv   t5, zero            # prev
                .Lalloc_fsearch:
                    beqz s2, .Lalloc_bump
                    lw   t2, 0(s2)           # block size
                    bltu t2, s0, .Lalloc_fnext
                    lw   t3, 4(s2)           # next
                    beqz t5, .Lalloc_fhead
                    sw   t3, 4(t5)
                    j    .Lalloc_found
                .Lalloc_fhead:
                    sw   t3, 0(s1)
                .Lalloc_found:
                    sw   zero, 12(s2)        # fora da free list (flags=0)
                    la   t4, .Lkof_alloc_count
                    lw   t2, 0(t4)
                    addi t2, t2, 1
                    sw   t2, 0(t4)
                    la   t4, .Lkof_alloc_bytes
                    lw   t2, 0(t4)
                    add  t2, t2, s0
                    sw   t2, 0(t4)
                    addi a0, s2, 16
                    j    .Lalloc_done
                .Lalloc_fnext:
                    mv   t5, s2
                    lw   s2, 4(s2)
                    j    .Lalloc_fsearch
                .Lalloc_bump:
                    la   t2, kof_alloc_ptr
                    lw   t0, 0(t2)           # base do bloco
                    add  t1, t0, s0          # novo bump
                    la   t3, _kof_heap_end
                    bgtu t1, t3, .Lalloc_oom
                    sw   t1, 0(t2)           # commit
                    sw   s0, 0(t0)           # size total
                    sw   zero, 4(t0)         # free_next = 0
                    la   t2, .Lkof_gc_head
                    lw   t3, 0(t2)
                    sw   t3, 8(t0)           # gc_next = head
                    sw   t0, 0(t2)           # head = bloco
                    sw   zero, 12(t0)        # flags = 0
                    la   t4, .Lkof_alloc_count
                    lw   t2, 0(t4)
                    addi t2, t2, 1
                    sw   t2, 0(t4)
                    la   t4, .Lkof_alloc_bytes
                    lw   t2, 0(t4)
                    add  t2, t2, s0
                    sw   t2, 0(t4)
                    addi a0, t0, 16
                    j    .Lalloc_done
                .Lalloc_oom:
                    # B4-GC-3: UM collect (mark+sweep) e UMA nova tentativa;
                    # ainda sem espaço -> panic nomeado (R6). Seguro porque o
                    # kof_gc_mark derrama os s0-s11 e varre a pilha: todo
                    # temporário vivo (inclusive o bloco em exame em s2) é visto.
                    bnez s3, .Lalloc_panic
                    li   s3, 1
                    call kof_gc_collect_now
                    j    .Lalloc_retry
                .Lalloc_panic:
                    la   a0, .Lgc_oom
                    call kof_panic
                .Lalloc_done:
                    lw   s3, 12(sp)
                    lw   s2, 16(sp)
                    lw   s1, 20(sp)
                    lw   s0, 24(sp)
                    lw   ra, 28(sp)
                    addi sp, sp, 32
                    ret

                # kof_free(ptr@a0) — devolve à free-list (LIFO), flags=2 (bit1).
                .globl kof_free
                kof_free:
                    beqz a0, .Lfree_done
                    addi t0, a0, -16
                    lw   t1, 0(t0)           # size
                    li   t2, 2
                    sw   t2, 12(t0)          # flags = 2
                    la   t3, .Lkof_free_head
                    lw   t4, 0(t3)
                    sw   t4, 4(t0)
                    sw   t0, 0(t3)
                    la   t4, .Lkof_free_count
                    lw   t5, 0(t4)
                    addi t5, t5, 1
                    sw   t5, 0(t4)
                    la   t4, .Lkof_free_bytes
                    lw   t5, 0(t4)
                    add  t5, t5, t1
                    sw   t5, 0(t4)
                .Lfree_done:
                    ret

                # kof_gc_dump() — uma linha por bloco: `gc <size_total> <flags>`.
                .globl kof_gc_dump
                kof_gc_dump:
                    addi sp, sp, -16
                    sw   ra, 12(sp)
                    sw   s0, 8(sp)
                    sw   s1, 4(sp)
                    la   s0, .Lkof_gc_head
                    lw   s1, 0(s0)
                .Lgcd_loop:
                    beqz s1, .Lgcd_done
                    la   a0, .Lgc_lbl
                    call .Lput
                    lw   a0, 0(s1)
                    call .Lput_uint
                    la   a0, .Lgc_sp
                    call .Lput
                    lw   a0, 12(s1)
                    call .Lput_uint
                    la   a0, .Lgc_nl
                    call .Lput
                    lw   s1, 8(s1)
                    j    .Lgcd_loop
                .Lgcd_done:
                    lw   s1, 4(sp)
                    lw   s0, 8(sp)
                    lw   ra, 12(sp)
                    addi sp, sp, 16
                    ret

                # kof_memstats() — allocs/frees/live bytes em decimal.
                .globl kof_memstats
                kof_memstats:
                    addi sp, sp, -16
                    sw   ra, 12(sp)
                    sw   s0, 8(sp)
                    la   a0, .Lms_alloc
                    call .Lput
                    la   t0, .Lkof_alloc_count
                    lw   a0, 0(t0)
                    call .Lput_uint
                    la   a0, .Lgc_nl
                    call .Lput
                    la   a0, .Lms_free
                    call .Lput
                    la   t0, .Lkof_free_count
                    lw   a0, 0(t0)
                    call .Lput_uint
                    la   a0, .Lgc_nl
                    call .Lput
                    la   a0, .Lms_live
                    call .Lput
                    la   t0, .Lkof_alloc_bytes
                    lw   s0, 0(t0)
                    la   t0, .Lkof_free_bytes
                    lw   t0, 0(t0)
                    sub  a0, s0, t0
                    call .Lput_uint
                    la   a0, .Lgc_nl
                    call .Lput
                    lw   s0, 8(sp)
                    lw   ra, 12(sp)
                    addi sp, sp, 16
                    ret

                # kof_panic(msg@a0): imprime e sai(1); se exit retornar, gira.
                .globl kof_panic
                kof_panic:
                    addi sp, sp, -16
                    sw   ra, 12(sp)
                    call .Lput
                    la   a0, .Lgc_nl
                    call .Lput
                    li   a0, 1
                    call kof_plat_exit
                .Lpanic_halt:
                    j    .Lpanic_halt

                # .Lput(a0=asciz): kof_plat_write(buf,len). Não aloca.
                .Lput:
                    addi sp, sp, -16
                    sw   ra, 12(sp)
                    mv   t0, a0
                    li   t1, 0
                .Lput_len:
                    add  t2, t0, t1
                    lbu  t3, 0(t2)
                    beqz t3, .Lput_w
                    addi t1, t1, 1
                    j    .Lput_len
                .Lput_w:
                    mv   a0, t0
                    mv   a1, t1
                    call kof_plat_write
                    lw   ra, 12(sp)
                    addi sp, sp, 16
                    ret

                # .Lput_uint(a0): decimal via subtração repetida (RV32I puro).
                .Lput_uint:
                    addi sp, sp, -48
                    sw   ra, 44(sp)
                    sw   s0, 40(sp)
                    sw   s1, 36(sp)
                    addi s1, sp, 32
                    mv   s0, a0
                    beqz s0, .Lpu_zero
                .Lpu_loop:
                    mv   a0, s0
                    call .Ldiv10
                    mv   s0, a0
                    addi a1, a1, 48
                    addi s1, s1, -1
                    sb   a1, 0(s1)
                    bnez s0, .Lpu_loop
                    j    .Lpu_write
                .Lpu_zero:
                    addi s1, s1, -1
                    li   a1, 48
                    sb   a1, 0(s1)
                .Lpu_write:
                    addi a1, sp, 32
                    sub  a1, a1, s1
                    mv   a0, s1
                    call kof_plat_write
                    lw   s1, 36(sp)
                    lw   s0, 40(sp)
                    lw   ra, 44(sp)
                    addi sp, sp, 48
                    ret

                # .Ldiv10(a0) -> a0=quot, a1=resto (sem div/rem da M-extension).
                .Ldiv10:
                    li   a1, 0
                    li   t0, 10
                .Ld10_loop:
                    bltu a0, t0, .Ld10_done
                    sub  a0, a0, t0
                    addi a1, a1, 1
                    j    .Ld10_loop
                .Ld10_done:
                    mv   t1, a0
                    mv   a0, a1
                    mv   a1, t1
                    ret

# kof_string_of_int(a0:int) -> a0:ptr to String(len+bytes).
                # Stub: not yet implemented for RV32I. Returns empty string.
                .globl kof_string_of_int
                kof_string_of_int:
                    li   a0, 16
                    call kof_alloc
                    mv   s1, a0
                    li   s0, 0
                    sw   s0, 0(s1)               # len = 0
                    mv   a0, s1
                    ret

                # kof_println_string(a0:String ptr) -> escreve via kof_plat_write + newline
                .globl kof_println_string
                kof_println_string:
                    addi sp, sp, -32
                    sw   ra, 28(sp)
                    sw   s0, 24(sp)
                    sw   s1, 20(sp)
                    sw   s2, 16(sp)
                    mv   s0, a0
                    lw   s1, 0(s0)               # len
                    addi s2, s0, 4               # ptr bytes
                    mv   a0, s2
                    mv   a1, s1
                    call kof_plat_write
                    # escreve newline
                    li   a0, 10
                    sb   a0, -1(sp)              # usa 1 byte da pilha
                    addi a0, sp, -1
                    li   a1, 1
                    call kof_plat_write
                    lw   s2, 16(sp)
                    lw   s1, 20(sp)
                    lw   s0, 24(sp)
                    lw   ra, 28(sp)
                    addi sp, sp, 32
                    ret

                # --- LIST RUNTIME ---
                # Layout do List: [len:word][cap:word][data...]
                # kof_list_new() -> a0:ptr List
                .globl kof_list_new
                kof_list_new:
                    addi sp, sp, -32
                    sw   ra, 28(sp)
                    sw   s0, 24(sp)
                    sw   s1, 20(sp)
                    # cap fixa 8 para fatia vertical (evita realloc)
                    li   a0, 40                  # 2 words header + 8*4 data = 40 bytes
                    call kof_alloc
                    li   s0, 0
                    sw   s0, 0(a0)               # len = 0
                    li   s0, 8
                    sw   s0, 4(a0)               # cap = 8
                    lw   s1, 20(sp)
                    lw   s0, 24(sp)
                    lw   ra, 28(sp)
                    addi sp, sp, 32
                    ret

                # kof_list_add(a0:List, a1:int) -> void (expande se cheio = panic)
                .globl kof_list_add
                kof_list_add:
                    addi sp, sp, -48
                    sw   ra, 44(sp)
                    sw   s0, 40(sp)
                    sw   s1, 36(sp)
                    sw   s2, 32(sp)
                    sw   s3, 28(sp)
                    mv   s0, a0                  # List ptr
                    mv   s1, a1                  # value
                    lw   s2, 0(s0)               # len
                    lw   s3, 4(s0)               # cap
                    beq  s2, s3, .Lla_panic
                    # data[ len ] = value
                    addi s3, s0, 8
                    slli s3, s2, 2
                    add  s3, s3, s0
                    addi s3, s3, 8
                    sw   s1, 0(s3)
                    addi s2, s2, 1
                    sw   s2, 0(s0)               # len++
                    j    .Lla_done
                .Lla_panic:
                    la   a0, .Lla_msg
                    li   a1, 15
                    call kof_plat_write
                    li   a0, 1
                    call kof_plat_exit
                .Lla_done:
                    lw   s3, 28(sp)
                    lw   s2, 32(sp)
                    lw   s1, 36(sp)
                    lw   s0, 40(sp)
                    lw   ra, 44(sp)
                    addi sp, sp, 48
                    ret
                .Lla_msg: .asciz "list overflow"

                # kof_list_size(a0:List) -> a0:int
                .globl kof_list_size
                kof_list_size:
                    lw   a0, 0(a0)
                    ret

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

                .section .rodata
                .Lgc_lbl: .asciz "gc "
                .Lgc_sp: .asciz " "
                .Lgc_nl: .asciz "\\n"
                .Lms_alloc: .asciz "allocs: "
                .Lms_free: .asciz "frees: "
                .Lms_live: .asciz "live bytes: "
                .Lgc_oom: .asciz "out of memory"

                .section .text
                """;
    }

    /** Runtime COMPLETO do coletor MCU (core + sweep) — entry point p/ link. */
    public static String all() {
        return runtimeAsm() + NativeMcuGcRiscv32Sweep.runtimeAsm();
    }

    /** Linker script do harness: heap dimensionado entre os símbolos do B-4. */
    public static String linkerScript(long heapBytes) {
        return """
                ENTRY(_start)
                SECTIONS {
                  . = 0x80000000;
                  .text : { *(.text*) }
                  .rodata : { *(.rodata*) }
                  .data : { *(.data*) }
                  .bss : { *(.bss*) *(COMMON) }
                  . = ALIGN(16);
                  _kof_heap_start = .;
                  . = . + %d;
                  _kof_heap_end = .;
                  . = ALIGN(16);
                  _stack_top = . + 0x4000;
                }
                """.formatted(heapBytes);
    }
}
