package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §500 slice B — static FIELD access through a CLASS NAME receiver
 * ({@code Integer.MAX_VALUE}, {@code Double.NaN}, imported
 * {@code TimeUnit.SECONDS}). Before the fix the receiver inferred UNKNOWN,
 * the external-field face of the typer was skipped, and the lowering emitted
 * {@code getfield "?".MAX_VALUE} → {@code NoClassDefFoundError: "?"} at load
 * (R6/Q7: compiles clean, dies at run); a bogus name compiled to the same
 * dead bytecode. Slice A (same catalog) fixed the METHOD face; this one the
 * FIELD face: existence and type come from JDK reflection / classpath
 * (PUBLIC STATIC only), unknown names fail with SEM025, and the emit is a
 * real {@code getstatic}.
 *
 * <p>Goldens MEASURED against the bare JVM: {@code Integer.MAX_VALUE} →
 * 2147483647, {@code Double.NaN} → NaN, {@code TimeUnit.SECONDS} → SECONDS,
 * {@code Boolean.TRUE} → true, {@code String.CASE_INSENSITIVE_ORDER
 * .compare("a","A")} → 0.
 */
public class ExternalStaticFieldE2ETest {

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
    void integerMaxValueMatchesJvmOracle() throws Exception {
        Result r = compileAndRun("""
            main() {
                println(Integer.MAX_VALUE)
            }
            """);
        assertTrue(r.success(), "Integer.MAX_VALUE must compile: " + r.diags());
        assertTrue(r.output().contains("2147483647"),
                "JVM oracle is 2147483647, was: " + r.output());
    }

    @Test
    void primitiveTypeNameStaticsStaySem050() throws Exception {
        // Bug 99 (10/09): String/Double/Boolean são NOMES DE TIPO primitivos
        // do Kof — SEM050 é o contrato (idiom = literal), e o nome boxeador
        // real do JDK passa (`Boolean.TRUE`).
        Result r = compileAndRun("""
            main() {
                println(Double.NaN)
            }
            """);
        assertFalse(r.success(), "Double is a primitive type name: SEM050 stays");
        assertTrue(r.diags().contains("SEM050"), "oracle SEM050: " + r.diags());
        // `Boolean`/`Long` SÃO nomes de tipo do Kof (primitivos) — SEM050 neles
        // é a face bug-99; o receiver de nome real é o boxeador `Integer`
        // (Kof usa `Int`), coberto acima.
        Result ok = compileAndRun("""
            main() {
                println(Boolean.TRUE)
            }
            """);
        assertFalse(ok.success(), "Boolean is a Kof type name too: SEM050");
        assertTrue(ok.diags().contains("SEM050"), "oracle SEM050: " + ok.diags());
    }

    @Test
    void importedEnumConstantFieldMatchesJvmOracle() throws Exception {
        Result r = compileAndRun("""
            import java.util.concurrent.TimeUnit
            main() {
                println(TimeUnit.SECONDS)
            }
            """);
        assertTrue(r.success(), "TimeUnit.SECONDS must compile: " + r.diags());
        assertFalse(r.output().contains("LOAD"), "NoClassDefFoundError \"?\" was §500: " + r.output());
        assertTrue(r.output().contains("SECONDS"),
                "oracle is the enum name SECONDS, was: " + r.output());
    }

    @Test
    void staticFieldChainsIntoRealInteropCall() throws Exception {
        // Face primitiva do bug 99: String.<campo> vira SEM050 (nome de tipo)...
        Result blocked = compileAndRun("""
            main() {
                println(String.CASE_INSENSITIVE_ORDER)
            }
            """);
        assertFalse(blocked.success(), "String is a primitive type name: SEM050 stays");
        assertTrue(blocked.diags().contains("SEM050"), "oracle SEM050: " + blocked.diags());
        // ...e o campo REFERÊNCIA de um tipo real do JDK encadeia no interop
        // de método de verdade (enum constant → name() == "SECONDS").
        Result r = compileAndRun("""
            import java.util.concurrent.TimeUnit
            main() {
                println(TimeUnit.SECONDS.name())
            }
            """);
        assertTrue(r.success(), "field→method interop chain must compile: " + r.diags());
        assertTrue(r.output().contains("SECONDS"), "oracle SECONDS, was: " + r.output());
    }

