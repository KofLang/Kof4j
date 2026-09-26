package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * #628 — residual of §32: a record declared INSIDE a package and used as a
 * type argument of a returned {@code List<Rotulo>} was emitted with the
 * package-less class constant ({@code ldc // class Rotulo}), so the JVM
 * aborted with {@code NoClassDefFoundError: Rotulo} at class load (the
 * JavaFX-message trap hid the real cause). §32 fixed the receiver/checkcast
 * points ({@code resolveType}); this pins the generic-argument face.
 */
class PackageRecordGenericListE2ETest {

    private static final String ROTULO = """
            package dominio

            record Rotulo(String campo, String texto)

            rotulos(): List<Rotulo> {
                return listOf(Rotulo("cnpj", "NUMERO DE INSCRICAO"), Rotulo("uf", "UF"))
            }
            """;

    private static final String MAIN = """
            import dominio.Rotulo

            procurar(nome: String): String {
                var lista = rotulos()
                var i = 0
                while (i < lista.size) {
                    if (lista.get(i).campo() == nome) {
                        return lista.get(i).texto()
                    }
                    i = i + 1
                }
                return ""
            }

            main() {
                println("indexado: " + procurar("uf"))
                for (var r in rotulos()) {
                    println("  " + r.campo())
                }
            }
            """;

    private final CompilerDriver driver = new CompilerDriver();

    private List<Path> sources(Path tmp) throws IOException {
        Path pkg = tmp.resolve("dominio");
        Files.createDirectories(pkg);
        Files.writeString(pkg.resolve("Rotulo.kf"), ROTULO);
        Files.writeString(tmp.resolve("Main.kf"), MAIN);
        return List.of(tmp.resolve("Main.kf"), pkg.resolve("Rotulo.kf"));
    }

    @Test
    void recordFromPackageInGenericListRunsOnJvm(@TempDir Path tmp) throws Exception {
        CompilationResult r = driver.compileSources(sources(tmp), tmp.resolve("classes"),
                Target.JVM, tmp);
        assertTrue(r.success(), "JVM build: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-cp", r.outputDir().toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assertTrue(p.waitFor(60, java.util.concurrent.TimeUnit.SECONDS), "JVM run timeout");
        assertEquals(0, p.exitValue(), "JVM exit (#628): " + out);
        assertEquals("indexado: UF" + System.lineSeparator()
                + "  cnpj" + System.lineSeparator()
                + "  uf" + System.lineSeparator(), out, "JVM golden");
    }

    @Test
    void recordFromPackageInGenericListRunsOnScript(@TempDir Path tmp) throws IOException {
        KofInterpreter.Result ir = driver.interpret(sources(tmp), tmp, new String[0]);
        assertEquals(0, ir.exitCode(), "script exit: " + ir.stdout() + " " + ir.stderr());
        assertEquals("indexado: UF\n  cnpj\n  uf\n", ir.stdout(), "script golden");
    }

    @Test
    void recordFromPackageInGenericListRunsOnJs(@TempDir Path tmp)
            throws IOException, InterruptedException {
        String node;
        try {
            node = TestJdk.onPath("node");
        } catch (IOException e) {
            assumeTrue(false, "node missing on PATH (environment)");
            return;
        }
        CompilationResult js = driver.compileSources(sources(tmp), tmp.resolve("out-js"),
                Target.JS, tmp);
        assertTrue(js.success(), "JS compile: " + js.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(node, tmp.resolve("out-js/Default.mjs").toString())
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
        assertEquals(0, p.waitFor(), "JS exit: " + out);
        assertEquals("indexado: UF\n  cnpj\n  uf\n", out, "JS golden");
    }

    @Test
    void recordFromPackageInGenericListRunsOnNative(@TempDir Path tmp)
            throws IOException, InterruptedException {
        CompilationResult nat = driver.compileSources(sources(tmp), tmp.resolve("out-nat"),
                Target.NATIVE, tmp);
        assertTrue(nat.success(), "Native compile: " + nat.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(tmp.resolve("out-nat/Default/Main").toString())
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
        assertEquals(0, p.waitFor(), "Native exit: " + out);
        assertEquals("indexado: UF\n  cnpj\n  uf\n", out, "Native golden");
    }
}
