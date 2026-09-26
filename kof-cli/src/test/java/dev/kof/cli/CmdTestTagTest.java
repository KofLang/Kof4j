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
}
