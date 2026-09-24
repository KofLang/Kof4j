package dev.kof.compiler.runtime;

/**
 * #386 (fatia 2) — lookups de VALOR no map nativo x86_64: containsValue
 * (scan com tag do valor: 0=cmpq raw, 1=String via kof_string_equals,
 * 2=caixa MAGIC via kof_box_equals, 3=miss garantido, 6=valor Object
 * DINÂMICO — §352 NAT002) e putIfAbsent (find → hit: devolve o slot sem
 * tocar; miss: kof_map_put já devolve 0 = null de `V?`). Reusa os
 * invariantes de §123 (tag de chave no slot 40 — escrito pelo caller, igual
 * ao put) e §284 (slot de valor boxed para Int/Long; §352 estende o box a
 * TODO primitivo quando o slot é Object).
 *
 * §352 NAT002 (fechado 21/09): no mapa de valor Object o slot pode ser
 * caixa MAGIC (Int/Long/Double/Bool/Float, todos com box no runtime),
 * String ou ponteiro de objeto; nenhum é distinguível por tag ESTÁTICO
 * (o compile não sabe o que uma expressão Object carrega). O tag 6 resolve
 * em runtime: {@code kof_value_kind} classifica o arg UMA vez e cada
 * entrada do scan; kind igual compara no caminho certo (box/box, str/str,
 * ptr/ptr), kind diferente é miss — exatamente o equals do JVM. A guarda
 * de heap do classificador (kof_heap_low/high, cumulativos do kof_alloc)
 * torna a sonda segura: bits de primitivo cru (fora do heap) viram kind 0
 * SEM dereferência — o SIGSEGV clássico do slot de Double nunca ocorre.
 */
public final class RuntimeMapLookups {

    private RuntimeMapLookups() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            .text
            # kof_value_kind(rdi=val) -> eax: 0=raw/ponteiro, 1=String, 2=caixa
            # §352 NAT002: classifica p/ o scan dinâmico do mapa de valor
            # Object. Guarda de heap ANTES de qualquer dereferência (bits de
            # Double/Int cru nunca são lidos como ponteiro).
            .globl kof_value_kind
            .type kof_value_kind, @function
            kof_value_kind:
                testq %rdi, %rdi
                jz .Lkvk_zero
                movq kof_heap_low(%rip), %rax
                cmpq %rax, %rdi
                jb .Lkvk_zero
                movq kof_heap_high(%rip), %rax
                cmpq %rax, %rdi
                jae .Lkvk_zero
                movabsq $@@MAGIC@@, %rax
                cmpq %rax, (%rdi)
                je .Lkvk_box
                cmpl $1, (%rdi)             # String: [0]=1 (from_literal)
                jne .Lkvk_zero
                movl $1, %eax
                ret
            .Lkvk_box:
                movl $2, %eax
                ret
            .Lkvk_zero:
                xorl %eax, %eax
                ret

