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
 * #1179 at the MODULE-class static face: a call to a nonexistent static method
 * of a Kof class of the same module ({@code T.muda("x")}) must be refused at
 * compile time with {@code SEM025} — never admitted by {@code kof check} and
 * then lowered into a receiver-less call that the JVM rejects with
 * {@code VerifyError: Operand stack underflow}.
 *
 * <p>The external-class arm of {@code MemberCallTyper} already emits
 * {@code SEM025 "Cannot resolve static method ..."} (lines 99-140); the
 * module-class arm (lines 57-95) did NOT — an absent method fell through with
 * no diagnostic, the resolved symbol stayed null and the lowering produced an
 * inconsistent frame. This test pins both halves: the refusal, and that the
 * fix cannot be "refuse every module-class static call" — the valid
 * {@code T.info("x")} control must still compile and RUN.
 */
class ModuleStaticCallUnknownDiagnosticE2ETest {

    @TempDir
    Path tmp;

    private record ExecResult(boolean compiled, String diagnostics, String output, Throwable loadFailure) {}

    private ExecResult compileAndLoad(String code) throws Exception {
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
    @DisplayName("#1179 metodo estatico inexistente de classe do modulo e recusado no check (SEM025), nunca VerifyError")
    void nonexistentStaticMethodOfModuleClassIsRefused() throws Exception {
        ExecResult r = compileAndLoad("""
            class T {
                static String info(String t) { return t }

                void run() {
                    println(T.muda("x"))
                }
            }

            main() {
                T().run()
            }
            """);
        assertFalse(r.compiled(),
                "T.muda(\"x\") must be refused at compile time; the JVM rejected the emitted bytecode: "
                        + (r.loadFailure() == null ? "" : r.loadFailure()));
        assertTrue(r.diagnostics().contains("SEM025"),
                "expected SEM025 naming the missing static method, got: " + r.diagnostics());
        assertTrue(r.diagnostics().toLowerCase().contains("muda"),
                "the diagnostic must name the missing method, got: " + r.diagnostics());
    }

    @Test
    @DisplayName("#1179 metodo estatico de classe do modulo com aridade errada tambem e recusado")
    void wrongArityStaticMethodOfModuleClassIsRefused() throws Exception {
        ExecResult r = compileAndLoad("""
            class T {
                static String info(String t) { return t }

                void run() {
                    println(T.info())
                }
            }

            main() {
                T().run()
            }
            """);
        assertFalse(r.compiled(),
                "T.info() (wrong arity) must be refused at compile time: "
                        + (r.loadFailure() == null ? "" : r.loadFailure()));
        assertTrue(r.diagnostics().contains("SEM025"),
                "expected SEM025 for the wrong-arity static call, got: " + r.diagnostics());
    }

    @Test
    @DisplayName("#1179 controle: chamada estatica VALIDA de classe do modulo continua compilando e rodando")
    void validStaticMethodOfModuleClassStillRuns() throws Exception {
        ExecResult r = compileAndLoad("""
            class T {
                static String info(String t) { return t }

                void run() {
                    println(T.info("x"))
                }
            }

            main() {
                T().run()
            }
            """);
        assertTrue(r.compiled(), "T.info(\"x\") is a valid static call and must compile: " + r.diagnostics());
        assertEquals("x", r.output().trim(), "the valid static call must run and print its argument");
    }
}
