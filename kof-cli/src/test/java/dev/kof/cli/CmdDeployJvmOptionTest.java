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
 * #855 (parte 2) — `kof deploy --jvm-option <opt>`: o COMANDO PUBLICADO carrega as
 * mesmas opções da JVM que o `kof run`.
 *
 * <p>Antes, o `RELEASE.md` saía sempre com `java -jar <app>.jar`: o teto de
 * memória só existia se o operador lembrasse de escrever `java -Xmx512m -jar` à
 * mão — e a issue pede explicitamente que a opção viaje com o artefato.
 */
class CmdDeployJvmOptionTest {

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

    private static void writeDeployApp(Path dir) throws Exception {
        Files.createDirectories(dir.resolve("src"));
        Files.writeString(dir.resolve("src/Main.kf"), """
                main() { println("ok") }
                """);
    }

    private static String releaseMd(Path dir) throws Exception {
        return Files.readString(dir.resolve("dist/deploy/app-1.0.0/RELEASE.md"), StandardCharsets.UTF_8);
    }

    @Test
    void publishedCommandCarriesTheJvmOption(@TempDir Path dir) throws Exception {
        writeDeployApp(dir);
        Cli c = cli(dir, "deploy", "src", "--target", "jvm", "--name", "app",
                "--version", "1.0.0", "--output", "dist", "--jvm-option", "-Xmx512m");
        assertEquals(0, c.exit(), "deploy com a opcao da JVM deve funcionar:\n" + c.out());
        String md = releaseMd(dir);
        assertTrue(md.contains("java -Xmx512m -jar"),
                "o comando PUBLICADO tem de carregar a opcao (antes: flag desconhecida):\n" + md);
    }

    @Test
    void withoutTheFlagTheCommandIsUnchanged(@TempDir Path dir) throws Exception {
        writeDeployApp(dir);
        Cli c = cli(dir, "deploy", "src", "--target", "jvm", "--name", "app",
                "--version", "1.0.0", "--output", "dist");
        assertEquals(0, c.exit(), "o caminho historico segue:\n" + c.out());
        String md = releaseMd(dir);
        assertTrue(md.contains("java -jar"), "sem a flag o comando e o de sempre:\n" + md);
        assertFalse(md.contains("-Xmx"), "nenhuma opcao inventada:\n" + md);
    }

    @Test
    void nonJvmTargetRefusesTheFlagHonestly(@TempDir Path dir) throws Exception {
        writeDeployApp(dir);
        Cli c = cli(dir, "deploy", "src", "--target", "js", "--name", "app",
                "--version", "1.0.0", "--output", "dist", "--jvm-option", "-Xmx512m");
        assertNotEquals(0, c.exit(), "sem JVM no alvo nao ha onde carregar a opcao:\n" + c.out());
        assertTrue(c.out().contains("--jvm-option only applies to --target jvm"),
                "a recusa e NOMEADA, nunca silenciosa:\n" + c.out());
    }
}
