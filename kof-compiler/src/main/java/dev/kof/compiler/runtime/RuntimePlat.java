package dev.kof.compiler.runtime;

/**
 * B-0 (D-BAREMETAL-BOOT): costura de plataforma {@code kof_plat_*} — face
 * x86_64. Mesma ABI da face riscv64 ({@code NativeRiscvAsmRt0}): a
 * implementação Linux é syscall direto; o perfil bare-metal (B-1+) troca só
 * o corpo, sem tocar os chamadores.
 *
 * <p>Dividida por FAMÍLIA (um método = uma fatia p/ o podador de
 * {@code RuntimeSlices}; classe+método é a granularidade da fatia): só a
 * família referenciada pelo programa entra no binário. Núcleo single-thread
 * (write/exit), ambiente (time/random/tid), sincronização (sync/create),
 * I/O (read/close) e rede.
 */
public final class RuntimePlat {

    private RuntimePlat() {}

    /** B-3b-3: TX de 1 byte no COM1 (polling do LSR 0x3FD, bit THR vazio) —
     *  mesmo padrão dos laços do setor de boot (NativeBiosBootEmitter). */
    private static void emitBiosTxHelper(StringBuilder sb) {
        sb.append("""
            .section .text
            .globl kof_plat_bios_tx
            .type kof_plat_bios_tx, @function
            kof_plat_bios_tx:
                pushq %rdx
                movzbl %al, %r10d
                movw $0x3FD, %dx
            kof_plat_bios_txe:
                inb %dx, %al
                testb $0x20, %al
                jz kof_plat_bios_txe
                movw $0x3F8, %dx
                movb %r10b, %al
                outb %al, %dx
                popq %rdx
                ret
            """);
    }

    public static void emitPlatWrite(StringBuilder sb) {
        // M-2 (#1135): no perfil macOS o Darwin NÃO tem syscall Linux cru —
        // a costura fala com a libSystem (a libc do macOS): write/writev/exit
        // são chamadas de libc, não `syscall`. A ABI de argumentos é a mesma
        // (SysV x86-64: %rdi/%rsi/%rdx), então só o corpo muda.
        if (dev.kof.compiler.nat.NativeProfile.active.isMacos()) {
            RuntimePlatMacos.emitWrite(sb);
            return;
        }
        // B-2: no perfil UEFI os syscalls Linux não existem — a costura fala
        // com o firmware (OutputString/Exit) via RuntimeUefi.
        if (dev.kof.compiler.nat.NativeProfile.activeIsUefi()) {
            RuntimeUefi.emitUefiHelpers(sb);
            RuntimeUefi.emitUefiWrite(sb);
            return;
        }
        // B-3b-3: no perfil BIOS a costura escreve no COM1 (0x3F8) por porta
        // de E/S — o hello do main Kof sai no serial de verdade, bare.
        if (dev.kof.compiler.nat.NativeProfile.activeIsBios()) {
            emitBiosTxHelper(sb);
            sb.append("""
                .globl kof_plat_write
                .type kof_plat_write, @function
                kof_plat_write:
                    movq %rsi, %r8          # buf
                    movq %rdx, %r9          # len
                kof_plat_bios_w1:
                    testq %r9, %r9
                    jz kof_plat_bios_wdone
                    movb (%r8), %al
                    call kof_plat_bios_tx
                    incq %r8
                    decq %r9
                    jmp kof_plat_bios_w1
                kof_plat_bios_wdone:
                    ret

                .globl kof_plat_writev
                .type kof_plat_writev, @function
                kof_plat_writev:
                    movq %rsi, %r8          # iov
                    movq %rdx, %r11         # iovcnt
                    xorq %r10, %r10
                kof_plat_bios_wv1:
                    cmpq %r11, %r10
                    jae kof_plat_bios_wvdone
                    movq %r10, %rax
                    shlq $4, %rax           # iovec = 16 bytes
                    movq (%r8,%rax), %rsi   # iov[i].base
                    movq 8(%r8,%rax), %r9   # iov[i].len
                kof_plat_bios_wv2:
                    testq %r9, %r9
                    jz kof_plat_bios_wv3
                    movb (%rsi), %al
                    call kof_plat_bios_tx
                    incq %rsi
                    decq %r9
                    jmp kof_plat_bios_wv2
                kof_plat_bios_wv3:
                    incq %r10
                    jmp kof_plat_bios_wv1
                kof_plat_bios_wvdone:
                    ret

                .globl kof_plat_exit
                .type kof_plat_exit, @function
                kof_plat_exit:
                    cli
                kof_plat_bios_halt:
                    hlt
                    jmp kof_plat_bios_halt

                .globl kof_plat_exit_group
                .type kof_plat_exit_group, @function
                kof_plat_exit_group:
                    cli
                kof_plat_bios_halt_g:
                    hlt
                    jmp kof_plat_bios_halt_g
                """);
            return;
        }
        sb.append("""
            .section .text
            .globl kof_plat_write
            .type kof_plat_write, @function
            kof_plat_write:
                movq $1, %rax
                syscall
                ret

            .globl kof_plat_writev
            .type kof_plat_writev, @function
            kof_plat_writev:
                movq $20, %rax
                syscall
                ret

            .globl kof_plat_exit
            .type kof_plat_exit, @function
            kof_plat_exit:
                movq $60, %rax
                syscall

            .globl kof_plat_exit_group
            .type kof_plat_exit_group, @function
            kof_plat_exit_group:
                movq $231, %rax
                syscall
            """);
    }

