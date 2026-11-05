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
 * §821 — the elvis operator {@code a ?: b} (maintainer decision D-ELVIS):
 * the value of {@code a} when it is not null, otherwise {@code b}; {@code a}
 * is evaluated once and {@code b} only when needed. Before, {@code ?:} was
 * PARSE041 and every default of an optional needed statements. Precedence is
 * Kotlin's: above comparisons, below shifts and arithmetic.
 */
class ElvisOperatorE2ETest {

    private static final String SRC = """
            String? find(String k) {
                if (k == "a") { return "found" }
                return null
            }
            Int? count(Int n) {
                if (n > 0) { return n }
                return null
            }
            Long? big(Bool some) {
                if (some) { return 40000000000L }
                return null
            }
            Double? ratio(Bool some) {
                if (some) { return 0.25 }
                return null
            }
            class Log {
                String calls = ""
                String side(String v) {
                    calls = calls + v
                    return v
                }
            }
            class User {
                String? nick
                User(String? n) { this.nick = n }
                String label() { return nick ?: "anonymous" }
            }
            Int orZero(Int? v) { return v ?: 0 }

            main() {
                println(find("a") ?: "none")
                println(find("b") ?: "none")
                val n: Int = count(5) ?: 0
                println(n + (count(0) ?: -1))
                println(count(0) ?: count(0) ?: 7)
                val s: String? = find("b") ?: null
                println(s == null)
                val log = Log()
                println(find("a") ?: log.side("x"))
                println(find("b") ?: log.side("y"))
                println(log.calls)
                println((find("b") ?: "abc").length())
                println(find("b") ?: "x" == "x")
                println(1L + (big(false) ?: 1L) + (big(true) ?: 0L))
                println((ratio(false) ?: 0.5) + (ratio(true) ?: 0.0))
                println(User(null).label() + " " + User("kof").label())
                println(orZero(null) + orZero(3))
                var pick = (k: String) -> find(k) ?: "default"
                println(pick("a") + " " + pick("z"))
                if ((count(0) ?: 2) > 1) { println("cond") }
                println("n=" + (count(0) ?: 9))
            }
            """;

    private static final String GOLDEN = """
            found
            none
            4
            7
            true
            found
            y
            y
            3
            true
            40000000002
            0.75
            anonymous kof
            3
            found default
            cond
            n=9
            """;

    @TempDir Path tmp;

    @Test
    void elvisPicksTheLeftValueOrTheDefaultOnJvmScriptAndJs() throws Exception {
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
    void aDefaultOfTheWrongTypeIsSem014() throws Exception {
        CompilationResult r = new CompilerDriver().compile(write("Bad.kf", """
                Int? count(Int n) {
                    if (n > 0) { return n }
                    return null
                }
                main() { println(count(0) ?: "x") }
                """), tmp.resolve("bad"), Target.JVM);
        assertFalse(r.success(), "an Int? with a String default must not compile");
        assertTrue(r.diagnostics().getDiagnostics().stream().anyMatch(d -> "SEM014".equals(d.code())),
                "expected SEM014: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void theFormatterKeepsElvisAndItsGrouping() {
        String src = "main() {\n    val a: String? = null\n    println((a ?: \"x\") + \"!\")\n    println(a ?: \"y\" == \"y\")\n}\n";
        String once = KofFormatter.format(src, "Main.kf");
        assertTrue(once.contains("(a ?: \"x\") + \"!\""), once);
        assertTrue(once.contains("a ?: \"y\" == \"y\""), once);
        assertEquals(once, KofFormatter.format(once, "Main.kf"), "idempotent");
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
