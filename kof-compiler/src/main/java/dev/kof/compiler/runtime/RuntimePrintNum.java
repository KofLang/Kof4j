package dev.kof.compiler.runtime;
import dev.kof.compiler.NativeRuntime;

/**
 * Emissão do ASM de impressão numérica (kof_print_int/float/double) do
 * runtime nativo. Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
public final class RuntimePrintNum {

    private RuntimePrintNum() {}

    public static void emitPrintInt(StringBuilder sb) {
        sb.append("""
            .globl kof_print_int
            .type kof_print_int, @function
            kof_print_int:
                pushq %rbx
                pushq %r12
                pushq %r13
                movl %edi, %eax
                movq $0, %r12
                testl %eax, %eax
                jns .Lkof_print_int_pos
                movq $1, %r12
                negl %eax
            .Lkof_print_int_pos:
                movl %eax, %r13d
                movq $0, %rbx
                movl $10, %ecx
            .Lkof_print_int_count:
                xorl %edx, %edx
                divl %ecx
                incq %rbx
                testl %eax, %eax
                jnz .Lkof_print_int_count
                testq %r12, %r12
                jz .Lkof_print_int_count_done
                incq %rbx
            .Lkof_print_int_count_done:
                leaq -48(%rsp), %rsi
                addq %rbx, %rsi
                movl %r13d, %eax
                movq $0, %r13
                movl $10, %ecx
            .Lkof_print_int_loop:
                xorl %edx, %edx
                divl %ecx
                addb $48, %dl
                movb %dl, (%rsi)
                decq %rsi
                incq %r13
                testl %eax, %eax
                jnz .Lkof_print_int_loop
                testq %r12, %r12
                jz .Lkof_print_int_negdone
                movb $45, (%rsi)
                incq %r13
            .Lkof_print_int_negdone:
                testq %r12, %r12
                jnz .Lkof_print_int_ready
                incq %rsi
            .Lkof_print_int_ready:
                movq %r13, %rdx
                movq $1, %rax
                movq $1, %rdi
                syscall
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }

    public static void emitPrintFloat(StringBuilder sb) {
        sb.append("""
            .section .data
            .Lfmt_float: .asciz "%.16g"
            .Lfmt_double: .asciz "%.16g"
            .section .text
            .globl kof_print_float
            .type kof_print_float, @function
            kof_print_float:
                pushq %rbp
                movq %rsp, %rbp
                pushq %rbx
                pushq %r12
                subq $80, %rsp
                cvtss2sd %xmm0, %xmm0
                leaq -72(%rbp), %r12
                movq %r12, %rdi
                movq $64, %rsi
                leaq .Lfmt_float(%rip), %rdx
                movl $1, %eax
                movq %rsp, %rbx
                andq $-16, %rsp             # alinha para snprintf
                call snprintf
                movq %rbx, %rsp
                jmp kof_print_dbl_emit      # +6: float imprime como double
            .globl kof_print_double
            .type kof_print_double, @function
            kof_print_double:
                pushq %rbp
                movq %rsp, %rbp
                pushq %rbx
                pushq %r12
                subq $80, %rsp
                leaq -72(%rbp), %r12
                movq %r12, %rdi
                movq $64, %rsi
                leaq .Lfmt_double(%rip), %rdx
                movl $1, %eax
                movq %rsp, %rbx
                andq $-16, %rsp             # alinha para snprintf
                call snprintf
                movq %rbx, %rsp
            kof_print_dbl_emit:
                # bug 44: NaN/Infinity passam retos (JVM idem).
                # inteiro-válido (sem '.', 'e', 'n'/'i' de nan/inf) → append ".0"
                # (JDK Double.toString: println(5.0) == "5.0", não "5").
                # write via syscall (NÃO printf) — bug 44 face (b): misturar
                # stdout-buffered (printf) com write direto (Int/String)
                # REORDENAVA a saída inteira do programa.
                xorl %ecx, %ecx             # rc = len
            .Lkof_dbl_emit_len:
                cmpb $0, (%r12,%rcx)
                je .Lkof_dbl_emit_have
                incq %rcx
                jmp .Lkof_dbl_emit_len
            .Lkof_dbl_emit_have:
                xorl %ebx, %ebx             # precisa .0? (0 = ainda não achou)
                testq %rcx, %rcx
                jz .Lkof_dbl_emit_write     # vazio → imprime como está
                xorl %edx, %edx             # idx
            .Lkof_dbl_emit_scan:
                movb (%r12,%rdx), %al
                cmpb $46, %al               # '.' → decimal, ok
                je .Lkof_dbl_emit_write
                cmpb $101, %al              # 'e' → notação científica, ok
                je .Lkof_dbl_emit_write
                cmpb $110, %al              # 'n' de nan
                je .Lkof_dbl_emit_write
                cmpb $105, %al              # 'i' de inf/Infinity
                je .Lkof_dbl_emit_write
                incq %rdx
                cmpq %rcx, %rdx
                jb .Lkof_dbl_emit_scan
                movl $1, %ebx               # inteiro-válido → precisa .0
            .Lkof_dbl_emit_write:
                testq %rbx, %rbx
                jz .Lkof_dbl_emit_ok
                # append ".0" ao buffer (64 bytes: %.16g de 1 dígito ocupa
                # no máx ~24 — sempre cabe) — o mesmo buffer é reaproveitado
                # pelo kof_double_to_string (String) via .Lkof_dbl_str_done
                leaq (%r12,%rcx), %rsi
                movw $12334, (%rsi)         # 0x302E = ".0" little-endian ('.','0')
                movb $0, 2(%rsi)
                addq $2, %rcx
                movb $0, (%r12,%rcx)
            .Lkof_dbl_emit_ok:
                movq $1, %rax               # SYS_write
                movq $1, %rdi               # stdout
                leaq -72(%rbp), %rsi
                movq %rcx, %rdx
                syscall
                addq $80, %rsp
                popq %r12
                popq %rbx
                popq %rbp
                ret
            """);
    }

    public static void emitPrintDouble(StringBuilder sb) {
        // emitted together with emitPrintFloat (keep for symmetry)
    }

}