    public static void emitPlatTime(StringBuilder sb) {
        // M-2 (#1135): no macOS o relógio vem da libSystem (clock_gettime) —
        // e o CLOCK_MONOTONIC do Darwin é 6 (no Linux é 1), medido nos
        // headers do SDK (RuntimePlatMacos).
        if (dev.kof.compiler.nat.NativeProfile.active.isMacos()) {
            RuntimePlatMacos.emitTime(sb);
            return;
        }
        // B-5 (D-BAREMETAL-BODIES, 24/09): no UEFI o relógio de parede vem de
        // RuntimeServices->GetTime (time.now()) e o monotônico do TSC calibrado
        // por BootServices->Stall; sleep por Stall — nada de recusa, roda bare.
        if (dev.kof.compiler.nat.NativeProfile.activeIsUefi()) {
            RuntimeUefi.emitUefiTime(sb);
            RuntimeUefi.emitUefiSleep(sb);
            RuntimeUefi.emitUefiMono(sb);
            return;
        }
        // B-5 (D-BAREMETAL-BODIES, 24/09): no BIOS o relógio de parede vem do
        // RTC CMOS (portas 0x70/0x71) — time.now() roda bare de verdade.
        if (dev.kof.compiler.nat.NativeProfile.activeIsBios()) {
            RuntimeBios.emitTime(sb);
            return;
        }
        sb.append("""
            .globl kof_plat_time
            .type kof_plat_time, @function
            kof_plat_time:
                movq %rdi, %rsi          # ts
                xorq %rdi, %rdi          # CLOCK_REALTIME
                movq $228, %rax
                syscall
                ret

            .globl kof_plat_time_mono
            .type kof_plat_time_mono, @function
            kof_plat_time_mono:
                movq %rdi, %rsi          # ts
                movq $1, %rdi            # CLOCK_MONOTONIC
                movq $228, %rax
                syscall
                ret

            .globl kof_plat_sleep
            .type kof_plat_sleep, @function
            kof_plat_sleep:
                movq $35, %rax           # nanosleep(req, rem)
                syscall
                ret
            """);
    }

    public static void emitPlatRandom(StringBuilder sb) {
        // M-2 (#1135): no macOS a aleatoriedade vem do CSPRNG do Darwin
        // (arc4random_buf) — a getentropy do Linux NÃO EXISTE no Darwin
        // (medido nos headers do SDK) e teria o limite de 256 bytes.
        if (dev.kof.compiler.nat.NativeProfile.active.isMacos()) {
            RuntimePlatMacos.emitRandom(sb);
            return;
        }
        // B-5: corpo real por face bare-metal (RDRAND/TSC + xorshift64),
        // compartilhado BIOS/UEFI — nunca um syscall Linux silencioso.
        if (dev.kof.compiler.nat.NativeProfile.activeIsUefi()
                || dev.kof.compiler.nat.NativeProfile.activeIsBios()) {
            RuntimeBareRandom.emit(sb);
            return;
        }
        sb.append("""
            .globl kof_plat_random
            .type kof_plat_random, @function
            kof_plat_random:
                movq $318, %rax          # getrandom(buf, len, 0)
                xorq %rdx, %rdx
                syscall
                ret
            """);
    }

