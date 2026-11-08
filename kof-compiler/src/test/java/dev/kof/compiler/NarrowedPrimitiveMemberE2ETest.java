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
 * #1054 — numeric conversions on a NARROWED nullable-primitive local.
 *
 * <p>{@code var v = m.get("k")} leaves the slot as the D-NULL-INTENT boxed
 * reference ({@code Long?}); after {@code if (v != null)} the semantic type is
 * the primitive, but the SLOT keeps the box. {@code v.toDouble()} and
 * {@code v as Long} emitted the raw numeric conversion over the REFERENCE —
 * the compiler's own frame computation crashed (COMP002) before ever reaching
 * the verifier. The fix opens the box at both cast paths and at the numeric
 * conversion members (the same representation bridge the primitive consumers
 * use since #845).
 */
class NarrowedPrimitiveMemberE2ETest {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    /** The issue's headline: toDouble on the narrowed Long?. */
    private static final String TO_DOUBLE = """
            main() {
                var m = mapOf("k", 5L)
                var v = m.get("k")
                if (v != null) {
                    println(v.toDouble() + 1.5)
                }
            }
            """;

    /** The issue's repro: `m.get(k) as Long` then a numeric conversion. */
    private static final String AS_LONG = """
            main() {
                var m = mapOf("k", 5L)
                var v = m.get("k")
                if (v != null) {
                    var n = v as Long
                    println(n.toDouble() + 1.5)
                }
            }
            """;

    /** Int? through the same path (the box is an Integer on the JVM). */
    private static final String INT_FACE = """
            main() {
                var m = mapOf("k", 7)
                var v = m.get("k")
                if (v != null) {
                    println(v.toDouble() * 2.0)
                }
            }
            """;

    @Test
    void toDoubleOnNarrowedLong() throws Exception {
        Path root = tmp.resolve("np-double");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), TO_DOUBLE);
        Path out = root.resolve("out");
        CompilationResult r = driver.compile(root.resolve("Main.kf"), out, Target.JVM);
        assertTrue(r.success(), () -> "compile: " + r.diagnostics().getDiagnostics());
        var stdout = new ByteArrayOutputStream();
        var previousOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(stdout, true, StandardCharsets.UTF_8));
            try (var loader = new java.net.URLClassLoader(
                    new java.net.URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
                Class.forName("Default.Main", true, loader)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            }
        } finally {
            System.setOut(previousOut);
        }
        assertEquals("6.5", stdout.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void asLongThenConvert() throws Exception {
        Path root = tmp.resolve("np-as");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), AS_LONG);
        KofInterpreter.Result result = driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]);
        assertEquals(0, result.exitCode(), "script: " + result.stdout() + result.stderr());
        assertEquals("6.5", result.stdout().strip());
    }

    @Test
    void toDoubleOnNarrowedInt() throws Exception {
        Path root = tmp.resolve("np-int");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), INT_FACE);
        Path out = root.resolve("out");
        CompilationResult r = driver.compile(root.resolve("Main.kf"), out, Target.JVM);
        assertTrue(r.success(), () -> "compile: " + r.diagnostics().getDiagnostics());
        var stdout = new ByteArrayOutputStream();
        var previousOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(stdout, true, StandardCharsets.UTF_8));
            try (var loader = new java.net.URLClassLoader(
                    new java.net.URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
                Class.forName("Default.Main", true, loader)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            }
        } finally {
            System.setOut(previousOut);
        }
        assertEquals("14.0", stdout.toString(StandardCharsets.UTF_8).strip());
    }
}
