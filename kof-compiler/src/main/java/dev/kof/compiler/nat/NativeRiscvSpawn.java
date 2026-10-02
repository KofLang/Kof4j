package dev.kof.compiler.nat;
import dev.kof.compiler.IRBasicBlock;
import dev.kof.compiler.IRClass;
import dev.kof.compiler.IRMethod;
import dev.kof.compiler.IRModule;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofOperation;

/**
 * FASE 3 (REFACTOR-500): spawn/await riscv64 (clone+futex, asm puro).
 * Extraído verbatim de NativeBackend (usesSpawn + emitRiscvSpawn).
 * qemu-riscv64 8.2.2 NÃO implementa clone3 (ENOSYS) — usa clone(220) com o
 * flag-set da glibc (0x3D0F00), que é aceito. O filho herda os registradores
 * do pai no ecall (a0=0, s0=handle) e roda o trampoline; await espera via
 * futex em handle->done (sem pthread_join). exit(93) mata só a thread.
 */
public final class NativeRiscvSpawn {

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
            # handle: [typeId@0(i32) done@4(i32) result@8 stack@16 stacktop@24
            #          tid@32 cancelEntry@40 exc@48 pending@56] (64B, §286) — tid
            # é gravado pelo KERNEL (clone ctid); cancelEntry/exc/pending pelas
            # fatias CONC001 (B48); pending = cancel pedido antes do registro.
            # kof_spawn_result(task@a0) -> handle@a0
            .globl kof_spawn_result
            kof_spawn_result:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)              # §545: bloco TLS do worker
                mv   s0, a0                 # task
                li   a0, 64                 # §286: +8 p/ pending@56
                call kof_alloc
                mv   s1, a0                 # handle
                li   t0, 2
                sw   t0, 0(s1)              # typeId=2 (handle)
                sw   zero, 4(s1)            # done=0
                sd   zero, 8(s1)            # result=0
                sd   zero, 32(s1)           # tid=0
                sd   zero, 40(s1)           # cancelEntry=0
                sd   zero, 48(s1)           # exc=0
                sd   zero, 56(s1)           # §286: pending=0
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
                # §545: bloco TLS do worker via `_dl_allocate_tls(NULL)` do
                # loader — spawn FORÇA o link dinâmico, então o símbolo resolve
                # sempre (nunca weak: --gc-sections o relaxaria a 0). aarch64
                # herda esta sequência pelo tradutor linha-a-linha.
                li   a0, 0
                call _dl_allocate_tls
                mv   s3, a0
                # clone(flags, stack_top, ptid=&tid, tls=0, ctid=0) — filho
                # herda s0,s1; o KERNEL grava o TID do filho em &handle->tid
                # (ctid), que kof_cancel (B48) usa p/ achar a entry de flag.
                # §129: a cadeia é por-TID (kof_exc_slot), então não precisa de
                # TLS no clone. B-1: o clone cruza a costura kof_plat_thread_create
                # (o `call` riscv não empilha — o frame do pai fica intacto).
                li   a0, 0x3D0F00
                mv   a1, s2
                addi a2, s1, 32
                mv   a3, s3                 # §545: tls = bloco do loader (tp)
                li   a4, 0
                call kof_plat_thread_create
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
                ld   s3, 8(sp)
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret
            # kof_spawn(task@a0) -> handle registrado (join implícito no fim do main)
            .globl kof_spawn
            kof_spawn:
                j    kof_spawn_result
            # trampoline: s0=task, s1=handle -> registra cancel slot (CONC001),
            # roda task.invoke(), marca done, wake, remove o slot
            kof_spawn_trampoline:
                addi sp, sp, -96
                sd   ra, 48(sp)
                sd   s0, 56(sp)
                sd   s1, 64(sp)
                # CONC001: registra (TID real, flag=0) e guarda a entry no
                # handle->cancelEntry — kof_cancel/cancelled (B48) usam.
                li   a7, 178                 # gettid
                ecall
                call kof_cancel_slot_insert
                sd   a0, 40(s1)             # handle->cancelEntry
                sd   a0, 40(sp)             # §286: cópia da entry NO FRAME (o delete
                                            # lê daqui; o handle pode ser reciclado
                                            # entre done=1 e o nosso delete)
                fence rw, rw                # §286: Dekker — store(entry) → load(pending)
                ld   t0, 56(s1)             # cancel pedido antes do registro?
                beqz t0, .Lst_no_pend
                beqz a0, .Lst_no_pend       # tabela cheia → nada a marcar
                li   t0, 1
                sd   t0, 8(a0)              # flag = 1 (pedido antigo vale agora)
            .Lst_no_pend:
                # §129 (port 19/09): frame de handler POR WORKER no nó de 32B em
                # 0..31 do frame. A cadeia é por-TID (kof_exc_slot), então um
                # throw sem try no worker longjmpa AQUI, não no try da main; o
                # catch publica a causa em handle->exc@48 e o consumidor
                # (await/await_timeout/select_any) relança.
                sd   s1, 32(sp)             # frame->handle (o catch lê daqui)
                la   t0, .Lst_catch
                sd   t0, 0(sp)              # [0]=handler
                sd   sp, 8(sp)              # [8]=sp a restaurar
                sd   s11, 16(sp)            # [16]=s11 a restaurar
                call kof_exc_slot
                sd   a0, 72(sp)             # §129: &chain do worker (limpar no fim)
                ld   t1, 0(a0)
                sd   t1, 24(sp)             # [24]=chain antigo
                sd   sp, 0(a0)              # chain = este nó
                ld   t0, 8(s0)              # task vtable
                ld   t0, 0(t0)              # vtable[0] = invoke
                mv   a0, s0
                jalr t0                     # a0 = resultado
                sd   a0, 80(sp)             # §129: preserva o resultado (o
                                            # kof_exc_slot abaixo clobbera a0)
                # término normal: desinstala o handler e publica o resultado
                call kof_exc_slot
                ld   t2, 24(sp)
                sd   t2, 0(a0)
                ld   s0, 56(sp)
                ld   s1, 64(sp)
                ld   a0, 80(sp)
                sd   a0, 8(s1)              # handle->result
                fence rw, rw                # ordena result antes de done (RVO)
                li   t0, 1
                sw   t0, 4(s1)              # handle->done = 1
                j    .Lst_wake
            .Lst_catch:
                # kof_throw_string desempilhou a chain e restaurou sp/s11; a0 =
                # mensagem. O handle vem do FRAME (32(sp)), NÃO de s1 (o task
                # pode clobberar callee-saved).
                ld   s1, 32(sp)
                sd   a0, 48(s1)             # handle->exc = causa
                fence rw, rw
                li   t0, 1
                sw   t0, 4(s1)              # handle->done = 1
            .Lst_wake:
                addi a0, s1, 4              # &done (futex word)
                li   a1, 129                # FUTEX_WAKE_PRIVATE
                li   a2, 1
                call kof_plat_sync
                # CONC001/§286: slot volta a vazio (tid=0) sem tocar worker
                # alheio — a entry vem do FRAME (40(sp)), não do handle
                # (reciclável entre done=1 e este delete).
                ld   t0, 40(sp)
                beqz t0, .Lst_noexc
                sd   zero, 0(t0)
            .Lst_noexc:
                # §129: libera a entry da cadeia deste TID (a tabela não cresce
                # com workers que já terminaram).
                ld   t0, 72(sp)
                beqz t0, .Lst_nocl
                sd   zero, 0(t0)
            .Lst_nocl:
                ld   ra, 48(sp)
                ld   s0, 56(sp)
                ld   s1, 64(sp)
                addi sp, sp, 96
                ret
            # kof_await(handle@a0) -> result@a0 (futex wait em done)
            .globl kof_await
            kof_await:
                beqz a0, .Lkw_null
                lw   t0, 4(a0)              # done?
                beqz t0, .Lkw_futex
                fence r, rw                 # §256: acquire no caminho rapido
                j    .Lkw_val
            .Lkw_futex:
                addi sp, sp, -16
                sd   s0, 8(sp)
                sd   ra, 0(sp)
                mv   s0, a0
            .Lkw_wait:
                addi a0, s0, 4              # &done
                li   a1, 128                # FUTEX_WAIT_PRIVATE
                li   a2, 0                  # esperado done==0
                li   a3, 0
                call kof_plat_sync
                lw   t0, 4(s0)
                beqz t0, .Lkw_wait
                mv   a0, s0
                ld   s0, 8(sp)
                ld   ra, 0(sp)
                addi sp, sp, 16
            .Lkw_val:
                ld   t0, 48(a0)             # §129: handle excepcional?
                beqz t0, .Lkw_ret
                mv   a0, t0
                j    kof_throw_string       # relança no consumidor (não retorna)
            .Lkw_ret:
                ld   a0, 8(a0)
                ret
            .Lkw_null:
                li   a0, 0
                ret
            # join implícito: aguarda todos os handles registrados (lista .bss).
            .globl kof_spawn_join_all
            kof_spawn_join_all:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)
                sd   s1, 24(sp)
                sd   s2, 16(sp)
                sd   s3, 8(sp)
                la   s0, kof_spawn_handles
                la   s1, kof_spawn_count
                ld   s1, 0(s1)
                li   s2, 0
            .Lkj_loop:
                bge  s2, s1, .Lkj_done
                slli t0, s2, 3
                add  t0, s0, t0
                ld   s3, 0(t0)
                beqz s3, .Lkj_next
                # §129: join implícito espera done SEM relançar (paridade x86
                # pthread_join) — só o await explícito do usuário relança a causa.
            .Lkj_wait:
                lw   t0, 4(s3)
                bnez t0, .Lkj_next
                addi a0, s3, 4
                li   a1, 128                # FUTEX_WAIT_PRIVATE
                li   a2, 0
                li   a3, 0
                call kof_plat_sync
                j    .Lkj_wait
            .Lkj_next:
                addi s2, s2, 1
                j    .Lkj_loop
            .Lkj_done:
                ld   ra, 40(sp)
                ld   s0, 32(sp)
                ld   s1, 24(sp)
                ld   s2, 16(sp)
                ld   s3, 8(sp)
                addi sp, sp, 48
                ret

            .section .data
            .align 3
            kof_spawn_handles: .space 512
            kof_spawn_count:   .quad 0
            .section .text
            """);
    }
}
