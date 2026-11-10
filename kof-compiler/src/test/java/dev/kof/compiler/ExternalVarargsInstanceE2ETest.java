package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §768 — an INSTANCE call to a varargs method of an external class
 * ({@code Flyway.configure().locations("filesystem:db")}) passed the trailing
 * arguments one by one against the real {@code [Ljava/lang/String;} formal and
 * the class died at load with {@code VerifyError: Type 'java/lang/String' is
 * not assignable to '[Ljava/lang/String;'}. §500 packed varargs only on the
 * static path; the typer now carries ACC_VARARGS on the resolved symbol and
 * the instance lowering packs through the same {@code VarargsArrayPacker}.
 *
 * <p>Goldens are the output of the same calls compiled with javac.
 */
class ExternalVarargsInstanceE2ETest {

    private static final String CONF = """
        package ext;
        public class Conf {
            public String last = "";
            public Conf locations(String... l) {
                last = "loc" + l.length + (l.length > 0 ? l[0] : "");
                return this;
            }
            public Conf tagged(int n, String... l) { last = "tag" + n + "/" + l.length; return this; }
            public int sum(int... xs) { int t = 0; for (int x : xs) t += x; return t; }
            public String objs(Object... o) { return java.util.Arrays.toString(o); }
        }
        """;

    @TempDir Path tmp;

    private Path buildJar() throws Exception {
        Path src = tmp.resolve("src/ext");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Conf.java"), CONF);
        Path classes = tmp.resolve("cls");
        Files.createDirectories(classes);
        JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
        assertNotNull(jc, "the JDK JavaCompiler is required (surefire must run on a JDK)");
        assertEquals(0, jc.run(null, null, null, "--release", "21", "-d", classes.toString(),
                src.resolve("Conf.java").toString()));
        Path jar = tmp.resolve("ext-varargs.jar");
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (Path c : Files.walk(classes).filter(f -> f.toString().endsWith(".class")).toList()) {
                z.putNextEntry(new ZipEntry(classes.relativize(c).toString().replace('\\', '/')));
                z.write(Files.readAllBytes(c));
            }
        }
        return jar;
    }

    private String compileAndRun(String kf) throws Exception {
        Path jar = buildJar();
        Path source = tmp.resolve("Main.kf");
        Files.writeString(source, kf);
        Path out = tmp.resolve("out");
        CompilerDriver d = new CompilerDriver();
        d.setExternalClasspath(List.of(jar));
        CompilationResult r = d.compile(source, out, Target.JVM);
        assertTrue(r.success(), "must compile: " + r.diagnostics().getDiagnostics());
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true));
        try (URLClassLoader cl = new URLClassLoader(
                new URL[]{out.toUri().toURL(), jar.toUri().toURL()}, getClass().getClassLoader())) {
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(old);
        }
        return buf.toString();
    }

    @Test
    void trailingArgumentsArePackedIntoTheVarargsArray() throws Exception {
        String out = compileAndRun("""
            import ext.Conf
            main() {
                val c = Conf()
                println(c.locations("a").last)
                println(c.locations("a", "b").last)
                println(c.locations().last)
                println(c.tagged(1, "x", "y").last)
            }
            """);
        assertEquals("loc1a\nloc2a\nloc0\ntag1/2\n", out);
    }

    @Test
    void anExplicitArrayIsPassedThroughUnpacked() throws Exception {
        String out = compileAndRun("""
            import ext.Conf
            main() {
                val c = Conf()
                println(c.locations("p,q".split(",")).last)
            }
            """);
        assertEquals("loc2p\n", out);
    }

    @Test
    void primitiveAndObjectComponentsAreCoerced() throws Exception {
        String out = compileAndRun("""
            import ext.Conf
            main() {
                val c = Conf()
                println(c.sum(1, 2, 3))
                println(c.objs(1, "a", true))
            }
            """);
        assertEquals("6\n[1, a, true]\n", out);
    }

    @Test
    void jdkInstanceVarargsCallRuns() throws Exception {
        String out = compileAndRun("""
            import java.nio.file.FileSystems
            main() {
                val fs = FileSystems.getDefault()
                println(fs.getPath("a", "b", "c").getNameCount())
            }
            """);
        assertEquals("3\n", out);
    }
}
