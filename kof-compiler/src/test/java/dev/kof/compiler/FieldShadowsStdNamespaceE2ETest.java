package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A class field named like a stdlib namespace ({@code cache}, {@code buffer},
 * {@code config}) was lowered as the namespace when read through the implicit
 * {@code this}: {@code cache.get(k)} on a {@code TtlCache cache} field called
 * the stdlib cache (a wrong value, or a VerifyError with a lambda argument),
 * {@code buffer.get(k)} had "no lowering" and {@code !config.ok(x)} built a
 * broken condition. The analyzer typed the field; the lowering only checked
 * the locals. The namespace keeps working where no value shadows it.
 */
class FieldShadowsStdNamespaceE2ETest {

    @TempDir Path tmp;

    @Test
    void aFieldShadowsTheNamespaceOfTheSameName() throws Exception {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, """
            class TtlCache {
                Map<String, String> m = mapOf("k", "v")
                String? get(k: String) = m.get(k)
                String getOrLoad(k: String, load: () -> String) {
                    val v = m.get(k)
                    if (v != null) { return v }
                    return load()
                }
            }
            class Flags {
                Bool ok(x: Int) = x > 0
            }
            class C {
                TtlCache cache = TtlCache()
                TtlCache buffer = TtlCache()
                Flags config = Flags()
                String f(k: String) {
                    var v = cache.get(k)
                    if (v == null) { return "none" }
                    var w = buffer.get(k)
                    if (w == null) { return "none" }
                    if (!config.ok(1)) { return "off" }
                    return v + w + cache.getOrLoad("z", () -> "loaded")
                }
            }
            main() {
                println(C().f("k"))
                cache.set("a", "std")
                println(cache.get("a"))
            }
            """);
        Path out = tmp.resolve("out");
        CompilationResult r = new CompilerDriver().compile(src, out, Target.JVM);
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
        assertEquals("vvloaded\nstd\n", buf.toString());
    }
}