    public static void emitPlatThreadId(StringBuilder sb) {
        // M-5 (#1135): no macOS o id da thread vem da API pública do Darwin
        // (pthread_threadid_np) — o gettid do Linux não existe lá.
        if (dev.kof.compiler.nat.NativeProfile.active.isMacos()) {
            RuntimePlatMacos.emitThreadId(sb);
            return;
        }
        // B-2: UEFI roda sem threads — TID constante (o GC do hello não marca concorrente).
        // B-3b-3: BIOS single-thread — mesmo contrato.
        if (dev.kof.compiler.nat.NativeProfile.activeIsUefi()
                || dev.kof.compiler.nat.NativeProfile.activeIsBios()) {
            RuntimeUefi.emitUefiThreadId(sb);
            return;
        }
        sb.append("""
            .globl kof_plat_thread_id
            .type kof_plat_thread_id, @function
            kof_plat_thread_id:
                movq $186, %rax          # gettid
                syscall
                ret
            """);
    }

    public static void emitPlatSync(StringBuilder sb) {
        // M-5 (#1135): no macOS o futex não existe — a costura fala a API
        // ulock do Darwin (__ulock_wait/__ulock_wake), com a semântica
        // medida na fonte do XNU (RuntimePlatMacos).
        if (dev.kof.compiler.nat.NativeProfile.active.isMacos()) {
            RuntimePlatMacos.emitSync(sb);
            return;
        }
        // B-2: UEFI é single-threaded — o futex nunca tem contensão;
        // no-op é semântica correta (não é silenciamento: não há threads
        // para esperar — kof_plat_thread_create é recusado com diagnóstico).
        // B-3b-3: BIOS single-thread — mesmo contrato.
        if (dev.kof.compiler.nat.NativeProfile.activeIsUefi()
                || dev.kof.compiler.nat.NativeProfile.activeIsBios()) {
            RuntimeUefi.emitUefiSyncNoop(sb);
            return;
        }
        sb.append("""
            .globl kof_plat_sync
            .type kof_plat_sync, @function
            kof_plat_sync:
                movq $202, %rax          # futex(uaddr, op, val, timeout)
                syscall
                ret
            """);
    }

    public static void emitPlatThreadCreate(StringBuilder sb) {
        // B-2: família sem corpo UEFI nesta fatia — recusa NOMEADA (R6),
        // nunca um syscall Linux silencioso que não existe no firmware.
        if (dev.kof.compiler.nat.NativeProfile.activeIsUefi()
                || dev.kof.compiler.nat.NativeProfile.activeIsBios()) {
            RuntimeBios.refuse(sb, "kof_plat_thread_create");
            return;
        }
        sb.append("""
            .globl kof_plat_thread_create
            .type kof_plat_thread_create, @function
            kof_plat_thread_create:
                jmp pthread_create       # Linux+libc: o spawn x86 é C call
            """);
    }

    public static void emitPlatIo(StringBuilder sb) {
        // M-2 (#1135): no macOS a io é a da libSystem (read/close) — a mesma
        // ABI do Linux (fd=%rdi, buf=%rsi, len=%rdx).
        if (dev.kof.compiler.nat.NativeProfile.active.isMacos()) {
            RuntimePlatMacos.emitIo(sb);
            return;
        }
        // B-2: família sem corpo UEFI nesta fatia — recusa NOMEADA (R6),
        // nunca um syscall Linux silencioso que não existe no firmware.
        if (dev.kof.compiler.nat.NativeProfile.activeIsUefi()
                || dev.kof.compiler.nat.NativeProfile.activeIsBios()) {
            RuntimeBios.refuse(sb, "kof_plat_read, kof_plat_close");
            return;
        }
        sb.append("""
            .globl kof_plat_read
            .type kof_plat_read, @function
            kof_plat_read:
                movq $0, %rax            # read(fd, buf, len)
                syscall
                ret

            .globl kof_plat_close
            .type kof_plat_close, @function
            kof_plat_close:
                movq $3, %rax            # close(fd)
                syscall
                ret
            """);
    }

