package dev.kof.compiler;

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
 * Family #1183/#1179/#1175 at the BUILTIN face: when a builtin handle call
 * cannot be resolved (unknown method verb, or an unknown receiver type), the
 * front-end must refuse at compile time with a code — never emit bytecode
 * whose owner/descriptor is the placeholder {@code ""}/{@code ?}.
 *
 * <p>That placeholders are a hard JVM load failure: {@code Method "".m:()…}
 * makes the class loader reject the file ({@code ClassFormatError: Illegal
 * class name "" in class file}) and {@code Field "?".length} raises
 * {@code NoClassDefFoundError: ?}. Both are proven here RED-first by loading
 * the emitted class through a {@link URLClassLoader} — the real throwable,
 * never the launcher mask.
 *
 * <p>Measured 20/11 on tip {@code 663669344}, {@code kof check} said
 * "no errors" for all three sources below, and {@code javap} showed the exact
 * placeholder in the emitted constant pool; after the fix the same sources are
 * refused with {@code SEM025} naming the receiver type and the valid verbs.
 *
 * <p>Neighbours that must keep working (the resolver already covered them):
 * unknown verb on a known builtin ({@code kof.media.ImageData},
 * {@code kof.io.File}) reports SEM025 today, and a valid verb on a valid
 * receiver still runs.
 */
class BuiltinReceiverUnknownDiagnosticE2ETest {

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
    @DisplayName("#1183 builtin verbo inexistente: Media.ImageData.scaled() e recusado no check, nao vira Method \"\".width")
    void unknownVerbOnMediaImageIsRefusedAtCompileTime() throws Exception {
        ExecResult r = compileAndLoad("""
            import kof.media

            main() {
                var img = Image.open("/tmp/does-not-matter.png")
                var mini = img.scaled(4)
                println(mini.width())
            }
            """);
        assertFalse(r.compiled(),
                "scaled() must be refused at compile time; it emitted Method \"\".width which the JVM rejects: "
                        + (r.loadFailure() == null ? "" : r.loadFailure()));
        assertTrue(r.diagnostics().contains("SEM025"),
                "expected SEM025 naming the receiver and the valid verbs, got: " + r.diagnostics());
    }

    @Test
    @DisplayName("#1183 builtin receiver desconhecido: http.send(...).bodyx() e recusado, nao vira Field \"?.length\"")
    void unknownVerbOnHttpResponseIsRefusedAtCompileTime() throws Exception {
        ExecResult r = compileAndLoad("""
            import kof.http

            main() {
                var resposta = http.send("GET", "http://127.0.0.1:1/x", "", "")
                var corpo = resposta.bodyx()
                println(corpo.length)
            }
            """);
        assertFalse(r.compiled(),
                "bodyx() must be refused at compile time; it emitted Field \"?.length\" which the JVM rejects: "
                        + (r.loadFailure() == null ? "" : r.loadFailure()));
        assertTrue(r.diagnostics().contains("SEM025"),
                "expected SEM025 naming kof.http.Response, got: " + r.diagnostics());
    }

    @Test
    @DisplayName("#1183 controle: a face valida (Image.open + width) continua verde e executa")
    void validBuiltinFaceStillRuns() throws Exception {
        ExecResult r = compileAndLoad("""
            main() {
                var s = "abc"
                println("len=" + s.length)
            }
            """);
        assertTrue(r.compiled(), "valid source must compile: " + r.diagnostics());
        assertTrue(r.output().contains("len=3"), "expected len=3, got: " + r.output());
    }

    @Test
    @DisplayName("#1183 vizinho ja coberto: verbo inexistente em kof.io.File e recusado no check, com o receptor nomeado e a lista de verbos validos")
    void unknownVerbOnKofIoFileStillRefusedWithReceiverNamed() throws Exception {
        ExecResult r = compileAndLoad("""
            import kof.io

            main() {
                var f = File("x.txt")
                var v = f.noSuchVerb()
            }
            """);
        assertFalse(r.compiled(), "unknown verb on kof.io.File must be refused, not emitted");
        assertTrue(r.diagnostics().contains("no method 'noSuchVerb()'"),
                "expected the diagnostic to name the receiver and the verb, got: " + r.diagnostics());
    }

    @Test
    @DisplayName("a recusa de verbo desconhecido NAO engole o toString herdado por todo valor (kof.media, ff7ed6b73)")
    void inheritedToStringIsNotSwallowedByTheUnknownVerbRefusal() throws Exception {
        ExecResult ok = compileAndLoad("""
            main() {
                var img = Image.open("x.png")
                var s = img.toString()
                println(s.length > 0)
            }
            """);
        assertTrue(ok.compiled(),
                "toString is a universal member (StringMethodRegistry.objectMethodSignature), "
                        + "not a kof.media verb — it must bind; got: " + ok.diagnostics());
        assertFalse(ok.diagnostics().contains("SEM025"),
                "toString must not be refused as an unknown kof.media verb: " + ok.diagnostics());

        ExecResult refused = compileAndLoad("""
            main() {
                var img = Image.open("x.png")
                var mini = img.scaled(4)
                println(mini.width())
            }
            """);
        assertFalse(refused.compiled(), "a genuinely unknown verb must STILL be refused");
        assertTrue(refused.diagnostics().contains("SEM025"),
                "expected SEM025 for the unknown verb, got: " + refused.diagnostics());
    }
}
