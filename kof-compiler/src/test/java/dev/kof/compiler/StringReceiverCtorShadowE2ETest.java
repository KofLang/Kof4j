package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * issue #996 — an unknown method on a String receiver compiled CLEAN when a
 * class/record of that name existed: {@code BuiltinCallTyper}'s implicit-ctor
 * branch ({@code allClasses().get(methodName())}) fired for ANY receiver and
 * returned the class type, so the SEM025/SEM102 guards (which only speak when
 * the resolved type is {@code Unknown}) stayed silent and every target failed
 * its own way — JVM {@code VerifyError} at load, JS {@code TypeError}, native
 * link failure, Script silent exit 1.
 *
 * <p>Fix: the implicit-ctor form is the TOP-LEVEL {@code Foo(1)} or the
 * type-NAME-qualified {@code Foo.Foo(1)}; a VALUE receiver falls to instance
 * resolution, where the receiver type's guard diagnoses
 * (String → SEM025; the other receivers the reporter's control table lists
 * keep their own refusals).
 *
 * <p>The oracle is compile-time refusal on every target. RED-first: the JVM
 * face was RED pre-fix (compiled clean).
 */
class StringReceiverCtorShadowE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            class Foo {
                Int x
                public constructor(Int x) { this.x = x }
            }
            main() {
                String s = "a"
                var f = s.Foo(1)
                println("done")
            }
            """;

    /** #994's pinned form must KEEP the ctor-arity diagnostic (type-name receiver). */
    private static final String TYPE_NAME_RECEIVER = """
            class Foo {
                Int x
                public constructor(Int x) { this.x = x }
            }
            main() {
                var f = Foo.Foo(1, 2)
                println("done")
            }
            """;

    private CompilationResult compile(String source, Path dir, Target target) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), source);
        return driver.compile(dir.resolve("Main.kf"), dir.resolve("out"), target);
    }

    @Test
    void stringReceiverShadowedByClassIsDiagnosedOnJvm(@TempDir Path tempDir) throws IOException {
        CompilationResult r = compile(SRC, tempDir.resolve("jvm"), Target.JVM);
        assertFalse(r.success(), "got: " + r.diagnostics().getDiagnostics());
        String diags = "" + r.diagnostics().getDiagnostics();
        assertTrue(diags.contains("SEM025"), diags);
        assertTrue(diags.contains("'Foo'"), diags);
    }

    @Test
    void stringReceiverShadowedByClassIsDiagnosedOnJs(@TempDir Path tempDir) throws IOException {
        CompilationResult r = compile(SRC, tempDir.resolve("js"), Target.JS);
        assertFalse(r.success(), "got: " + r.diagnostics().getDiagnostics());
        assertTrue(("" + r.diagnostics().getDiagnostics()).contains("SEM025"),
                "" + r.diagnostics().getDiagnostics());
    }

    @Test
    void stringReceiverShadowedByRecordIsDiagnosed(@TempDir Path tempDir) throws IOException {
        String rec = """
                record Foo(Int x)
                main() {
                    String s = "a"
                    var f = s.Foo(1)
                    println("done")
                }
                """;
        CompilationResult r = compile(rec, tempDir.resolve("rec"), Target.JVM);
        assertFalse(r.success(), "got: " + r.diagnostics().getDiagnostics());
        assertTrue(("" + r.diagnostics().getDiagnostics()).contains("SEM025"),
                "" + r.diagnostics().getDiagnostics());
    }

    @Test
    void typeNameReceiverKeepsTheCtorArityDiagnostic(@TempDir Path tempDir) throws IOException {
        CompilationResult r = compile(TYPE_NAME_RECEIVER, tempDir.resolve("tn"), Target.JVM);
        assertFalse(r.success(), "got: " + r.diagnostics().getDiagnostics());
        String diags = "" + r.diagnostics().getDiagnostics();
        assertTrue(diags.contains("SEM023") && diags.contains("no constructor of 'Foo' with 2 argument(s)"),
                "a face da #994 deve continuar fixada: " + diags);
    }

    @Test
    void noShadowStillRefusesSem025(@TempDir Path tempDir) throws IOException {
        String noClass = """
                main() {
                    String s = "a"
                    var f = s.Foo(1)
                    println("done")
                }
                """;
        CompilationResult r = compile(noClass, tempDir.resolve("ns"), Target.JVM);
        assertFalse(r.success(), "got: " + r.diagnostics().getDiagnostics());
        assertTrue(("" + r.diagnostics().getDiagnostics()).contains("SEM025"),
                "" + r.diagnostics().getDiagnostics());
    }

    @Test
    void realStringMethodsUnaffectedByASimilarlyNamedClass(@TempDir Path tempDir) throws IOException {
        String program = """
                class Length {
                    Int n
                    public constructor(Int n) { this.n = n }
                }
                main() {
                    var s = "abc"
                    println(s.length())
                    println(s.isEmpty())
                    println("done")
                }
                """;
        Path dir = tempDir.resolve("real");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), program);
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), dir.resolve("out"), Target.JVM);
        assertTrue(r.success(), "got: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                "-cp", dir.resolve("out").toString(), "Default.Main").redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).strip();
        try {
            p.waitFor();
        } catch (InterruptedException e) {
            throw new IOException("interrupted", e);
        }
        assertEquals("3\nfalse\ndone", out, "controle da issue: métodos reais da String não são sombreados");
    }

    @Test
    void nativeX86RefusesAtCompileToo(@TempDir Path tempDir) throws IOException {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        CompilationResult r = compile(SRC, tempDir.resolve("nat"), Target.NATIVE);
        assertFalse(r.success(), "got: " + r.diagnostics().getDiagnostics());
        assertTrue(("" + r.diagnostics().getDiagnostics()).contains("SEM025"),
                "" + r.diagnostics().getDiagnostics());
    }

    private static boolean has(String... progs) {
        for (String prog : progs) {
            try {
                new ProcessBuilder(prog, "--version").start().waitFor();
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }
}
