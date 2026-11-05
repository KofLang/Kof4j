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
 * §830 — {@code s.containsAll(other)} with a Set or a List argument (stdlib
 * batch, maintainer decision 05/11): true when every element of
 * {@code other} is in {@code s}, by the same content equality as
 * {@code contains}. Before, it was SEM025 and a port looped over
 * {@code contains}.
 */
class SetContainsAllE2ETest {

    private static final String SRC = """
            record P(Int x)
            main() {
                val s = setOf(1, 2, 3)
                println(s.containsAll(setOf(1, 3)) + " " + s.containsAll(listOf(3, 4)))
                println(s.containsAll(listOf<Int>()) + " " + setOf("a", "b").containsAll(listOf("b")))
                println(setOf(P(1), P(2)).containsAll(listOf(P(2))))
                if (s.containsAll(listOf(2))) { println("yes") }
            }
            """;

    private static final String GOLDEN = """
            true false
            true true
            true
            yes
            """;

    @TempDir Path tmp;

    @Test
    void containsAllOnJvmScriptAndJs() throws Exception {
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
    void theArgumentIsACollectionAndNativeIsANamedGap() throws Exception {
        assertRefused("main() { println(setOf(1).containsAll(1)) }\n", Target.JVM, "SEM025");
        assertRefused("main() { println(setOf(1).containsAll(listOf(1))) }\n", Target.NATIVE, "NAT009");
    }

    private void assertRefused(String code, Target target, String expected) throws Exception {
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
