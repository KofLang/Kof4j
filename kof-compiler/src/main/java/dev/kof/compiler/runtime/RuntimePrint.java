package dev.kof.compiler.runtime;
import dev.kof.compiler.NativeRuntime;

/**
 * Emissão do ASM de impressão bruta (kof_print / kof_println) do runtime
 * nativo. Domínio isolado do NativeRuntime -- a extração NÃO muda o corpo
 * (refactor preserva semântica).
 */
public final class RuntimePrint {

    private RuntimePrint() {}

    public static void emitPrint(StringBuilder sb) {
        sb.append("""
            .globl kof_print
            .type kof_print, @function
            kof_print:
                pushq %rbx
                movq %rdi, %rbx
                xorq %rdx, %rdx
            .Lkof_print_len:
                cmpb $0, (%rbx,%rdx)
                je .Lkof_print_do
                incq %rdx
                jmp .Lkof_print_len
            .Lkof_print_do:
                movq $1, %rax
                movq $1, %rdi
                movq %rbx, %rsi
                syscall
                popq %rbx
                ret
            """);
    }

    public static void emitPrintln(StringBuilder sb) {
        sb.append("""
            .globl kof_println
            .type kof_println, @function
            kof_println:
                call kof_print
                pushq %rbx
                leaq .Lnewline(%rip), %rdi
                call kof_print
                popq %rbx
                ret
            """);
    }
}