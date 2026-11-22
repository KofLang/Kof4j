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
 * #1175, the FIELD sibling of #1179: a read of a nonexistent static field of a
 * Kof class of the same module ({@code Ansi.LARGURA_CONTEUD0}, a {@code 0}-for-{@code O}
 * typo) must be refused at compile time with {@code SEM025} — never admitted by
 * {@code kof check} and then lowered as an INSTANCE access, which the JVM
 * rejects with {@code VerifyError: Bad local variable type} (there is no
 * {@code aload_0} in a static method).
 *
 * <p>The root cause is measured in {@code SemFieldAccessTyper.infer}: a bare
 * class name as receiver is not a local, so the semantic typer yields
 * {@code UnknownType}; the {@code SEM025 "Cannot resolve field ..."} guard lives
 * inside {@code recvType instanceof Type.ClassType} and
 * {@code StaticClassReceiver.semInfer} only serves EXTERNAL classes. The
 * module-class static field therefore fell through with no diagnostic. The
 * second half of the pin is that the fix cannot refuse every module-class
 * static read: the valid {@code Ansi.LARGURA_CONTEUDO} control must still
 * compile and RUN.
 */
class ModuleStaticFieldUnknownDiagnosticE2ETest {

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
    @DisplayName("#1175 campo estatico inexistente de classe do modulo e recusado no check (SEM025), nunca VerifyError")
    void nonexistentStaticFieldOfModuleClassIsRefused() throws Exception {
        ExecResult r = compileAndLoad("""
            class Ansi {
                static Int LARGURA_CONTEUDO = 100
            }

            class Lista {
                static String item() {
                    return String.valueOf(Ansi.LARGURA_CONTEUD0)
                }
            }

            main() { println(Lista.item()) }
            """);
        assertFalse(r.compiled(),
                "Ansi.LARGURA_CONTEUD0 must be refused at compile time; the JVM rejected the emitted "
                        + "bytecode: " + (r.loadFailure() == null ? "" : r.loadFailure()));
        assertTrue(r.diagnostics().contains("SEM025"),
                "expected SEM025 naming the missing static field, got: " + r.diagnostics());
        assertTrue(r.diagnostics().contains("LARGURA_CONTEUD0"),
                "the diagnostic must name the missing field, got: " + r.diagnostics());
    }

    @Test
    @DisplayName("#1175 controle: leitura de campo estatico VALIDO de classe do modulo continua compilando e rodando")
    void validStaticFieldOfModuleClassStillRuns() throws Exception {
        ExecResult r = compileAndLoad("""
            class Ansi {
                static Int LARGURA_CONTEUDO = 100
            }

            class Lista {
                static String item() {
                    return String.valueOf(Ansi.LARGURA_CONTEUDO)
                }
            }

            main() { println(Lista.item()) }
            """);
        assertTrue(r.compiled(), "Ansi.LARGURA_CONTEUDO is a valid static read and must compile: "
                + r.diagnostics());
        assertEquals("100", r.output().trim(), "the valid static read must run and print its value");
    }
}
