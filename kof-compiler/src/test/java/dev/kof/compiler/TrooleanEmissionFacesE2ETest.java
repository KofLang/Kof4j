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
 * #1178 — as faces de EMISSAO PURA de um campo {@code Troolean?} de record,
 * SEPARADAS do fork semantico de {@code ==}/{@code if} (que segue parado na
 * decisao da mantenedora, sem toque aqui).
 *
 * <p>O contrato vem de {@code training/language/types.md} §Troolean:
 * <ul>
 *   <li>{@code N(false)} / {@code N(true)} / {@code N(null)} — construir o
 *       record a partir de um literal {@code Bool}/{@code null} e' Kof valido
 *       (o campo existe para guardar os TRES estados).</li>
 *   <li>{@code println(t)} mostra {@code true/false/null} (doc linha 156) —
 *       logo {@code String.valueOf(t)} tambem, pelo mesmo contrato de
 *       impressao.</li>
 * </ul>
 *
 * <p>Nenhuma face daqui usa {@code ==}/{@code if (t)}: a decisao de valor x
 * identidade para {@code ==} com uma caixa nullable fica INTACTA para a
 * mantenedora. Esta classe pina que a MONTAGEM do campo e a IMPRESSAO do
 * campo sao emissao correta por si — o {@code VerifyError} do construtor e o
 * {@code Boolean.valueOf(Boolean)} do {@code String.valueOf} sao defeitos de
 * emissao, nao da semantica de {@code ==}.
 */
class TrooleanEmissionFacesE2ETest {

    @TempDir
    Path tmp;

    private record ExecResult(boolean compiled, String diagnostics, String output, Throwable loadFailure) {}

    /** Construtor a partir dos tres literais, observado por String.valueOf. */
    private static final String CONSTRUCTOR_FACES = """
            record N(Troolean? lida)

            main() {
                var a = N(false)
                println(String.valueOf(a.lida))
                var b = N(true)
                println(String.valueOf(b.lida))
                var c = N(null)
                println(String.valueOf(c.lida))
            }
            """;

    /** json.decode monta o record (bypassa o construtor literal); so' valueOf. */
    private static final String DECODE_FACES = """
            import kof.json
            record N(Troolean? lida)

            main() {
                var t = json.decode<N>("{\\"lida\\":true}")
                println(String.valueOf(t.lida))
                var f = json.decode<N>("{\\"lida\\":false}")
                println(String.valueOf(f.lida))
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
    @DisplayName("#1178 construtor: N(false)/N(true)/N(null) montam o campo e String.valueOf mostra os 3 estados")
    void constructorLiteralsRoundTrip() throws Exception {
        ExecResult r = compileAndLoad(CONSTRUCTOR_FACES);
        assertTrue(r.compiled(), "constructor literals must compile: " + r.diagnostics());
        assertEquals("false\ntrue\nnull", r.output().trim(),
                "the three constructor literals must read back through String.valueOf; load failure: "
                        + r.loadFailure());
    }

    @Test
    @DisplayName("#1178 json.decode + String.valueOf: a caixa Troolean impressa e' true/false, nunca 1/0 nem erro")
    void decodedFieldPrintsItsValue() throws Exception {
        ExecResult r = compileAndLoad(DECODE_FACES);
        assertTrue(r.compiled(), "json.decode faces must compile: " + r.diagnostics());
        assertEquals("true\nfalse", r.output().trim(),
                "String.valueOf of a decoded Troolean field; load failure: " + r.loadFailure());
    }

    /**
     * O CONTRATO NORMATIVO COMPLETO de {@code training/language/types.md}
     * §Troolean, com as expectativas COERENTES com a propria fonte (o ramo
     * que rotula "sugarfalse" e' o ramo FALSE de {@code if (a.lida)}, nao o
     * ramo true): {@code == true/false/null} separa os tres estados, e
     * {@code if (t)} e' acucar de {@code t == true} — o desconhecido cai no
     * ramo false (doc linha 148).
     */
    private static final String DOCUMENTED_CONTRACT = """
            record N(Troolean? lida)

            main() {
                var a = N(false)
                if (a.lida == false) { println("eqfalse") } else { println("NO") }
                if (a.lida == true) { println("NO") } else { println("ne_true_false") }
                if (a.lida) { println("NO") } else { println("sugarfalse") }
                println(String.valueOf(a.lida))
                var b = N(true)
                if (b.lida == true) { println("eqtrue") } else { println("NO") }
                if (b.lida) { println("sugar") } else { println("NO") }
                println(String.valueOf(b.lida))
                var c = N(null)
                if (c.lida == null) { println("null") } else { println("NO") }
                if (c.lida == true) { println("NO") } else { println("null_ne_true") }
                if (c.lida) { println("NO") } else { println("sugarnull") }
                println(String.valueOf(c.lida))
            }
            """;

    @Test
    @DisplayName("#1178 contrato de types.md: == true/false/null separa os 3 estados e if(t) == acucar de t==true")
    void documentedComparisonAndSugarContract() throws Exception {
        ExecResult r = compileAndLoad(DOCUMENTED_CONTRACT);
        assertTrue(r.compiled(), "documented contract must compile: " + r.diagnostics());
        assertEquals("eqfalse\nne_true_false\nsugarfalse\nfalse\neqtrue\nsugar\ntrue\nnull\nnull_ne_true\nsugarnull\nnull",
                r.output().trim(),
                "documented Troolean ==/if contract; load failure: " + r.loadFailure());
    }
}
