package dev.kof.compiler.runtime;

/**
 * Emissão do ASM x86-64 de {@code valueOf(List/Set/Map)} (bug §107).
 *
 * <p>Antes o dispatch {@code valueOf} não achava vtable {@code toString} em
 * List/Map/Set (tipos de RUNTIME, sem vtable), não emitia nada e o ponteiro
 * cru caía em {@code kof_println_string} = lixo de ponteiro (R6). Aqui o
 * despachante passa, <b>em tempo de compilação</b>, a TAG do elemento
 * ({@code collectionTag}): 0=int/char/short/byte, 1=String, 2=Long, 3=Bool,
 * 4=Double, 5=Float, 6=desconhecido/record/aninhado → {@code "?"} (a recusa
 * honesta, cara do §104b-ii). SEM056 garante homogeneidade: UMA tag basta.
 *
 * <p>Nenhum mutador compartilhado nem header de container é tocado (lição
 * §104b-ii): os helpers só LEEM o layout (List/Set: size@16, data@24; Map:
 * size@16, keys@24, vals@32). O acumulador e cada String temporária vivem no
 * <b>frame</b> (varredura conservadora da pilha no GC — {@code [rsp, rbp)}) e
 * nunca só em registrador, senão o {@code kof_alloc} de um concat subsequente
 * liberaria a String viva.
 */
public final class RuntimeCollectionToString {

    private RuntimeCollectionToString() {}

    public static void emit(StringBuilder sb) {
        emitRodata(sb);
        emitElemToString(sb);
        emitListToString(sb);
        emitMapToString(sb);
    }

    /**
     * Constantes usadas pelos helpers. O rótulo é definido em `.rodata` e a
     * seção volta para `.text` no mesmo bloco (idioma do `RuntimeStringBase`,
     * `.Lkof_null_str`) — assim as referências `leaq .Lc2s_*(%rip)` abaixo, já
     * dentro de `.text`, resolvem para o endereço na rodata.
     */
    static void emitRodata(StringBuilder sb) {
        sb.append("""
            .section .rodata
            .Lc2s_qstr: .ascii "?"
            .Lc2s_lbr:  .ascii "["
            .Lc2s_rbr:  .ascii "]"
            .Lc2s_lcur: .ascii "{"
            .Lc2s_rcur: .ascii "}"
            .Lc2s_comma: .ascii ", "
            .Lc2s_eq:   .ascii "="
            .section .text
            """);
    }

    /**
     * kof_elem_to_string(rdi = &slot, esi = tag) -> rax String* (NULL só se a
     * alocação falhar). Tag 0 lê os 4 bytes baixos do slot (int/char/short/
     * byte); 1 é o ponteiro String; demais primitivos seguem o converter da
     * RuntimeStringConv; 6 e qualquer tag fora = "?" (honesto).
     */
    static void emitElemToString(StringBuilder sb) {
        sb.append("""
            .globl kof_elem_to_string
            .type kof_elem_to_string, @function
            kof_elem_to_string:
                pushq %rbx
                cmpb $1, %sil
                je .Lce_str
                cmpb $2, %sil
                je .Lce_long
                cmpb $3, %sil
                je .Lce_bool
                cmpb $4, %sil
                je .Lce_double
                cmpb $5, %sil
                je .Lce_float
                cmpb $6, %sil
                je .Lce_q
            .Lce_int:
                movl (%rdi), %edi
                call kof_int_to_string
                popq %rbx
                ret
            .Lce_str:
                movq (%rdi), %rax
                popq %rbx
                ret
            .Lce_long:
                movq (%rdi), %rdi
                call kof_long_to_string
                popq %rbx
                ret
            .Lce_bool:
                movl (%rdi), %edi
                call kof_bool_to_string
                popq %rbx
                ret
            .Lce_double:
                movsd (%rdi), %xmm0
                call kof_double_to_string
                popq %rbx
                ret
            .Lce_float:
                movss (%rdi), %xmm0
                call kof_float_to_string
                popq %rbx
                ret
            .Lce_q:
                leaq .Lc2s_qstr(%rip), %rdi
                movl $1, %esi
                call kof_string_from_literal
                popq %rbx
                ret
            """);
    }

