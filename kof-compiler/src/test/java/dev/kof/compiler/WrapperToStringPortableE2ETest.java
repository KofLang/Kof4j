package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
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

import static org.junit.jupiter.api.Assertions.*;

class WrapperToStringPortableE2ETest {

    private static final String SRC = """
            main() {
                Int n = 255
                Long big = 9000000000L
                println(Integer.toString(n))
                println(Int.toString(-7))
                println(Long.toString(big))
                println(Double.toString(2.0))
                println(Double.toString(1.0E7))
                println(Float.toString(0.5f))
                println(Boolean.toString(true))
                println(Integer.toString(n) + "!")
                println(Integer.toString(n).length)
            }
            """;

    private static final String OUT = "255\n-7\n9000000000\n2.0\n1.0E7\n0.5\ntrue\n255!\n3";

    @TempDir
    Path tmp;

    @Test
    void wrapperToStringRunsJvm() throws Exception {
        assertEquals(OUT, jvm("wrap-jvm", SRC));
    }

    @Test
    void wrapperToStringRunsScript() throws Exception {
        assertEquals(OUT, script("wrap-script", SRC));
    }

    @Test
    void wrapperToStringRunsJs() throws Exception {
        assertEquals(OUT, js("wrap-js", SRC));
    }

    @Test
    void wrapperToStringRunsNative() throws Exception {
        Assumptions.assumeTrue(hasToolchain(), "native toolchain absent");
        assertEquals(OUT, nativeRun("wrap-native", SRC));
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

    private String script(String name, String src) throws Exception {
        Path file = write(name, src);
        KofInterpreter.Result r = new CompilerDriver().interpret(List.of(file), file.getParent(), new String[0]);
        assertEquals(0, r.exitCode(), r.stdout() + " " + r.stderr());
        return r.stdout().replace("\r\n", "\n").strip();
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

    private String nativeRun(String name, String src) throws Exception {
        Path file = write(name, src);
        Path out = tmp.resolve(name + "-out");
        CompilationResult r = new CompilerDriver().compile(file, out, Target.NATIVE);
        assertTrue(r.success(), String.valueOf(r.diagnostics().getDiagnostics()));
        Process p = new ProcessBuilder(out.resolve("Default/Main").toString()).redirectErrorStream(true).start();
        String o = NativeRiscv64E2ETest.runBounded(p, "x86");
        assertEquals(0, p.exitValue(), o);
        return o.strip();
    }

    private static boolean hasToolchain() {
        try {
            return new ProcessBuilder("sh", "-c", "command -v as && command -v ld").start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private Path write(String name, String src) throws Exception {
        Path file = tmp.resolve(name).resolve("Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        return file;
    }
}
