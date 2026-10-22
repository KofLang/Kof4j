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

class JsRecordToStringFieldsE2ETest {

    private static final String SRC = """
            record R(Double d, Float f, Long l, Char c)

            record Wrap(R inner, Double? maybe, List<Double> ds)

            main() {
                println(R(1.0, 2.0f, 3L, 'x'))
                println("" + R(1.5, 2.5f, 3L, 'y'))
                var big = R(1.0E7, 0.1f, -1L, 'z')
                println(big.toString())
                println(listOf(R(-0.0, 3.0f, 0L, 'q')))
                println(Wrap(R(2.0, 1.0f, 1L, 'w'), 4.0, listOf(1.5)))
                println(Wrap(R(2.0, 1.0f, 1L, 'w'), null, listOf()))
            }
            """;

    private static final String OUT = "R[d=1.0, f=2.0, l=3, c=x]\nR[d=1.5, f=2.5, l=3, c=y]\nR[d=1.0E7, f=0.1, l=-1, c=z]\n[R[d=-0.0, f=3.0, l=0, c=q]]\nWrap[inner=R[d=2.0, f=1.0, l=1, c=w], maybe=4.0, ds=[1.5]]\nWrap[inner=R[d=2.0, f=1.0, l=1, c=w], maybe=null, ds=[]]";

    @TempDir
    Path tmp;

    @Test
    void recordToStringFormatsDoubleAndCharOnJs() throws Exception {
        assertEquals(OUT, js("rec-js", SRC));
    }

    @Test
    void recordToStringFormatsDoubleAndCharOnJvm() throws Exception {
        assertEquals(OUT, jvm("rec-jvm", SRC));
    }

    private String jvm(String name, String src) throws Exception {
        Path file = write(name, src);
        Path out = tmp.resolve(name + "-out");
        CompilationResult r = new CompilerDriver().compile(file, out, Target.JVM);
        assertTrue(r.success(), String.valueOf(r.diagnostics().getDiagnostics()));
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try (URLClassLoader cl = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
            Class.forName("Default.Main", true, cl).getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(old);
        }
        return buf.toString(StandardCharsets.UTF_8).strip();
    }

    private String js(String name, String src) throws Exception {
        Path file = write(name, src);
        Path out = tmp.resolve(name + "-out");
        CompilationResult r = new CompilerDriver().compile(file, out, Target.JS);
        assertTrue(r.success(), String.valueOf(r.diagnostics().getDiagnostics()));
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), buf,
                new java.io.ByteArrayInputStream(new byte[0]), buf);
        assertEquals(0, exit, buf.toString(StandardCharsets.UTF_8));
        return buf.toString(StandardCharsets.UTF_8).strip();
    }

    private Path write(String name, String src) throws Exception {
        Path file = tmp.resolve(name).resolve("Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        return file;
    }
}
