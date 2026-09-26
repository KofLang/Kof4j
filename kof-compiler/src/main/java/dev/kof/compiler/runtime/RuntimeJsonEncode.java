package dev.kof.compiler.runtime;
import dev.kof.compiler.NativeRuntime;

/**
 * Emissão do ASM do encode JSON (kof_json_encode_int/long/bool/double/float/string/
 * list/array) do runtime nativo. Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
public final class RuntimeJsonEncode {

    private RuntimeJsonEncode() {}

    public static void emitJsonEncode(StringBuilder sb) {
        sb.append("""
            .section .rodata
            .Ljson_null: .asciz "null"
            .section .text
            .globl kof_json_encode_int
            .type kof_json_encode_int, @function
            kof_json_encode_int:
                jmp kof_int_to_string

            .globl kof_json_encode_long
            .type kof_json_encode_long, @function
            kof_json_encode_long:
                jmp kof_long_to_string

            .globl kof_json_encode_bool
            .type kof_json_encode_bool, @function
            kof_json_encode_bool:
                jmp kof_bool_to_string

            .globl kof_json_encode_double
            .type kof_json_encode_double, @function
            kof_json_encode_double:
                # §180 (DECISIONS §6): NaN/±Infinity → JSON null (JSON não tem
                # esses valores); caso contrário o MESMO kof_double_to_string do
                # RuntimeDtoa (contrato JDK) — antes reimplementava o %.16g.
                pushq %rbp
                movq %rsp, %rbp
                movq %xmm0, %rax
                movq %rax, %rcx
                shrq $52, %rcx
                andl $0x7ff, %ecx
                cmpl $0x7ff, %ecx
                je .Lkof_je_dbl_null
                call kof_double_to_string
                popq %rbp
                ret
            .Lkof_je_dbl_null:
                leaq .Ljson_null(%rip), %rdi
                movl $4, %esi
                call kof_string_from_literal
                popq %rbp
                ret

            .globl kof_json_encode_float
            .type kof_json_encode_float, @function
            kof_json_encode_float:
                pushq %rbp
                movq %rsp, %rbp
                movd %xmm0, %eax
                movl %eax, %ecx
                shrl $23, %ecx
                andl $0xff, %ecx
                cmpl $0xff, %ecx
                je .Lkof_je_flt_null
                call kof_float_to_string
                popq %rbp
                ret
            .Lkof_je_flt_null:
                leaq .Ljson_null(%rip), %rdi
                movl $4, %esi
                call kof_string_from_literal
                popq %rbp
                ret

            .globl kof_json_encode_string
            .type kof_json_encode_string, @function
            kof_json_encode_string:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                call kof_json_builder_new
                movq %rax, %r12
                movq %r12, %rdi
                movl $34, %esi
                call kof_json_builder_char
                movl 16(%rbx), %r13d
                xorq %r14, %r14
            .Lkof_json_esc_loop:
                cmpl %r13d, %r14d
                jge .Lkof_json_esc_done
                leaq 24(%rbx), %rax
                movzbl (%rax,%r14), %eax
                cmpb $34, %al
                je .Lkof_json_esc_quote
                cmpb $92, %al
                je .Lkof_json_esc_backslash
                movq %r12, %rdi
                movl %eax, %esi
                call kof_json_builder_char
                incq %r14
                jmp .Lkof_json_esc_loop
            .Lkof_json_esc_quote:
                movq %r12, %rdi
                movl $92, %esi
                call kof_json_builder_char
                movq %r12, %rdi
                movl $34, %esi
                call kof_json_builder_char
                incq %r14
                jmp .Lkof_json_esc_loop
            .Lkof_json_esc_backslash:
                movq %r12, %rdi
                movl $92, %esi
                call kof_json_builder_char
                movq %r12, %rdi
                movl $92, %esi
                call kof_json_builder_char
                incq %r14
                jmp .Lkof_json_esc_loop
            .Lkof_json_esc_done:
                movq %r12, %rdi
                movl $34, %esi
                call kof_json_builder_char
                movq %r12, %rdi
                call kof_json_builder_result
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_json_encode_list
            .type kof_json_encode_list, @function
            kof_json_encode_list:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movl %esi, %r15d
                call kof_json_builder_new
                movq %rax, %r12
                movq %r12, %rdi
                movl $91, %esi
                call kof_json_builder_char
                movl 16(%rbx), %r13d
                xorq %r14, %r14
            .Lkof_json_el_loop:
                cmpl %r13d, %r14d
                jge .Lkof_json_el_done
                testq %r14, %r14
                jz .Lkof_json_el_no_comma
                movq %r12, %rdi
                movl $44, %esi
                call kof_json_builder_char
            .Lkof_json_el_no_comma:
                movq 24(%rbx), %rax
                movq (%rax,%r14,8), %rdi
                cmpl $1, %r15d
                je .Lkof_json_el_string
                cmpl $2, %r15d
                je .Lkof_json_el_bool
                cmpl $3, %r15d
                je .Lkof_json_el_double
                cmpl $5, %r15d
                je .Lkof_json_el_long
                call kof_json_encode_int
                jmp .Lkof_json_el_appended
            .Lkof_json_el_string:
                call kof_json_encode_string
                jmp .Lkof_json_el_appended
            .Lkof_json_el_bool:
                call kof_json_encode_bool
                jmp .Lkof_json_el_appended
            .Lkof_json_el_double:
                movq %rdi, %xmm0
                call kof_json_encode_double
                jmp .Lkof_json_el_appended
            .Lkof_json_el_long:
                call kof_json_encode_long
            .Lkof_json_el_appended:
                movq %r12, %rdi
                movq %rax, %rsi
                call kof_json_builder_str
                incq %r14
                jmp .Lkof_json_el_loop
            .Lkof_json_el_done:
                movq %r12, %rdi
                movl $93, %esi
                call kof_json_builder_char
                movq %r12, %rdi
                call kof_json_builder_result
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_json_encode_array
            .type kof_json_encode_array, @function
            kof_json_encode_array:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                call kof_json_builder_new
                movq %rax, %r12
                movq %r12, %rdi
                movl $91, %esi
                call kof_json_builder_char
                movl 16(%rbx), %r13d
                movl 20(%rbx), %r15d
                xorq %r14, %r14
            .Lkof_json_ea_loop:
                cmpl %r13d, %r14d
                jge .Lkof_json_ea_done
                testq %r14, %r14
                jz .Lkof_json_ea_no_comma
                movq %r12, %rdi
                movl $44, %esi
                call kof_json_builder_char
            .Lkof_json_ea_no_comma:
                leaq 24(%rbx), %rax
                cmpl $4, %r15d
                je .Lkof_json_ea_int
                movq (%rax,%r14,8), %rdi
                call kof_json_encode_string
                jmp .Lkof_json_ea_appended
            .Lkof_json_ea_int:
                movl (%rax,%r14,4), %edi
                call kof_json_encode_int
            .Lkof_json_ea_appended:
                movq %r12, %rdi
                movq %rax, %rsi
                call kof_json_builder_str
                incq %r14
                jmp .Lkof_json_ea_loop
            .Lkof_json_ea_done:
                movq %r12, %rdi
                movl $93, %esi
                call kof_json_builder_char
                movq %r12, %rdi
                call kof_json_builder_result
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # §106 (decisão 2b, 13/09): kof_json_encode_map(rdi=map, esi=tag
            # do VALOR: 0=int, 1=string, 2=bool) -> JSON objeto com chaves
            # SORTED (mesma superfície do JVM/interp). Copia chaves p/ lista,
            # selection-sort com kof_string_compare_to, monta "{k:v,...}".
                        # §106 (decisão 2b, 13/09): kof_json_encode_map(rdi=map, esi=tag
            # do VALOR: 0=int, 1=string, 2=bool) -> JSON objeto com chaves
            # SORTED (mesma superfície do JVM/interp). Copia chaves p/ lista,
            # selection-sort com kof_string_compare_to, monta "{k:v,...}".
            # Layout da pilha local (16 bytes apos os 5 pushes):
            #   0(%rsp) = tag do valor (4B) | 4(%rsp) pad
            #   8(%rsp) = j (4B)            | 12(%rsp) pad
            #   16(%rsp) = tmp de swap (8B)
            # Registros: rbx=map, r12=lista keys, r13d=i, r14d=min,
            #            r15d=n, rbp=builder (fase de montagem: n/i)
            .globl kof_json_encode_map
            .type kof_json_encode_map, @function
            kof_json_encode_map:
                pushq %rbx
                pushq %rbp
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $24, %rsp
                movq %rdi, %rbx             # rbx = map
                movl %esi, 0(%rsp)          # tag do valor
                movq %rbx, %rdi
                call kof_map_keys
                movq %rax, %r12             # r12 = lista de chaves
                movl 16(%r12), %r15d        # r15d = n chaves
                # selection sort: i em [0..n-1]; min; j em [i+1..n-1]
                xorl %r13d, %r13d           # i = 0
            .Lkjm_si:
                cmpl %r15d, %r13d
                jge .Lkjm_sdone
                movl %r13d, %r14d           # min = i
                leal 1(%r13), %eax          # j = i+1
                movl %eax, 8(%rsp)
            .Lkjm_sj:
                movl 8(%rsp), %eax          # j
                cmpl %r15d, %eax
                jge .Lkjm_swap
                movq %r12, %rdi
                movslq %eax, %rsi
                call kof_list_get
                movq %rax, %r8              # r8 = keys[j]  (r8 sobrevive? NAO: caller-saved
                                            # na ABI, mas aqui as unicas calls seguintes
                                            # sao list_get/compare_to que nao escrevem r8
                                            # garantido — uso mesmo assim com cuidado)
                movq %r12, %rdi
                movslq %r14d, %rsi
                call kof_list_get
                movq %rax, %rsi             # rsi = keys[min]
                movq %r8, %rdi              # rdi = keys[j]
                call kof_string_compare_to
                testl %eax, %eax
                jge .Lkjm_next
                movl 8(%rsp), %eax
                movl %eax, %r14d            # min = j
            .Lkjm_next:
                incl 8(%rsp)
                jmp .Lkjm_sj
            .Lkjm_swap:
                cmpl %r14d, %r13d
                je .Lkjm_iinc
                movq %r12, %rdi
                movslq %r13d, %rsi
                call kof_list_get
                movq %rax, 16(%rsp)         # tmp = keys[i]
                movq %r12, %rdi
                movslq %r14d, %rsi
                call kof_list_get
                movq %rax, %rdx             # keys[min]
                movq %r12, %rdi
                movslq %r13d, %rsi
                call kof_list_set           # keys[i] = keys[min]
                movq %r12, %rdi
                movslq %r14d, %rsi
                movq 16(%rsp), %rdx
                call kof_list_set           # keys[min] = tmp
            .Lkjm_iinc:
                incl %r13d
                jmp .Lkjm_si
            .Lkjm_sdone:
                # monta "{...}" com builder; rbp = builder
                call kof_json_builder_new
                movq %rax, %rbp
                movq %rbp, %rdi
                movl $123, %esi             # '{'
                call kof_json_builder_char
                xorl %r13d, %r13d           # i
            .Lkjm_loop:
                cmpl %r15d, %r13d
                jge .Lkjm_done
                testl %r13d, %r13d
                jz .Lkjm_nocomma
                movq %rbp, %rdi
                movl $44, %esi              # ','
                call kof_json_builder_char
            .Lkjm_nocomma:
                # chave: keys[i] -> encode string -> builder
                movq %r12, %rdi
                movslq %r13d, %rsi
                call kof_list_get
                movq %rax, %rdi
                call kof_json_encode_string
                movq %rbp, %rdi
                movq %rax, %rsi
                call kof_json_builder_str
                movq %rbp, %rdi
                movl $58, %esi              # ':'
                call kof_json_builder_char
                # valor = map.get(keys[i])
                movq %r12, %rdi
                movslq %r13d, %rsi
                call kof_list_get
                movq %rax, %rsi
                movq %rbx, %rdi
                call kof_map_get
                # encode pelo tag: 1=string, 2=bool, 7=caixa numerica
                # (§284-map: slot boxado MAGIC — abre via box_to_string; miss
                # null → "null" cru, mesmo output do oraculo JVM), senao int cru
                cmpl $1, 0(%rsp)
                je .Lkjm_valstr
                cmpl $2, 0(%rsp)
                je .Lkjm_valbool
                cmpl $3, 0(%rsp)
                je .Lkjm_valdouble
                cmpl $7, 0(%rsp)
                je .Lkjm_valbox
                movq %rax, %rdi
                call kof_json_encode_int
                jmp .Lkjm_vapp
            .Lkjm_valdouble:
                movq %rax, %rdi
                movq %rdi, %xmm0
                call kof_json_encode_double
                jmp .Lkjm_vapp
            .Lkjm_valbox:
                testq %rax, %rax
                jz .Lkjm_valnull
                movq 8(%rax), %rdx          # tag interno da caixa
                cmpq $2, %rdx
                je .Lkjm_valblong           # long: numero cru (nunca quote)
                movq %rax, %rdi
                call kof_box_to_string      # resto da familia: decimal cru
                jmp .Lkjm_vapp              #   via builder (int/bool/double/float)
            .Lkjm_valblong:
                movq 16(%rax), %rdi
                call kof_json_encode_long
                jmp .Lkjm_vapp
            .Lkjm_valnull:
                leaq .Lkjm_nullstr(%rip), %rax
                jmp .Lkjm_vapp
            .Lkjm_nullstr:
                .int 1
                .int 0
                .int 0
                .int 0
                .int 4
                .int 0
                .ascii "null"
            .Lkjm_valstr:
                movq %rax, %rdi
                call kof_json_encode_string
                jmp .Lkjm_vapp
            .Lkjm_valbool:
                movq %rax, %rdi
                call kof_json_encode_bool
            .Lkjm_vapp:
                movq %rbp, %rdi
                movq %rax, %rsi
                call kof_json_builder_str
                incl %r13d
                jmp .Lkjm_loop
            .Lkjm_done:
                movq %rbp, %rdi
                movl $125, %esi             # '}'
                call kof_json_builder_char
                movq %rbp, %rdi
                call kof_json_builder_result
                addq $24, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbp
                popq %rbx
                ret

            """);
    }

}