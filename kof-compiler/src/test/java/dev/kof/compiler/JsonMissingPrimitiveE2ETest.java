package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §565 (JVM, rule 6): {@code json.decode<Record>} whose JSON is missing a key
 * (or carries JSON-null) for a PRIMITIVE record component passed {@code null}
 * straight to the record constructor MethodHandle, dying in the JDK-internal
 * {@code ValueConversions.primitiveConversion} NPE instead of an honest named
 * diagnostic. The maintainer decision: fail with {@code JSN004}. Reference
 * components stay nullable (a missing key decodes to {@code null}, no error).
 */
class JsonMissingPrimitiveE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private Path write(Path root, String rel, String src) throws Exception {
        Path f = root.resolve(rel);
        Files.createDirectories(f.getParent() == null ? root : f.getParent());
        Files.writeString(f, src);
        return f;
    }

    private String runJvm(Path root, Path source, String tag, String expected) throws Exception {
        Path outDir = root.resolve("out-" + tag);
        CompilationResult r = driver.compileSources(java.util.List.of(source), outDir, Target.JVM, root);
        assertTrue(r.success(), tag + " compile failed: " + r.diagnostics().getDiagnostics());
        var oldOut = System.out;
        var buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true));
        try (URLClassLoader cl = new URLClassLoader(
                new java.net.URL[]{outDir.toUri().toURL()}, getClass().getClassLoader())) {
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new AssertionError(tag + " JVM threw: " + e.getCause(), e.getCause());
        } finally {
            System.setOut(oldOut);
        }
        String out = buf.toString().replace("\r\n", "\n").trim();
        assertEquals(expected, out, tag + " JVM output");
        return out;
    }

    @Test
    void missingPrimitiveComponentFailsNamedJsn004Jvm(@TempDir Path root) throws Exception {
        Path source = write(root, "Main.kf", """
                record Envelope(String model, Bool done, Int n)

                void main() {
                    try {
                        var e = json.decode<Envelope>("{\\"model\\":\\"m\\"}")
                        println("no-error " + e.model)
                    } catch (String err) {
                        println(err)
                    }
                }
                """);
        runJvm(root, source, "missing", "JSN004: missing field 'done' for Envelope");
    }

    @Test
    void nullPrimitiveComponentFailsNamedJsn004Jvm(@TempDir Path root) throws Exception {
        Path source = write(root, "Main.kf", """
                record Envelope(String model, Bool done, Int n)

                void main() {
                    try {
                        var e = json.decode<Envelope>("{\\"model\\":\\"m\\",\\"done\\":null,\\"n\\":1}")
                        println("no-error " + e.model)
                    } catch (String err) {
                        println(err)
                    }
                }
                """);
        runJvm(root, source, "null", "JSN004: missing field 'done' for Envelope");
    }

    @Test
    void nullableReferenceComponentStaysNullJvm(@TempDir Path root) throws Exception {
        Path source = write(root, "Main.kf", """
                record Envelope(String? model, Int n)

                void main() {
                    var e = json.decode<Envelope>("{\\"n\\":5}")
                    if (e.model == null) {
                        println("null-ok")
                    } else {
                        println("got " + e.model)
                    }
                    println(e.n)
                }
                """);
        runJvm(root, source, "refnull", "null-ok\n5");
    }

    @Test
    void completePrimitiveComponentsStillDecodeJvm(@TempDir Path root) throws Exception {
        Path source = write(root, "Main.kf", """
                record Envelope(String model, Bool done, Int n)

                void main() {
                    var e = json.decode<Envelope>("{\\"model\\":\\"m\\",\\"done\\":true,\\"n\\":7}")
                    println(e.model)
                    println(e.done)
                    println(e.n)
                }
                """);
        runJvm(root, source, "complete", "m\ntrue\n7");
    }
}
