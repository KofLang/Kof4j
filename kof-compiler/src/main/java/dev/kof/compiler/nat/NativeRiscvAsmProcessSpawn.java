package dev.kof.compiler.nat;

/**
 * D-FULL-PARITY-050 row 1, slice D (lane native-cross) — {@code process.spawn}
 * + handle ops on the CROSS riscv64/aarch64 (aarch64 inherits line-by-line via
 * the translator). This is the LAST face of row 1: {@code process.run} landed
 * on the cross earlier (slice C, {@link NativeRiscvAsmProcess}); {@code spawn}
 * kept the honest {@code PROC001} until now.
 *
 * <p>Faithful port of the proven x86-64 {@code RuntimeProcessSpawn} to riscv64
 * asm. riscv64 has no {@code fork}, so the child is made with
 * {@code clone(flags=SIGCHLD(17), stack=0)} (the exact mechanism slice C
 * already uses and validates under qemu). The child keeps ONLY stdout on a
 * live pipe; stdin/stderr go to {@code /dev/null} — same measured JVM contract
 * (so {@code write} is an honest no-op on every target; live input is a rule-6
 * contract change). Failed exec = {@code -1} via a {@code CLOEXEC} pipe
 * (posix_spawn convention).
 *
 * <p>Handle = index into a persistent {@code .bss} table (64 slots x 32 B:
 * {@code {pid@0, out_r@4, flags@8, exit_code@12}}; flags 0=free,1=alive,2=dead);
 * {@code -1} is the invalid handle. Not a heap object — avoids a GC scan over an
 * int. Reaping is LAZY (in {@code alive}/{@code exitCode}/{@code kill}) with
 * {@code wait4(WNOHANG)}, mirroring the x86 slice exactly:
 * {@code readLine} (line without the newline, {@code ""} at EOF/dead),
 * {@code exitCode} ({@code Integer.MIN_VALUE} alive, {@code -1} dead/killed),
 * {@code alive} (Bool 0/1), {@code kill} (SIGKILL(9) + wait4 + forget),
 * {@code write} (no-op).
 *
 * <p>Syscalls (riscv64, asm-generic — identical numbers on aarch64, so the
 * translated child works too): pipe2=59, dup3=24, openat=56, close=57, read=63,
 * write=64, kill=129, exit=93, clone=220, wait4=260. {@code execvp} is the libc
 * PATH resolver (link-by-use — {@code NativeCrossLink.LIBC_SYMBOLS} already
 * lists it; the pruner drops this whole piece for programs that never spawn).
 */
public final class NativeRiscvAsmProcessSpawn {

    private NativeRiscvAsmProcessSpawn() {}

