package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #1009 (the issue EXACT DbParams shape + a standalone both-branches shape — the independent verification of the compiler-lane fix d8b508085): `if (v == null) { f() } else { g(v) }` with a nullable primitive
 * parameter emitted invalid bytecode (VerifyError / NoSuchMethodError). TWO
 * measured faces: (1) `StatementAnalyzer` analyzed ONLY the ELSE branch when
 * the condition narrowed the ELSE (`v == null`) — the THEN branch never
 * reached the typer, so an unqualified member call there had no
 * `resolvedMethods` entry and the lowering treated it as a TOP-LEVEL
 * function (`invokestatic Default/Main.addNull`, the value never popped),
 * and any THEN-branch error was silently unchecked; (2) the box gates used
 * `TypeMetrics.isPrimitiveType`, which answers true for `Long?` (already a
 * boxed reference — the §294-2a `isNullablePrimitive` trap) → double boxing
 * (`Long.valueOf:(Ljava/lang/Long;)`, NoSuchMethodError).
 */
class NullableParamDbParamsE2ETest {

    @TempDir
    Path tempDir;

    private String runOnJvm(String source) throws Exception {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, source);
        Path out = tempDir.resolve("out");
        CompilerDriver driver = new CompilerDriver();
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
        // java.home = the real JDK (the PATH java is the javapackages stub)
        String javaBin = System.getProperty("java.home") + "/bin/java";
        ProcessBuilder pb = new ProcessBuilder(javaBin, "-cp", out.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "exit 0, output: " + output);
        return output;
    }

    /** The issue's exact DbParams shape. */
    private static final String DB_PARAMS = """
            class DbParams {
                Int n = 0
                DbParams addNull() { n += 100
                    return this }
                DbParams addValue(Object o) { n += 1
                    return this }
                DbParams bindLongOrNull(v: Long?) {
                    if (v == null) { addNull() } else { addValue(v as Object) }
                    return this
                }
            }
            main() {
                var p = DbParams()
                p.bindLongOrNull(null)
                p.bindLongOrNull(5L)
                println(p.n)
            }
            """;

    /** A THEN-branch member call after an else-narrowing condition, standalone. */
    private static final String THEN_BRANCH_CALL = """
            class Counter {
                Int hits = 0
                Counter hit() { hits += 1
                    return this }
                Counter bind(s: String?) {
                    if (s == null) { hit() } else { hit() }
                    return this
                }
            }
            main() {
                var c = Counter()
                c.bind(null)
                c.bind("x")
                println(c.hits)
            }
            """;

    @Test
    void theIssueDbParamsShapeRunsJvm() throws Exception {
        String out = runOnJvm(DB_PARAMS);
        assertEquals("101", out.strip(),
                "bindLongOrNull(null)=+100 then bindLongOrNull(5L)=+1: " + out);
    }

    @Test
    void thenBranchMemberCallAfterElseNarrowingRunsJvm() throws Exception {
        String out = runOnJvm(THEN_BRANCH_CALL);
        assertEquals("2", out.strip(),
                "BOTH branches always analyzed: the THEN call is the member, not a top-level fn: " + out);
    }
}
