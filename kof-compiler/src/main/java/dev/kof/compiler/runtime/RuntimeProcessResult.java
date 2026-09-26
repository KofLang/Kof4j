package dev.kof.compiler.runtime;

/**
 * Content printer do {@code process.Result} no Native x86-64 (linha 1, fatia E).
 *
 * <p>Contrato JVM-oráculo (§367): {@code
 * ProcessResult[exitCode=N, stdout=S, stderr=E]}, com os newlines TRAILING
 * removidos de cada stream. O Result nativo é opaco ({@code
 * stdout@0, stderr@8, exitCode@16}) e não tem vtable; a emissão do
 * {@code String.valueOf(Result)} chama esta peça diretamente — nunca
 * {@code kof_box_to_string} sobre um ponteiro cru.
 */
public final class RuntimeProcessResult {

    private RuntimeProcessResult() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # ── Result de kof.process: toString por CONTEUDO (JVM §367) ─────
            .section .rodata
            .Lkof_prts_prefix:
                .ascii "ProcessResult[exitCode="
            .Lkof_prts_stdout:
                .ascii ", stdout="
            .Lkof_prts_stderr:
                .ascii ", stderr="
            .Lkof_prts_suffix:
                .ascii "]"
            .Lkof_prts_empty:
                .ascii ""
            .section .text

            # .Lkof_prts_trim(rdi=KofString*) -> rax=copia sem CR/LF final.
            # O delimitador e ASCII, portanto a varredura byte-a-byte da cauda
            # e fiel ao contrato JVM sem interpretar o resto do UTF-8.
            .Lkof_prts_trim:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                testq %rbx, %rbx
                jz .Lkof_prts_trim_empty
                movl 16(%rbx), %r12d
                leaq 24(%rbx), %r13
                addq %r12, %r13
            .Lkof_prts_trim_scan:
                testl %r12d, %r12d
                jz .Lkof_prts_trim_copy
                cmpb $10, -1(%r13)
                je .Lkof_prts_trim_drop
                cmpb $13, -1(%r13)
                je .Lkof_prts_trim_drop
                jmp .Lkof_prts_trim_copy
            .Lkof_prts_trim_drop:
                decq %r13
                decl %r12d
                jmp .Lkof_prts_trim_scan
            .Lkof_prts_trim_copy:
                leaq 24(%rbx), %rdi
                movl %r12d, %esi
                call kof_string_from_literal
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkof_prts_trim_empty:
                leaq .Lkof_prts_empty(%rip), %rdi
                xorl %esi, %esi
                call kof_string_from_literal
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_process_result_to_string(rdi=Result*) -> rax=KofString*
            .globl kof_process_result_to_string
            .type kof_process_result_to_string, @function
            kof_process_result_to_string:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %r15
                movq 0(%r15), %rdi
                call .Lkof_prts_trim
                movq %rax, %r12
                movq 8(%r15), %rdi
                call .Lkof_prts_trim
                movq %rax, %r13
                movl 16(%r15), %edi
                call kof_int_to_string
                movq %rax, %r14
                leaq .Lkof_prts_prefix(%rip), %rdi
                movl $23, %esi
                call kof_string_from_literal
                movq %rax, %rbx
                movq %rbx, %rdi
                movq %r14, %rsi
                call kof_string_concat
                movq %rax, %rbx
                leaq .Lkof_prts_stdout(%rip), %rdi
                movl $9, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movq %rbx, %rdi
                call kof_string_concat
                movq %rax, %rbx
                movq %rbx, %rdi
                movq %r12, %rsi
                call kof_string_concat
                movq %rax, %rbx
                leaq .Lkof_prts_stderr(%rip), %rdi
                movl $9, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movq %rbx, %rdi
                call kof_string_concat
                movq %rax, %rbx
                movq %rbx, %rdi
                movq %r13, %rsi
                call kof_string_concat
                movq %rax, %rbx
                leaq .Lkof_prts_suffix(%rip), %rdi
                movl $1, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movq %rbx, %rdi
                call kof_string_concat
                movq %rax, %rbx
                movq %rbx, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }
}
