package dev.kof.compiler;

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

/**
 * #1128 — the 05/11 JSN006 decision applied to FIELDS: a wrong-shaped
 * payload at ANY depth is the named error, never a silent empty container or
 * a host error, on every target. The issue's matrix: `Box(List<Item>)`
 * given `{"items": {}}` printed `0` on the JVM (the object became an empty
 * list); a `List<String>` field given a scalar died `argument type
 * mismatch`.
 */
class JsonShapeDepthE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void wrongShapeAtDepthIsJsn006OnJvm() throws Exception {
        Path dir = tmp.resolve("shape-jvm");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), """
                record Item(String name)
                record Box(List<Item> items)
                main() {
                    val b = json.decode<Box>("{\\"items\\": {}}")
                    println(b.items.size())
                }
                """);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JVM);
        assertTrue(r.success(), () -> "compile: " + r.diagnostics().getDiagnostics());
        var stdout = new ByteArrayOutputStream();
        var previousOut = System.out;
        String errText = "";
        try {
            System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
            try (var loader = new URLClassLoader(new URL[]{out.toUri().toURL()},
                    getClass().getClassLoader())) {
                try {
                    Class.forName("Default.Main", true, loader)
                            .getMethod("main", String[].class)
                            .invoke(null, (Object) new String[0]);
                    fail("the wrong-shaped payload must NOT decode silently");
                } catch (java.lang.reflect.InvocationTargetException ite) {
                    errText = String.valueOf(ite.getCause());
                }
            }
        } finally {
            System.setOut(previousOut);
        }
        assertTrue(errText.contains("JSN006"), "the named error: " + errText);
        assertTrue(errText.contains("expected an array") && errText.contains("an object"),
                "the expected/got shape in the message: " + errText);
    }

    @Test
    void wrongShapeAtDepthIsJsn006OnScript() throws Exception {
        Path dir = tmp.resolve("shape-script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), """
                record Item(String name)
                record Box(List<Item> items)
                main() {
                    val b = json.decode<Box>("{\\"items\\": {}}")
                    println(b.items.size())
                }
                """);
        KofInterpreter.Result r = driver.interpret(
                List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(1, r.exitCode(), () -> "the run must fail with the named error: "
                + r.stdout() + r.stderr());
        String text = r.stderr() + r.stdout();
        assertTrue(text.contains("JSN006"), "the named error: " + text);
    }

    @Test
    void listFieldGivenScalarIsJsn006OnJvm() throws Exception {
        // The issue's neighbouring face: `List<String>` given `"x"` died
        // `argument type mismatch` — now the named error.
        Path dir = tmp.resolve("shape-scalar");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), """
                record Box(List<String> items)
                main() {
                    val b = json.decode<Box>("{\\"items\\": \\"x\\"}")
                    println(b.items.size())
                }
                """);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JVM);
        assertTrue(r.success(), () -> "compile: " + r.diagnostics().getDiagnostics());
        var previousOut = System.out;
        String errText = "";
        try {
            System.setOut(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
            try (var loader = new URLClassLoader(new URL[]{out.toUri().toURL()},
                    getClass().getClassLoader())) {
                try {
                    Class.forName("Default.Main", true, loader)
                            .getMethod("main", String[].class)
                            .invoke(null, (Object) new String[0]);
                    fail("the scalar-into-list must NOT decode silently");
                } catch (java.lang.reflect.InvocationTargetException ite) {
                    errText = String.valueOf(ite.getCause());
                }
            }
        } finally {
            System.setOut(previousOut);
        }
        assertTrue(errText.contains("JSN006"), "the named error: " + errText);
    }

    @Test
    void correctShapesStillDecodeJvm() throws Exception {
        // The parity guard: the RIGHT shapes keep decoding (never over-gated).
        Path dir = tmp.resolve("shape-ok");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), """
                record Item(String name)
                record Box(List<Item> items)
                main() {
                    val b = json.decode<Box>("{\\"items\\": [{\\"name\\": \\"n1\\"}]}")
                    println(b.items.get(0).name)
                }
                """);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JVM);
        assertTrue(r.success(), () -> "compile: " + r.diagnostics().getDiagnostics());
        var stdout = new ByteArrayOutputStream();
        var previousOut = System.out;
        try {
            System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
            try (var loader = new URLClassLoader(new URL[]{out.toUri().toURL()},
                    getClass().getClassLoader())) {
                Class.forName("Default.Main", true, loader)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            }
        } finally {
            System.setOut(previousOut);
        }
        assertEquals("n1", stdout.toString(StandardCharsets.UTF_8).strip());
    }
}
