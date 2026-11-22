package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * #1189: a String method that EXISTS in the registry called with an arity no
 * overload takes ({@code "abcdef".substring(1, 3, 4)}) passed {@code check}
 * with "no errors" and died at load with
 * {@code NoSuchMethodError: java.lang.String.substring(int, int, int)} —
 * measured on tip {@code e4d8165c5}. The return also degraded to Unknown, so
 * a following call on it ({@code a.length()}) was emitted over an UNKNOWN.
 * The guard lives in the shared frontend ({@code StringReceiverGuards}), so
 * every target inherits the diagnostic.
 */
class Tri9093StringArityE2ETest {

    @TempDir
    Path tmp;

    private record ExecResult(boolean compiled, String diagnostics, String output, Throwable loadFailure) {}

    private ExecResult compileAndRun(String code) throws Exception {
        Path src = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(src, code);
        Path out = Files.createTempDirectory(tmp, "out-jvm");
        CompilationResult r = new CompilerDriver().compile(src, out, Target.JVM);
        if (!r.success()) {
            StringBuilder sb = new StringBuilder();
            r.diagnostics().getDiagnostics()
                    .forEach(d -> sb.append('[').append(d.code()).append("] ").append(d.message()).append('\n'));
            return new ExecResult(false, sb.toString(), "", null);
        }
        PrintStream oldOut = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true));
        try {
            URLClassLoader cl = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class)
                    .invoke(null, (Object) new String[0]);
            return new ExecResult(true, "", buf.toString(), null);
        } catch (java.lang.reflect.InvocationTargetException e) {
            return new ExecResult(true, "", buf.toString(), e.getCause());
        } catch (Throwable t) {
            return new ExecResult(true, "", buf.toString(), t);
        } finally {
            System.setOut(oldOut);
        }
    }

    @Test
    @DisplayName("#1189 substring de 3 args: recusado no check com SEM014 nomeando as aridades validas")
    void substringWithThreeArgsIsRejected() throws Exception {
        ExecResult r = compileAndRun("""
            main() {
                var texto = "abcdef".substring(1, 3, 4)
                println(texto)
            }
            """);
        assertFalse(r.compiled(), "substring(Int, Int, Int) is not a String method and must fail the check"
                + " (the JVM emitter builds invokespecial ...substring(III))");
        assertTrue(r.diagnostics().contains("SEM014"),
                "the diagnostic must be SEM014 (arity/type of an argument list), got: " + r.diagnostics());
        assertTrue(r.diagnostics().contains("substring(Int)") && r.diagnostics().contains("substring(Int, Int)"),
                "the diagnostic must name the valid arities, got: " + r.diagnostics());
    }

    @Test
    @DisplayName("#1189 o vazamento de UNKNOWN: chamar um metodo do resultado da chamada invalida tambem e recusado")
    void callingThroughTheInvalidCallIsRejected() throws Exception {
        ExecResult r = compileAndRun("""
            main() {
                var a = "abcdef".substring(1, 3, 4)
                println(a.length())
            }
            """);
        assertFalse(r.compiled(), "the invalid call must not leave an UNKNOWN receiver behind");
        assertTrue(r.diagnostics().contains("SEM014"), "expected SEM014, got: " + r.diagnostics());
    }

    @Test
    @DisplayName("#1189 controle: as aridades validas do substring continuam compilando e rodando")
    void validAritiesStayGreen() throws Exception {
        ExecResult r = compileAndRun("""
            main() {
                println("abcdef".substring(1))
                println("abcdef".substring(1, 3))
            }
            """);
        assertTrue(r.compiled(), "substring(Int) and substring(Int, Int) stay legal: " + r.diagnostics());
        assertEquals("bcdef\nbc", r.output().trim(), "valid substring output; load failure: " + r.loadFailure());
    }
}
