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
 * A class with a field and a method of the same name typed the bare field
 * read inside the class as the method's return type: {@code viewing.get(k)}
 * on a {@code Map} field {@code viewing} was {@code String has no "get"}
 * (SEM066). A bare name is a value, so the field wins; the call form keeps
 * reaching the method, whatever the declaration order.
 */
class FieldMethodSameNameE2ETest {

    @TempDir Path tmp;

    private String run(String kf) throws Exception {
        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, kf);
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
        return buf.toString();
    }

    @Test
    void theBareNameIsTheFieldAndTheCallIsTheMethod() throws Exception {
        assertEquals("v/m-k/1\nm-z\n1\n", run("""
            class V {
                String viewing(k: String) = "m-" + k
                Map<String, String> viewing = mapOf("k", "v")
                String read(k: String) {
                    val x = viewing.get(k)
                    if (x == null) { return "null" }
                    return x + "/" + viewing(k) + "/" + this.viewing.size()
                }
            }
            main() {
                val v = V()
                println(v.read("k"))
                println(v.viewing("z"))
                println(v.viewing.size())
            }
            """));
    }

    @Test
    void theFieldDeclaredFirstWinsToo() throws Exception {
        assertEquals("v\n", run("""
            class V {
                Map<String, String> viewing = mapOf("k", "v")
                String viewing(k: String) = "m"
                String read(k: String) {
                    val x = viewing.get(k)
                    if (x == null) { return "null" }
                    return x
                }
            }
            main() { println(V().read("k")) }
            """));
    }
}
