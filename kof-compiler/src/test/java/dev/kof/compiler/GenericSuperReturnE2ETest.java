package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
 * §814 — a fluent builder that inherits its methods from a GENERIC
 * super-interface (the AWS SDK v2 shape: {@code S3ClientBuilder extends
 * AwsClientBuilder<S3ClientBuilder, S3Client>}) lost its type at the first
 * inherited call: {@code region(..)} returns {@code T}, erased to the bound,
 * so {@code build()} was {@code Object} and the next member was SEM025. The
 * type arguments of the jar's generic {@code Signature} are now carried up
 * the hierarchy; the call keeps the erased descriptor and a CHECKCAST gives
 * the value its substituted type, as javac does.
 */
class GenericSuperReturnE2ETest {

    @TempDir Path tmp;

    private Path buildJar() throws Exception {
        Path src = tmp.resolve("src/ext");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Builder.java"), """
            package ext;
            public interface Builder<T extends Builder<T, C>, C> {
                T region(String r);
                T endpoint(String e);
                C build();
            }
            """);
        Files.writeString(src.resolve("S3Builder.java"), """
            package ext;
            public interface S3Builder extends Builder<S3Builder, S3> {
                S3Builder tag(String t);
            }
            """);
        Files.writeString(src.resolve("S3.java"), """
            package ext;
            public final class S3 {
                public final String desc;
                S3(String d) { desc = d; }
                public String describe() { return desc; }
                public static S3Builder builder() {
                    return new S3Builder() {
                        String r = "", e = "", t = "";
                        public S3Builder region(String x) { r = x; return this; }
                        public S3Builder endpoint(String x) { e = x; return this; }
                        public S3Builder tag(String x) { t = t + x; return this; }
                        public S3 build() { return new S3(r + "|" + e + "|" + t); }
                    };
                }
            }
            """);
        Path cls = tmp.resolve("cls");
        Files.createDirectories(cls);
        var jc = ToolProvider.getSystemJavaCompiler();
        assertNotNull(jc, "the JDK JavaCompiler is required");
        assertEquals(0, jc.run(null, null, null, "--release", "21", "-d", cls.toString(),
                src.resolve("Builder.java").toString(), src.resolve("S3Builder.java").toString(),
                src.resolve("S3.java").toString()));
        Path jar = tmp.resolve("s3.jar");
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (Path c : Files.walk(cls).filter(f -> f.toString().endsWith(".class")).toList()) {
                z.putNextEntry(new ZipEntry(cls.relativize(c).toString().replace('\\', '/')));
                z.write(Files.readAllBytes(c));
            }
        }
        return jar;
    }

    @Test
    void anInheritedGenericReturnKeepsTheBuilderType() throws Exception {
        Path jar = buildJar();
        Path main = tmp.resolve("Main.kf");
        Files.writeString(main, """
            import ext.S3
            main() {
                val b = S3.builder()
                b.region("us-east-1")
                println(b.build().desc)
                println(S3.builder().region("eu").endpoint("http://x").build().desc)
                println(S3.builder().tag("a").region("sa").tag("b").build().describe())
            }
            """);
        CompilerDriver d = new CompilerDriver();
        d.setExternalClasspath(List.of(jar));
        CompilationResult r = d.compile(main, tmp.resolve("out"), Target.JVM);
        assertTrue(r.success(), "must compile: " + r.diagnostics().getDiagnostics());
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true));
        try (URLClassLoader cl = new URLClassLoader(new URL[]{tmp.resolve("out").toUri().toURL(),
                jar.toUri().toURL()}, getClass().getClassLoader())) {
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(old);
        }
        assertEquals("us-east-1||\neu|http://x|\nsa||ab\n", buf.toString());
    }
}
