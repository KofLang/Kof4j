package dev.kof.compiler.nat.mcu;

/**
 * B-4 follow-up (b): runtime de STRING/LIST do MCU riscv32 — int-decimal
 * ({@code kof_string_of_int}, divisao binaria u32/10 sem M-extension),
 * {@code kof_println_string} e as listas growable de header estavel
 * ({@code kof_list_new}/{@code kof_list_add}/{@code kof_list_size}).
 *
 * <p>Irmio de responsabilidade do {@link NativeMcuGcRiscv32} (heap/collect):
 * o bloco e texto puro em {@code .section .text} (aberto pelo runtimeAsm do
 * GC) e assume as âncoras {@code kof_alloc}/{@code kof_plat_write} ja
 * definidas la. Prova: {@code NativeMcuListTest} (4/4) e
 * {@code NativeMcuE2ETest#mcuSpikeListOfPrintlnSizeAndIntOverUart}.
 */
public final class NativeMcuListStringRiscv32 {

    private NativeMcuListStringRiscv32() {}

    public static String runtimeAsm() {
        return """
                .section .text
                # kof_string_of_int(a0:int) -> a0:ptr to String(len+bytes).
                # Conversão decimal RV32I pura (divisão binária u32/10 — sem
                # M-extension; INT_MIN tratado como magnitude unsigned).
                .globl kof_string_of_int
                kof_string_of_int:
                    addi sp, sp, -48
                    sw   ra, 44(sp)
                    sw   s0, 40(sp)
                    sw   s1, 36(sp)
                    sw   s2, 32(sp)
                    sw   s3, 28(sp)
                    mv   s0, a0                  # valor
                    addi s1, sp, 12              # cursor: buffer sp+0..sp+12, escreve de trás
                    li   s3, 0                   # flag de sinal
                    bge  s0, zero, .Lsoi_digits
                    li   s3, 1
                    neg  s0, s0                  # INT_MIN -> 0x80000000 (magnitude u32 correta)
                .Lsoi_digits:
                    beqz s0, .Lsoi_zero_digit
                .Lsoi_loop:
                    mv   a0, s0
                    call .Ldivu10                # a0=quociente, a1=resto
                    mv   s0, a0
                    addi a1, a1, 48
                    addi s1, s1, -1
                    sb   a1, 0(s1)
                    bnez s0, .Lsoi_loop
                    j    .Lsoi_sign
                .Lsoi_zero_digit:
                    addi s1, s1, -1
                    li   a1, 48
                    sb   a1, 0(s1)
                .Lsoi_sign:
                    beqz s3, .Lsoi_build
                    addi s1, s1, -1
                    li   a1, 45                  # '-'
                    sb   a1, 0(s1)
                .Lsoi_build:
                    addi a1, sp, 12
                    sub  s2, a1, s1              # nº de dígitos
                    mv   a0, s2
                    addi a0, a0, 4               # len word + bytes
                    call kof_alloc
                    mv   s3, a0                  # ptr String (flag não precisa mais)
                    sw   s2, 0(s3)               # len
                    addi a1, s3, 4
                .Lsoi_copy:
                    beqz s2, .Lsoi_done
                    lbu  a0, 0(s1)
                    sb   a0, 0(a1)
                    addi s1, s1, 1
                    addi a1, a1, 1
                    addi s2, s2, -1
                    j    .Lsoi_copy
                .Lsoi_done:
                    mv   a0, s3
                    lw   s3, 28(sp)
                    lw   s2, 32(sp)
                    lw   s1, 36(sp)
                    lw   s0, 40(sp)
                    lw   ra, 44(sp)
                    addi sp, sp, 48
                    ret

                # .Ldivu10(a0:u32) -> a0=quociente, a1=resto. Long-division binária
                # (32 iterações), só scratch t*. RV32I puro (sem div/rem/M).
                .Ldivu10:
                    li   a1, 0
                    li   t3, 10
                    li   t4, 32
                    mv   t0, a0
                    li   t5, 0
                .Ldu_loop:
                    srli t6, t0, 31
                    slli t0, t0, 1
                    slli a1, a1, 1
                    or   a1, a1, t6
                    slli t5, t5, 1
                    bltu a1, t3, .Ldu_next
                    sub  a1, a1, t3
                    ori  t5, t5, 1
                .Ldu_next:
                    addi t4, t4, -1
                    bnez t4, .Ldu_loop
                    mv   a0, t5
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
                # Layout do List: header estavel [len:word][cap:word][data:ptr].
                # O bloco de dados e separado e DOBRA quando cheio: o ponteiro do
                # header nunca muda, entao quem segura a lista continua valido
                # depois de um add com realloc (o bloco antigo vira lixo p/ GC).
                # kof_list_new() -> a0:ptr header
                .globl kof_list_new
                kof_list_new:
                    addi sp, sp, -32
                    sw   ra, 28(sp)
                    sw   s0, 24(sp)
                    li   a0, 12                  # header: len/cap/data
                    call kof_alloc
                    mv   s0, a0
                    li   a0, 32                  # cap inicial 8 palavras
                    call kof_alloc
                    sw   a0, 8(s0)               # data
                    sw   zero, 0(s0)             # len = 0
                    li   a0, 8
                    sw   a0, 4(s0)               # cap = 8
                    mv   a0, s0
                    lw   s0, 24(sp)
                    lw   ra, 28(sp)
                    addi sp, sp, 32
                    ret

                # kof_list_add(a0:List, a1:int) -> a0:List (header sempre o mesmo)
                .globl kof_list_add
                kof_list_add:
                    addi sp, sp, -48
                    sw   ra, 44(sp)
                    sw   s0, 40(sp)
                    sw   s1, 36(sp)
                    sw   s2, 32(sp)
                    sw   s3, 28(sp)
                    sw   s4, 24(sp)
                    mv   s0, a0                  # header
                    mv   s1, a1                  # value
                    lw   s2, 0(s0)               # len
                    lw   s3, 4(s0)               # cap
                    beq  s2, s3, .Lla_grow
                .Lla_store:
                    lw   t0, 8(s0)               # data
                    slli t1, s2, 2
                    add  t0, t0, t1
                    sw   s1, 0(t0)               # data[len] = value
                    addi s2, s2, 1
                    sw   s2, 0(s0)               # len++
                    mv   a0, s0
                    j    .Lla_done
                .Lla_grow:
                    slli a0, s3, 3               # newcap*4 = (2*cap)*4 = cap*8 bytes
                    call kof_alloc
                    mv   s4, a0                  # newdata
                    lw   t1, 8(s0)               # olddata
                    li   t2, 0
                .Lla_copy:
                    bge  t2, s3, .Lla_copied
                    lw   t3, 0(t1)
                    slli t4, t2, 2
                    add  t4, t4, s4
                    sw   t3, 0(t4)
                    addi t2, t2, 1
                    j    .Lla_copy
                .Lla_copied:
                    sw   s4, 8(s0)               # data = newdata
                    slli s3, s3, 1               # cap *= 2
                    sw   s3, 4(s0)
                    j    .Lla_store
                .Lla_done:
                    lw   s4, 24(sp)
                    lw   s3, 28(sp)
                    lw   s2, 32(sp)
                    lw   s1, 36(sp)
                    lw   s0, 40(sp)
                    lw   ra, 44(sp)
                    addi sp, sp, 48
                    ret

                # kof_list_size(a0:List) -> a0:int
                .globl kof_list_size
                kof_list_size:
                    lw   a0, 0(a0)
                    ret
                """;
    }
}
