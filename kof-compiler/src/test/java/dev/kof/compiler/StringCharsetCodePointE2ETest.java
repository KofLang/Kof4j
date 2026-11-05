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
 * §827 — {@code s.getBytes(charset)} and {@code s.codePointAt(i)}, the two
 * java.lang.String members the stdlib batch adds (maintainer decision 05/11).
 * Before, both were SEM025 ("Cannot resolve method"), and a port wrote
 * {@code getBytes()} and a manual surrogate decode.
 */
class StringCharsetCodePointE2ETest {

    private static final String SRC = """
            main() {
                val s = "aé"
                println(s.getBytes("UTF-8").length + " " + s.getBytes("ISO-8859-1").length)
                println(s.codePointAt(0) + " " + s.codePointAt(1))
                val clef = "x𝄞"
                println(clef.codePointAt(1) + " " + clef.codePointAt(2))
            }
            """;

    @TempDir Path tmp;

    @Test
    void getBytesWithACharsetAndCodePointAtOnJvmAndScript() throws Exception {
        Path src = write("Main.kf", SRC);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult jvm = driver.compile(src, tmp.resolve("jvm"), Target.JVM);
        assertTrue(jvm.success(), "JVM compile: " + jvm.diagnostics().getDiagnostics());
        String golden = "3 2\n97 233\n119070 56606\n";
        assertEquals(golden, run(System.getProperty("java.home") + "/bin/java", "-cp",
                tmp.resolve("jvm").toString(), "Default.Main"), "JVM");
        KofInterpreter.Result script = driver.interpret(List.of(src), tmp, new String[0]);
        assertEquals(golden, script.stdout(), "Script, stderr: " + script.stderr());
    }

    @Test
    void codePointAtRunsOnJs() throws Exception {
        Path src = write("Js.kf", """
                main() {
                    val clef = "x𝄞"
                    println("aé".codePointAt(1) + " " + clef.codePointAt(1) + " " + clef.codePointAt(2))
                }
                """);
        CompilationResult js = new CompilerDriver().compile(src, tmp.resolve("js"), Target.JS);
        assertTrue(js.success(), "JS compile: " + js.diagnostics().getDiagnostics());
        assertEquals("233 119070 56606\n", run("node", tmp.resolve("js/Default.mjs").toString()), "JS");
    }

    @Test
    void nativeRefusesCodePointAtWithStr003() throws Exception {
        Path src = write("Nat.kf", "main() { println(\"a\".codePointAt(0)) }\n");
        CompilationResult r = new CompilerDriver().compile(src, tmp.resolve("nat"), Target.NATIVE);
        assertFalse(r.success(), "Native has no codePointAt; it must be refused, not linked broken");
        assertTrue(r.diagnostics().getDiagnostics().stream().anyMatch(d -> "STR003".equals(d.code())),
                "the named STR003 gap: " + r.diagnostics().getDiagnostics());
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
