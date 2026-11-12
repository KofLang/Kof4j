package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kof.expr slice 1 (`D-KOF-EXPR`) — the matcher: atoms (`start`/`end` anchors +
 * literal atoms), sequence, and the two operations (`matches` = whole-subject
 * Bool; `match` = the subject or null). Strategy P: the lowering emits the SAME
 * IR user String calls emit (`length`/`substring` INSTANCE,
 * `kof_string_equals` FUNCTION) — zero new backend op — so JVM and Script run
 * the same compiled form and the oracle is byte-identical. Everything beyond
 * the slice set refuses at lowering with EXPR001 naming its slice (never a
 * silent fallback), and the declaration always lowers (an unused `expr` is a
 * declared value, not dead text).
 */
class KofExprMatchE2ETest {

    @TempDir
    Path tmp;

    private static final String PARITY = """
            expr greeting {
                "hello"
                " "
                "world"
            }

            expr exact {
                start
                "kof"
                end
            }

            expr gap {
                "a"
                ""
                "b"
            }

            main() {
                println(greeting.matches("hello world"))
                println(greeting.matches("hello world!"))
                println(greeting.matches("hello"))
                var m = greeting.match("hello world")
                if (m != null) { println(m) } else { println("none") }
                var n = greeting.match("hello")
                if (n != null) { println(n) } else { println("none") }
                println(exact.matches("kof"))
                println(exact.matches("koff"))
                println(gap.matches("ab"))
                println(gap.matches("a b"))
            }
            """;

    private static final String EXPECTED = "true\nfalse\nfalse\nhello world\nnone\ntrue\nfalse\ntrue\nfalse\n";

    @Test
    void sequenceMatchesWholeSubjectOnly() throws Exception {
        String src = """
                expr greeting {
                    "hello"
                    " "
                    "world"
                }

                main() {
                    println(greeting.matches("hello world"))
                    println(greeting.matches("hello world!"))
                    println(greeting.matches("hello"))
                    var m = greeting.match("hello world")
                    if (m != null) { println(m) } else { println("none") }
                    var n = greeting.match("hello")
                    if (n != null) { println(n) } else { println("none") }
                }
                """;
        assertEquals("true\nfalse\nfalse\nhello world\nnone\n", runJvm("seq", src));
    }

    @Test
    void anchorsKeepTheWholeSubjectContract() throws Exception {
        String src = """
                expr exact {
                    start
                    "kof"
                    end
                }

                main() {
                    println(exact.matches("kof"))
                    println(exact.matches("koff"))
                    println(exact.matches(" ko"))
                }
                """;
        assertEquals("true\nfalse\nfalse\n", runJvm("anchor", src));
    }

    @Test
    void emptyLiteralConsumesNothing() throws Exception {
        String src = """
                expr gap {
                    "a"
                    ""
                    "b"
                }

                main() {
                    println(gap.matches("ab"))
                    println(gap.matches("a b"))
                }
                """;
        assertEquals("true\nfalse\n", runJvm("gap", src));
    }

    @Test
    void scriptParityIsByteIdentical() throws Exception {
        assertEquals(EXPECTED, runJvm("parity", PARITY));
        assertEquals(EXPECTED, runScript(PARITY),
                "Strategy P compiles ONE form: the interpreter and the JVM must agree byte for byte");
    }

    @Test
    void ptSurfaceCompilesAndMatches() throws Exception {
        String src = """
                molde saudacao {
                    inicio
                    "olá"
                    fim
                }

                principal() {
                    diga(saudacao.matches("olá"))
                    diga(saudacao.matches("olá!"))
                }
                """;
        Path file = tmp.resolve("pt").resolve("Main.ptkf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        Path out = tmp.resolve("pt-out");
        CompilationResult r = new CompilerDriver().compile(file, out, Target.JVM);
        assertTrue(r.success(), r.diagnostics().getDiagnostics().toString());
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try (URLClassLoader cl = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
            Class.forName("Default.Main", true, cl).getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(old);
        }
        assertEquals("true\nfalse\n", buf.toString(StandardCharsets.UTF_8));
    }

    @Test
    void unusedDeclarationStillLowers() throws Exception {
        String src = """
                expr unused { "x" }

                main() {
                    println("ok")
                }
                """;
        assertEquals("ok\n", runJvm("unused", src));
    }

    @Test
    void quantifiersEnterWithSlice2AndMatch() throws Exception {
        // Fatia 2 (D-KOF-EXPR): o quantificador da fatia 2 agora casa de verdade
        // (a face de contrato do spike-0 evoluiu duas vezes: sem lowering ->
        // EXPR001 fatia 2 -> o quantificador real).
        String src = """
                expr many { one_or_more { "a" } }

                main() {
                    println(many.matches("aaa"))
                    println(many.matches("baa"))
                }
                """;
        Path file = tmp.resolve("q2").resolve("Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        Path out = tmp.resolve("q2-out");
        CompilationResult r = new CompilerDriver().compile(file, out, Target.JVM);
        assertTrue(r.success(), r.diagnostics().getDiagnostics().toString());
        PrintStream oldOut = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try (URLClassLoader cl = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
            Class.forName("Default.Main", true, cl).getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(oldOut);
        }
        assertEquals("true\nfalse\n", buf.toString(StandardCharsets.UTF_8));
    }

    @Test
    void classAtomRefusesNamingSlice3() throws Exception {
        String d = diagnostics("""
                expr wordy { letter }
                main() { println(wordy.matches("a")) }
                """);
        assertTrue(d.contains("EXPR001") && d.contains("slice 3"), () -> d);
    }

    @Test
    void alternationRefusesNamingSlice4() throws Exception {
        String d = diagnostics("""
                expr either2 { either { "a" or "b" } }
                main() { println(either2.matches("a")) }
                """);
        assertTrue(d.contains("EXPR001") && d.contains("slice 4"), () -> d);
    }

    @Test
    void captureRefusesNamingSlice5() throws Exception {
        String d = diagnostics("""
                expr dated { capture("v") { "a" } }
                main() { println(dated.matches("a")) }
                """);
        assertTrue(d.contains("EXPR001") && d.contains("slice 5"), () -> d);
    }

    @Test
    void referenceRefusesWithExpr001() throws Exception {
        String d = diagnostics("""
                expr outer { inner "." inner }
                expr inner { "x" }
                main() { println(outer.matches("x.x")) }
                """);
        assertTrue(d.contains("EXPR001"), () -> d);
    }

    private String runJvm(String tag, String src) throws Exception {
        Path file = tmp.resolve(tag).resolve("Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        Path out = tmp.resolve(tag + "-out");
        CompilationResult r = new CompilerDriver().compile(file, out, Target.JVM);
        assertTrue(r.success(), tag + ": " + r.diagnostics().getDiagnostics());
        PrintStream old = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try (URLClassLoader cl = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
            Class.forName("Default.Main", true, cl).getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(old);
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    private String runScript(String src) throws Exception {
        Path file = tmp.resolve("script").resolve("Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        KofInterpreter.Result r = new CompilerDriver().interpret(List.of(file), file.getParent(), new String[0]);
        assertEquals(0, r.exitCode(), r.stdout());
        return r.stdout();
    }

    private String diagnostics(String src) throws Exception {
        Path file = tmp.resolve("diag").resolve("Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        CompilationResult r = new CompilerDriver().compile(file, tmp.resolve("diag-out"), Target.JVM);
        assertFalse(r.success(), "an out-of-slice pattern element must refuse: " + src);
        return r.diagnostics().getDiagnostics().toString();
    }
}
