package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §747 (the NAT-STR01 pad pair, on the AUTHORIZED D-FULL-PARITY-050
 * row-11 front): {@code strings.padLeft}/{@code padRight} CORRUPTED the value
 * whenever the pad was not ASCII, and mis-counted the pad for a multi-byte
 * victim. Measured before the fix on all three native targets:
 *
 * <ul>
 *   <li>{@code padLeft("7", 3, 'é')} → {@code éé7} on JVM/Script/JS but a
 *       lone-0xE9-per-slot {@code ??7} natively — a MALFORMED Kof String
 *       (invalid UTF-8), not merely an ASCII approximation (same for {@code '€'});</li>
 *   <li>{@code padRight("café", 6, '-')} → {@code café--} on the oracle but
 *       {@code café-} natively: the pad COUNT was computed from the victim's
 *       BYTE length instead of its CHARACTER count.</li>
 * </ul>
 *
 * <p>The measured contract (JVM ≡ Script ≡ JS) is: pad with the pad's FIRST
 * CHARACTER, repeated until the result reaches {@code n} CHARACTERS — a
 * multi-char pad string contributes only its first char ({@code
 * padLeft("7",5,"ab")} = {@code aaaa7}), an empty pad or {@code n <= len(s)}
 * returns the string unchanged.
 *
 * <p>NON-BMP FACE (found 18/10 while authoring the equivalence case): a pad
 * CHARACTER is a CODE POINT, not a UTF-16 code unit. Native already did this
 * (§747 fix); JVM/Script/JS used {@code charAt(0)}/`length`, so an emoji pad
 * printed {@code ??} on those three where native printed the emoji, and a
 * non-BMP VICTIM was counted as two characters. Fixed in
 * {@code JvmStringMathRuntime} (JVM + Script) and {@code JsRuntimeUiStdlib}
 * (JS) with {@code codePointCount}/{@code codePointAt} — the native path is
 * unchanged.
 *
 * <p>Oracle is the CODE-POINT contract, first measured on the native path
 * (which already implemented it); the other three targets were brought to it.
 */
class StringsPadUnicodeE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            main() {
                println(strings.padLeft("7", 5, "ab"))
                println(strings.padLeft("abcd", 2, "x"))
                println(strings.padLeft("", 3, '\\u00e9'))
                println(strings.padRight("7", 4, "ab"))
                println(strings.padLeft("7", 4, '\\u00e9'))
                println(strings.padLeft("7", 5, ""))
                println(strings.padLeft("7", 2, "xyz"))
                println(strings.padRight("caf\\u00e9", 6, '-'))
                println(strings.padLeft("7", 3, '\\u20ac'))
                println(strings.padLeft("ab", 4, "\uD83D\uDE00"))
                println(strings.padRight("ab", 4, "\uD83D\uDE00"))
                println(strings.padRight("\uD83D\uDE00", 3, "-"))
            }
            """;

    private static final String GOLDEN = String.join("\n",
            "aaaa7",
            "abcd",
            "\u00e9\u00e9\u00e9",
            "7aaa",
            "\u00e9\u00e9\u00e9" + "7",
            "7",
            "x7",
            "caf\u00e9--",
            "\u20ac\u20ac7",
            "\uD83D\uDE00\uD83D\uDE00ab",
            "ab\uD83D\uDE00\uD83D\uDE00",
            "\uD83D\uDE00--");

    @Test
    void jvmIsTheContractForStringsPad() throws Exception {
        assertEquals(GOLDEN, runJvm(), "o oráculo JVM mudou — revise o caso");
    }

    @Test
    void scriptStringsPadMatchesTheJvm() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        KofInterpreter.Result r = driver.interpret(
                java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals(GOLDEN, r.stdout().strip());
    }

    @Test
    void jsStringsPadMatchesTheJvm() throws Exception {
        Path dir = tmp.resolve("js");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JS);
        assertTrue(r.success(), () -> "JS compile: " + r.diagnostics().getDiagnostics());
        var so = new java.io.ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), so,
                new java.io.ByteArrayInputStream(new byte[0]), so);
        assertEquals(0, exit, () -> "js saída: " + so);
        assertEquals(GOLDEN, so.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void nativeX86StringsPadMatchesTheJvm() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        Path dir = tmp.resolve("x86");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.NATIVE);
        assertTrue(r.success(), () -> "x86 compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(out.resolve("Default/Main").toString())
                .redirectErrorStream(true).start();
        String o = NativeRiscv64E2ETest.runBounded(p, "x86");
        assertEquals(0, p.exitValue(), () -> "x86 saída: " + o);
        assertEquals(GOLDEN, o);
    }

    @Test
    void riscv64StringsPadMatchesTheJvm() throws Exception {
        runCross("riscv64", Target.NATIVE_RISCV64);
    }

    @Test
    void aarch64StringsPadMatchesTheJvm() throws Exception {
        runCross("aarch64", Target.NATIVE_AARCH64);
    }

    private void runCross(String arch, Target target) throws Exception {
        Assumptions.assumeTrue(NativeRiscv64E2ETest.hasToolchain(arch),
                arch + " cross toolchain/qemu absent");
        Path dir = tmp.resolve(arch);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, target);
        assertTrue(r.success(), () -> arch + " compile: " + r.diagnostics().getDiagnostics());
        String o = NativeRiscv64E2ETest.runQemu(arch, out.resolve("Default/Main"));
        assertEquals(GOLDEN, o.strip());
    }

    private String runJvm() throws Exception {
        Path dir = tmp.resolve("jvm");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JVM);
        assertTrue(r.success(), () -> "JVM compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                "-cp", out.toString(), "Default.Main").redirectErrorStream(true).start();
        String o = NativeRiscv64E2ETest.runBounded(p, "jvm");
        assertEquals(0, p.exitValue(), () -> "oráculo JVM: " + o);
        return o;
    }

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
}
