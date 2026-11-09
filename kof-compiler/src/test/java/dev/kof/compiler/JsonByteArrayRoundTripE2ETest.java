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

import static org.junit.jupiter.api.Assertions.*;

/**
 * #1085 — a record with a `Byte[]` component round-trips through
 * json.encode/json.decode<R> as a JSON number array, on JVM and Script (JS
 * is the oracle: {"data":[7,-1],"t":"t"} then -1).
 *
 * <p>The reporter's root: `kof_json_encode` had no Byte/Short faces (a boxed
 * byte fell into the reflective object encoder and reached
 * `java.lang.Byte.value` — InaccessibleObjectException), and `kof_json_bind`
 * / `bindKof` had no ARRAY target (the parsed list reached the array slot as
 * a List — `argument type mismatch` / `Argument is not an array`).
 */
class JsonByteArrayRoundTripE2ETest {

    private static final String SRC = """
            record R(Byte[] data, String t)
            main() {
                val b = new Byte[2]
                b[0] = 7 as Byte
                b[1] = -1 as Byte
                val s = json.encode(R(b, "t"))
                println(s)
                val back = json.decode<R>(s)
                println(back.data()[1])
            }
            """;

    private static final String OUT = "{\"data\":[7,-1],\"t\":\"t\"}\n-1";

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void byteArrayRecordRoundTripsOnJvm() throws Exception {
        Path dir = tmp.resolve("j1085");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
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
                        .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            }
        } finally {
            System.setOut(previousOut);
        }
        assertEquals(OUT, stdout.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void byteArrayRecordRoundTripsOnScript() throws Exception {
        Path dir = tmp.resolve("s1085");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        KofInterpreter.Result r = driver.interpret(
                java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals(OUT, r.stdout().strip());
    }
}
