package dev.kof.compiler.nat;

/**
 * D-FULL-PARITY-050 row 3 (ssh.*) slice B — CROSS riscv64/aarch64 runtime
 * piece. {@code kof_ssh_argv(host, command)} is the exact JVM oracle
 * ({@code JvmRuntimeCore.kof_ssh_argv}):
 * {@code ["ssh","-o","BatchMode=yes","-o","ConnectTimeout=5",host,command]} —
 * host and command stay ONE argv element each. {@code kof_ssh_run} reuses
 * {@code kof_process_run} with {@code "ssh"} as the program and the six
 * remaining elements as args (the JVM lowers onto
 * {@code kof_shell_runwith → kof_process_run}), so connection failures are an
 * honest {@code Result} (R6). Mirrors the x86 {@code RuntimeSsh}.
 */
public final class NativeRiscvAsmSsh {

    private NativeRiscvAsmSsh() {
    }

    public static String RISCV_RUNTIME_ASM_SSH = """
            # ── kof.ssh (D-FULL-PARITY-050 row 3 slice B) — cross riscv64/aarch64 ──
            .section .rodata
            .Lkof_ssh_lit_ssh:       .asciz "ssh"
            .Lkof_ssh_lit_dasho:     .asciz "-o"
            .Lkof_ssh_lit_batchmode: .asciz "BatchMode=yes"
            .Lkof_ssh_lit_timeout:   .asciz "ConnectTimeout=5"
            .section .text
            # .Lkof_ssh_push(a0=list, a1=ptr, a2=len): list.add(from_literal(ptr,len))
            .Lkof_ssh_push:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                mv   s0, a0                # list
                mv   s1, a1                # ptr
                mv   a0, a1
                mv   a1, a2
                call kof_string_from_literal
                mv   a1, a0
                mv   a0, s0
                call kof_list_add
                ld   ra, 24(sp)
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                addi sp, sp, 32
                ret

            # .Lkof_ssh_build(a0=host, a1=command, a2=include_ssh) -> List
            .Lkof_ssh_build:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                mv   s0, a0                # host
                mv   s1, a1                # command
                mv   s2, a2                # include_ssh
                call kof_list_new
                mv   s3, a0                # list
                beqz s2, .Lkof_ssh_no_ssh
                mv   a0, s3
                la   a1, .Lkof_ssh_lit_ssh
                li   a2, 3
                call .Lkof_ssh_push
            .Lkof_ssh_no_ssh:
                mv   a0, s3
                la   a1, .Lkof_ssh_lit_dasho
                li   a2, 2
                call .Lkof_ssh_push
                mv   a0, s3
                la   a1, .Lkof_ssh_lit_batchmode
                li   a2, 13
                call .Lkof_ssh_push
                mv   a0, s3
                la   a1, .Lkof_ssh_lit_dasho
                li   a2, 2
                call .Lkof_ssh_push
                mv   a0, s3
                la   a1, .Lkof_ssh_lit_timeout
                li   a2, 16
                call .Lkof_ssh_push
                mv   a0, s3
                mv   a1, s0
                call kof_list_add          # host
                mv   a0, s3
                mv   a1, s1
                call kof_list_add          # command
                mv   a0, s3
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                ld   s3, 8(sp)
                addi sp, sp, 48
                ret

            .globl kof_ssh_argv
            .type kof_ssh_argv, @function
            kof_ssh_argv:
                li   a2, 1                 # include "ssh"
                j    .Lkof_ssh_build

            .globl kof_ssh_run
            .type kof_ssh_run, @function
            kof_ssh_run:
                # a0 = host, a1 = command -> Result (via kof_process_run)
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                mv   s0, a0                # host
                mv   s1, a1                # command
                mv   a0, s0
                mv   a1, s1
                li   a2, 0                 # no "ssh" prefix
                call .Lkof_ssh_build       # a0 = 6-element args
                mv   s1, a0                # args
                la   a0, .Lkof_ssh_lit_ssh
                li   a1, 3
                call kof_string_from_literal
                mv   a1, s1
                call kof_process_run
                ld   ra, 24(sp)
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                addi sp, sp, 32
                ret
            """;
}
