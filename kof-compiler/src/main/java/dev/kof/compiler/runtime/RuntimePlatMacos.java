package dev.kof.compiler.runtime;

/**
 * M-2 (#1135, D-NATIVE-MACOS): os CORPOS da costura {@code kof_plat_*} sobre a
 * <em>libSystem</em> (a libc do Darwin) — a face macOS da costura.
 *
 * <p><b>POR QUE UMA CLASSE À PARTE:</b> a costura é o ponto onde o runtime
 * fala com o SO. No Linux ela fala o ABI Linux cru ({@code syscall}); no
 * macOS ela fala a libSystem (a chamada C). O Darwin NÃO tem syscall Linux,
 * então a macOS é a face que migra — e os corpos moram aqui, numa classe só,
 * para que a costura por família fique legível (e o arquivo dentro do
 * gate de 500 linhas).
 *
 * <p><b>O CONTRATO É O MESMO, O CORPO MUDA:</b> a ABI de argumentos é a
 * SysV x86-64 nos dois lados ({@code %rdi}, {@code %rsi}, {@code %rdx}), então
 * cada corpo muda só a ÚLTIMA linha — o syscall Linux vira a chamada da
 * libSystem. A EXCEÇÃO medida é o relógio: o {@code CLOCK_MONOTONIC} do
 * Darwin é <b>6</b> (no Linux é 1) — usar o número do Linux pediria um
 * clockid que o Darwin não tem.
 *
 * <p><b>MEDIDO NOS HEADERS DO SDK do macOS</b> (o toolchain provisionado por
 * {@code scripts/provision-macho-toolchain.sh} traz os headers do Darwin):
 * {@code CLOCK_REALTIME = 0}, {@code CLOCK_MONOTONIC = 6},
 * {@code int clock_gettime(clockid_t, struct timespec *)},
 * {@code int nanosleep(const struct timespec *, struct timespec *)},
 * {@code void arc4random_buf(void *, size_t)} (10.7+),
 * {@code ssize_t read(int, void *, size_t)}, {@code int close(int)} — e o
 * {@code struct timespec} do Darwin tem o MESMO layout do Linux
 * ({@code tv_sec} em 0, {@code tv_nsec} em 8), então o runtime que lê
 * {@code 0(%rsp)}/`8(%rsp)` continua certo. O {@code getentropy} do Linux
 * <b>NÃO EXISTE</b> no Darwin — por isso a aleatoriedade usa a
 * {@code arc4random_buf} (o CSPRNG do próprio Darwin), que aliás não tem o
 * limite de 256 bytes da {@code getentropy}.
 *
 * <p><b>O ALINHAMENTO DA PILHA:</b> a libSystem usa SSE, e a ABI SysV exige
 * {@code %rsp ≡ 0 (mod 16)} NO SITE do call. A forma usada aqui é a mesma
 * que o runtime já usa no spawn ({@code pushq %r15; movq %rsp, %r15;
 * andq $-16, %rsp; call …; subq %rsp, %r15; addq %r15, %rsp; popq %r15}) —
 * o delta devolve a pilha exata, sem tocar a red zone do caller.
 */
public final class RuntimePlatMacos {

    private RuntimePlatMacos() {}

