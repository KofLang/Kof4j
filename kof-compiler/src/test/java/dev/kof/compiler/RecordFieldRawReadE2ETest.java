package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §715 — DECISION `D-RECORD-FIELD-RAW`: `p.x` on a record is a RAW
 * FIELD read; the explicit accessor participates ONLY in `p.x()`
 * (classes.md already documents the duality: "accessor by method" /
 * "direct read also works").
 *
 * <p>The pre-fix divergence (measured 14/10): `p.x + p.y` with an overriding
 * accessor printed 107 on the JVM (the field access routed through
 * `x()`) but 7 on Script/JS/native (the raw field). The fix: the field-access
 * lowerer must NOT route a record component's `p.x` through the same-name
 * accessor — the raw field wins (the accessor override is invisible to
 * `p.x`, visible only to `p.x()`).
 */
class RecordFieldRawReadE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    /** The §715 repro: the accessor overrides the component. */
    private static final String SRC = """
            record Point(Int x, Int y) {
                Int x() { return x + 100 }
            }
            Int f() {
                var p = Point(3, 4)
                return p.x + p.y
            }
            main() { println(f()) }
            """;

    /** The ACCESSOR face: the explicit call still routes to the override. */
    private static final String CALL_SRC = """
            record Point(Int x, Int y) {
                Int x() { return x + 100 }
            }
            main() {
                var p = Point(3, 4)
                println(p.x())
            }
            """;

    private Path write(Path tmp, String tag, String src) throws Exception {
        Path p = tmp.resolve("Main-" + tag + ".kf");
        Files.writeString(p, src, StandardCharsets.UTF_8);
        return p;
    }

    @Test
    void jvmFieldReadIsRAW(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        assertEquals("7", runJvm(write(tmp, "jvm", SRC), tmp),
                "§715: p.x must read the RAW field on the JVM (the pre-fix JVM"
                        + " routed through the accessor → 107)");
    }

    @Test
    void scriptFieldReadIsRAW(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        assertEquals("7", runScript(write(tmp, "script", SRC), tmp),
                "the Script face was already raw — stays so");
    }

    @Test
    void accessorCallStillRoutesToTheOverride(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        assertEquals("103", runJvm(write(tmp, "call", CALL_SRC), tmp),
                "p.x() (explicit) still calls the accessor (the override)");
    }

    private String runJvm(Path src, Path tmp) throws Exception {
        Path out = tmp.resolve("out");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), () -> "jvm compile: " + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder("java", "-Dfile.encoding=UTF-8",
                "-Dstdout.encoding=UTF-8", "-cp", out.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String outTxt = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        assertEquals(0, p.waitFor(), outTxt);
        return outTxt;
    }

    private String runScript(Path src, Path tmp) throws Exception {
        KofInterpreter.Result result = driver.interpret(List.of(src), tmp, new String[0]);
        assertEquals(0, result.exitCode(), () -> "script: " + result.stderr());
        return result.stdout().strip();
    }
}
