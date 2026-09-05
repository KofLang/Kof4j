package dev.kof.compiler;

/**
Emissão do ASM de db5 do runtime nativo.
 * Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeDb5 {

    private RuntimeDb5() {}

    static void emit(StringBuilder sb) {
        sb.append("""
                leaq 5(%rcx), %rdx
                call kof_net_write
                movq 32(%rsp), %rdi
                movq %r13, %rsi
                movl $4096, %edx
                call kof_net_read
                testq %rax, %rax
                jle .Ldb_exec_bad\\n
                cmpb $0xFF, 4(%r13)
                je .Ldb_exec_bad\\n
                cmpb $0x00, 4(%r13)
                jne .Ldb_exec_bad\\n
                movzbl 5(%r13), %eax
                cmpb $0xFC, %al
                je .Ldb_exec_afc\\n
                cmpb $0xFD, %al
                je .Ldb_exec_afd\\n
                jmp .Ldb_exec_done\\n
            .Ldb_exec_afc\\n:
                movzwl 6(%r13), %eax
                jmp .Ldb_exec_done\\n
            .Ldb_exec_afd\\n:
                movzbl 6(%r13), %eax
                movzbl 7(%r13), %edx
                shll $8, %edx
                orl %edx, %eax
                movzbl 8(%r13), %edx
                shll $16, %edx
                orl %edx, %eax
                jmp .Ldb_exec_done\\n
            .Ldb_exec_bad\\n:
                xorl %eax, %eax
            .Ldb_exec_done\\n:
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

            KOF_DB_EXEC_N 1
            KOF_DB_EXEC_N 2
            KOF_DB_EXEC_N 3
            KOF_DB_EXEC_N 4

            .macro KOF_DB_QUERY_N n
            .globl kof_db_query\\n
            .type kof_db_query\\n, @function
            kof_db_query\\n:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $40, %rsp
                movq %rdi, %rbx
                movq %rsi, %r12
                .if \\n >= 1
                movq %rdx, %r13
                .endif
                .if \\n >= 2
                movq %rcx, %r14
                .endif
                .if \\n >= 3
                movq %r8, %r15
                .endif
                .if \\n >= 4
                movq %r9, 16(%rsp)
                .endif
                call kof_db_resolve
                movq %rax, 32(%rsp)
                movq %rbx, %rdi
                call kof_db_type
                cmpl $1, %eax
                je .Ldb_query_sqlite\\n
                cmpl $2, %eax
                je .Ldb_query_mysql\\n
                jmp .Ldb_query_bad\\n
            .Ldb_query_sqlite\\n:
                leaq 24(%r12), %rsi
                movq 32(%rsp), %rdi
                movq $-1, %rdx
                movq %rsp, %rcx
                xorl %r8d, %r8d
                call sqlite3_prepare_v2
                movq (%rsp), %r12
                .if \\n >= 1
                movq %r12, %rdi
                movl $1, %esi
                movq %r13, %rdx
                call kof_db_bind
                .endif
                .if \\n >= 2
                movq %r12, %rdi
                movl $2, %esi
                movq %r14, %rdx
                call kof_db_bind
                .endif
                .if \\n >= 3
                movq %r12, %rdi
                movl $3, %esi
                movq %r15, %rdx
                call kof_db_bind
                .endif
                .if \\n >= 4
                movq %r12, %rdi
                movl $4, %esi
                movq 16(%rsp), %rdx
                call kof_db_bind
                .endif
                call kof_list_new
                movq %rax, %r14
            .Ldb_query_row\\n:
                movq %r12, %rdi
                call sqlite3_step
                cmpl $100, %eax
                jne .Ldb_query_sqlite_done\\n
                call kof_json_builder_new
                movq %rax, %r15
                movq %r15, %rdi
                movl $'{', %esi
                call kof_json_builder_char
                xorl %ebx, %ebx
            .Ldb_query_col\\n:
                movq %r12, %rdi
                call sqlite3_column_count
                cmpl %eax, %ebx
                jge .Ldb_query_end\\n
                testl %ebx, %ebx
                jz .Ldb_query_comma\\n
                movq %r15, %rdi
                movl $44, %esi
                call kof_json_builder_char
            .Ldb_query_comma\\n:
                movq %r12, %rdi
                movl %ebx, %esi
                call sqlite3_column_name
                movq %rax, 24(%rsp)
                movq 24(%rsp), %rdi
                call kof_io_strlen
                movq 24(%rsp), %rdi
                movq %rax, %rsi
                call kof_io_make_string
                movq %rax, %rdi
                call kof_json_encode_string
                movq %r15, %rdi
                movq %rax, %rsi
                call kof_json_builder_str
                movq %r15, %rdi
                movl $58, %esi
                call kof_json_builder_char
                movq %r12, %rdi
                movl %ebx, %esi
                call sqlite3_column_type
                cmpl $1, %eax
                je .Ldb_query_int\\n
                cmpl $3, %eax
                je .Ldb_query_text\\n
                leaq .Ldb_null(%rip), %rdi
                xorl %esi, %esi
                call kof_io_make_string
                jmp .Ldb_query_val\\n
            .Ldb_query_int\\n:
                movq %r12, %rdi
                movl %ebx, %esi
                call sqlite3_column_int
                movl %eax, %edi
                call kof_json_encode_int
                jmp .Ldb_query_val\\n
            .Ldb_query_text\\n:
                movq %r12, %rdi
                movl %ebx, %esi
                call sqlite3_column_text
                movq %rax, 24(%rsp)
                movq 24(%rsp), %rdi
                call kof_io_strlen
                movq 24(%rsp), %rdi
                movq %rax, %rsi
                call kof_io_make_string
                movq %rax, %rdi
                call kof_json_encode_string
            .Ldb_query_val\\n:
                movq %r15, %rdi
                movq %rax, %rsi
                call kof_json_builder_str
                incl %ebx
                jmp .Ldb_query_col\\n
            .Ldb_query_end\\n:
                movq %r15, %rdi
                movl $'}', %esi
                call kof_json_builder_char
                movq %r15, %rdi
                call kof_json_builder_result
                movq %r14, %rdi
                movq %rax, %rsi
                call kof_list_add
                jmp .Ldb_query_row\\n
            .Ldb_query_sqlite_done\\n:
                movq %r12, %rdi
                call sqlite3_finalize
                movq %r14, %rax
                jmp .Ldb_query_done\\n
            .Ldb_query_mysql\\n:
                .if \\n >= 1
                # binario: PREPARE + EXECUTE + parse de linhas binarias
                leaq .Ldb_prep_args(%rip), %rax
                movq %r13, 0(%rax)
                .if \\n >= 2
                movq %r14, 8(%rax)
                .endif
                .if \\n >= 3
                movq %r15, 16(%rax)
                .endif
                .if \\n >= 4
                movq 16(%rsp), %rcx
                movq %rcx, 24(%rax)
                .endif
                movq 32(%rsp), %rdi
                movq %r12, %rsi
                call kof_db_mysql_prepare
                testl %eax, %eax
                jz .Ldb_query_subst\\n
                movq 32(%rsp), %rdi
                movl %eax, %esi
                movl $\\n, %edx
                call kof_db_mysql_prep_query
                movq %rax, %r14              # list (ou 0)
                testq %r14, %r14
                jz .Ldb_query_subst\\n
                movq %r14, %rax
                jmp .Ldb_query_done\\n
            .Ldb_query_subst\\n:
                movq %r13, %rdi
                call kof_db_mysql_render
                movq %rax, %rsi
                movq %r12, %rdi
                call kof_db_mysql_replace_q
                movq %rax, %r12
                .endif
                .if \\n >= 2
                movq %r14, %rdi
                call kof_db_mysql_render
                movq %rax, %rsi
                movq %r12, %rdi
                call kof_db_mysql_replace_q
                movq %rax, %r12
                .endif
                .if \\n >= 3
                movq %r15, %rdi
                call kof_db_mysql_render
                movq %rax, %rsi
                movq %r12, %rdi
                call kof_db_mysql_replace_q
                movq %rax, %r12
                .endif
                .if \\n >= 4
                movq 16(%rsp), %rdi
                call kof_db_mysql_render
                movq %rax, %rsi
                movq %r12, %rdi
                call kof_db_mysql_replace_q
                movq %rax, %r12
                .endif
                # COM_QUERY + parse do resultset pacote a pacote
                leaq .Ldb_mysql_buf(%rip), %r13
                movb $0x03, 4(%r13)
                leaq 24(%r12), %rsi
                movl 16(%r12), %ecx
                movq %rcx, %rdx
                leaq 5(%r13), %rdi
                call kof_memcpy
                leal 1(%ecx), %eax
                movb %al, 0(%r13)
                shrl $8, %eax
                movb %al, 1(%r13)
                shrl $8, %eax
                movb %al, 2(%r13)
                movb $0, 3(%r13)
                movq 32(%rsp), %rdi
                movq %r13, %rsi
                leaq 5(%rcx), %rdx
                call kof_net_write
                # reader de pacotes: reset + 1º pacote
                movq 32(%rsp), %rdi
                call kof_db_mysql_reset
                call kof_db_mysql_next
                testq %rax, %rax
                jle .Ldb_query_bad\\n
                movq %rsi, 24(%rsp)          # salva payload (kof_list_new clobbera rsi)
                cmpb $0xFF, (%rsi)
                je .Ldb_query_bad\\n
                call kof_list_new
                movq %rax, %r14
                movq 24(%rsp), %rsi          # restaura payload
                # col count (lenenc) do 1º pacote
                call kof_db_mysql_lenenc
                movl %eax, %r13d
                # column definitions: um pacote por coluna
                xorl %ebx, %ebx
            .Ldb_mysql_cols\\n:
                cmpl %r13d, %ebx
                jge .Ldb_mysql_cols_done\\n
                call kof_db_mysql_next
                testq %rax, %rax
                jle .Ldb_query_bad\\n
                # pula cat, schema, table, org_table (4 lenenc: len + addq p/ dados)
                call kof_db_mysql_lenenc
                addq %rax, %rsi
                call kof_db_mysql_lenenc
                addq %rax, %rsi
                call kof_db_mysql_lenenc
                addq %rax, %rsi
                call kof_db_mysql_lenenc
                addq %rax, %rsi
                # name = lenenc string: len em eax, dados em rsi
                call kof_db_mysql_lenenc
                movq %rsi, .Ldb_mysql_names(,%rbx,8)
                movl %eax, .Ldb_mysql_names+512(,%rbx,4)
                # pula org_name (lenenc)
                call kof_db_mysql_lenenc
                addq %rax, %rsi
                incq %rbx
                jmp .Ldb_mysql_cols\\n
            .Ldb_mysql_cols_done\\n:
                # pacote apos colunas: 0x00 (OK, sem resultset) ou 0xFE (EOF)
                call kof_db_mysql_next
                testq %rax, %rax
                jle .Ldb_query_done\\n
                cmpb $0x00, (%rsi)
                je .Ldb_query_mydone\\n
                jmp .Ldb_mysql_rows\\n
            .Ldb_mysql_rows\\n:
                call kof_db_mysql_next
                testq %rax, %rax
                jle .Ldb_query_mydone\\n
                movzbl (%rsi), %eax
                cmpb $0xFF, %al
                je .Ldb_query_mydone\\n
                cmpb $0xFE, %al
                je .Ldb_query_mydone\\n
                # pacote de linha: monta o JSON object (cursor do pacote em 24(%rsp))
                movq %rsi, 24(%rsp)
                call kof_json_builder_new
                movq %rax, %r15
                movq %r15, %rdi
                movl $'{', %esi
                call kof_json_builder_char
                xorl %ebx, %ebx
            .Ldb_mysql_col\\n:
                cmpl %r13d, %ebx
                jge .Ldb_mysql_row_end\\n
                testl %ebx, %ebx
                jz .Ldb_mysql_nocomma\\n
                movq %r15, %rdi
                movl $44, %esi
                call kof_json_builder_char
            .Ldb_mysql_nocomma\\n:
                # nome da coluna
                movq .Ldb_mysql_names(,%rbx,8), %rdi
                movl .Ldb_mysql_names+512(,%rbx,4), %esi
                call kof_io_make_string
                movq %rax, %rdi
                call kof_json_encode_string
                movq %r15, %rdi
                movq %rax, %rsi
                call kof_json_builder_str
                movq %r15, %rdi
                movl $58, %esi
                call kof_json_builder_char
                # valor: lenenc; NULL = 0xFB
                movq 24(%rsp), %rsi
                movzbl (%rsi), %eax
                cmpb $0xFB, %al
                je .Ldb_mysql_null\\n
                call kof_db_mysql_lenenc
                leaq (%rsi,%rax), %r10
                movq %r10, 24(%rsp)           # cursor = fim dos dados
                movq %rsi, %rdi               # rdi = src (dados)
                movq %rax, %rsi               # rsi = len (make_string: rdi=src, rsi=len)
                call kof_io_make_string
                movq %rax, %r12
                xorl %r10d, %r10d
            .Ldb_mysql_num\\n:
                cmpl 16(%r12), %r10d
                jge .Ldb_mysql_is_num\\n
                movzbl 24(%r12,%r10), %eax
                cmpb $'0', %al
                jb .Ldb_mysql_is_str\\n
                cmpb $'9', %al
                ja .Ldb_mysql_is_str\\n
                incq %r10
                jmp .Ldb_mysql_num\\n
            .Ldb_mysql_is_num\\n:
                movq %r15, %rdi
                movq %r12, %rsi
                call kof_json_builder_str
                jmp .Ldb_mysql_val\\n
            .Ldb_mysql_is_str\\n:
                movq %r12, %rdi
                call kof_json_encode_string
                movq %r15, %rdi
                movq %rax, %rsi
                call kof_json_builder_str
                jmp .Ldb_mysql_val\\n
            .Ldb_mysql_null\\n:
                leaq .Ldb_mysql_nullstr(%rip), %rdi
                xorl %esi, %esi
                call kof_io_make_string
                movq %r15, %rdi
                movq %rax, %rsi
                call kof_json_builder_str
                movq 24(%rsp), %rsi
                incq %rsi
            """);
    }
}