    /**
     * A FAMÍLIA write/exit (a fatia M-2 1): {@code write}, {@code writev} e
     * as duas saídas. O Darwin não tem {@code exit_group}: as duas saídas
     * chamam {@code exit} (o processo termina de qualquer forma; no Linux o
     * {@code kof_plat_exit} encerra só a thread — a diferença fica
     * registrada aqui).
     */
    public static void emitWrite(StringBuilder sb) {
        sb.append("""
            .section .text
            .globl kof_plat_write
            .type kof_plat_write, @function
            kof_plat_write:
                pushq %r15              # alinha a pilha (a libSystem usa SSE)
                movq %rsp, %r15
                andq $-16, %rsp         # rsp ≡ 0 (mod 16) NO SITE DO CALL
                call write              # write(fd=%rdi, buf=%rsi, len=%rdx)
                subq %rsp, %r15         # devolve o delta do alinhamento
                addq %r15, %rsp
                popq %r15
                ret

            .globl kof_plat_writev
            .type kof_plat_writev, @function
            kof_plat_writev:
                pushq %r15
                movq %rsp, %r15
                andq $-16, %rsp
                call writev             # writev(fd=%rdi, iov=%rsi, cnt=%rdx)
                subq %rsp, %r15
                addq %r15, %rsp
                popq %r15
                ret

            .globl kof_plat_exit
            .type kof_plat_exit, @function
            kof_plat_exit:
                pushq %r15
                movq %rsp, %r15
                andq $-16, %rsp
                call exit               # exit(code=%rdi) — NÃO retorna
                ud2                     # se retornar, trap (não há retorno)

            .globl kof_plat_exit_group
            .type kof_plat_exit_group, @function
            kof_plat_exit_group:
                pushq %r15
                movq %rsp, %r15
                andq $-16, %rsp
                call exit               # o Darwin não tem exit_group: exit
                ud2
            """);
    }

    /**
     * A FAMÍLIA time: o relógio de parede, o monotônico e o sleep.
     *
     * <p><b>O PONTO MEDIDO:</b> {@code CLOCK_MONOTONIC} é <b>6</b> no Darwin
     * (no Linux é 1) — o número do Linux pediria um clockid inválido. E o
     * {@code struct timespec} do Darwin tem o mesmo layout do Linux
     * ({@code tv_sec} em 0, {@code tv_nsec} em 8), então o runtime que lê
     * {@code 0(%rsp)}/`8(%rsp)` continua certo.
     */
    public static void emitTime(StringBuilder sb) {
        sb.append("""
            .globl kof_plat_time
            .type kof_plat_time, @function
            kof_plat_time:
                movq %rdi, %rsi          # ts (o 2o argumento da libSystem)
                xorq %rdi, %rdi          # CLOCK_REALTIME = 0 (medido no SDK)
                pushq %r15
                movq %rsp, %r15
                andq $-16, %rsp
                call clock_gettime       # clock_gettime(clockid=%rdi, ts=%rsi)
                subq %rsp, %r15
                addq %r15, %rsp
                popq %r15
                ret

            .globl kof_plat_time_mono
            .type kof_plat_time_mono, @function
            kof_plat_time_mono:
                movq %rdi, %rsi          # ts
                movq $6, %rdi            # CLOCK_MONOTONIC = 6 NO DARWIN (1 no Linux)
                pushq %r15
                movq %rsp, %r15
                andq $-16, %rsp
                call clock_gettime
                subq %rsp, %r15
                addq %r15, %rsp
                popq %r15
                ret

            .globl kof_plat_sleep
            .type kof_plat_sleep, @function
            kof_plat_sleep:
                pushq %r15
                movq %rsp, %r15
                andq $-16, %rsp
                call nanosleep           # nanosleep(req=%rdi, rem=%rsi)
                subq %rsp, %r15
                addq %r15, %rsp
                popq %r15
                ret
            """);
    }

    /**
     * A FAMÍLIA random: o CSPRNG do Darwin.
     *
     * <p><b>POR QUE {@code arc4random_buf} E NÃO {@code getentropy}:</b> a
     * {@code getentropy} do Linux <b>NÃO EXISTE</b> no Darwin (medido nos
     * headers do SDK) — e ela tem um limite de 256 bytes por chamada, que o
     * {@code random.hex(n)} do Kof estouraria. A {@code arc4random_buf} é o
     * CSPRNG do próprio Darwin (10.7+), sem limite de tamanho.
     *
     * <p>O contrato da costura é {@code kof_plat_random(buf, len) -> n} com
     * {@code n >= 0} = bytes lidos (o chamador testa o sinal), e a
     * {@code arc4random_buf} devolve {@code void} — o corpo devolve o
     * {@code len} pedido, que é o que foi escrito.
     */
    public static void emitRandom(StringBuilder sb) {
        sb.append("""
            .globl kof_plat_random
            .type kof_plat_random, @function
            kof_plat_random:
                pushq %rbx
                movq %rsi, %rbx          # len (o contrato: n = bytes escritos)
                pushq %r15
                movq %rsp, %r15
                andq $-16, %rsp
                call arc4random_buf      # arc4random_buf(buf=%rdi, len=%rsi) — void
                subq %rsp, %r15
                addq %r15, %rsp
                popq %r15
                movq %rbx, %rax          # n = len (>= 0: o chamador testa o sinal)
                popq %rbx
                ret
            """);
    }

