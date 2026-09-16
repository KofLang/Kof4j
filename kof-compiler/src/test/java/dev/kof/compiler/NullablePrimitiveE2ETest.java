package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-NULL-INTENT/N1 (mantenedora 15/09, e04f10ff): `Nullable(primitivo)` agora
 * carrega null de verdade nos 3 targets implementados (JVM/Script/JS — Native
 * é N2, fila separada) em vez de colapsar em 0/false/0.0 (§125 opção A,
 * revogada). Repros diretos das issues #259 (retorno) e #266 (parâmetro),
 * cross-target — o oráculo do nullableprint agregado vive em
 * {@link ConformanceMatrixTest}.
 */
class NullablePrimitiveE2ETest {

    private static String norm(String s) {
        return s == null ? "" : s.replace("\r\n", "\n").trim();
    }

    private String runJvm(Path source, Path outDir) throws IOException {
        CompilationResult r = new CompilerDriver().compile(source, outDir, Target.JVM);
        assertTrue(r.success(), "JVM compile: " + r.diagnostics().getDiagnostics());
        try {
            // Reflection runner: evita o launcher "java" mal-detectar a
            // classe gerada e mascarar VerifyError como falso "JavaFX runtime
            // missing" (mesmo padrão de GenericOperandConcatE2ETest).
            Path runnerDir = outDir.resolveSibling(outDir.getFileName() + "-runner");
            Files.createDirectories(runnerDir);
            Path runnerSrc = runnerDir.resolve("Run.java");
            Files.writeString(runnerSrc, """
                public class Run {
                    public static void main(String[] args) throws Exception {
                        Class.forName(args[0]).getMethod("main", String[].class)
                            .invoke(null, (Object) new String[0]);
                    }
                }
                """);
            Process pCompile = new ProcessBuilder("javac", "-d", runnerDir.toString(), runnerSrc.toString())
                    .redirectErrorStream(true).start();
            String compileOut = new String(pCompile.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(0, pCompile.waitFor(), "runner javac: " + compileOut);
            ProcessBuilder pb = new ProcessBuilder("java", "-cp",
                    outDir.toString() + java.io.File.pathSeparator + runnerDir.toString(), "Run", "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code, output: " + out);
            return norm(out);
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    private String runScript(Path source, Path dir) {
        KofInterpreter.Result r = new CompilerDriver().interpret(List.of(source), dir, new String[0]);
        assertEquals(0, r.exitCode(), "Script exit code, stderr: " + r.stderr());
        return norm(r.stdout());
    }

    private String runJs(Path source, Path outDir) throws IOException {
        CompilationResult r = new CompilerDriver().compile(source, outDir, Target.JS);
        assertTrue(r.success(), "JS compile: " + r.diagnostics().getDiagnostics());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                (InputStream) new ByteArrayInputStream(new byte[0]), out);
        assertEquals(0, ec, "JS exit code, output: " + out);
        return norm(out.toString());
    }

    private void assertAllTargets(String name, String program, String expected, Path tempDir) throws IOException {
        Path dir = tempDir.resolve(name);
        Files.createDirectories(dir);
        Path source = dir.resolve("Main.kf");
        Files.writeString(source, program);
        assertEquals(expected, runJvm(source, dir.resolve("jvm")), "[" + name + "] JVM");
        assertEquals(expected, runScript(source, dir), "[" + name + "] Script");
        assertEquals(expected, runJs(source, dir.resolve("js")), "[" + name + "] JS");
    }

    @Test
    void issue259NullableIntReturnCarriesRealNull(@TempDir Path tempDir) throws IOException {
        assertAllTargets("i259", """
                Int? f() { return null }
                main() {
                    println(f() == null)
                    println(f())
                }
                """, "true\nnull", tempDir);
    }

    @Test
    void issue259NonNullReturnStaysDistinctFromNull(@TempDir Path tempDir) throws IOException {
        // zero/false nao podem se confundir com null (o ponto central do D-NULL-INTENT)
        assertAllTargets("i259b", """
                Int? zero() { return 0 }
                main() {
                    println(zero() == null)
                    println(zero())
                }
                """, "false\n0", tempDir);
    }

    @Test
    void switchExprNullBranchCarriesRealNull(@TempDir Path tempDir) throws IOException {
        assertAllTargets("swexpr", """
                Int? sw(Int x) = switch (x) { case 1 -> 10 default -> null }
                main() {
                    println(sw(1))
                    println(sw(2))
                }
                """, "10\nnull", tempDir);
    }

    @Test
    void genuineNullableComparisonAsIfConditionShortcut(@TempDir Path tempDir) throws IOException {
        // a/b são Nullable(Int) GENUÍNOS (retorno de função, boxed de
        // verdade) usados DIRETO como condição de if — um caminho de
        // lowering separado (CompilerComparisons.emitComparisonShortcut)
        // do `==` avaliado como valor; tinha o mesmo unwrap-p/-numérico do
        // §125 e emitia if_icmpeq sobre uma referência (VerifyError).
        assertAllTargets("cmpshortcut", """
                Int? ni() { return null }
                Int? five() { return 5 }
                main() {
                    var a = ni()
                    var b = ni()
                    if (a == b) { println("iguais") } else { println("dif") }
                    if (a != five()) { println("ne") } else { println("nao-ne") }
                }
                """, "iguais\nne", tempDir);
    }

    @Test
    void issue266NullableBooleanParamCarriesRealNull(@TempDir Path tempDir) throws IOException {
        assertAllTargets("i266", """
                class Handler {
                    void handle(Boolean? flag) {
                        if (flag == null) {
                            println("was null")
                        } else if (flag) {
                            println("true")
                        } else {
                            println("false")
                        }
                    }
                }
                main() {
                    var h = new Handler()
                    h.handle(null)
                    h.handle(true)
                    h.handle(false)
                }
                """, "was null\ntrue\nfalse", tempDir);
    }
}
