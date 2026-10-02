package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regressao de paridade help x parser: a linha de usage de cada comando deve
 * anunciar exatamente os targets que o comando aceita.
 *
 * Drift historico (corrigido aqui):
 *  - `build`/`run` omitiam `native.risc|native.arm` — aceitos por
 *    {@link KofCliSupport#parseTarget(String)} e documentados em
 *    `docs/status.md:88-89`;
 *  - `bench`/`profile` anunciavam `android`, que `BenchDiscovery.parseTarget`
 *    e `Profile.parseTarget` rejeitam com `unknown target`;
 *  - `test` anunciava `jvm|native` e omitia `js` (`docs/status.md:566`).
 */
class CliUsageTargetsTest {

    private static String mainUsage() {
        PrintStream realOut = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        try {
            Main.main(new String[0]);
        } finally {
            System.setOut(realOut);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static String lineOf(String usage, String cmd) {
        for (String l : usage.split("\\R")) {
            if (l.trim().startsWith(cmd + " ")) return l.trim();
        }
        return fail("usage sem linha para `" + cmd + "`:\n" + usage);
    }

    @Test
    void buildAndRunAdvertiseNativeRiscAndArm() {
        String usage = mainUsage();
        for (String cmd : new String[] { "build", "run" }) {
            String line = lineOf(usage, cmd);
            assertTrue(line.contains("native.risc"),
                    cmd + " deve anunciar native.risc (aceito por parseTarget): " + line);
            assertTrue(line.contains("native.arm"),
                    cmd + " deve anunciar native.arm (aceito por parseTarget): " + line);
        }
    }

    @Test
    void benchAndProfileDoNotAdvertiseUnsupportedAndroid() {
        String usage = mainUsage();
        for (String cmd : new String[] { "bench", "profile" }) {
            String line = lineOf(usage, cmd);
            assertFalse(line.contains("android"),
                    cmd + " rejeita android (unknown target) — usage nao deve anuncia-lo: " + line);
        }
    }

    @Test
    void testAdvertisesJsAndNotAndroid() {
        String line = lineOf(mainUsage(), "test");
        assertTrue(line.contains("js"), "test aceita js (status.md:566) — usage: " + line);
        assertFalse(line.contains("android"), "test nao aceita android — usage: " + line);
    }

    /**
     * Regressao #715 (a face fatal do teste introduzido em 5aa776881): aquele
     * teste chamava {@code CmdBuild.run({"build"})} IN-PROCESS; apos #708 esse
     * caminho faz {@code System.exit(1)}, matando o JVM do surefire ("forked
     * VM terminated without properly saying goodbye") e derrubando o modulo
     * kof-cli antes de qualquer teste rodar. O CLI tem de ser exercitado em
     * SUBPROCESSO (como {@code TwoRootsCliE2ETest}) para o exit ser contido e
     * observavel, e a assercao do texto mudou (o caminho sem fonte agora diz
     * explicitamente o que falta, nao o usage generico).
     */
    @Test
    void buildWithoutSourceFailsExplicitlyInSubprocess(@TempDir Path dir) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("dev.kof.cli.Main");
        cmd.add("build");
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(dir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(300, TimeUnit.SECONDS), "o CLI nao pode hangar\n" + out);
        assertEquals(1, p.exitValue(), "`kof build` sem raiz deve exit 1 (nunca crashar):\n" + out);
        assertTrue(out.contains("no source root given"),
                "deve dizer o que falta (nao um AIOOBE/stacktrace):\n" + out);
    }

}
