package dev.kof.compiler.nat;

/**
 * D-FULL-PARITY-050 row 2 (shell.*) slice B2 — CROSS riscv64/aarch64 runtime
 * piece: {@code kof_shell_pipeline(stages@a0)} where {@code stages} is a
 * {@code List<List<KofString>>}. Mirrors the JVM oracle
 * ({@code JvmRuntimeCore.kof_shell_pipeline}): stage 0 stdin = {@code /dev/null},
 * each stage's stdout feeds the next stage's stdin, intermediate stderr is
 * discarded, and only the LAST stage's stdout+stderr are captured; the Result is
 * {@code {stdout, stderr, exitCode}} of the last stage. Empty stages / empty
 * stage / more than 16 stages are honest Result failures (never a silent break,
 * R6), exactly like the JVM messages.
 *
 * <p>The JVM uses pump threads; here the kernel does the piping — each stage is a
 * {@code clone(SIGCHLD)} child with {@code dup3} wiring and {@code execvp}, so no
 * thread runtime is needed. Self-contained (own rodata/buffers/helpers) so the
 * slice pruner closes it over {@code kof_process_run}'s primitives without
 * crossing into the process piece's locals.
 */
public final class NativeRiscvAsmPipeline {

    private NativeRiscvAsmPipeline() {
    }

