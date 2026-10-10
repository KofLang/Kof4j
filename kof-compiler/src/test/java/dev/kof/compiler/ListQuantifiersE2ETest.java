package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * D-MULTIPARADIGMA-PHASE1A slice 1a — eager short-circuit quantifiers
 * {@code any}/{@code all}/{@code none} on {@code List}, reusing the
 * {@code kof_list_*} per-target pattern (map/filter precedent).
 *
 * <p>Semantics (plan §4): predicates use the {@code filter} truthiness rule;
 * loops short-circuit; vacuous: {@code all} true, {@code any}/{@code none}
 * false on empty. Bool results are asserted with {@code assert} (house pattern
 * for Bool parity — rendering of Bool is not pinned here). BOUNDARY:
 * {@code take}/{@code drop}/{@code slice} belong to pagination P1 and are not
 * covered here.</p>
 */
class ListQuantifiersE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private record Run(boolean ok, String output) {}

    /** Vacuous truth table + basic match/mismatch on Ints. */
    private static final String BASIC = """
            main() {
                var xs = listOf(1, 2, 3)
                var empty = listOf()
                assert(xs.any((x) -> x > 2), "any-T")
                assert(!xs.any((x) -> x > 9), "any-F")
                assert(xs.all((x) -> x > 0), "all-T")
                assert(!xs.all((x) -> x > 2), "all-F")
                assert(xs.none((x) -> x > 9), "none-T")
                assert(!xs.none((x) -> x > 0), "none-F")
                assert(!empty.any((x) -> true), "any-empty")
                assert(empty.all((x) -> false), "all-empty")
                assert(empty.none((x) -> true), "none-empty")
            }
            """;

    /** Short-circuit: the throw must never fire (match on element 0).
     * Block lambdas yield only via explicit `return` (SEM033 otherwise). */
    private static final String SHORT = """
            main() {
                var xs = listOf(2, 101, 102)
                assert(xs.any((x) -> {
                    if (x > 100) throw "boom"
                    return x == 2
                }), "short-circuit")
            }
            """;

    private Run runJvm(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JVM);
        if (!r.success()) return new Run(false, diags(r));
        var oldOut = System.out;
        var buf = new ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(buf, true));
        try {
            var cl = new URLClassLoader(new java.net.URL[]{out.toUri().toURL()},
                    getClass().getClassLoader());
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            return new Run(true, buf.toString());
        } catch (ReflectiveOperationException e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            return new Run(false, "THROW: " + c);
        } finally {
            System.setOut(oldOut);
        }
    }

    private Run runJs(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JS);
        if (!r.success()) return new Run(false, diags(r));
        var buf = new ByteArrayOutputStream();
        try {
            int rc = dev.kof.runtime.KofJsRunner.run(
                    out.resolve("Default.mjs"), buf,
                    new ByteArrayInputStream(new byte[0]), buf);
            return new Run(rc == 0, buf.toString());
        } catch (Exception e) {
            return new Run(false, "THROW: " + e.getMessage() + "\n" + buf);
        }
    }

    private Run runScript(Path src, Path root) {
        try {
            var r = driver.interpret(java.util.List.of(src), root, new String[0]);
            return new Run(r.exitCode() == 0, r.stdout());
        } catch (Exception e) {
            return new Run(false, "THROW: " + e.getMessage());
        }
    }

    private Run runNativeX86(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        if (!r.success()) return new Run(false, diags(r));
        Path binary = out.resolve("Default/Main");
        if (!Files.isRegularFile(binary)) return new Run(false, "no binary at " + binary);
        Process p = new ProcessBuilder(binary.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        return new Run(p.waitFor() == 0, output);
    }

    private static String diags(CompilationResult r) {
        StringBuilder sb = new StringBuilder();
        r.diagnostics().getDiagnostics().forEach(d -> sb.append(d.message()).append("\n"));
        return sb.toString();
    }

    /** Exit-0 on JVM + Script + JS (rule 5 — assert() is the parity vehicle). */
    private void assertManagedTargets(Path tmp, String base, String source) throws Exception {
        Path src = tmp.resolve(base + ".kf");
        Files.writeString(src, source);
        Run jvm = runJvm(src, tmp.resolve("o-" + base + "-jvm"));
        assertTrue(jvm.ok(), () -> "JVM: " + jvm.output());
        Run scr = runScript(src, tmp);
        assertTrue(scr.ok(), () -> "Script: " + scr.output());
        Run js = runJs(src, tmp.resolve("o-" + base + "-js"));
        assertTrue(js.ok(), () -> "JS: " + js.output());
    }

    @Test
    void quantifiersBasicVacuous(@TempDir Path tmp) throws Exception {
        assertManagedTargets(tmp, "QuantBasic", BASIC);
    }

    @Test
    void quantifiersShortCircuit(@TempDir Path tmp) throws Exception {
        assertManagedTargets(tmp, "QuantShort", SHORT);
    }

    @Test
    void quantifiersNativeX86(@TempDir Path tmp) throws Exception {
        assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path src = tmp.resolve("QuantNative.kf");
        Files.writeString(src, BASIC);
        Run n = runNativeX86(src, tmp.resolve("o-quant-native"));
        assertTrue(n.ok(), () -> "Native: " + n.output());
        Path src2 = tmp.resolve("QuantNativeShort.kf");
        Files.writeString(src2, SHORT);
        Run n2 = runNativeX86(src2, tmp.resolve("o-quant-native-short"));
        assertTrue(n2.ok(), () -> "Native short-circuit: " + n2.output());
    }

    private void runCross(Path src, String base, Path tmp, Target t, String arch)
            throws Exception {
        assumeTrue(NativeRiscv64E2ETest.hasToolchain(arch),
                "cross " + arch + " toolchain + qemu ausente — pulando");
        Path out = tmp.resolve("o-" + base + "-" + arch);
        CompilationResult r = driver.compile(src, out, t);
        assertTrue(r.success(), arch + " compile: " + diags(r));
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.isRegularFile(bin), arch + " binary must exist");
        NativeRiscv64E2ETest.runQemu(arch, bin);
    }

    @Test
    void quantifiersCrossRiscv64(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("QuantRv.kf");
        Files.writeString(src, BASIC);
        runCross(src, "QuantRv", tmp, Target.NATIVE_RISCV64, "riscv64");
    }

    @Test
    void quantifiersCrossAarch64(@TempDir Path tmp) throws Exception {
        Path src = tmp.resolve("QuantAa.kf");
        Files.writeString(src, BASIC);
        runCross(src, "QuantAa", tmp, Target.NATIVE_AARCH64, "aarch64");
    }
}
