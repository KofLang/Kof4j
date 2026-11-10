package dev.kof.compiler.runtime;

/**
 * G240 — Native x86-64 face of {@code lock (target) { }}: a reentrant lock per
 * object, kept in a fixed table of 256 held locks.
 *
 * <p>Table entry (16 B): {@code 0=target 8=owner tid (4B) 12=depth (4B)}; a
 * free entry has target 0. The table is guarded by a futex mutex
 * ({@code kof_lock_guard}, the {@code kof_alloc}/channel technique). A thread
 * that finds the target held by another thread snapshots
 * {@code kof_lock_gen} while still holding the guard, releases the guard and
 * futex-waits for the generation to change: every release that frees an entry
 * bumps the generation and wakes all waiters, so no wake-up is lost (a release
 * that happens between the snapshot and the wait makes the wait return at
 * once). The entry is removed when its depth reaches 0, so the table only
 * holds locks that are held right now. Single-threaded profiles (UEFI/BIOS)
 * reuse the same code: their thread id is constant and the futex is a no-op.
 */
public final class RuntimeLock {

    private RuntimeLock() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            .section .bss
            .balign 8
            kof_lock_tab: .zero 4096          # 256 entries x 16 B
            kof_lock_guard: .long 0
            kof_lock_gen: .long 0
            .section .rodata
            kof_lock_full_msg: .asciz "kof: lock table full (more than 256 locks held at once)"
            .text

            # guard acquire/release: rbx/r12/r13 are preserved; rax/rcx/rdx/
            # rsi/rdi/r10/r11 are scratch (syscall clobbers rcx/r11).
            .type kof_lock_guard_acquire, @function
            kof_lock_guard_acquire:
                subq $8, %rsp
            .Lkof_lock_guard_try:
                xorl %eax, %eax
                movl $1, %edx
                lock cmpxchgl %edx, kof_lock_guard(%rip)
                jz .Lkof_lock_guard_got
                leaq kof_lock_guard(%rip), %rdi   # futex WAIT while guard == 1
                xorl %esi, %esi
                movl $1, %edx
                xorq %r10, %r10
                call kof_plat_sync
                jmp .Lkof_lock_guard_try
            .Lkof_lock_guard_got:
                addq $8, %rsp
                ret

            .type kof_lock_guard_release, @function
            kof_lock_guard_release:
                subq $8, %rsp
                movl $0, kof_lock_guard(%rip)
                leaq kof_lock_guard(%rip), %rdi   # futex WAKE one guard waiter
                movl $1, %esi
                movl $1, %edx
                xorq %r10, %r10
                call kof_plat_sync
                addq $8, %rsp
                ret

            .globl kof_lock_enter
            .type kof_lock_enter, @function
            kof_lock_enter:
                # rdi = target. rbx = target, r12d = tid, r13 = scratch.
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                call kof_plat_thread_id
                movl %eax, %r12d
            .Lkof_lock_enter_retry:
                call kof_lock_guard_acquire
                leaq kof_lock_tab(%rip), %rcx
                xorq %r13, %r13                   # first free entry
                movl $256, %edx
            .Lkof_lock_enter_scan:
                movq 0(%rcx), %rax
                cmpq %rbx, %rax
                je .Lkof_lock_enter_found
                testq %rax, %rax
                jnz .Lkof_lock_enter_next
                testq %r13, %r13
                jnz .Lkof_lock_enter_next
                movq %rcx, %r13
            .Lkof_lock_enter_next:
                addq $16, %rcx
                decl %edx
                jnz .Lkof_lock_enter_scan
                testq %r13, %r13
                jz .Lkof_lock_enter_full
                movq %rbx, 0(%r13)                # take a free entry
                movl %r12d, 8(%r13)
                movl $1, 12(%r13)
                jmp .Lkof_lock_enter_done
            .Lkof_lock_enter_found:
                cmpl %r12d, 8(%rcx)
                jne .Lkof_lock_enter_busy
                incl 12(%rcx)                     # reentrant: same thread
                jmp .Lkof_lock_enter_done
            .Lkof_lock_enter_busy:
                movl kof_lock_gen(%rip), %r13d    # snapshot under the guard
                call kof_lock_guard_release
                leaq kof_lock_gen(%rip), %rdi     # futex WAIT while gen unchanged
                xorl %esi, %esi
                movl %r13d, %edx
                xorq %r10, %r10
                call kof_plat_sync
                jmp .Lkof_lock_enter_retry
            .Lkof_lock_enter_full:
                call kof_lock_guard_release
                leaq kof_lock_full_msg(%rip), %rdi
                call kof_panic
            .Lkof_lock_enter_done:
                call kof_lock_guard_release
                popq %r13
                popq %r12
                popq %rbx
                ret

            .globl kof_lock_exit
            .type kof_lock_exit, @function
            kof_lock_exit:
                # rdi = target. Frees the entry when its depth reaches 0.
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                call kof_lock_guard_acquire
                leaq kof_lock_tab(%rip), %rcx
                movl $256, %edx
            .Lkof_lock_exit_scan:
                cmpq %rbx, 0(%rcx)
                je .Lkof_lock_exit_found
                addq $16, %rcx
                decl %edx
                jnz .Lkof_lock_exit_scan
                call kof_lock_guard_release       # not held: nothing to do
                jmp .Lkof_lock_exit_ret
            .Lkof_lock_exit_found:
                decl 12(%rcx)
                jnz .Lkof_lock_exit_keep
                movq $0, 0(%rcx)
                movl $0, 8(%rcx)
                lock incl kof_lock_gen(%rip)
                call kof_lock_guard_release
                leaq kof_lock_gen(%rip), %rdi     # futex WAKE every waiter
                movl $1, %esi
                movl $0x7fffffff, %edx
                xorq %r10, %r10
                call kof_plat_sync
                jmp .Lkof_lock_exit_ret
            .Lkof_lock_exit_keep:
                call kof_lock_guard_release
            .Lkof_lock_exit_ret:
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }
}