    /**
     * M-4 (#1135): a família TLS do runtime (a cadeia de exceções
     * {@code kof_exc_chain}) pela costura — o Linux usa o TLS ELF
     * local-exec; o macOS usa uma pthread key (os corpos em RuntimePlatMacos).
     * O par get/set é TRANSPARENTE: só o {@code %rax} do get muda (o valor)
     * e o set preserva todos os registradores.
     */
    /**
     * M-6 (#1135): a família STAT da costura — o {@code newfstatat} que os
     * sítios do runtime faziam direto. O CONTRATO é o buffer do runtime no
     * layout do HOST (st_mode@24, st_size@48, st_mtim@88/96): no Linux o
     * corpo é o syscall 262 (semântica idêntica à de antes); no macOS é o
     * {@code fstatat} da libSystem com a NORMALIZAÇÃO do layout do Darwin
     * para o do host (os offsets medidos no SDK: mode@4, size@96,
     * mtimespec@48).
     */
    public static void emitPlatStat(StringBuilder sb) {
        if (dev.kof.compiler.nat.NativeProfile.active.isMacos()) {
            RuntimePlatMacosStat.emitStat(sb);
            return;
        }
        sb.append("""
            .section .text
            .globl kof_plat_stat
            .type kof_plat_stat, @function
            kof_plat_stat:
                # rdi=dirfd (-100 = AT_FDCWD), rsi=path, rdx=buf, r10=flags
                movq $262, %rax          # newfstatat(dirfd, path, buf, flags)
                syscall
                ret
            """);
    }

    public static void emitPlatTls(StringBuilder sb) {
        if (dev.kof.compiler.nat.NativeProfile.active.isMacos()) {
            RuntimePlatMacosTls.emitTls(sb);
            return;
        }
        sb.append("""
            .section .text
            .globl kof_plat_tls_init
            .type kof_plat_tls_init, @function
            kof_plat_tls_init:
                ret                     # no Linux o TLS ELF ja existe (initial-exec)

            .globl kof_plat_tls_get
            .type kof_plat_tls_get, @function
            kof_plat_tls_get:
                movq %fs:kof_exc_chain@tpoff, %rax
                ret

            .globl kof_plat_tls_set
            .type kof_plat_tls_set, @function
            kof_plat_tls_set:
                movq %rdi, %fs:kof_exc_chain@tpoff
                ret
            """);
    }

    public static void emitPlatNet(StringBuilder sb) {
        // B-2: família sem corpo UEFI nesta fatia — recusa NOMEADA (R6),
        // nunca um syscall Linux silencioso que não existe no firmware.
        if (dev.kof.compiler.nat.NativeProfile.activeIsUefi()
                || dev.kof.compiler.nat.NativeProfile.activeIsBios()) {
            RuntimeBios.refuse(sb, "kof_plat_net_socket, kof_plat_net_connect, kof_plat_net_bind, kof_plat_net_listen, kof_plat_net_accept, kof_plat_net_send");
            return;
        }
        // M-6 (#1135): no macOS a rede fala os sockets do libSystem.
        if (dev.kof.compiler.nat.NativeProfile.active.isMacos()) {
            RuntimePlatMacosNet.emitNet(sb);
            return;
        }
        sb.append("""
            .globl kof_plat_net_socket
            .type kof_plat_net_socket, @function
            kof_plat_net_socket:
                movq $41, %rax           # socket(domain, type, proto)
                syscall
                ret

            .globl kof_plat_net_connect
            .type kof_plat_net_connect, @function
            kof_plat_net_connect:
                movq $42, %rax           # connect(fd, addr, len)
                syscall
                ret

            .globl kof_plat_net_bind
            .type kof_plat_net_bind, @function
            kof_plat_net_bind:
                movq $49, %rax           # bind(fd, addr, len)
                syscall
                ret

            .globl kof_plat_net_listen
            .type kof_plat_net_listen, @function
            kof_plat_net_listen:
                movq $50, %rax           # listen(fd, backlog)
                syscall
                ret

            .globl kof_plat_net_accept
            .type kof_plat_net_accept, @function
            kof_plat_net_accept:
                movq $43, %rax           # accept(fd, addr, lenp)
                syscall
                ret

            .globl kof_plat_net_send
            .type kof_plat_net_send, @function
            kof_plat_net_send:
                movq $44, %rax           # sendto(fd, buf, len, flags, 0, 0)
                syscall
                ret
            """);
    }
}
