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

class TypedEmptyMapSetE2ETest {

    private static final String SRC = """
            record P(Int x)

            main() {
                var s = setOf<Long>()
                s.add(5L)
                println(s.contains(5L))
                println(s)
                var m = mapOf<String, Double>()
                m.put("pi", 3.14)
                var v = m.getOrDefault("pi", 0.0)
                println(v * 2.0)
                var c = mapOf<String, Int>()
                c.put("a", c.getOrDefault("a", 0) + 1)
                c.put("a", c.getOrDefault("a", 0) + 1)
                println(c)
                Int? g = c.get("a")
                if (g != null) {
                    println(g + 10)
                }
                var ps = setOf<P>()
                ps.add(P(1))
                ps.add(P(1))
                println(ps.size())
            }
            """;

    private static final String OUT = "true\n[5]\n6.28\n{a=2}\n12\n1\n";

    @TempDir
    Path tmp;

    @Test
    void typedEmptyMapAndSetJvm() throws Exception {
        Path file = write("jvm");
        Path out = tmp.resolve("jvm-out");
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
        assertEquals(OUT, buf.toString(StandardCharsets.UTF_8));
    }

    @Test
    void typedEmptyMapAndSetScript() throws Exception {
        Path file = write("script");
        KofInterpreter.Result r = new CompilerDriver().interpret(List.of(file), file.getParent(), new String[0]);
        assertEquals(0, r.exitCode(), r.stdout() + " " + r.stderr());
        assertEquals(OUT, r.stdout().replace("\r\n", "\n"));
    }

    @Test
    void typedEmptyMapAndSetJs() throws Exception {
        Path file = write("js");
        Path out = tmp.resolve("js-out");
        CompilationResult r = new CompilerDriver().compile(file, out, Target.JS);
        assertTrue(r.success(), String.valueOf(r.diagnostics().getDiagnostics()));
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), buf,
                new java.io.ByteArrayInputStream(new byte[0]), buf);
        assertEquals(0, exit, buf.toString(StandardCharsets.UTF_8));
        assertEquals(OUT.strip(), buf.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void typedEmptyMapAndSetNative() throws Exception {
        Assumptions.assumeTrue(hasToolchain(), "native toolchain absent");
        Path file = write("native");
        Path out = tmp.resolve("native-out");
        CompilationResult r = new CompilerDriver().compile(file, out, Target.NATIVE);
        assertTrue(r.success(), String.valueOf(r.diagnostics().getDiagnostics()));
        Process p = new ProcessBuilder(out.resolve("Default/Main").toString()).redirectErrorStream(true).start();
        String o = NativeRiscv64E2ETest.runBounded(p, "x86");
        assertEquals(0, p.exitValue(), o);
        assertEquals(OUT.strip(), o.strip());
    }

    private static boolean hasToolchain() {
        try {
            return new ProcessBuilder("sh", "-c", "command -v as && command -v ld").start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private Path write(String name) throws Exception {
        Path file = tmp.resolve(name).resolve("Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, SRC);
        return file;
    }
}
