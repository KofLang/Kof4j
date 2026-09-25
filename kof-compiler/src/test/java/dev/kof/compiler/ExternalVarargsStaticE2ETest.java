package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §500 — static call on an imported external class name (or on a builtin
 * type name) whose real JDK signature is VARARGS. Before the fix the
 * classpath resolvers matched by name+fixed-arity only (no ACC_VARARGS /
 * isVarArgs), so the valid call fell through to a fabricated descriptor:
 * {@code Arrays.asList(1, 2)} emitted {@code invokevirtual "".asList:(II)}
 * (empty owner — the class dies at LOAD) and {@code String.join(", ", "a",
 * "b")} emitted {@code invokestatic String.join:(String,String,String)},
 * dying with {@code NoSuchMethodError} at RUN. An unknown name
 * ({@code Arrays.bogus(1)}) emitted the same empty-owner garbage instead of
 * a diagnostic (R6). The fix teaches {@code JdkReflectionResolver} and
 * {@code ExternalClasspath} varargs, packs the trailing arguments into the
 * component-type array at the two call sites, and turns the unresolved
 * static-on-class-name into {@code SEM025}.
 *
 * <p>Goldens are MEASURED against the bare JVM oracle
 * ({@code java.util.Arrays.asList(1,2)} → {@code [1, 2]},
 * {@code String.join(", ", "a", "b")} → {@code a, b}), never from memory.
 */
public class ExternalVarargsStaticE2ETest {

    @TempDir Path tmp;

    private record Result(boolean success, String output, String diags) {}

    private Result compileAndRun(String code) throws Exception {
        Files.writeString(tmp.resolve("S.kf"), code);
        Path out = Files.createTempDirectory(tmp, "o");
        CompilationResult r = new CompilerDriver().compile(tmp.resolve("S.kf"), out, Target.JVM);
        StringBuilder diags = new StringBuilder();
        r.diagnostics().getDiagnostics().forEach(d -> diags.append(d.code()).append(' ')
                .append(d.message()).append('\n'));
        if (!r.success()) {
            return new Result(false, "", diags.toString());
        }
        var oldOut = System.out;
        var buf = new java.io.ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(buf, true));
        try {
            var cl = new java.net.URLClassLoader(new java.net.URL[]{out.toUri().toURL()},
                    getClass().getClassLoader());
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            return new Result(true, buf.toString(), diags.toString());
        } catch (java.lang.reflect.InvocationTargetException e) {
            return new Result(false, "THROW: " + e.getCause(), diags.toString());
        } catch (Throwable t) {
            return new Result(false, "LOAD: " + t, diags.toString());
        } finally {
            System.setOut(oldOut);
        }
    }

    @Test
    void importedClassVarargsStaticMatchesJvmOracle() throws Exception {
        Result r = compileAndRun("""
            import java.util.Arrays
            main() {
                val l = Arrays.asList(1, 2)
                println(l)
                val e = Arrays.asList()
                println(e)
            }
            """);
        // Golden measured against the bare JVM (od -c): [1, 2] / []
        assertTrue(r.success(), "Arrays.asList must compile (was §500 empty-owner): " + r.diags());
        assertFalse(r.output().contains("THROW") || r.output().contains("LOAD"),
                "must load and run, no NoSuchMethodError: " + r.output());
        assertEqualsJvm("[1, 2]\n[]\n", r.output());
    }

    private void assertEqualsJvm(String expected, String actual) {
        assertTrue(actual.contains(expected),
                "expected JVM-oracle output [" + expected.trim() + "] inside: " + actual);
    }

    @Test
    void builtinNameVarargsStaticMatchesJvmOracle() throws Exception {
        Result r = compileAndRun("""
            main() {
                println(String.join(", ", "a", "b"))
            }
            """);
        assertTrue(r.success(), "String.join must compile: " + r.diags());
        assertFalse(r.output().contains("THROW") || r.output().contains("LOAD"),
                "String.join(3-arg fabricated) died NoSuchMethodError before the fix: " + r.output());
        // Golden measured against the bare JVM: "a, b"
        assertTrue(r.output().contains("a, b"), "expected `a, b` inside: " + r.output());
    }

    @Test
    void unknownStaticMethodOnImportedClassIsSem025() throws Exception {
        Result r = compileAndRun("""
            import java.util.Arrays
            main() {
                println(Arrays.bogus(1))
            }
            """);
        assertFalse(r.success(), "Arrays.bogus(1) must NOT compile (empty-owner emit was §500)");
        assertTrue(r.diags().contains("SEM025"),
                "expected SEM025 naming the unresolvable static method, was: " + r.diags());
    }

    @Test
    void validFixedArityStaticInteropStillCompiles() throws Exception {
        Result r = compileAndRun("""
            import java.util.Objects
            main() {
                println(Objects.requireNonNull("x"))
            }
            """);
        assertTrue(r.success(), "fixed-arity interop must keep compiling: " + r.diags());
        assertTrue(r.output().contains("x"), "control output: " + r.output());
    }
}
