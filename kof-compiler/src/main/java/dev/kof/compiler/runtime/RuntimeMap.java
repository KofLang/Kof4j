package dev.kof.compiler.runtime;
import dev.kof.compiler.NativeRuntime;

/**
 * Emissão do ASM de map do runtime nativo.
 * Domínio isolado do NativeRuntime -- refactor preserva semântica.
 */
public final class RuntimeMap {

    private RuntimeMap() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            .globl kof_map_new
            .type kof_map_new, @function
            kof_map_new:
                pushq %rbx
                movq $64, %rdi
                call kof_alloc
                movq %rax, %rbx
                movl $100, 0(%rbx)
                movl $0, 4(%rbx)
                movq $0, 8(%rbx)
                movl $0, 16(%rbx)
                movl $16, 20(%rbx)
                movq $128, %rdi
                call kof_alloc
                movq %rax, 24(%rbx)
                movq $128, %rdi
                call kof_alloc
                movq %rax, 32(%rbx)
                movl $1, 40(%rbx)           # §123: key tag — 1=String default
                movq %rbx, %rax
                popq %rbx
                ret

            # kof_map_put(rdi=map, rsi=key, rdx=val) -> valor anterior | 0
            .globl kof_map_put
            .type kof_map_put, @function
            kof_map_put:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx
                movq %rsi, %r12
                movq %rdx, %r13
                movq %rbx, %rdi
                movq %r12, %rsi
                call kof_map_find
                cmpq $-1, %rax
                je .Lkmp_insert
                movslq %eax, %rcx
                movq 32(%rbx), %rdx
                movq (%rdx,%rcx,8), %r14    # anterior
                movq %r13, (%rdx,%rcx,8)
                movq %r14, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lkmp_insert:
                movl 16(%rbx), %eax
                cmpl 20(%rbx), %eax
                jl .Lkmp_space
                # #933 (as DUAS raízes): (1) o campo cap (off 20) é contagem de
                # SLOTS — os usos (o limiar count>=cap e o índice do insert)
                # leem slots; gravar o byte-count aqui fazia os inserts
                # seguintes escreverem ALEM do array de chaves. (2) os DOIS
                # arrays novos precisam de newCap*8 bytes (o original alocava
                # o vals com oldCap*8 = METADE — todo insert além da cap
                # antiga escrevia OOB no heap: corrupção silenciosa que o
                # coletor conservador tropeça (SIGSEGV no mark, ~4k chaves).
                # A cópia fica em oldCap*8 EXATA (sem overread do bloco antigo).
                movl 20(%rbx), %ecx
                shll $3, %ecx               # oldCap*8 (cópia exata)
                movl %ecx, %r14d
                movl 20(%rbx), %edx
                addl %edx, %edx             # newCap (slots)
                movl %edx, 20(%rbx)         # cap *= 2 (SLOTS — gêmeo riscv)
                shll $3, %edx               # newCap*8 (alloc)
                movslq %edx, %rcx
                movq 24(%rbx), %rdi         # keys antigo
                movq %rcx, %rsi             # alloc newCap*8
                movq %r14, %rdx             # cópia oldCap*8
                call kof_copy_alloc
                movq %rax, 24(%rbx)
                movq 32(%rbx), %rdi         # vals antigo
                # o helper/kof_alloc consomem caller-saved (edx/rcx): re-ler o
                # NEW cap do CAMPO do mapa (gravado, sobrevive às chamadas) —
                # o mesmo recurso do gêmeo riscv
                movl 20(%rbx), %edx
                shll $3, %edx               # newCap*8
                movslq %edx, %rcx
                movq %rcx, %rsi             # alloc newCap*8 (gêmeo do keys)
                movq %r14, %rdx             # cópia oldCap*8
                call kof_copy_alloc
                movq %rax, 32(%rbx)
            .Lkmp_space:
                movl 16(%rbx), %eax
                movslq %eax, %rcx
                movq 24(%rbx), %rdx
                movq %r12, (%rdx,%rcx,8)
                movq 32(%rbx), %rdx
                movq %r13, (%rdx,%rcx,8)
                addl $1, 16(%rbx)
                xorl %eax, %eax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # auxiliar: kof_copy_alloc(rdi=src, rsi=alloc_bytes, rdx=copy_bytes)
            # -> novo bloco com os primeiros copy_bytes de src (#933: o alloc
            # e a cópia separados — o grow aloca newCap*8 e copia oldCap*8)
            kof_copy_alloc:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx             # src
                movq %rsi, %r12             # alloc bytes
                movq %rdx, %r13             # copy bytes
                movq %r12, %rdi
                call kof_alloc
                movq %rax, %r14             # novo bloco
                xorl %ecx, %ecx
            .Lkca_loop:
                cmpq %r13, %rcx
                jge .Lkca_done
                movzbl (%rbx,%rcx), %eax
                movb %al, (%r14,%rcx)
                incq %rcx
                jmp .Lkca_loop
            .Lkca_done:
                movq %r14, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_map_get(rdi=map, rsi=key) -> val | 0
            .globl kof_map_get
            .type kof_map_get, @function
            kof_map_get:
                pushq %rbx
                movq %rdi, %rbx
                call kof_map_find
                cmpq $-1, %rax
                je .LKMG_miss
                movq 32(%rbx), %rdx
                movslq %eax, %rcx
                movq (%rdx,%rcx,8), %rax
                popq %rbx
                ret
            .LKMG_miss:
                xorl %eax, %eax
                popq %rbx
                ret

