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
 * #856 — `kof test --module-root <raiz> <alvo>`: a raiz do MÓDULO deixa de ser
 * derivada do alvo.
 *
 * <p>Antes: em modo ARQUIVO a raiz de pacotes era o diretório do PRÓPRIO arquivo
 * ({@code testsRoot = src.getParent()}), então rodar um teste de um módulo falhava:
 * {@code kof test src/testes/CalcTest.kf} com o teste declarando {@code package
 * testes} fazia a raiz ser {@code src/testes} → o pacote mapearia para {@code
 * src/testes/testes} → {@code PKG004 package ... does not match the directory}.
 * Com {@code --module-root src} a raiz é o MÓDULO, o arquivo pode ser alvo único
 * e continuar dentro (ou fora) da árvore de fontes, e os imports do app resolvem.
 */
class CmdTestModuleRootTest {

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
        assertTrue(p.waitFor(180, TimeUnit.SECONDS), "o proprio CLI nao pode hangar\n" + out);
        return new Cli(p.exitValue(), out);
    }

    /** `src/area/` = o módulo; `src/testes/` = o teste DENTRO da árvore, noutro pacote. */
    private static void writeModuleFixture(Path dir) throws Exception {
        Files.createDirectories(dir.resolve("src/area"));
        Files.createDirectories(dir.resolve("src/testes"));
        Files.writeString(dir.resolve("src/area/Calc.kf"), """
                package area
                Int dobro(Int n) { return n * 2 }
                """);
        Files.writeString(dir.resolve("src/testes/CalcTest.kf"), """
                package testes
                import area.Calc
                test "dobro" {
                    assert(dobro(21) == 42)
                }
                """);
    }

    @Test
    void singleTestFileResolvesPackagesAgainstTheModuleRoot(@TempDir Path dir) throws Exception {
        writeModuleFixture(dir);
        Cli c = cli(dir, "test", "--module-root", "src", "src/testes/CalcTest.kf");
        assertEquals(0, c.exit(), "um arquivo SO de um modulo deve rodar com a raiz explicita:\n" + c.out());
        assertTrue(c.out().contains("PASS"), "o teste deve passar (nao PKG004):\n" + c.out());
    }

    /** #856(a): um modulo com `main` (programa) + testes — `--tests-only` NAO roda o programa. */
    private static void writeProgramAndTest(Path dir) throws Exception {
        Files.createDirectories(dir.resolve("src/area"));
        Files.createDirectories(dir.resolve("src/testes"));
        Files.writeString(dir.resolve("src/area/Calc.kf"), """
                package area
                Int dobro(Int n) { return n * 2 }
                """);
        Files.writeString(dir.resolve("src/Main.kf"), """
                main() { println("PROGRAMA RODOU") }
                """);
        Files.writeString(dir.resolve("src/testes/CalcTest.kf"), """
                package testes
                import area.Calc
                test "dobro" {
                    assert(dobro(21) == 42)
                }
                """);
    }

    @Test
    void testsOnlyDoesNotRunTheModuleProgram(@TempDir Path dir) throws Exception {
        writeProgramAndTest(dir);
        Cli c = cli(dir, "test", "--module-root", "src", "--tests-only", "src");
        assertEquals(0, c.exit(), "--tests-only nao pode falhar (0 passed, 0 failed nao e verde):\n" + c.out());
        assertTrue(c.out().contains("PASS"), "os testes do modulo rodam:\n" + c.out());
        assertFalse(c.out().contains("PROGRAMA RODOU"),
                "--tests-only NAO pode executar o `main` do modulo:\n" + c.out());
        assertTrue(c.out().contains("--tests-only"), "o SKIP nomeado aparece para o programa:\n" + c.out());
    }

    @Test
    void withoutTestsOnlyTheModuleProgramStillRuns(@TempDir Path dir) throws Exception {
        writeProgramAndTest(dir);
        Cli c = cli(dir, "test", "--module-root", "src", "src");
        assertEquals(0, c.exit(), "o caminho historico segue rodando:\n" + c.out());
        assertTrue(c.out().contains("PROGRAMA RODOU"),
                "sem a flag o `main` do modulo CONTINUA sendo executado (comportamento preservado):\n" + c.out());
    }

    @Test
    void withoutTheFlagTheSingleFileKeepsTheOldBehaviour(@TempDir Path dir) throws Exception {
        writeModuleFixture(dir);
        Cli c = cli(dir, "test", "src/testes/CalcTest.kf");
        // Comportamento histórico preservado: sem a flag a raiz é o diretório do
        // arquivo e o pacote `testes` não casa — o comando falha HONESTAMENTE.
        assertNotEquals(0, c.exit(), "sem --module-root o modo arquivo segue como era:\n" + c.out());
        assertTrue(c.out().contains("PKG004"), "o diagnóstico é o PKG004 histórico:\n" + c.out());
    }
}