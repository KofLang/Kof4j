package dev.kof.compiler;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §597 residual (D-MAINT-BATCH-0510/TY1): the tightening that compared two
 * builtins by the runtime class their JVM erasure loads only worked when BOTH
 * classes were on the COMPILER classpath. The builtins that erase to NESTED
 * classes of {@code KofRuntime} ({@code ProcessResult}/{@code Buffer}/
 * {@code Secret}/{@code KeyHandle}/{@code InteropError}) are GENERATED
 * per-output — the compiler never loads them, so {@code Class.forName}
 * returned null and the relation fell back to "conservative = accept". A
 * declared {@code kof.process.Result} therefore accepted ANY class:
 * {@code Result r = "x"} and passing a {@code String}/{@code Long} to a
 * {@code Result} parameter compiled clean and died at load with
 * {@code NoClassDefFoundError} / {@code VerifyError}. The relation now resolves
 * the generated class's real superclass from the canonical runtime source.
 */
class GeneratedRuntimeAssignabilityE2ETest {

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

    /** A `String` value into a declared `kof.process.Result` is refused. */
    @Test
    void stringIntoDeclaredResultIsRefused() throws Exception {
        CompilationResult r = compile("str", """
            import kof.process

            main() {
                Result r = "x"
                println(r.exitCode)
            }
            """);
        assertFalse(r.success(), "`Result r = \"x\"` must be refused — it compiled clean and died at load: " + diags(r));
        assertTrue(hasCode(r, "SEM021") || hasCode(r, "SEM014"),
                "expected a named type mismatch, got: " + diags(r));
    }

    /** A `String` argument into a `Result` parameter is refused at the call site. */
    @Test
    void stringArgumentIntoResultParameterIsRefused() throws Exception {
        CompilationResult r = compile("arg", """
            import kof.process

            Int codeOf(Result r) {
                return r.exitCode
            }

            main() {
                var s = "x"
                println(codeOf(s))
            }
            """);
        assertFalse(r.success(), "a String argument into a Result parameter must be refused: " + diags(r));
        assertTrue(hasCode(r, "SEM014"), "expected SEM014 at the call site, got: " + diags(r));
    }

    /** The boxed `java.lang.Long` spawn handle into a `Result` is refused. */
    @Test
    void spawnHandleIntoResultParameterIsRefused() throws Exception {
        CompilationResult r = compile("handle", """
            import kof.process

            Int codeOf(Result r) {
                return r.exitCode
            }

            main() {
                var h = process.spawn("echo", "hi")
                println(codeOf(h))
                h.kill()
            }
            """);
        assertFalse(r.success(), "the java.lang.Long spawn handle must NOT assign to a Result: " + diags(r));
        assertTrue(hasCode(r, "SEM014"), "expected SEM014, got: " + diags(r));
    }

    /** Control: a genuine `process.run` Result still flows through the same helper. */
    @Test
    void genuineResultStillCompiles() throws Exception {
        CompilationResult r = compile("ok", """
            import kof.process

            Int codeOf(Result r) {
                return r.exitCode
            }

            main() {
                var r = process.run("echo", "hi")
                println(codeOf(r))
            }
            """);
        assertTrue(r.success(), "a genuine Result must still compile: " + diags(r));
    }

    /** Control: a `Result` still assigns to `Object` (the root of the hierarchy). */
    @Test
    void resultIntoObjectStillCompiles() throws Exception {
        CompilationResult r = compile("obj", """
            import kof.process

            main() {
                var r = process.run("echo", "hi")
                Object o = r
                println(o)
            }
            """);
        assertTrue(r.success(), "Result -> Object must stay assignable: " + diags(r));
    }
}
