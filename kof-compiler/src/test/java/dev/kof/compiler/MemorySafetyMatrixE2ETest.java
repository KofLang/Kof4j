package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 6 cross-target safety matrix pins (spec §11, D-MEM-PHASE6-4BACKENDS).
 * Verifies that the safety matrix promises hold identically on the four
 * reachable backends (JVM, Native x86-64, JS, Script) using the shared
 * frontend analysis. Precedent: Phase 4 (#658/#659/#662) "the pin is the product".
 */
class MemorySafetyMatrixE2ETest {
    private static final String WEB_LEAK = """
            main() {
                var app = web.app()
                println("up")
            }
            """;

    private static final String DB_LEAK = """
            main() {
                var conn = db.connect("sqlite::memory:")
                println("up")
            }
            """;



    private final CompilerDriver driver = new CompilerDriver();

    private static final String UNCHECKED_NULL = """
            String? next(Int i) {
                if (i < 5) return "v" + i
                return null
            }
            main() {
                var s = next(0)
                if (s == null) { println("nil") }
                println(s.length)
            }
            """;

    private static final String NARROWED_POSITIVE = """
            String? next(Int i) {
                if (i < 5) return "v" + i
                return null
            }
            main() {
                var s = next(0)
                if (s != null) {
                    println(s.length)
                }
            }
            """;

    private static final String NARROWED_EARLY_RETURN = """
            String? next(Int i) {
                if (i < 5) return "v" + i
                return null
            }
            main() {
                var s = next(0)
                if (s == null) { return }
                println(s.length)
            }
            """;


    @Test
    void mem014WebLeakSurfacePinnedOnScriptWarnings(@TempDir Path tempDir) throws IOException {
        Path kf = tempDir.resolve("mem014-web-script-" + System.nanoTime() + ".kf");
        Files.writeString(kf, WEB_LEAK);
        KofInterpreter.Result ir = driver.interpret(java.util.List.of(kf), tempDir, new String[0]);
        assertEquals(0, ir.exitCode(), "warning nao bloqueia a interpretacao: " + ir.stderr());
        String warns = ir.warnings().toString();
        assertTrue(warns.contains("MEM014"),
                "D-SCRIPT-WARN-SURFACE: Result.warnings() deve carregar MEM014 — veio [" + warns + "]");
    }

    @Test
    void mem014DbLeakSurfacePinnedOnScriptWarnings(@TempDir Path tempDir) throws IOException {
        Path kf = tempDir.resolve("mem014-db-script-" + System.nanoTime() + ".kf");
        Files.writeString(kf, DB_LEAK);
        KofInterpreter.Result ir = driver.interpret(java.util.List.of(kf), tempDir, new String[0]);
        assertEquals(0, ir.exitCode(), "db.connect roda no interpretador; warning nao bloqueia: " + ir.stderr());
        assertEquals("up" + System.lineSeparator(), ir.stdout(), "bytes do script verdes");
        String warns = ir.warnings().toString();
        assertTrue(warns.contains("MEM014"),
                "face db.connect: Result.warnings() deve carregar MEM014 — veio [" + warns + "]");
    }

    @Test
    void nullDerefRefusesSem049OnAllFourBackends(@TempDir Path tempDir) throws IOException {
        for (Target t : new Target[]{Target.JVM, Target.NATIVE, Target.JS}) {
            CompilationResult r = compile(tempDir, "sem049-" + t, UNCHECKED_NULL, t);
            assertFalse(r.success(), t + ": fluxo com null deve falhar");
            String d = r.diagnostics().getDiagnostics().toString();
            assertTrue(d.contains("SEM049"), t + ": esperado SEM049 — " + d);
        }
        matrixScriptDiag(tempDir, "sem049-script", UNCHECKED_NULL, "SEM049");
    }

    @Test
    void positiveNarrowingCompilesOnAllFourBackends(@TempDir Path tempDir) throws IOException {
        for (Target t : new Target[]{Target.JVM, Target.NATIVE, Target.JS}) {
            CompilationResult r = compile(tempDir, "narrow-pos-" + t, NARROWED_POSITIVE, t);
            assertTrue(r.success(), t + ": narrowing positivo deve passar — " + r.diagnostics().getDiagnostics());
        }
        assertScriptCompiles(tempDir, "narrow-pos-script", NARROWED_POSITIVE);
    }

    @Test
    void earlyReturnNarrowingCompilesOnAllFourBackends(@TempDir Path tempDir) throws IOException {
        for (Target t : new Target[]{Target.JVM, Target.NATIVE, Target.JS}) {
            CompilationResult r = compile(tempDir, "narrow-ret-" + t, NARROWED_EARLY_RETURN, t);
            assertTrue(r.success(), t + ": early-return narrowing deve passar — " + r.diagnostics().getDiagnostics());
        }
        assertScriptCompiles(tempDir, "narrow-ret-script", NARROWED_EARLY_RETURN);
    }

    private CompilationResult compile(Path tempDir, String name, String source, Target target) throws IOException {
        Path file = tempDir.resolve(name + "-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + name + "-" + System.nanoTime());
        return driver.compile(file, outDir, target);
    }

    private void matrixScriptDiag(Path tempDir, String name, String src, String code) throws IOException {
        Path kf = tempDir.resolve(name + "-" + System.nanoTime() + ".kf");
        Files.writeString(kf, src);
        String text;
        int exit;
        try {
            KofInterpreter.Result ir = driver.interpret(java.util.List.of(kf), tempDir, new String[0]);
            text = ir.stderr();
            exit = ir.exitCode();
        } catch (KofInterpretException e) {
            text = String.valueOf(e.getMessage());
            exit = 1;
        }
        assertTrue(exit != 0, "SCRIPT deveria falhar com " + code + " — " + text);
        assertTrue(text.contains(code), "SCRIPT: esperado " + code + " — " + text);
    }

    private void assertScriptCompiles(Path tempDir, String name, String src) throws IOException {
        Path kf = tempDir.resolve(name + "-" + System.nanoTime() + ".kf");
        Files.writeString(kf, src);
        try {
            KofInterpreter.Result ir = driver.interpret(java.util.List.of(kf), tempDir, new String[0]);
            assertTrue(ir.exitCode() == 0, "SCRIPT interpret exit: " + ir.exitCode() + " stderr: " + ir.stderr());
        } catch (KofInterpretException e) {
            throw new AssertionError("SCRIPT falhou: " + e.getMessage(), e);
        }
    }
}
