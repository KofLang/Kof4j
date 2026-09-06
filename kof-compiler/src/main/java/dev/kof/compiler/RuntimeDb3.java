package dev.kof.compiler;

/**
Emissão do ASM de db3 do runtime nativo.
 * Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
final class RuntimeDb3 {

    private RuntimeDb3() {}

    static void emit(StringBuilder sb) {
        sb.append("""
                leaq 1(%r11), %r10
                jmp .Ldb_no_at
            .Ldb_no_at_simple:
            .Ldb_no_at:
                movq %r10, %rsi
                xorl %r10d, %r10d
                xorq %r13, %r13
            .Ldb_mysql_host2:
                movzbl (%rsi), %eax
                testb %al, %al
                jz .Ldb_mysql_open
                cmpb $':', %al
                je .Ldb_mysql_colon2
                cmpb $'/', %al
                je .Ldb_mysql_slash2
                incq %rsi
                jmp .Ldb_mysql_host2
            .Ldb_mysql_colon2:
                movb $0, (%rsi)
                incq %rsi
            .Ldb_mysql_port2:
                movzbl (%rsi), %eax
                testb %al, %al
                jz .Ldb_mysql_open
                cmpb $'/', %al
                je .Ldb_mysql_slash2
                subl $'0', %eax
                imull $10, %r10d, %r10d
                addl %eax, %r10d
                incq %rsi
                jmp .Ldb_mysql_port2
            .Ldb_mysql_slash2:
                movb $0, (%rsi)
                incq %rsi
                movq %rsi, %r13
            .Ldb_mysql_open:
                testl %r10d, %r10d
                jnz .Ldb_mysql_port_ok
                movl $3306, %r10d
            .Ldb_mysql_port_ok:
                subq $16, %rsp
                movl %r10d, 8(%rsp)
                movq %r11, 0(%rsp)
                movl $2, %edi
                movl $1, %esi
                xorl %edx, %edx
                call kof_net_socket
                movq 0(%rsp), %r11
                movl 8(%rsp), %r10d
                addq $16, %rsp
                testq %rax, %rax
                js .Ldb_connect_bad
                movq %rax, %rbx
                leaq -48(%rsp), %r8
                subq $48, %rsp
                movw $2, (%r8)
                movl %r10d, %eax
                xchgb %al, %ah
                movw %ax, 2(%r8)
                testq %r11, %r11
                jz .Ldb_host_orig
                leaq 1(%r11), %rsi
                jmp .Ldb_host_ip
            .Ldb_host_orig:
                leaq 8(%r12), %rsi
            .Ldb_host_ip:
                movzbl (%rsi), %eax
                cmpb $'0', %al
                jb .Ldb_ip_fallback
                cmpb $'9', %al
                ja .Ldb_ip_fallback
                xorl %r9d, %r9d
                xorl %ecx, %ecx
                jmp .Ldb_ip
            .Ldb_ip_fallback:
                movb $127, 4(%r8)
                movb $0, 5(%r8)
                movb $0, 6(%r8)
                movb $1, 7(%r8)
                jmp .Ldb_ip_done
            .Ldb_ip:
                movzbl (%rsi), %eax
                testb %al, %al
                jz .Ldb_ip_last
                cmpb $'.', %al
                je .Ldb_ip_store
                subl $'0', %eax
                imull $10, %ecx, %ecx
                addl %eax, %ecx
                incq %rsi
                jmp .Ldb_ip
            .Ldb_ip_store:
                movb %cl, 4(%r8,%r9,1)
                incq %r9
                xorl %ecx, %ecx
                incq %rsi
                jmp .Ldb_ip
            .Ldb_ip_last:
                movb %cl, 4(%r8,%r9,1)
            .Ldb_ip_done:
                movq %rbx, %rdi
                movq %r8, %rsi
                movl $16, %edx
                movq $42, %rax
                syscall
                testq %rax, %rax
                js .Ldb_connect_bad
                addq $48, %rsp
                # handshake: read server greeting via kof_net_read
                leaq .Ldb_mysql_buf(%rip), %r12
                movq %rbx, %rdi
                movq %r12, %rsi
                movl $4096, %edx
                call kof_net_read
                testq %rax, %rax
                jle .Ldb_connect_bad
                cmpb $0x0A, 4(%r12)
                jne .Ldb_connect_bad
                # --- Parse greeting seed (20 bytes) into .Ldb_mysql_names ---
                leaq 4(%r12), %rsi
                incq %rsi
            .Ldb_skip_ver:
                movzbl (%rsi), %eax
                testb %al, %al
                je .Ldb_skip_ver_done
                incq %rsi
                jmp .Ldb_skip_ver
            .Ldb_skip_ver_done:
                incq %rsi
                addq $4, %rsi
                leaq .Ldb_mysql_names(%rip), %rdi
                xorl %ecx, %ecx
            .Ldb_copy_seed8:
                cmpl $8, %ecx
                jge .Ldb_seed8_done
                movb (%rsi,%rcx), %al
                movb %al, (%rdi,%rcx)
                incq %rcx
                jmp .Ldb_copy_seed8
            .Ldb_seed8_done:
                addq $8, %rsi
                addq $19, %rsi
                xorl %ecx, %ecx
            .Ldb_copy_seed12:
                cmpl $12, %ecx
                jge .Ldb_seed12_done
                movb (%rsi,%rcx), %al
                testb %al, %al
                je .Ldb_seed12_done
                movb %al, 8(%rdi,%rcx)
                incq %rcx
                jmp .Ldb_copy_seed12
            .Ldb_seed12_done:
                testq %r15, %r15
                jz .Ldb_no_scramble_needed
                cmpl $0, 16(%r15)
                je .Ldb_no_scramble_needed
                leaq .Ldb_mysql_names+32(%rip), %rdi
                leaq .Ldb_mysql_names(%rip), %rsi
                movl $20, %edx
                movq %r15, %rcx
                call kof_db_mysql_scramble
                jmp .Ldb_scramble_done
            .Ldb_no_scramble_needed:
                # no scramble needed
                nop
            .Ldb_scramble_done:
                leaq .Ldb_mysql_buf(%rip), %r8
                movl $0x00088209, 4(%r8)   # +0x0008 CLIENT_CONNECT_WITH_DB (db no auth)
                movl $0x01000000, 8(%r8)
                movb $0x21, 12(%r8)
                leaq 13(%r8), %rdi
                xorl %ecx, %ecx
            .Ldb_auth_zero2:
                cmpl $23, %ecx
                jge .Ldb_auth_zero_done2
                movb $0, (%rdi,%rcx)
                incq %rcx
                jmp .Ldb_auth_zero2
            .Ldb_auth_zero_done2:
                leaq 36(%r8), %rdi
                testq %r14, %r14
                jz .Ldb_auth_user_empty2
                leaq 24(%r14), %rsi
                movl 16(%r14), %ecx
                movq %rcx, %rdx
                call kof_memcpy
                leaq 36(%r8), %rdi
                addq %rcx, %rdi
                jmp .Ldb_auth_user_end2
            .Ldb_auth_user_empty2:
                movq %rdi, %rsi
                movq %rsi, %rdi
            .Ldb_auth_user_end2:
                movb $0, (%rdi)
                incq %rdi
                testq %r15, %r15
                jz .Ldb_auth_no_pass
                cmpl $0, 16(%r15)
                je .Ldb_auth_no_pass
                movb $20, (%rdi)
                incq %rdi
                leaq .Ldb_mysql_names+32(%rip), %rsi
                xorl %ecx, %ecx
            .Ldb_copy_scramble_first:
                cmpl $20, %ecx
                jge .Ldb_scramble_copied
                movb (%rsi,%rcx), %al
                movb %al, (%rdi,%rcx)
                incq %rcx
                jmp .Ldb_copy_scramble_first
            .Ldb_scramble_copied:
                addq $20, %rdi
                jmp .Ldb_auth_db2
            .Ldb_auth_no_pass:
                movb $0, (%rdi)
                incq %rdi
            .Ldb_auth_db2:
                testq %r13, %r13
                jz .Ldb_auth_db_empty
                movq %r13, %rax
                jmp .Ldb_auth_db_copy
            .Ldb_auth_db_empty:
                leaq .Ldb_mysql_empty(%rip), %rax
            .Ldb_auth_db_copy:
            .Ldb_auth_db_loop:
                movzbl (%rax), %ecx
                movb %cl, (%rdi)
                testb %cl, %cl
                je .Ldb_auth_db_done
                incq %rax
                incq %rdi
                jmp .Ldb_auth_db_loop
            .Ldb_auth_db_done:
                incq %rdi
                leaq .Ldb_mysql_plugin(%rip), %rsi
            .Ldb_auth_plug_loop:
                movzbl (%rsi), %ecx
                movb %cl, (%rdi)
                testb %cl, %cl
                je .Ldb_auth_plug_done
                incq %rsi
                incq %rdi
                jmp .Ldb_auth_plug_loop
            .Ldb_auth_plug_done:
                incq %rdi
                # header: len = rdi - (buf+4), seq 1
                leaq .Ldb_mysql_buf(%rip), %r12
                subq %r12, %rdi
                subq $4, %rdi
                movl %edi, %eax
                movb %al, 0(%r12)
                shrl $8, %eax
                movb %al, 1(%r12)
                shrl $8, %eax
                movb %al, 2(%r12)
                movb $1, 3(%r12)
                leaq 4(%rdi), %rdx
                movq %rbx, %rdi
                movq %r12, %rsi
                call kof_net_write
                movq %rbx, %rdi
                movq %r12, %rsi
                movl $4096, %edx
                call kof_net_read
                testq %rax, %rax
                jle .Ldb_connect_bad
                cmpb $0xFE, 4(%r12)
                jne .Ldb_auth_done
                # AuthSwitchRequest: [0xFE][plugin NUL][seed...]
                # acha o seed após o plugin; seedlen = len - offset
                leaq 5(%r12), %rsi
            .Ldb_switch_find_plugin_end:
                movzbl (%rsi), %eax
                testb %al, %al
                je .Ldb_switch_plugin_end
                incq %rsi
                jmp .Ldb_switch_find_plugin_end
            .Ldb_switch_plugin_end:
                incq %rsi
                movq %rsi, %r13          # seed
                # len do pacote (3 bytes LE) = header len
                movzbl 0(%r12), %eax
                movzbl 1(%r12), %ecx
                shll $8, %ecx
                orl %ecx, %eax
                movzbl 2(%r12), %ecx
                shll $16, %ecx
                orl %ecx, %eax
                subq %r12, %rsi
                subq $4, %rsi
                subl %esi, %eax          # seedlen = pacote - offset (should be 21, but use 20 without terminator)
                movl $20, %edx
                movq %r13, %rsi
                # sem pass: responde vazio
                testq %r15, %r15
                jz .Ldb_switch_no_scramble2
                # out do scramble em .Ldb_mysql_names+32; seed NOVA do switch em r13
                leaq .Ldb_mysql_names+32(%rip), %rdi
                movq %r13, %rsi
                movl $20, %edx
                movq %r15, %rcx
                call kof_db_mysql_scramble
                jmp .Ldb_switch_scramble_done2
            .Ldb_switch_no_scramble2:
                leaq .Ldb_mysql_names+32(%rip), %rdi
                movl $0, 0(%rdi)
            .Ldb_switch_scramble_done2:
                # AuthSwitchResponse: 20-byte scramble (sem plugin name), seq 3
                leaq .Ldb_mysql_buf(%rip), %r8
                leaq .Ldb_mysql_names+32(%rip), %rsi
                leaq 4(%r8), %rdi
                xorl %ecx, %ecx
            .Ldb_switch_copy_scramble2:
                cmpl $20, %ecx
                jge .Ldb_switch_copy_done2
                movb (%rsi,%rcx), %al
                movb %al, (%rdi,%rcx)
                incq %rcx
                jmp .Ldb_switch_copy_scramble2
            .Ldb_switch_copy_done2:
                movb $20, 0(%r8)
                movb $0, 1(%r8)
                movb $0, 2(%r8)
                movb $3, 3(%r8)
                movq %rbx, %rdi
                movq %r8, %rsi
                movq $24, %rdx
                call kof_net_write
                movq %rbx, %rdi
                movq %r12, %rsi
                movl $4096, %edx
                call kof_net_read
                testq %rax, %rax
                jle .Ldb_connect_bad
            .Ldb_auth_done:
                cmpb $0xFF, 4(%r12)
                je .Ldb_connect_bad
                movb $4, .Ldb_mysql_seq(%rip)
                movq %rbx, %r12
                movl $2, %eax
            .Ldb_connect_register:
                movq .Ldb_count(%rip), %r13
                cmpq $63, %r13
                jge .Ldb_connect_bad
                movq %r12, .Ldb_slots(,%r13,8)
                movb %al, .Ldb_types(,%r13,1)
                incq %r13
                movq %r13, .Ldb_count(%rip)
                # handle = "db" + decimal(r13) em buffer de 48 bytes
                leaq -96(%rsp), %r14
                movq %r13, %rax
                leaq 47(%r14), %rcx
                movb $0, (%rcx)
                decq %rcx
                movq $10, %rbx
            .Ldb_itoa:
                xorl %edx, %edx
                divq %rbx
                addb $'0', %dl
                movb %dl, (%rcx)
                testq %rax, %rax
                je .Ldb_itoa_done
                decq %rcx
                jmp .Ldb_itoa
            .Ldb_itoa_done:
                decq %rcx
                movb $'b', (%rcx)
                decq %rcx
                movb $'d', (%rcx)
                # handle KofString na mao: alloc + header + copia inline
                movq %rcx, %rbx
                leaq 48(%r14), %rdx
                subq %rcx, %rdx
                decq %rdx
                movq %rdx, %rsi
                movq %rsi, %r13
                leal 25(%rsi), %edi
                call kof_alloc
                movq %rax, %r12
                movl $1, 0(%r12)
                movl $0, 4(%r12)
                movq $0, 8(%r12)
                movl %r13d, 16(%r12)
                movl $0, 20(%r12)
                xorl %ecx, %ecx
            .Ldb_handle_copy:
                cmpl %r13d, %ecx
                jge .Ldb_handle_copy_done
                movzbl (%rbx,%rcx), %eax
                movb %al, 24(%r12,%rcx)
                incq %rcx
                jmp .Ldb_handle_copy
            .Ldb_handle_copy_done:
                movb $0, 24(%r12,%r13)
                movq %r12, .Ldb_default_handle(%rip)   # conexao atual = default (tx)
                movq %r12, %rax
                addq $96, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret
            .Ldb_connect_fail:
                addq $40, %rsp
            .Ldb_connect_bad:
                xorl %eax, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret

            # kof_db_close(id: KofString) — handles both sqlite and mysql fd
            """);
    }
}