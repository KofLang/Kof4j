package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #233 regression (root cause found by lane bugs-and-gaps 14/09).
 *
 * The wrapper-static branch added to {@code ExpressionMethodCallLowerer} matched
 * a builtin receiver ({@code String}/{@code Int}/...) even when the external
 * classpath was present but did NOT contain the class (the common case: plain
 * Kof compilation), and then emitted NOTHING for calls other than
 * {@code isNaN}/{@code isInfinite}/{@code isFinite}. The call was silently
 * dropped (R6), so the enclosing {@code println} emitted its own
 * {@code String.valueOf(Object)} with no value on the stack →
 * ASM COMPUTE_FRAMES crash ({@code NegativeArraySizeException: -1}).
 *
 * Before the fix, {@code String.valueOf(char)} inside {@code println} broke;
 * {@code Double.isNaN} (#233) and the {@code parse*} statics were affected the
 * same way when their wrapper branch did not match.
 */
class WrapperStaticCallsE2ETest extends JvmJsRunSupport {

    private String runNativeX86(Path outDir) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder(outDir.resolve("Default/Main").toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "Native x86 exit code, output: " + output);
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    // Exact repro of the regression: two `String.valueOf(char)` calls inside
    // println. The first crashed ASM; both must return the UTF-8 char.
    @Test
    void stringValueOfCharInsidePrintln(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    println(String.valueOf(104 as Char))
                    println(String.valueOf(72 as Char))
                }
                """, "h\nH", tempDir, "wrap-valueof-char");
    }

    // valueOf of other primitives must keep working (same swallowed branch).
    @Test
    void stringValueOfPrimitivesInsidePrintln(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    println(String.valueOf(42))
                    println(String.valueOf(7L))
                    println(String.valueOf(true))
                }
                """, "42\n7\ntrue", tempDir, "wrap-valueof-primitives");
    }

    // #233 face: Double/Float isNaN/isInfinite (the branch that motivated the
    // code) plus the parse* statics that also flow through the same dispatch.
    //
    // JVM + JS: the JS backend NOW lowers wrapper statics (`Double.isNaN`,
    // `Int.parseInt`, …) to runtime helpers (§235, fixed 16/09 by lane
    // development 192.168.100.18 — before it threw `ReferenceError:
    // java_lang_Double is not defined`). `parse*` reuse the `kof_string_to_*`
    // helpers (github #51/§81); `parseBoolean` is `"true".equals(ignoreCase)`.
    @Test
    void wrapperIsAndParseStatics(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var d: Double = 0.0 / 0.0
                    println(Double.isNaN(d))
                    println(Double.isInfinite(d))
                    println(Int.parseInt("42") + 1)
                    println(Long.parseLong("100") * 2L)
                    println(Double.parseDouble("3.5") + 0.5)
                    println(Bool.parseBoolean("true"))
                }
                """, "true\nfalse\n43\n200\n4.0\ntrue", tempDir, "wrap-is-parse");
    }

    // §235 NATIVE face: the wrapper statics must compile+run on native (before:
    // `undefined reference to java_lang_Integer_parseInt [COMP001]`). The golden
    // is the JVM oracle of this exact program (`wrapperStaticsJvmOracle`).
    // Covers the Q3 edges: NaN/inf/finite both ways, parse* happy path, and
    // parseBoolean case-insensitivity (no trim — JVM contract).
    static final String WRAPPER_STATICS_SRC = """
            main() {
                var nan: Double = 0.0 / 0.0
                var inf: Double = 1.0 / 0.0
                println(Double.isNaN(nan))
                println(Double.isNaN(1.5))
                println(Double.isInfinite(inf))
                println(Double.isInfinite(1.5))
                println(Double.isFinite(1.5))
                println(Double.isFinite(inf))
                println(Double.isFinite(nan))
                println(Int.parseInt("42") + 1)
                println(Long.parseLong("100") * 2L)
                println(Double.parseDouble("3.5") + 0.5)
                println(Bool.parseBoolean("true"))
                println(Bool.parseBoolean("TRUE"))
                println(Bool.parseBoolean("false"))
                var fnan: Float = Float.parseFloat("NaN")
                println(Float.isNaN(fnan))
                println(Float.isFinite(fnan))
                println(Float.parseFloat("2.5"))
            }
            """;
    static final String WRAPPER_STATICS_GOLDEN =
            "true\nfalse\ntrue\nfalse\ntrue\nfalse\nfalse\n43\n200\n4.0\ntrue\ntrue\nfalse\ntrue\nfalse\n2.5";

    @Test
    void wrapperStaticsJvmOracle(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("wrap-oracle.kf");
        Files.writeString(src, WRAPPER_STATICS_SRC);
        Path out = tempDir.resolve("wrap-oracle-out");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals(WRAPPER_STATICS_GOLDEN, runJvm(out), "JVM oracle mismatch");
    }

    @Test
    void wrapperStaticsNativeX86(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("wrap-native.kf");
        Files.writeString(src, WRAPPER_STATICS_SRC);
        Path out = tempDir.resolve("wrap-native-out");
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), "Native compile failed: " + r.diagnostics().getDiagnostics());
        assertEquals(WRAPPER_STATICS_GOLDEN, runNativeX86(out), "Native x86 output mismatch");
    }

    // §264 (JS): Double/Float print no contrato do JDK — inteiro com ponto
    // ("4.0"), científico com E ("1.0E7"), -0.0, e round-trip curto. Antes
    // o JS imprimia `String(v)` cru: "4", "10000000" (silencioso, R6).
    @Test
    void doubleFloatJdkPrintFormat(@TempDir Path tempDir) throws IOException {
        runBoth("""
                main() {
                    var d: Double = 4.0
                    var f: Float = 6.0
                    println(d)
                    println(f)
                    println("v=" + d)
                    println(String.valueOf(d))
                    println(d.toString())
                    println(Double.toString(d))
                    println(10000000.0)
                    println(0.00001)
                    println(-0.0)
                }
                """, "4.0\n6.0\nv=4.0\n4.0\n4.0\n4.0\n1.0E7\n1.0E-5\n-0.0",
                tempDir, "wrap-numfmt");
    }
}