            # kof_map_get_or_default(rdi=map, rsi=key, rdx=def) -> val | def
            .globl kof_map_get_or_default
            .type kof_map_get_or_default, @function
            kof_map_get_or_default:
                pushq %rbx
                pushq %r12
                movq %rdi, %rbx
                movq %rdx, %r12             # def
                call kof_map_find
                cmpq $-1, %rax
                je .LKMGD_miss
                movq 32(%rbx), %rdx
                movslq %eax, %rcx
                movq (%rdx,%rcx,8), %rax
                popq %r12
                popq %rbx
                ret
            .LKMGD_miss:
                movq %r12, %rax
                popq %r12
                popq %rbx
                ret

            # kof_map_remove(rdi=map, rsi=key) -> val removido | 0
            .globl kof_map_remove
            .type kof_map_remove, @function
            kof_map_remove:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rdi, %rbx             # map
                movq %rsi, %r12             # key
                call kof_map_find
                cmpq $-1, %rax
                je .LKMR_miss
                movslq %eax, %r13           # idx
                movq 24(%rbx), %r14         # keys
                movq 32(%rbx), %r15         # vals
                movq (%r15,%r13,8), %r12    # valor removido
                movl 16(%rbx), %ecx
                decl %ecx                   # count-1
            .LKMR_shift:
                cmpl %ecx, %r13d
                jge .LKMR_last
                movq 8(%r14,%r13,8), %rax
                movq %rax, (%r14,%r13,8)
                movq 8(%r15,%r13,8), %rax
                movq %rax, (%r15,%r13,8)
                incq %r13
                jmp .LKMR_shift
            .LKMR_last:
                decl 16(%rbx)
                movq %r12, %rax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret
            .LKMR_miss:
                xorl %eax, %eax
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # kof_map_contains(rdi=map, rsi=key) -> 1/0
            .globl kof_map_contains
            .type kof_map_contains, @function
            kof_map_contains:
                call kof_map_find
                cmpq $-1, %rax
                setne %al
                movzbl %al, %eax
                ret

            .globl kof_map_size
            .type kof_map_size, @function
            kof_map_size:
                movl 16(%rdi), %eax
                ret

            .globl kof_map_is_empty
            .type kof_map_is_empty, @function
            kof_map_is_empty:
                cmpl $0, 16(%rdi)
                sete %al
                movzbl %al, %eax
                ret

            # D-MEMORY-CLEAR (O-03): anula keys e vals ANTES de encolher — o
            # map nao retem mais nenhuma referencia apos clear()
            .globl kof_map_clear
            .type kof_map_clear, @function
            kof_map_clear:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movl 16(%rbx), %r12d
                movq 24(%rbx), %r13
                movq 32(%rbx), %rdx
                xorl %eax, %eax
            .Lkof_map_clear_loop:
                cmpl %r12d, %eax
                jge .Lkof_map_clear_done
                movslq %eax, %rcx
                movq $0, (%r13,%rcx,8)
                movq $0, (%rdx,%rcx,8)
                incl %eax
                jmp .Lkof_map_clear_loop
            .Lkof_map_clear_done:
                movl $0, 16(%rbx)
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_map_keys
            .type kof_map_keys, @function
            kof_map_keys:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx             # map
                movq 24(%rbx), %r14         # array de chaves
                call kof_list_new
                movq %rax, %r12             # lista resultado
                xorq %r13, %r13
            .LKMK_loop:
                cmpl 16(%rbx), %r13d
                jge .LKMK_done
                movq (%r14,%r13,8), %rsi
                movq %r12, %rdi
                call kof_list_add
                incq %r13
                jmp .LKMK_loop
            .LKMK_done:
                movq %r12, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_map_values
            .type kof_map_values, @function
            kof_map_values:
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                movq %rdi, %rbx             # map
                movq 32(%rbx), %r14         # array de valores
                call kof_list_new
                movq %rax, %r12
                xorq %r13, %r13
            .LKMV_loop:
                cmpl 16(%rbx), %r13d
                jge .LKMV_done
                movq (%r14,%r13,8), %rsi
                # §284-map (18/09): o slot de valor pode ser a CAIXA (contrato
                # Int/Long do Map). A visao List<V> nativa e de qwords CRUS
                # (get/consumidores nao desempacotam), entao a caixa e aberta
                # AQUI — espelha o ArrayList<Integer>+intValue do JVM
                # (JvmOpCollections.emitUnboxIfPrimitive). Nao-box (String,
                # Double/Bool crus, null) passa cru; so MAGIC alcanca o valor.
                testq %rsi, %rsi
                jz .LKMV_add
                movabsq $@@MAGIC@@, %rax
                cmpq %rax, (%rsi)
                jne .LKMV_add
                movq 16(%rsi), %rsi
            .LKMV_add:
                movq %r12, %rdi
                call kof_list_add
                incq %r13
                jmp .LKMV_loop
            .LKMV_done:
                movq %r12, %rax
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                ret

            # ── kof.collections: Set<T> nativo (P1) ─────────────────────
            # Set = List com checagem de contido no add (busca linear)
            # kof_set_new -> usa kof_list_new
            """.replace("@@MAGIC@@", RuntimeErasureBox.MAGIC));
    }
}