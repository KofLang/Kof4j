package dev.kof.compiler;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-MAINT-BATCH-0510/TY1 — the #753 residual: {@code TypeChecker.isAssignable}'s
 * conservative {@code ClassType→ClassType} fallback accepted ANY two builtin
 * classes, so the reporter's exact program
 * ({@code Handle start() { return process.spawn(...) }}) compiled clean
 * ({@code kof check} → no errors) and the class died at LOAD with
 * {@code VerifyError: Bad return type}: {@code process.spawn} produces a boxed
 * {@code java.lang.Long} handle, while a declared {@code Handle} erases to
 * {@code java.util.concurrent.CompletableFuture}. The tightening compares two
 * builtins by the RUNTIME class their JVM erasure
 * ({@code JvmTypeMapper.classDescriptor}) loads, and refuses only when both
 * load and are unrelated; every synthetic/non-loadable type stays conservative.
 * The legit {@code spawn { … }} control (a real {@code Handle}) keeps compiling.
 */
class HandleAssignabilityE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private String diags(CompilationResult r) {
        StringBuilder sb = new StringBuilder();
        r.diagnostics().getDiagnostics().forEach(d -> sb.append(d.message()).append("\n"));
        return sb.toString();
    }

    private boolean hasCode(CompilationResult r, String code) {
        return r.diagnostics().getDiagnostics().stream().anyMatch(d -> code.equals(d.code()));
    }

    private CompilationResult compile(String name, String source) throws Exception {
        Path src = tmp.resolve(name + ".kf");
        Files.writeString(src, source);
        return driver.compile(src, tmp.resolve("out-" + name), Target.JVM);
    }

    /** The reporter's exact shape: a declared `Handle` return fed by `process.spawn`. */
    @Test
    void processSpawnHandleIntoDeclaredHandleReturnIsRefused() throws Exception {
        CompilationResult r = compile("ret", """
            import kof.process

            Handle start() {
                return process.spawn("whoami")
            }

            main() {
                var h = start()
                println("started")
            }
            """);
        assertFalse(r.success(), "process.spawn (java.lang.Long) must NOT assign to a declared Handle"
                + " — it compiled clean and died at load with VerifyError: Bad return type: " + diags(r));
        assertTrue(hasCode(r, "SEM010"),
                "expected the named return-type mismatch (SEM010), got: " + diags(r));
        assertTrue(diags(r).contains("Handle") && diags(r).contains("Long"),
                "the diagnostic must name both the declared and the actual type: " + diags(r));
    }

    /** The same mismatch on a declared LOCAL variable (a distinct assignment path). */
    @Test
    void processSpawnHandleIntoDeclaredHandleLocalIsRefused() throws Exception {
        CompilationResult r = compile("local", """
            import kof.process

            main() {
                Handle h = process.spawn("whoami")
                println("started")
            }
            """);
        assertFalse(r.success(), "a declared `Handle h = process.spawn(...)` must be refused: " + diags(r));
    }

    /** A genuinely unrelated builtin pair is now refused too (the tightening is general). */
    @Test
    void unrelatedBuiltinAssignmentIsRefused() throws Exception {
        CompilationResult r = compile("unrelated", """
            main() {
                String s = listOf(1, 2)
                println(s)
            }
            """);
        assertFalse(r.success(), "`String s = <List>` must be refused: " + diags(r));
    }

    /** Control: the real concurrency handle (`spawn { … }`) still assigns to `Handle`. */
    @Test
    void spawnExpressionIntoDeclaredHandleStillCompiles() throws Exception {
        CompilationResult r = compile("spawn", """
            Handle start() {
                return spawn { 7 }
            }

            main() {
                println("ok " + await start())
            }
            """);
        assertTrue(r.success(), "`spawn { 7 }` produces a real Handle and must still compile: " + diags(r));
    }

    /** Control: a `Handle` → `Handle` pass-through (same erasure) stays legal. */
    @Test
    void handleToHandlePassThroughStillCompiles() throws Exception {
        CompilationResult r = compile("pass", """
            Handle start() {
                return spawn { 7 }
            }

            Handle relay(Handle h) {
                return h
            }

            main() {
                println("ok " + await relay(start()))
            }
            """);
        assertTrue(r.success(), "Handle → Handle must stay assignable: " + diags(r));
    }

    /** Control: any reference still assigns to `Object` (the root of the hierarchy). */
    @Test
    void referenceIntoObjectStillCompiles() throws Exception {
        CompilationResult r = compile("object", """
            main() {
                Object o = listOf(1, 2)
                println(o)
            }
            """);
        assertTrue(r.success(), "List → Object must stay assignable: " + diags(r));
    }
}
