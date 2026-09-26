package dev.kof.compiler.nat;

// D-FULL-PARITY-050 linha 1, fatia E (lane native-cross, 26/09):
// content printer do Result de process.run no cross riscv64/aarch64.
// Aarch64 herda pelo tradutor. O Result e opaco (stdout@0, stderr@8,
// exitCode@16) e o contrato e o MESMO do x86-64/JVM (JVM §367), incluindo
// a remocao dos CR/LF finais de cada stream.
public final class NativeRiscvAsmProcessResult {

    private NativeRiscvAsmProcessResult() {}

    static String RISCV_RUNTIME_ASM_PROCESS_RESULT = """
            .section .rodata
            .Lkof_rprts_prefix:
                .ascii "ProcessResult[exitCode="
            .Lkof_rprts_stdout:
                .ascii ", stdout="
            .Lkof_rprts_stderr:
                .ascii ", stderr="
            .Lkof_rprts_suffix:
                .ascii "]"
            .Lkof_rprts_empty:
                .ascii ""
            .section .text

            # .Lkof_rprts_trim(a0=KofString*) -> a0=copia sem CR/LF final.
            # Newline e ASCII: a varredura byte-a-byte da cauda preserva o
            # restante UTF-8 e da paridade com ProcessResult.toString no JVM.
            .Lkof_rprts_trim:
                addi sp, sp, -32
                sd ra, 24(sp)
                sd s0, 16(sp)
                sd s1, 8(sp)
                sd s2, 0(sp)
                mv s0, a0
                beqz s0, .Lkof_rprts_trim_empty
                lw s1, 16(s0)
                addi s2, s0, 24
                add s2, s2, s1
            .Lkof_rprts_trim_scan:
                beqz s1, .Lkof_rprts_trim_copy
                addi t0, s2, -1
                lbu t1, 0(t0)
                li t2, 10
                beq t1, t2, .Lkof_rprts_trim_drop
                li t2, 13
                beq t1, t2, .Lkof_rprts_trim_drop
                j .Lkof_rprts_trim_copy
            .Lkof_rprts_trim_drop:
                addi s2, s2, -1
                addi s1, s1, -1
                j .Lkof_rprts_trim_scan
            .Lkof_rprts_trim_copy:
                addi a0, s0, 24
                mv   a1, s1
                call kof_string_from_literal
                ld ra, 24(sp)
                ld s0, 16(sp)
                ld s1, 8(sp)
                ld s2, 0(sp)
                addi sp, sp, 32
                ret
            .Lkof_rprts_trim_empty:
                la a0, .Lkof_rprts_empty
                li a1, 0
                call kof_string_from_literal
                ld ra, 24(sp)
                ld s0, 16(sp)
                ld s1, 8(sp)
                ld s2, 0(sp)
                addi sp, sp, 32
                ret

            # kof_process_result_to_string(a0=Result*) -> a0=KofString*
            .globl kof_process_result_to_string
            .type kof_process_result_to_string, @function
            kof_process_result_to_string:
                addi sp, sp, -48
                sd ra, 40(sp)
                sd s0, 32(sp)
                sd s1, 24(sp)
                sd s2, 16(sp)
                sd s3, 8(sp)
                sd s4, 0(sp)
                mv s0, a0
                ld a0, 0(s0)
                call .Lkof_rprts_trim
                mv s1, a0
                ld a0, 8(s0)
                call .Lkof_rprts_trim
                mv s2, a0
                lw a0, 16(s0)
                call kof_int_to_string
                mv s3, a0
                la a0, .Lkof_rprts_prefix
                li a1, 23
                call kof_string_from_literal
                mv s4, a0
                mv a0, s4
                mv a1, s3
                call kof_string_concat
                mv s4, a0
                la a0, .Lkof_rprts_stdout
                li a1, 9
                call kof_string_from_literal
                mv a1, a0
                mv a0, s4
                call kof_string_concat
                mv s4, a0
                mv a0, s4
                mv a1, s1
                call kof_string_concat
                mv s4, a0
                la a0, .Lkof_rprts_stderr
                li a1, 9
                call kof_string_from_literal
                mv a1, a0
                mv a0, s4
                call kof_string_concat
                mv s4, a0
                mv a0, s4
                mv a1, s2
                call kof_string_concat
                mv s4, a0
                la a0, .Lkof_rprts_suffix
                li a1, 1
                call kof_string_from_literal
                mv a1, a0
                mv a0, s4
                call kof_string_concat
                ld ra, 40(sp)
                ld s0, 32(sp)
                ld s1, 24(sp)
                ld s2, 16(sp)
                ld s3, 8(sp)
                ld s4, 0(sp)
                addi sp, sp, 48
                ret
            """;
}
