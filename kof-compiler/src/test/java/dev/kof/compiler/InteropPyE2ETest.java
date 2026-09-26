package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.PrintStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * X2 — motor Python do {@code kof.interop} (D-COMPLETE-FIRST item 2, plano
 * {@code docs/development/interop-engine-plan.md}). O motor é código Kof puro
 * ({@code interop-py-host.kf}) sobre {@code process.spawn} (F10) +
 * {@code json.encode/decode} — aqui se prova a face completa do corte
 * declarado da fatia 1: chamadas tipadas de função (Int/Double/Bool/String,
 * args homogêneos via List), erro remoto nomeado (INTEROP006), interpretador
 * ausente/morto nomeado (INTEROP004), paridade JVM≡x86≡JS, e recusa honesta
 * nos alvos sem runtime de processo (host de recusa INTEROP005 injetado no
 * lugar — provada pela classe existir no alvo sem backing).
 */
class InteropPyE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir
    Path tmp;

    private record Run(boolean ok, String output) {}

    private static void requirePython3() {
        assumeTrue(Files.isExecutable(Path.of("/usr/bin/python3")),
                "python3 ausente — motor não executável neste host");
    }

    private Run runJvm(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JVM);
        if (!r.success()) return new Run(false, diags(r));
        var oldOut = System.out;
        var buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true));
        try {
            var cl = new URLClassLoader(new java.net.URL[]{out.toUri().toURL()},
                    getClass().getClassLoader());
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            return new Run(true, buf.toString());
        } catch (java.lang.reflect.InvocationTargetException e) {
            return new Run(false, "THROW: " + e.getCause());
        } finally {
            System.setOut(oldOut);
        }
    }

    private Run runNative(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        if (!r.success()) return new Run(false, diags(r));
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binário nativo deve existir");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Run(p.waitFor() == 0, o);
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

    private static String diags(CompilationResult r) {
        StringBuilder sb = new StringBuilder();
        for (Diagnostic d : r.diagnostics().getDiagnostics()) {
            sb.append(d.code()).append(": ").append(d.message()).append('\n');
        }
        return sb.toString();
    }

    private Run compileAndRun(Path src, Target target) throws Exception {
        return switch (target) {
            case JVM -> runJvm(src, tmp.resolve("out-jvm"));
            case NATIVE -> runNative(src, tmp.resolve("out-nat"));
            case JS -> runJs(src, tmp.resolve("out-js"));
            default -> throw new IllegalArgumentException(target.toString());
        };
    }

    private static final String HAPPY = """
            import kof.interop
            main() {
                var py = KofPy("def sq(n):\\n    return n*n\\ndef add(x):\\n    return x+1\\ndef yes():\\n    return True\\ndef hi(n):\\n    return \\"oi \\"+n")
                println(py.callInt("sq", listOf(5)))
                println(py.callDouble("add", listOf(1.5)))
                println(py.callBool("yes", listOf()))
                println(py.callString("hi", listOf("mel")))
            }
            """;

    @Test
    void typedCallsRoundTripOnJvm() throws Exception {
        requirePython3();
        Path src = tmp.resolve("happy.kf");
        Files.writeString(src, HAPPY);
        Run jvm = runJvm(src, tmp.resolve("out-jvm"));
        assertTrue(jvm.ok(), "JVM deve compilar e rodar: " + jvm.output());
        assertEquals("25\n2.5\ntrue\noi mel\n", jvm.output(),
                "round-trip tipado Int/Double/Bool/String com args e sem args");
    }

    @Test
    void x86AndJsMatchJvmOutput() throws Exception {
        requirePython3();
        Path src = tmp.resolve("happy2.kf");
        Files.writeString(src, HAPPY);
        Run jvm = compileAndRun(src, Target.JVM);
        assertTrue(jvm.ok(), "JVM base: " + jvm.output());
        Run nat = compileAndRun(src, Target.NATIVE);
        assertEquals(jvm.output(), nat.output(), "motor Python JVM≡x86 broken");
        Run js = compileAndRun(src, Target.JS);
        assertEquals(jvm.output(), js.output(), "motor Python JVM≡JS broken");
    }

    @Test
    void remoteErrorIsNamedInterop006WithTraceback() throws Exception {
        requirePython3();
        Path src = tmp.resolve("err.kf");
        Files.writeString(src, """
                import kof.interop
                main() {
                    var py = KofPy("def boom():\n    raise ValueError('kaboom')")
                    try {
                        println(py.callInt("boom", listOf()))
                    } catch (String e) {
                        println(e)
                    }
                }
                """);
        Run jvm = runJvm(src, tmp.resolve("out-err"));
        assertTrue(jvm.ok(), "JVM: " + jvm.output());
        assertTrue(jvm.output().contains("INTEROP006"),
                "falha remota deve sair nomeada: " + jvm.output());
        assertTrue(jvm.output().contains("kaboom"),
                "traceback remoto deve vir na mensagem: " + jvm.output());
    }

    @Test
    void interpreterDeathWithoutResponseIsNamedInterop004() throws Exception {
        requirePython3();
        Path src = tmp.resolve("dead.kf");
        Files.writeString(src, """
                import kof.interop
                main() {
                    var py = KofPy("import os\nos._exit(9)")
                    try {
                        println(py.callInt("sq", listOf(2)))
                    } catch (String e) {
                        println(e)
                    }
                }
                """);
        Run jvm = runJvm(src, tmp.resolve("out-dead"));
        assertTrue(jvm.ok(), "JVM: " + jvm.output());
        assertTrue(jvm.output().contains("INTEROP004"),
                "morte sem resposta deve ser INTEROP004, nunca linha vazia: " + jvm.output());
    }

    @Test
    void refusalAndroidStillSeesTheKofPyShape() throws Exception {
        Path src = tmp.resolve("refusal.kf");
        Files.writeString(src, """
                import kof.interop
                main() {
                    var py = KofPy("def sq(n): return n*n")
                    println(py.callInt("sq", listOf(2)))
                }
                """);
        // No ANDROID (face de processo ainda não PROVADA — R7) o host de
        // recusa define o MESMO shape público — o programa compila (o único
        // erro possível é o gate de emissão COMP003, nunca método/símbolo
        // desconhecido). SCRIPT NÃO é mais exemplo: roda o motor real por
        // paridade de construção — provado em `InteropPyScriptE2ETest`.
        CompilationResult r = driver.compile(src, tmp.resolve("out-ref"), Target.ANDROID);
        String msgs = diags(r);
        // O único erro permitido é o gate de alvo (COMP003/AND004 — emissão
        // ou ambiente), nunca método/símbolo desconhecido: a face existe.
        assertFalse(!r.success() && !msgs.contains("COMP003") && !msgs.contains("AND004"),
                "shape KofPy deve sobreviver a parse/tipo no alvo de recusa: " + msgs);
        assertTrue(msgs.isEmpty() || msgs.contains("COMP003") || msgs.contains("AND004"),
                "recusa deve falhar apenas no gate de emissão/alvo: " + msgs);
    }
}
