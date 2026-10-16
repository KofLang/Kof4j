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
 * #855 — `kof run --jvm-option <opção>`: o teto/opção da JVM no caminho de
 * DESENVOLVIMENTO.
 *
 * <p>Antes, impor um teto de memória exigia empacotar com `kof deploy` e rodar
 * `java -Xmx512m -jar <app>.jar`; `kof run` não aceitava opção nenhuma da JVM.
 *
 * <p>A prova é direta e determinística: uma opção **válida** (`-Xmx64m`) tem de
 * ser ACEITA (a execução termina 0 e o programa imprime), e uma opção que a JVM
 * **não conhece** tem de fazer a JVM REJEITAR — a mensagem `Unrecognized VM
 * option` só aparece se a opção chegou à JVM. E onde não há JVM (binário nativo
 * ou execução in-process), a recusa é honesta em vez de silenciosa.
 */
class CmdRunJvmOptionTest {

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

    private static void writeApp(Path dir) throws Exception {
        Files.writeString(dir.resolve("Main.kf"), """
                main() { println("RODOU") }
                """);
    }

    @Test
    void validJvmOptionIsAcceptedAndTheProgramRuns(@TempDir Path dir) throws Exception {
        writeApp(dir);
        Cli c = cli(dir, "run", "Main.kf", "--jvm-option", "-Xmx64m");
        assertEquals(0, c.exit(), "--jvm-option valida nao pode quebrar a execucao:\n" + c.out());
        assertTrue(c.out().contains("RODOU"), "o programa roda:\n" + c.out());
    }

    @Test
    void theOptionReallyReachesTheJvm(@TempDir Path dir) throws Exception {
        writeApp(dir);
        Cli c = cli(dir, "run", "Main.kf", "--jvm-option", "-XX:BogusOptionForTest");
        assertNotEquals(0, c.exit(), "a JVM tem de REJEITAR a opcao desconhecida:\n" + c.out());
        assertTrue(c.out().contains("Unrecognized VM option"),
                "a mensagem so aparece se a opcao CHEGOU a JVM (antes: flag desconhecida no CLI):\n" + c.out());
    }

    @Test
    void optionEqualsFormWorksToo(@TempDir Path dir) throws Exception {
        writeApp(dir);
        Cli c = cli(dir, "run", "Main.kf", "--jvm-option=-Xmx64m");
        assertEquals(0, c.exit(), "a forma --jvm-option=X tambem vale:\n" + c.out());
        assertTrue(c.out().contains("RODOU"), c.out());
    }

    @Test
    void nativeTargetRefusesTheFlagHonestly(@TempDir Path dir) throws Exception {
        writeApp(dir);
        Cli c = cli(dir, "run", "Main.kf", "--target", "native", "--jvm-option", "-Xmx64m");
        assertNotEquals(0, c.exit(), "sem JVM nao ha como honrar a opcao:\n" + c.out());
        assertTrue(c.out().contains("--jvm-option only applies where a JVM runs"),
                "a recusa e NOMEADA, nunca silenciosa:\n" + c.out());
    }
}
