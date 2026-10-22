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

class GenericInferViaSupertypeE2ETest {

    private static final String SRC = """
            interface Source<R> {
                get(): R
            }

            class Seven implements Source<Int> {
                public constructor() {}
                get(): Int = 7
            }

            class Name implements Source<String> {
                public constructor() {}
                get(): String = "kof"
            }

            class Lucky extends Seven {
                public constructor() {
                    super()
                }
            }

            class Wrap<T> implements Source<T> {
                T v
                public constructor(T v) {
                    this.v = v
                }
                get(): T = v
            }

            pull<R>(Source<R> s): R = s.get()

            main() {
                println(pull(Seven()) + 1)
                println(pull(Name()).length)
                println(pull(Lucky()) * 2)
                Int a = pull(Seven())
                println(a)
                println(pull(Wrap<Int>(5)) + 1)
            }
            """;

    private static final String OUT = "8\n3\n14\n7\n6";

    @TempDir
    Path tmp;

    @Test
    void genericReturnInferredThroughInterfaceJvm() throws Exception {
        assertEquals(OUT, jvm("gsv-jvm", SRC));
    }

    @Test
    void genericReturnInferredThroughInterfaceScript() throws Exception {
        assertEquals(OUT, script("gsv-script", SRC));
    }

    @Test
    void genericReturnInferredThroughInterfaceJs() throws Exception {
        assertEquals(OUT, js("gsv-js", SRC));
    }

    @Test
    void genericReturnInferredThroughInterfaceNative() throws Exception {
        Assumptions.assumeTrue(hasToolchain(), "native toolchain absent");
        assertEquals(OUT, nativeRun("gsv-native", SRC));
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
