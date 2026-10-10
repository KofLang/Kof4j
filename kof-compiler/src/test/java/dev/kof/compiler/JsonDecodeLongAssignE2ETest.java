package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * §583 (03/10, KofShare C2/C3): `json.decode<T>(...)` returned UNKNOWN to
 * the SEM analyzer while the EMIT side knew T — so
 * `var e = 0; if (req.ttlMs > 0) { e = now + req.ttlMs }` skipped the
 * SEM012 assignability check (`!Type.isUnknown(valueType)` was false),
 * compiled "clean", and the JVM died with VerifyError at first dispatch
 * (Int slot receiving a Long store; measured first in a captured route
 * lambda, reproduced here in plain main — the leak is the decode typing,
 * not the lambda). Fix: SemMethodCallTyper now mirrors the json branch of
 * MethodCallNamespaces (decode<T> = T via the analyzer-aware toType of
 * §582; encode = String). The honest typed form (`var e: Long = 0`)
 * compiles and RUNS on JVM and Script — the product workaround shape is
 * pinned green.
 */
class JsonDecodeLongAssignE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String BODY = """
            import kof.json
            import kof.time

            record Req(String offerId, Long ttlMs)

            main() {
                var s = "{\\"offerId\\":\\"o1\\",\\"ttlMs\\":1000}"
                var req = json.decode<Req>(s)
                var now = time.now()
                %s
                println("DECODECAP-OK")
            }
            """;

    @Test
    void intSlotFromDecodeLongRefusesByName(@TempDir Path root) throws Exception {
        Files.createDirectories(root);
        Path src = root.resolve("Main.kf");
        Files.writeString(src, BODY.formatted("""
                var e = 0
                if (req.ttlMs > 0) {
                    e = now + req.ttlMs
                }
                println("e=" + e)"""));
        CompilationResult r = driver.compileSources(List.of(src), root.resolve("out"),
                Target.JVM, root);
        assertFalse(r.success(), "must refuse the long-into-int-slot assign at COMPILE time");
        String diags = r.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("SEM012"), "named refusal expected: " + diags);
        assertTrue(diags.contains("cannot assign"), diags);
    }

    @Test
    void typedLongSlotFromDecodeRunsOnJvm(@TempDir Path root) throws Exception {
        Files.createDirectories(root);
        Path src = root.resolve("Main.kf");
        Files.writeString(src, BODY.formatted("""
                var e: Long = 0
                if (req.ttlMs > 0) {
                    e = now + req.ttlMs
                }
                println("e>now=" + (e > now))"""));
        CompilationResult r = driver.compileSources(List.of(src), root.resolve("out"),
                Target.JVM, root);
        assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-cp", root.resolve("out").toString(),
                "Default.Main").redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes()).trim();
        assertEquals(0, p.waitFor(), "runtime: " + output);
        assertTrue(output.contains("e>now=true"), output);
        assertTrue(output.contains("DECODECAP-OK"), output);
    }

    @Test
    void typedLongSlotFromDecodeRunsInterpreted(@TempDir Path root) throws Exception {
        Files.createDirectories(root);
        Path src = root.resolve("Main.kf");
        Files.writeString(src, BODY.formatted("""
                var e: Long = 0
                if (req.ttlMs > 0) {
                    e = now + req.ttlMs
                }
                println("e>now=" + (e > now))"""));
        KofInterpreter.Result r = driver.interpret(List.of(src), root, new String[0]);
        assertEquals(0, r.stderr().isEmpty() ? 0 : 1, "stderr: " + r.stderr());
        assertTrue(r.stdout().contains("e>now=true"), "stdout: " + r.stdout());
    }
}
