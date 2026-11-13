package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kof.expr slice 6 (`D-KOF-EXPR`) — the CROSS face (riscv64 + aarch64) of the
 * matcher: the same compiled chain of states runs on the cross backend, whose
 * runtime is a stack machine in pure asm ({@code NativeRiscvAsmExpr}; the
 * aarch64 inherits it through the line-by-line translator).
 *
 * <p>The oracle is the JVM: the same source compiled for JVM and for the cross
 * must answer byte-identically. A cross face that merely COMPILES is not
 * parity; parity is the same answers.
 *
 * <p>The test skips honestly when the cross toolchain/qemu is absent (the
 * prefix-aware guard of {@code NativeRiscv64E2ETest.hasToolchain}).
 */
class KofExprCrossE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String CORPUS = """
            expr identifier { one_or_more { either { letter or digit or "_" } } }
            expr uuid {
                exactly(8) hex
                "-"
                exactly(4) hex
                "-"
                exactly(4) hex
                "-"
                exactly(4) hex
                "-"
                exactly(12) hex
            }
            expr date {
                capture("day") { exactly(2) digit }
                "/"
                capture("month") { exactly(2) digit }
            }

            main() {
                println(identifier.matches("user_name_2"))
                println(identifier.matches("user-2"))
                println(uuid.matches("deadBEEF-1234-abcd-AAAA-0123456789ab"))
                println(uuid.matches("geadBEEF-1234-abcd-AAAA-0123456789ab"))
                var m = date.match("25/12")
                if (m != null) {
                    var d = m.day
                    if (d != null) { println(d) }
                    var mo = m.month
                    if (mo != null) { println(mo) }
                } else {
                    println("none")
                }
            }
            """;

    @Test
    void riscv64AnswersIdenticallyToTheJvm() throws Exception {
        assertExprParity("riscv64", Target.NATIVE_RISCV64);
    }

    @Test
    void aarch64AnswersIdenticallyToTheJvm() throws Exception {
        assertExprParity("aarch64", Target.NATIVE_AARCH64);
    }

    private void assertExprParity(String arch, Target target) throws Exception {
        Assumptions.assumeTrue(NativeRiscv64E2ETest.hasToolchain(arch),
                "cross toolchain absent: " + arch);
        String expected = runJvm(arch, CORPUS);

        Path file = tmp.resolve(arch).resolve("Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, CORPUS);
        Path out = tmp.resolve(arch + "-out");
        CompilationResult r = driver.compile(file, out, target);
        assertTrue(r.success(), arch + ": " + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), "the " + arch + " binary must exist");
        String actual = NativeRiscv64E2ETest.runQemu(arch, bin);
        // o binário cross não emite o newline final (o print nativo é sem
        // trailing newline) — a comparação é sobre as LINHAS.
        assertEquals(expected.stripTrailing(), actual.stripTrailing(),
                "the " + arch + " face must answer like the JVM");
    }

    private String runJvm(String tag, String src) throws Exception {
        Path file = tmp.resolve(tag + "-jvm").resolve("Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        Path out = tmp.resolve(tag + "-jvm-out");
        CompilationResult r = driver.compile(file, out, Target.JVM);
        assertTrue(r.success(), tag + " (jvm): " + r.diagnostics().getDiagnostics());
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try (URLClassLoader cl = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
            Class.forName("Default.Main", true, cl).getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(old);
        }
        return buf.toString(StandardCharsets.UTF_8);
    }
}
