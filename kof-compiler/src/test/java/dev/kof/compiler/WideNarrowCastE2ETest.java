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
 * #1087 — a WIDE-to-NARROW cast ({@code Long/Float/Double as Byte/Short/Char})
 * emitted the INT-width narrow ops (i2b/i2s/i2c) over the 2-slot value still
 * on the stack — VerifyError "Bad type on operand stack". The fix gives
 * {@code emitPrimNarrow} the wide→Int step BEFORE the narrow op (the same
 * pattern the store path already had).
 *
 * <p>The values follow the JVM cast contract: (byte) 300L = 44,
 * (short) 70000L = 4464, (char) 65L = 'A'.
 */
class WideNarrowCastE2ETest {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String LONG_FACE = """
            main() {
                var b = 300L as Byte
                println(b)
                var s = 70000L as Short
                println(s)
            }
            """;

    private static final String EXPECTED_LONG = "44\n4464";

    private static final String CHAR_FACE = """
            main() {
                var c = 65L as Char
                println(c)
                var d = 66.9 as Char
                println(d)
            }
            """;

    private static final String EXPECTED_CHAR = "A\nB";

    @Test
    void wideToByteAndShortOnJvm() throws Exception {
        Path root = tmp.resolve("wn-long");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), LONG_FACE);
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
        assertEquals(EXPECTED_LONG, stdout.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void wideToCharOnJvm() throws Exception {
        Path root = tmp.resolve("wn-char");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), CHAR_FACE);
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
        assertEquals(EXPECTED_CHAR, stdout.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void wideToNarrowOnScript() throws Exception {
        Path root = tmp.resolve("wn-script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), LONG_FACE);
        KofInterpreter.Result result = driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]);
        assertEquals(0, result.exitCode(), "script: " + result.stdout() + result.stderr());
        assertEquals(EXPECTED_LONG, result.stdout().strip());
    }
    @Test
    void nullablePrimitiveAsCastOnDirectCallJvm() throws Exception {
        // #1097: `score() as Object` with score(): Int? — the method's
        // descriptor is the WRAPPER, so the stack already holds the boxed
        // reference; re-boxing emitted Integer.valueOf(Integer)
        // (NoSuchMethodError). The reference-target face skips the box.
        Path root = tmp.resolve("wn-1097obj");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), """
                Int? score() { return 7 }
                main() {
                    println(score() as Object)
                }
                """);
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
        assertEquals("7", stdout.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void nullablePrimitiveAsIntNumericCastUnboxesJvm() throws Exception {
        // #1097: `(score() as Int) + 1` — the direct-call source is
        // Nullable(Int): the stack holds the BOX; the numeric cast face opens
        // it before the widening (the #1054 rule, now for non-var sources).
        Path root = tmp.resolve("wn-1097num");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), """
                Int? score() { return 7 }
                main() {
                    println((score() as Int) + 1)
                }
                """);
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
        assertEquals("8", stdout.toString(StandardCharsets.UTF_8).strip());
    }
}
