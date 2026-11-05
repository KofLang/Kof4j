package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §782 — inside a STATIC method, a static field read by its simple name
 * ({@code seen.set(v)} with {@code static ThreadLocal seen}) was typed by a
 * module-wide scan that returned the first class with a member of that name
 * (HashMap order): an unrelated {@code List<String> seen} field of another
 * file made the class fail to compile ({@code List.set takes an Int INDEX},
 * SEM055). The class being lowered is now looked up first, as the emitter
 * already did.
 */
class StaticFieldTypeScopeE2ETest {

    @TempDir Path tmp;

    @Test
    void aStaticFieldIsTypedByItsOwnClassNotBySameNamedFieldsElsewhere() throws Exception {
        Path src = tmp.resolve("src");
        Path lib = src.resolve("kernel/http");
        Path app = src.resolve("app");
        Files.createDirectories(lib);
        Files.createDirectories(app);
        Files.writeString(lib.resolve("ResponseHeaders.kf"), """
            package kernel.http

            import java.lang.ThreadLocal

            class ResponseHeaders {
                static ThreadLocal seen = new ThreadLocal()
                static mark(v: String) {
                    seen.set(v)
                }
                static String last() { return seen.get() as String }
            }
            """);
        StringBuilder main = new StringBuilder("import kernel.http.*\n");
        for (String n : List.of("A", "B", "C", "D", "E", "F", "G", "H", "Spy", "Zed", "Qux")) {
            main.append("class ").append(n).append(" {\n    List<String> seen = listOf<String>()\n}\n");
        }
        main.append("""
            main() {
                ResponseHeaders.mark("a")
                println(ResponseHeaders.last())
                val s = Spy()
                s.seen.add("x")
                println(s.seen.size())
            }
            """);
        Files.writeString(app.resolve("Main.kf"), main.toString());
        Path out = tmp.resolve("out");
        CompilationResult r = new CompilerDriver().compileSources(
                List.of(lib.resolve("ResponseHeaders.kf"), app.resolve("Main.kf")), out, Target.JVM, src);
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
        assertEquals("a\n1\n", buf.toString());
    }
}
