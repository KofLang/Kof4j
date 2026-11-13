package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §827 — {@code s.codePointAt(i)}, the java.lang.String member the stdlib
 * batch asked for (maintainer decision 05/11) and the one face #1120 left
 * open. Before, it was SEM025 ("Cannot resolve method") and a port wrote a
 * manual surrogate decode.
 */
class StringCodePointAtE2ETest {

    private static final String SRC = """
            main() {
                val s = "aé"
                println(s.codePointAt(0) + " " + s.codePointAt(1))
                val clef = "x𝄞"
                println(clef.codePointAt(1) + " " + clef.codePointAt(2))
            }
            """;

    @TempDir Path tmp;

    @Test
    void codePointAtOnJvmAndScript() throws Exception {
        Path src = write("Main.kf", SRC);
        CompilerDriver driver = new CompilerDriver();
        CompilationResult jvm = driver.compile(src, tmp.resolve("jvm"), Target.JVM);
        assertTrue(jvm.success(), "JVM compile: " + jvm.diagnostics().getDiagnostics());
        String golden = "97 233\n119070 56606\n";
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
    void codePointAtRunsOnNativeX86() throws Exception {
        // Kof.expr fatia 6 (D-KOF-EXPR): o nativo x86-64 ganhou a face
        // (RuntimeExpr.kof_string_code_point_at) — o mesmo escalar na unidade
        // UTF-16 que a JVM e o JS devolvem.
        Path src = write("Nat.kf", """
                main() {
                    val s = "aé"
                    println(s.codePointAt(0) + " " + s.codePointAt(1))
                    val clef = "x𝄞"
                    println(clef.codePointAt(1) + " " + clef.codePointAt(2))
                }
                """);
        CompilationResult nat = new CompilerDriver().compile(src, tmp.resolve("nat"), Target.NATIVE);
        assertTrue(nat.success(), "Native compile: " + nat.diagnostics().getDiagnostics());
        Path bin = tmp.resolve("nat/Default/Main");
        assertTrue(Files.exists(bin), "the native binary must exist");
        assertEquals("97 233\n119070 56606\n",
                run(bin.toString()).replace("\r\n", "\n"), "native");
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
