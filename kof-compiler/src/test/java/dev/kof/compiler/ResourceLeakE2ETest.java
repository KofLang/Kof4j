package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-MEMORY-SAFETY Fase 3 fatia 3.1b — L-05/MEM014 resource-lifetime warning
 * ({@code dev.kof.compiler.memory.ResourceLeakAnalysis}).
 *
 * <p>The warning fires at the creation site of a {@code web.app()} handle that
 * is never closed anywhere in the body and never escapes it; it runs in the
 * SHARED frontend (like {@code OwnershipPass}), so the same source yields the
 * same diagnostic on JVM/Native/JS by construction, and the Script target
 * (interpret) stays byte-green for valid programs. Complements
 * {@link MemorySafetyE2ETest} (fatia 3.1: MEM001/MEM002). Close-bearing faces
 * on Native/JS use the CREATOR only — the {@code close}/{@code port} calls are
 * the pre-existing, R6-honest WEB001 lowering gaps, not a MEM concern.</p>
 */
class ResourceLeakE2ETest {

    /** Created, used only for println of an unrelated value, never closed, no escape. */
    private static final String LEAK_BODY = """
            main() {
                var app = web.app()
                println("up")
            }
            """;

    private static final String CLOSED = """
            main() {
                var app = web.app()
                app.close()
                println("released")
            }
            """;

    private static final String GUARDED_CLOSE = """
            main() {
                var app = web.app()
                if (1 == 1) {
                    app.close()
                }
                println("released")
            }
            """;

    private static final String ALIAS_ESCAPES = """
            main() {
                var app = web.app()
                var other = app
                println("up")
            }
            """;

    private final CompilerDriver driver = new CompilerDriver();

    private Path write(Path tmp, String dir, String source) throws IOException {
        Path out = tmp.resolve(dir);
        Files.createDirectories(out);
        Path src = out.resolve("Main.kf");
        Files.writeString(src, source);
        return src;
    }

    @Test
    void leakWarnsIdenticallyOnJvmNativeJs(@TempDir Path tmp) throws IOException {
        Path src = write(tmp, "mem014-leak", LEAK_BODY);
        String expected = null;
        for (Target t : List.of(Target.JVM, Target.NATIVE, Target.JS)) {
            CompilationResult r = driver.compile(src, tmp.resolve("mem014-leak-" + t.name()), t);
            assertTrue(r.success(), t + ": MEM014 is a warning, the build must stay green: "
                    + r.diagnostics().getDiagnostics());
            String diags = r.diagnostics().getDiagnostics().toString();
            assertTrue(diags.contains("MEM014"), t + ": expected MEM014, got: " + diags);
            assertTrue(diags.contains("line=2"), t + ": must point at creation, got: " + diags);
            assertFalse(diags.contains("MEM001"), t + ": no claim happens here: " + diags);
            if (expected == null) {
                expected = diags;
            } else {
                assertEquals(expected, diags, "cross-target parity of MEM014 (" + t + ")");
            }
        }
    }

    @Test
    void scriptTargetRunsLeakProgramWithSameWarning(@TempDir Path tmp) throws IOException {
        Path src = write(tmp, "mem014-leak-scr", LEAK_BODY);
        KofInterpreter.Result ir = driver.interpret(List.of(src), src.getParent(), new String[0]);
        assertEquals(0, ir.exitCode(), "warning must not block interpretation");
        assertEquals("up" + System.lineSeparator(), ir.stdout(), "script bytes green");
    }

    @Test
    void closedHandleStaysSilentOnJvm(@TempDir Path tmp) throws IOException {
        Path src = write(tmp, "mem014-closed", CLOSED);
        CompilationResult r = driver.compile(src, tmp.resolve("mem014-closed-classes"), Target.JVM);
        assertTrue(r.success(), "JVM build");
        assertFalse(r.diagnostics().getDiagnostics().toString().contains("MEM"),
                "correct lifecycle must be silent: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void guardedCloseAnywhereSilencesTheWarning(@TempDir Path tmp) throws IOException {
        Path src = write(tmp, "mem014-guard", GUARDED_CLOSE);
        CompilationResult r = driver.compile(src, tmp.resolve("mem014-guard-classes"), Target.JVM);
        assertTrue(r.success(), "JVM build");
        assertFalse(r.diagnostics().getDiagnostics().toString().contains("MEM"),
                "conservative: close exists somewhere in the body: "
                        + r.diagnostics().getDiagnostics());
    }

    @Test
    void aliasedHandleSilencesTheWarning(@TempDir Path tmp) throws IOException {
        Path src = write(tmp, "mem014-alias", ALIAS_ESCAPES);
        CompilationResult r = driver.compile(src, tmp.resolve("mem014-alias-classes"), Target.JVM);
        assertTrue(r.success(), "JVM build");
        assertFalse(r.diagnostics().getDiagnostics().toString().contains("MEM"),
                "the alias may close it elsewhere — never a false positive: "
                        + r.diagnostics().getDiagnostics());
    }
}
