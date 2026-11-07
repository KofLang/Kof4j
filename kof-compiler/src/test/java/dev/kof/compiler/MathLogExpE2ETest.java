package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MATH-LOG-EXP (07/11) — the {@code math.log} (natural logarithm) and
 * {@code math.exp} (e^x) primitives on every target. The contract is the
 * IEEE/JDK one: log(1)=0, log(e)=1, log(0)=-Inf, log(-1)=NaN, exp(0)=1,
 * exp(1)=e, exp(-Inf)=0. The values are asserted by INTERVAL narrowing
 * (the libm/fdlibm implementations differ from the JDK by at most 1 ulp —
 * the same reason the trig faces are interval-asserted), plus the exact
 * identities log(1)=0 and exp(0)=1 which are bit-exact everywhere.
 *
 * <p>Targets: JVM, Script (the interpreter over the generated runtime), and
 * native x86_64 (the libm shim + {@code -lm} link-by-use). The cross natives
 * (riscv64/aarch64) share the same shim source and are covered by the
 * existing cross-link tests; WASM is NOT covered here — the fdlibm
 * {@code __ieee754_log}/{@code __ieee754_exp} kernels are a separate slice
 * (the same shape as the sin/tan/atan/pow kernels), recorded in the plan.
 */
class MathLogExpE2ETest {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String PROBE = """
            main() {
                var l1 = math.log(1.0)
                if (l1 == 0.0) {
                    println("log1-ok")
                } else {
                    println("log1-bad")
                }
                var le = math.log(math.e())
                if (le > 0.999 && le < 1.001) {
                    println("loge-ok")
                } else {
                    println("loge-bad")
                }
                var l10 = math.log(10.0)
                if (l10 > 2.302 && l10 < 2.303) {
                    println("log10-ok")
                } else {
                    println("log10-bad")
                }
                var e0 = math.exp(0.0)
                if (e0 == 1.0) {
                    println("exp0-ok")
                } else {
                    println("exp0-bad")
                }
                var e1 = math.exp(1.0)
                if (e1 > 2.718 && e1 < 2.719) {
                    println("exp1-ok")
                } else {
                    println("exp1-bad")
                }
                var round = math.exp(math.log(42.0))
                if (round > 41.99 && round < 42.01) {
                    println("roundtrip-ok")
                } else {
                    println("roundtrip-bad")
                }
                var neg = math.log(-1.0)
                if (neg != neg) {
                    println("nan-ok")
                } else {
                    println("nan-bad")
                }
                var zero = math.log(0.0)
                if (zero < -1.0e300) {
                    println("neginf-ok")
                } else {
                    println("neginf-bad")
                }
            }
            """;

    private static final String EXPECTED =
            "log1-ok\nloge-ok\nlog10-ok\nexp0-ok\nexp1-ok\nroundtrip-ok\nnan-ok\nneginf-ok";

    @Test
    void logExpOnJvm() throws Exception {
        Path root = tmp.resolve("logexp-jvm");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE);
        Path out = root.resolve("out");
        CompilationResult r = driver.compile(root.resolve("Main.kf"), out, Target.JVM);
        assertTrue(r.success(), () -> "compile: " + r.diagnostics().getDiagnostics());
        var stdout = new ByteArrayOutputStream();
        var previousOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(stdout, true, StandardCharsets.UTF_8));
            try (var loader = new java.net.URLClassLoader(
                    new java.net.URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
                Class.forName("Default.Main", true, loader)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            }
        } finally {
            System.setOut(previousOut);
        }
        assertEquals(EXPECTED, stdout.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void logExpOnScript() throws Exception {
        Path root = tmp.resolve("logexp-script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE);
        KofInterpreter.Result result = driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]);
        assertEquals(0, result.exitCode(), "script: " + result.stdout() + result.stderr());
        assertEquals(EXPECTED, result.stdout().strip());
    }

    @Test
    void logExpOnNativeX86() throws Exception {
        Path root = tmp.resolve("logexp-native");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROBE);
        Path out = root.resolve("out-native");
        CompilationResult r = driver.compile(root.resolve("Main.kf"), out, Target.NATIVE);
        assertTrue(r.success(), () -> "compile: " + r.diagnostics().getDiagnostics());
        Path main = out.resolve("Default").resolve("Main");
        if (!Files.exists(main)) main = out.resolve("Main");
        assertTrue(Files.exists(main), () -> "no artifact at " + out);
        var proc = new ProcessBuilder(main.toString()).redirectErrorStream(true).start();
        String text = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        assertEquals(EXPECTED, text, "native run: " + text);
    }
}
