package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * issue #850: the return type of a JDK static receiver (`Long.toHexString`,
 * `String.valueOf`, ...) was not inferred — the call resolved to UNKNOWN, so
 * `var h = Long.toHexString(255L)` made `h` UNKNOWN and the overload resolution
 * of a stdlib call in ARGUMENT position (`strings.padLeft(h, 8, "0")`) failed
 * with SEM025, while the same call with a literal or with an explicit
 * `String t = ...` worked. The fix resolves the method through the JDK
 * reflection resolver and returns its real return type.
 *
 * <p>Oracle is the JVM. The last two probes are CONTROLS: `Long.parseLong("42")
 * + 1` must stay ARITHMETIC (`43`, so the fix must not blanket-map static
 * receivers to String) and `Double.isNaN(1.0)` must be usable as a Bool.
 */
class StaticReceiverReturnTypeE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            main() {
                println("literal=" + strings.padLeft("ff", 8, "0"))
                var h = Long.toHexString(255L)
                println("var=" + strings.padLeft(h, 8, "0"))
                println("direto=" + strings.padLeft(Long.toHexString(255L), 8, "0"))
                println("valueOf=" + strings.padLeft(String.valueOf(5L), 3, "0"))
                println("parse=" + (Long.parseLong("42") + 1))
                if (Double.isNaN(1.0)) {
                    println("nan=sim")
                } else {
                    println("nan=nao")
                }
            }
            """;

    private static final String GOLDEN = String.join("\n",
            "literal=000000ff",
            "var=000000ff",
            "direto=000000ff",
            "valueOf=005",
            "parse=43",
            "nan=nao");

    /** The #850 face: pre-fix this program does not COMPILE (SEM025 on padLeft). */
    @Test
    void jvmInfersTheStaticReceiverReturnType() throws Exception {
        assertEquals(GOLDEN, runJvm("oracle"), "o oráculo JVM mudou — revise o caso");
    }

    /**
     * Negative control: the honest SEM074 gate for an unknown static method must
     * survive the fix (it is the same receiver path that now resolves types).
     */
    @Test
    void unknownStaticMethodIsStillRejected() throws Exception {
        assertEquals("SEM074", firstCodeFor("""
                main() {
                    println(Long.bogus(1L))
                }
                """));
    }

    /**
     * Negative control: the return type must be the REAL one, not String for
     * everything — `Bool` from `Double.isNaN` cannot be a `String` argument.
     */
    @Test
    void boolStaticResultIsNotAcceptedAsString() throws Exception {
        assertEquals("SEM025", firstCodeFor("""
                main() {
                    println(strings.padLeft(Double.isNaN(1.0), 8, "0"))
                }
                """));
    }

    // ── harness ────────────────────────────────────────────────────────────

    private String firstCodeFor(String src) throws Exception {
        Path dir = Files.createTempDirectory(tmp, "diag");
        Files.writeString(dir.resolve("Main.kf"), src);
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), dir.resolve("o"), Target.JVM);
        assertFalse(r.success(), "o programa devia ser rejeitado:\n" + src);
        var ds = r.diagnostics().getDiagnostics();
        assertFalse(ds.isEmpty(), "sem diagnóstico (nunca silencioso)");
        return ds.get(0).code();
    }

    private String runJvm(String name) throws Exception {
        Path dir = tmp.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JVM);
        assertTrue(r.success(), () -> "compila (#850): " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                "-cp", out.toString(), "Default.Main").redirectErrorStream(true).start();
        String o = NativeRiscv64E2ETest.runBounded(p, name + " jvm");
        assertEquals(0, p.exitValue(), () -> name + ": " + o);
        return o;
    }
}