package dev.kof.compiler.nat;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * S5.1 (db-parity-plan, gaps-db lane, 23/09): as primitivas do wire MySQL
 * cruzado — SHA1 curto ({@code kof_sec_sha1_internal}, port do
 * {@code RuntimeDb1} x86 para a peça B62) + bswap — provadas por harness asm
 * nos DOIS alvos cross, contra o oráculo do JVM ({@code MessageDigest SHA-1}).
 *
 * <p>Por que harness e não E2E: {@code kof.security} recusa no cross por
 * {@code SECN000} (não há superfície Kof), então a prova do primitivo é direta
 * — a mesma fronteira que o S5.1 (handshake/auth) vai consumir.
 *
 * <p>Q0: sem a peça B62 o {@code ld} falha com referência indefinida a
 * {@code kof_sec_sha1_internal} (sabotagem) — prova que o harness exercita a
 * peça nova, não uma homônima.
 */
class NativeRiscvDbWireTest {

    private static boolean has(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c)
                        .redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                if (p.waitFor() != 0 || out.isEmpty()) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private void assumeRiscv() {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (S5.1)");
    }

    private void assumeAarch64() {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (S5.1)");
    }

    private static final byte[][] MESSAGES = {
            new byte[0],
            "abc".getBytes(StandardCharsets.UTF_8),
            "The quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8),
    };

    /** Oráculo: cada byte do digest (0..255) em uma linha, os 3 vetores. */
    private static String oracle() throws Exception {
        StringBuilder sb = new StringBuilder();
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        for (byte[] msg : MESSAGES) {
            for (byte b : md.digest(msg)) sb.append(b & 0xff).append('\n');
        }
        return sb.toString().trim();
    }

    private static final byte[] SEED = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PASS = "password".getBytes(StandardCharsets.US_ASCII);

