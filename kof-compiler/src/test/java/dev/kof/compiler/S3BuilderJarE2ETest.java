package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #1093 slice 2 — the issue's EXACT case: a fluent builder that inherits its
 * methods from a GENERIC super-interface on the `--deps` classpath (the AWS
 * SDK v2 shape).
 *
 * <p>`S3Builder implements Builder<S3Builder, S3>` inherits the DEFAULT
 * `region(String)` from the generic interface; the chain
 * `S3.builder().region("eu").build()` must thread the receiver's type
 * arguments through the hierarchy (`B := S3Builder`, `T := S3`) so `region`
 * returns `S3Builder` and `build()` returns `S3`.
 *
 * <p>The slice's machinery: external-classpath classes resolve by REFLECTION
 * when loadable (the loaded class walks interfaces AND carries generic
 * signatures; the byte-parse stays as the fallback for unloadable bytecode),
 * classes load with NO initialization (never run foreign static init in the
 * compiler process), and a concrete receiver inheriting an interface default
 * emits INVOKEVIRTUAL (the INVOKEINTERFACE owner must BE the interface).
 */
class S3BuilderJarE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    /** The AWS shape, compiled to a jar in the temp dir. */
    private Path buildS3Jar() throws Exception {
        Path src = tmp.resolve("src");
        Files.createDirectories(src.resolve("ext"));
        Files.writeString(src.resolve("ext/Builder.java"), """
                package ext;
                public interface Builder<B extends Builder<B, T>, T> {
                    B self();
                    default B region(String r) { return self(); }
                    T build();
                }
                """);
        Files.writeString(src.resolve("ext/S3.java"), """
                package ext;
                public class S3 {
                    public static S3Builder builder() { return new S3Builder(); }
                    public String toString() { return "S3[eu]"; }
                }
                """);
        Files.writeString(src.resolve("ext/S3Builder.java"), """
                package ext;
                public class S3Builder implements Builder<S3Builder, S3> {
                    public S3Builder self() { return this; }
                    public S3 build() { return new S3(); }
                }
                """);
        Path classes = tmp.resolve("classes");
        Files.createDirectories(classes);
        Process p1 = new ProcessBuilder(TestJdk.javacBin(), "-d", classes.toString(),
                src.resolve("ext/Builder.java").toString(),
                src.resolve("ext/S3.java").toString(),
                src.resolve("ext/S3Builder.java").toString()).start();
        assertEquals(0, p1.waitFor(), "javac failed for the S3 jar sources");
        Path jar = tmp.resolve("s3.jar");
        Process p2 = new ProcessBuilder(TestJdk.jarBin(), "cf", jar.toString(),
                "-C", classes.toString(), ".").start();
        assertEquals(0, p2.waitFor(), "jar failed");
        return jar;
    }

    @Test
    void jarInheritedGenericBuilderChainsLikeTheJvmOracle() throws Exception {
        Path jar = buildS3Jar();
        driver.setExternalClasspath(List.of(jar));
        Path main = tmp.resolve("Main.kf");
        Files.writeString(main, """
                import ext.S3

                main() {
                    var b = S3.builder()
                    println(b.region("eu").build())
                    var direct = S3.builder().build()
                    println(direct)
                }
                """);
        Path out = tmp.resolve("out");
        CompilationResult r = driver.compile(main, out, Target.JVM);
        assertTrue(r.success(), "compile failed: " + r.diagnostics().getDiagnostics());
        try (var loader = new java.net.URLClassLoader(
                new java.net.URL[]{out.toUri().toURL(), jar.toUri().toURL()},
                getClass().getClassLoader())) {
            var captured = new java.io.ByteArrayOutputStream();
            var previousOut = System.out;
            try {
                System.setOut(new java.io.PrintStream(captured, true, StandardCharsets.UTF_8));
                Class.forName("Default.Main", true, loader)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            } finally {
                System.setOut(previousOut);
            }
            String output = captured.toString(StandardCharsets.UTF_8).replace("\r\n", "\n").strip();
            assertEquals("S3[eu]\nS3[eu]", output, "the chained builder output");
        }
    }
}
