package dev.kof.compiler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §367 (#548) — {@code println(result)} de process/shell imprime por
 * CONTEUDO em todos os alvos: {@code ProcessResult[exitCode=0, stdout=x,
 * stderr=]}. Antes vazava identidade Java crua
 * ({@code dev.kof.runtime.KofRuntime$ProcessResult@<hash>}) em JVM e Script
 * — FQCN do runtime + hash diferente a cada execucao (nondeterminismo na
 * superficie do usuario), violando o golden do corpus (record é impresso
 * {@code P[x=1, y=2]}) e a regra 8 ("Kof nao e Java").
 *
 * <p>Prova 3-alvos de conteudo: JVM (compilado), Script (interpretador) e JS
 * (host runner — shim + bridge). A paridade do registro inteiro nos alvos
 * nativos e coberta por {@link ProcessResultWholePrintE2ETest}; aqui ficam os
 * acessos a campos ({@code r.stdout},
 * {@code r.exitCode}) nao mudam (additive, freeze 2).
 */
class ProcessResultContentE2ETest {

    private static final String SRC = """
            import kof.process
            main() {
              var r = process.run("echo", "x")
              println(r)
              println("F:" + r.exitCode + "|" + r.stdout)
            }
            """;

    private static String diags(CompilationResult r) {
        StringBuilder sb = new StringBuilder();
        r.diagnostics().getDiagnostics().forEach(d -> sb.append(d.message()).append("\n"));
        return sb.toString();
    }

    private static final String EXPECTED = "ProcessResult[exitCode=0, stdout=x, stderr=]\nF:0|x";

    @Test
    void jvmPrintsProcessResultByContent(@TempDir Path tmp) throws Exception {
        CompilerDriver driver = new CompilerDriver();
        Path src = tmp.resolve("M.kf");
        Files.writeString(src, SRC);
        Path out = tmp.resolve("out");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM compile: " + diags(r));
        String[] cp = {out.toString(), System.getProperty("user.dir") + "/../kof-runtime/target/classes"};
        Process p = new ProcessBuilder(TestJdk.javaBin(), "-cp", String.join(":", cp), "Default.Main")
                .redirectErrorStream(true).start();
        String s = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "JVM run, output: " + s);
        assertEquals(EXPECTED, s, "JVM");
        assertFalse(s.contains("KofRuntime$"),
                "identidade Java vazou no JVM: " + s);
    }

    @Test
    void scriptPrintsProcessResultByContent(@TempDir Path tmp) throws Exception {
        CompilerDriver driver = new CompilerDriver();
        Path src = tmp.resolve("M.kf");
        Files.writeString(src, SRC);
        KofInterpreter.Result i = driver.interpret(java.util.List.of(src), src.getParent(), new String[0]);
        assertEquals(0, i.exitCode(), "Script exit/stderr: " + i.stdout() + " " + i.stderr());
        assertEquals(EXPECTED, i.stdout().replace("\r\n", "\n").trim(), "Script");
        assertFalse(i.stdout().contains("KofRuntime$"), "identidade Java vazou no Script");
    }

    @Test
    void jsHostRunnerPrintsProcessResultByContent(@TempDir Path tmp) throws Exception {
        CompilerDriver driver = new CompilerDriver();
        Path src = tmp.resolve("M.kf");
        Files.writeString(src, SRC);
        Path out = tmp.resolve("js");
        CompilationResult r = driver.compile(src, out, Target.JS);
        assertTrue(r.success(), "JS compile: " + diags(r));
        var buf = new ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), buf,
                new ByteArrayInputStream(new byte[0]), buf);
        String s = buf.toString().replace("\r\n", "\n").trim();
        assertEquals(0, ec, "JS run, output: " + s);
        assertEquals(EXPECTED, s, "JS host");
    }

    @Test
    void shellRunWithPrintsProcessResultByContent(@TempDir Path tmp) throws Exception {
        CompilerDriver driver = new CompilerDriver();
        Path src = tmp.resolve("S.kf");
        Files.writeString(src, """
                import kof.shell
                main() {
                  var r = shell.run("echo", listOf("hi"))
                  println(r)
                }
                """);
        KofInterpreter.Result i = driver.interpret(java.util.List.of(src), src.getParent(), new String[0]);
        assertEquals(0, i.exitCode(), "Script: " + i.stderr());
        assertEquals("ProcessResult[exitCode=0, stdout=hi, stderr=]",
                i.stdout().replace("\r\n", "\n").trim(), "Script shell.run conteudo");
    }
}
