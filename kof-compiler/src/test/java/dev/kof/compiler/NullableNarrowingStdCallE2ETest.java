package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #770 (COMP002): a narrowed nullable passed to a std-namespace call
 * (`math.parseInt(q)` with `q: String?` inside `if (q != null)`) crashed the
 * JVM backend. The frontend narrows `q` in the branch scope, but the lowerer
 * reads the IR-locals (still `Nullable`), so `KofStd.staticMethod` returned
 * null and the call was silently dropped — the IR reached the store/merge with
 * an empty stack and ASM COMPUTE_FRAMES blew up (`Index -1`).
 *
 * Both reproducers from the issue are pinned here on JVM; the non-narrowed form
 * stays a `SEM025` (see the guard test).
 */
class NullableNarrowingStdCallE2ETest extends JvmSupport {

    /** R1 — nullable parameter read through branches. */
    @Test
    void nullableParameterReadThroughBranches(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            record G(String a, String b)
            String encodeIt(String? q) {
                if (q == null) {
                    return json.encode(G("parent", "x"))
                }
                var ci = math.parseInt(q)
                if (ci < 0) {
                    return "neg"
                }
                return json.encode(G(q, "" + ci))
            }
            void main() {
                println(encodeIt("5"))
                println(encodeIt(null))
            }
            """);
        runJvm(source, tempDir.resolve("out"), "{\"a\":\"5\",\"b\":\"5\"}\n{\"a\":\"parent\",\"b\":\"x\"}");
    }

    /** R2 — nullable read through a compound `&&` guard, result in a local. */
    @Test
    void nullableReadThroughCompoundGuardIntoLocal(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            Int takeIt(String? q) {
                if (q != null && q.length() > 0) {
                    var n = math.parseInt(q)
                    return n
                }
                return 0
            }
            void main() {
                println("r=" + takeIt("42"))
                println("z=" + takeIt(""))
                println("n=" + takeIt(null))
            }
            """);
        runJvm(source, tempDir.resolve("out"), "r=42\nz=0\nn=0");
    }

    /** R2 without the local: the narrowed value is the return expression. */
    @Test
    void nullableReadThroughCompoundGuardAsReturn(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            Int takeIt(String? q) {
                if (q != null && q.length() > 0) {
                    return math.parseInt(q)
                }
                return 0
            }
            void main() {
                println(takeIt("42"))
            }
            """);
        runJvm(source, tempDir.resolve("out"), "42");
    }

    /** Without narrowing the std call is still refused (SEM025), not dropped. */
    @Test
    void nonNarrowedNullableStdCallIsRejected(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            Int takeIt(String? q) { return math.parseInt(q) }
            void main() { println(takeIt("42")) }
            """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.JVM);
        assertFalse(result.success(), "A non-narrowed nullable std call must not compile");
        assertTrue(result.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM025".equals(d.code())),
                "Expected SEM025, got: " + result.diagnostics().getDiagnostics());
    }
}