    /**
     * kof_list_to_string / kof_set_to_string (rdi = container, esi = tag do
     * elem) -> rax String* "[e1, e2, ...]". Set É um List no runtime
     * (kof_set_new = kof_list_new, mesma forma 100), então os dois são o MESMO
     * código: um galho `.globl` a mais aponta para a mesma etiqueta.
     */
    static void emitListToString(StringBuilder sb) {
        sb.append("""
            .globl kof_list_to_string
            .type kof_list_to_string, @function
            .globl kof_set_to_string
            .type kof_set_to_string, @function
            kof_list_to_string:
            kof_set_to_string:
                pushq %rbp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rsp, %rbp             # ancora o frame: locais abaixo de
                subq $64, %rsp              # rbp; os pushq de CALL caem ABAIXO
                                            # dos locais (não os pisa)
                movq %rdi, -32(%rbp)        # container (raiz p/ GC)
                movl %esi, -40(%rbp)        # tag do elem
                movl 16(%rdi), %r12d        # size
                movq 24(%rdi), %r13         # data
                xorl %ebx, %ebx             # i
                leaq .Lc2s_lbr(%rip), %rdi
                movl $1, %esi
                call kof_string_from_literal
                movq %rax, -8(%rbp)         # acc = '['
                testl %r12d, %r12d
                jz .Lcl_fin
            .Lcl_loop:
                movslq %ebx, %rax
                movq (%r13,%rax,8), %rax
                movq %rax, -24(%rbp)        # slot do elem (raiz p/ tag 1)
                testl %ebx, %ebx
                jnz .Lcl_sep2
                leaq .Lc2s_lbr(%rip), %rdi
                xorl %esi, %esi
                jmp .Lcl_sepcall
            .Lcl_sep2:
                leaq .Lc2s_comma(%rip), %rdi
                movl $2, %esi
            .Lcl_sepcall:
                call kof_string_from_literal
                movq %rax, -16(%rbp)        # sep (raiz p/ alloc do elem)
                leaq -24(%rbp), %rdi
                movl -40(%rbp), %esi
                call kof_elem_to_string
                movq %rax, %rsi
                movq -16(%rbp), %rdi
                call kof_string_concat      # sep + elem
                movq %rax, %rsi
                movq -8(%rbp), %rdi
                call kof_string_concat      # acc + (sep + elem)
                movq %rax, -8(%rbp)         # acc (raiz)
                incl %ebx
                cmpl %r12d, %ebx
                jl .Lcl_loop
            .Lcl_fin:
                leaq .Lc2s_rbr(%rip), %rdi
                movl $1, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movq -8(%rbp), %rdi
                call kof_string_concat
                movq %rbp, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                popq %rbp
                ret
            """);
    }

    /**
     * kof_map_to_string (rdi = map, esi = tag da chave, edx = tag do valor)
     * -> rax String* "{k=v, k=v}". Ordem de ARMAZENAMENTO (inserção) — o
     * runtime usa vetores lineares, não buckets de hash do HashMap do JVM
     * (divergência de arquitetura registrada no §107, não lixo). A String
     * "chave=" fica enraizada no frame enquanto o valor é convertido.
     */
    static void emitMapToString(StringBuilder sb) {
        sb.append("""
            .globl kof_map_to_string
            .type kof_map_to_string, @function
            kof_map_to_string:
                pushq %rbp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rsp, %rbp
                subq $80, %rsp
                movq %rdi, -48(%rbp)        # container (raiz)
                movl %esi, -56(%rbp)        # tag chave
                movl %edx, -64(%rbp)        # tag valor
                movl 16(%rdi), %r12d        # size
                movq 24(%rdi), %r13         # keys
                movq 32(%rdi), %r14         # vals
                xorl %ebx, %ebx             # i
                leaq .Lc2s_lcur(%rip), %rdi
                movl $1, %esi
                call kof_string_from_literal
                movq %rax, -8(%rbp)         # acc = '{'
                testl %r12d, %r12d
                jz .Lcm_fin
            .Lcm_loop:
                movslq %ebx, %rax
                movq (%r13,%rax,8), %rcx
                movq %rcx, -24(%rbp)        # slot chave (raiz)
                movq (%r14,%rax,8), %rcx
                movq %rcx, -32(%rbp)        # slot valor (raiz)
                testl %ebx, %ebx
                jnz .Lcm_sep2
                leaq .Lc2s_lcur(%rip), %rdi
                xorl %esi, %esi
                jmp .Lcm_sepcall
            .Lcm_sep2:
                leaq .Lc2s_comma(%rip), %rdi
                movl $2, %esi
            .Lcm_sepcall:
                call kof_string_from_literal
                movq %rax, -16(%rbp)        # sep (raiz)
                leaq -24(%rbp), %rdi
                movl -56(%rbp), %esi
                call kof_elem_to_string
                movq %rax, -40(%rbp)        # keyStr (raiz)
                leaq .Lc2s_eq(%rip), %rdi
                movl $1, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movq -40(%rbp), %rdi
                call kof_string_concat      # keyStr + '='
                movq %rax, -40(%rbp)        # 'chave=' (raiz p/ alloc do valor)
                leaq -32(%rbp), %rdi
                movl -64(%rbp), %esi
                call kof_elem_to_string
                movq %rax, %rsi
                movq -40(%rbp), %rdi
                call kof_string_concat      # 'chave=valor'
                movq %rax, %rsi
                movq -16(%rbp), %rdi
                call kof_string_concat      # sep + 'chave=valor'
                movq %rax, %rsi
                movq -8(%rbp), %rdi
                call kof_string_concat      # acc + sep + 'chave=valor'
                movq %rax, -8(%rbp)         # acc (raiz)
                incl %ebx
                cmpl %r12d, %ebx
                jl .Lcm_loop
            .Lcm_fin:
                leaq .Lc2s_rcur(%rip), %rdi
                movl $1, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movq -8(%rbp), %rdi
                call kof_string_concat
                movq %rbp, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                popq %rbp
                ret
            """);
    }

}
