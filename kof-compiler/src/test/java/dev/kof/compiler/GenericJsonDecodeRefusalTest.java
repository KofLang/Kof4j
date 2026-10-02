package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §538 — {@code json.decode<T>} with an OPEN type parameter must be refused at
 * compile time (JSN005): a type variable has no runtime type token, so the old
 * lowerer emitted {@code kof_json_decode_T} + {@code checkcast T} (invalid
 * bytecode masked as the JavaFX launcher message on the JVM). The concrete
 * form ({@code json.decode<Pt>}) must keep working.
 *
 * RED evidence: the bug's own 28/09 measurement (javap showed
 * {@code KofRuntime.kof_json_decode_T} + {@code checkcast T}, "compiles clean").
 */
class GenericJsonDecodeRefusalTest {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(Path tmp, String name, String src) throws IOException {
        Path file = tmp.resolve(name);
        Files.writeString(file, src);
        return driver.compile(file, tmp.resolve("out-" + name.replace(".kf", "")), Target.JVM);
    }

    private static boolean hasDiagnostic(CompilationResult r, String code) {
        return r.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> code.equals(d.code()));
    }

    @Test
    void openTypeParamInFunctionIsRefused(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "Fn.kf", """
                record Pt(Int x, Int y)

                T decodeIt<T>(String s) {
                    return json.decode<T>(s)
                }

                main() {
                    var p = decodeIt<Pt>("{\\"x\\":1,\\"y\\":2}")
                    println(p.x + p.y)
                }
                """);
        assertFalse(r.success(), "json.decode<T> (open type param) must be refused, not compile clean");
        assertTrue(hasDiagnostic(r, "JSN005"), r.diagnostics().getDiagnostics().toString());
    }

    @Test
    void openTypeParamInClassMethodIsRefused(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "Cls.kf", """
                record Pt(Int x, Int y)

                class Box<T> {
                    T decode(String s) {
                        return json.decode<T>(s)
                    }
                }

                main() {
                    val b = Box<Pt>()
                    val p = b.decode("{\\"x\\":1,\\"y\\":2}")
                    println(p.x)
                }
                """);
        assertFalse(r.success(), "json.decode<T> in a generic class method must be refused");
        assertTrue(hasDiagnostic(r, "JSN005"), r.diagnostics().getDiagnostics().toString());
    }

    @Test
    void openTypeParamInListIsRefused(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "Lst.kf", """
                T firstOf<T>(String s) {
                    val xs = json.decode<List<T>>(s)
                    return xs.get(0)
                }

                main() {
                    println(firstOf<Int>("[1,2]"))
                }
                """);
        assertFalse(r.success(), "json.decode<List<T>> (open type param) must be refused");
        assertTrue(hasDiagnostic(r, "JSN005"), r.diagnostics().getDiagnostics().toString());
    }

    @Test
    void concreteTypeStillCompilesAndRuns(@TempDir Path tmp) throws IOException {
        CompilationResult r = compile(tmp, "Ok.kf", """
                record Pt(Int x, Int y)

                main() {
                    val p = json.decode<Pt>("{\\"x\\":1,\\"y\\":2}")
                    println(p.x + p.y)
                }
                """);
        assertTrue(r.success(), "concrete json.decode<Pt> must keep compiling: "
                + r.diagnostics().getDiagnostics());
        Path out = tmp.resolve("out-Ok");
        String stdout = runJvm(out);
        assertEquals("3", stdout.trim(), stdout);
    }

    @Test
    void concreteGenericHelperStillCompiles(@TempDir Path tmp) throws IOException {
        // O type-param aberto é permitido enquanto NÃO for alvo de decode.
        CompilationResult r = compile(tmp, "Pass.kf", """
                record Pt(Int x, Int y)

                T id<T>(T v) {
                    return v
                }

                main() {
                    val p = json.decode<Pt>("{\\"x\\":4,\\"y\\":5}")
                    val q = id<Pt>(p)
                    println(q.x + q.y)
                }
                """);
        assertTrue(r.success(), "generic functions without decode must keep compiling: "
                + r.diagnostics().getDiagnostics());
    }

    private String runJvm(Path outDir) throws IOException {
        try {
            Process p = new ProcessBuilder(TestJdk.javaBin(), "-cp", outDir.toString(), "Default.Main")
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(p.waitFor(30, TimeUnit.SECONDS), "program timed out");
            assertEquals(0, p.exitValue(), out);
            return out;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }
}
