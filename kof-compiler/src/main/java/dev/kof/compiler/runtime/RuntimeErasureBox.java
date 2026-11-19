package dev.kof.compiler.runtime;

/**
 * §284 — box de erasure nativo (x86_64): `kof_box_*`/`kof_unbox` reais,
 * substituindo o no-op silencioso que deixava o bit primitivo cru onde o
 * consumer esperava ponteiro (SIGSEGV 139 em `var o: Object = 99`).
 *
 * Layout do box (24B): [0]=MAGIC ímpar (nunca ponteiro válido alinhado),
 * [8]=tag da coleção (mesma numeração de `collectionTag`: 0=Int/Char, 2=Long,
 * 3=Bool, 4=Double, 5=Float), [16]=valor (64 bits; Float zero-extendado pelo
 * `movl`/`movd` do lowering). `kof_println` testa o MAGIC: box → imprime pelo
 * tag (golden JVM); ponteiro de objeto real → caminho antigo byte-exato
 * (zero regressão). Invariante: `kof_unbox` só recebe box válido (os emissores
 * pareiam box/unbox por tipo no mesmo KofCall — violação = bug de codegen).
 */
public final class RuntimeErasureBox {

    /** MAGIC ímpar: nenhum endereço 8-alinhado do heap/.rodata pode casar. */
    public static final String MAGIC = "0x4B4F46425F425801";

    /** §284: mensagem de diagnostico do unbox (String Kof estatico, layout
     *  [0]=typeId [16]=len [24]=bytes — `kof_throw_string` espera String do
     *  runtime, nao asciz crua: o catch(String e) faz "+e" sobre ela). */
    public static final String UNBOX_MSG =
            "type error: class java.lang.Integer cannot be cast to the boxed value (Kof native)";

    private RuntimeErasureBox() {}

