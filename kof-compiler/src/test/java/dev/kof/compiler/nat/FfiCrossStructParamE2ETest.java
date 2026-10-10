package dev.kof.compiler.nat;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Target;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 3.7 fatia 4 ({@code docs/development/kof-c-cross.md}): {@code record} de
 * campos INTEGER por valor como PARÂMETRO no cross (riscv64/aarch64) — o
 * caller monta cada eightbyte do objeto Kof no registrador inteiro da sua
 * classe (LP64 {@code a0}/{@code a1}; AAPCS64 {@code x0}/{@code x1} pelo
 * tradutor). Fixture C montada com o {@code as} cross (sem cc cruzado — o
 * host não tem), chamada pelo binário Kof sob qemu.
 *
 * <p>Oráculo regra 5: o golden é MEDIÇÃO real (qemu nas duas archs, que
 * concordam entre si) — não memória. Sem toolchain → skip honesto (NATIVE002).
 * Struct com campo float/HFA ou &gt; 16 B segue FFI001 (testes de gate, sem
 * toolchain).
 */
class FfiCrossStructParamE2ETest {

    private static final String FIXTURE_RISCV = """
            .text
            .globl pairplus
            pairplus:
                sext.w a2, a0
                srli a3, a0, 32
                sext.w a3, a3
                add a2, a2, a3
                addw a0, a2, a1
                ret
            .globl triplesum
            triplesum:
                sext.w a2, a0
                srli a3, a0, 32
                sext.w a3, a3
                sext.w a4, a1
                add a2, a2, a3
                addw a0, a2, a4
                ret
            """;

    private static final String FIXTURE_AARCH = """
            .text
            .globl pairplus
            pairplus:
                sxtw x2, w0
                lsr x3, x0, #32
                sxtw x3, w3
                add x2, x2, x3
                add w0, w2, w1
                ret
            .globl triplesum
            triplesum:
                sxtw x2, w0
                lsr x3, x0, #32
                sxtw x3, w3
                sxtw x4, w1
                add x2, x2, x3
                add w0, w2, w4
                ret
            """;

    private static final String PROGRAM_GOLDEN = String.join("\n", "42", "2", "6");

    /** D-MEM-FFI-CROSS-FULL face 3 (> 16 B by-value param → BYREF): a C `Big`
     *  struct is passed as a POINTER in `a0` on both archs (measured 30/09). */
    private static final String BIGFIX_RISCV = """
            .text
            .globl bigsum
            bigsum:
                ld a2, 0(a0)
                ld a3, 8(a0)
                ld a4, 16(a0)
                add a2, a2, a3
                add a2, a2, a4
                add a0, a2, a1
                ret
            """;

    private static final String BIGFIX_AARCH = """
            .text
            .globl bigsum
            bigsum:
                ldr x2, [x0]
                ldr x3, [x0, #8]
                ldr x4, [x0, #16]
                add x2, x2, x3
                add x2, x2, x4
                add x0, x2, x1
                ret
            """;

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

