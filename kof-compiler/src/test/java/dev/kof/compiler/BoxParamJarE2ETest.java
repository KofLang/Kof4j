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
 * #1089 — a Kof primitive passed to a Java parameter of its BOXED type is
 * boxed, as javac does (JLS 5.3 method invocation conversion: boxing then
 * widening reference).
 *
 * <p>Three faces against a REAL jar: `Bool`→`Boolean`, `Int`→`Integer`,
 * `Int`→`Number`; plus the issue's second face — `Int?`→`Integer` passes the
 * ALREADY-BOXED reference (D-NULL-INTENT: the emission must never re-box, the
 * measured `NoSuchMethodError Integer.valueOf(Integer)`).
 */
class BoxParamJarE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private Path buildBJar() throws Exception {
        Path src = tmp.resolve("src");
        Files.createDirectories(src.resolve("ext"));
        Files.writeString(src.resolve("ext/B.java"), """
                package ext;
                public class B {
                    public static String flag(Boolean b) { return "flag=" + b; }
                    public static String num(Integer i) { return "num=" + (i + 1); }
                    public static String gen(Number n) { return "gen=" + n; }
                }
                """);
        Path classes = tmp.resolve("classes");
        Files.createDirectories(classes);
        Process p1 = new ProcessBuilder(TestJdk.javacBin(), "-d", classes.toString(),
                src.resolve("ext/B.java").toString()).start();
        assertEquals(0, p1.waitFor(), "javac failed for the B jar source");
        Path jar = tmp.resolve("b.jar");
        Process p2 = new ProcessBuilder(TestJdk.jarBin(), "cf", jar.toString(),
                "-C", classes.toString(), ".").start();
        assertEquals(0, p2.waitFor(), "jar failed");
        return jar;
    }

    @Test
    void primitivesBoxAtTheJavaBoundaryLikeJavacJvm(@TempDir Path ignored) throws Exception {
        Path jar = buildBJar();
        driver.setExternalClasspath(List.of(jar));
        Path main = tmp.resolve("Main.kf");
        Files.writeString(main, """
                import ext.B

                main() {
                    Bool t = true
                    println(B.flag(t))
                    println(B.num(3))
                    println(B.gen(7))
                    Int? maybe = 5
                    println(B.num(maybe))
                }
                """);
        Path out = tmp.resolve("out");
        CompilationResult r = driver.compile(main, out, Target.JVM);
        assertTrue(r.success(), "compile failed: " + r.diagnostics().getDiagnostics());
        try (var loader = new java.net.URLClassLoader(
                new java.net.URL[]{out.toUri().toURL(), jar.toUri().toURL()},
                getClass().getClassLoader())) {
            var captured = new ByteArrayOutputStream();
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
            // num=4 (3+1 inside Java), gen=7, num=6 (5+1) — the Java side adds.
            assertEquals("flag=true\nnum=4\ngen=7\nnum=6", output, "the boxed-param faces");
        }
    }
}
