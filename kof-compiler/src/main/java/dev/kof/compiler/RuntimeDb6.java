package dev.kof.compiler;

/**
Emissão do ASM de db6 do runtime nativo.
 * Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeDb6 {

    private RuntimeDb6() {}

    static void emit(StringBuilder sb) {
        sb.append("""
                movq %rsi, 24(%rsp)
            .Ldb_mysql_val\\n:
                incl %ebx
                jmp .Ldb_mysql_col\\n
            .Ldb_mysql_row_end\\n:
                movq %r15, %rdi
                movl $'}', %esi
                call kof_json_builder_char
                movq %r15, %rdi
                call kof_json_builder_result
                movq %r14, %rdi
                movq %rax, %rsi
                call kof_list_add
                jmp .Ldb_mysql_rows\\n
            .Ldb_query_bad\\n:
                call kof_list_new
            .Ldb_query_mydone\\n:
                movq %r14, %rax
            .Ldb_query_done\\n:
                addq $40, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret
            .endm

            KOF_DB_QUERY_N 0
            KOF_DB_QUERY_N 1
            KOF_DB_QUERY_N 2
            KOF_DB_QUERY_N 3
            KOF_DB_QUERY_N 4
            """);
    }
}