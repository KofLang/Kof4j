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
 * §824 — {@code List.removeValue(v)} removes the first element equal to
 * {@code v} and returns whether it found one (maintainer decision, 05/11).
 * {@code remove(i)} stays index-based; before, removing by value needed
 * {@code indexOf} plus {@code remove(i)} by hand.
 */
class ListRemoveValueE2ETest {

    private static final String SRC = """
            record Point(Int x, Int y)
            class Log {
                String calls = ""
                List<String> names(String tag) {
                    calls = calls + tag
                    return listOf("a", "b", "a")
                }
            }
            main() {
                var xs = listOf(3, 1, 3, 2)
                println(xs.removeValue(3))
                println(xs)
                println(xs.removeValue(9))
                println(xs.size())
                var ss = listOf("x", "y", "x")
                ss.removeValue("x")
                println(ss)
                val key = "y" + ""
                println(ss.removeValue(key) + " " + ss.size())
                var ls = listOf(5L, 40000000000L)
                println(ls.removeValue(40000000000L) + " " + ls)
                var ds = listOf(0.5, 1.5)
                println(ds.removeValue(1.5) + " " + ds)
                var ps = listOf(Point(1, 2), Point(3, 4))
                println(ps.removeValue(Point(1, 2)) + " " + ps.size())
                val log = Log()
                println(log.names("n").removeValue("a"))
                println(log.calls)
                if (xs.removeValue(1)) { println("gone " + xs) }
                var ys = listOf(3, 3, 1)
                var n = 0
                while (ys.removeValue(3)) { n = n + 1 }
                println(n + " " + (ys.removeValue(1) && ys.isEmpty()))
            }
            """;

    private static final String GOLDEN = """
            true
            [1, 3, 2]
            false
            3
            [y, x]
            true 1
            true [5]
            true [0.5]
            true 1
            true
            n
            gone [3, 2]
            2 true
            """;

    @TempDir Path tmp;

    @Test
    void removeValueRemovesTheFirstMatchOnJvmScriptAndJs() throws Exception {
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
    void removeValueTakesExactlyOneArgument() throws Exception {
        CompilationResult r = new CompilerDriver().compile(write("Bad.kf", """
                main() {
                    var xs = listOf(1, 2)
                    println(xs.removeValue())
                }
                """), tmp.resolve("bad"), Target.JVM);
        assertFalse(r.success(), "removeValue() with no argument must not compile");
        assertTrue(r.diagnostics().getDiagnostics().stream().anyMatch(d -> "SEM025".equals(d.code())),
                "expected SEM025: " + r.diagnostics().getDiagnostics());
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
