package dev.kof.compiler.nat;

// G240: `lock (target) { }` no runtime cross — kof_lock_enter/kof_lock_exit em
// asm riscv64 (aarch64 herda via NativeAarch64Translator). Port de
// runtime/RuntimeLock (x86): a mesma tabela de 256 entradas de 16B
// [target@0, tid@8 (4B), depth@12 (4B)], guardada por um mutex futex
// (amoswap.w + fence, como os canais da B61) e com espera no contador de
// geracao kof_lock_gen (snapshot sob o guard, futex WAIT fora dele; a
// liberacao que esvazia uma entrada incrementa a geracao e acorda todos).
// Futex PRIVATE (128/129), a convencao da lane cross. So usa mnemonicos que o
// tradutor aarch64 ja mapeia (sem amoadd.w: a geracao so muda sob o guard).
//
// ABI: kof_lock_enter(a0=target) -> void; kof_lock_exit(a0=target) -> void.
public final class NativeRiscvAsmRtB85 {

    private NativeRiscvAsmRtB85() {}

    static String RISCV_RUNTIME_ASM_B_85 = """
            # ---- G240: lock (target) { } (port RuntimeLock) ----
            .section .data
            .align 3
            kof_lock_tab: .space 4096
            kof_lock_guard: .word 0
            kof_lock_gen: .word 0
            kof_lock_full_msg: .asciz "kof: lock table full (more than 256 locks held at once)"
            .align 3
            .section .text

            kof_lock_guard_acquire:
                addi sp, sp, -16
                sd   ra, 8(sp)
            .Lrlock_guard_try:
                la   t2, kof_lock_guard
                li   t1, 1
                amoswap.w t0, t1, (t2)
                beqz t0, .Lrlock_guard_got
                la   a0, kof_lock_guard     # futex WAIT while guard == 1
                li   a1, 128
                li   a2, 1
                li   a3, 0
                call kof_plat_sync
                j    .Lrlock_guard_try
            .Lrlock_guard_got:
                fence rw, rw                # acquire
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            kof_lock_guard_release:
                addi sp, sp, -16
                sd   ra, 8(sp)
                fence rw, rw                # release
                la   t2, kof_lock_guard
                sw   zero, 0(t2)
                la   a0, kof_lock_guard     # futex WAKE one guard waiter
                li   a1, 129
                li   a2, 1
                li   a3, 0
                call kof_plat_sync
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            # kof_lock_enter(a0=target): s0 = target, s1 = tid, s2 = scratch
            .globl kof_lock_enter
            .type kof_lock_enter, @function
            kof_lock_enter:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                mv   s0, a0
                call kof_plat_thread_id
                mv   s1, a0
            .Lrlock_enter_retry:
                call kof_lock_guard_acquire
                la   t3, kof_lock_tab
                li   s2, 0                  # first free entry
                li   t4, 256
            .Lrlock_enter_scan:
                ld   t0, 0(t3)
                beq  t0, s0, .Lrlock_enter_found
                bnez t0, .Lrlock_enter_next
                bnez s2, .Lrlock_enter_next
                mv   s2, t3
            .Lrlock_enter_next:
                addi t3, t3, 16
                addi t4, t4, -1
                bnez t4, .Lrlock_enter_scan
                beqz s2, .Lrlock_enter_full
                sd   s0, 0(s2)              # take a free entry
                sw   s1, 8(s2)
                li   t0, 1
                sw   t0, 12(s2)
                j    .Lrlock_enter_done
            .Lrlock_enter_found:
                lw   t1, 8(t3)
                bne  t1, s1, .Lrlock_enter_busy
                lw   t1, 12(t3)             # reentrant: same thread
                addi t1, t1, 1
                sw   t1, 12(t3)
                j    .Lrlock_enter_done
            .Lrlock_enter_busy:
                la   t2, kof_lock_gen
                lw   s2, 0(t2)              # snapshot under the guard
                call kof_lock_guard_release
                la   a0, kof_lock_gen       # futex WAIT while gen unchanged
                li   a1, 128
                mv   a2, s2
                li   a3, 0
                call kof_plat_sync
                j    .Lrlock_enter_retry
            .Lrlock_enter_full:
                call kof_lock_guard_release
                la   a0, kof_lock_full_msg
                call kof_panic
            .Lrlock_enter_done:
                call kof_lock_guard_release
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_lock_exit(a0=target): frees the entry when its depth reaches 0
            .globl kof_lock_exit
            .type kof_lock_exit, @function
            kof_lock_exit:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                mv   s0, a0
                call kof_lock_guard_acquire
                la   t3, kof_lock_tab
                li   t4, 256
            .Lrlock_exit_scan:
                ld   t0, 0(t3)
                beq  t0, s0, .Lrlock_exit_found
                addi t3, t3, 16
                addi t4, t4, -1
                bnez t4, .Lrlock_exit_scan
                call kof_lock_guard_release # not held: nothing to do
                j    .Lrlock_exit_ret
            .Lrlock_exit_found:
                lw   t1, 12(t3)
                addi t1, t1, -1
                sw   t1, 12(t3)
                bnez t1, .Lrlock_exit_keep
                sd   zero, 0(t3)
                sw   zero, 8(t3)
                la   t2, kof_lock_gen
                lw   t1, 0(t2)
                addi t1, t1, 1
                sw   t1, 0(t2)              # gen++ (only under the guard)
                call kof_lock_guard_release
                la   a0, kof_lock_gen       # futex WAKE every waiter
                li   a1, 129
                li   a2, 2147483647
                li   a3, 0
                call kof_plat_sync
                j    .Lrlock_exit_ret
            .Lrlock_exit_keep:
                call kof_lock_guard_release
            .Lrlock_exit_ret:
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret
            """;
}
