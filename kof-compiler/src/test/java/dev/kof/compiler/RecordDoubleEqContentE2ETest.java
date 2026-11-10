package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Regressao do §764 — o fold de igualdade de record no JVM usava o `==` IEEE
 * CRU (`DCMPL`/`FCMPL`) para campos `Double`/`Float`, divergindo do contrato
 * de record do javac (`Double.compare` — ordem total) nas DUAS bordas:
 * NaN == NaN deve ser `true`, e +0.0 == -0.0 deve ser `false`. O backend WASI
 * (bits) ja seguia o javac; o JVM era o divergente.
 *
 * A divergencia propaga para as colecoes: `setOf(T(NaN), T(NaN))` deve ter
 * tamanho 1 (javac `HashSet`), nao 2.
 */
class RecordDoubleEqContentE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    // NaN == NaN -> true (javac Double.compare); +0.0 == -0.0 -> false.
    private static final String NAN_AND_ZERO = """
        record T(Double v)
        main() {
            var nan = 0.0 / 0.0
            println(T(nan) == T(nan))
            println(T(0.0) == T(-0.0))
            println(T(1.5) == T(1.5))
            println(T(1.5) == T(2.5))
            println("done")
        }
        """;

    // HashSet de records com NaN: javac junta em 1; hashCode(+) tem de casar.
    private static final String SET_NAN = """
        record T(Double v)
        main() {
            var nan = 0.0 / 0.0
            var s = setOf(T(nan), T(nan))
            println(s.size())
            println(s.contains(T(nan)))
            println("done")
        }
        """;

    // Float usa a MESMA semantica (Float.compare).
    private static final String FLOAT_EDGES = """
        record F(Float v)
        main() {
            var nan = 0.0 / 0.0
            println(F(nan) == F(nan))
            println(F(0.0) == F(-0.0))
            println("done")
        }
        """;

    @Test
    void nanAndSignedZeroJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, NAN_AND_ZERO, "true\nfalse\ntrue\nfalse\ndone");
    }

    @Test
    void setNanDedupJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, SET_NAN, "1\ntrue\ndone");
    }

    @Test
    void floatEdgesJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, FLOAT_EDGES, "true\nfalse\ndone");
    }

    private String runJvm(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        try {
            Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                    "-cp", outDir.toString(), "Default.Main").redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit, output: " + output);
            assertEquals(expected, output, "JVM output");
            return output;
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }
}