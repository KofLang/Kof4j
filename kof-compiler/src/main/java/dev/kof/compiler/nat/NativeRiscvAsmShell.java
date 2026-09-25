package dev.kof.compiler.nat;

/**
 * D-FULL-PARITY-050 row 2 (shell.*) — CROSS riscv64/aarch64 runtime piece.
 * {@code kof_shell_argv(program@a0, args@a1|null) -> List [program, args...]}
 * (the JVM oracle is a prepend, {@code JvmRuntimeCore.kof_shell_argv} — no
 * splitting). Pure list surgery ({@code kof_list_new/add/get}), zero libc.
 * {@code shell.run} reuses {@code kof_process_run} (row 1 slice C).
 *
 * <p>Slice B1 (26/09): {@code kof_shell_runwith(argv@a0, cwd@a1, env@a2)} — the
 * argv-first spawn. The inherited case (cwd {@code ""}/null, env null/empty)
 * delegates to {@code kof_process_run(argv[0], argv[1..])} and is byte-parity
 * with the JVM. A non-empty cwd or env is NOT silently ignored (R6): it returns
 * an honest {@code Result} failure ({@code stdout=""}, {@code stderr=message},
 * {@code exitCode=-1}) exactly like the JVM's spawn-failure convention —
 * additive-env/cwd-exec on the freestanding process layer is slice B2.
 * {@code pipeline} stays {@code PROC001} (the lowerer gates it).
 */
public final class NativeRiscvAsmShell {

    private NativeRiscvAsmShell() {
    }

    public static String RISCV_RUNTIME_ASM_SHELL = """
            # ── kof.shell (D-FULL-PARITY-050 row 2) — cross riscv64/aarch64 ──
            .section .rodata
            .Lkof_shell_empty: .asciz ""
            .Lkof_shell_msg_empty: .asciz "kof_shell_runwith: empty argv"
            .Lkof_shell_msg_cwd:   .asciz "kof_shell_runwith: cwd unsupported on riscv64/aarch64"
            .Lkof_shell_msg_env:   .asciz "kof_shell_runwith: env unsupported on riscv64/aarch64"
            .section .text
            .globl kof_shell_argv
            .type kof_shell_argv, @function
            kof_shell_argv:
                # a0 = program (KofString), a1 = args (KofList|null)
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                mv   s0, a0                # program
                mv   s1, a1                # args (pode ser null)
                call kof_list_new
                mv   s2, a0                # argv
                mv   a0, s2
                mv   a1, s0
                call kof_list_add          # argv[0] = program
                li   s3, 0                 # i = 0
            .Lkof_shargv_loop:
                beqz s1, .Lkof_shargv_done
                lw   t0, 16(s1)            # args.size
                bge  s3, t0, .Lkof_shargv_done
                mv   a0, s1
                mv   a1, s3
                call kof_list_get          # a0 = item
                mv   a1, a0
                mv   a0, s2
                call kof_list_add
                addi s3, s3, 1
                j    .Lkof_shargv_loop
            .Lkof_shargv_done:
                mv   a0, s2
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                ld   s3, 8(sp)
                addi sp, sp, 48
                ret

            # .Lkof_shell_fail(a0=msg ptr, a1=len) -> Result{stdout="", stderr=msg, exitCode=-1}
            .Lkof_shell_fail:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                mv   s0, a0                # msg ptr
                mv   s1, a1                # msg len
                mv   a0, s0
                mv   a1, s1
                call kof_string_from_literal
                mv   s2, a0                # stderr string
                la   a0, .Lkof_shell_empty
                li   a1, 0
                call kof_string_from_literal
                mv   s3, a0                # stdout (empty)
                li   a0, 32
                call kof_alloc
                sd   s3, 0(a0)
                sd   s2, 8(a0)
                li   t0, -1
                sw   t0, 16(a0)
                sd   zero, 24(a0)
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                ld   s3, 8(sp)
                addi sp, sp, 48
                ret

            # kof_shell_runwith(a0=argv List, a1=cwd String|null, a2=env Map|null) -> Result
            .globl kof_shell_runwith
            .type kof_shell_runwith, @function
            kof_shell_runwith:
                addi sp, sp, -64
                sd   ra, 56(sp)
                sd   s0, 48(sp)
                sd   s1, 40(sp)
                sd   s2, 32(sp)
                sd   s3, 24(sp)
                sd   s4, 16(sp)
                sd   s5, 8(sp)
                sd   s6, 0(sp)
                mv   s0, a0                # argv
                mv   s1, a1                # cwd
                mv   s2, a2                # env
                beqz s0, .Lkof_shell_rw_empty
                lw   t0, 16(s0)            # argv.size
                beqz t0, .Lkof_shell_rw_empty
                beqz s1, .Lkof_shell_rw_env
                lw   t0, 16(s1)            # cwd.byteLen
                bnez t0, .Lkof_shell_rw_cwd
            .Lkof_shell_rw_env:
                beqz s2, .Lkof_shell_rw_go
                mv   a0, s2
                call kof_map_size
                bnez a0, .Lkof_shell_rw_env_gap
            .Lkof_shell_rw_go:
                mv   a0, s0
                li   a1, 0
                call kof_list_get
                mv   s3, a0                # program = argv[0]
                call kof_list_new
                mv   s4, a0                # newargs
                li   s5, 1                 # i = 1
                lw   s6, 16(s0)            # size
            .Lkof_shell_rw_loop:
                bge  s5, s6, .Lkof_shell_rw_done
                mv   a0, s0
                mv   a1, s5
                call kof_list_get
                mv   a1, a0
                mv   a0, s4
                call kof_list_add
                addi s5, s5, 1
                j    .Lkof_shell_rw_loop
            .Lkof_shell_rw_done:
                mv   a0, s3
                mv   a1, s4
                call kof_process_run
                j    .Lkof_shell_rw_ret
            .Lkof_shell_rw_empty:
                la   a0, .Lkof_shell_msg_empty
                li   a1, 29
                j    .Lkof_shell_rw_fail
            .Lkof_shell_rw_cwd:
                la   a0, .Lkof_shell_msg_cwd
                li   a1, 53
                j    .Lkof_shell_rw_fail
            .Lkof_shell_rw_env_gap:
                la   a0, .Lkof_shell_msg_env
                li   a1, 53
            .Lkof_shell_rw_fail:
                call .Lkof_shell_fail
            .Lkof_shell_rw_ret:
                ld   ra, 56(sp)
                ld   s0, 48(sp)
                ld   s1, 40(sp)
                ld   s2, 32(sp)
                ld   s3, 24(sp)
                ld   s4, 16(sp)
                ld   s5, 8(sp)
                ld   s6, 0(sp)
                addi sp, sp, 64
                ret
            """;
}
