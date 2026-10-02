package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Connector ecosystem — plan §9.16 slice A (`D-CONNECTORS`): a gramática
 * {@code foreign module <name> { library "..." ; extern ... }}. O bloco é
 * açúcar sobre a via FFI EXISTENTE (regra 54, nenhum motor novo): o parser o
 * desdobra em {@link ExternalFunctionNode} que herdam a {@code library} do
 * cabeçalho, e o binding/ABI segue exatamente {@code CompilerFfiBinding}.
 *
 * <p>A prova RED-first chama símbolos reais via libm no JVM, o caminho que o
 * plano nomeia ("declaring and calling a symbol through the existing FFI
 * path"), e fixa os diagnósticos honestos (R6) do que o bloco recusa.
 */
class ForeignModuleGrammarE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void foreignModuleCallsThroughExistingFfiOnJvm(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, """
                foreign module libm {
                    library "libm.so.6"
                    abi c
                    ownership borrowed
                    extern fmod(Double a, Double b): Double
                    extern sqrt(Double x): Double
                    extern pow(Double x, Double y): Double
                }

                main() {
                    println("fmod=" + fmod(10.0, 3.0))
                    println("sqrt=" + sqrt(144.0))
                    println("pow=" + pow(2.0, 10.0))
                }
                """);

        Path out = dir.resolve("out");
        CompilationResult result = driver.compile(src, out, Target.JVM);
        assertTrue(result.success(), "foreign module must bind on the existing FFI path: "
                + result.diagnostics().getDiagnostics());
        assertEquals("fmod=1.0\nsqrt=12.0\npow=1024.0", runJvm(out));
    }

    @Test
    void foreignModuleExternKeepsItsOwmLibrary(@TempDir Path dir) throws IOException {
        // Retrocompat: um `extern "..."` dentro do bloco com library própria
        // mantém a sua, não a do módulo — o bloco só preenche o que falta.
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, """
                foreign module mixed {
                    library "libm.so.6"
                    abi c
                    ownership borrowed
                    extern sqrt(Double x): Double
                    extern "libc.so.6" abs(Int x): Int
                }

                main() {
                    println("sqrt=" + sqrt(9.0))
                    println("abs=" + abs(-5))
                }
                """);

        Path out = dir.resolve("out");
        CompilationResult result = driver.compile(src, out, Target.JVM);
        assertTrue(result.success(), "per-extern library must override the module header: "
                + result.diagnostics().getDiagnostics());
        assertEquals("sqrt=3.0\nabs=5", runJvm(out));
    }

    @Test
    void foreignModuleWithoutLibraryIsADiagnostic(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, """
                foreign module nobib {
                    extern sqrt(Double x): Double
                }

                main() {
                    println("hi")
                }
                """);

        CompilationResult result = driver.compile(src, dir.resolve("out"), Target.JVM);
        assertFalse(result.success(), "a module without `library` must not compile silently (R6)");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("PARSE097"), "expected PARSE097, got: " + diags);
    }

    @Test
    void foreignModuleUnknownOwnershipIsADiagnostic(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, """
                foreign module libm {
                    library "libm.so.6"
                    ownership widget
                    extern sqrt(Double x): Double
                }

                main() {
                    println("hi")
                }
                """);

        CompilationResult result = driver.compile(src, dir.resolve("out"), Target.JVM);
        assertFalse(result.success(), "unknown ownership must be diagnosed, never accepted (R6)");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("PARSE099"), "expected PARSE099, got: " + diags);
    }

    @Test
    void foreignAndModuleStayUsableAsIdentifiers(@TempDir Path dir) throws IOException {
        // `foreign`/`module` são keywords CONTEXTUAIS (como `sealed`): fora do
        // cabeçalho `foreign module <name> {`, seguem identificadores comuns.
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, """
                Int foreign(Int x) {
                    return x + 1
                }

                main() {
                    var module = foreign(41)
                    println(module)
                }
                """);

        Path out = dir.resolve("out");
        CompilationResult result = driver.compile(src, out, Target.JVM);
        assertTrue(result.success(), "`foreign`/`module` must remain identifiers (rule 2): "
                + result.diagnostics().getDiagnostics());
        assertEquals("42", runJvm(out));
    }

    private String runJvm(Path outDir) throws IOException {
        try {
            String javaHome = System.getProperty("java.home");
            ProcessBuilder pb = new ProcessBuilder(
                    Path.of(javaHome, "bin", "java").toString(),
                    "--enable-native-access=ALL-UNNAMED",
                    "-cp", outDir.toString(),
                    "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "JVM exit code, output: " + output);
            return output;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }
}
