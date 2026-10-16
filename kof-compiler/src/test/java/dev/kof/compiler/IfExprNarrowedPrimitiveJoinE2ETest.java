package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #870 — o JOIN de if-expression de um `Int?` ESTREITADO + literal `Int`.
 *
 * <pre>
 *   if (opt != null) { opt } else { 0 }     // opt: Int?
 * </pre>
 *
 * O ramo `then` é o PRÓPRIO local estreitado: por dentro do if ele é não-nulo,
 * então os dois ramos são `Int` e o join tem de ser `Int`. O tipador tratava o
 * ramo pelo tipo DECLARADO (`Int?`), os ramos divergiam, o join caía em
 * `java.lang.Object` e o consumidor (`v + 5`) emitia `iadd` sobre a CAIXA:
 * JVM `VerifyError: Bad type on operand stack` (escondido pela mensagem do
 * launcher JavaFX), Native rc=0 com saída VAZIA, Script/JS corretos — divergência
 * cross-target de um programa VÁLIDO.
 *
 * A correção: o join enxerga o narrowing que o semântico já coleta
 * (`StatementAnalyzer.collectNarrowing`, `x != null` → ramo then), os dois ramos
 * unificam no primitivo e o ramo fisicamente BOXEADO (o local `Int?`, boxed por
 * D-NULL-INTENT) é DESBOXADO na convergência — o mesmo idioma que a família
 * #845/#848/#874 usa nos consumidores.
 *
 * O oráculo é a JVM (rule 5): a MESMA saída tem de valer em Script, JS e no
 * Native x86-64.
 */
class IfExprNarrowedPrimitiveJoinE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    /** Local estreitado: o caso exato da issue. */
    private static final String LOCAL = """
            Int? findPos(Int i) {
                if (i < 0) { return null }
                return i
            }

            main() {
                var opt = findPos(-1)
                var v = if (opt != null) { opt } else { 0 }
                println(v + 5)
                var opt2 = findPos(7)
                var w = if (opt2 != null) { opt2 } else { 0 }
                println(w + 5)
            }
            """;

    /** Campo estreitado (`this.data != null`) — a mesma regra, outra superfície. */
    private static final String FIELD = """
            class Caixa {
                Int? data

                Caixa(Int? d) { this.data = d }

                Int total() {
                    var v = if (this.data != null) { this.data } else { 0 }
                    return v + 5
                }
            }

            main() {
                println(Caixa(null).total())
                println(Caixa(9).total())
            }
            """;

    private static final String EXPECTED_LOCAL = "5\n12";
    private static final String EXPECTED_FIELD = "5\n14";

    private Path write(Path dir, String tag, String program) throws Exception {
        Path src = dir.resolve("Main-" + tag + ".kf");
        Files.writeString(src, program);
        return src;
    }

    private String runJvmOracle(Path src, Path outDir) throws Exception {
        CompilationResult rj = driver.compile(src, outDir, Target.JVM);
        assertTrue(rj.success(), "JVM compile: " + rj.diagnostics().getDiagnostics());
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder pb = new ProcessBuilder(java, "--enable-native-access=ALL-UNNAMED",
                "-cp", outDir.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "JVM oracle run (a VerifyError shows as the launcher message): " + out);
        return out;
    }

    private void assertFace(String tag, String program, String expected, Path tempDir) throws Exception {
        Path src = write(tempDir, tag, program);
        String oracle = runJvmOracle(src, tempDir.resolve("out-jvm-" + tag));
        assertEquals(expected, oracle, tag + ": JVM oracle golden");

        KofInterpreter.Result script = driver.interpret(java.util.List.of(src), tempDir, new String[0]);
        assertEquals(0, script.exitCode(), tag + ": Script exit, stderr=" + script.stderr());
        assertEquals(expected, script.stdout().trim(), tag + ": Script must match the JVM oracle (rule 5)");

        Path outJs = tempDir.resolve("out-js-" + tag);
        CompilationResult rjs = driver.compile(src, outJs, Target.JS);
        assertTrue(rjs.success(), tag + ": JS compile: " + rjs.diagnostics().getDiagnostics());
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(outJs.resolve("Default.mjs"), stdout,
                new ByteArrayInputStream(new byte[0]), stdout);
        String js = stdout.toString(StandardCharsets.UTF_8).strip();
        assertEquals(0, exit, tag + ": JS exit, output: " + js);
        assertEquals(oracle, js, tag + ": JS must match the JVM oracle (rule 5)");
    }

    @Test
    void narrowedLocalJoin(@TempDir Path tempDir) throws Exception {
        assertFace("local", LOCAL, EXPECTED_LOCAL, tempDir);
    }

    @Test
    void narrowedFieldJoin(@TempDir Path tempDir) throws Exception {
        assertFace("field", FIELD, EXPECTED_FIELD, tempDir);
    }

    @Test
    void narrowedLocalJoinNativeX86(@TempDir Path tempDir) throws Exception {
        Assumptions.assumeTrue(NativeToolchainAssumptions.hasTool("as", "ld"), "native toolchain absent");
        Path src = write(tempDir, "nat", LOCAL);
        String oracle = runJvmOracle(src, tempDir.resolve("out-jvm-nat"));
        Path out = tempDir.resolve("out-nat");
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), "native compile: " + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), "native binary must exist");
        ProcessBuilder pb = new ProcessBuilder(bin.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertEquals(0, p.waitFor(), "native output: " + output);
        assertEquals(oracle, output, "Native x86-64 must match the JVM oracle (rule 5)");
    }
}