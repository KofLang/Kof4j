package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * #1178: a {@code Troolean}/{@code Troolean?} RECORD FIELD must behave exactly
 * like an unboxed local, on every face {@code training/language/types.md}
 * documents as idiomatic ({@code if (t)}, {@code t == true}) plus the
 * constructor literal and {@code String.valueOf(t)}.
 *
 * <p>STATE 22/11 (triage lane 192.168.15.30:9093): the file is the RED-first
 * measurement and STAYS in the tree while the fix is parked at the design
 * fork (value equality with unboxing vs identity-only {@code ==}). Measured
 * on tip {@code a1887d8b9}: 2 of 3 RED; the emitter chose the BOXED
 * representation for the field ({@code java/lang/Boolean}) while the
 * comparison consumers emit {@code iconst_1} + {@code if_icmpeq} (an INT
 * against a reference slot) and the record constructor's
 * {@code invokespecial} takes an {@code int} where the descriptor says
 * {@code Boolean} — {@code VerifyError: Bad type on operand stack}, a class
 * that {@code kof check} accepted. The point of this test is that the four
 * faces agree with the local they mirror, not that a specific representation
 * was picked.
 */
class TrooleanRecordFieldE2ETest {

    @TempDir
    Path tmp;

    private record ExecResult(boolean compiled, String diagnostics, String output, Throwable loadFailure) {}

    /** Lines the faces print, one per face, in order. */
    private static final String FACES = """
            record N(Troolean? lida)

            main() {
                var a = N(false)
                if (a.lida == false) { println("eqfalse") } else { println("NO") }
                var b = N(true)
                if (b.lida == true) { println("eqtrue") } else { println("NO") }
                if (b.lida) { println("sugar") } else { println("NO") }
                println(String.valueOf(b.lida))
                var c = N(null)
                if (c.lida == null) { println("null") } else { println("NO") }
                if (a.lida) { println("sugarfalse") } else { println("NO") }
                if (c.lida) { println("NO") } else { println("sugarnull") }
            }
            """;

    /** The reference: the same faces over an unboxed LOCAL of the same type. */
    private static final String LOCAL_CONTROL = """
            main() {
                var v = false
                if (v == false) { println("eqfalse") } else { println("NO") }
                var w = true
                if (w == true) { println("eqtrue") } else { println("NO") }
                println(String.valueOf(w))
            }
            """;

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
    @DisplayName("#1178 controle: as mesmas faces sobre um LOCAL nao-boxeado continuam verdes")
    void unboxedLocalControl() throws Exception {
        ExecResult r = compileAndLoad(LOCAL_CONTROL);
        assertTrue(r.compiled(), "unboxed local control must compile: " + r.diagnostics());
        assertEquals("eqfalse\neqtrue\ntrue", r.output().trim(),
                "control output; load failure: " + r.loadFailure());
    }

    @Test
    @DisplayName("#1178 campo Troolean? de record: comparacao, acucar, literal e String.valueOf sao validos e corretos")
    void nullableTrooleanRecordFieldFaces() throws Exception {
        ExecResult r = compileAndLoad(FACES);
        assertTrue(r.compiled(), "Troolean? record field faces must compile: " + r.diagnostics());
        assertEquals("eqfalse\neqtrue\nsugar\ntrue\nnull\nsugarfalse\nsugarnull", r.output().trim(),
                "every documented Troolean face must produce the unboxed-local result; load failure: "
                        + r.loadFailure());
    }

    @Test
    @DisplayName("#1178 o repro da issue (json.decode<N> com lida:true) carrega e imprime 'lida'")
    void issueReproducerLoads() throws Exception {
        ExecResult r = compileAndLoad("""
            record N(Troolean? lida)

            main() {
                var n = json.decode<N>("{\\"lida\\":true}")
                if (n.lida == true) {
                    println("lida")
                } else {
                    println("nao lida")
                }
            }
            """);
        assertTrue(r.compiled(), "issue reproducer must compile: " + r.diagnostics());
        assertEquals("lida", r.output().trim(),
                "issue reproducer must load and decode true; load failure: " + r.loadFailure());
    }
}
