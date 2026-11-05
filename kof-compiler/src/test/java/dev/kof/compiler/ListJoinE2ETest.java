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
 * §828 — {@code xs.join(sep)} and {@code strings.join(xs, sep)} (stdlib
 * batch, maintainer decision 05/11): the elements formatted as
 * {@code println} formats them, separated by {@code sep}. Before, both were
 * SEM025 and a port wrote a loop with {@code +} or {@code String.join}.
 */
class ListJoinE2ETest {

    private static final String SRC = """
            record Point(Int x, Int y)
            main() {
                println(listOf("a", "b", "c").join(","))
                println(listOf(1, 2, 3).join(" - "))
                println(listOf<String>().join(",") == "")
                println(listOf(1.5, 2.0).join("|") + " " + listOf(5L, 6L).join("+"))
                val ps = listOf(Point(1, 2), Point(3, 4))
                println(ps.join(";") == "" + ps.get(0) + ";" + ps.get(1))
                println(listOf('a', 'b').join("") + " " + listOf(true, false).join("/"))
                println(strings.join(listOf("x", "y"), ", "))
                val parts = listOf("p")
                parts.add("q")
                println(strings.join(parts, ".").length())
            }
            """;

    private static final String GOLDEN = """
            a,b,c
            1 - 2 - 3
            true
            1.5|2.0 5+6
            true
            ab true/false
            x, y
            3
            """;

    @TempDir Path tmp;

    @Test
    void joinFormatsElementsLikePrintlnOnJvmScriptAndJs() throws Exception {
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
    void aNonStringSeparatorIsRefusedAndNativeIsANamedGap() throws Exception {
        CompilationResult bad = new CompilerDriver().compile(write("Bad.kf",
                "main() { println(listOf(1, 2).join(3)) }\n"), tmp.resolve("bad"), Target.JVM);
        assertFalse(bad.success(), "join(Int) must not compile");
        assertTrue(bad.diagnostics().getDiagnostics().stream().anyMatch(d -> "SEM025".equals(d.code())),
                "expected SEM025: " + bad.diagnostics().getDiagnostics());
        CompilationResult nat = new CompilerDriver().compile(write("Nat.kf",
                "main() { println(listOf(\"a\").join(\",\")) }\n"), tmp.resolve("nat"), Target.NATIVE);
        assertFalse(nat.success(), "Native has no join; it must be refused, not linked broken");
        assertTrue(nat.diagnostics().getDiagnostics().stream().anyMatch(d -> "NAT009".equals(d.code())),
                "the named NAT009 gap: " + nat.diagnostics().getDiagnostics());
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
