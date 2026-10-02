package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * §549 — vazamento do handler de exceção no caminho NORMAL de um {@code try}
 * nos alvos nativos.
 *
 * <p>Antes do fix, o {@code KofTryEnd} era emitido só no ramo inalcançável do
 * lowering (o corpo do try salta por cima dele), então o handler nativo
 * permanecia vinculado em {@code kof_exc_chain} após o fim do try e capturava o
 * {@code throw SEGUINTE <b>na mesma função</b>. O JVM (tabela estática) e o
 * Script (match por intervalo de pc) sempre estiveram corretos e entram como
 * paridade.</p>
 *
 * <p>Sonda: cada programa termina com um {@code throw "BOOM"} fora de qualquer
 * try, e cada handler imprime um sentinel próprio. Um vazamento re-executa o
 * handler com o BOOM, então a contagem do sentinel fica MAIOR que o esperado.
 * A contagem — não o exit code — é o discriminador exato.</p>
 */
class NativeTryHandlerLeakE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private record Run(boolean ok, String output, int ec) {
        String norm() { return output.replace("\r\n", "\n").trim(); }
    }

    /** sentinel esperado e quantas vezes deve aparecer na saída correta. */
    private record Expect(String sentinel, int count) {}

    private static String program(String tryPart) {
        return "main() {\n" + tryPart + "\n    throw \"BOOM\"\n}\n";
    }

    private static final String NORMAL = program("""
                try {
                    println("N")
                } catch (String e) {
                    println("LEAKED")
                }""");

    private static final String CAUGHT = program("""
                try {
                    throw "e"
                } catch (String e) {
                    println("HANDLED")
                }""");

    private static final String FINALLY = program("""
                try {
                    println("T")
                } finally {
                    println("FIN")
                }""");

    // §551: desvios que ATRAVESSAM uma região try (return/break/continue) têm
    // que desvincular o handler nativo antes de saltar; senão o frame fica
    // pendurado e o throw seguinte (fora da região) dá UAF/captura espúria.
    // O goto ilegal em produção nativa antiga termina com "CAUGHT" (handler
    // fantasma) ou crash/no-output; as âncoras exigem "fim correto".
    private static final String RETURN_VOID = """
            Void f(Bool b) {
                try {
                    println("T")
                    if (b) {
                        return
                    }
                } catch (String e) {
                    println("CAUGHT")
                }
                println("END")
            }
            main() {
                f(true)
                println("AFTER")
                throw "BOOM"
            }
            """;

    private static final String RETURN_VALUE = """
            String g(Bool b) {
                try {
                    if (b) {
                        return "RESULT"
                    }
                } catch (String e) {
                    return "BAD"
                }
                return "FALL"
            }
            main() {
                println(g(true))
                throw "BOOM"
            }
            """;

    private static final String RETURN_FINALLY = """
            String h(Bool b) {
                try {
                    if (b) {
                        return "VALUE"
                    }
                } finally {
                    println("F")
                }
                return "FALL"
            }
            main() {
                println(h(true))
                throw "BOOM"
            }
            """;

    private static final String BREAK_IN_TRY = """
            main() {
                for (var i in listOf(1)) {
                    try {
                        println("T")
                        break
                    } catch (String e) {
                        println("CAUGHT")
                    }
                }
                println("AFTER")
                throw "BOOM"
            }
            """;

    private static final String CONTINUE_IN_TRY = """
            main() {
                for (var i in listOf(1, 2)) {
                    try {
                        println("T" + i)
                        continue
                    } catch (String e) {
                        println("CAUGHT")
                    }
                    println("X")
                }
                println("AFTER")
                throw "BOOM"
            }
            """;

    private static final String NESTED = program("""
                try {
                    try {
                        throw "in"
                    } catch (String e) {
                        println("INNER")
                        throw "out"
                    }
                } catch (String e) {
                    println("OUTER")
                }""");

    private static int count(String hay, String needle) {
        int n = 0, i = 0;
        while ((i = hay.indexOf(needle, i)) >= 0) { n++; i += needle.length(); }
        return n;
    }

    private Run runJvm(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JVM);
        if (!r.success()) return new Run(false, diags(r), -1);
        var buf = new ByteArrayOutputStream();
        var old = System.out;
        System.setOut(new java.io.PrintStream(buf, true));
        try {
            var cl = new URLClassLoader(new java.net.URL[]{out.toUri().toURL()},
                    getClass().getClassLoader());
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            return new Run(true, buf.toString(), 0);
        } catch (ReflectiveOperationException e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            return new Run(false, buf + "THROW: " + c, 1);
        } finally {
            System.setOut(old);
        }
    }

    private Run runNative(Path src, Path out) throws Exception {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("linux"),
                "native roda em Linux");
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        if (!r.success()) return new Run(false, diags(r), -1);
        Path bin = out.resolve("Default/Main");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String outStr = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Run(p.waitFor() == 0, outStr, p.exitValue());
    }

    private Run runCross(Path src, Path out, Target target, String arch) throws Exception {
        assumeTrue(NativeRiscv64E2ETest.hasToolchain(arch),
                "cross " + arch + " + qemu ausente — pulando");
        CompilationResult r = driver.compile(src, out, target);
        if (!r.success()) return new Run(false, diags(r), -1);
        Path bin = out.resolve("Default/Main");
        Process p = NativeRiscv64E2ETest.qemu(arch, bin).redirectErrorStream(true).start();
        String outStr = NativeRiscv64E2ETest.runBounded(p, arch + " leak probe");
        return new Run(p.exitValue() == 0, outStr, p.exitValue());
    }

    private Run runJs(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JS);
        if (!r.success()) return new Run(false, diags(r), -1);
        var buf = new ByteArrayOutputStream();
        int rc = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), buf,
                new ByteArrayInputStream(new byte[0]), buf);
        return new Run(rc == 0, buf.toString(), rc);
    }

    private Run runScript(Path src, Path root) {
        try {
            var r = driver.interpret(java.util.List.of(src), root, new String[0]);
            return new Run(r.exitCode() == 0, r.stdout(), r.exitCode());
        } catch (Exception e) {
            return new Run(false, "THROW: " + e.getMessage(), 1);
        }
    }

    private static String diags(CompilationResult r) {
        return "compile failed: " + r.diagnostics().getDiagnostics();
    }

    private void assertNoLeak(String name, Run r, List<Expect> expects) {
        String o = r.norm();
        for (Expect e : expects) {
            assertEquals(e.count(), count(o, e.sentinel()),
                    name + ": '" + e.sentinel() + "' apareceu " + count(o, e.sentinel())
                            + "x (esperado " + e.count() + ") — handler reexecutou no BOOM? saída='" + o + "'");
        }
    }

    private void allTargets(String name, String program, Expect... expects) throws Exception {
        List<Expect> ex = List.of(expects);
        Path src = tmp.resolve(name + ".kf");
        Files.writeString(src, program);
        assertNoLeak(name + " JVM", runJvm(src, tmp.resolve(name + "-jvm")), ex);
        assertNoLeak(name + " Native x86-64", runNative(src, tmp.resolve(name + "-nat")), ex);
        assertNoLeak(name + " JS", runJs(src, tmp.resolve(name + "-js")), ex);
        assertNoLeak(name + " Script", runScript(src, tmp.resolve(name + "-scr")), ex);
    }

    @TempDir
    Path tmp;

    @Test
    void normalTryBodyDoesNotLeakHandler() throws Exception {
        allTargets("normal", NORMAL, new Expect("LEAKED", 0));
    }

    @Test
    void caughtExceptionDoesNotReenterCatchOnLaterThrow() throws Exception {
        allTargets("caught", CAUGHT, new Expect("HANDLED", 1));
    }

    @Test
    void nestedTryReThrowDoesNotReenterCatchOnLaterThrow() throws Exception {
        allTargets("nested", NESTED, new Expect("INNER", 1), new Expect("OUTER", 1));
    }

    @Test
    void tryFinallyDoesNotRerunFinallyOnLaterThrow() throws Exception {
        allTargets("finally", FINALLY, new Expect("FIN", 1));
    }

    @Test
    void returnVoidInsideTryDoesNotLeakHandler() throws Exception {
        allTargets("return-void", RETURN_VOID,
                new Expect("CAUGHT", 0), new Expect("AFTER", 1), new Expect("END", 0));
    }

    @Test
    void returnValueInsideTryDoesNotLeakHandler() throws Exception {
        allTargets("return-value", RETURN_VALUE,
                new Expect("CAUGHT", 0), new Expect("RESULT", 1), new Expect("FALL", 0));
    }

    @Test
    void returnInsideTryWithFinallyDoesNotLeakHandler() throws Exception {
        allTargets("return-finally", RETURN_FINALLY,
                new Expect("CAUGHT", 0), new Expect("VALUE", 1), new Expect("FALL", 0));
    }

    @Test
    void breakInsideTryDoesNotLeakHandler() throws Exception {
        allTargets("break-try", BREAK_IN_TRY, new Expect("CAUGHT", 0), new Expect("AFTER", 1));
    }

    @Test
    void continueInsideTryDoesNotLeakHandler() throws Exception {
        allTargets("continue-try", CONTINUE_IN_TRY, new Expect("CAUGHT", 0), new Expect("AFTER", 1));
    }

    @Test
    void crossRiscv64ReturnVoidInsideTryDoesNotLeakHandler() throws Exception {
        Path src = tmp.resolve("cross-rv-ret.kf");
        Files.writeString(src, RETURN_VOID);
        assertNoLeak("riscv64", runCross(src, tmp.resolve("cross-rv-ret-out"),
                        Target.NATIVE_RISCV64, "riscv64"),
                List.of(new Expect("CAUGHT", 0), new Expect("AFTER", 1)));
    }

    @Test
    void crossAarch64ReturnVoidInsideTryDoesNotLeakHandler() throws Exception {
        Path src = tmp.resolve("cross-aa-ret.kf");
        Files.writeString(src, RETURN_VOID);
        assertNoLeak("aarch64", runCross(src, tmp.resolve("cross-aa-ret-out"),
                        Target.NATIVE_AARCH64, "aarch64"),
                List.of(new Expect("CAUGHT", 0), new Expect("AFTER", 1)));
    }

    @Test
    void crossRiscv64BreakInsideTryDoesNotLeakHandler() throws Exception {
        Path src = tmp.resolve("cross-rv-brk.kf");
        Files.writeString(src, BREAK_IN_TRY);
        assertNoLeak("riscv64", runCross(src, tmp.resolve("cross-rv-brk-out"),
                        Target.NATIVE_RISCV64, "riscv64"),
                List.of(new Expect("CAUGHT", 0), new Expect("AFTER", 1)));
    }

    @Test
    void crossAarch64BreakInsideTryDoesNotLeakHandler() throws Exception {
        Path src = tmp.resolve("cross-aa-brk.kf");
        Files.writeString(src, BREAK_IN_TRY);
        assertNoLeak("aarch64", runCross(src, tmp.resolve("cross-aa-brk-out"),
                        Target.NATIVE_AARCH64, "aarch64"),
                List.of(new Expect("CAUGHT", 0), new Expect("AFTER", 1)));
    }

    @Test
    void crossRiscv64DoesNotLeakHandler() throws Exception {
        Path src = tmp.resolve("cross-rv.kf");
        Files.writeString(src, NORMAL);
        assertNoLeak("riscv64", runCross(src, tmp.resolve("cross-rv-out"), Target.NATIVE_RISCV64, "riscv64"),
                List.of(new Expect("LEAKED", 0)));
    }

    @Test
    void crossAarch64DoesNotLeakHandler() throws Exception {
        Path src = tmp.resolve("cross-aa.kf");
        Files.writeString(src, NORMAL);
        assertNoLeak("aarch64", runCross(src, tmp.resolve("cross-aa-out"), Target.NATIVE_AARCH64, "aarch64"),
                List.of(new Expect("LEAKED", 0)));
    }
}