    /**
     * A FAMÍLIA io: a leitura e o fechamento (a mesma ABI do Linux:
     * {@code read(fd, buf, len)} e {@code close(fd)}).
     */
    public static void emitIo(StringBuilder sb) {
        sb.append("""
            .globl kof_plat_read
            .type kof_plat_read, @function
            kof_plat_read:
                pushq %r15
                movq %rsp, %r15
                andq $-16, %rsp
                call read                # read(fd=%rdi, buf=%rsi, len=%rdx)
                subq %rsp, %r15
                addq %r15, %rsp
                popq %r15
                ret

            .globl kof_plat_close
            .type kof_plat_close, @function
            kof_plat_close:
                pushq %r15
                movq %rsp, %r15
                andq $-16, %rsp
                call close               # close(fd=%rdi)
                subq %rsp, %r15
                addq %r15, %rsp
                popq %r15
                ret
            """);
    }

    /**
     * A FAMÍLIA thread-id: o {@code kof_plat_thread_id} pela API pública do
     * Darwin.
     *
     * <p>No Linux a costura usa o syscall {@code gettid} (186) — que o Darwin
     * não tem. A API pública equivalente é a
     * {@code pthread_threadid_np(pthread_t, uint64_t *)}: com o primeiro
     * argumento {@code NULL} ela devolve o id da thread corrente (medido na
     * declaração do SDK: {@code int pthread_threadid_np(pthread_t, __uint64_t *)}).
     * O corpo aloca o slot do id na pilha (16 bytes, alinhado), chama a libc
     * com a pilha alinhada e devolve o id em {@code %rax} — o mesmo contrato
     * do {@code gettid} (o id de 64 bits).
     */
    public static void emitThreadId(StringBuilder sb) {
        sb.append("""
            .globl kof_plat_thread_id
            .type kof_plat_thread_id, @function
            kof_plat_thread_id:
                pushq %r15
                movq %rsp, %r15
                andq $-16, %rsp         # alinha a pilha (a libSystem usa SSE)
                subq $16, %rsp          # o slot do id (16-align, dentro do frame)
                xorq %rdi, %rdi         # thread = NULL (a thread corrente)
                movq %rsp, %rsi         # &tid
                call pthread_threadid_np
                movq (%rsp), %rax       # o id de 64 bits
                addq $16, %rsp          # volta ao frame alinhado
                subq %rsp, %r15         # devolve o delta do alinhamento
                addq %r15, %rsp
                popq %r15
                ret
            """);
    }

