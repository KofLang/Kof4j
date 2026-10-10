package dev.kof.compiler.runtime;

/**
 * §553 / D-EQ-UNBOUNDED-T (mantenedora 02/10, regra 6): o helper de runtime do
 * {@code ==} sobre um type variable SEM bound (apaga p/ {@code Object}).
 *
 * <p>Fica numa FATIA própria (regra ≤500 + poda S-3): um {@code println("hello")}
 * não a carrega, e só o fecho de quem realmente compara um {@code T} não-limitado
 * a puxa junto com {@code kof_obj_equals}/{@code kof_box_equals}.
 *
 * <p>Contrato: {@code rdi=a, rsi=b -> eax 1/0}. Caixa de erasure (MAGIC §284)
 * → {@code kof_box_equals} (caixa=valor, null-safe); String (type_id==1) e
 * record/classe → {@code kof_obj_equals} (que já faz null-safe + String +
 * equals virtual). Um type var não-limitado apaga p/ {@code Object}, então os
 * dois lados chegam como referência/box.
 */
public final class RuntimeGenericEq {

    private RuntimeGenericEq() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            .globl kof_eq_generic
            .type kof_eq_generic, @function
            kof_eq_generic:
                testq %rdi, %rdi
                jz .Lkeg_null
                testq %rsi, %rsi
                jz .Lkeg_no
                movabsq $@@MAGIC@@, %rax
                cmpq %rax, (%rdi)
                je .Lkeg_box
                jmp kof_obj_equals
            .Lkeg_null:
                testq %rsi, %rsi
                jz .Lkeg_yes
                jmp .Lkeg_no
            .Lkeg_box:
                jmp kof_box_equals
            .Lkeg_yes:
                movl $1, %eax
                ret
            .Lkeg_no:
                xorl %eax, %eax
                ret
            """.replace("@@MAGIC@@", RuntimeErasureBox.MAGIC));
    }
}
