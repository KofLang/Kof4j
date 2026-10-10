package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #459 — {@code X as T <op> Y} (cast à esquerda de operador de precedência
 * maior, sem parênteses) engolia o operador + RHS no parse do type-ref do
 * {@code as}: {@code a as Double / 2.0} virava um type-ref malformado
 * ({@code "?"}) e o {@code ddiv} nunca era emitido — {@code check} passava
 * "no errors" e o runtime explodia com {@code NoClassDefFoundError: ?} (R6:
 * corrupção silenciosa). Parsers de {@code as}/{@code instanceof} agora usam
 * o caminho type-only ({@link TypeParser#parseTypeRef}) e devolvem o controle
 * ao loop de precedência — {@code a as Double / 2.0} = {@code (a as Double) / 2.0}
 * (tabela do grammar §5.1: {@code as}=5, aritmética=7/8).
 *
 * <p>Q3: cada operador da matriz da issue (+ - * / % &lt;&lt; &gt;&gt; &gt;&gt;&gt;),
 * cast à esquerda e à direita (controle — já era correto), instanceof com o
 * MESMO problema, type-ref completo ({@code List<Int>}, {@code Int[]},
 * {@code Int?}) sem regressão (bug 127: {@code () -> Int} continua type-ref).
 * Cross-target JVM/Script/JS com o mesmo output.
 */
class AsCastPrecedenceE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path outDir) throws Exception {
        try {
            ProcessBuilder pb = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                    "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "JVM exit code, output: " + output);
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    private void runAllThree(Path tempDir, String name, String source, String expected) throws Exception {
        Path src = tempDir.resolve(name + ".kf");
        Files.writeString(src, source);
        Path out = tempDir.resolve(name + "-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), name + " JVM compile: " + r.diagnostics().getDiagnostics());
        assertEquals(expected, runJvm(out), name + " JVM");
        KofInterpreter.Result i = driver.interpret(java.util.List.of(src), src.getParent(), new String[0]);
        assertEquals(0, i.exitCode(), name + " Script exit/stderr: " + i.stdout() + " " + i.stderr());
        assertEquals(expected, i.stdout().trim(), name + " Script");
        Path jsOut = tempDir.resolve(name + "-js");
        CompilationResult js = driver.compile(src, jsOut, Target.JS);
        assertTrue(js.success(), name + " JS compile: " + js.diagnostics().getDiagnostics());
        ProcessBuilder jb = new ProcessBuilder("node", jsOut.resolve("Default.mjs").toString());
        jb.redirectErrorStream(true);
        Process jp = jb.start();
        String jout = new String(jp.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
            .replace("\r\n", "\n").trim();
        assertEquals(0, jp.waitFor(), name + " JS exit, output: " + jout);
        assertEquals(expected, jout, name + " JS");
    }

    @Test
    void verbatimIssueReproBindsCastBeforeDiv(@TempDir Path tempDir) throws Exception {
        runAllThree(tempDir, "as459p", """
                main() {
                    var a = 1
                    println(a as Double / 2.0)
                }
                """, "0.5");
    }

    @Test
    void castOnLeftOfEveryArithAndShiftOp(@TempDir Path tempDir) throws Exception {
        runAllThree(tempDir, "as459m", """
                main() {
                    var a = 4
                    var b = 2
                    println(a as Int + b)
                    println(a as Int - b)
                    println(a as Int * b)
                    println(a as Int / b)
                    println(a as Int % 3)
                    println(a as Int << b)
                    println(a as Int >> b)
                    println(a as Int >>> b)
                }
                """, "6\n2\n8\n2\n1\n16\n1\n1");
    }

    @Test
    void castOnRightOperandUnchangedAsControl(@TempDir Path tempDir) throws Exception {
        runAllThree(tempDir, "as459r", """
                main() {
                    var a = 4
                    var b = 2
                    println(a / b as Int)
                    println(a + b as Int)
                }
                """, "2\n6");
    }

    @Test
    void comparisonAndEqualityAfterCastStillBindLeft(@TempDir Path tempDir) throws Exception {
        runAllThree(tempDir, "as459c", """
                main() {
                    var n = 7
                    println(n as Int == 7)
                    println(n as Double > 3.0)
                }
                """, "true\ntrue");
    }

    @Test
    void fullTypeRefGrammarAfterCastUnaffected(@TempDir Path tempDir) throws Exception {
        runAllThree(tempDir, "as459t", """
                main() {
                    var xs = listOf(2, 3)
                    var l = xs as List<Int>
                    println(l.size + 0)
                    var m = new Int[2]
                    m[0] = 1
                    m[1] = 2
                    var a = m as Int[]
                    println(a[0] + a[1])
                }
                """, "2\n3");
    }

    @Test
    void instanceOfBindsTypeFirstAndReportsBoolArithHonestly(@TempDir Path tempDir) throws Exception {
        runAllThree(tempDir, "as459i", """
                main() {
                    var o = "x"
                    println(o instanceof String == true)
                }
                """, "true");
        Path src = tempDir.resolve("as459b.kf");
        Files.writeString(src, """
                main() {
                    var o = "x"
                    var n = 5
                    println(o instanceof String + n)
                }
                """);
        CompilationResult r = driver.compile(src, tempDir.resolve("as459b-jvm"), Target.JVM);
        assertFalse(r.success(), "#459: antes o `+ n` era engolido pelo type-ref (silent drop); agora o `+` acha Bool a esquerda");
        assertTrue(r.diagnostics().getDiagnostics().stream().anyMatch(d ->
                d.message().contains("boolean") || "SEM002".equals(d.code())),
                "diagnostico honesto do Bool + Int: " + r.diagnostics().getDiagnostics());
    }
}