    private static void run(String... cmd) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        try {
            assertEquals(0, p.waitFor(), "comando falhou: " + String.join(" ", cmd) + "\n" + out);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    private String compileAndRun(String arch, Target target, String fixtureAsm,
                                 String fixtureAs, Path tmp) throws IOException {
        Path s = tmp.resolve("fixture-" + arch + ".s");
        Path o = tmp.resolve("fixture-" + arch + ".o");
        Files.writeString(s, fixtureAsm);
        run(fixtureAs, "-o", o.toString(), s.toString());

        Path src = tmp.resolve("Main-" + arch + ".kf");
        Files.writeString(src, """
                record Pair(Int a, Int b)
                record Triple(Int a, Int b, Int c)

                extern "%s" pairplus(Pair p, Int k): Int
                extern "%s" triplesum(Triple t): Int

                main() {
                    println(pairplus(Pair(20, 22), 0))
                    println(pairplus(Pair(-3, 5), 0))
                    println(triplesum(Triple(1, 2, 3)))
                }
                """.formatted(o.toString(), o.toString()));

        CompilationResult r = new CompilerDriver().compile(src, tmp.resolve("out-" + arch), target);
        assertTrue(r.success(), "compile " + arch + " (struct param cross): " + r.diagnostics().getDiagnostics());
        Path bin = tmp.resolve("out-" + arch + "/Default/Main");
        assertTrue(Files.exists(bin), "binário ausente em " + arch);

        ProcessBuilder pb = new ProcessBuilder("qemu-" + arch, bin.toString()).redirectErrorStream(true);
        pb.environment().put("QEMU_LD_PREFIX", NativeCrossLink.qemuPrefixFor(arch));
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        try {
            assertEquals(0, p.waitFor(), arch + " exit != 0: '" + out + "'");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        return out;
    }

    private String compileAndRunBig(String arch, Target target, String fixtureAsm,
                                    String fixtureAs, Path tmp) throws IOException {
        Path s = tmp.resolve("bigfix-" + arch + ".s");
        Path o = tmp.resolve("bigfix-" + arch + ".o");
        Files.writeString(s, fixtureAsm);
        run(fixtureAs, "-o", o.toString(), s.toString());

        Path src = tmp.resolve("Big-" + arch + ".kf");
        Files.writeString(src, """
                record Big(Long a, Long b, Long c)

                extern "%s" bigsum(Big b, Long z): Long

                main() {
                    println(bigsum(Big(7, 14, 21), 100))
                }
                """.formatted(o.toString()));

        CompilationResult r = new CompilerDriver().compile(src, tmp.resolve("out-big-" + arch), target);
        assertTrue(r.success(), "compile " + arch + " (struct >16 B param cross): "
                + r.diagnostics().getDiagnostics());
        Path bin = tmp.resolve("out-big-" + arch + "/Default/Main");
        assertTrue(Files.exists(bin), "binário ausente em " + arch);

        ProcessBuilder pb = new ProcessBuilder("qemu-" + arch, bin.toString()).redirectErrorStream(true);
        pb.environment().put("QEMU_LD_PREFIX", NativeCrossLink.qemuPrefixFor(arch));
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        try {
            assertEquals(0, p.waitFor(), arch + " exit != 0: '" + out + "'");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        return out;
    }

    private void ready(String arch) {
        Assumptions.assumeTrue(has(arch + "-linux-gnu-as", arch + "-linux-gnu-ld", "qemu-" + arch),
                "toolchain " + arch + " ausente — pulando (NATIVE002)");
        Assumptions.assumeTrue(NativeCrossLink.sysrootFor(arch) != null,
                "libc " + arch + "-cross ausente (KOF_CROSS_SYSROOT) — pulando");
    }

    @Test
    void riscv64StructParamByValue(@TempDir Path tmp) throws IOException {
        ready("riscv64");
        assertEquals(PROGRAM_GOLDEN, compileAndRun("riscv64", Target.NATIVE_RISCV64,
                FIXTURE_RISCV, "riscv64-linux-gnu-as", tmp),
                "riscv64 struct INTEGER por valor (LP64: words em a0/a1)");
    }

    @Test
    void aarch64StructParamByValue(@TempDir Path tmp) throws IOException {
        ready("aarch64");
        assertEquals(PROGRAM_GOLDEN, compileAndRun("aarch64", Target.NATIVE_AARCH64,
                FIXTURE_AARCH, "aarch64-linux-gnu-as", tmp),
                "aarch64 struct INTEGER por valor (AAPCS64: words em x0/x1)");
    }

    @Test
    void crossStructParamAgreesBetweenArchs(@TempDir Path tmp) throws IOException {
        ready("riscv64");
        ready("aarch64");
        assertEquals(compileAndRun("riscv64", Target.NATIVE_RISCV64, FIXTURE_RISCV, "riscv64-linux-gnu-as", tmp),
                compileAndRun("aarch64", Target.NATIVE_AARCH64, FIXTURE_AARCH, "aarch64-linux-gnu-as", tmp),
                "regra 5: struct param by value, mesma saída nos dois cross");
    }

    @Test
    void riscv64StructParamWithFloatFieldStaysFfi001(@TempDir Path tmp) throws IOException {
        Path src = tmp.resolve("FloatParam.kf");
        Files.writeString(src, """
                record Mix(Double d, Int i)

                extern "libc.so.6" takes(Mix m): Int

                main() { println("gap") }
                """);
        CompilationResult r = new CompilerDriver().compile(src, tmp.resolve("out-fp"), Target.NATIVE_RISCV64);
        assertFalse(r.success(), "struct param com campo float não pode virar silêncio no cross");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("FFI001"),
                "float/HFA como parâmetro cross permanece FFI001 honesto: " + r.diagnostics().getDiagnostics());
    }

    /** D-MEM-FFI-CROSS-FULL face 3: struct > 16 B por valor agora BINDA (BYREF
     *  — um ponteiro em `a0`/`x0`); a prova por execução é `bigsum` abaixo. */
    @Test
    void riscv64StructParamLargerThan16BytesBinds(@TempDir Path tmp) throws IOException {
        Path src = tmp.resolve("BigParam.kf");
        Files.writeString(src, """
                record Big(Int a, Int b, Int c, Int d, Int e)

                extern "libc.so.6" takes(Big b): Int

                main() { println("bound") }
                """);
        CompilationResult r = new CompilerDriver().compile(src, tmp.resolve("out-big"), Target.NATIVE_RISCV64);
        assertTrue(r.success(), "struct > 16 B (BYREF) deve bindar no cross: "
                + r.diagnostics().getDiagnostics());
    }

    @Test
    void riscv64StructParamLargerThan16BytesByValue(@TempDir Path tmp) throws IOException {
        ready("riscv64");
        assertEquals("142", compileAndRunBig("riscv64", Target.NATIVE_RISCV64,
                BIGFIX_RISCV, "riscv64-linux-gnu-as", tmp),
                "riscv64 struct > 16 B por valor (BYREF: ponteiro do payload em a0)");
    }

    @Test
    void aarch64StructParamLargerThan16BytesByValue(@TempDir Path tmp) throws IOException {
        ready("aarch64");
        assertEquals("142", compileAndRunBig("aarch64", Target.NATIVE_AARCH64,
                BIGFIX_AARCH, "aarch64-linux-gnu-as", tmp),
                "aarch64 struct > 16 B por valor (AAPCS64: ponteiro do payload em x0)");
    }

    @Test
    void crossStructParamLargerThan16BytesAgreesBetweenArchs(@TempDir Path tmp) throws IOException {
        ready("riscv64");
        ready("aarch64");
        assertEquals(compileAndRunBig("riscv64", Target.NATIVE_RISCV64, BIGFIX_RISCV,
                        "riscv64-linux-gnu-as", tmp),
                compileAndRunBig("aarch64", Target.NATIVE_AARCH64, BIGFIX_AARCH,
                        "aarch64-linux-gnu-as", tmp),
                "regra 5: struct > 16 B por valor, mesma saída nos dois cross");
    }
}
