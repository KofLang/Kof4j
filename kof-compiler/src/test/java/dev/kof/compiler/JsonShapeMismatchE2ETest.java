package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #1103 (05/11, external report + deploy/quality-gate repro):
 * `json.decode<Map<String, Object>>("[1, 2]")` returned an EMPTY map (JVM and
 * Script, silent `0`) and `json.decode<List<Object>>("{\"a\": 1}")` the same;
 * JS was worse — a map keyed "0","1" and `undefined`. A wrong-shaped payload
 * for the requested container is a SILENT WRONG VALUE (the client's array was
 * handled as "no fields" and the request ran on with defaults).
 *
 * <p>Contract: the Kof error contract (no silent wrong result) + the
 * maintainer's 05/11 decision for the new code JSN006. A decode whose parsed
 * shape does not match the requested container THROWS a named runtime error
 * (`JSN006`, catchable as a Kof String exception) on every target; correct
 * shapes decode exactly as before.
 */
class JsonShapeMismatchE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            main() {
                try {
                    var m = json.decode<Map<String, Object>>("[1, 2]")
                    println("no-throw:" + m.size())
                } catch (String e) {
                    println("caught:" + e)
                }
                try {
                    var l = json.decode<List<Object>>("{\\"a\\": 1}")
                    println("no-throw:" + l.size())
                } catch (String e) {
                    println("caught:" + e)
                }
                println("end")
            }
            """;

    private static final String CONTROL = """
            main() {
                var m = json.decode<Map<String, Object>>("{\\"x\\": 1, \\"y\\": 2}")
                println(m.size())
                var l = json.decode<List<Object>>("[1, 2, 3]")
                println(l.size())
            }
            """;

    private Path write(String name, String src) throws IOException {
        Path dir = tmp.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), src);
        return dir;
    }

    /** JVM face: both mismatches throw the named code; the run ends. */
    @Test
    void wrongShapeIsANamedRuntimeErrorOnJvm() throws Exception {
        Path dir = write("jvm", SRC);
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), dir.resolve("out"), Target.JVM);
        assertTrue(r.success(), () -> "" + r.diagnostics().getDiagnostics());
        String out = runJvm(dir.resolve("out"));
        assertFalse(out.contains("no-throw"), "a wrong-shape decode must not return a container: " + out);
        assertEquals(2, out.split("caught:JSN006", -1).length - 1, "both mismatches throw JSN006: " + out);
        assertTrue(out.strip().endsWith("end"), out);
    }

    /** Script face: the interpreter refuses identically. */
    @Test
    void wrongShapeIsANamedRuntimeErrorOnScript() throws Exception {
        Path dir = write("script", SRC);
        KofInterpreter.Result r = driver.interpret(
                java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        String out = r.stdout().strip();
        assertFalse(out.contains("no-throw"), out);
        assertEquals(2, out.split("caught:JSN006", -1).length - 1, out);
        assertTrue(out.endsWith("end"), out);
    }

    /** JS face: no keyed-"0" mangled map, no undefined — the named error. */
    @Test
    void wrongShapeIsANamedRuntimeErrorOnJs() throws Exception {
        Path dir = write("js", SRC);
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), dir.resolve("out"), Target.JS);
        assertTrue(r.success(), () -> "" + r.diagnostics().getDiagnostics());
        var so = new java.io.ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(dir.resolve("out").resolve("Default.mjs"), so,
                new java.io.ByteArrayInputStream(new byte[0]), so);
        assertEquals(0, exit, () -> "js exit: " + so);
        String out = so.toString(java.nio.charset.StandardCharsets.UTF_8).strip();
        assertFalse(out.contains("no-throw"), out);
        assertTrue(out.contains("JSN006"), out);
        assertTrue(out.endsWith("end"), out);
    }

    /** Control: correct shapes decode exactly as before (no behavior change). */
    @Test
    void correctShapesStillDecodeOnJvm() throws Exception {
        Path dir = write("control-jvm", CONTROL);
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), dir.resolve("out"), Target.JVM);
        assertTrue(r.success(), () -> "" + r.diagnostics().getDiagnostics());
        assertEquals("2\n3", runJvm(dir.resolve("out")).strip());
    }

    @Test
    void correctShapesStillDecodeOnScript() throws Exception {
        Path dir = write("control-script", CONTROL);
        KofInterpreter.Result r = driver.interpret(
                java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals("2\n3", r.stdout().strip());
    }

    private String runJvm(Path outDir) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
        pb.environment().put("JAVA_HOME", System.getenv("JAVA_HOME") == null ? "" : System.getenv("JAVA_HOME"));
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor();
        assertTrue(p.exitValue() == 0, "jvm run failed: " + err);
        return out;
    }
}
