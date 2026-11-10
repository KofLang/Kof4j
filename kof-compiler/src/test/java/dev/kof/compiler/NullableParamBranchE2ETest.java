package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §777 — two faces of {@code if (v == null) { f() } else { g(v as Object) }}
 * with a nullable primitive parameter, both invalid bytecode on the JVM:
 * (1) the analyzer skipped the THEN branch whenever the ELSE got a narrowing,
 * so the unqualified member call {@code f()} was lowered as a top-level
 * function ({@code invokestatic Main.f()Ljava/lang/Object;}, value never
 * popped: "Instruction type does not match stack map"); (2) a {@code Long?} /
 * {@code Double?} passed to an {@code Object} parameter (or cast {@code as
 * Object}) was boxed again ({@code Long.valueOf(Ljava/lang/Long;)},
 * NoSuchMethodError) although it is already a reference.
 */
class NullableParamBranchE2ETest {

    private static final String SRC = """
        class DbParams {
            Int n = 0
            DbParams addNull() {
                n += 100
                return this
            }
            DbParams addValue(Object o) {
                n += 1
                return this
            }
            DbParams bindLongOrNull(v: Long?) {
                if (v == null) { addNull() } else { addValue(v as Object) }
                return this
            }
            DbParams bindDoubleOrNull(v: Double?) {
                if (v == null) { addNull() } else { addValue(v) }
                return this
            }
        }
        main() {
            val p = DbParams()
            p.bindLongOrNull(5L)
            p.bindLongOrNull(null)
            p.bindDoubleOrNull(1.5)
            p.bindDoubleOrNull(null)
            println(p.n)
        }
        """;

    @TempDir Path tmp;

    @Test
    void jvmRunsBothBranches() throws Exception {
        Path source = tmp.resolve("Main.kf");
        Files.writeString(source, SRC);
        Path out = tmp.resolve("out");
        CompilationResult r = new CompilerDriver().compile(source, out, Target.JVM);
        assertTrue(r.success(), "must compile: " + r.diagnostics().getDiagnostics());
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true));
        try (URLClassLoader cl = new URLClassLoader(new URL[]{out.toUri().toURL()},
                getClass().getClassLoader())) {
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(old);
        }
        assertEquals("202\n", buf.toString());
    }

    @Test
    void scriptMatchesTheJvm() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        KofInterpreter.Result r = new CompilerDriver().interpret(
                List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals("202", r.stdout().strip());
    }

    @Test
    void theThenBranchOfANullCheckIsTypeChecked() throws Exception {
        Path source = tmp.resolve("Bad.kf");
        Files.writeString(source, """
            Int f(v: Long?) {
                if (v == null) { return undefinedThing() } else { return 1 }
            }
            main() { println(f(null)) }
            """);
        CompilationResult r = new CompilerDriver().compile(source, tmp.resolve("bad"), Target.JVM);
        assertTrue(!r.success() && r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM015".equals(d.code())),
                "the THEN branch must be analyzed: " + r.diagnostics().getDiagnostics());
    }
}
