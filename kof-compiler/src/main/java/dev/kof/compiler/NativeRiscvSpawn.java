package dev.kof.compiler;

/**
 * FASE 3 (REFACTOR-500): spawn/await riscv64 (clone+futex, asm puro).
 * Extraído verbatim de NativeBackend (usesSpawn + emitRiscvSpawn).
 * qemu-riscv64 8.2.2 NÃO implementa clone3 (ENOSYS) — usa clone(220) com o
 * flag-set da glibc (0x3D0F00), que é aceito. O filho herda os registradores
 * do pai no ecall (a0=0, s0=handle) e roda o trampoline; await espera via
 * futex em handle->done (sem pthread_join). exit(93) mata só a thread.
 */
final class NativeRiscvSpawn {

    private NativeRiscvSpawn() {}


    static boolean usesSpawn(IRModule module) {
        for (IRClass c : module.classes()) {
            for (IRMethod m : c.methods()) {
                for (IRBasicBlock b : m.basicBlocks()) {
                    for (KofOperation op : b.operations()) {
                        if (op instanceof KofCall kc
                                && (kc.methodName().equals("kof_spawn")
                                    || kc.methodName().equals("kof_spawn_result")
                                    || kc.methodName().equals("kof_await"))) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    static void emitRiscvSpawn(StringBuilder sb) {
        sb.append("""
            # ---- spawn/await riscv64 (NATIVE002-stdlib) ----
            .section .text
            # handle: [typeId@0(i32) done@4(i32) result@8 stack@16] (32B)
            # kof_spawn_result(task@a0) -> handle@a0
            .globl kof_spawn_result
            kof_spawn_result:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                sd   s2, 0(sp)
                mv   s0, a0                 # task
                li   a0, 32
                call kof_alloc
                mv   s1, a0                 # handle
                li   t0, 2
                sw   t0, 0(s1)              # typeId=2 (handle)
                sw   zero, 4(s1)            # done=0
                sd   zero, 8(s1)            # result=0
                # stack do worker: mmap(NULL, 1MB, RW, PRIVATE|ANON, -1, 0)
                li   a0, 0
                li   a1, 1048576
                li   a2, 3
                li   a3, 0x22
                li   a4, -1
                li   a5, 0
                li   a7, 222
                ecall
                sd   a0, 16(s1)             # stack base (p/ debug/free)
                li   t1, 1048576
                add  s2, a0, t1             # stack TOP
                sd   s2, 24(s1)             # stack top no handle (filho lê)
                # clone(flags, stack_top, ptid, tls, ctid) — filho herda s0,s1
                li   a0, 0x3D0F00
                mv   a1, s2
                li   a2, 0
                li   a3, 0
                li   a4, 0
                li   a7, 220
                ecall
                bltz a0, .Lsp_inline
                bnez a0, .Lsp_reg           # pai: registra handle p/ join
                # ---- filho: a0=0, s0=task, s1=handle ----
                # TROCA sp p/ a stack dedicada ANTES do call: o sp herdado aponta
                # p/ o frame ativo do kof_spawn_result do pai; o call empilharia
                # ra lá e corromperia os slots salvos do pai (race real — só
                # aparece no fire-and-forget, onde o pai continua sem bloquear).
                ld   sp, 24(s1)
                call kof_spawn_trampoline
                li   a0, 0
                li   a7, 93
                ecall
            .Lsp_inline:
                # clone falhou: roda inline (degradação segura, sem thread)
                call kof_spawn_trampoline
                j    .Lsp_ret
            .Lsp_reg:
                # handle na lista global (máx 64) p/ join implícito
                la   t0, kof_spawn_count
                ld   t1, 0(t0)
                li   t2, 64
                bge  t1, t2, .Lsp_ret
                slli t2, t1, 3
                la   t3, kof_spawn_handles
                add  t3, t3, t2
                sd   s1, 0(t3)
                addi t1, t1, 1
                sd   t1, 0(t0)
            .Lsp_ret:
                mv   a0, s1
                ld   s2, 0(sp)
                ld   s1, 8(sp)
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret
            # kof_spawn(task@a0) -> handle registrado (join implícito no fim do main)
            .globl kof_spawn
            kof_spawn:
                j    kof_spawn_result
            # trampoline: s0=task, s1=handle -> roda task.invoke(), marca done, wake
            kof_spawn_trampoline:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                ld   t0, 8(s0)              # task vtable
                ld   t0, 0(t0)              # vtable[0] = invoke
                mv   a0, s0
                jalr t0                     # a0 = resultado
                ld   s0, 16(sp)             # invoke pode clobberar s-regs? não
                ld   s1, 8(sp)              # (callee-saved), mas protege s0/s1
                sd   a0, 8(s1)              # handle->result
                fence rw, rw                # ordena result antes de done (RVO)
                li   t0, 1
                sw   t0, 4(s1)              # handle->done = 1
                addi a0, s1, 4              # &done (futex word)
                li   a1, 129                # FUTEX_WAKE_PRIVATE
                li   a2, 1
                li   a7, 98
                ecall
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret
            # kof_await(handle@a0) -> result@a0 (futex wait em done)
            .globl kof_await
            kof_await:
                beqz a0, .Lkw_null
                lw   t0, 4(a0)              # done?
                bnez t0, .Lkw_val
                addi sp, sp, -16
                sd   s0, 8(sp)
                sd   ra, 0(sp)
                mv   s0, a0
            .Lkw_wait:
                addi a0, s0, 4              # &done
                li   a1, 128                # FUTEX_WAIT_PRIVATE
                li   a2, 0                  # esperado done==0
                li   a3, 0
                li   a7, 98
                ecall
                lw   t0, 4(s0)
                beqz t0, .Lkw_wait
                mv   a0, s0
                ld   s0, 8(sp)
                ld   ra, 0(sp)
                addi sp, sp, 16
            .Lkw_val:
                ld   a0, 8(a0)
                ret
            .Lkw_null:
                li   a0, 0
                ret
            # join implícito: aguarda todos os handles registrados (lista .bss).
            .globl kof_spawn_join_all
            kof_spawn_join_all:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                sd   s2, 0(sp)
                la   s0, kof_spawn_handles
                la   s1, kof_spawn_count
                ld   s1, 0(s1)
                li   s2, 0
            .Lkj_loop:
                bge  s2, s1, .Lkj_done
                slli t0, s2, 3
                add  t0, s0, t0
                ld   a0, 0(t0)
                beqz a0, .Lkj_next
                call kof_await
            .Lkj_next:
                addi s2, s2, 1
                j    .Lkj_loop
            .Lkj_done:
                ld   ra, 24(sp)
                ld   s0, 16(sp)
                ld   s1, 8(sp)
                ld   s2, 0(sp)
                addi sp, sp, 32
                ret

            .section .data
            .align 3
            kof_spawn_handles: .space 512
            kof_spawn_count:   .quad 0
            .section .text
            """);
    }
}
