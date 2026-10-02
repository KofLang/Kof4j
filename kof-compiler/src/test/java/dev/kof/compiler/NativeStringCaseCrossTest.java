package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D-STR-UNICODE (row 11) — {@code String.toUpperCase}/{@code toLowerCase}
 * fold Unicode per UTF-16 CODE UNIT on the cross-arch (riscv64/aarch64), not
 * ASCII-only. Golden = the JVM measured in the SAME program; the units are read
 * back via {@code toCharArray()} (the already-proven face) so the golden does
 * not depend on stdout encoding. Inputs avoid locale/full-mapping exceptions
 * (ß→SS, ﬁ, İ, Deseret) that are outside the ratified per-code-unit scope.
 * Same discipline as {@link NativeStringsReverseCrossTest}.
 */
class NativeStringCaseCrossTest {

    private final CompilerDriver driver = new CompilerDriver();

    private static boolean has(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c)
                        .redirectErrorStream(true).start();
                if (p.waitFor() != 0) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private String runCross(Path tempDir, String source, String qemu, String archFlag)
            throws IOException {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, source);
        Path outDir = tempDir.resolve("out-" + archFlag);
        CompilationResult result = driver.compile(src, outDir,
                Target.valueOf(archFlag));
        assertTrue(result.success(), "compile: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binary should exist");
        ProcessBuilder pb = NativeRiscv64E2ETest.qemu(qemu.substring(5), bin);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec;
        try {
            ec = p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
        assertEquals(0, ec, "exit code, output: " + output);
        return output;
    }

    // café→CAFÉ (67,65,70,201) ; CAFÉ→café (99,97,102,233) ; Greek upper
    // (902,923,934,913) ; Cyrillic upper (1055,1056,1048,1042,1045,1058) ;
    // a😀b→A😀B (65,55357,56832,66) ; straße lower keeps ß (223) ; Kſı→KSI
    // (75,83,73 — 2-byte→1-byte shrink) ; Ǆǅǆ lower → ǆǆǆ (454×3) ; ""=0.
    private static final String PROGRAM = """
            main() {
                var a = "café".toUpperCase().toCharArray()
                println(a.length)
                for (var i = 0; i < a.length; i++) { println(a[i] as Int) }
                var b = "CAFÉ".toLowerCase().toCharArray()
                println(b.length)
                for (var i = 0; i < b.length; i++) { println(b[i] as Int) }
                var g = "άλφα".toUpperCase().toCharArray()
                println(g.length)
                for (var i = 0; i < g.length; i++) { println(g[i] as Int) }
                var c = "привет".toUpperCase().toCharArray()
                println(c.length)
                for (var i = 0; i < c.length; i++) { println(c[i] as Int) }
                var e = "a😀b".toUpperCase().toCharArray()
                println(e.length)
                for (var i = 0; i < e.length; i++) { println(e[i] as Int) }
                var s = "straße".toLowerCase().toCharArray()
                println(s.length)
                for (var i = 0; i < s.length; i++) { println(s[i] as Int) }
                var k = "Kſı".toUpperCase().toCharArray()
                println(k.length)
                for (var i = 0; i < k.length; i++) { println(k[i] as Int) }
                var d = "Ǆǅǆ".toLowerCase().toCharArray()
                println(d.length)
                for (var i = 0; i < d.length; i++) { println(d[i] as Int) }
                println("".toUpperCase().toCharArray().length)
                println("".toLowerCase().toCharArray().length)
            }
            """;

    private static final String GOLDEN = "4\n67\n65\n70\n201\n4\n99\n97\n102\n233\n"
            + "4\n902\n923\n934\n913\n6\n1055\n1056\n1048\n1042\n1045\n1058\n"
            + "4\n65\n55357\n56832\n66\n6\n115\n116\n114\n97\n223\n101\n"
            + "3\n75\n83\n73\n3\n454\n454\n454\n0\n0";

    // D-FULL-PARITY-050 row 11: compareToIgnoreCase cross — CASE_INSENSITIVE_ORDER
    // do JVM (fold SIMPLES por code unit). Golden = JVM medido (mesmos valores
    // em x86/JS/Script, StringUnicodeFacesMeasuredTest#cicMatchesJvm).
    private static final String CIC_PROGRAM = """
            main() {
                println("straße".compareToIgnoreCase("STRASSE"))
                println("İ".compareToIgnoreCase("i"))
                println("Hello".compareToIgnoreCase("hello"))
                println("ǰ".compareToIgnoreCase("J̌"))
                println("Σ".compareToIgnoreCase("σ"))
                println("café".compareToIgnoreCase("CAFÉ"))
                println("abc".compareToIgnoreCase("abcd"))
                println("".compareToIgnoreCase("x"))
            }
            """;

    private static final String CIC_GOLDEN = "108\n0\n0\n390\n0\n0\n-1\n-1";

    @Test
    void riscv64CaseFoldByCodeUnit(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        assertEquals(GOLDEN, runCross(tempDir, PROGRAM, "qemu-riscv64", "NATIVE_RISCV64"));
    }

    @Test
    void aarch64CaseFoldByCodeUnit(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
        assertEquals(GOLDEN, runCross(tempDir, PROGRAM, "qemu-aarch64", "NATIVE_AARCH64"));
    }

    @Test
    void riscv64CompareToIgnoreCase(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        assertEquals(CIC_GOLDEN, runCross(tempDir, CIC_PROGRAM, "qemu-riscv64", "NATIVE_RISCV64"));
    }

    @Test
    void aarch64CompareToIgnoreCase(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(
                has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
        assertEquals(CIC_GOLDEN, runCross(tempDir, CIC_PROGRAM, "qemu-aarch64", "NATIVE_AARCH64"));
    }
}
