package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #1008 — `instanceof`/`as` on a record of the CURRENT PACKAGE emitted the
 * UNQUALIFIED class name (NoClassDefFoundError: DbNullBind while the class
 * is kernel/db/DbNullBind; the same for the `checkcast` of `v as R`). Root:
 * `ExpressionBinaryInstanceOfLowerer` resolved the target with
 * `CompilerTypes.toType(name, unit)` — it qualifies imports but NOT the
 * classes the module itself declares in its package; the analyzer-aware
 * overload (`toType(name, unit, sa)`, qualifyDeep) used for every other
 * type reference was not used here.
 */
class PackagedRecordInstanceofE2ETest {

    @TempDir
    Path tempDir;

    private String runOnJvm(String source, String entryClass) throws Exception {
        // The compiler requires the package to match the DIRECTORY (the
        // module-root layout): the source lives at <root>/kernel/db/Main.kf.
        Path root = tempDir.resolve("src");
        Path pkg = root.resolve("kernel").resolve("db");
        Files.createDirectories(pkg);
        Path src = pkg.resolve("Main.kf");
        Files.writeString(src, source);
        Path out = tempDir.resolve("out");
        CompilerDriver driver = new CompilerDriver();
        CompilationResult r = driver.compileSources(
                java.util.List.of(src), out, Target.JVM, root);
        assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
        String javaBin = System.getProperty("java.home") + "/bin/java";
        ProcessBuilder pb = new ProcessBuilder(javaBin, "-cp", out.toString(), entryClass);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "exit 0, output: " + output);
        return output;
    }

    /** The issue's exact shape: a packaged record type-tested via instanceof. */
    private static final String INSTANCEOF_FACE = """
            package kernel.db
            record DbNullBind(Int sqlType)
            String kind(Object v) {
                if (v instanceof DbNullBind) { return "null-bind" }
                return "value"
            }
            main() { println(kind(DbNullBind(4))) }
            """;

    /** The as-cast face: a packaged record cast, then a field read through it. */
    private static final String AS_FACE = """
            package kernel.db
            record DbNullBind(Int sqlType)
            Int sqlOf(Object v) {
                var b = v as DbNullBind
                return b.sqlType
            }
            main() { println(sqlOf(DbNullBind(4))) }
            """;

    @Test
    void instanceOfOnAPackagedRecordRunsJvm() throws Exception {
        String out = runOnJvm(INSTANCEOF_FACE, "Default.Main");
        assertEquals("null-bind", out.strip(),
                "the instanceof target must be kernel/db/DbNullBind: " + out);
    }

    @Test
    void asCastOnAPackagedRecordRunsJvm() throws Exception {
        String out = runOnJvm(AS_FACE, "Default.Main");
        assertEquals("4", out.strip(),
                "the as-checkcast target must be kernel/db/DbNullBind: " + out);
    }
}
