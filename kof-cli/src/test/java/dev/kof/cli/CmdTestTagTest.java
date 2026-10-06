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
 * X8 fatia 3 (26/09, {@code D-COMPLETE-FIRST}): {@code kof test --tag <t>}
 * filtra o catálogo em COMPILE-TIME — o MESMO harness vale para os 4 alvos.
 * Ouro do CLI real (subprocesso `dev.kof.cli.Main`), padrão da casa
 * ({@code CmdTestTimeoutTest}); sem flag = contrato histórico intacto (rule 2).
 */
class CmdTestTagTest {

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

    private static final String SUITE = """
            test "soma", "smoke" {
                assert(2 + 2 == 4)
            }
            test "string" {
                assert("kof" == "kof")
            }
            """;

    @Test
    void tagFilterKeepsOnlyMatchingTests(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Main.kf"), SUITE);
        Cli r = cli(dir, "test", src.toString(), "--tag", "smoke");
        assertEquals(0, r.exit(), "filtro deve passar:\n" + r.out());
        assertTrue(r.out().contains("kof test: tag 'smoke' (1 of 2)"),
                "header do filtro (ouro medido):\n" + r.out());
        assertTrue(r.out().contains("PASS soma"), "teste com a tag roda:\n" + r.out());
        assertFalse(r.out().contains("PASS string"), "teste sem a tag NAO roda:\n" + r.out());
    }

    @Test
    void unknownTagRefusesHonestlyWithExitZero(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Main.kf"), SUITE);
        Cli r = cli(dir, "test", src.toString(), "--tag=ui");
        assertEquals(0, r.exit(), "nada falhou — nada rodou (honesto):\n" + r.out());
        assertTrue(r.out().contains("no tests with tag 'ui' (of 2)"),
                "R6: recusa nomeada, nunca silencio:\n" + r.out());
    }

    @Test
    void commaSeparatedTagsKeepTheUnionOfMatchingTests(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Main.kf"),
                "test \"soma\", \"smoke\" {\n    assert(2 + 2 == 4)\n}\n"
                + "test \"janela\", \"ui\" {\n    assert(\"kof\" == \"kof\")\n}\n"
                + "test \"banco\", \"db\" {\n    assert(true)\n}\n");
        Cli r = cli(dir, "test", src.toString(), "--tag", "smoke,ui");
        assertEquals(0, r.exit(), "multi-tag deve passar:\n" + r.out());
        assertTrue(r.out().contains("kof test: tag 'smoke,ui' (2 of 3)"),
                "§7.1: lista separada por vírgula casa por OR:\n" + r.out());
        assertTrue(r.out().contains("PASS soma"), "smoke roda:\n" + r.out());
        assertTrue(r.out().contains("PASS janela"), "ui roda:\n" + r.out());
        assertFalse(r.out().contains("PASS banco"), "db NÃO roda:\n" + r.out());
    }

    @Test
    void emptyTagValueIsRefusedAtTheFlag(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Main.kf"), SUITE);
        Cli r = cli(dir, "test", src.toString(), "--tag", "");
        assertEquals(1, r.exit(), "--tag vazio deve falhar alto:\n" + r.out());
        assertTrue(r.out().contains("non-empty"), "mensagem nomeia a causa:\n" + r.out());
    }

    @Test
    void noFlagKeepsLegacyContractByteIdentical(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Main.kf"), SUITE);
        Cli r = cli(dir, "test", src.toString());
        assertEquals(0, r.exit(), "suite completa:\n" + r.out());
        assertFalse(r.out().contains("kof test: tag"),
                "sem filtro = nenhuma linha de header (rule 2):\n" + r.out());
        assertTrue(r.out().contains("0 failed of 2 tests"),
                "contrato historico intacto:\n" + r.out());
    }

    /**
     * Medido 03/10 (tip {@code 5262b745c}): um arquivo que casa ZERO testes com
     * o filtro era contado como {@code PASS}/{@code passed} — {@code kof test
     * <dir> --tag smoke} com 1 arquivo casando e 1 sem a tag imprimia
     * {@code 2 passed, 0 failed} e {@code suite b: 1 passed, 0 failed}. Falso
     * verde: a suite que nao rodou nenhum teste nao e uma suite verde. Agora o
     * arquivo sem match e um {@code SKIP} nomeado (nao conta como passed), mas
     * o exit continua 0 quando NADA falhou (contrato historico de
     * {@link #unknownTagRefusesHonestlyWithExitZero} preservado).
     */
    @Test
    void tagFilterDoesNotCountAZeroMatchFileAsPassed(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("src");
        Files.createDirectories(src.resolve("a"));
        Files.createDirectories(src.resolve("b"));
        Files.writeString(src.resolve("a").resolve("A.kf"),
                "test \"smokeA\", \"smoke\" {\n    assert(2 + 2 == 4)\n}\n");
        Files.writeString(src.resolve("b").resolve("B.kf"),
                "test \"unitB\", \"unit\" {\n    assert(\"kof\" == \"kof\")\n}\n");
        Cli r = cli(dir, "test", src.toString(), "--tag", "smoke");
        assertEquals(0, r.exit(), "um casou, o outro nao — nada falhou:\n" + r.out());
        assertTrue(r.out().contains("PASS smokeA"), r.out());
        assertFalse(r.out().contains("PASS unitB"), "teste sem a tag NAO roda:\n" + r.out());
        assertTrue(r.out().contains("SKIP " + src.resolve("b").resolve("B.kf")
                + " (no tests with tag 'smoke')"),
                "arquivo sem teste com a tag e SKIP nomeado, nunca PASS:\n" + r.out());
        assertTrue(r.out().contains("1 passed, 0 failed, 1 skipped"),
                "so a suite que rodou conta passed; a vazia e skipped:\n" + r.out());
        assertFalse(r.out().contains("suite b: 1 passed"),
                "a suite sem match nao pode ser contada como verde:\n" + r.out());
    }

    @Test
    void tagFilterWithNoMatchAnywhereIsAnHonestNoOp(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Main.kf"), SUITE);
        Cli r = cli(dir, "test", src.toString(), "--tag", "ui");
        assertEquals(0, r.exit(), "filtro sem match = no-op honesto (nada falhou):\n" + r.out());
        assertTrue(r.out().contains("no tests with tag 'ui' (of 2)"),
                "o harness nomeia o filtro (rule 6):\n" + r.out());
        assertTrue(r.out().contains("0 passed, 0 failed, 1 skipped"),
                "nada rodou: nao pode aparecer como passed:\n" + r.out());
    }
}
