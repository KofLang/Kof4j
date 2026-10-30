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
 * QA probe #29 (30/10): an unknown member access on a PRIMITIVE receiver
 * ({@code bogus.day} with {@code bogus: Int}, the shape probed through
 * {@code time.parseDateIso(...).day} — the parse returns an epoch Int per
 * DECISIONS.md) compiled CLEAN and every target failed its own way:
 * JVM {@code VerifyError} (getfield on an integer — hidden behind the false
 * "JavaFX runtime missing" launcher message), Script printed the debug string
 * {@code PrimitiveType[name=int, sort=10].day} (silent wrong value), JS
 * undefined/ESM link error, Native printed nothing and a SIGSEGV face.
 *
 * <p>Contract: the §491/§692 unknown-field family (SEM050/SEM079/SEM102) —
 * a member access that does not exist is a COMPILE diagnostic, never a
 * per-target garbage value. Primitives expose NO members in Kof (operators,
 * casts and stdlib functions are the idiom).
 */
class PrimitiveMemberDiagnosticE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            main() {
                var bogus = 5
                println(bogus.day)
            }
            """;

    private CompilationResult compile(String source, Path dir, Target target) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), source);
        return driver.compile(dir.resolve("Main.kf"), dir.resolve("out"), target);
    }

    private void assertSem102(CompilationResult r, String member) {
        assertFalse(r.success(), "unknown member on a primitive must fail to compile");
        String diags = "" + r.diagnostics().getDiagnostics();
        assertTrue(diags.contains("SEM102"), "expected SEM102, got: " + diags);
        assertTrue(diags.contains(member), "diagnostic must name the member: " + diags);
    }

    @Test
    void unknownMemberOnIntIsDiagnosedOnJvm(@TempDir Path tempDir) throws IOException {
        assertSem102(compile(SRC, tempDir.resolve("jvm"), Target.JVM), "day");
    }

    @Test
    void unknownMemberOnIntIsDiagnosedOnJs(@TempDir Path tempDir) throws IOException {
        assertSem102(compile(SRC, tempDir.resolve("js"), Target.JS), "day");
    }

    @Test
    void unknownMemberOnCharIsDiagnosed(@TempDir Path tempDir) throws IOException {
        assertSem102(compile("""
            main() {
                var c = 'a'
                println(c.day)
            }
            """, tempDir.resolve("chr"), Target.JVM), "day");
    }

    @Test
    void unknownMemberOnBoolIsDiagnosed(@TempDir Path tempDir) throws IOException {
        assertSem102(compile("""
            main() {
                var b = true
                println(b.day)
            }
            """, tempDir.resolve("bl"), Target.JVM), "day");
    }

    // ---- counter-proofs: legitimate primitive usage keeps compiling ----

    @Test
    void operatorsCastsAndTimeNamespaceStillCompile(@TempDir Path tempDir) throws IOException {
        String program = """
            main() {
                var n = 5
                println(n + 1)
                println(n as Double)
                var d = time.parseDateIso("2024-02-29")
                println(d)
                println(time.isLeapYear(2024))
                println('a' + "!")
            }
            """;
        for (Target t : new Target[]{Target.JVM, Target.JS}) {
            assertTrue(compile(program, tempDir.resolve(t.name().toLowerCase()), t).success(),
                    "valid usage must compile on " + t);
        }
        Path dir = tempDir.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), program);
        KofInterpreter.Result r = driver.interpret(
                java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals("6\n5.0\n19782\ntrue\na!", r.stdout().strip());
    }
}
