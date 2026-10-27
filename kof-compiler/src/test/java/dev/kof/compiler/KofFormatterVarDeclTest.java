package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class KofFormatterVarDeclTest {

    private static final String DECLS = """
            main() {
                var a = 1
                val b = 2
                var c: Int = 3
                val d: Int = 4
                Int e = 5
                var f: (Int) -> Int = (x: Int) -> x * 2
                val g: (Int) -> Int = (x: Int) -> x + 1
                println(a + b + c + d + e + f(1) + g(2))
            }
            """;

    @TempDir
    Path tmp;

    @Test
    void typedValAndFunctionTypedLocalsKeepADeclarationThatParses() throws Exception {
        String out = KofFormatter.format(DECLS, "Main.kf");
        assertNotNull(out);
        assertTrue(out.contains("    var a = 1\n    val b = 2\n"), out);
        assertTrue(out.contains("    val d: Int = 4\n"), out);
        assertTrue(out.contains("    Int e = 5\n"), out);
        assertTrue(out.contains("    var f: (Int) -> Int = (x: Int) -> x * 2\n"), out);
        assertTrue(out.contains("    val g: (Int) -> Int = (x: Int) -> x + 1\n"), out);
        assertEquals(out, KofFormatter.format(out, "Main.kf"));
        assertEquals(run("original", DECLS), run("formatted", out));
    }

    @Test
    void typedValStaysImmutableAfterFormatting() throws Exception {
        String src = """
                main() {
                    val d: Int = 4
                    d = 5
                    println(d)
                }
                """;
        String out = KofFormatter.format(src, "Main.kf");
        assertNotNull(out);
        CompilationResult r = compile("immutable", out);
        assertFalse(r.success(), "a val must stay a val after kof fmt:\n" + out);
        assertTrue(r.diagnostics().getDiagnostics().stream().anyMatch(d -> "SEM037".equals(d.code())),
                r.diagnostics().getDiagnostics().toString());
    }

    private CompilationResult compile(String name, String src) throws Exception {
        Path file = tmp.resolve(name).resolve("Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        return new CompilerDriver().compile(file, tmp.resolve(name + "-out"), Target.JVM);
    }

    private String run(String name, String src) throws Exception {
        CompilationResult r = compile(name, src);
        assertTrue(r.success(), name + ": " + r.diagnostics().getDiagnostics() + "\n" + src);
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try (URLClassLoader cl = new URLClassLoader(new URL[]{tmp.resolve(name + "-out").toUri().toURL()},
                getClass().getClassLoader())) {
            Class.forName("Default.Main", true, cl).getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(old);
        }
        return buf.toString(StandardCharsets.UTF_8);
    }
}
