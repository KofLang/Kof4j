package dev.kof.compiler.nat;

/**
 * §553 / D-EQ-UNBOUNDED-T (mantenedora 02/10, regra 6): peça própria do helper
 * {@code kof_eq_generic} no cross (riscv64; aarch64 herda via tradutor).
 *
 * <p>Peça separada pela poda por peça do modelo riscv (regra ≤500): só quem
 * compara um type variable não-limitado a puxa, junto de {@code kof_obj_equals}
 * (peça B0) e {@code kof_box_equals} (peça B49).
 */
public final class NativeRiscvAsmGenericEq {

    private NativeRiscvAsmGenericEq() {}

    static String RISCV_RUNTIME_ASM_GENERIC_EQ = """
            # §553 / D-EQ-UNBOUNDED-T: `==` sobre type variable SEM bound.
            # a0=a, a1=b -> a0 1/0. Caixa de erasure (MAGIC §284) ->
            # kof_box_equals; String/record/classe -> kof_obj_equals
            # (null-safe + String + equals virtual).
            .globl kof_eq_generic
            kof_eq_generic:
                beqz a0, .Lkeg_null
                beqz a1, .Lkeg_no
                la   t0, .Lkeg_magic
                ld   t0, 0(t0)
                ld   t1, 0(a0)
                beq  t0, t1, .Lkeg_box
                j    kof_obj_equals
            .Lkeg_null:
                bnez a1, .Lkeg_no
                j    .Lkeg_yes
            .Lkeg_box:
                j    kof_box_equals
            .Lkeg_yes:
                li   a0, 1
                ret
            .Lkeg_no:
                li   a0, 0
                ret
            .align 3
            .Lkeg_magic: .8byte 0x4B4F46425F425801
            """;
}
