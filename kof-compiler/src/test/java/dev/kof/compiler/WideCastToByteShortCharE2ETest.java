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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §812 — {@code 300L as Byte} (and every Long/Float/Double cast to Byte,
 * Short or Char) emitted {@code i2b}/{@code i2s}/{@code i2c} straight over the
 * wide value: {@code VerifyError: Bad type on operand stack} on the JVM. The
 * cast now goes through Int first (JLS 5.1.3), the same narrowing a store into
 * a Byte/Short slot already used. Goldens are what javac prints for the same
 * casts; Script and JS already agreed.
 */
class WideCastToByteShortCharE2ETest {

    private static final String SRC = """
            main() {
                Long v = 300L
                println(v as Byte)
                Byte b = v as Byte
                println(b + 1)
                Long w = 70000L
                println(w as Short)
                Double d = 65.9
                println(d as Char)
                println(d as Byte)
                Float f = -129.5f
                println(f as Byte)
                Int i = 200
                println(i as Byte)
                Long c = 66L
                println(c as Char)
            }
            """;

    private static final String GOLDEN = "44\n45\n4464\nA\n65\n127\n-56\nB\n";

    @TempDir Path tmp;

    private Path source() throws Exception {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, SRC);
        return src;
    }

    @Test
    void jvmNarrowsWideSourcesThroughInt() throws Exception {
        Path out = tmp.resolve("jvm");
        CompilationResult r = new CompilerDriver().compile(source(), out, Target.JVM);
        assertTrue(r.success(), "must compile: " + r.diagnostics().getDiagnostics());
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true));
        try (URLClassLoader cl = new URLClassLoader(new URL[]{out.toUri().toURL()},
                getClass().getClassLoader())) {
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(old);
        }
        assertEquals(GOLDEN, buf.toString());
    }

    @Test
    void scriptMatchesTheJvm() throws Exception {
        KofInterpreter.Result r = new CompilerDriver().interpret(List.of(source()), tmp, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals(GOLDEN, r.stdout());
    }

    @Test
    void jsMatchesTheJvm() throws Exception {
        Path out = tmp.resolve("js");
        CompilationResult r = new CompilerDriver().compile(source(), out, Target.JS);
        assertTrue(r.success(), () -> "JS compile: " + r.diagnostics().getDiagnostics());
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, () -> "js output: " + stdout);
        assertEquals(GOLDEN, stdout.toString(StandardCharsets.UTF_8));
    }
}
