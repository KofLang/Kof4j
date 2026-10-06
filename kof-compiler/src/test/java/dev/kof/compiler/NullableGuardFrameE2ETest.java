package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * #770 (COMP002 frame crash no JVM): um valor T? LIDO apos guarda de null
 * (ramo if / cadeia else-if) ou dentro de uma guarda composta && fazia o
 * backend JVM emitir uma chamada que NAO empilha Bool no caminho narrowed,
 * sobrando um ICONST_0 orfao antes do IF_ICMPNE e estourando o
 * COMPUTE_FRAMES do ASM (ArrayIndexOutOfBoundsException: Index -1), surfado
 * como "frame crash in Default/Main.<fn>". As duas formas abaixo sao os
 * reprodutores medidos da issue (R2 guarda &&, R1 leitura pos-guarda).
 */
class NullableGuardFrameE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    // R2 — leitura narrowed DENTRO de uma guarda && composta.
    private static final String COMPOUND_GUARD = """
            Int takeIt(String? q) {
                if (q != null && q.length() > 0) {
                    var n = math.parseInt(q)
                    return n
                }
                return 0
            }
            main() { println("r=" + takeIt("42")) }
            """;

    // R1 — leitura narrowed pos-guarda early-return em cadeia if.
    private static final String AFTER_NULL_GUARD = """
            String encodeIt(String? q) {
                if (q == null) {
                    return "empty"
                }
                var ci = math.parseInt(q)
                if (ci < 0) {
                    return "neg"
                }
                return "" + q + "/" + ci
            }
            main() { println(encodeIt("5")); println(encodeIt(null)) }
            """;

    private String frameCrashIn(Path dir, String tag, String kof) throws IOException {
        Path src = dir.resolve(tag + ".kf");
        Files.writeString(src, kof);
        CompilationResult r = driver.compile(src, dir.resolve("out-" + tag), Target.JVM);
        if (r.success()) {
            return null;
        }
        String diag = String.valueOf(r.diagnostics().getDiagnostics());
        return (diag.contains("frame crash") || diag.contains("Index -1") || diag.contains("COMP002"))
                ? diag : null;
    }

    @Test
    void compoundGuardNarrowingCompilesOnJvm(@TempDir Path dir) throws Exception {
        String crash = frameCrashIn(dir, "compound", COMPOUND_GUARD);
        assertTrue(crash == null, () -> "JVM deve compilar a guarda && com T? narrowed (sem COMP002): " + crash);
    }

    @Test
    void readAfterNullGuardCompilesOnJvm(@TempDir Path dir) throws Exception {
        String crash = frameCrashIn(dir, "afterguard", AFTER_NULL_GUARD);
        assertTrue(crash == null, () -> "JVM deve compilar a leitura pos-guarda de T? (sem COMP002): " + crash);
    }

    @Test
    void nullResultStillCompilesOnJvm(@TempDir Path dir) throws Exception {
        String kof = """
                Int probe(String? q) {
                    if (q == null) { return 7 }
                    return 0
                }
                main() { println(probe(null)) }
                """;
        String crash = frameCrashIn(dir, "nullret", kof);
        assertFalse(crash != null, () -> "return de literal no ramo null nao deve frame-crash: " + crash);
    }

    private void runsOnJvm(Path dir, String tag, String kof, String expected) throws IOException {
        Path src = dir.resolve(tag + ".kf");
        Files.writeString(src, kof);
        Path out = dir.resolve("run-" + tag);
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), tag + " deve compilar: " + r.diagnostics().getDiagnostics());
        try {
            Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                    "-cp", out + java.io.File.pathSeparator + System.getProperty("java.class.path"),
                    "Default.Main").redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, tag + " exit code, output: " + output);
            assertEquals(expected, output, tag + " output no JVM");
        } catch (InterruptedException e) {
            throw new IOException("interrupted", e);
        }
    }

    // O fix narrowed NAO pode so compilar — tem de Executar certo (prova de
    // runtime do call emitido com o receiver narrowing, incl. o arg primitivo
    // boxed `Int?` que desembrulha p/ `int` formal).
    @Test
    void compoundGuardRunsOnJvm(@TempDir Path dir) throws Exception {
        runsOnJvm(dir, "compoundRun", COMPOUND_GUARD, "r=42");
    }

    @Test
    void readAfterNullGuardRunsOnJvm(@TempDir Path dir) throws Exception {
        runsOnJvm(dir, "afterguardRun", AFTER_NULL_GUARD, "5/5\nempty");
    }

    @Test
    void narrowedPrimArgRunsOnJvm(@TempDir Path dir) throws Exception {
        runsOnJvm(dir, "primArgRun", """
                Int probe(Int? x) {
                    if (x != null) { var n = math.abs(x); return n }
                    return 0
                }
                main() { println(probe(-5)); println(probe(3)); println(probe(null)) }
                """, "5\n3\n0");
    }

    // #772: o reprodutor EXATO da issue — o resultado narrowed concatenado
    // (`"" + absIt(-5)`) passa pelo sugar `String.valueOf` no mesmo call-site
    // do box de erasure; o backend native confundia os dois.
    @Test
    void narrowedPrimArgConcatRunsOnJvm(@TempDir Path dir) throws Exception {
        runsOnJvm(dir, "primConcatRun", """
                Int absIt(Int? x) { if (x != null) { var n = math.abs(x); return n } return 0 }
                Int minIt(Int? a, Int? b) { if (a != null && b != null) { return math.min(a, b) } return 0 }
                main() { println("" + absIt(-5) + "/" + minIt(5, 3)) }
                """, "5/3");
    }

    @Test
    void narrowedIfExpressionRunsOnJvm(@TempDir Path dir) throws Exception {
        runsOnJvm(dir, "ifExprRun", """
                Int probe(String? q) { return if (q != null) { math.parseInt(q) } else { 0 } }
                main() { println(probe("13")); println(probe(null)) }
                """, "13\n0");
    }

    // Parity cross-target (Q3): o narrowing narrowed roda no Script e no JS
    // com o mesmo golden (mesma fonte Kof, mesmo resultado) — espelha o
    // harness de FunctionValueInvokeDescriptorE2ETest (a familia do §603).
    private void runsOnScript(Path dir, String tag, String kof, String expected) throws IOException {
        Path root = dir.resolve("script-" + tag);
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), kof);
        KofInterpreter.Result r = driver.interpret(java.util.List.of(root.resolve("Main.kf")), root, new String[0]);
        assertEquals(0, r.exitCode(), tag + " script exit: " + r.stdout());
        assertEquals(expected, r.stdout().strip(), tag + " script output");
    }

    private void runsOnJs(Path dir, String tag, String kof, String expected) throws IOException {
        Path root = dir.resolve("js-" + tag);
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), kof);
        Path out = root.resolve("out");
        CompilationResult r = driver.compile(root.resolve("Main.kf"), out, Target.JS);
        assertTrue(r.success(), tag + " js compile: " + r.diagnostics().getDiagnostics());
        Path jsFile = out.resolve("Default.mjs");
        assertTrue(Files.isRegularFile(jsFile), tag + " js module must exist");
        var stdout = new ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(jsFile, stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, tag + " js exit: " + stdout.toString(StandardCharsets.UTF_8));
        assertEquals(expected, stdout.toString(StandardCharsets.UTF_8).strip(), tag + " js output");
    }

    @Test
    void compoundGuardRunsOnScriptAndJs(@TempDir Path dir) throws Exception {
        runsOnScript(dir, "compoundScript", COMPOUND_GUARD, "r=42");
        runsOnJs(dir, "compoundJs", COMPOUND_GUARD, "r=42");
    }

    @Test
    void readAfterNullGuardRunsOnScriptAndJs(@TempDir Path dir) throws Exception {
        runsOnScript(dir, "afterguardScript", AFTER_NULL_GUARD, "5/5\nempty");
        runsOnJs(dir, "afterguardJs", AFTER_NULL_GUARD, "5/5\nempty");
    }

    @Test
    void narrowedPrimArgRunsOnScriptAndJs(@TempDir Path dir) throws Exception {
        String kof = """
                Int probe(Int? x) {
                    if (x != null) { var n = math.abs(x); return n }
                    return 0
                }
                main() { println(probe(-5)); println(probe(3)); println(probe(null)) }
                """;
        runsOnScript(dir, "primScript", kof, "5\n3\n0");
        runsOnJs(dir, "primJs", kof, "5\n3\n0");
        String concat = """
                Int absIt(Int? x) { if (x != null) { var n = math.abs(x); return n } return 0 }
                Int minIt(Int? a, Int? b) { if (a != null && b != null) { return math.min(a, b) } return 0 }
                main() { println("" + absIt(-5) + "/" + minIt(5, 3)) }
                """;
        runsOnScript(dir, "primConcatScript", concat, "5/3");
        runsOnJs(dir, "primConcatJs", concat, "5/3");
    }

    @Test
    void narrowedIfExpressionRunsOnScriptAndJs(@TempDir Path dir) throws Exception {
        String kof = """
                Int probe(String? q) { return if (q != null) { math.parseInt(q) } else { 0 } }
                main() { println(probe("13")); println(probe(null)) }
                """;
        runsOnScript(dir, "ifExprScript", kof, "13\n0");
        runsOnJs(dir, "ifExprJs", kof, "13\n0");
    }

    private void runsOnNative(Path dir, String tag, String kof, String expected) throws IOException {
        Path src = dir.resolve(tag + ".kf");
        Files.writeString(src, kof);
        Path out = dir.resolve("nat-" + tag);
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), tag + " deve compilar no Native: " + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(out.resolve("Default/Main").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String got = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec;
        try {
            ec = p.waitFor();
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
        assertEquals(0, ec, tag + " native exit " + ec + ", output: " + got);
        assertEquals(expected, got, tag + " native output");
    }

    @Test
    void narrowedShapesRunOnNative(@TempDir Path dir) throws Exception {
        runsOnNative(dir, "compoundNat", COMPOUND_GUARD, "r=42");
        runsOnNative(dir, "ifExprNat", """
                Int probe(String? q) { return if (q != null) { math.parseInt(q) } else { 0 } }
                main() { println(probe("13")); println(probe(null)) }
                """, "13\n0");
        // #772 (fechado): a face `Int? -> primitivo` no Native era LIXO no
        // baseline pre-#770 (medido 1650458528 no tip 24a23bba9): o backend
        // native confundia o BOX de erasure `Integer.valueOf(int)` (slot MAGIC
        // do §284) com o sugar `String.valueOf(int)` do println -- mesmo nome
        // `valueOf` -- e convertia o box para STRING; o consumidor do slot lia
        // o PONTEIRO como inteiro. Agora pina o golden real nos 4 alvos.
        runsOnNative(dir, "primNat", """
                Int probe(Int? x) {
                    if (x != null) { var n = math.abs(x); return n }
                    return 0
                }
                main() { println(probe(-5)); println(probe(3)); println(probe(null)) }
                """, "5\n3\n0");
        runsOnNative(dir, "concatNat", """
                Int absIt(Int? x) { if (x != null) { var n = math.abs(x); return n } return 0 }
                Int minIt(Int? a, Int? b) { if (a != null && b != null) { return math.min(a, b) } return 0 }
                main() { println("" + absIt(-5) + "/" + minIt(5, 3)) }
                """, "5/3");
    }

    @Test
    void unguardedNullableArgRejected(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("unguarded.kf");
        Files.writeString(src, "Int probe(String? q) { return math.parseInt(q) } main() { println(probe(\"5\")) }");
        CompilationResult r = driver.compile(src, dir.resolve("out-unguarded"), Target.JVM);
        assertFalse(r.success(), () -> "semantica deve rejeitar parseInt(String?) sem guarda (SEM na borda, nao lowering silencioso): "
                + r.diagnostics().getDiagnostics());
    }
}
