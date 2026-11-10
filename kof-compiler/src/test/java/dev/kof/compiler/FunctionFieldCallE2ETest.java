package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §820 — a function-typed field could not be called as a method:
 * {@code h.run()} was SEM025 "Cannot resolve method 'run' on type 'H'", and a
 * second function-typed field with parameters on the next line
 * ({@code (Int) -> String show}) was read as the first field's parameter list
 * (PARSE023). The call now invokes the field value when the class has no
 * method of that name (a method with the same name wins), and each field
 * declaration ends at its line.
 */
class FunctionFieldCallE2ETest {

    private static final String SRC = """
            class Base {
                (Int) -> String show
                Base((Int) -> String s) { this.show = s }
            }

            class H extends Base {
                () -> Int run
                (Int, Int) -> Int add
                () -> Void ping
                H(() -> Int f) {
                    super((n: Int) -> "n=" + n)
                    this.run = f
                    this.add = (a: Int, b: Int) -> a + b
                    this.ping = () -> { println("ping") }
                }
                Int twice() { return run() * 2 + this.run() }
            }

            main() {
                val h = H(() -> 21)
                println(h.run())
                println(h.show(5))
                println(h.add(2, 3))
                h.ping()
                println(h.twice())
                var hs = listOf<H>()
                hs.add(h)
                println(hs.get(0).run() + 1)
            }
            """;

    private static final String GOLDEN = "21\nn=5\n5\nping\n63\n22\n";

    @TempDir Path tmp;

    @Test
    void aFunctionTypedFieldIsCalledLikeAMethodOnJvmScriptAndJs() throws Exception {
        Path src = write("Main.kf", SRC);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult jvm = driver.compile(src, tmp.resolve("jvm"), Target.JVM);
        assertTrue(jvm.success(), "JVM compile: " + jvm.diagnostics().getDiagnostics());
        assertEquals(GOLDEN, run(System.getProperty("java.home") + "/bin/java", "-cp",
                tmp.resolve("jvm").toString(), "Default.Main"), "JVM");
        KofInterpreter.Result script = driver.interpret(List.of(src), tmp, new String[0]);
        assertEquals(GOLDEN, script.stdout(), "Script, stderr: " + script.stderr());
        CompilationResult js = driver.compile(src, tmp.resolve("js"), Target.JS);
        assertTrue(js.success(), "JS compile: " + js.diagnostics().getDiagnostics());
        assertEquals(GOLDEN, run("node", tmp.resolve("js/Default.mjs").toString()), "JS");
    }

    @Test
    void aMethodWithTheSameNameWins() throws Exception {
        Path src = write("Same.kf", """
                class W {
                    () -> String label
                    W() { this.label = () -> "field" }
                    String label() { return "method" }
                }
                main() { println(W().label()) }
                """);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult jvm = driver.compile(src, tmp.resolve("same"), Target.JVM);
        assertTrue(jvm.success(), "JVM compile: " + jvm.diagnostics().getDiagnostics());
        assertEquals("method\n", run(System.getProperty("java.home") + "/bin/java", "-cp",
                tmp.resolve("same").toString(), "Default.Main"));
        assertEquals("method\n", driver.interpret(List.of(src), tmp, new String[0]).stdout());
    }

    @Test
    void aMissingMemberAndAWrongArityAreStillErrors() throws Exception {
        CompilationResult missing = new CompilerDriver().compile(write("Missing.kf", """
                class H {
                    () -> Int run
                    H() { this.run = () -> 1 }
                }
                main() { println(H().walk()) }
                """), tmp.resolve("m"), Target.JVM);
        assertFalse(missing.success());
        assertTrue(missing.diagnostics().getDiagnostics().stream().anyMatch(d -> "SEM025".equals(d.code())),
                "SEM025 for a member that is neither a method nor a field: " + missing.diagnostics().getDiagnostics());
        CompilationResult arity = new CompilerDriver().compile(write("Arity.kf", """
                class H {
                    () -> Int run
                    H() { this.run = () -> 1 }
                }
                main() { println(H().run(7)) }
                """), tmp.resolve("a"), Target.JVM);
        assertFalse(arity.success(), "an extra argument to a () -> Int field must not compile");
    }

    private Path write(String name, String code) throws Exception {
        Path p = tmp.resolve(name);
        Files.writeString(p, code);
        return p;
    }

    private static String run(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        p.waitFor();
        return out;
    }
}
