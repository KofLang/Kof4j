package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #633 — {@code json.decode} lost the type-directed conversion one level down:
 * a {@code Map<String, Map<String, E>>} kept the inner values as raw
 * {@code LinkedHashMap} (ClassCastException on first use), and a record field
 * declared {@code Map<String, E>} decoded as an EMPTY map — silently.
 *
 * <p>Root cause: the compile-time dispatch picked the value decoder from the
 * IMMEDIATE type argument only ({@code ExpressionJsonCallLowerer}), and the
 * runtime binder ({@code kof_json_bind}) had no {@code Map} descent. The JVM
 * now routes nested collection values through {@code kof_json_decode_typed}
 * (a generic-signature-driven recursive bind); the record-field face is fixed
 * by the {@code Map} descent in {@code kof_json_bind}. The interpreter
 * ({@code BindKof}) mirrors the record-field face, and it intercepts
 * {@code kof_json_decode_typed} by parsing the JVM generic signature back into
 * a Kof {@code Type} (the records are {@code KofObj} there, so the JVM helper
 * cannot be reused directly).
 */
class NestedMapRecordDecodeE2ETest {

    private static final String PROGRAM = """
            record E(String upright)
            record Holder(Map<String, E> m)

            main() {
                // §583 note: Map.get returns V? — the old source dereferenced it
                // without narrowing because the analyzer typed every decode as
                // UNKNOWN and skipped both SEM012 and SEM049. Now that decode<T>
                // is honestly T (SEM mirrors the emit), the source follows the
                // frozen null-safety law (D-NARROW-WHILE): narrow, then use.
                var ok = json.decode<Map<String, E>>("{\\"ace\\":{\\"upright\\":\\"u1\\"}}")
                var ace: E? = ok.get("ace")
                if (ace != null) {
                    println("A: " + ace.upright())
                }
                var nested = json.decode<Map<String, Map<String, E>>>("{\\"wands\\":{\\"ace\\":{\\"upright\\":\\"u1\\"}}}")
                var nw: Map<String, E>? = nested.get("wands")
                if (nw != null) {
                    var nace: E? = nw.get("ace")
                    if (nace != null) {
                        println("B: " + nace.upright())
                    }
                }
                var h = json.decode<Holder>("{\\"m\\":{\\"ace\\":{\\"upright\\":\\"u1\\"}}}")
                println("H: " + h.m().size)
                var hAce: E? = h.m().get("ace")
                if (hAce != null) {
                    println("H: " + hAce.upright())
                }
                var c = json.decode<Map<String, List<E>>>("{\\"wands\\":[{\\"upright\\":\\"a\\"},{\\"upright\\":\\"b\\"}]}")
                var cw: List<E>? = c.get("wands")
                if (cw != null) {
                    println("C: " + cw.size)
                    println("C: " + cw.get(1).upright())
                }
                var d = json.decode<Map<String, Map<String, E>>>("{\\"wands\\":{}}")
                var dw: Map<String, E>? = d.get("wands")
                if (dw != null) {
                    println("D: " + dw.isEmpty())
                }
                println("D: " + (d.get("nope") == null))
                var e = json.decode<Map<String, Map<String, E>>>("{}")
                println("E: " + e.isEmpty())
            }
            """;

    private static final String GOLDEN =
            "A: u1\nB: u1\nH: 1\nH: u1\nC: 2\nC: b\nD: true\nD: true\nE: true\n";

    private final CompilerDriver driver = new CompilerDriver();

    private List<Path> sources(Path tmp, String program) throws IOException {
        Files.writeString(tmp.resolve("Main.kf"), program);
        return List.of(tmp.resolve("Main.kf"));
    }

    @Test
    void nestedMapAndRecordFieldDecodeOnJvm(@TempDir Path tmp) throws Exception {
        CompilationResult r = driver.compileSources(sources(tmp, PROGRAM), tmp.resolve("classes"),
                Target.JVM, tmp);
        assertTrue(r.success(), "JVM build: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-cp", r.outputDir().toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "JVM run timeout");
        assertEquals(0, p.exitValue(), "JVM exit (#633): " + out);
        assertEquals(GOLDEN, out, "JVM golden");
    }

    @Test
    void nestedMapAndRecordFieldDecodeOnScript(@TempDir Path tmp) throws IOException {
        KofInterpreter.Result ir = driver.interpret(sources(tmp, PROGRAM), tmp, new String[0]);
        assertEquals(0, ir.exitCode(), "script exit: " + ir.stdout() + " " + ir.stderr());
        assertEquals(GOLDEN, ir.stdout(), "script golden");
    }
}
