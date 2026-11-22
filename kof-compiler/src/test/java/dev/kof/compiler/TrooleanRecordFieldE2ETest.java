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
 * <p>STATE 22/11 (lane deploy-flow/quality-gate 192.168.100.19:0): GREEN. The
 * root cause was NOT a design fork but a TYPE defect: {@code Troolean?}
 * parsed as {@code Nullable(Nullable(Bool))} and the extra {@code Nullable}
 * escaped the guards that test {@code inner() instanceof PrimitiveType}
 * (asymmetric with {@code isPrimitiveType}, which recurses). Flattening
 * {@code Nullable(Nullable(X)) -> Nullable(X)} in the {@code NullableType}
 * constructor fixed the record constructor's int-into-Boolean
 * {@code invokespecial}, the {@code Boolean.valueOf(Boolean)} of
 * {@code String.valueOf}, AND the {@code ==}/{@code if} faces — all of which
 * then match the documented {@code training/language/types.md} contract
 * (value equality with unboxing; {@code if (t)} = sugar for {@code t == true},
 * unknown falls in the false branch). The 55th-line face had its branches
 * swapped in the SOURCE (a false {@code a.lida} was labelled in the TRUE
 * branch); the EXPECTED values were already the documented contract, so the
 * source was corrected and the assertion kept intact (no weakening).
 * RED/GREEN proved in {@code TrooleanEmissionFacesE2ETest}.
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
                if (a.lida) { println("NO") } else { println("sugarfalse") }
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
