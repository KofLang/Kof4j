package dev.kof.compiler.nat;

// D-FULL-PARITY-050 linha 1, fatia C (lane native-cross, 26/09): kof.process.run
// no CROSS riscv64/aarch64, mesmo contrato JVM do x86 (RuntimeProcess.java).
// riscv64 NÃO tem syscall fork -> clone(flags=SIGCHLD(17), stack=0) = fork.
// Syscalls generic: pipe2=59, clone=220, execve=221 (aqui execvp da libc),
// wait4=260, read=63, write=64, close=57, ppoll=73, dup3=24, openat=56.
// Result = 32 B {stdout@0, stderr@8, exitCode@16, pad@24}; KofStr bytes@24,
// KofList size@16. Buffer de dreno 1 MiB/stream pre-alocado; estouro = panic.
public final class NativeRiscvAsmProcess {

    private NativeRiscvAsmProcess() {}

    static String RISCV_RUNTIME_ASM_PROCESS = """
            .section .rodata
            .Lkof_rproc_devnull:
                .asciz "/dev/null"
            .Lkof_rproc_overflow: .asciz "process: stream exceeds the 1 MiB native capture buffer (PROC001)"
            .Lkof_rproc_execfail: .asciz "process: exec failed"
            .Lkof_rproc_sysfail: .asciz "process: syscall failed"
            .section .text

            .globl kof_process_result_stdout
            .type kof_process_result_stdout, @function
            kof_process_result_stdout:
                ld a0, 0(a0)
                ret

            .globl kof_process_result_stderr
            .type kof_process_result_stderr, @function
            kof_process_result_stderr:
                ld a0, 8(a0)
                ret

            .globl kof_process_result_exitCode
            .type kof_process_result_exitCode, @function
            kof_process_result_exitCode:
                lw a0, 16(a0)
                ret

            # .Lkof_rproc_append(a0=trio{ptr,len,cap}, a1=src, a2=n)
            .Lkof_rproc_append:
                addi sp, sp, -32
                sd ra, 24(sp)
                sd s0, 16(sp)
                sd s1, 8(sp)
                sd s2, 0(sp)
                mv s0, a0
                mv s1, a1
                mv s2, a2
                ld t0, 8(s0)
                add t1, t0, s2
                ld t2, 16(s0)
                bltu t2, t1, .Lkof_rproc_over
                ld a0, 0(s0)
                add a0, a0, t0
                mv a1, s1
                mv a2, s2
                call kof_memcpy
                ld t0, 8(s0)
                add t0, t0, s2
                sd t0, 8(s0)
                ld ra, 24(sp)
                ld s0, 16(sp)
                ld s1, 8(sp)
                ld s2, 0(sp)
                addi sp, sp, 32
                ret
            .Lkof_rproc_over:
                la a0, .Lkof_rproc_overflow
                call kof_panic

            .globl kof_process_run
            .type kof_process_run, @function
            kof_process_run:
                # a0 = program (KofString), a1 = List<String>
                addi sp, sp, -192
                sd ra, 184(sp)
                sd s0, 176(sp)
                sd s1, 168(sp)
                sd s2, 160(sp)
                sd s3, 152(sp)
                sd s4, 144(sp)
                sd s5, 136(sp)
                mv s0, a0
                mv s1, a1
                # buffers de dreno ESTÁTICOS no .bss da fatia (1 MiB cada): não
                # consomem a arena GC de 256 KiB do cross (que continua pequena
                # para os testes de esgotamento de heap). run/spawn são síncronos
                # e spawn é recusado no cross, logo não há reentrância.
                la a0, _kof_rproc_out_buf
                sd a0, 48(sp)
                sd zero, 56(sp)
                li t0, 1048576
                sd t0, 64(sp)
                la a0, _kof_rproc_err_buf
                sd a0, 72(sp)
                sd zero, 80(sp)
                li t0, 1048576
                sd t0, 88(sp)
                la a0, _kof_rproc_chunk
                sd a0, 112(sp)
                sd zero, 32(sp)
                sd zero, 36(sp)
                sd zero, 40(sp)
                # pipe2(out@0, 0)
                li a7, 59
                addi a0, sp, 0
                li a1, 0
                ecall
                bltz a0, .Lkof_rproc_sysfail_exit
                # pipe2(err@8, 0)
                li a7, 59
                addi a0, sp, 8
                li a1, 0
                ecall
                bltz a0, .Lkof_rproc_sysfail_exit
                # pipe2(fail@16, O_CLOEXEC)
                li a7, 59
                addi a0, sp, 16
                li a1, 524288
                ecall
                bltz a0, .Lkof_rproc_sysfail_exit
                # clone(SIGCHLD, 0, 0, 0, 0)
                li a7, 220
                li a0, 17
                li a1, 0
                li a2, 0
                li a3, 0
                li a4, 0
                ecall
                bltz a0, .Lkof_rproc_sysfail_exit
                beqz a0, .Lkof_rproc_child
                j .Lkof_rproc_parent

            .Lkof_rproc_child:
                lw a0, 4(sp)
                li a1, 1
                li a2, 0
                li a7, 24
                ecall
                lw a0, 12(sp)
                li a1, 2
                li a2, 0
                li a7, 24
                ecall
                lw a0, 0(sp)
                li a7, 57
                ecall
                lw a0, 8(sp)
                li a7, 57
                ecall
                lw a0, 16(sp)
                li a7, 57
                ecall
                li a0, 0
                li a7, 57
                ecall
                li a7, 56
                li a0, -100
                la a1, .Lkof_rproc_devnull
                li a2, 0
                li a3, 0
                ecall
                # argv na pilha do filho
                lw t0, 16(s1)
                addi t1, t0, 2
                slli t1, t1, 3
                addi t1, t1, 15
                andi t1, t1, -16
                mv s4, t1
                sub sp, sp, t1
                mv s5, sp
                beqz s0, .Lkof_rproc_null0
                addi t2, s0, 24
                sd t2, 0(s5)
                j .Lkof_rproc_argv0_ok
            .Lkof_rproc_null0:
                sd zero, 0(s5)
            .Lkof_rproc_argv0_ok:
                li s3, 0
            .Lkof_rproc_argv_loop:
                lw t1, 16(s1)
                bge s3, t1, .Lkof_rproc_argv_done
                mv a0, s1
                mv a1, s3
                call kof_list_get
                addi t3, s3, 1
                slli t3, t3, 3
                add t3, s5, t3
                beqz a0, .Lkof_rproc_argv_null
                addi t2, a0, 24
                sd t2, 0(t3)
                j .Lkof_rproc_argv_next
            .Lkof_rproc_argv_null:
                sd zero, 0(t3)
            .Lkof_rproc_argv_next:
                addi s3, s3, 1
                j .Lkof_rproc_argv_loop
            .Lkof_rproc_argv_done:
                addi t3, s3, 1
                slli t3, t3, 3
                add t3, s5, t3
                sd zero, 0(t3)
                # issue #762: nenhum fd do pai/runtime vaza para o filho (JVM
                # ProcessBuilder fecha os herdados). close_range(3, ~0,
                # CLOSE_RANGE_CLOEXEC) marca todo fd >= 3 close-on-exec; 0/1/2
                # e o fail pipe (já O_CLOEXEC) sobrevivem. Fallback fcntl no
                # kernel < 5.9 (sem close_range).
                li a0, 3
                li a1, -1
                li a2, 4
                li a7, 436
                ecall
                bgez a0, .Lkof_rproc_cloexec_done
                li t0, 3
            .Lkof_rproc_cloexec_loop:
                mv a0, t0
                li a1, 2
                li a2, 1
                li a7, 25
                ecall
                addi t0, t0, 1
                li t1, 1024
                blt t0, t1, .Lkof_rproc_cloexec_loop
            .Lkof_rproc_cloexec_done:
                ld a0, 0(s5)
                mv a1, s5
                call execvp
                add sp, sp, s4
                la a1, .Lkof_rproc_execfail
                li a2, 20
                lw a0, 12(sp)
                li a7, 64
                ecall
                la a1, .Lkof_rproc_execfail
                li a2, 1
                lw a0, 20(sp)
                li a7, 64
                ecall
                li a0, 127
                li a7, 93
                ecall

            .Lkof_rproc_parent:
                sw a0, 28(sp)
                lw a0, 4(sp)
                li a7, 57
                ecall
                lw a0, 12(sp)
                li a7, 57
                ecall
                lw a0, 20(sp)
                li a7, 57
                ecall
            .Lkof_rproc_poll:
                lw t0, 0(sp)
                sw t0, 96(sp)
                li t1, 1
                sh t1, 100(sp)
                lw t0, 8(sp)
                sw t0, 104(sp)
                sh t1, 108(sp)
                addi a0, sp, 96
                li a1, 2
                li a2, 0
                li a3, 0
                li a4, 0
                li a7, 73
                ecall
                lhu t0, 102(sp)
                andi t0, t0, 25
                beqz t0, .Lkof_rproc_poll_err
                lw a0, 0(sp)
                ld a1, 112(sp)
                li a2, 4096
                li a7, 63
                ecall
                bgtz a0, .Lkof_rproc_read_out
                li t0, 1
                sw t0, 36(sp)
                j .Lkof_rproc_poll_err
            .Lkof_rproc_read_out:
                mv a2, a0
                addi a0, sp, 48
                ld a1, 112(sp)
                call .Lkof_rproc_append
                j .Lkof_rproc_poll
            .Lkof_rproc_poll_err:
                lhu t0, 110(sp)
                andi t0, t0, 25
                beqz t0, .Lkof_rproc_poll_check
                lw a0, 8(sp)
                ld a1, 112(sp)
                li a2, 4096
                li a7, 63
                ecall
                bgtz a0, .Lkof_rproc_read_err
                li t0, 1
                sw t0, 40(sp)
                j .Lkof_rproc_poll_check
            .Lkof_rproc_read_err:
                mv a2, a0
                addi a0, sp, 72
                ld a1, 112(sp)
                call .Lkof_rproc_append
                j .Lkof_rproc_poll
            .Lkof_rproc_poll_check:
                lw t0, 36(sp)
                beqz t0, .Lkof_rproc_poll
                lw t0, 40(sp)
                beqz t0, .Lkof_rproc_poll
                lw a0, 0(sp)
                li a7, 57
                ecall
                lw a0, 8(sp)
                li a7, 57
                ecall
                lw a0, 16(sp)
                ld a1, 112(sp)
                li a2, 1
                li a7, 63
                ecall
                mv s2, a0
                lw a0, 16(sp)
                li a7, 57
                ecall
                sw zero, 32(sp)
                blez s2, .Lkof_rproc_wait
                li t0, 1
                sw t0, 32(sp)
            .Lkof_rproc_wait:
                lw a0, 28(sp)
                addi a1, sp, 24
                li a2, 0
                li a3, 0
                li a7, 260
                ecall
                ld a0, 48(sp)
                ld a1, 56(sp)
                call kof_string_from_literal
                mv s3, a0
                ld a0, 72(sp)
                ld a1, 80(sp)
                call kof_string_from_literal
                mv s4, a0
                lw t0, 32(sp)
                beqz t0, .Lkof_rproc_build
                li s5, -1
                j .Lkof_rproc_build2
            .Lkof_rproc_build:
                lw s5, 24(sp)
                srai s5, s5, 8
                andi s5, s5, 255
            .Lkof_rproc_build2:
                li a0, 32
                call kof_alloc
                sd s3, 0(a0)
                sd s4, 8(a0)
                sw s5, 16(a0)
                sd zero, 24(a0)
                ld ra, 184(sp)
                ld s0, 176(sp)
                ld s1, 168(sp)
                ld s2, 160(sp)
                ld s3, 152(sp)
                ld s4, 144(sp)
                ld s5, 136(sp)
                addi sp, sp, 192
                ret

            .Lkof_rproc_sysfail_exit:
                la a0, .Lkof_rproc_sysfail
                li a1, 23
                call kof_string_from_literal
                mv s4, a0
                li a0, 32
                call kof_alloc
                sd zero, 0(a0)
                sd s4, 8(a0)
                li t0, -1
                sw t0, 16(a0)
                sd zero, 24(a0)
                ld ra, 184(sp)
                ld s0, 176(sp)
                ld s1, 168(sp)
                ld s2, 160(sp)
                ld s3, 152(sp)
                ld s4, 144(sp)
                ld s5, 136(sp)
                addi sp, sp, 192
                ret

                .section .bss
                .align 3
            _kof_rproc_out_buf: .space 1048576
            _kof_rproc_err_buf: .space 1048576
            _kof_rproc_chunk:   .space 4096
            """;
}
