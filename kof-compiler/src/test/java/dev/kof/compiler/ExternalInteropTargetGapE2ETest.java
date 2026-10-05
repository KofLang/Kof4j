package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #760: JVM interop ({@code import java.X}) on a non-JVM-backed target must
 * refuse at COMPILE time with the named {@code INTEROP003} instead of leaking a
 * raw {@code ld} undefined-reference (Native) or a runtime
 * {@code ReferenceError: java_io_File is not defined} (JS).
 *
 * <p>§510 gated only the external-class STATIC FIELD face
 * ({@code Integer.MAX_VALUE}). The constructor ({@code new File(...)}), the
 * instance method ({@code sc.nextLine()}) and the static method
 * ({@code Runtime.getRuntime()}) faces had no target gate: the backend emitted
 * the real {@code java_*} call and the artifact died at link/run — the same
 * rule-5/R6 silent cross-target divergence §510 fixed for fields.
 *
 * <p>JVM/SCRIPT/ANDROID keep the real interop; the wrapper statics
 * ({@code Integer.parseInt}, {@code Long.parseLong}, {@code Double.isNaN}, …)
 * stay supported on JS/Native through their dedicated shims.
 */
class ExternalInteropTargetGapE2ETest {

    @TempDir Path tmp;

    private static final String CTOR_INSTANCE = """
            import java.io.File

            main() {
                var f = new File("secret.txt")
                println(f.getName())
            }
            """;

    private static final String STATIC_METHOD = """
            import java.lang.Runtime

            main() {
                var rt = Runtime.getRuntime()
                println(rt.availableProcessors())
            }
            """;

    /** Wrapper statics reached through the explicit JDK name stay supported. */
    private static final String WRAPPER_STATICS = """
            import java.lang.Integer
            import java.lang.Long

            main() {
                println(Integer.parseInt("42") + 1)
                println(Long.parseLong("100") * 2L)
            }
            """;

    private record Result(boolean success, String diags) {}

    private Result compileOnly(String code, Target target) throws Exception {
        Files.writeString(tmp.resolve("S.kf"), code);
        Path out = Files.createTempDirectory(tmp, "o");
        CompilationResult r = new CompilerDriver().compile(tmp.resolve("S.kf"), out, target);
        StringBuilder diags = new StringBuilder();
        r.diagnostics().getDiagnostics().forEach(d -> diags.append(d.code()).append(' ')
                .append(d.message()).append('\n'));
        return new Result(r.success(), diags.toString());
    }

    private Result compileAndRunJvm(String code) throws Exception {
        Files.writeString(tmp.resolve("S.kf"), code);
        Path out = Files.createTempDirectory(tmp, "o");
        CompilationResult r = new CompilerDriver().compile(tmp.resolve("S.kf"), out, Target.JVM);
        StringBuilder diags = new StringBuilder();
        r.diagnostics().getDiagnostics().forEach(d -> diags.append(d.code()).append(' ')
                .append(d.message()).append('\n'));
        if (!r.success()) return new Result(false, diags.toString());
        var oldOut = System.out;
        var buf = new java.io.ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(buf, true));
        try {
            var cl = new java.net.URLClassLoader(new java.net.URL[]{out.toUri().toURL()},
                    getClass().getClassLoader());
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            return new Result(true, diags.toString());
        } catch (java.lang.reflect.InvocationTargetException e) {
            return new Result(false, diags + "THROW: " + e.getCause());
        } catch (Throwable t) {
            return new Result(false, diags + "LOAD: " + t);
        } finally {
            System.setOut(oldOut);
        }
    }

    // ---- Native: the reported face ----

    @Test
    void nativeRejectsExternalConstructorAndInstanceMethodWithInterop003() throws Exception {
        Result r = compileOnly(CTOR_INSTANCE, Target.NATIVE);
        assertFalse(r.success(), "Native must refuse at compile time, was: " + r.diags());
        assertTrue(r.diags().contains("INTEROP003"),
                "expected INTEROP003, was: " + r.diags());
        assertFalse(r.diags().contains("COMP001") || r.diags().contains("undefined reference"),
                "must refuse BEFORE the linker, was: " + r.diags());
    }

    @Test
    void nativeRejectsExternalStaticMethodWithInterop003() throws Exception {
        Result r = compileOnly(STATIC_METHOD, Target.NATIVE);
        assertFalse(r.success(), "Native must refuse at compile time, was: " + r.diags());
        assertTrue(r.diags().contains("INTEROP003"), "expected INTEROP003, was: " + r.diags());
    }

    // ---- JS: the same divergence class ----

    @Test
    void jsRejectsExternalConstructorAndInstanceMethodWithInterop003() throws Exception {
        Result r = compileOnly(CTOR_INSTANCE, Target.JS);
        assertFalse(r.success(), "JS must refuse at compile time, was: " + r.diags());
        assertTrue(r.diags().contains("INTEROP003"), "expected INTEROP003, was: " + r.diags());
    }

    // ---- JVM keeps the real interop ----

    @Test
    void jvmKeepsExternalConstructorAndInstanceMethod() throws Exception {
        Result r = compileAndRunJvm(CTOR_INSTANCE);
        assertTrue(r.success(), "JVM is JVM-backed: must compile and run, was: " + r.diags());
    }

    // ---- wrapper statics stay supported on Native (the §235 shims) ----

    @Test
    void nativeKeepsWrapperStaticsThroughExplicitJdkName() throws Exception {
        Result r = compileOnly(WRAPPER_STATICS, Target.NATIVE);
        assertTrue(r.success(),
                "Integer.parseInt/Long.parseLong on Native are supported shims, was: " + r.diags());
    }

    @Test
    void jsKeepsWrapperStaticsThroughExplicitJdkName() throws Exception {
        Result r = compileOnly(WRAPPER_STATICS, Target.JS);
        assertTrue(r.success(),
                "Integer.parseInt/Long.parseLong on JS are supported shims, was: " + r.diags());
    }
}
