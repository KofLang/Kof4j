package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #708 — duas raízes de fontes Kof (app em {@code src/main/kof}, testes em
 * {@code src/test/kof}) declaradas em {@code kof.toml [sources]}. O build
 * compila a árvore recursivamente e o teste importa a implementação real sem
 * cópia nem arquivo de entrada gerado (o workaround do SiFuture #2).
 *
 * <p>Interface decidida pela mantenedora (regra 6): D1 = {@code kof.toml
 * [sources]}; D2 = recursivo só na source root declarada; D3 = reusar
 * {@code dependencySourceRoots}; D4 = todos os alvos de teste reais
 * (jvm/native/js).</p>
 */
class TwoRootsCliE2ETest {

    private record Cli(int exit, String out) {}

    private static Cli cli(Path workDir, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("dev.kof.cli.Main");
        cmd.addAll(List.of(args));
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(workDir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(300, TimeUnit.SECONDS), "o CLI nao pode hangar\n" + out);
        return new Cli(p.exitValue(), out);
    }

    private static void writeProject(Path dir) throws Exception {
        Files.writeString(dir.resolve("kof.toml"), """
                [project]
                name = "tworoots"

                [sources]
                app = "src/main/kof"
                test = "src/test/kof"
                """);
        Path app = dir.resolve("src/main/kof/exemplo");
        Files.createDirectories(app);
        Files.writeString(app.resolve("Calculo.kf"), """
                package exemplo
                Int somar(Int a, Int b) { return a + b }
                """);
        // Entrada do app num subpacote distinto, importando o outro — prova
        // que o build recursivo resolve a árvore inteira como um módulo.
        Files.writeString(dir.resolve("src/main/kof/Main.kf"), """
                import exemplo.Calculo
                main() { println(somar(2, 3)) }
                """);
        Path tests = dir.resolve("src/test/kof/exemplo");
        Files.createDirectories(tests);
        Files.writeString(tests.resolve("CalculoTest.kf"), """
                package exemplo
                import exemplo.Calculo
                test "soma" {
                    assert(somar(2, 3) == 5)
                }
                """);
    }

    @Test
    void buildFromDeclaredSourceRootProducesArtifact(@TempDir Path dir) throws Exception {
        writeProject(dir);
        // Sem posicional: a raiz vem de [sources] app, a árvore é recursiva.
        Cli b = cli(dir, "build", "--target", "jvm", "--output", "out");
        assertEquals(0, b.exit(), "build do app declarado deve exit 0:\n" + b.out());
        assertTrue(Files.exists(dir.resolve("out/Default/Main.class")),
                "o artefato do app (Main + import exemplo.Calculo) deve existir:\n" + b.out());
    }

    @Test
    void testFromDeclaredTestRootImportsTheAppWithoutCopies(@TempDir Path dir) throws Exception {
        writeProject(dir);
        // `kof test` sem posicional: tests = [sources] test, app = [sources] app
        // como source path. Compila o exemplo real e roda.
        for (String target : List.of("jvm", "native", "js")) {
            Cli t = cli(dir, "test", "--target", target);
            assertEquals(0, t.exit(), "test " + target + " das duas raízes deve passar:\n" + t.out());
            assertTrue(t.out().contains("1 passed, 0 failed"),
                    "resumo esperado em " + target + ":\n" + t.out());
        }
    }

    @Test
    void buildWithoutSourcesOrDeclaredRootFailsExplicitly(@TempDir Path dir) throws Exception {
        // R6: sem posicional e sem [sources] — nunca compilar "nada" em silêncio.
        Cli b = cli(dir, "build");
        assertEquals(1, b.exit(), "build sem raiz deve exit 1:\n" + b.out());
        assertTrue(b.out().contains("no source root given"), b.out());

        Cli t = cli(dir, "test");
        assertEquals(1, t.exit(), "test sem raiz deve exit 1:\n" + t.out());
        assertTrue(t.out().contains("no test root given"), t.out());
    }
}