    /** Oráculo: scramble (20 bytes) + os 3 casos de lenenc (valor, offset). */
    private static String wireOracle() throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] s1 = md.digest(PASS);
        byte[] s2 = md.digest(s1);
        byte[] combo = new byte[SEED.length + s2.length];
        System.arraycopy(SEED, 0, combo, 0, SEED.length);
        System.arraycopy(s2, 0, combo, SEED.length, s2.length);
        byte[] s3 = md.digest(combo);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) sb.append((s1[i] ^ s3[i]) & 0xff).append('\n');
        // offsets absolutos no buffer .Ldw_lenenc (caso 2 no byte 1, caso 3 no byte 4)
        sb.append("16\n1\n4660\n4\n1193046\n8");
        return sb.toString().trim();
    }

    /**
     * _start: scramble do par (seed, pass) + impressão dos 20 bytes; depois os
     * 3 casos de lenenc (valor, offset de retorno) sobre buscas em offsets
     * distintos do mesmo buffer.
     */
    private static String wireHarness() {
        return """
                .section .rodata
                .Ldw_seed:
                    .ascii "12345678901234567890"
                .Ldw_lenenc:
                    .byte 0x10, 0xFC, 0x34, 0x12, 0xFD, 0x56, 0x34, 0x12
                .section .data
                .align 3
                .Ldw_pass:
                    .zero 16
                    .word 8
                    .zero 4
                    .ascii "password"
                .align 3
                .Lkof_heap_root_start:
                .Lkof_heap_root_end:
                .section .text
                .globl _start
                _start:
                    andi sp, sp, -16
                    addi sp, sp, -64
                    mv   a0, sp
                    la   a1, .Ldw_seed
                    li   a2, 20
                    la   a3, .Ldw_pass
                    call kof_db_mysql_scramble
                    mv   a0, sp
                    call .Ldw_print20
                    la   s0, .Ldw_lenenc
                    # caso 1: 1 byte (0x10), offset base+0
                    mv   a0, s0
                    call kof_db_mysql_lenenc
                    mv   s1, a1
                    call kof_println_int
                    sub  a0, s1, s0
                    call kof_println_int
                    # caso 2: 0xFC + 2 bytes LE, offset base+1
                    addi a0, s0, 1
                    call kof_db_mysql_lenenc
                    mv   s1, a1
                    call kof_println_int
                    sub  a0, s1, s0
                    call kof_println_int
                    # caso 3: 0xFD + 3 bytes LE, offset base+4
                    addi a0, s0, 4
                    call kof_db_mysql_lenenc
                    mv   s1, a1
                    call kof_println_int
                    sub  a0, s1, s0
                    call kof_println_int
                    li   a0, 0
                    li   a7, 93
                    ecall
                .Ldw_print20:
                    addi sp, sp, -32
                    sd   ra, 0(sp)
                    sd   s0, 8(sp)
                    sd   s1, 16(sp)
                    sd   s2, 24(sp)
                    mv   s0, a0
                    li   s1, 0
                .Ldw_pr_loop:
                    li   s2, 20
                    bge  s1, s2, .Ldw_pr_done
                    add  t0, s0, s1
                    lbu  a0, 0(t0)
                    call kof_println_int
                    addi s1, s1, 1
                    j    .Ldw_pr_loop
                .Ldw_pr_done:
                    ld   ra, 0(sp)
                    ld   s0, 8(sp)
                    ld   s1, 16(sp)
                    ld   s2, 24(sp)
                    addi sp, sp, 32
                    ret
                .globl kof_super_table
                kof_super_table:
                    .word 0
                    .globl kof_equals_table
                    kof_equals_table:
                    .quad 0
                    .globl kof_hashcode_table
                    kof_hashcode_table:
                    .quad 0
                """;
    }

    /** _start: SHA1 dos 3 vetores + impressão dos 20 bytes de cada digest. */
    private static String harness() {
        return """
                .section .rodata
                .Lhw_abc:
                    .ascii "abc"
                .Lhw_fox:
                    .ascii "The quick brown fox jumps over the lazy dog"
                .section .data
                .align 3
                .Lkof_heap_root_start:
                .Lkof_heap_root_end:
                .section .text
                .globl _start
                _start:
                    andi sp, sp, -16
                    addi sp, sp, -64
                    mv   a0, sp
                    la   a1, .Lhw_abc
                    li   a2, 0
                    call kof_sec_sha1_internal
                    mv   a0, sp
                    call .Lhw_print20
                    mv   a0, sp
                    la   a1, .Lhw_abc
                    li   a2, 3
                    call kof_sec_sha1_internal
                    mv   a0, sp
                    call .Lhw_print20
                    mv   a0, sp
                    la   a1, .Lhw_fox
                    li   a2, 43
                    call kof_sec_sha1_internal
                    mv   a0, sp
                    call .Lhw_print20
                    li   a0, 0
                    li   a7, 93
                    ecall
                .Lhw_print20:
                    addi sp, sp, -32
                    sd   ra, 0(sp)
                    sd   s0, 8(sp)
                    sd   s1, 16(sp)
                    sd   s2, 24(sp)
                    mv   s0, a0
                    li   s1, 0
                .Lhw_pr_loop:
                    li   s2, 20
                    bge  s1, s2, .Lhw_pr_done
                    add  t0, s0, s1
                    lbu  a0, 0(t0)
                    call kof_println_int
                    addi s1, s1, 1
                    j    .Lhw_pr_loop
                .Lhw_pr_done:
                    ld   ra, 0(sp)
                    ld   s0, 8(sp)
                    ld   s1, 16(sp)
                    ld   s2, 24(sp)
                    addi sp, sp, 32
                    ret
                .globl kof_super_table
                kof_super_table:
                    .word 0
                    .globl kof_equals_table
                    kof_equals_table:
                    .quad 0
                    .globl kof_hashcode_table
                    kof_hashcode_table:
                    .quad 0
                """;
    }

    private String runCapture(String... cmd) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        try {
            int ec = p.waitFor();
            assertEquals(0, ec, "comando falhou (" + ec + "): " + String.join(" ", cmd) + "\n" + out);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        return out;
    }

    private String[] runAllowFail(String... cmd) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        try {
            return new String[]{out, String.valueOf(p.waitFor())};
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    private String buildRun(String arch, Path tempDir, String name, String asmText) throws IOException {
        Path asm = tempDir.resolve(name + ".s");
        Files.writeString(asm, asmText);
        Path obj = tempDir.resolve(name + ".o");
        Path bin = tempDir.resolve(name);
        String as = arch.equals("riscv64") ? "riscv64-linux-gnu-as" : "aarch64-linux-gnu-as";
        String ld = arch.equals("riscv64") ? "riscv64-linux-gnu-ld" : "aarch64-linux-gnu-ld";
        if (arch.equals("riscv64")) {
            runCapture(as, "-mno-relax", "-o", obj.toString(), asm.toString());
        } else {
            runCapture(as, "-o", obj.toString(), asm.toString());
        }
        // link estático: o harness não usa libc (só o runtime puro).
        runCapture(ld, "--no-relax", "-o", bin.toString(), obj.toString());
        bin.toFile().setExecutable(true);
        return QemuRun.runExpect0("qemu-" + arch, bin.toString());
    }

    /** Variante que ESPERA o throw nao-capturado (ec 1 + mensagem) — §523:
     *  o handshake contra DB inexistente/auth rejeitada lanca em vez de -1. */
    private String buildRunExpectThrow(String arch, Path tempDir, String name, String asmText) throws IOException {
        Path asm = tempDir.resolve(name + ".s");
        Files.writeString(asm, asmText);
        Path obj = tempDir.resolve(name + ".o");
        Path bin = tempDir.resolve(name);
        String as = arch.equals("riscv64") ? "riscv64-linux-gnu-as" : "aarch64-linux-gnu-as";
        String ld = arch.equals("riscv64") ? "riscv64-linux-gnu-ld" : "aarch64-linux-gnu-ld";
        if (arch.equals("riscv64")) {
            runCapture(as, "-mno-relax", "-o", obj.toString(), asm.toString());
        } else {
            runCapture(as, "-o", obj.toString(), asm.toString());
        }
        runCapture(ld, "--no-relax", "-o", bin.toString(), obj.toString());
        bin.toFile().setExecutable(true);
        QemuRun.Exit run = QemuRun.run("qemu-" + arch, bin.toString());
        assertEquals(1, run.exitCode(),
                "qemu " + arch + " deveria abortar com o throw (ec 1): " + run.output());
        return run.output();
    }

    @Test
    void sha1MatchesJvmOracleOnRiscv64(@TempDir Path tempDir) throws Exception {
        assumeRiscv();
        String harness = harness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String out = buildRun("riscv64", tempDir, "sha1rv", harness + "\n" + runtime);
        assertEquals(oracle(), out, "SHA1 riscv64 diverge do oráculo JVM");
    }

    @Test
    void sha1MatchesJvmOracleOnAarch64(@TempDir Path tempDir) throws Exception {
        assumeAarch64();
        String harness = harness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String riscv = harness + "\n" + runtime;
        StringBuilder arm = new StringBuilder();
        for (String line : riscv.split("\n", -1)) {
            for (String t : NativeAarch64Translator.translateRiscvToAarch64(line)) arm.append(t).append('\n');
        }
        String out = buildRun("aarch64", tempDir, "sha1aa", arm.toString());
        assertEquals(oracle(), out, "SHA1 aarch64 diverge do oráculo JVM");
    }

    private static final String GREETING_SEED = "abcdefghABCDEFGH1234";

    /** Oráculo: status 1 + os 20 bytes do seed; depois status 0 no pacote ruim. */
    private static String greetingOracle() {
        StringBuilder sb = new StringBuilder("1");
        for (byte b : GREETING_SEED.getBytes(StandardCharsets.US_ASCII)) sb.append('\n').append(b & 0xff);
        sb.append("\n0");
        return sb.toString();
    }

    /** _start: parse do greeting sintético (seed 20B) + pacote com protocolo ruim. */
    private static String greetingHarness() {
        return """
                .section .rodata
                .Lgr_pkt:
                    .byte 0x4A, 0x00, 0x00, 0x00
                    .byte 0x0A
                    .ascii "5.5.5-10.3.39-MariaDB"
                    .byte 0
                    .byte 0x2A, 0x00, 0x00, 0x00
                    .ascii "abcdefgh"
                    .byte 0
                    .byte 0x00, 0x00
                    .byte 0x21
                    .byte 0x02, 0x00
                    .byte 0x00, 0x00
                    .byte 21
                    .zero 10
                    .ascii "ABCDEFGH1234"
                    .byte 0
                .Lgr_bad:
                    .byte 0x05, 0x00, 0x00, 0x00
                    .byte 0x0B
                    .zero 8
                .section .data
                .align 3
                .Lkof_heap_root_start:
                .Lkof_heap_root_end:
                .section .text
                .globl _start
                _start:
                    andi sp, sp, -16
                    addi sp, sp, -32
                    mv   s0, sp
                    li   t0, 0
                .Lgr_zero:
                    li   t1, 20
                    bge  t0, t1, .Lgr_zero_done
                    add  t2, s0, t0
                    sb   zero, 0(t2)
                    addi t0, t0, 1
                    j    .Lgr_zero
                .Lgr_zero_done:
                    la   a0, .Lgr_pkt
                    mv   a1, s0
                    call kof_db_mysql_parse_greeting
                    call kof_println_int
                    mv   a0, s0
                    call .Lgr_print20
                    la   a0, .Lgr_bad
                    mv   a1, s0
                    call kof_db_mysql_parse_greeting
                    call kof_println_int
                    li   a0, 0
                    li   a7, 93
                    ecall
                .Lgr_print20:
                    addi sp, sp, -32
                    sd   ra, 0(sp)
                    sd   s0, 8(sp)
                    sd   s1, 16(sp)
                    sd   s2, 24(sp)
                    mv   s0, a0
                    li   s1, 0
                .Lgr_pr_loop:
                    li   s2, 20
                    bge  s1, s2, .Lgr_pr_done
                    add  t0, s0, s1
                    lbu  a0, 0(t0)
                    call kof_println_int
                    addi s1, s1, 1
                    j    .Lgr_pr_loop
                .Lgr_pr_done:
                    ld   ra, 0(sp)
                    ld   s0, 8(sp)
                    ld   s1, 16(sp)
                    ld   s2, 24(sp)
                    addi sp, sp, 32
                    ret
                .globl kof_super_table
                kof_super_table:
                    .word 0
                    .globl kof_equals_table
                    kof_equals_table:
                    .quad 0
                    .globl kof_hashcode_table
                    kof_hashcode_table:
                    .quad 0
                """;
    }

    private static final byte[] AUTH_SCRAMBLE = new byte[20];
    static {
        for (int i = 0; i < 20; i++) AUTH_SCRAMBLE[i] = (byte) (0x10 + i);
    }

    /** Payload do handshake response, espelhando o layout do RuntimeDb3 x86. */
    private static byte[] authPayload(int passLen) {
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        o.write(0x0B);
        o.write(0x82);
        o.write(0x08);
        o.write(0x00);
        o.write(0x00);
        o.write(0x00);
        o.write(0x00);
        o.write(0x01);
        o.write(0x21);
        for (int i = 0; i < 23; i++) o.write(0);
        for (byte b : "root".getBytes(StandardCharsets.US_ASCII)) o.write(b);
        o.write(0);
        if (passLen > 0) {
            o.write(20);
            o.writeBytes(AUTH_SCRAMBLE);
        } else {
            o.write(0);
        }
        for (byte b : "kof".getBytes(StandardCharsets.US_ASCII)) o.write(b);
        o.write(0);
        for (byte b : "mysql_native_password".getBytes(StandardCharsets.US_ASCII)) o.write(b);
        o.write(0);
        return o.toByteArray();
    }

    private static String authOracle() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (int passLen : new int[]{20, 0}) {
            byte[] p = authPayload(passLen);
            if (!first) sb.append('\n');
            first = false;
            sb.append(p.length);
            for (byte b : p) sb.append('\n').append(b & 0xff);
        }
        return sb.toString();
    }

    private static String authHarness() {
        return """
                .section .rodata
                .Law_scramble:
                    .byte 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17
                    .byte 0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F
                    .byte 0x20, 0x21, 0x22, 0x23
                .section .data
                .align 3
                .Law_user:
                    .zero 16
                    .word 4
                    .zero 4
                    .ascii "root"
                .align 3
                .Law_db:
                    .zero 16
                    .word 3
                    .zero 4
                    .ascii "kof"
                .align 3
                .Lkof_heap_root_start:
                .Lkof_heap_root_end:
                .section .text
                .globl _start
                _start:
                    andi sp, sp, -16
                    addi sp, sp, -528
                    mv   s0, sp
                    # caso passLen=20
                    mv   a0, s0
                    la   a1, .Law_scramble
                    la   a2, .Law_user
                    la   a3, .Law_db
                    li   a4, 20
                    call kof_db_mysql_build_auth_response
                    mv   s1, a0
                    call kof_println_int
                    li   s2, 0
                .Law_pr1:
                    bge  s2, s1, .Law_pr1_done
                    add  t0, s0, s2
                    addi t0, t0, 4
                    lbu  a0, 0(t0)
                    call kof_println_int
                    addi s2, s2, 1
                    j    .Law_pr1
                .Law_pr1_done:
                    # caso passLen=0
                    mv   a0, s0
                    la   a1, .Law_scramble
                    la   a2, .Law_user
                    la   a3, .Law_db
                    li   a4, 0
                    call kof_db_mysql_build_auth_response
                    mv   s1, a0
                    call kof_println_int
                    li   s2, 0
                .Law_pr2:
                    bge  s2, s1, .Law_pr2_done
                    add  t0, s0, s2
                    addi t0, t0, 4
                    lbu  a0, 0(t0)
                    call kof_println_int
                    addi s2, s2, 1
                    j    .Law_pr2
                .Law_pr2_done:
                    li   a0, 0
                    li   a7, 93
                    ecall
                .globl kof_super_table
                kof_super_table:
                    .word 0
                    .globl kof_equals_table
                    kof_equals_table:
                    .quad 0
                    .globl kof_hashcode_table
                    kof_hashcode_table:
                    .quad 0
                """;
    }

    @Test
    void authResponseMatchesOracleOnRiscv64(@TempDir Path tempDir) throws Exception {
        assumeRiscv();
        String harness = authHarness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String out = buildRun("riscv64", tempDir, "auth_rv", harness + "\n" + runtime);
        assertEquals(authOracle(), out, "auth response riscv64 diverge do oráculo");
    }

    @Test
    void authResponseMatchesOracleOnAarch64(@TempDir Path tempDir) throws Exception {
        assumeAarch64();
        String harness = authHarness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String riscv = harness + "\n" + runtime;
        StringBuilder arm = new StringBuilder();
        for (String line : riscv.split("\n", -1)) {
            for (String t : NativeAarch64Translator.translateRiscvToAarch64(line)) arm.append(t).append('\n');
        }
        String out = buildRun("aarch64", tempDir, "auth_aa", arm.toString());
        assertEquals(authOracle(), out, "auth response aarch64 diverge do oráculo");
    }

    @Test
    void withoutAuthResponsePieceLinkFailsSabotage(@TempDir Path tempDir) throws IOException {
        assumeRiscv();
        String harness = authHarness();
        Set<Integer> keep = new LinkedHashSet<>(RiscvSlices.keepForProgramText(harness));
        int b65 = -1;
        for (RiscvSlices.Piece p : RiscvSlices.pieces()) {
            if ("RISCV_RUNTIME_ASM_B_65".equals(p.field())) b65 = p.index();
        }
        assertTrue(b65 >= 0, "peça B65 (auth response) não encontrada no inventário");
        assertTrue(keep.remove(b65), "B65 deveria estar no keep do harness de auth response");
        String runtime = RiscvSlices.renderSubset(keep);
        Path asm = tempDir.resolve("sab_authr.s");
        Files.writeString(asm, harness + "\n" + runtime);
        Path obj = tempDir.resolve("sab_authr.o");
        Path bin = tempDir.resolve("sab_authr");
        runCapture("riscv64-linux-gnu-as", "-mno-relax", "-o", obj.toString(), asm.toString());
        String[] r = runAllowFail("riscv64-linux-gnu-ld", "--no-relax", "-o", bin.toString(), obj.toString());
        assertNotEquals("0", r[1], "sem a B65 o link deveria falhar (undefined kof_db_mysql_build_auth_response); saída: " + r[0]);
        assertTrue(r[0].contains("kof_db_mysql_build_auth_response"),
                "a falha deve citar kof_db_mysql_build_auth_response: " + r[0]);
    }

    private static int mysqlPort() {
        return Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "13306"));
    }

    /** Pula se o MariaDB real não estiver acessível (mesma porta de KofDbE2ETest). */
    private void assumeMaria() {
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress("127.0.0.1", mysqlPort()), 500);
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "MariaDB em 127.0.0.1:" + mysqlPort() + " ausente — pulando (S5.1 real)");
        }
    }

    /** _start: handshake real com credenciais corretas (0) e senha errada (-1). */
    private static String mysqlHandshakeHarness() {
        int port = mysqlPort();
        String hi = String.format("0x%02X", (port >> 8) & 0xff);
        String lo = String.format("0x%02X", port & 0xff);
        return """
                .section .data
                .align 3
                .Lmh_user:
                    .zero 16
                    .word 4
                    .zero 4
                    .ascii "root"
                .align 3
                .Lmh_pass:
                    .zero 16
                    .word 7
                    .zero 4
                    .ascii "kofpass"
                .align 3
                .Lmh_baddb:
                    .zero 16
                    .word 18
                    .zero 4
                    .ascii "kof_no_such_db_xyz"
                .align 3
                .Lmh_db:
                    .zero 16
                    .word 4
                    .zero 4
                    .ascii "test"
                .align 3
                .Lmh_addr:
                    .byte 2, 0, PORT_HI, PORT_LO, 127, 0, 0, 1
                    .zero 8
                .align 3
                .Lkof_heap_root_start:
                .Lkof_heap_root_end:
                .section .text
                .globl _start
                _start:
                    andi sp, sp, -16
                    addi sp, sp, -64
                    # conexao 1: credenciais corretas
                    li   a0, 2
                    li   a1, 1
                    li   a2, 0
                    call kof_plat_net_socket
                    mv   s0, a0
                    mv   a0, s0
                    la   a1, .Lmh_addr
                    li   a2, 16
                    call kof_plat_net_connect
                    mv   a0, s0
                    la   a1, .Lmh_user
                    la   a2, .Lmh_pass
                    la   a3, .Lmh_db
                    call kof_db_mysql_handshake
                    call kof_println_int
                    mv   a0, s0
                    call kof_plat_close
                    # conexao 2: banco inexistente -> ERR (1049) do servidor
                    li   a0, 2
                    li   a1, 1
                    li   a2, 0
                    call kof_plat_net_socket
                    mv   s0, a0
                    mv   a0, s0
                    la   a1, .Lmh_addr
                    li   a2, 16
                    call kof_plat_net_connect
                    mv   a0, s0
                    la   a1, .Lmh_user
                    la   a2, .Lmh_pass
                    la   a3, .Lmh_baddb
                    call kof_db_mysql_handshake
                    call kof_println_int
                    mv   a0, s0
                    call kof_plat_close
                    li   a0, 0
                    li   a7, 93
                    ecall
                .globl kof_super_table
                kof_super_table:
                    .word 0
                    .globl kof_equals_table
                    kof_equals_table:
                    .quad 0
                    .globl kof_hashcode_table
                    kof_hashcode_table:
                    .quad 0
                """.replace("PORT_HI", hi).replace("PORT_LO", lo);
    }

    @Test
    void handshakeAgainstRealMariaDbOnRiscv64(@TempDir Path tempDir) throws Exception {
        assumeRiscv();
        assumeMaria();
        String harness = mysqlHandshakeHarness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        // §523: creds ok dao 0; banco inexistente LANCA `mysql: ...` (era -1).
        String out = buildRunExpectThrow("riscv64", tempDir, "hs_rv", harness + "\n" + runtime);
        assertEquals("0\nmysql: Unknown database 'kof_no_such_db_xyz'", out,
                "handshake riscv64: creds ok 0, banco inexistente lanca (§523)");
    }

    @Test
    void handshakeAgainstRealMariaDbOnAarch64(@TempDir Path tempDir) throws Exception {
        assumeAarch64();
        assumeMaria();
        String harness = mysqlHandshakeHarness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String riscv = harness + "\n" + runtime;
        StringBuilder arm = new StringBuilder();
        for (String line : riscv.split("\n", -1)) {
            for (String t : NativeAarch64Translator.translateRiscvToAarch64(line)) arm.append(t).append('\n');
        }
        String out = buildRunExpectThrow("aarch64", tempDir, "hs_aa", arm.toString());
        assertEquals("0\nmysql: Unknown database 'kof_no_such_db_xyz'", out,
                "handshake aarch64: creds ok 0, banco inexistente lanca (§523)");
    }

    @Test
    void withoutHandshakePieceLinkFailsSabotage(@TempDir Path tempDir) throws IOException {
        assumeRiscv();
        String harness = mysqlHandshakeHarness();
        Set<Integer> keep = new LinkedHashSet<>(RiscvSlices.keepForProgramText(harness));
        int b66 = -1;
        for (RiscvSlices.Piece p : RiscvSlices.pieces()) {
            if ("RISCV_RUNTIME_ASM_B_66".equals(p.field())) b66 = p.index();
        }
        assertTrue(b66 >= 0, "peça B66 (handshake) não encontrada no inventário");
        assertTrue(keep.remove(b66), "B66 deveria estar no keep do harness de handshake");
        String runtime = RiscvSlices.renderSubset(keep);
        Path asm = tempDir.resolve("sab_hs.s");
        Files.writeString(asm, harness + "\n" + runtime);
        Path obj = tempDir.resolve("sab_hs.o");
        Path bin = tempDir.resolve("sab_hs");
        runCapture("riscv64-linux-gnu-as", "-mno-relax", "-o", obj.toString(), asm.toString());
        String[] r = runAllowFail("riscv64-linux-gnu-ld", "--no-relax", "-o", bin.toString(), obj.toString());
        assertNotEquals("0", r[1], "sem a B66 o link deveria falhar (undefined kof_db_mysql_handshake); saída: " + r[0]);
        assertTrue(r[0].contains("kof_db_mysql_handshake"),
                "a falha deve citar kof_db_mysql_handshake: " + r[0]);
    }

    /**
     * S5.2 (db-parity-plan, gaps-db lane, 23/09): envia COM_QUERY texto e le o
     * 1o pacote de resposta para classificar — {@code >=1}=column count de um
     * resultset, {@code 0x00}=OK, {@code 0xFF}=ERR. Cada comando usa conexao
     * propria (SELECT gera varios pacotes; uma conexao por comando evita ler
     * sobras do resultset anterior, que e o escopo do B67 — so a 1a resposta).
     */
    private static String mysqlCommandHarness() {
        int port = mysqlPort();
        String hi = String.format("0x%02X", (port >> 8) & 0xff);
        String lo = String.format("0x%02X", port & 0xff);
        return """
                .section .data
                .align 3
                .Lmq_user:
                    .zero 16
                    .word 4
                    .zero 4
                    .ascii "root"
                .align 3
                .Lmq_pass:
                    .zero 16
                    .word 7
                    .zero 4
                    .ascii "kofpass"
                .align 3
                .Lmq_db:
                    .zero 16
                    .word 4
                    .zero 4
                    .ascii "test"
                .align 3
                .Lmq_sel:
                    .zero 16
                    .word 8
                    .zero 4
                    .ascii "SELECT 1"
                .align 3
                .Lmq_set:
                    .zero 16
                    .word 12
                    .zero 4
                    .ascii "SET @kof_x=1"
                .align 3
                .Lmq_bad:
                    .zero 16
                    .word 7
                    .zero 4
                    .ascii "SELEC 1"
                .align 3
                .Lmq_addr:
                    .byte 2, 0, PORT_HI, PORT_LO, 127, 0, 0, 1
                    .zero 8
                .align 3
                .Lkof_heap_root_start:
                .Lkof_heap_root_end:
                .section .text
                .globl _start
                _start:
                    andi sp, sp, -16
                    li   t6, 4224
                    sub  sp, sp, t6
                    addi s2, sp, 128          # buffer de response (4096)
                    la   a0, .Lmq_sel
                    call .Lmq_one
                    call kof_println_int
                    la   a0, .Lmq_set
                    call .Lmq_one
                    call kof_println_int
                    la   a0, .Lmq_bad
                    call .Lmq_one
                    call kof_println_int
                    li   a0, 0
                    li   a7, 93
                    ecall
                # a0 = sql KofString -> a0 = byte de classificacao | -1
                .Lmq_one:
                    addi sp, sp, -64
                    sd   ra, 0(sp)
                    sd   s0, 8(sp)
                    sd   s1, 16(sp)
                    sd   s3, 24(sp)
                    mv   s1, a0
                    li   a0, 2
                    li   a1, 1
                    li   a2, 0
                    call kof_plat_net_socket
                    mv   s0, a0
                    mv   a0, s0
                    la   a1, .Lmq_addr
                    li   a2, 16
                    call kof_plat_net_connect
                    mv   a0, s0
                    la   a1, .Lmq_user
                    la   a2, .Lmq_pass
                    la   a3, .Lmq_db
                    call kof_db_mysql_handshake
                    bnez a0, .Lmq_one_fail
                    mv   a0, s0
                    mv   a1, s1
                    mv   a2, s2
                    li   a3, 4096
                    call kof_db_mysql_command
                    blt  a0, zero, .Lmq_one_fail
                    lbu  s3, 0(a1)
                    j    .Lmq_one_close
                .Lmq_one_fail:
                    li   s3, -1
                .Lmq_one_close:
                    mv   a0, s0
                    call kof_plat_close
                    mv   a0, s3
                    ld   ra, 0(sp)
                    ld   s0, 8(sp)
                    ld   s1, 16(sp)
                    ld   s3, 24(sp)
                    addi sp, sp, 64
                    ret
                .globl kof_super_table
                kof_super_table:
                    .word 0
                    .globl kof_equals_table
                    kof_equals_table:
                    .quad 0
                    .globl kof_hashcode_table
                    kof_hashcode_table:
                    .quad 0
                """.replace("PORT_HI", hi).replace("PORT_LO", lo);
    }

    @Test
    void commandClassifiesResponseAgainstRealMariaDbOnRiscv64(@TempDir Path tempDir) throws Exception {
        assumeRiscv();
        assumeMaria();
        String harness = mysqlCommandHarness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String out = buildRun("riscv64", tempDir, "cmd_rv", harness + "\n" + runtime);
        assertEquals("1\n0\n255", out, "COM_QUERY riscv64: SELECT=1, SET=0, SQL ruim=255");
    }

    @Test
    void commandClassifiesResponseAgainstRealMariaDbOnAarch64(@TempDir Path tempDir) throws Exception {
        assumeAarch64();
        assumeMaria();
        String harness = mysqlCommandHarness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String riscv = harness + "\n" + runtime;
        StringBuilder arm = new StringBuilder();
        for (String line : riscv.split("\n", -1)) {
            for (String t : NativeAarch64Translator.translateRiscvToAarch64(line)) arm.append(t).append('\n');
        }
        String out = buildRun("aarch64", tempDir, "cmd_aa", arm.toString());
        assertEquals("1\n0\n255", out, "COM_QUERY aarch64: SELECT=1, SET=0, SQL ruim=255");
    }

    @Test
    void withoutCommandPieceLinkFailsSabotage(@TempDir Path tempDir) throws IOException {
        assumeRiscv();
        String harness = mysqlCommandHarness();
        Set<Integer> keep = new LinkedHashSet<>(RiscvSlices.keepForProgramText(harness));
        int b67 = -1;
        for (RiscvSlices.Piece p : RiscvSlices.pieces()) {
            if ("RISCV_RUNTIME_ASM_B_67".equals(p.field())) b67 = p.index();
        }
        assertTrue(b67 >= 0, "peça B67 (COM_QUERY) não encontrada no inventário");
        assertTrue(keep.remove(b67), "B67 deveria estar no keep do harness de COM_QUERY");
        String runtime = RiscvSlices.renderSubset(keep);
        Path asm = tempDir.resolve("sab_cmd.s");
        Files.writeString(asm, harness + "\n" + runtime);
        Path obj = tempDir.resolve("sab_cmd.o");
        Path bin = tempDir.resolve("sab_cmd");
        runCapture("riscv64-linux-gnu-as", "-mno-relax", "-o", obj.toString(), asm.toString());
        String[] r = runAllowFail("riscv64-linux-gnu-ld", "--no-relax", "-o", bin.toString(), obj.toString());
        assertNotEquals("0", r[1], "sem a B67 o link deveria falhar (undefined kof_db_mysql_command); saída: " + r[0]);
        assertTrue(r[0].contains("kof_db_mysql_command"),
                "a falha deve citar kof_db_mysql_command: " + r[0]);
    }

    /**
     * S5.2 (db-parity-plan, gaps-db lane, 23/09): parseia o cabecalho do
     * resultset texto — ncols + a PRIMEIRA linha (payload cru, celulas lenenc).
     * Prova contra o MariaDB real: `SELECT 1` → 1 coluna, linha [0x01,'1'];
     * `SELECT 1,'ab'` → 2 colunas, linha [0x01,'1',0x02,'a','b'].
     */
    private static String mysqlResultsetHarness() {
        int port = mysqlPort();
        String hi = String.format("0x%02X", (port >> 8) & 0xff);
        String lo = String.format("0x%02X", port & 0xff);
        return """
                .section .data
                .align 3
                .Lqt_user:
                    .zero 16
                    .word 4
                    .zero 4
                    .ascii "root"
                .align 3
                .Lqt_pass:
                    .zero 16
                    .word 7
                    .zero 4
                    .ascii "kofpass"
                .align 3
                .Lqt_db:
                    .zero 16
                    .word 4
                    .zero 4
                    .ascii "test"
                .align 3
                .Lqt_sel1:
                    .zero 16
                    .word 8
                    .zero 4
                    .ascii "SELECT 1"
                .align 3
                .Lqt_sel2:
                    .zero 16
                    .word 14
                    .zero 4
                    .ascii "SELECT 1,'ab'"
                .align 3
                .Lqt_addr:
                    .byte 2, 0, PORT_HI, PORT_LO, 127, 0, 0, 1
                    .zero 8
                .align 3
                .Lkof_heap_root_start:
                .Lkof_heap_root_end:
                .section .text
                .globl _start
                _start:
                    andi sp, sp, -16
                    addi sp, sp, -64
                    la   a0, .Lqt_sel1
                    call .Lqt_one
                    la   a0, .Lqt_sel2
                    call .Lqt_one
                    li   a0, 0
                    li   a7, 93
                    ecall
                # a0 = sql -> imprime ncols, rowlen e os bytes da 1a linha
                .Lqt_one:
                    addi sp, sp, -96
                    sd   ra, 0(sp)
                    sd   s0, 8(sp)
                    sd   s1, 16(sp)
                    sd   s2, 24(sp)
                    sd   s3, 32(sp)
                    sd   s4, 40(sp)
                    sd   s5, 48(sp)
                    mv   s1, a0
                    li   a0, 2
                    li   a1, 1
                    li   a2, 0
                    call kof_plat_net_socket
                    mv   s0, a0
                    mv   a0, s0
                    la   a1, .Lqt_addr
                    li   a2, 16
                    call kof_plat_net_connect
                    mv   a0, s0
                    la   a1, .Lqt_user
                    la   a2, .Lqt_pass
                    la   a3, .Lqt_db
                    call kof_db_mysql_handshake
                    bnez a0, .Lqt_fail
                    mv   a0, s0
                    mv   a1, s1
                    call kof_db_mysql_query_text
                    blt  a0, zero, .Lqt_fail
                    mv   s2, a0
                    mv   s3, a1
                    mv   s4, a2
                    mv   a0, s2
                    call kof_println_int
                    mv   a0, s4
                    call kof_println_int
                    li   s5, 0
                .Lqt_bytes:
                    bge  s5, s4, .Lqt_close
                    add  t0, s3, s5
                    lbu  a0, 0(t0)
                    call kof_println_int
                    addi s5, s5, 1
                    j    .Lqt_bytes
                .Lqt_fail:
                    li   a0, -1
                    call kof_println_int
                .Lqt_close:
                    mv   a0, s0
                    call kof_plat_close
                    ld   ra, 0(sp)
                    ld   s0, 8(sp)
                    ld   s1, 16(sp)
                    ld   s2, 24(sp)
                    ld   s3, 32(sp)
                    ld   s4, 40(sp)
                    ld   s5, 48(sp)
                    addi sp, sp, 96
                    ret
                .globl kof_super_table
                kof_super_table:
                    .word 0
                    .globl kof_equals_table
                    kof_equals_table:
                    .quad 0
                    .globl kof_hashcode_table
                    kof_hashcode_table:
                    .quad 0
                """.replace("PORT_HI", hi).replace("PORT_LO", lo);
    }

    @Test
    void resultsetHeaderAgainstRealMariaDbOnRiscv64(@TempDir Path tempDir) throws Exception {
        assumeRiscv();
        assumeMaria();
        String harness = mysqlResultsetHarness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String out = buildRun("riscv64", tempDir, "rs_rv", harness + "\n" + runtime);
        assertEquals("1\n2\n1\n49\n2\n5\n1\n49\n2\n97\n98", out,
                "resultset riscv64: SELECT 1 -> 1 col/[01 31]; SELECT 1,'ab' -> 2 col/[01 31 02 61 62]");
    }

    @Test
    void resultsetHeaderAgainstRealMariaDbOnAarch64(@TempDir Path tempDir) throws Exception {
        assumeAarch64();
        assumeMaria();
        String harness = mysqlResultsetHarness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String riscv = harness + "\n" + runtime;
        StringBuilder arm = new StringBuilder();
        for (String line : riscv.split("\n", -1)) {
            for (String t : NativeAarch64Translator.translateRiscvToAarch64(line)) arm.append(t).append('\n');
        }
        String out = buildRun("aarch64", tempDir, "rs_aa", arm.toString());
        assertEquals("1\n2\n1\n49\n2\n5\n1\n49\n2\n97\n98", out,
                "resultset aarch64: SELECT 1 -> 1 col/[01 31]; SELECT 1,'ab' -> 2 col/[01 31 02 61 62]");
    }

    @Test
    void withoutReaderPieceLinkFailsSabotage(@TempDir Path tempDir) throws IOException {
        assumeRiscv();
        String harness = mysqlResultsetHarness();
        Set<Integer> keep = new LinkedHashSet<>(RiscvSlices.keepForProgramText(harness));
        int b68 = -1;
        for (RiscvSlices.Piece p : RiscvSlices.pieces()) {
            if ("RISCV_RUNTIME_ASM_B_68".equals(p.field())) b68 = p.index();
        }
        assertTrue(b68 >= 0, "peça B68 (reader de pacotes) não encontrada no inventário");
        assertTrue(keep.remove(b68), "B68 deveria estar no keep do harness de resultset");
        String runtime = RiscvSlices.renderSubset(keep);
        Path asm = tempDir.resolve("sab_rd.s");
        Files.writeString(asm, harness + "\n" + runtime);
        Path obj = tempDir.resolve("sab_rd.o");
        Path bin = tempDir.resolve("sab_rd");
        runCapture("riscv64-linux-gnu-as", "-mno-relax", "-o", obj.toString(), asm.toString());
        String[] r = runAllowFail("riscv64-linux-gnu-ld", "--no-relax", "-o", bin.toString(), obj.toString());
        assertNotEquals("0", r[1], "sem a B68 o link deveria falhar (undefined kof_db_mysql_next); saída: " + r[0]);
        assertTrue(r[0].contains("kof_db_mysql_next"),
                "a falha deve citar kof_db_mysql_next: " + r[0]);
    }

    @Test
    void withoutResultsetsPieceLinkFailsSabotage(@TempDir Path tempDir) throws IOException {
        assumeRiscv();
        String harness = mysqlResultsetHarness();
        Set<Integer> keep = new LinkedHashSet<>(RiscvSlices.keepForProgramText(harness));
        int b69 = -1;
        for (RiscvSlices.Piece p : RiscvSlices.pieces()) {
            if ("RISCV_RUNTIME_ASM_B_69".equals(p.field())) b69 = p.index();
        }
        assertTrue(b69 >= 0, "peça B69 (resultset texto) não encontrada no inventário");
        assertTrue(keep.remove(b69), "B69 deveria estar no keep do harness de resultset");
        String runtime = RiscvSlices.renderSubset(keep);
        Path asm = tempDir.resolve("sab_rs.s");
        Files.writeString(asm, harness + "\n" + runtime);
        Path obj = tempDir.resolve("sab_rs.o");
        Path bin = tempDir.resolve("sab_rs");
        runCapture("riscv64-linux-gnu-as", "-mno-relax", "-o", obj.toString(), asm.toString());
        String[] r = runAllowFail("riscv64-linux-gnu-ld", "--no-relax", "-o", bin.toString(), obj.toString());
        assertNotEquals("0", r[1], "sem a B69 o link deveria falhar (undefined kof_db_mysql_query_text); saída: " + r[0]);
        assertTrue(r[0].contains("kof_db_mysql_query_text"),
                "a falha deve citar kof_db_mysql_query_text: " + r[0]);
    }

    /**
     * S5.2 (db-parity-plan, gaps-db lane, 23/09): query texto COMPLETA —
     * {@code kof_db_mysql_query} itera todas as linhas e devolve
     * {@code List<KofString>} de registros JSON, seguindo o contrato JVM
     * (`kof_db_row_to_json`: digits->numero cru, NULL->literal `null`,
     * string vazia->`""`, aspas/escape via json_encode_string). Prova contra o MariaDB real: 1 coluna, 2 colunas,
     * 2 linhas (UNION) e NULL+escape.
     */
    private static String mysqlQueryAllHarness() {
        int port = mysqlPort();
        String hi = String.format("0x%02X", (port >> 8) & 0xff);
        String lo = String.format("0x%02X", port & 0xff);
        return """
                .section .data
                .align 3
                .Lfq_user:
                    .zero 16
                    .word 4
                    .zero 4
                    .ascii "root"
                .align 3
                .Lfq_pass:
                    .zero 16
                    .word 7
                    .zero 4
                    .ascii "kofpass"
                .align 3
                .Lfq_db:
                    .zero 16
                    .word 4
                    .zero 4
                    .ascii "test"
                .align 3
                .Lfq_q1:
                    .zero 16
                    .word 8
                    .zero 4
                    .ascii "SELECT 1"
                .align 3
                .Lfq_q2:
                    .zero 16
                    .word 13
                    .zero 4
                    .ascii "SELECT 1,'ab'"
                .align 3
                .Lfq_q3:
                    .zero 16
                    .word 27
                    .zero 4
                    .ascii "SELECT 1 UNION ALL SELECT 2"
                .align 3
                .Lfq_q4:
                    .zero 16
                    .word 27
                    .zero 4
                    .ascii "SELECT NULL AS n,'a\\042b' AS s"
                .align 3
                .Lfq_addr:
                    .byte 2, 0, PORT_HI, PORT_LO, 127, 0, 0, 1
                    .zero 8
                .align 3
                .Lkof_heap_root_start:
                .Lkof_heap_root_end:
                .section .text
                .globl _start
                _start:
                    andi sp, sp, -16
                    addi sp, sp, -64
                    la   a0, .Lfq_q1
                    call .Lfq_one
                    la   a0, .Lfq_q2
                    call .Lfq_one
                    la   a0, .Lfq_q3
                    call .Lfq_one
                    la   a0, .Lfq_q4
                    call .Lfq_one
                    li   a0, 0
                    li   a7, 93
                    ecall
                # a0 = sql -> imprime size + cada registro JSON da linha
                .Lfq_one:
                    addi sp, sp, -112
                    sd   ra, 0(sp)
                    sd   s0, 8(sp)
                    sd   s1, 16(sp)
                    sd   s2, 24(sp)
                    sd   s3, 32(sp)
                    sd   s4, 40(sp)
                    mv   s1, a0
                    li   a0, 2
                    li   a1, 1
                    li   a2, 0
                    call kof_plat_net_socket
                    mv   s0, a0
                    mv   a0, s0
                    la   a1, .Lfq_addr
                    li   a2, 16
                    call kof_plat_net_connect
                    mv   a0, s0
                    la   a1, .Lfq_user
                    la   a2, .Lfq_pass
                    la   a3, .Lfq_db
                    call kof_db_mysql_handshake
                    bnez a0, .Lfq_fail
                    mv   a0, s0
                    mv   a1, s1
                    call kof_db_mysql_query
                    mv   s2, a0
                    mv   a0, s2
                    call kof_list_size
                    mv   s4, a0
                    call kof_println_int
                    li   s3, 0
                .Lfq_loop:
                    bge  s3, s4, .Lfq_close
                    mv   a0, s2
                    mv   a1, s3
                    call kof_list_get
                    call kof_println_string
                    addi s3, s3, 1
                    j    .Lfq_loop
                .Lfq_fail:
                    li   a0, -1
                    call kof_println_int
                .Lfq_close:
                    mv   a0, s0
                    call kof_plat_close
                    ld   ra, 0(sp)
                    ld   s0, 8(sp)
                    ld   s1, 16(sp)
                    ld   s2, 24(sp)
                    ld   s3, 32(sp)
                    ld   s4, 40(sp)
                    addi sp, sp, 112
                    ret
                .globl kof_super_table
                kof_super_table:
                    .word 0
                .globl kof_equals_table
                kof_equals_table:
                    .quad 0
                .globl kof_hashcode_table
                kof_hashcode_table:
                    .quad 0
                .globl kof_tostring_table
                kof_tostring_table:
                    .quad 0
                """.replace("PORT_HI", hi).replace("PORT_LO", lo);
    }

    private static String queryAllOracle() {
        return """
                1
                {"1":1}
                1
                {"1":1,"ab":"ab"}
                2
                {"1":1}
                {"1":2}
                1
                {"n":null,"s":"a\\\"b"}""";
    }

    @Test
    void queryAllRowsAgainstRealMariaDbOnRiscv64(@TempDir Path tempDir) throws Exception {
        assumeRiscv();
        assumeMaria();
        String harness = mysqlQueryAllHarness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String out = buildRun("riscv64", tempDir, "qa_rv", harness + "\n" + runtime);
        assertEquals(queryAllOracle(), out, "query riscv64: registros JSON devem seguir o contrato JVM");
    }

    @Test
    void queryAllRowsAgainstRealMariaDbOnAarch64(@TempDir Path tempDir) throws Exception {
        assumeAarch64();
        assumeMaria();
        String harness = mysqlQueryAllHarness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String riscv = harness + "\n" + runtime;
        StringBuilder arm = new StringBuilder();
        for (String line : riscv.split("\n", -1)) {
            for (String t : NativeAarch64Translator.translateRiscvToAarch64(line)) arm.append(t).append('\n');
        }
        String out = buildRun("aarch64", tempDir, "qa_aa", arm.toString());
        assertEquals(queryAllOracle(), out, "query aarch64: registros JSON devem seguir o contrato JVM");
    }

    @Test
    void withoutQueryPieceLinkFailsSabotage(@TempDir Path tempDir) throws IOException {
        assumeRiscv();
        String harness = mysqlQueryAllHarness();
        Set<Integer> keep = new LinkedHashSet<>(RiscvSlices.keepForProgramText(harness));
        int b70 = -1;
        for (RiscvSlices.Piece p : RiscvSlices.pieces()) {
            if ("RISCV_RUNTIME_ASM_B_70".equals(p.field())) b70 = p.index();
        }
        assertTrue(b70 >= 0, "peça B70 (query texto completa) não encontrada no inventário");
        assertTrue(keep.remove(b70), "B70 deveria estar no keep do harness de query");
        String runtime = RiscvSlices.renderSubset(keep);
        Path asm = tempDir.resolve("sab_qa.s");
        Files.writeString(asm, harness + "\n" + runtime);
        Path obj = tempDir.resolve("sab_qa.o");
        Path bin = tempDir.resolve("sab_qa");
        runCapture("riscv64-linux-gnu-as", "-mno-relax", "-o", obj.toString(), asm.toString());
        String[] r = runAllowFail("riscv64-linux-gnu-ld", "--no-relax", "-o", bin.toString(), obj.toString());
        assertNotEquals("0", r[1], "sem a B70 o link deveria falhar (undefined kof_db_mysql_query); saída: " + r[0]);
        assertTrue(r[0].contains("kof_db_mysql_query"),
                "a falha deve citar kof_db_mysql_query: " + r[0]);
    }

    /**
     * S5.3 (db-parity-plan, gaps-db lane, 24/09): bind client-side do wire
     * MySQL — {@code kof_db_mysql_render} (valor → literal SQL) +
     * {@code kof_db_mysql_replace_q} (troca o 1º `?` pelo literal). Port do
     * fallback `.Ldb_exec_subst` do x86 (COM_QUERY não suporta `?`).
     * Prova por harness: Int → decimais; String → `'escaped'`
     * (`'`→`''`, `\`→`\\`); vazio → `''`; sem `?` devolve o sql inalterado;
     * só o 1º `?` é trocado. O caso negativo documenta a divergência honesta
     * vs x86 (janela do heap cross → ramo int correto `-5`; o x86 cai no ramo
     * string por comparação unsigned).
     */
    private static String bindHarness() {
        return """
                .section .rodata
                .Lb_raw_ab:
                    .ascii "ab"
                .Lb_raw_quote:
                    .ascii "a'b"
                .Lb_raw_bs:
                    .ascii "a\\\\b"
                .Lb_raw_x:
                    .ascii "x"
                .Lb_raw_sql2:
                    .ascii "SELECT ? + ?"
                .Lb_raw_sql0:
                    .ascii "no binds"
                .section .data
                .align 3
                .Lkof_heap_root_start:
                .Lkof_heap_root_end:
                .section .text
                .globl _start
                _start:
                    andi sp, sp, -16
                    # KofStrings no HEAP via kof_io_make_string: a classificacao
                    # do render usa a janela do heap (como em producao, onde os
                    # literais sao heap) — literais estaticos cairiam no ramo int.
                    la   a0, .Lb_raw_ab
                    li   a1, 2
                    call kof_io_make_string
                    mv   s0, a0
                    la   a0, .Lb_raw_quote
                    li   a1, 3
                    call kof_io_make_string
                    mv   s1, a0
                    la   a0, .Lb_raw_bs
                    li   a1, 3
                    call kof_io_make_string
                    mv   s2, a0
                    la   a0, .Lb_raw_ab
                    li   a1, 0
                    call kof_io_make_string
                    mv   s3, a0
                    la   a0, .Lb_raw_x
                    li   a1, 1
                    call kof_io_make_string
                    mv   s4, a0
                    la   a0, .Lb_raw_sql2
                    li   a1, 12
                    call kof_io_make_string
                    mv   s5, a0
                    la   a0, .Lb_raw_sql0
                    li   a1, 8
                    call kof_io_make_string
                    mv   s6, a0
                    li   a0, 42
                    call kof_db_mysql_render
                    call kof_println_string
                    mv   a0, s0
                    call kof_db_mysql_render
                    call kof_println_string
                    mv   a0, s1
                    call kof_db_mysql_render
                    call kof_println_string
                    mv   a0, s2
                    call kof_db_mysql_render
                    call kof_println_string
                    mv   a0, s3
                    call kof_db_mysql_render
                    call kof_println_string
                    li   a0, 0
                    call kof_db_mysql_render
                    call kof_println_string
                    li   a0, -5
                    call kof_db_mysql_render
                    call kof_println_string
                    mv   a0, s4
                    call kof_db_mysql_render
                    mv   s7, a0
                    mv   a0, s5
                    mv   a1, s7
                    call kof_db_mysql_replace_q
                    call kof_println_string
                    mv   a0, s6
                    mv   a1, s7
                    call kof_db_mysql_replace_q
                    call kof_println_string
                    li   a0, 0
                    li   a7, 93
                    ecall
                .globl kof_super_table
                kof_super_table:
                    .word 0
                .globl kof_equals_table
                kof_equals_table:
                    .quad 0
                .globl kof_hashcode_table
                kof_hashcode_table:
                    .quad 0
                .globl kof_tostring_table
                kof_tostring_table:
                    .quad 0
                """;
    }

    private static String bindOracle() {
        return """
                42
                'ab'
                'a''b'
                'a\\\\b'
                ''
                0
                -5
                SELECT 'x' + ?
                no binds""";
    }

    @Test
    void bindRenderReplaceMatchesOracleOnRiscv64(@TempDir Path tempDir) throws Exception {
        assumeRiscv();
        String harness = bindHarness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String out = buildRun("riscv64", tempDir, "bind_rv", harness + "\n" + runtime);
        assertEquals(bindOracle(), out, "bind riscv64 diverge do oráculo");
    }

    @Test
    void bindRenderReplaceMatchesOracleOnAarch64(@TempDir Path tempDir) throws Exception {
        assumeAarch64();
        String harness = bindHarness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String riscv = harness + "\n" + runtime;
        StringBuilder arm = new StringBuilder();
        for (String line : riscv.split("\n", -1)) {
            for (String t : NativeAarch64Translator.translateRiscvToAarch64(line)) arm.append(t).append('\n');
        }
        String out = buildRun("aarch64", tempDir, "bind_aa", arm.toString());
        assertEquals(bindOracle(), out, "bind aarch64 diverge do oráculo");
    }

    @Test
    void withoutBindPieceLinkFailsSabotage(@TempDir Path tempDir) throws IOException {
        assumeRiscv();
        String harness = bindHarness();
        Set<Integer> keep = new LinkedHashSet<>(RiscvSlices.keepForProgramText(harness));
        int b71 = -1;
        for (RiscvSlices.Piece p : RiscvSlices.pieces()) {
            if ("RISCV_RUNTIME_ASM_B_71".equals(p.field())) b71 = p.index();
        }
        assertTrue(b71 >= 0, "peça B71 (bind client-side) não encontrada no inventário");
        assertTrue(keep.remove(b71), "B71 deveria estar no keep do harness de bind");
        String runtime = RiscvSlices.renderSubset(keep);
        Path asm = tempDir.resolve("sab_bind.s");
        Files.writeString(asm, harness + "\n" + runtime);
        Path obj = tempDir.resolve("sab_bind.o");
        Path bin = tempDir.resolve("sab_bind");
        runCapture("riscv64-linux-gnu-as", "-mno-relax", "-o", obj.toString(), asm.toString());
        String[] r = runAllowFail("riscv64-linux-gnu-ld", "--no-relax", "-o", bin.toString(), obj.toString());
        assertNotEquals("0", r[1], "sem a B71 o link deveria falhar (undefined kof_db_mysql_render); saída: " + r[0]);
        assertTrue(r[0].contains("kof_db_mysql_render"),
                "a falha deve citar kof_db_mysql_render: " + r[0]);
    }

    /**
     * S5.3 (db-parity-plan, gaps-db lane, 24/09): execute COM_QUERY no cross —
     * {@code kof_db_mysql_execute(fd, sql)} (COM_QUERY via B67 + affected-rows
     * do OK-packet; port da cauda de execute de RuntimeDb4/Db5 x86) com binds
     * substituídos pela B71, mais a query com binds (B71 + B70). Prova por
     * harness contra o MariaDB real, numa ÚNICA conexão (a tabela TEMPORARY
     * vive nela): CREATE (0), INSERT com binds Int+String (1), INSERT com
     * quote no bind (escape, 1), UPDATE com 2 binds (1), DELETE sem match (0),
     * DELETE com match (1), SQL inválido (ERR → 0), SELECT via execute
     * (resultset → 0, contrato `.Ldb_exec_bad` do x86) e a query com bind
     * provando a coerência execute→query.
     */
    private static String execBindHarness() {
        int port = mysqlPort();
        String hi = String.format("0x%02X", (port >> 8) & 0xff);
        String lo = String.format("0x%02X", port & 0xff);
        // Q4: bulk multi-VALUES INSERT (300 linhas, ids 100..399) — o
        // affected-rows 300 força o caminho FC (0xFC+2LE) do OK-packet, que o
        // corpus pequeno (0/1) nunca exercita. SQL gerado (~4.5KB, abaixo do
        // limite 8000 da B67); '"' não aparece (GAS proíbe em .ascii).
        StringBuilder bulk = new StringBuilder("INSERT INTO kof_b72 VALUES ");
        for (int i = 100; i < 400; i++) {
            if (i > 100) bulk.append(',');
            bulk.append('(').append(i).append(",'r").append(i).append("')");
        }
        String bulkSql = bulk.toString();
        String head = """
                .section .rodata
                .Le_raw_create:
                    .ascii "CREATE TEMPORARY TABLE kof_b72 (id INT PRIMARY KEY, name VARCHAR(32))"
                .Le_raw_insert:
                    .ascii "INSERT INTO kof_b72 VALUES (?, ?)"
                .Le_raw_bulk:
                    .ascii \"""";
        String tail = """
                .Le_raw_update:
                    .ascii "UPDATE kof_b72 SET name = ? WHERE id = ?"
                .Le_raw_delete:
                    .ascii "DELETE FROM kof_b72 WHERE id = ?"
                .Le_raw_badsql:
                    .ascii "THIS IS NOT SQL"
                .Le_raw_select1:
                    .ascii "SELECT 1"
                .Le_raw_selbind:
                    .ascii "SELECT id, name FROM kof_b72 WHERE id = ?"
                .Le_raw_seven:
                    .ascii "seven"
                .Le_raw_obrien:
                    .ascii "o'brien"
                .Le_raw_n7:
                    .ascii "n7"
                .section .data
                .align 3
                .Le_user:
                    .zero 16
                    .word 4
                    .zero 4
                    .ascii "root"
                .align 3
                .Le_pass:
                    .zero 16
                    .word 7
                    .zero 4
                    .ascii "kofpass"
                .align 3
                .Le_db:
                    .zero 16
                    .word 4
                    .zero 4
                    .ascii "test"
                .align 3
                .Le_addr:
                    .byte 2, 0, PORT_HI, PORT_LO, 127, 0, 0, 1
                    .zero 8
                .align 3
                .Lkof_heap_root_start:
                .Lkof_heap_root_end:
                .section .text
                .globl _start
                _start:
                    andi sp, sp, -16
                    addi sp, sp, -160
                    # strings no HEAP via kof_io_make_string (o render da B71
                    # classifica pela janela do heap, como em producao):
                    # slots 0:create 8:insert 16:update 24:delete 32:badsql
                    # 40:select1 48:selbind 56:seven 64:obrien 72:n7 80:bulk
                    la   a0, .Le_raw_create
                    li   a1, 69
                    call kof_io_make_string
                    sd   a0, 0(sp)
                    la   a0, .Le_raw_insert
                    li   a1, 33
                    call kof_io_make_string
                    sd   a0, 8(sp)
                    la   a0, .Le_raw_bulk
                    li   a1, BULKLEN
                    call kof_io_make_string
                    sd   a0, 80(sp)
                    la   a0, .Le_raw_update
                    li   a1, 40
                    call kof_io_make_string
                    sd   a0, 16(sp)
                    la   a0, .Le_raw_delete
                    li   a1, 32
                    call kof_io_make_string
                    sd   a0, 24(sp)
                    la   a0, .Le_raw_badsql
                    li   a1, 15
                    call kof_io_make_string
                    sd   a0, 32(sp)
                    la   a0, .Le_raw_select1
                    li   a1, 8
                    call kof_io_make_string
                    sd   a0, 40(sp)
                    la   a0, .Le_raw_selbind
                    li   a1, 41
                    call kof_io_make_string
                    sd   a0, 48(sp)
                    la   a0, .Le_raw_seven
                    li   a1, 5
                    call kof_io_make_string
                    sd   a0, 56(sp)
                    la   a0, .Le_raw_obrien
                    li   a1, 7
                    call kof_io_make_string
                    sd   a0, 64(sp)
                    la   a0, .Le_raw_n7
                    li   a1, 2
                    call kof_io_make_string
                    sd   a0, 72(sp)
                    # socket + connect + handshake (s0 = fd)
                    li   a0, 2
                    li   a1, 1
                    li   a2, 0
                    call kof_plat_net_socket
                    mv   s0, a0
                    mv   a0, s0
                    la   a1, .Le_addr
                    li   a2, 16
                    call kof_plat_net_connect
                    mv   a0, s0
                    la   a1, .Le_user
                    la   a2, .Le_pass
                    la   a3, .Le_db
                    call kof_db_mysql_handshake
                    bnez a0, .Le_fail
                    ld   a0, 0(sp)
                    call .Le_x0
                    ld   a0, 8(sp)
                    li   a1, 7
                    ld   a2, 56(sp)
                    call .Le_x2
                    ld   a0, 8(sp)
                    li   a1, 8
                    ld   a2, 64(sp)
                    call .Le_x2
                    ld   a0, 80(sp)
                    call .Le_x0
                    ld   a0, 16(sp)
                    ld   a1, 72(sp)
                    li   a2, 7
                    call .Le_x2
                    ld   a0, 24(sp)
                    li   a1, 999
                    call .Le_x1
                    ld   a0, 24(sp)
                    li   a1, 8
                    call .Le_x1
                    ld   a0, 32(sp)
                    call .Le_x0
                    ld   a0, 40(sp)
                    call .Le_x0
                    ld   a0, 48(sp)
                    li   a1, 7
                    call .Le_q1
                    mv   a0, s0
                    call kof_plat_close
                    li   a0, 0
                    li   a7, 93
                    ecall
                .Le_fail:
                    li   a0, -1
                    call kof_println_int
                    mv   a0, s0
                    call kof_plat_close
                    li   a0, 0
                    li   a7, 93
                    ecall
                # a0 = sql -> execute + println(affected)
                .Le_x0:
                    addi sp, sp, -32
                    sd   ra, 0(sp)
                    sd   s0, 8(sp)
                    sd   s1, 16(sp)
                    mv   s1, a0
                    mv   a0, s0
                    mv   a1, s1
                    call kof_db_mysql_execute
                    call kof_println_int
                    ld   ra, 0(sp)
                    ld   s0, 8(sp)
                    ld   s1, 16(sp)
                    addi sp, sp, 32
                    ret
                # a0 = sql, a1 = b1 -> render + replace + execute + println
                .Le_x1:
                    addi sp, sp, -32
                    sd   ra, 0(sp)
                    sd   s0, 8(sp)
                    sd   s1, 16(sp)
                    sd   s2, 24(sp)
                    mv   s1, a0
                    mv   s2, a1
                    mv   a0, s2
                    call kof_db_mysql_render
                    mv   s2, a0
                    mv   a0, s1
                    mv   a1, s2
                    call kof_db_mysql_replace_q
                    mv   s1, a0
                    mv   a0, s0
                    mv   a1, s1
                    call kof_db_mysql_execute
                    call kof_println_int
                    ld   ra, 0(sp)
                    ld   s0, 8(sp)
                    ld   s1, 16(sp)
                    ld   s2, 24(sp)
                    addi sp, sp, 32
                    ret
                # a0 = sql, a1 = b1, a2 = b2 -> 2x render + replace + execute
                .Le_x2:
                    addi sp, sp, -48
                    sd   ra, 0(sp)
                    sd   s0, 8(sp)
                    sd   s1, 16(sp)
                    sd   s2, 24(sp)
                    sd   s3, 32(sp)
                    mv   s1, a0
                    mv   s2, a1
                    mv   s3, a2
                    mv   a0, s2
                    call kof_db_mysql_render
                    mv   a1, a0
                    mv   a0, s1
                    call kof_db_mysql_replace_q
                    mv   s1, a0
                    mv   a0, s3
                    call kof_db_mysql_render
                    mv   a1, a0
                    mv   a0, s1
                    call kof_db_mysql_replace_q
                    mv   s1, a0
                    mv   a0, s0
                    mv   a1, s1
                    call kof_db_mysql_execute
                    call kof_println_int
                    ld   ra, 0(sp)
                    ld   s0, 8(sp)
                    ld   s1, 16(sp)
                    ld   s2, 24(sp)
                    ld   s3, 32(sp)
                    addi sp, sp, 48
                    ret
                # a0 = sql, a1 = b1 -> render + replace + query + print rows
                .Le_q1:
                    addi sp, sp, -64
                    sd   ra, 0(sp)
                    sd   s0, 8(sp)
                    sd   s1, 16(sp)
                    sd   s2, 24(sp)
                    sd   s3, 32(sp)
                    sd   s4, 40(sp)
                    mv   s1, a0
                    mv   s2, a1
                    mv   a0, s2
                    call kof_db_mysql_render
                    mv   a1, a0
                    mv   a0, s1
                    call kof_db_mysql_replace_q
                    mv   a1, a0
                    mv   a0, s0
                    call kof_db_mysql_query
                    mv   s2, a0
                    mv   a0, s2
                    call kof_list_size
                    mv   s4, a0
                    call kof_println_int
                    li   s3, 0
                .Le_qloop:
                    bge  s3, s4, .Le_qout
                    mv   a0, s2
                    mv   a1, s3
                    call kof_list_get
                    call kof_println_string
                    addi s3, s3, 1
                    j    .Le_qloop
                .Le_qout:
                    ld   ra, 0(sp)
                    ld   s0, 8(sp)
                    ld   s1, 16(sp)
                    ld   s2, 24(sp)
                    ld   s3, 32(sp)
                    ld   s4, 40(sp)
                    addi sp, sp, 64
                    ret
                .globl kof_super_table
                kof_super_table:
                    .word 0
                .globl kof_equals_table
                kof_equals_table:
                    .quad 0
                .globl kof_hashcode_table
                kof_hashcode_table:
                    .quad 0
                .globl kof_tostring_table
                kof_tostring_table:
                    .quad 0
                """;
        // head + bulk + '"' + tail: o '"' fecha o .ascii do bulk (GAS proíbe
        // '"' dentro de .ascii, por isso o bulk é injetado aqui, não no fonte).
        String asmTail = tail.replace("PORT_HI", hi).replace("PORT_LO", lo);
        return (head + bulkSql + "\"\n" + asmTail)
                .replace("BULKLEN", String.valueOf(bulkSql.length()));
    }

    private static String execBindOracle() {
        return """
                0
                1
                1
                300
                1
                0
                1
                0
                0
                1
                {"id":7,"name":"n7"}""";
    }

    @Test
    void execWithBindsAgainstRealMariaDbOnRiscv64(@TempDir Path tempDir) throws Exception {
        assumeRiscv();
        assumeMaria();
        String harness = execBindHarness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String out = buildRun("riscv64", tempDir, "ex_rv", harness + "\n" + runtime);
        assertEquals(execBindOracle(), out, "execute riscv64 diverge do oráculo");
    }

    @Test
    void execWithBindsAgainstRealMariaDbOnAarch64(@TempDir Path tempDir) throws Exception {
        assumeAarch64();
        assumeMaria();
        String harness = execBindHarness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String riscv = harness + "\n" + runtime;
        StringBuilder arm = new StringBuilder();
        for (String line : riscv.split("\n", -1)) {
            for (String t : NativeAarch64Translator.translateRiscvToAarch64(line)) arm.append(t).append('\n');
        }
        String out = buildRun("aarch64", tempDir, "ex_aa", arm.toString());
        assertEquals(execBindOracle(), out, "execute aarch64 diverge do oráculo");
    }

    // ---- S5.5 fatia 4a (gaps-db lane, 24/09): exec que LANCA (peca B76) ----

    /** Harness do {@code kof_orm_mysql_exec} (peca B76, port do x86
     *  {@code RuntimeOrmMysqlExec}/{@code .Lorm_sa_exec}) contra o MariaDB
     *  real: uma conexao, CREATE TEMPORARY (affected 0), INSERT (1),
     *  INSERT (1), UPDATE casado (1), DELETE sem match (0) e DELETE casado (1).
     *  As strings vao para o HEAP (como em producao). {@code badSql}, quando
     *  nao-vazio, substitui a ultima operacao (prova do throw). */
    private static String ormExecHarness(String badSql) {
        int port = mysqlPort();
        String hi = String.format("0x%02X", (port >> 8) & 0xff);
        String lo = String.format("0x%02X", port & 0xff);
        String create = "CREATE TEMPORARY TABLE kof_b76 (id INT PRIMARY KEY, name VARCHAR(16))";
        String i1 = "INSERT INTO kof_b76 VALUES (1,'a')";
        String i2 = "INSERT INTO kof_b76 VALUES (2,'b')";
        String upd = "UPDATE kof_b76 SET name='c' WHERE id=1";
        String d0 = "DELETE FROM kof_b76 WHERE id=999";
        String d1 = "DELETE FROM kof_b76 WHERE id=2";
        String bad = badSql == null || badSql.isEmpty() ? "THIS IS NOT SQL" : badSql;
        return """
                .section .rodata
                .Lq76_raw_create:
                    .ascii "@CREATE@"
                .Lq76_raw_i1:
                    .ascii "@I1@"
                .Lq76_raw_i2:
                    .ascii "@I2@"
                .Lq76_raw_upd:
                    .ascii "@UPD@"
                .Lq76_raw_d0:
                    .ascii "@D0@"
                .Lq76_raw_d1:
                    .ascii "@D1@"
                .Lq76_raw_bad:
                    .ascii "@BAD@"
                .section .data
                .align 3
                .Lq76_user:
                    .zero 16
                    .word 4
                    .zero 4
                    .ascii "root"
                .align 3
                .Lq76_pass:
                    .zero 16
                    .word 7
                    .zero 4
                    .ascii "kofpass"
                .align 3
                .Lq76_db:
                    .zero 16
                    .word 4
                    .zero 4
                    .ascii "test"
                .align 3
                .Lq76_addr:
                    .byte 2, 0, PORT_HI, PORT_LO, 127, 0, 0, 1
                    .zero 8
                .align 3
                .Lkof_heap_root_start:
                .Lkof_heap_root_end:
                .section .text
                .globl _start
                _start:
                    andi sp, sp, -16
                    addi sp, sp, -96
                    # strings no heap: 0:create 8:i1 16:i2 24:upd 32:d0 40:d1 48:bad
                    la   a0, .Lq76_raw_create
                    li   a1, @LEN_CREATE@
                    call kof_io_make_string
                    sd   a0, 0(sp)
                    la   a0, .Lq76_raw_i1
                    li   a1, @LEN_I1@
                    call kof_io_make_string
                    sd   a0, 8(sp)
                    la   a0, .Lq76_raw_i2
                    li   a1, @LEN_I2@
                    call kof_io_make_string
                    sd   a0, 16(sp)
                    la   a0, .Lq76_raw_upd
                    li   a1, @LEN_UPD@
                    call kof_io_make_string
                    sd   a0, 24(sp)
                    la   a0, .Lq76_raw_d0
                    li   a1, @LEN_D0@
                    call kof_io_make_string
                    sd   a0, 32(sp)
                    la   a0, .Lq76_raw_d1
                    li   a1, @LEN_D1@
                    call kof_io_make_string
                    sd   a0, 40(sp)
                    la   a0, .Lq76_raw_bad
                    li   a1, @LEN_BAD@
                    call kof_io_make_string
                    sd   a0, 48(sp)
                    # socket + connect + handshake (s0 = fd)
                    li   a0, 2
                    li   a1, 1
                    li   a2, 0
                    call kof_plat_net_socket
                    mv   s0, a0
                    mv   a0, s0
                    la   a1, .Lq76_addr
                    li   a2, 16
                    call kof_plat_net_connect
                    mv   a0, s0
                    la   a1, .Lq76_user
                    la   a2, .Lq76_pass
                    la   a3, .Lq76_db
                    call kof_db_mysql_handshake
                    bnez a0, .Lq76_fail
                    ld   a0, 0(sp)
                    call .Lq76_x
                    ld   a0, 8(sp)
                    call .Lq76_x
                    ld   a0, 16(sp)
                    call .Lq76_x
                    ld   a0, 24(sp)
                    call .Lq76_x
                    ld   a0, 32(sp)
                    call .Lq76_x
                    ld   a0, 40(sp)
                    call .Lq76_x
                    @BAD_CALL@
                    mv   a0, s0
                    call kof_plat_close
                    li   a0, 0
                    li   a7, 93
                    ecall
                .Lq76_fail:
                    li   a0, -1
                    call kof_println_int
                    mv   a0, s0
                    call kof_plat_close
                    li   a0, 0
                    li   a7, 93
                    ecall
                # a0 = sql -> kof_orm_mysql_exec(fd, sql) + println(affected)
                .Lq76_x:
                    addi sp, sp, -32
                    sd   ra, 0(sp)
                    sd   s0, 8(sp)
                    sd   s1, 16(sp)
                    mv   s1, a0
                    mv   a0, s0
                    mv   a1, s1
                    call kof_orm_mysql_exec
                    call kof_println_int
                    ld   ra, 0(sp)
                    ld   s0, 8(sp)
                    ld   s1, 16(sp)
                    addi sp, sp, 32
                    ret
                .globl kof_super_table
                kof_super_table:
                    .word 0
                .globl kof_equals_table
                kof_equals_table:
                    .quad 0
                .globl kof_hashcode_table
                kof_hashcode_table:
                    .quad 0
                .globl kof_tostring_table
                kof_tostring_table:
                    .quad 0
                """
                .replace("PORT_HI", hi).replace("PORT_LO", lo)
                .replace("@CREATE@", create).replace("@I1@", i1).replace("@I2@", i2)
                .replace("@UPD@", upd).replace("@D0@", d0).replace("@D1@", d1)
                .replace("@BAD@", bad)
                .replace("@LEN_CREATE@", String.valueOf(create.length()))
                .replace("@LEN_I1@", String.valueOf(i1.length()))
                .replace("@LEN_I2@", String.valueOf(i2.length()))
                .replace("@LEN_UPD@", String.valueOf(upd.length()))
                .replace("@LEN_D0@", String.valueOf(d0.length()))
                .replace("@LEN_D1@", String.valueOf(d1.length()))
                .replace("@LEN_BAD@", String.valueOf(bad.length()))
                .replace("@BAD_CALL@", (badSql != null && !badSql.isEmpty())
                        ? "                    ld   a0, 48(sp)\n                    call .Lq76_x\n"
                        : "");
    }

    private static String ormExecOracle() {
        return """
                0
                1
                1
                1
                0
                1""";
    }

    @Test
    void ormExecAgainstRealMariaDbOnRiscv64(@TempDir Path tempDir) throws Exception {
        assumeRiscv();
        assumeMaria();
        String harness = ormExecHarness(null);
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String out = buildRun("riscv64", tempDir, "ormex_rv", harness + "\n" + runtime);
        assertEquals(ormExecOracle(), out, "kof_orm_mysql_exec riscv64 diverge do oraculo");
    }

    @Test
    void ormExecAgainstRealMariaDbOnAarch64(@TempDir Path tempDir) throws Exception {
        assumeAarch64();
        assumeMaria();
        String harness = ormExecHarness(null);
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String riscv = harness + "\n" + runtime;
        StringBuilder arm = new StringBuilder();
        for (String line : riscv.split("\n", -1)) {
            for (String t : NativeAarch64Translator.translateRiscvToAarch64(line)) arm.append(t).append('\n');
        }
        String out = buildRun("aarch64", tempDir, "ormex_aa", arm.toString());
        assertEquals(ormExecOracle(), out, "kof_orm_mysql_exec aarch64 diverge do oraculo");
    }

    /** O ERR do servidor no exec do save deve LANÇAR (`mysql: <msg>`), nao
     *  devolver 0 como a B72 — RED sem a peca. Sem handler, o throw imprime a
     *  KofString e sai 1 (paridade x86). */
    @Test
    void ormExecErrorThrowsAgainstRealMariaDbOnRiscv64(@TempDir Path tempDir) throws Exception {
        assumeRiscv();
        assumeMaria();
        String harness = ormExecHarness("THIS IS NOT SQL");
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String out = buildRunAllowFail("riscv64", tempDir, "ormex_err", harness + "\n" + runtime);
        String[] lines = out.split("\n", -1);
        assertTrue(lines.length >= 1 && lines[lines.length - 1].startsWith("mysql: "),
                "o ERR deve lancar 'mysql: ...' (nao 0); saida: " + out);
    }

    @Test
    void withoutOrmExecPieceLinkFailsSabotage(@TempDir Path tempDir) throws IOException {
        assumeRiscv();
        String harness = ormExecHarness(null);
        Set<Integer> keep = new LinkedHashSet<>(RiscvSlices.keepForProgramText(harness));
        int b76 = -1;
        for (RiscvSlices.Piece p : RiscvSlices.pieces()) {
            if ("RISCV_RUNTIME_ASM_B_76".equals(p.field())) b76 = p.index();
        }
        assertTrue(b76 >= 0, "peça B76 (exec que lança) não encontrada no inventário");
        assertTrue(keep.remove(b76), "B76 deveria estar no keep do harness de orm.exec");
        String runtime = RiscvSlices.renderSubset(keep);
        Path asm = tempDir.resolve("sab_ormex.s");
        Files.writeString(asm, harness + "\n" + runtime);
        Path obj = tempDir.resolve("sab_ormex.o");
        Path bin = tempDir.resolve("sab_ormex");
        runCapture("riscv64-linux-gnu-as", "-mno-relax", "-o", obj.toString(), asm.toString());
        String[] r = runAllowFail("riscv64-linux-gnu-ld", "--no-relax", "-o", bin.toString(), obj.toString());
        assertNotEquals("0", r[1], "sem a B76 o link deveria falhar (undefined kof_orm_mysql_exec); saída: " + r[0]);
        assertTrue(r[0].contains("kof_orm_mysql_exec"),
                "a falha deve citar kof_orm_mysql_exec: " + r[0]);
    }

    private String buildRunAllowFail(String arch, Path tempDir, String name, String asmText) throws IOException {
        Path asm = tempDir.resolve(name + ".s");
        Files.writeString(asm, asmText);
        Path obj = tempDir.resolve(name + ".o");
        Path bin = tempDir.resolve(name);
        String as = arch.equals("riscv64") ? "riscv64-linux-gnu-as" : "aarch64-linux-gnu-as";
        String ld = arch.equals("riscv64") ? "riscv64-linux-gnu-ld" : "aarch64-linux-gnu-ld";
        if (arch.equals("riscv64")) {
            runCapture(as, "-mno-relax", "-o", obj.toString(), asm.toString());
        } else {
            runCapture(as, "-o", obj.toString(), asm.toString());
        }
        runCapture(ld, "--no-relax", "-o", bin.toString(), obj.toString());
        bin.toFile().setExecutable(true);
        ProcessBuilder pb = new ProcessBuilder("qemu-" + arch, bin.toString());
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        try {
            p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        return out;
    }

    @Test
    void withoutExecutePieceLinkFailsSabotage(@TempDir Path tempDir) throws IOException {
        assumeRiscv();
        String harness = execBindHarness();
        Set<Integer> keep = new LinkedHashSet<>(RiscvSlices.keepForProgramText(harness));
        int b72 = -1;
        for (RiscvSlices.Piece p : RiscvSlices.pieces()) {
            if ("RISCV_RUNTIME_ASM_B_72".equals(p.field())) b72 = p.index();
        }
        assertTrue(b72 >= 0, "peça B72 (execute COM_QUERY) não encontrada no inventário");
        assertTrue(keep.remove(b72), "B72 deveria estar no keep do harness de execute");
        String runtime = RiscvSlices.renderSubset(keep);
        Path asm = tempDir.resolve("sab_ex.s");
        Files.writeString(asm, harness + "\n" + runtime);
        Path obj = tempDir.resolve("sab_ex.o");
        Path bin = tempDir.resolve("sab_ex");
        runCapture("riscv64-linux-gnu-as", "-mno-relax", "-o", obj.toString(), asm.toString());
        String[] r = runAllowFail("riscv64-linux-gnu-ld", "--no-relax", "-o", bin.toString(), obj.toString());
        assertNotEquals("0", r[1], "sem a B72 o link deveria falhar (undefined kof_db_mysql_execute); saída: " + r[0]);
        assertTrue(r[0].contains("kof_db_mysql_execute"),
                "a falha deve citar kof_db_mysql_execute: " + r[0]);
    }

    // ---- S5.4 (gaps-db lane, 24/09): connect real no cross (peça B73) ----

    /** Harness do connect cross: builda as 3 formas de URL (userinfo completa,
     *  alias `mariadb://` e host-only + `kof_db_connect2`) mais as SQLs no heap
     *  e prova que o handle resolvido é type 2 e a conexão está AUTENTICADA
     *  (CREATE/INSERT pela B72 sobre o fd resolvido). O `@` e o `:` das URLs
     *  não conflitam com `.ascii`. */
    private static String connectHarness() {
        int port = mysqlPort();
        String urlFull = "mysql://root:kofpass@127.0.0.1:" + port + "/test";
        String urlMaria = "mariadb://root:kofpass@127.0.0.1:" + port + "/test";
        String urlHostOnly = "mysql://127.0.0.1:" + port + "/test";
        String create = "CREATE TEMPORARY TABLE kof_b73 (id INT, name VARCHAR(16))";
        String insert = "INSERT INTO kof_b73 VALUES (7, 's5')";
        String select1 = "SELECT 1";
        return """
                .section .rodata
                .Lc73_raw_urlfull:
                    .ascii "URLFULL"
                .Lc73_raw_urlmaria:
                    .ascii "URLMARIA"
                .Lc73_raw_urlhost:
                    .ascii "URLHOST"
                .Lc73_raw_create:
                    .ascii "CREATESQL"
                .Lc73_raw_insert:
                    .ascii "INSERTSQL"
                .Lc73_raw_select1:
                    .ascii "SELECTSQL"
                .section .data
                .align 3
                .Lc73_user:
                    .zero 16
                    .word 4
                    .zero 4
                    .ascii "root"
                .align 3
                .Lc73_pass:
                    .zero 16
                    .word 7
                    .zero 4
                    .ascii "kofpass"
                .align 3
                .Lkof_heap_root_start:
                .Lkof_heap_root_end:
                .section .text
                .globl _start
                _start:
                    andi sp, sp, -16
                    addi sp, sp, -96
                    # SQLs no HEAP (kof_io_make_string): 0:create 8:insert 16:select1
                    la   a0, .Lc73_raw_create
                    li   a1, LEN_CREATE
                    call kof_io_make_string
                    sd   a0, 0(sp)
                    la   a0, .Lc73_raw_insert
                    li   a1, LEN_INSERT
                    call kof_io_make_string
                    sd   a0, 8(sp)
                    la   a0, .Lc73_raw_select1
                    li   a1, LEN_SELECT
                    call kof_io_make_string
                    sd   a0, 16(sp)
                    # URLs no heap
                    la   a0, .Lc73_raw_urlfull
                    li   a1, LEN_URLFULL
                    call kof_io_make_string
                    sd   a0, 24(sp)
                    la   a0, .Lc73_raw_urlmaria
                    li   a1, LEN_URLMARIA
                    call kof_io_make_string
                    sd   a0, 32(sp)
                    la   a0, .Lc73_raw_urlhost
                    li   a1, LEN_URLHOST
                    call kof_io_make_string
                    sd   a0, 40(sp)
                    # h0 = kof_db_connect(url full); type == 2
                    ld   a0, 24(sp)
                    call kof_db_connect
                    mv   s1, a0
                    mv   a0, s1
                    call kof_db_type
                    call kof_println_int
                    # fd = resolve(h0); != 0
                    mv   a0, s1
                    call kof_db_resolve
                    mv   s0, a0
                    snez a0, s0
                    call kof_println_int
                    # CREATE TEMPORARY + INSERT pela B72 (fd real autenticado)
                    mv   a0, s0
                    ld   a1, 0(sp)
                    call kof_db_mysql_execute
                    call kof_println_int
                    mv   a0, s0
                    ld   a1, 8(sp)
                    call kof_db_mysql_execute
                    call kof_println_int
                    # h1 = kof_db_connect(mariadb:// alias); type == 2 + SELECT 1
                    ld   a0, 32(sp)
                    call kof_db_connect
                    mv   s2, a0
                    mv   a0, s2
                    call kof_db_type
                    call kof_println_int
                    mv   a0, s2
                    call kof_db_resolve
                    ld   a1, 16(sp)
                    call kof_db_mysql_execute
                    call kof_println_int
                    # h2 = kof_db_connect2(host-only, user, pass); type == 2
                    ld   a0, 40(sp)
                    la   a1, .Lc73_user
                    la   a2, .Lc73_pass
                    call kof_db_connect2
                    mv   s2, a0
                    mv   a0, s2
                    call kof_db_type
                    call kof_println_int
                    # SELECT 1 sobre h2 (auth host-only OK)
                    mv   a0, s2
                    call kof_db_resolve
                    ld   a1, 16(sp)
                    call kof_db_mysql_execute
                    call kof_println_int
                    li   a0, 0
                    li   a7, 93
                    ecall
                .globl kof_super_table
                kof_super_table:
                    .word 0
                .globl kof_equals_table
                kof_equals_table:
                    .quad 0
                .globl kof_hashcode_table
                kof_hashcode_table:
                    .quad 0
                .globl kof_tostring_table
                kof_tostring_table:
                    .quad 0
                # B47 (execute/query sqlite) e' arrastada pelo kof_db_connect;
                # os simbolos sqlite3_* nunca sao chamados neste harness mysql.
                .globl sqlite3_open
                sqlite3_open:
                    ret
                .globl sqlite3_prepare_v2
                sqlite3_prepare_v2:
                    ret
                .globl sqlite3_step
                sqlite3_step:
                    ret
                .globl sqlite3_finalize
                sqlite3_finalize:
                    ret
                .globl sqlite3_changes
                sqlite3_changes:
                    ret
                .globl sqlite3_close
                sqlite3_close:
                    ret
                .globl sqlite3_column_count
                sqlite3_column_count:
                    ret
                .globl sqlite3_column_name
                sqlite3_column_name:
                    ret
                .globl sqlite3_column_type
                sqlite3_column_type:
                    ret
                .globl sqlite3_column_int
                sqlite3_column_int:
                    ret
                .globl sqlite3_column_text
                sqlite3_column_text:
                    ret
                .globl sqlite3_bind_text
                sqlite3_bind_text:
                    ret
                .globl sqlite3_bind_int
                sqlite3_bind_int:
                    ret
                """
                .replace("LEN_CREATE", String.valueOf(create.length()))
                .replace("LEN_INSERT", String.valueOf(insert.length()))
                .replace("LEN_SELECT", String.valueOf(select1.length()))
                .replace("LEN_URLFULL", String.valueOf(urlFull.length()))
                .replace("LEN_URLMARIA", String.valueOf(urlMaria.length()))
                .replace("LEN_URLHOST", String.valueOf(urlHostOnly.length()))
                .replace("URLFULL", urlFull)
                .replace("URLMARIA", urlMaria)
                .replace("URLHOST", urlHostOnly)
                .replace("CREATESQL", create)
                .replace("INSERTSQL", insert)
                .replace("SELECTSQL", select1);
    }

    private static String connectOracle() {
        return """
                2
                1
                0
                1
                2
                0
                2
                0""";
    }

    @Test
    void connectMysqlAgainstRealMariaDbOnRiscv64(@TempDir Path tempDir) throws Exception {
        assumeRiscv();
        assumeMaria();
        String harness = connectHarness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String out = buildRun("riscv64", tempDir, "cn_rv", harness + "\n" + runtime);
        assertEquals(connectOracle(), out, "connect mysql riscv64 diverge do oráculo");
    }

    @Test
    void connectMysqlAgainstRealMariaDbOnAarch64(@TempDir Path tempDir) throws Exception {
        assumeAarch64();
        assumeMaria();
        String harness = connectHarness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String riscv = harness + "\n" + runtime;
        StringBuilder arm = new StringBuilder();
        for (String line : riscv.split("\n", -1)) {
            for (String t : NativeAarch64Translator.translateRiscvToAarch64(line)) arm.append(t).append('\n');
        }
        String out = buildRun("aarch64", tempDir, "cn_aa", arm.toString());
        assertEquals(connectOracle(), out, "connect mysql aarch64 diverge do oráculo");
    }

    @Test
    void withoutConnectPieceLinkFailsSabotage(@TempDir Path tempDir) throws IOException {
        assumeRiscv();
        String harness = connectHarness();
        Set<Integer> keep = new LinkedHashSet<>(RiscvSlices.keepForProgramText(harness));
        int b73 = -1;
        for (RiscvSlices.Piece p : RiscvSlices.pieces()) {
            if ("RISCV_RUNTIME_ASM_B_73".equals(p.field())) b73 = p.index();
        }
        assertTrue(b73 >= 0, "peça B73 (connect mysql cross) não encontrada no inventário");
        assertTrue(keep.remove(b73), "B73 deveria estar no keep do harness de connect");
        String runtime = RiscvSlices.renderSubset(keep);
        Path asm = tempDir.resolve("sab_cn.s");
        Files.writeString(asm, harness + "\n" + runtime);
        Path obj = tempDir.resolve("sab_cn.o");
        Path bin = tempDir.resolve("sab_cn");
        runCapture("riscv64-linux-gnu-as", "-mno-relax", "-o", obj.toString(), asm.toString());
        String[] r = runAllowFail("riscv64-linux-gnu-ld", "--no-relax", "-o", bin.toString(), obj.toString());
        assertNotEquals("0", r[1], "sem a B73 o link deveria falhar (undefined kof_db_connect_mysql); saída: " + r[0]);
        assertTrue(r[0].contains("kof_db_connect_mysql"),
                "a falha deve citar kof_db_connect_mysql: " + r[0]);
    }

    // ---- S5.4 fatia 2: dispatch type 2 no kof_db_execute/query (B47b) ----

    /** Harness do dispatch: conecta pelo `kof_db_connect`, roda
     *  `kof_db_execute`/`kof_db_execute2`/`kof_db_query1` pelo HANDLE (não pelo
     *  fd direto como os harnesses B70/B72) e prova o ramo mysql + close. */
    private static String dispatchHarness() {
        int port = mysqlPort();
        String urlFull = "mysql://root:kofpass@127.0.0.1:" + port + "/test";
        String urlHost = "mariadb://127.0.0.1:" + port + "/test";
        String drop = "DROP TABLE IF EXISTS kof_disp";
        String create = "CREATE TABLE kof_disp (id INT, name VARCHAR(32))";
        String del = "DELETE FROM kof_disp";
        String insert = "INSERT INTO kof_disp VALUES (?, ?)";
        String select = "SELECT id, name FROM kof_disp WHERE id = ?";
        String alias = "Alias";
        return """
                .section .rodata
                .Ld_raw_urlfull: .ascii "URLFULL"
                .Ld_raw_urlhost: .ascii "URLHOST"
                .Ld_raw_drop: .ascii "DROPSQL"
                .Ld_raw_create: .ascii "CREATESQL"
                .Ld_raw_del: .ascii "DELSQL"
                .Ld_raw_insert: .ascii "INSERTSQL"
                .Ld_raw_select: .ascii "SELECTSQL"
                .Ld_raw_alias: .ascii "ALIAS"
                .section .data
                .align 3
                .Lkof_heap_root_start:
                .Lkof_heap_root_end:
                .section .text
                .globl _start
                _start:
                    andi sp, sp, -16
                    addi sp, sp, -112
                    # slots: 0 urlfull 8 urlhost 16 drop 24 create 32 del 40 insert 48 select 56 alias
                    la   a0, .Ld_raw_urlfull
                    li   a1, LEN_URLFULL
                    call kof_io_make_string
                    sd   a0, 0(sp)
                    la   a0, .Ld_raw_urlhost
                    li   a1, LEN_URLHOST
                    call kof_io_make_string
                    sd   a0, 8(sp)
                    la   a0, .Ld_raw_drop
                    li   a1, LEN_DROP
                    call kof_io_make_string
                    sd   a0, 16(sp)
                    la   a0, .Ld_raw_create
                    li   a1, LEN_CREATE
                    call kof_io_make_string
                    sd   a0, 24(sp)
                    la   a0, .Ld_raw_del
                    li   a1, LEN_DEL
                    call kof_io_make_string
                    sd   a0, 32(sp)
                    la   a0, .Ld_raw_insert
                    li   a1, LEN_INSERT
                    call kof_io_make_string
                    sd   a0, 40(sp)
                    la   a0, .Ld_raw_select
                    li   a1, LEN_SELECT
                    call kof_io_make_string
                    sd   a0, 48(sp)
                    la   a0, .Ld_raw_alias
                    li   a1, LEN_ALIAS
                    call kof_io_make_string
                    sd   a0, 56(sp)
                    # h0 = connect(urlfull); type == 2
                    ld   a0, 0(sp)
                    call kof_db_connect
                    mv   s1, a0
                    mv   a0, s1
                    call kof_db_type
                    call kof_println_int
                    # drop -> 0
                    mv   a0, s1
                    ld   a1, 16(sp)
                    call kof_db_execute
                    call kof_println_int
                    # create -> 0
                    mv   a0, s1
                    ld   a1, 24(sp)
                    call kof_db_execute
                    call kof_println_int
                    # delete (tabela vazia) -> 0
                    mv   a0, s1
                    ld   a1, 32(sp)
                    call kof_db_execute
                    call kof_println_int
                    # execute2(insert, 7, "Alias") -> 1
                    mv   a0, s1
                    ld   a1, 40(sp)
                    li   a2, 7
                    ld   a3, 56(sp)
                    call kof_db_execute2
                    call kof_println_int
                    # query1(select, 7) -> size 1
                    mv   a0, s1
                    ld   a1, 48(sp)
                    li   a2, 7
                    li   a3, 0
                    call kof_db_query1
                    mv   s2, a0
                    mv   a0, s2
                    call kof_list_size
                    call kof_println_int
                    # close(h0)
                    mv   a0, s1
                    call kof_db_close
                    # h1 = connect(host-only mariadb:// alias); resolve != 0
                    ld   a0, 8(sp)
                    call kof_db_connect
                    mv   s1, a0
                    mv   a0, s1
                    call kof_db_resolve
                    snez a0, a0
                    call kof_println_int
                    # type == 2
                    mv   a0, s1
                    call kof_db_type
                    call kof_println_int
                    # query1 on h1 -> size 1
                    mv   a0, s1
                    ld   a1, 48(sp)
                    li   a2, 7
                    li   a3, 0
                    call kof_db_query1
                    mv   s2, a0
                    mv   a0, s2
                    call kof_list_size
                    call kof_println_int
                    mv   a0, s1
                    call kof_db_close
                    li   a0, 0
                    li   a7, 93
                    ecall
                .globl kof_super_table
                kof_super_table:
                    .word 0
                .globl kof_equals_table
                kof_equals_table:
                    .quad 0
                .globl kof_hashcode_table
                kof_hashcode_table:
                    .quad 0
                .globl kof_tostring_table
                kof_tostring_table:
                    .quad 0
                .globl sqlite3_open
                sqlite3_open:
                    ret
                .globl sqlite3_prepare_v2
                sqlite3_prepare_v2:
                    ret
                .globl sqlite3_step
                sqlite3_step:
                    ret
                .globl sqlite3_finalize
                sqlite3_finalize:
                    ret
                .globl sqlite3_changes
                sqlite3_changes:
                    ret
                .globl sqlite3_close
                sqlite3_close:
                    ret
                .globl sqlite3_column_count
                sqlite3_column_count:
                    ret
                .globl sqlite3_column_name
                sqlite3_column_name:
                    ret
                .globl sqlite3_column_type
                sqlite3_column_type:
                    ret
                .globl sqlite3_column_int
                sqlite3_column_int:
                    ret
                .globl sqlite3_column_text
                sqlite3_column_text:
                    ret
                .globl sqlite3_bind_text
                sqlite3_bind_text:
                    ret
                .globl sqlite3_bind_int
                sqlite3_bind_int:
                    ret
                """
                .replace("LEN_DROP", String.valueOf(drop.length()))
                .replace("LEN_CREATE", String.valueOf(create.length()))
                .replace("LEN_DEL", String.valueOf(del.length()))
                .replace("LEN_INSERT", String.valueOf(insert.length()))
                .replace("LEN_SELECT", String.valueOf(select.length()))
                .replace("LEN_ALIAS", String.valueOf(alias.length()))
                .replace("LEN_URLFULL", String.valueOf(urlFull.length()))
                .replace("LEN_URLHOST", String.valueOf(urlHost.length()))
                .replace("URLFULL", urlFull)
                .replace("URLHOST", urlHost)
                .replace("DROPSQL", drop)
                .replace("CREATESQL", create)
                .replace("DELSQL", del)
                .replace("INSERTSQL", insert)
                .replace("SELECTSQL", select)
                .replace("ALIAS", alias);
    }

    private static String dispatchOracle() {
        return """
                2
                0
                0
                0
                1
                1
                1
                2
                1""";
    }

    @Test
    void dispatchExecuteQueryAgainstRealMariaDbOnRiscv64(@TempDir Path tempDir) throws Exception {
        assumeRiscv();
        assumeMaria();
        String harness = dispatchHarness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String out = buildRun("riscv64", tempDir, "dp_rv", harness + "\n" + runtime);
        assertEquals(dispatchOracle(), out, "dispatch mysql riscv64 diverge do oráculo");
    }

    @Test
    void dispatchExecuteQueryAgainstRealMariaDbOnAarch64(@TempDir Path tempDir) throws Exception {
        assumeAarch64();
        assumeMaria();
        String harness = dispatchHarness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String riscv = harness + "\n" + runtime;
        StringBuilder arm = new StringBuilder();
        for (String line : riscv.split("\n", -1)) {
            for (String t : NativeAarch64Translator.translateRiscvToAarch64(line)) arm.append(t).append('\n');
        }
        String out = buildRun("aarch64", tempDir, "dp_aa", arm.toString());
        assertEquals(dispatchOracle(), out, "dispatch mysql aarch64 diverge do oráculo");
    }

    @Test
    void withoutDispatchPieceLinkFailsSabotage(@TempDir Path tempDir) throws IOException {
        assumeRiscv();
        String harness = dispatchHarness();
        Set<Integer> keep = new LinkedHashSet<>(RiscvSlices.keepForProgramText(harness));
        int b47b = -1;
        for (RiscvSlices.Piece p : RiscvSlices.pieces()) {
            if ("RISCV_RUNTIME_ASM_B_47B".equals(p.field())) b47b = p.index();
        }
        assertTrue(b47b >= 0, "peça B47b (dispatch mysql cross) não encontrada no inventário");
        assertTrue(keep.remove(b47b), "B47b deveria estar no keep do harness de dispatch");
        String runtime = RiscvSlices.renderSubset(keep);
        Path asm = tempDir.resolve("sab_dp.s");
        Files.writeString(asm, harness + "\n" + runtime);
        Path obj = tempDir.resolve("sab_dp.o");
        Path bin = tempDir.resolve("sab_dp");
        runCapture("riscv64-linux-gnu-as", "-mno-relax", "-o", obj.toString(), asm.toString());
        String[] r = runAllowFail("riscv64-linux-gnu-ld", "--no-relax", "-o", bin.toString(), obj.toString());
        assertNotEquals("0", r[1], "sem a B47b o link deveria falhar (undefined kof_db_execute/query); saída: " + r[0]);
        assertTrue(r[0].contains("kof_db_execute") || r[0].contains("kof_db_query"),
                "a falha deve citar o dispatch kof_db_execute/query: " + r[0]);
    }

    @Test
    void greetingParseMatchesOracleOnRiscv64(@TempDir Path tempDir) throws Exception {
        assumeRiscv();
        String harness = greetingHarness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String out = buildRun("riscv64", tempDir, "greet_rv", harness + "\n" + runtime);
        assertEquals(greetingOracle(), out, "parse do greeting riscv64 diverge do oráculo");
    }

    @Test
    void greetingParseMatchesOracleOnAarch64(@TempDir Path tempDir) throws Exception {
        assumeAarch64();
        String harness = greetingHarness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String riscv = harness + "\n" + runtime;
        StringBuilder arm = new StringBuilder();
        for (String line : riscv.split("\n", -1)) {
            for (String t : NativeAarch64Translator.translateRiscvToAarch64(line)) arm.append(t).append('\n');
        }
        String out = buildRun("aarch64", tempDir, "greet_aa", arm.toString());
        assertEquals(greetingOracle(), out, "parse do greeting aarch64 diverge do oráculo");
    }

    @Test
    void withoutGreetingPieceLinkFailsSabotage(@TempDir Path tempDir) throws IOException {
        assumeRiscv();
        String harness = greetingHarness();
        Set<Integer> keep = new LinkedHashSet<>(RiscvSlices.keepForProgramText(harness));
        int b64 = -1;
        for (RiscvSlices.Piece p : RiscvSlices.pieces()) {
            if ("RISCV_RUNTIME_ASM_B_64".equals(p.field())) b64 = p.index();
        }
        assertTrue(b64 >= 0, "peça B64 (parse do greeting) não encontrada no inventário");
        assertTrue(keep.remove(b64), "B64 deveria estar no keep do harness de greeting");
        String runtime = RiscvSlices.renderSubset(keep);
        Path asm = tempDir.resolve("sab_greet.s");
        Files.writeString(asm, harness + "\n" + runtime);
        Path obj = tempDir.resolve("sab_greet.o");
        Path bin = tempDir.resolve("sab_greet");
        runCapture("riscv64-linux-gnu-as", "-mno-relax", "-o", obj.toString(), asm.toString());
        String[] r = runAllowFail("riscv64-linux-gnu-ld", "--no-relax", "-o", bin.toString(), obj.toString());
        assertNotEquals("0", r[1], "sem a B64 o link deveria falhar (undefined kof_db_mysql_parse_greeting); saída: " + r[0]);
        assertTrue(r[0].contains("kof_db_mysql_parse_greeting"),
                "a falha deve citar kof_db_mysql_parse_greeting: " + r[0]);
    }

    @Test
    void scrambleAndLenencMatchOracleOnRiscv64(@TempDir Path tempDir) throws Exception {
        assumeRiscv();
        String harness = wireHarness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String out = buildRun("riscv64", tempDir, "wire_rv", harness + "\n" + runtime);
        assertEquals(wireOracle(), out, "scramble/lenenc riscv64 diverge do oráculo");
    }

    @Test
    void scrambleAndLenencMatchOracleOnAarch64(@TempDir Path tempDir) throws Exception {
        assumeAarch64();
        String harness = wireHarness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String riscv = harness + "\n" + runtime;
        StringBuilder arm = new StringBuilder();
        for (String line : riscv.split("\n", -1)) {
            for (String t : NativeAarch64Translator.translateRiscvToAarch64(line)) arm.append(t).append('\n');
        }
        String out = buildRun("aarch64", tempDir, "wire_aa", arm.toString());
        assertEquals(wireOracle(), out, "scramble/lenenc aarch64 diverge do oráculo");
    }

    @Test
    void withoutAuthPieceLinkFailsSabotage(@TempDir Path tempDir) throws IOException {
        assumeRiscv();
        String harness = wireHarness();
        Set<Integer> keep = new LinkedHashSet<>(RiscvSlices.keepForProgramText(harness));
        int b63 = -1;
        for (RiscvSlices.Piece p : RiscvSlices.pieces()) {
            if ("RISCV_RUNTIME_ASM_B_63".equals(p.field())) b63 = p.index();
        }
        assertTrue(b63 >= 0, "peça B63 (auth cross) não encontrada no inventário");
        assertTrue(keep.remove(b63), "B63 deveria estar no keep do harness de auth");
        String runtime = RiscvSlices.renderSubset(keep);
        Path asm = tempDir.resolve("sab_auth.s");
        Files.writeString(asm, harness + "\n" + runtime);
        Path obj = tempDir.resolve("sab_auth.o");
        Path bin = tempDir.resolve("sab_auth");
        runCapture("riscv64-linux-gnu-as", "-mno-relax", "-o", obj.toString(), asm.toString());
        String[] r = runAllowFail("riscv64-linux-gnu-ld", "--no-relax", "-o", bin.toString(), obj.toString());
        assertNotEquals("0", r[1], "sem a B63 o link deveria falhar (undefined kof_db_mysql_scramble); saída: " + r[0]);
        assertTrue(r[0].contains("kof_db_mysql_scramble"),
                "a falha deve citar kof_db_mysql_scramble: " + r[0]);
    }

    @Test
    void withoutSha1PieceLinkFailsSabotage(@TempDir Path tempDir) throws IOException {
        assumeRiscv();
        String harness = harness();
        Set<Integer> keep = new LinkedHashSet<>(RiscvSlices.keepForProgramText(harness));
        int b62 = -1;
        for (RiscvSlices.Piece p : RiscvSlices.pieces()) {
            if ("RISCV_RUNTIME_ASM_B_62".equals(p.field())) b62 = p.index();
        }
        assertTrue(b62 >= 0, "peça B62 (SHA1 cross) não encontrada no inventário");
        assertTrue(keep.remove(b62), "B62 deveria estar no keep do harness de SHA1");
        String runtime = RiscvSlices.renderSubset(keep);
        Path asm = tempDir.resolve("sab.s");
        Files.writeString(asm, harness + "\n" + runtime);
        Path obj = tempDir.resolve("sab.o");
        Path bin = tempDir.resolve("sab");
        runCapture("riscv64-linux-gnu-as", "-mno-relax", "-o", obj.toString(), asm.toString());
        String[] r = runAllowFail("riscv64-linux-gnu-ld", "--no-relax", "-o", bin.toString(), obj.toString());
        assertNotEquals("0", r[1], "sem a B62 o link deveria falhar (undefined kof_sec_sha1_internal); saída: " + r[0]);
        assertTrue(r[0].contains("kof_sec_sha1_internal"),
                "a falha deve citar kof_sec_sha1_internal: " + r[0]);
    }
}