    /**
     * A FAMÍLIA sincronização: o {@code kof_plat_sync} (o futex) sobre a
     * API ulock do Darwin — a etapa M-5 do plano.
     *
     * <p><b>A MEDIÇÃO (fonte do XNU, não suposição):</b> o contrato da costura
     * é o do futex — {@code uaddr=%rdi}, {@code op=%rsi} (o comando nos bits
     * 0-6: 0 = WAIT, 1 = WAKE; as flags, como o {@code FUTEX_PRIVATE}, ficam
     * acima), {@code val=%rdx}. No Darwin o equivalente é a API <b>ulock</b>:
     * {@code __ulock_wait(uint32_t op, void *addr, uint64_t value, uint32_t
     * timeout_us)} e {@code __ulock_wake(uint32_t op, void *addr, uint64_t
     * wake_value)}. Lidos no {@code bsd/sys/ulock.h} e no
     * {@code bsd/kern/sys_ulock.c} do XNU: {@code UL_COMPARE_AND_WAIT = 1};
     * o wait compara {@code *addr == value} atomicamente e dorme só se for
     * igual (o mesmo do futex WAIT); o wake <b>sem</b> {@code ULF_WAKE_ALL}
     * acorda UM ({@code waitq_wakeup64_one}) e <b>com</b>
     * {@code ULF_WAKE_ALL = 0x100} acorda TODOS ({@code waitq_wakeup64_all});
     * o {@code wake_value} é ignorado no {@code COMPARE_AND_WAIT}; o timeout
     * é em MICROSEGUNDOS e 0 = sem timeout (o futex do Kof passa NULL).
     *
     * <p><b>A FRONTEIRA:</b> a ulock é uma API PRIVADA — ela NÃO está nos
     * headers do SDK (medido), mas a libSystem EXPORTA os símbolos
     * {@code ___ulock_wait}/{@code ___ulock_wake} (a lista de exportação do
     * SDK os mede, com os três underscores). A prova da fiação é o texto +
     * o assembler real; a prova de que a semântica roda é o runner do M-7.
     */
    public static void emitSync(StringBuilder sb) {
        sb.append("""
            .globl kof_plat_sync
            .type kof_plat_sync, @function
            kof_plat_sync:
                # %rdi=uaddr, %rsi=op (o comando do futex nos bits 0-6), %rdx=val
                movq %rsi, %rax
                andl $0x7f, %eax         # o comando: 0 = WAIT, 1 = WAKE
                testl %eax, %eax
                jnz .Lksync_wake
                # WAIT: __ulock_wait(UL_COMPARE_AND_WAIT=1, uaddr, val, 0)
                movq %rdx, %r8           # val
                movq %rdi, %r9           # uaddr
                movl $1, %edi            # UL_COMPARE_AND_WAIT
                movq %r9, %rsi           # addr
                movq %r8, %rdx           # value (dorme enquanto *addr == val)
                xorl %ecx, %ecx          # timeout_us = 0 = sem timeout (futex NULL)
                pushq %r15
                movq %rsp, %r15
                andq $-16, %rsp
                call ___ulock_wait
                subq %rsp, %r15
                addq %r15, %rsp
                popq %r15
                ret

            .Lksync_wake:
                # WAKE: __ulock_wake(op, uaddr, 0) — op = 1 (um) ou 0x101 (TODOS)
                movq %rdi, %r10          # uaddr (salva antes de mexer no %rdi)
                movl $1, %edi            # UL_COMPARE_AND_WAIT
                cmpq $0x7fffffff, %rdx   # futex WAKE(INT_MAX) = acordar TODOS
                jne .Lksync_wake_one
                orl $0x100, %edi         # ULF_WAKE_ALL
            .Lksync_wake_one:
                movq %r10, %rsi          # addr
                xorl %edx, %edx          # wake_value (ignorado no COMPARE_AND_WAIT)
                pushq %r15
                movq %rsp, %r15
                andq $-16, %rsp
                call ___ulock_wake
                subq %rsp, %r15
                addq %r15, %rsp
                popq %r15
                ret
            """);
    }

