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
 * §811 — {@code json.encode} of a record with a {@code Byte[]} component died
 * on the JVM and Script with {@code InaccessibleObjectException} (the encoder
 * had no Byte/Short case and reflected into {@code java.lang.Byte}), and
 * {@code json.decode<R>} handed the raw JSON list to the array slot
 * ("argument type mismatch" / "Argument is not an array"). JS was already
 * right; it is the oracle the other two targets now match.
 */
class JsonByteArrayRecordE2ETest {

    private static final String SRC = """
            record R(Byte[] data, String t)
            main() {
                val b = new Byte[2]
                b[0] = 7 as Byte
                b[1] = -1 as Byte
                val s = json.encode(R(b, "t"))
                println(s)
                Short sh = 3 as Short
                println(json.encode(listOf(sh)))
                val back = json.decode<R>(s)
                println(back.data().length)
                println(back.data()[1])
            }
            """;

    private static final String GOLDEN = "{\"data\":[7,-1],\"t\":\"t\"}\n[3]\n2\n-1\n";

    @TempDir Path tmp;

    private Path source() throws Exception {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, SRC);
        return src;
    }

    @Test
    void jvmEncodesAndDecodesAByteArrayComponent() throws Exception {
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
        Path src = source();
        KofInterpreter.Result r = new CompilerDriver().interpret(List.of(src), tmp, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals(GOLDEN, r.stdout());
    }

    @Test
    void jsIsTheOracle() throws Exception {
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
