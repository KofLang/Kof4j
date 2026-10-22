package dev.kof.compiler.nat;

/**
 * Runtime riscv64 — §678 face B (D-SEM-REJECTS-1210): o cast de referência
 * SIMPLES (`x as T`) é ESTRITO. `kof_cast_check(obj, typeId)` devolve o MESMO
 * objeto quando passa (incluindo `null` — a semântica CHECKCAST do JVM, o
 * oráculo) e derruba via `kof_panic` quando o objeto não é instância do alvo.
 *
 * <p>Fatia própria (e não dentro de {@code NativeRiscvAsmRt0}) porque aquele
 * arquivo já está acima das 600 linhas do gate `check_500`; aqui a peça nasce
 * com o dono certo e é puxada pela BFS do slicer só quando o programa emite um
 * cast estrito (`call kof_cast_check` no texto do programa é seed). Usa só
 * instruções que o runtime riscv já traduz para aarch64 (`beqz`/`sd`/`ld`/
 * `call`/`la`/`ret`).
 */
final class NativeRiscvAsmCast {

    private NativeRiscvAsmCast() {}

    static String RISCV_CAST_ASM = """
            .section .rodata
            .Lkof_castfail_msg: .asciz "type cast failed"
            .section .text
            .globl kof_cast_check
            kof_cast_check:
                beqz a0, .Lkof_cast_ok
                addi sp, sp, -16
                sd   ra, 8(sp)
                sd   a0, 0(sp)
                call kof_instanceof
                beqz a0, .Lkof_cast_fail
                ld   a0, 0(sp)
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret
            .Lkof_cast_fail:
                ld   ra, 8(sp)
                addi sp, sp, 16
                la   a0, .Lkof_castfail_msg
                li   a1, 16
                call kof_string_from_literal
                call kof_throw_string
            .Lkof_cast_ok:
                ret
            """;
}
