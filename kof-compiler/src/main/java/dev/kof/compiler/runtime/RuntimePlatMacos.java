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
}
