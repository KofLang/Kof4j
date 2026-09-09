package dev.kof.compiler.runtime;

/**
 * Runtime x86_64 — kof.random (STDLIB S10). Entropia SEMPRE getrandom(2)
 * (syscall 318 — mesma primitiva da crypto lane): NUNCA caseira (R11).
 *
 *  - kof_random_double()  -> Double em [0,1): 8 bytes aleatórios, 53 bits de
 *    mantissa (>>11) / 2^53 (mesmo algoritmo JVM); nunca 1.0; 0.0 em falha
 *    do SO (contrato do kof_sec_random_int: falha => 0, nunca saída fraca
 *    silenciosa — documentado na matriz stdrandom).
 *  - kof_random_boolean() -> Bool (bit baixo do 1º byte).
 *  - kof_random_int(bound) -> tail-jmp para kof_sec_random_int (reuso —
 *    rejection sampling já implementado lá; bound<=0 => 0 é o contrato dele).
 *  - kof_random_hex(n)    -> tail-jmp para kof_sec_random_hex (n<=0 => null).
 *
 * double = PRIMEIRO float de retorno em runtime x86 asm: xmm0 com o valor
 * (mesma convenção de kof_ui_measure_text — xorpd/fill xmm0). Chamador:
 * NativeMethodEmitter guarda %xmm0 p/ retorno Double.
 */
public final class RuntimeRandom {

    private RuntimeRandom() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # kof_random_double() -> Double em [0,1) via xmm0 (53-bit mantissa)
            .globl kof_random_double
            .type kof_random_double, @function
            kof_random_double:
                pushq %rbx
                subq $16, %rsp                  # buf 8 bytes (16-align)
                movq %rsp, %rdi
                movq $8, %rsi
                xorq %rdx, %rdx
                movq $318, %rax                 # getrandom
                syscall
                testq %rax, %rax
                js .Lrnd_d_fail
                movq (%rsp), %rax               # 64 bits aleatorios
                shrq $11, %rax                  # 53 bits (>= 0, < 2^53)
                cvtsi2sdq %rax, %xmm0           # v como double
                movsd .Lrnd_two53(%rip), %xmm1  # 2^53
                divsd %xmm1, %xmm0              # [0,1)
                addq $16, %rsp
                popq %rbx
                ret
            .Lrnd_d_fail:
                xorq %rax, %rax
                cvtsi2sdq %rax, %xmm0           # 0.0 (falha do SO)
                addq $16, %rsp
                popq %rbx
                ret

            # kof_random_boolean() -> Bool (bit baixo do 1º byte)
            .globl kof_random_boolean
            .type kof_random_boolean, @function
            kof_random_boolean:
                subq $16, %rsp
                movq %rsp, %rdi
                movq $1, %rsi
                xorq %rdx, %rdx
                movq $318, %rax
                syscall
                testq %rax, %rax
                js .Lrnd_b_fail
                movl (%rsp), %eax
                andl $1, %eax
                addq $16, %rsp
                ret
            .Lrnd_b_fail:
                xorl %eax, %eax
                addq $16, %rsp
                ret

            # kof_random_int(bound) / kof_random_hex(n): delegam à crypto lane
            # (rejection sampling e alocação de String já implementados lá).
            .globl kof_random_int
            .type kof_random_int, @function
            kof_random_int:
                jmp kof_sec_random_int

            .globl kof_random_hex
            .type kof_random_hex, @function
            kof_random_hex:
                jmp kof_sec_random_hex

            .section .rodata
            .balign 8
            .Lrnd_two53:
                .quad 0x4330000000000000        # 9007199254740992.0 = 2^53
            .section .text
        """);
    }
}