            # kof_map_contains_value(rdi=map, rsi=val, rdx=tag) -> 0/1 (#386)
            # tag 6 (§352): valor Object — classifica arg e entradas em runtime.
            .globl kof_map_contains_value
            .type kof_map_contains_value, @function
            kof_map_contains_value:
                cmpl $3, %edx
                je .LKMCV_no
                cmpl $6, %edx
                je .LKMCV_dyn
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx             # map
                movq %rsi, %r12             # val
                movl %edx, %r13d            # tag
                xorl %r14d, %r14d           # i
            .LKMCV_loop:
                cmpl 16(%rbx), %r14d
                jge .LKMCV_nopop
                movq 32(%rbx), %rax
                movslq %r14d, %rcx
                movq (%rax,%rcx,8), %r15    # slot
                cmpl $1, %r13d
                je .LKMCV_str
                cmpl $2, %r13d
                je .LKMCV_box
                cmpl $7, %r13d
                je .LKMCV_obj
                cmpq %r12, %r15
                je .LKMCV_yes
                jmp .LKMCV_next
            .LKMCV_str:
                movq %r15, %rdi
                movq %r12, %rsi
                call kof_string_equals
                testl %eax, %eax
                jnz .LKMCV_yes
                jmp .LKMCV_next
            .LKMCV_box:
                movq %r15, %rdi
                movq %r12, %rsi
                call kof_box_equals
                testl %eax, %eax
                jnz .LKMCV_yes
                jmp .LKMCV_next
            .LKMCV_obj:
                # #604 (§104b-ii, face VALOR): mesma sonda de conteúdo que
                # get/containsKey/remove já usam pra CHAVE desde hoje.
                movq %r15, %rdi
                movq %r12, %rsi
                call kof_obj_equals
                testl %eax, %eax
                jnz .LKMCV_yes
                jmp .LKMCV_next
            .LKMCV_next:
                incl %r14d
                jmp .LKMCV_loop
            .LKMCV_yes:
                movl $1, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .LKMCV_nopop:
                xorl %eax, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .LKMCV_no:
                xorl %eax, %eax
                ret
            # tag 6: kind do arg fixo; por entrada, kind igual → compara no
            # caminho do kind (2=box_equals, 1=string_equals, 0=cmpq), kind
            # diferente → miss (equals do JVM entre famílias distintas).
            .LKMCV_dyn:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx
                movq %rsi, %r12
                movq %r12, %rdi
                call kof_value_kind
                movl %eax, %r13d            # kind do arg
                xorl %r14d, %r14d
            .LKMCV_dloop:
                cmpl 16(%rbx), %r14d
                jge .LKMCV_dnopop
                movq 32(%rbx), %rax
                movslq %r14d, %rcx
                movq (%rax,%rcx,8), %r15
                movq %r15, %rdi
                call kof_value_kind
                cmpl %r13d, %eax
                jne .LKMCV_dnext
                cmpl $2, %r13d
                je .LKMCV_dbox
                cmpl $1, %r13d
                je .LKMCV_dstr
                cmpq %r12, %r15
                je .LKMCV_dyes
                jmp .LKMCV_dnext
            .LKMCV_dbox:
                movq %r15, %rdi
                movq %r12, %rsi
                call kof_box_equals
                testl %eax, %eax
                jnz .LKMCV_dyes
                jmp .LKMCV_dnext
            .LKMCV_dstr:
                movq %r15, %rdi
                movq %r12, %rsi
                call kof_string_equals
                testl %eax, %eax
                jnz .LKMCV_dyes
            .LKMCV_dnext:
                incl %r14d
                jmp .LKMCV_dloop
            .LKMCV_dyes:
                movl $1, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .LKMCV_dnopop:
                xorl %eax, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_map_put_if_absent(rdi=map, rsi=key, rdx=val) -> anterior | 0
            # (= null de V? — o mesmo sentinela do put/get §284). A tag de
            # chave no slot 40 é escrita pelo caller (família kof_map_put).
            .globl kof_map_put_if_absent
            .type kof_map_put_if_absent, @function
            kof_map_put_if_absent:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movq %rsi, %r12
                movq %rdx, %r13
                movq %rbx, %rdi
                movq %r12, %rsi
                call kof_map_find
                cmpq $-1, %rax
                je .LKMPIA_ins
                movslq %eax, %rcx
                movq 32(%rbx), %rdx
                movq (%rdx,%rcx,8), %rax    # anterior (sem sobrescrever)
                popq %r13
                popq %r12
                popq %rbx
                ret
            .LKMPIA_ins:
                movq %rbx, %rdi
                movq %r12, %rsi
                movq %r13, %rdx
                call kof_map_put            # insere; retorno já é 0
                xorl %eax, %eax
                popq %r13
                popq %r12
                popq %rbx
                ret
            """.replace("@@MAGIC@@", RuntimeErasureBox.MAGIC));
    }
}