    /**
     * A FAMÍLIA allocator: o {@code kof_plat_heap_grow} sobre a libSystem
     * ({@code mmap}/{@code munmap}) — a etapa M-3 do plano.
     *
     * <p><b>TRÊS MEDIÇÕES DO SDK DO DARWIN (mudam o corpo):</b>
     * <ol>
     *   <li><b>A 4ª flag vai em {@code %rcx}</b> — a ABI SysV da libSystem
     *       (o syscall Linux põe a 4ª flag em {@code %r10});</li>
     *   <li><b>As flags são OUTRAS:</b> {@code MAP_PRIVATE|MAP_ANON|MAP_NORESERVE}
     *       = {@code 0x2|0x1000|0x40} = <b>{@code 0x1042}</b> no Darwin (no
     *       Linux é {@code 0x4022}: o {@code MAP_ANONYMOUS} do Linux é 0x20 e
     *       o {@code MAP_NORESERVE} é 0x4000);</li>
     *   <li><b>O erro é {@code MAP_FAILED}</b> = {@code (void*)-1} exato no
     *       Darwin (o syscall Linux devolvia {@code -errno} em
     *       {@code [-4095,-1]}).</li>
     * </ol>
     *
     * <p>A arena, o bitmap e a lógica de queda (4 GiB → … → 64 MiB) são
     * exatamente as do corpo host — só a syscall vira a chamada da libSystem.
     */
    public static void emitHeapGrow(StringBuilder sb) {
        sb.append("""
            .section .text
            .globl kof_plat_heap_grow
            .type kof_plat_heap_grow, @function
            kof_plat_heap_grow:
                # §542: arena CONTÍGUA única (bump) p/ o GC usar bitmap O(1).
                # rdi=tamanho -> rax=ptr | -1 (mesma semântica: ptr válido ou -1).
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx              # salva o tamanho (rdi é clobberado)
                movq kof_arena_base(%rip), %rax
                testq %rax, %rax
                jne .Lgrow_have
                # primeiro alloc: a MAIOR arena que couber (4 GiB primeiro; o
                # corpo host cai por potências quando a mmap falha).
                movabsq $0x100000000, %r12   # 4 GiB
            .Lgrow_try:
                # bitmap = r12/128
                movq %r12, %rsi
                shrq $7, %rsi
                movq $0, %rdi                # addr = NULL
                movq $3, %rdx                # PROT_READ|PROT_WRITE (0x1|0x2)
                movq $0x1042, %rcx           # MAP_PRIVATE|MAP_ANON|MAP_NORESERVE (DARWIN)
                movq $-1, %r8                # fd = -1
                movq $0, %r9                 # offset = 0
                # a 4a flag da libSystem vai em %rcx (a ABI SysV); no syscall
                # Linux ela ia em %r10. E o call precisa de %rsp ≡ 0.
                pushq %r15
                movq %rsp, %r15
                andq $-16, %rsp
                call mmap
                subq %rsp, %r15
                addq %r15, %rsp
                popq %r15
                cmpq $-1, %rax               # MAP_FAILED no Darwin
                je .Lgrow_smaller
                movq %rax, %r13              # bitmap ptr (temporário)
                # arena = r12
                movq $0, %rdi
                movq %r12, %rsi
                movq $3, %rdx
                movq $0x1042, %rcx
                movq $-1, %r8
                movq $0, %r9
                pushq %r15
                movq %rsp, %r15
                andq $-16, %rsp
                call mmap
                subq %rsp, %r15
                addq %r15, %rsp
                popq %r15
                cmpq $-1, %rax               # MAP_FAILED
                jne .Lgrow_arena_ok
                # arena falhou: devolve o bitmap (< cap) antes de tentar menor,
                # senão os bitmaps de cada tentativa vazam o address space.
                movq %r13, %rdi
                movq %r12, %rsi
                shrq $7, %rsi
                pushq %r15
                movq %rsp, %r15
                andq $-16, %rsp
                call munmap                 # munmap(addr=%rdi, len=%rsi)
                subq %rsp, %r15
                addq %r15, %rsp
                popq %r15
                jmp .Lgrow_smaller
            .Lgrow_arena_ok:
                movq %rax, kof_arena_base(%rip)
                movq %rax, kof_arena_ptr(%rip)
                movq %r12, _kof_arena_size(%rip)
                movq %r13, _kof_bm_ptr(%rip)
                jmp .Lgrow_have
            .Lgrow_smaller:
                shrq $1, %r12                # 4G->2G->...->64M
                movabsq $0x4000000, %rax     # 64 MiB piso
                cmpq %rax, %r12
                jae .Lgrow_try
                jmp .Lgrow_fail
            .Lgrow_have:
                movq kof_arena_ptr(%rip), %rax
                addq $15, %rax
                andq $-16, %rax              # 16-align (bit exato no bitmap)
                movq %rax, %rcx
                addq %rbx, %rcx              # fim desta alocação
                movq kof_arena_base(%rip), %rdx
                addq _kof_arena_size(%rip), %rdx
                cmpq %rdx, %rcx
                ja .Lgrow_fail
                movq %rcx, kof_arena_ptr(%rip)
                popq %r13
                popq %r12
                popq %rbx
                ret
            .Lgrow_fail:
                movq $-1, %rax
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }
}
