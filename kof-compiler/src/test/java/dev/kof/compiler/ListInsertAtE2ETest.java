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
 * §829 — {@code xs.add(index, value)} inserts at a position and shifts the
 * rest right (stdlib batch, maintainer decision 05/11). Before, the
 * two-argument form had no lowering and a port inserted through {@code set}
 * and a manual shift.
 */
class ListInsertAtE2ETest {

    private static final String SRC = """
            record P(Int x)
            main() {
                var xs = listOf(1, 3)
                xs.add(1, 2)
                xs.add(0, 0)
                xs.add(xs.size(), 4)
                println(xs)
                var ss = listOf("b")
                ss.add(0, "a")
                println(ss.join(","))
                var ls = listOf(2L)
                ls.add(0, 1)
                println(ls.get(0) + ls.get(1))
                var ps = listOf(P(2))
                ps.add(0, P(1))
                println(ps.get(0).x + " " + ps.size())
            }
            """;

    private static final String GOLDEN = """
            [0, 1, 2, 3, 4]
            a,b
            3
            1 2
            """;

    @TempDir Path tmp;

    @Test
    void addAtIndexInsertsOnJvmScriptAndJs() throws Exception {
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
    void theIndexIsAnIntTheValueMatchesAndNativeIsANamedGap() throws Exception {
        assertCode("main() { var xs = listOf(1)\n xs.add(\"a\", 2) }\n", Target.JVM, "SEM055");
        assertCode("main() { var xs = listOf(1)\n xs.add(0, \"x\") }\n", Target.JVM, "SEM056");
        assertCode("main() { var xs = listOf(1)\n xs.add(0, 2) }\n", Target.NATIVE, "NAT009");
    }

    private void assertCode(String code, Target target, String expected) throws Exception {
        CompilationResult r = new CompilerDriver().compile(write("C" + expected + ".kf", code),
                tmp.resolve("c" + expected), target);
        assertFalse(r.success(), expected + " case must not compile");
        assertTrue(r.diagnostics().getDiagnostics().stream().anyMatch(d -> expected.equals(d.code())),
                "expected " + expected + ": " + r.diagnostics().getDiagnostics());
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