    // §257: NAO `final` — um literal `static final String` vira ConstantValue
    // no bytecode e o javac INLINEIA a peca inteira nos consumidores
    // (RuntimeConstantInliningGuardTest). Campo de runtime, atribuido 1x.
    public static String RISCV_RUNTIME_ASM_PIPELINE = """
            # ── kof.shell pipeline (D-FULL-PARITY-050 row 2 slice B2) — cross ──
            .section .rodata
            .Lkof_pl_devnull:       .asciz "/dev/null"
            .Lkof_pl_msg_nostages:  .asciz "kof_shell_pipeline: no stages"
            .Lkof_pl_msg_empty:     .asciz "kof_shell_pipeline: empty stage"
            .Lkof_pl_msg_toomany:   .asciz "kof_shell_pipeline: too many stages (>16)"
            .Lkof_pl_msg_exec:      .asciz "shell: exec failed"
            .Lkof_pl_msg_sys:       .asciz "shell: syscall failed"
            .Lkof_pl_empty:         .asciz ""
            .section .bss
            .p2align 4
            _kof_pl_out_buf: .space 1048576
            _kof_pl_err_buf: .space 1048576
            _kof_pl_chunk:   .space 4096
            .section .text

            # .Lkof_pl_append(a0=trio{ptr,len,cap}, a1=src, a2=n)
            .Lkof_pl_append:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                sd   s2, 0(sp)
                mv   s0, a0
                mv   s1, a1
                mv   s2, a2
                ld   t0, 8(s0)
                add  t1, t0, s2
                ld   t2, 16(s0)
                bltu t2, t1, .Lkof_pl_append_over
                ld   a0, 0(s0)
                add  a0, a0, t0
                mv   a1, s1
                mv   a2, s2
                call kof_memcpy
                ld   t0, 8(s0)
                add  t0, t0, s2
                sd   t0, 8(s0)
            .Lkof_pl_append_over:
                ld   ra, 24(sp)
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                ld   s2, 0(sp)
                addi sp, sp, 32
                ret

            # .Lkof_pl_fail(a0=msg ptr, a1=len) -> Result{stdout="", stderr=msg, -1}
            .Lkof_pl_fail:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                mv   s0, a0
                mv   s1, a1
                mv   a0, s0
                mv   a1, s1
                call kof_string_from_literal
                mv   s2, a0
                la   a0, .Lkof_pl_empty
                li   a1, 0
                call kof_string_from_literal
                mv   s3, a0
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

            .globl kof_shell_pipeline
            .type kof_shell_pipeline, @function
            kof_shell_pipeline:
                # a0 = List<List<KofString>>
                addi sp, sp, -272
                sd   ra, 184(sp)
                sd   s0, 192(sp)
                sd   s1, 200(sp)
                sd   s2, 208(sp)
                sd   s3, 216(sp)
                sd   s4, 224(sp)
                sd   s5, 232(sp)
                sd   s6, 240(sp)
                sd   s7, 248(sp)
                sd   s8, 256(sp)
                mv   s0, a0
                beqz s0, .Lkof_pl_nostages
                lw   s1, 16(s0)                 # n = stages.size
                beqz s1, .Lkof_pl_nostages
                li   t0, 16
                bltu t0, s1, .Lkof_pl_toomany
                li   s4, 0
            .Lkof_pl_val:
                bge  s4, s1, .Lkof_pl_begin
                mv   a0, s0
                mv   a1, s4
                call kof_list_get
                beqz a0, .Lkof_pl_emptystage
                lw   t0, 16(a0)
                beqz t0, .Lkof_pl_emptystage
                addi s4, s4, 1
                j    .Lkof_pl_val
            .Lkof_pl_begin:
                li   s3, -1                     # prev_read
                li   t0, -1
                sd   t0, 64(sp)                 # out_r
                sd   t0, 72(sp)                 # err_r
                li   s2, 0                      # i
            .Lkof_pl_loop:
                bge  s2, s1, .Lkof_pl_after
                li   t0, -1
                sw   t0, 128(sp)                # new_read
                addi t1, s1, -1
                beq  s2, t1, .Lkof_pl_last
                li   a7, 59                     # pipe2(sp, 0)
                mv   a0, sp
                li   a1, 0
                ecall
                bltz a0, .Lkof_pl_sysfail
                lw   t0, 0(sp)
                sw   t0, 128(sp)                # new_read = read end
                lw   t0, 4(sp)
                sw   t0, 132(sp)                # stdout_fd = write end
                li   t0, -1
                sw   t0, 136(sp)                # stderr_fd = -1 (devnull)
                j    .Lkof_pl_fork
            .Lkof_pl_last:
                li   a7, 59                     # pipe2(sp, 0) out
                mv   a0, sp
                li   a1, 0
                ecall
                bltz a0, .Lkof_pl_sysfail
                li   a7, 59                     # pipe2(sp+8, 0) err
                addi a0, sp, 8
                li   a1, 0
                ecall
                bltz a0, .Lkof_pl_sysfail
                lw   t0, 0(sp)
                sw   t0, 64(sp)                 # out_r
                lw   t0, 8(sp)
                sw   t0, 72(sp)                 # err_r
                lw   t0, 4(sp)
                sw   t0, 132(sp)                # stdout_fd = out_w
                lw   t0, 12(sp)
                sw   t0, 136(sp)                # stderr_fd = err_w
            .Lkof_pl_fork:
                li   a7, 220                    # clone(SIGCHLD, 0)
                li   a0, 17
                li   a1, 0
                li   a2, 0
                li   a3, 0
                li   a4, 0
                ecall
                bltz a0, .Lkof_pl_sysfail
                beqz a0, .Lkof_pl_child
                slli t0, s2, 2
                add  t0, sp, t0
                sw   a0, 0(t0)                  # pids[i]
                bltz s3, .Lkof_pl_p1
                mv   a0, s3
                li   a7, 57
                ecall
            .Lkof_pl_p1:
                lw   a0, 132(sp)
                bltz a0, .Lkof_pl_p2
                li   a7, 57
                ecall
            .Lkof_pl_p2:
                lw   a0, 136(sp)
                bltz a0, .Lkof_pl_p3
                li   a7, 57
                ecall
            .Lkof_pl_p3:
                lw   s3, 128(sp)                # prev_read = new_read
                addi s2, s2, 1
                j    .Lkof_pl_loop

            .Lkof_pl_child:
                bltz s3, .Lkof_pl_c_null
                mv   a0, s3
                li   a1, 0
                li   a2, 0
                li   a7, 24                     # dup3(prev_read, 0)
                ecall
                j    .Lkof_pl_c_out
            .Lkof_pl_c_null:
                li   a7, 56                     # openat(AT_FDCWD, /dev/null, 0)
                li   a0, -100
                la   a1, .Lkof_pl_devnull
                li   a2, 0
                li   a3, 0
                ecall
                mv   t0, a0
                mv   a0, t0
                li   a1, 0
                li   a2, 0
                li   a7, 24
                ecall
                mv   a0, t0
                li   a7, 57
                ecall
            .Lkof_pl_c_out:
                lw   a0, 132(sp)
                li   a1, 1
                li   a2, 0
                li   a7, 24                     # dup3(stdout_fd, 1)
                ecall
                lw   a0, 136(sp)
                bltz a0, .Lkof_pl_c_deverr
                li   a1, 2
                li   a2, 0
                li   a7, 24                     # dup3(err_w, 2)
                ecall
                j    .Lkof_pl_c_close
            .Lkof_pl_c_deverr:
                li   a7, 56                     # intermediate stderr -> /dev/null
                li   a0, -100
                la   a1, .Lkof_pl_devnull
                li   a2, 0
                li   a3, 0
                ecall
                mv   t0, a0
                mv   a0, t0
                li   a1, 2
                li   a2, 0
                li   a7, 24
                ecall
                mv   a0, t0
                li   a7, 57
                ecall
                j    .Lkof_pl_c_close
            .Lkof_pl_c_close:
                bltz s3, .Lkof_pl_cc1
                mv   a0, s3
                li   a7, 57
                ecall
            .Lkof_pl_cc1:
                lw   a0, 128(sp)
                bltz a0, .Lkof_pl_cc2
                li   a7, 57
                ecall
            .Lkof_pl_cc2:
                lw   a0, 132(sp)
                bltz a0, .Lkof_pl_cc3
                li   a7, 57
                ecall
            .Lkof_pl_cc3:
                lw   a0, 136(sp)
                bltz a0, .Lkof_pl_cc4
                li   a7, 57
                ecall
            .Lkof_pl_cc4:
                lw   a0, 64(sp)
                bltz a0, .Lkof_pl_cc5
                li   a7, 57
                ecall
            .Lkof_pl_cc5:
                lw   a0, 72(sp)
                bltz a0, .Lkof_pl_c_argv
                li   a7, 57
                ecall
            .Lkof_pl_c_argv:
                mv   a0, s0
                mv   a1, s2
                call kof_list_get               # stage list
                mv   s4, a0
                lw   t0, 16(s4)
                addi t1, t0, 2
                slli t1, t1, 3
                addi t1, t1, 15
                andi t1, t1, -16
                sub  sp, sp, t1
                mv   s5, sp                     # argv base
                li   s6, 0
            .Lkof_pl_c_argv_loop:
                lw   t1, 16(s4)
                bge  s6, t1, .Lkof_pl_c_argv_done
                mv   a0, s4
                mv   a1, s6
                call kof_list_get
                slli t3, s6, 3
                add  t3, s5, t3
                beqz a0, .Lkof_pl_c_argv_null
                addi t2, a0, 24
                sd   t2, 0(t3)
                j    .Lkof_pl_c_argv_next
            .Lkof_pl_c_argv_null:
                sd   zero, 0(t3)
            .Lkof_pl_c_argv_next:
                addi s6, s6, 1
                j    .Lkof_pl_c_argv_loop
            .Lkof_pl_c_argv_done:
                slli t3, s6, 3
                add  t3, s5, t3
                sd   zero, 0(t3)
                ld   a0, 0(s5)
                mv   a1, s5
                call execvp
                li   a0, 2
                la   a1, .Lkof_pl_msg_exec
                li   a2, 18
                li   a7, 64
                ecall
                li   a0, 127
                li   a7, 93
                ecall

            .Lkof_pl_after:
                bltz s3, .Lkof_pl_a1
                mv   a0, s3
                li   a7, 57
                ecall
            .Lkof_pl_a1:
                la   t0, _kof_pl_out_buf
                sd   t0, 80(sp)
                sd   zero, 88(sp)
                li   t1, 1048576
                sd   t1, 96(sp)
                la   t0, _kof_pl_err_buf
                sd   t0, 104(sp)
                sd   zero, 112(sp)
                sd   t1, 120(sp)
                la   t0, _kof_pl_chunk
                sd   t0, 144(sp)
                sw   zero, 152(sp)
                sw   zero, 156(sp)
                lw   s5, 64(sp)                 # out_r
                lw   s6, 72(sp)                 # err_r
            .Lkof_pl_poll:
                sw   s5, 160(sp)
                li   t1, 1
                sh   t1, 164(sp)
                sh   zero, 166(sp)
                sw   s6, 168(sp)
                sh   t1, 172(sp)
                sh   zero, 174(sp)
                addi a0, sp, 160
                li   a1, 2
                li   a2, 0
                li   a3, 0
                li   a4, 0
                li   a7, 73                     # ppoll
                ecall
                lhu  t0, 166(sp)
                andi t0, t0, 25
                beqz t0, .Lkof_pl_poll_err
                mv   a0, s5
                ld   a1, 144(sp)
                li   a2, 4096
                li   a7, 63
                ecall
                bgtz a0, .Lkof_pl_poll_read_out
                li   t0, 1
                sw   t0, 152(sp)
                j    .Lkof_pl_poll_err
            .Lkof_pl_poll_read_out:
                mv   a2, a0
                addi a0, sp, 80
                ld   a1, 144(sp)
                call .Lkof_pl_append
                j    .Lkof_pl_poll
            .Lkof_pl_poll_err:
                lhu  t0, 174(sp)
                andi t0, t0, 25
                beqz t0, .Lkof_pl_poll_check
                mv   a0, s6
                ld   a1, 144(sp)
                li   a2, 4096
                li   a7, 63
                ecall
                bgtz a0, .Lkof_pl_poll_read_err
                li   t0, 1
                sw   t0, 156(sp)
                j    .Lkof_pl_poll_check
            .Lkof_pl_poll_read_err:
                mv   a2, a0
                addi a0, sp, 104
                ld   a1, 144(sp)
                call .Lkof_pl_append
                j    .Lkof_pl_poll
            .Lkof_pl_poll_check:
                lw   t0, 152(sp)
                beqz t0, .Lkof_pl_poll
                lw   t0, 156(sp)
                beqz t0, .Lkof_pl_poll
                mv   a0, s5
                li   a7, 57
                ecall
                mv   a0, s6
                li   a7, 57
                ecall
                addi t0, s1, -1
                slli t0, t0, 2
                add  t0, sp, t0
                lw   a0, 0(t0)                  # last pid
                addi a1, sp, 176
                li   a2, 0
                li   a3, 0
                li   a7, 260
                ecall
                lw   t0, 176(sp)
                srai s8, t0, 8
                andi s8, s8, 255
                li   s4, 0
            .Lkof_pl_reap:
                addi t0, s1, -1
                bge  s4, t0, .Lkof_pl_build
                li   a0, -1
                addi a1, sp, 176
                li   a2, 0
                li   a3, 0
                li   a7, 260
                ecall
                addi s4, s4, 1
                j    .Lkof_pl_reap
            .Lkof_pl_build:
                ld   a0, 80(sp)
                ld   a1, 88(sp)
                call kof_string_from_literal
                mv   s4, a0
                ld   a0, 104(sp)
                ld   a1, 112(sp)
                call kof_string_from_literal
                mv   s5, a0
                li   a0, 32
                call kof_alloc
                sd   s4, 0(a0)
                sd   s5, 8(a0)
                sw   s8, 16(a0)
                sd   zero, 24(a0)
                j    .Lkof_pl_ret
            .Lkof_pl_nostages:
                la   a0, .Lkof_pl_msg_nostages
                li   a1, 29
                j    .Lkof_pl_failtail
            .Lkof_pl_emptystage:
                la   a0, .Lkof_pl_msg_empty
                li   a1, 31
                j    .Lkof_pl_failtail
            .Lkof_pl_toomany:
                la   a0, .Lkof_pl_msg_toomany
                li   a1, 41
                j    .Lkof_pl_failtail
            .Lkof_pl_sysfail:
                la   a0, .Lkof_pl_msg_sys
                li   a1, 21
            .Lkof_pl_failtail:
                call .Lkof_pl_fail
            .Lkof_pl_ret:
                ld   ra, 184(sp)
                ld   s0, 192(sp)
                ld   s1, 200(sp)
                ld   s2, 208(sp)
                ld   s3, 216(sp)
                ld   s4, 224(sp)
                ld   s5, 232(sp)
                ld   s6, 240(sp)
                ld   s7, 248(sp)
                ld   s8, 256(sp)
                addi sp, sp, 272
                ret
            """;
}