    public static String RISCV_RUNTIME_ASM_PROCESS_SPAWN = """
            # ── kof.process.spawn (riscv64) — linha 1, fatia D (cross) ──
            .section .rodata
            .Lkof_rpspawn_devnull:
                .asciz "/dev/null"
            .section .bss
            .align 3
            # 64 slots x 32 B {pid@0,out_r@4,flags@8,exit_code@12}
            _kof_rpspawn_tab:
                .space 2048
            # readLine buffer (1 byte por leitura)
            _kof_rpspawn_linebuf:
                .space 65536
            .section .text

            # kof_spawn_write(handle, String) — no-op honesto (stdin /dev/null).
            .globl kof_spawn_write
            .type kof_spawn_write, @function
            kof_spawn_write:
                ret

            # kof_process_spawn(a0=program, a1=List<String>) -> a0=indice|-1
            .globl kof_process_spawn
            .type kof_process_spawn, @function
            kof_process_spawn:
                addi sp, sp, -96
                sd ra, 88(sp)
                sd s0, 80(sp)
                sd s1, 72(sp)
                sd s2, 64(sp)
                sd s3, 56(sp)
                sd s4, 48(sp)
                sd s5, 40(sp)
                mv s0, a0                   # program
                mv s1, a1                   # list
                # slot livre?
                li s2, 0
            .Lkof_rpspawn_find:
                li t0, 64
                bgeu s2, t0, .Lkof_rpspawn_full
                slli t1, s2, 5
                la t2, _kof_rpspawn_tab
                add t2, t2, t1
                lw t3, 8(t2)
                beqz t3, .Lkof_rpspawn_found
                addi s2, s2, 1
                j .Lkof_rpspawn_find
            .Lkof_rpspawn_found:
                # pipe2(out=[0,4], 0)
                addi a0, sp, 0
                li a1, 0
                li a7, 59
                ecall
                bltz a0, .Lkof_rpspawn_sysfail
                # pipe2(fail=[8,12], O_CLOEXEC=524288)
                addi a0, sp, 8
                li a1, 524288
                li a7, 59
                ecall
                bltz a0, .Lkof_rpspawn_sysfail
                # clone(SIGCHLD=17, 0,0,0,0) — riscv nao tem fork
                li a0, 17
                li a1, 0
                li a2, 0
                li a3, 0
                li a4, 0
                li a7, 220
                ecall
                bltz a0, .Lkof_rpspawn_sysfail
                beqz a0, .Lkof_rpspawn_child
                j .Lkof_rpspawn_parent

            .Lkof_rpspawn_child:
                # dup3(out_w=4(sp) -> 1)
                lw a0, 4(sp)
                li a1, 1
                li a2, 0
                li a7, 24
                ecall
                # close(out_r=0(sp)); close(fail_r=8(sp)); close(0)
                lw a0, 0(sp)
                li a7, 57
                ecall
                lw a0, 8(sp)
                li a7, 57
                ecall
                li a0, 0
                li a7, 57
                ecall
                # openat(AT_FDCWD,/dev/null) -> cai em fd 0 (stdin)
                li a7, 56
                li a0, -100
                la a1, .Lkof_rpspawn_devnull
                li a2, 0
                li a3, 0
                ecall
                # dup3(0 -> 2): stderr tambem /dev/null
                li a0, 0
                li a1, 2
                li a2, 0
                li a7, 24
                ecall
                # argv = [prog, args..., NULL] na pilha do filho
                lw t0, 16(s1)
                addi t1, t0, 2
                slli t1, t1, 3
                addi t1, t1, 15
                andi t1, t1, -16
                mv s4, t1
                sub sp, sp, t1
                mv s5, sp
                beqz s0, .Lkof_rpspawn_null0
                addi t2, s0, 24
                sd t2, 0(s5)
                j .Lkof_rpspawn_argv0_ok
            .Lkof_rpspawn_null0:
                sd zero, 0(s5)
            .Lkof_rpspawn_argv0_ok:
                li s3, 0
            .Lkof_rpspawn_argv_loop:
                lw t1, 16(s1)
                bge s3, t1, .Lkof_rpspawn_argv_done
                mv a0, s1
                mv a1, s3
                call kof_list_get
                addi t3, s3, 1
                slli t3, t3, 3
                add t3, s5, t3
                beqz a0, .Lkof_rpspawn_argv_null
                addi t2, a0, 24
                sd t2, 0(t3)
                j .Lkof_rpspawn_argv_next
            .Lkof_rpspawn_argv_null:
                sd zero, 0(t3)
            .Lkof_rpspawn_argv_next:
                addi s3, s3, 1
                j .Lkof_rpspawn_argv_loop
            .Lkof_rpspawn_argv_done:
                addi t3, s3, 1
                slli t3, t3, 3
                add t3, s5, t3
                sd zero, 0(t3)
                ld a0, 0(s5)
                mv a1, s5
                call execvp
                # exec falhou: restaura sp, escreve 1 byte no pipe CLOEXEC, _exit(127)
                add sp, sp, s4
                lw a0, 12(sp)
                la a1, .Lkof_rpspawn_devnull
                li a2, 1
                li a7, 64
                ecall
                li a0, 127
                li a7, 93
                ecall

            .Lkof_rpspawn_parent:
                sw a0, 16(sp)               # pid
                lw a0, 4(sp)                # close(out_w)
                li a7, 57
                ecall
                lw a0, 12(sp)               # close(fail_w)
                li a7, 57
                ecall
                # read(fail_r=8(sp), 20(sp), 1): >0 = exec falhou; 0 = ok
                lw a0, 8(sp)
                addi a1, sp, 20
                li a2, 1
                li a7, 63
                ecall
                mv s3, a0
                lw a0, 8(sp)                # close(fail_r)
                li a7, 57
                ecall
                blez s3, .Lkof_rpspawn_ok
                # execfail: close(out_r) + wait4(pid) + return -1
                lw a0, 0(sp)
                li a7, 57
                ecall
                lw a0, 16(sp)
                addi a1, sp, 24
                li a2, 0
                li a3, 0
                li a7, 260
                ecall
                li a0, -1
                j .Lkof_rpspawn_ret
            .Lkof_rpspawn_ok:
                slli t1, s2, 5
                la t2, _kof_rpspawn_tab
                add t2, t2, t1
                lw t3, 16(sp)
                sw t3, 0(t2)                # pid
                lw t3, 0(sp)
                sw t3, 4(t2)                # out_r
                li t3, 1
                sw t3, 8(t2)                # flags=vivo
                sw zero, 12(t2)             # exit_code
                mv a0, s2                   # handle = indice
                j .Lkof_rpspawn_ret
            .Lkof_rpspawn_sysfail:
            .Lkof_rpspawn_full:
                li a0, -1
            .Lkof_rpspawn_ret:
                ld ra, 88(sp)
                ld s0, 80(sp)
                ld s1, 72(sp)
                ld s2, 64(sp)
                ld s3, 56(sp)
                ld s4, 48(sp)
                ld s5, 40(sp)
                addi sp, sp, 96
                ret

            # kof_spawn_alive(a0=handle) -> a0 0/1
            .globl kof_spawn_alive
            .type kof_spawn_alive, @function
            kof_spawn_alive:
                li t0, 64
                bgeu a0, t0, .Lkps_alive_false
                slli t1, a0, 5
                la t2, _kof_rpspawn_tab
                add t2, t2, t1
                lw t3, 8(t2)
                li t4, 1
                bne t3, t4, .Lkps_alive_false
                addi sp, sp, -32
                sd ra, 24(sp)
                sd s0, 16(sp)
                lw a0, 0(t2)                # pid
                mv s0, t2                   # slot
                addi a1, sp, 8
                li a2, 1                    # WNOHANG
                li a3, 0
                li a7, 260
                ecall
                blez a0, .Lkps_alive_negchk
                # reaped: exit = (wstat>>8)&255, flags=2, alive=false
                lw t3, 8(sp)
                srli t3, t3, 8
                andi t3, t3, 255
                sw t3, 12(s0)
                li t4, 2
                sw t4, 8(s0)
                li a0, 0
                j .Lkps_alive_out
            .Lkps_alive_negchk:
                li t4, 0
                blt a0, t4, .Lkps_alive_echild
                li a0, 1                    # == 0 → vivo
                j .Lkps_alive_out
            .Lkps_alive_echild:
                li t4, 2
                sw t4, 8(s0)
                li a0, 0
            .Lkps_alive_out:
                ld ra, 24(sp)
                ld s0, 16(sp)
                addi sp, sp, 32
                ret
            .Lkps_alive_false:
                li a0, 0
                ret

            # kof_spawn_exit_code(a0=handle) -> a0 (MIN vivo, -1 invalido/morto-por-kill)
            .globl kof_spawn_exit_code
            .type kof_spawn_exit_code, @function
            kof_spawn_exit_code:
                li t0, 64
                bgeu a0, t0, .Lkps_ec_neg
                slli t1, a0, 5
                la t2, _kof_rpspawn_tab
                add t2, t2, t1
                lw t3, 8(t2)
                beqz t3, .Lkps_ec_neg
                li t4, 2
                beq t3, t4, .Lkps_ec_dead
                # vivo: wait4 WNOHANG
                addi sp, sp, -32
                sd ra, 24(sp)
                sd s0, 16(sp)
                lw a0, 0(t2)
                mv s0, t2
                addi a1, sp, 8
                li a2, 1
                li a3, 0
                li a7, 260
                ecall
                blez a0, .Lkps_ec_alive
                lw t3, 8(sp)
                srli t3, t3, 8
                andi t3, t3, 255
                sw t3, 12(s0)
                li t4, 2
                sw t4, 8(s0)
                ld ra, 24(sp)
                ld s0, 16(sp)
                addi sp, sp, 32
                mv a0, t3
                ret
            .Lkps_ec_alive:
                ld ra, 24(sp)
                ld s0, 16(sp)
                addi sp, sp, 32
                li a0, 0x80000000           # padrão 32-bit de Integer.MIN_VALUE
                slli a0, a0, 32             # extensao de sinal 32->64 (Int no Kof
                srai a0, a0, 32             #   vive no registrador de 64 bits)
                ret
            .Lkps_ec_dead:
                lw a0, 12(t2)
                ret
            .Lkps_ec_neg:
                li a0, -1
                ret

            # kof_spawn_kill(a0=handle) → SIGKILL + wait4 + esquece
            .globl kof_spawn_kill
            .type kof_spawn_kill, @function
            kof_spawn_kill:
                li t0, 64
                bgeu a0, t0, .Lkps_kill_ret
                slli t1, a0, 5
                la t2, _kof_rpspawn_tab
                add t2, t2, t1
                lw t3, 8(t2)
                beqz t3, .Lkps_kill_ret
                li t4, 1
                bne t3, t4, .Lkps_kill_close
                # vivo: kill(pid, SIGKILL=9) + wait4(pid)
                lw a0, 0(t2)
                li a1, 9
                li a7, 129
                ecall
                addi sp, sp, -32
                sd ra, 24(sp)
                sd s0, 16(sp)
                lw a0, 0(t2)
                mv s0, t2
                addi a1, sp, 8
                li a2, 0
                li a3, 0
                li a7, 260
                ecall
                ld ra, 24(sp)
                ld s0, 16(sp)
                addi sp, sp, 32
            .Lkps_kill_close:
                lw a0, 4(t2)                # close(out_r)
                li a7, 57
                ecall
                sw zero, 8(t2)              # flags=0 (esquece)
                li t3, -1
                sw t3, 12(t2)               # exit_code = -1
            .Lkps_kill_ret:
                ret

            # kof_spawn_read_line(a0=handle) -> a0=KofString (linha sem \\n; vazio EOF)
            .globl kof_spawn_read_line
            .type kof_spawn_read_line, @function
            kof_spawn_read_line:
                addi sp, sp, -48
                sd ra, 40(sp)
                sd s0, 32(sp)
                sd s1, 24(sp)
                sd s2, 16(sp)
                sd s3, 8(sp)
                li t0, 64
                bgeu a0, t0, .Lkps_rl_empty
                slli t1, a0, 5
                la t2, _kof_rpspawn_tab
                add t2, t2, t1
                lw t3, 8(t2)
                beqz t3, .Lkps_rl_empty
                lw s3, 4(t2)                # out_r
                la s0, _kof_rpspawn_linebuf
                li s1, 0                    # pos
            .Lkps_rl_loop:
                li t0, 65535
                bge s1, t0, .Lkps_rl_build
                mv a0, s3
                add a1, s0, s1
                li a2, 1
                li a7, 63
                ecall
                blez a0, .Lkps_rl_build     # EOF/erro
                add a4, s0, s1
                lbu t4, 0(a4)
                li t5, 10
                beq t4, t5, .Lkps_rl_build  # newline consumido, nao entra
                addi s1, s1, 1
                j .Lkps_rl_loop
            .Lkps_rl_build:
                mv a0, s0
                mv a1, s1
                call kof_string_from_literal
                j .Lkps_rl_out
            .Lkps_rl_empty:
                la a0, _kof_rpspawn_linebuf
                li a1, 0
                call kof_string_from_literal
            .Lkps_rl_out:
                ld ra, 40(sp)
                ld s0, 32(sp)
                ld s1, 24(sp)
                ld s2, 16(sp)
                ld s3, 8(sp)
                addi sp, sp, 48
                ret
            """;
}