    public static void emitBox(StringBuilder sb) {
        sb.append("""
            # §284: box de erasure — alocar 24B [magic][tag][value]
            .text
            .globl kof_box_int
            .type kof_box_int, @function
            kof_box_int:
                pushq %rbx
                movq %rdi, %rbx
                movl $24, %edi
                call kof_alloc
                movabsq $@@MAGIC@@, %rcx
                movq %rcx, (%rax)
                movl $0, 8(%rax)
                movq %rbx, 16(%rax)
                popq %rbx
                ret
            .globl kof_box_long
            .type kof_box_long, @function
            kof_box_long:
                pushq %rbx
                movq %rdi, %rbx
                movl $24, %edi
                call kof_alloc
                movabsq $@@MAGIC@@, %rcx
                movq %rcx, (%rax)
                movl $2, 8(%rax)
                movq %rbx, 16(%rax)
                popq %rbx
                ret
            .globl kof_box_bool
            .type kof_box_bool, @function
            kof_box_bool:
                pushq %rbx
                movq %rdi, %rbx
                movl $24, %edi
                call kof_alloc
                movabsq $@@MAGIC@@, %rcx
                movq %rcx, (%rax)
                movl $3, 8(%rax)
                movq %rbx, 16(%rax)
                popq %rbx
                ret
            .globl kof_box_double
            .type kof_box_double, @function
            kof_box_double:
                pushq %rbx
                movq %rdi, %rbx
                movl $24, %edi
                call kof_alloc
                movabsq $@@MAGIC@@, %rcx
                movq %rcx, (%rax)
                movl $4, 8(%rax)
                movq %rbx, 16(%rax)
                popq %rbx
                ret
            .globl kof_box_float
            .type kof_box_float, @function
            # valor: bits IEEE-754 (32) — metade alta 0 por construção do
            # lowering (`movd`/`movl` zero-extend)
            kof_box_float:
                pushq %rbx
                movl %edi, %ebx
                movl $24, %edi
                call kof_alloc
                movabsq $@@MAGIC@@, %rcx
                movq %rcx, (%rax)
                movl $5, 8(%rax)
                movq %rbx, 16(%rax)
                popq %rbx
                ret
            .globl kof_unbox_int
            .type kof_unbox_int, @function
            # le um box de inteiro (tag 0 = Int/Char/Short/Byte — mesma largura
            # 64-bit dos slots int nativos). Regra JVM (medida 18/09): `o as Int`
            # sobre Long/Double/Boolean/String = ClassCastException — NUNCA
            # valor inventado nem passthrough cru. Qualquer outra forma (tag
            # != 0, sem MAGIC = referencia real) e diagnostico honesto (R6).
            kof_unbox_int:
                testq %rdi, %rdi                  # null -> diagnostico CCE
                jz .Lkui_bad                      # (JVM: NPE/CCE; nunca *(0))
                movabsq $@@MAGIC@@, %rax
                cmpq %rax, (%rdi)
                jne .Lkui_bad
                movq %rdi, %rcx
                movq 8(%rdi), %rax
                testq %rax, %rax
                jnz .Lkui_bad
                movq 16(%rcx), %rax
                ret
            .Lkui_bad:
                leaq .Lkui_msgint(%rip), %rdi
                call kof_throw_string
            # §284-map (18/09): kof_unbox_long — paridade Number.longValue():
            # aceita caixa Int (tag 0, o valor e um qword com signo) e caixa
            # Long (tag 2); qualquer outra forma e o mesmo diagnostico (R6).
            .globl kof_unbox_long
            .type kof_unbox_long, @function
            kof_unbox_long:
                movabsq $@@MAGIC@@, %rax
                cmpq %rax, (%rdi)
                jne .Lkul_bad
                movq %rdi, %rcx
                movq 8(%rdi), %rax
                cmpq $2, %rax
                je .Lkul_ok
                testq %rax, %rax
                jnz .Lkul_bad
            .Lkul_ok:
                movq 16(%rcx), %rax
                ret
            .Lkul_bad:
                leaq .Lkui_msgint(%rip), %rdi
                call kof_throw_string
            # §284-map (18/09): unbox SOFT para consumidores de `Int?`
            # (arithmetic/relacional/println/pos-call do get). No native o
            # valor pode chegar de 3 formas: caixa do slot (ler +16), CRU
            # de variavel local ja-desembalada (JSR-45-esque — passar cru)
            # ou null de get ausente (diagnostico igual ao estrito). O
            # estrito NAO passa cru por contrato (`as Int` de String-box e
            # CCE); quem precisa da flexibilidade e o consumidor nullable.
            .globl kof_unbox_int_soft
            .type kof_unbox_int_soft, @function
            kof_unbox_int_soft:
                testq %rdi, %rdi
                jz .Lkuis_bad
                movabsq $@@MAGIC@@, %rax
                cmpq %rax, (%rdi)
                jne .Lkuis_raw
                cmpq $0, 8(%rdi)
                jne .Lkuis_bad
                movq 16(%rdi), %rax
                ret
            .Lkuis_raw:
                movq %rdi, %rax
                ret
            .Lkuis_bad:
                leaq .Lkui_msgint(%rip), %rdi
                call kof_throw_string
            .globl kof_unbox_long_soft
            .type kof_unbox_long_soft, @function
            kof_unbox_long_soft:
                testq %rdi, %rdi
                jz .Lkuls_bad
                movabsq $@@MAGIC@@, %rax
                cmpq %rax, (%rdi)
                jne .Lkuls_raw
                movq 8(%rdi), %rax
                cmpq $2, %rax
                je .Lkuls_ok
                testq %rax, %rax
                jnz .Lkuls_bad
            .Lkuls_ok:
                movq 16(%rdi), %rax
                ret
            .Lkuls_raw:
                movq %rdi, %rax
                ret
            .Lkuls_bad:
                leaq .Lkui_msgint(%rip), %rdi
                call kof_throw_string
            .globl kof_unbox_bool
            .type kof_unbox_bool, @function
            kof_unbox_bool:
                testq %rdi, %rdi
                jz .Lkub_bad
                movabsq $@@MAGIC@@, %rax
                cmpq %rax, (%rdi)
                jne .Lkub_bad
                cmpq $3, 8(%rdi)
                jne .Lkub_bad
                movq 16(%rdi), %rax
                ret
            .Lkub_bad:
                leaq .Lkui_msgint(%rip), %rdi
                call kof_throw_string
            .globl kof_unbox_bool_soft
            .type kof_unbox_bool_soft, @function
            kof_unbox_bool_soft:
                testq %rdi, %rdi
                jz .Lkubs_bad
                movabsq $@@MAGIC@@, %rax
                cmpq %rax, (%rdi)
                jne .Lkubs_raw
                cmpq $3, 8(%rdi)
                jne .Lkubs_bad
                movq 16(%rdi), %rax
                ret
            .Lkubs_raw:
                movq %rdi, %rax
                ret
            .Lkubs_bad:
                leaq .Lkui_msgint(%rip), %rdi
                call kof_throw_string
            .globl kof_unbox_double
            .type kof_unbox_double, @function
            kof_unbox_double:
                testq %rdi, %rdi
                jz .Lkud_bad
                movabsq $@@MAGIC@@, %rax
                cmpq %rax, (%rdi)
                jne .Lkud_bad
                cmpq $4, 8(%rdi)
                jne .Lkud_bad
                movq 16(%rdi), %rax
                ret
            .Lkud_bad:
                leaq .Lkui_msgint(%rip), %rdi
                call kof_throw_string
            .globl kof_unbox_double_soft
            .type kof_unbox_double_soft, @function
            kof_unbox_double_soft:
                testq %rdi, %rdi
                jz .Lkuds_bad
                movabsq $@@MAGIC@@, %rax
                cmpq %rax, (%rdi)
                jne .Lkuds_raw
                cmpq $4, 8(%rdi)
                jne .Lkuds_bad
                movq 16(%rdi), %rax
                ret
            .Lkuds_raw:
                movq %rdi, %rax
                ret
            .Lkuds_bad:
                leaq .Lkui_msgint(%rip), %rdi
                call kof_throw_string
            .globl kof_unbox_float
            .type kof_unbox_float, @function
            kof_unbox_float:
                testq %rdi, %rdi
                jz .Lkuf_bad
                movabsq $@@MAGIC@@, %rax
                cmpq %rax, (%rdi)
                jne .Lkuf_bad
                cmpq $5, 8(%rdi)
                jne .Lkuf_bad
                movq 16(%rdi), %rax
                ret
            .Lkuf_bad:
                leaq .Lkui_msgint(%rip), %rdi
                call kof_throw_string
            .globl kof_unbox_float_soft
            .type kof_unbox_float_soft, @function
            kof_unbox_float_soft:
                testq %rdi, %rdi
                jz .Lkufs_bad
                movabsq $@@MAGIC@@, %rax
                cmpq %rax, (%rdi)
                jne .Lkufs_raw
                cmpq $5, 8(%rdi)
                jne .Lkufs_bad
                movq 16(%rdi), %rax
                ret
            .Lkufs_raw:
                movq %rdi, %rax
                ret
            .Lkufs_bad:
                leaq .Lkui_msgint(%rip), %rdi
                call kof_throw_string
            # §717 (18/11): kof_unbox_any_soft(rdi=valor de um `T` apagado)
            # -> rax = a representacao FISICA do slot de colecao nativo.
            # Dentro de um corpo generico o valor de um `T` e SEMPRE uma
            # referencia (a caixa do primitivo instanciado, um objeto real
            # ou null) — nunca um primitivo cru na pilha. Abre QUALQUER
            # caixa pela tag-agnostica [16], passa a referencia crua e mapeia
            # null -> 0 (o mesmo qword que a lista concreta guarda). O leitor
            # do call site (substituido a List<Int>, List<String>, …) le o
            # elemento pela representacao fisica UNICA da lista — builder e
            # reader concordam (a divergencia do §717 era esta).
            .globl kof_unbox_any_soft
            .type kof_unbox_any_soft, @function
            kof_unbox_any_soft:
                testq %rdi, %rdi
                jz .Lkuas_null
                movabsq $@@MAGIC@@, %rax
                cmpq %rax, (%rdi)
                jne .Lkuas_raw
                movq 16(%rdi), %rax
                ret
            .Lkuas_raw:
                movq %rdi, %rax
                ret
            .Lkuas_null:
                xorl %eax, %eax
                ret
            # §284-map (18/09): kof_box_equals(rdi=L, rsi=R) -> rax 0/1.
            # Igualdade de `T?` no native com slot FISICAMENTE boxed.
            # Caixa vs caixa = VALOR; null vs null = 1; null vs presente = 0.
            .globl kof_box_equals
            .type kof_box_equals, @function
            kof_box_equals:
                testq %rdi, %rdi
                jz .Lke_lnull
                testq %rsi, %rsi
                jz .Lke_false                   # L!=null, R=null -> 0 (false)
                jmp .Lke_go
            .Lke_lnull:
                testq %rsi, %rsi
                jz .Lke_tnull                   # null == null -> 1 (true)
                xorl %eax, %eax                 # L=null, R!=null -> 0 (false)
                ret
            .Lke_tnull:
                movl $1, %eax
                ret
            .Lke_false:
                xorl %eax, %eax
                ret
            .Lke_go:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movabsq $@@MAGIC@@, %rbx
                # lado L: r12=tag (-1 = nao-caixa), r13=valor|ponteiro
                movq (%rdi), %rax
                cmpq %rbx, %rax
                jne .Lke_lraw
                movq 8(%rdi), %r12
                movq 16(%rdi), %r13
                jmp .Lke_ldone
            .Lke_lraw:
                movq $-1, %r12
                movq %rdi, %r13
            .Lke_ldone:
                movq (%rsi), %rax
                cmpq %rbx, %rax
                jne .Lke_rraw
                movq 8(%rsi), %r14
                movq 16(%rsi), %rsi
                jmp .Lke_rdone
            .Lke_rraw:
                movq $-1, %r14
            .Lke_rdone:
                xorl %eax, %eax
                cmpq %r14, %r12
                jne .Lke_end
                cmpq %rsi, %r13
                jne .Lke_end
                movl $1, %eax
            .Lke_end:
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lke_bad:
                leaq .Lkui_msgint(%rip), %rdi
                call kof_throw_string
            # §284: kof_box_to_string (rdi = box|[0]=magic -> rax = ptr string)
            # O despacho do valueOf(Object) nativo cai aqui quando o static
            # type nao tem vtable (Object/Nullable(Object)): sem isto o box
            # cru caia em println_string (SIGSEGV). Nao-box passa cru (mesmo
            # ponteiro) — o que ja era string/objeto nao muda de mao.
            .globl kof_box_to_string
            .type kof_box_to_string, @function
            kof_box_to_string:
                testq %rdi, %rdi
                jz .Lkbs_null                   # null -> "null" (paridade JVM
                movabsq $@@MAGIC@@, %rax        #  String.valueOf(null)); a
                cmpq %rax, (%rdi)               #  sonda leu *(0) sem isto
                jne .Lkbs_pass
                pushq %rbx
                movq %rdi, %rbx
                movq 8(%rbx), %rax
                cmpl $0, %eax
                je .Lkbs_int
                cmpl $2, %eax
                je .Lkbs_long              # long: kof_int_to_string trunca em 32-bit
                cmpl $3, %eax
                je .Lkbs_bool
                cmpl $4, %eax
                je .Lkbs_dbl
                cmpl $5, %eax
                je .Lkbs_flt
                jmp .Lkbs_pass_end
            .Lkbs_int:
                movq 16(%rbx), %rdi
                call kof_int_to_string
                jmp .Lkbs_end
            .Lkbs_long:
                movq 16(%rbx), %rdi
                call kof_long_to_string
                jmp .Lkbs_end
            .Lkbs_bool:
                movq 16(%rbx), %rdi
                call kof_bool_to_string
                jmp .Lkbs_end
            .Lkbs_dbl:
                movq 16(%rbx), %rdi
                movq %rdi, %xmm0
                call kof_double_to_string
                jmp .Lkbs_end
            .Lkbs_flt:
                movq 16(%rbx), %rdi
                movd %edi, %xmm0
                call kof_float_to_string
            .Lkbs_end:
                popq %rbx
                ret
            .Lkbs_pass_end:
                popq %rbx
            .Lkbs_pass:
                # N2 (23/09): REFERENCIA nao-caixa. String (type_id==1 no
                # offset 0) passa cru; qualquer outro objeto despacha o SEU
                # toString por kof_tostring_table[type_id] (tail-call: rdi ja
                # e o this, o ret cai no chamador original). Sem entrada na
                # tabela -> passthrough (comportamento anterior, sem
                # regressao). Discriminador = type_id, o MESMO do kof_instanceof.
                movl (%rdi), %eax
                cmpl $1, %eax
                je .Lkbs_pass_ret
                leaq kof_tostring_table(%rip), %rcx
                movq (%rcx,%rax,8), %rax
                testq %rax, %rax
                jz .Lkbs_pass_ret
                jmp *%rax
            .Lkbs_pass_ret:
                movq %rdi, %rax
                ret
            .Lkbs_null:
                leaq .Lkbs_nullstr(%rip), %rax
                ret
            .section .rodata
            .Lkbs_nullstr:
                .int 1
                .int 0
                .int 0
                .int 0
                .int 4
                .int 0
                .ascii "null"
            .Lkui_msgint:
                .int 1
                .int 0
                .int 0
                .int 0
                .int @@MSGLEN@@
                .int 0
                .ascii "@@MSG@@"
                .byte 0
            .text
            """.replace("@@MAGIC@@", MAGIC).replace("@@MSG@@", UNBOX_MSG).replace("@@MSGLEN@@", java.lang.String.valueOf(UNBOX_MSG.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)));
    }
}