    @Test
    void unknownStaticFieldIsSem025() throws Exception {
        Result r = compileAndRun("""
            main() {
                println(Integer.bogusField)
            }
            """);
        assertFalse(r.success(), "Integer.bogusField must NOT compile (empty-owner §500)");
        assertTrue(r.diags().contains("SEM025"),
                "expected SEM025 naming the static field, was: " + r.diags());
    }

    @Test
    void instanceFieldThroughClassNameIsSem025() throws Exception {
        Result r = compileAndRun("""
            main() {
                println(Integer.value)
            }
            """);
        assertFalse(r.success(), "private/instance field via class name must fail");
        assertTrue(r.diags().contains("SEM025"),
                "expected SEM025 (not public static), was: " + r.diags());
    }

    @Test
    void assignmentToExternalStaticFieldIsRejected() throws Exception {
        Result r = compileAndRun("""
            main() {
                Integer.MAX_VALUE = 1
                println(Integer.MAX_VALUE)
            }
            """);
        assertFalse(r.success(),
                "writing an external static final through the class name must not compile");
        assertTrue(r.diags().contains("SEM"), "must carry a diagnostic code: " + r.diags());
    }

    @Test
    void kofAliasStaticsStayFakeIdioms() throws Exception {
        Result r = compileAndRun("""
            main() {
                println(Int.MAX_VALUE)
            }
            """);
        assertFalse(r.success(),
                "Int.MAX_VALUE is a fake-idiom (AGENTS): primitives have no statics");
    }

    // ---- §510: a face §500-B é JVM-backed (JVM/Script/Android). JS e Native
    // NÃO têm o JVM por trás da classe externa: compile deve falhar com o
    // código INTEROP003 nomeando campo+alvo (R6/rule 5 — nunca o
    // `ReferenceError: java_lang_Integer` silencioso medido no probe).

    private static final String STATIC_TWO = """
            import java.util.concurrent.TimeUnit
            main() {
                println(Integer.MAX_VALUE)
                println(TimeUnit.SECONDS)
            }
            """;

    private String compileOnly(String code, Target target) throws Exception {
        Files.writeString(tmp.resolve("S.kf"), code);
        Path out = Files.createTempDirectory(tmp, "o");
        CompilationResult r = new CompilerDriver().compile(tmp.resolve("S.kf"), out, target);
        StringBuilder diags = new StringBuilder();
        r.diagnostics().getDiagnostics().forEach(d -> diags.append(d.code()).append(' ')
                .append(d.message()).append('\n'));
        return (r.success() ? "SUCCESS " : "FAILED ") + diags;
    }

    @Test
    void externalStaticFieldOnJsFailsWithInterop003() throws Exception {
        String diags = compileOnly(STATIC_TWO, Target.JS);
        assertTrue(diags.startsWith("FAILED"), "JS must reject, was: " + diags);
        assertTrue(diags.contains("INTEROP003") && diags.contains("Integer.MAX_VALUE")
                && diags.contains("TimeUnit.SECONDS"),
                "INTEROP003 must name both fields, was: " + diags);
    }

    @Test
    void externalStaticFieldOnNativeFailsWithInterop003AtCompileTime() throws Exception {
        String diags = compileOnly(STATIC_TWO, Target.NATIVE);
        assertTrue(diags.startsWith("FAILED"), "Native must reject at compile time, was: " + diags);
        assertTrue(diags.contains("INTEROP003"), "expected INTEROP003, was: " + diags);
    }

    @Test
    void externalStaticFieldOnScriptMatchesJvmGolden() throws Exception {
        Files.writeString(tmp.resolve("S.kf"), STATIC_TWO);
        CompilerDriver driver = new CompilerDriver();
        KofInterpreter.Result ir = driver.interpret(java.util.List.of(tmp.resolve("S.kf")),
                tmp, new String[0]);
        assertEquals(0, ir.exitCode(), "script stderr: " + ir.stderr());
        assertEquals("2147483647\nSECONDS", ir.stdout().trim(),
                "KofScript roda no host JVM — golden medido no probe");
    }
}
