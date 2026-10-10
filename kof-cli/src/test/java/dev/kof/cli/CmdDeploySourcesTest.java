package dev.kof.cli;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static dev.kof.cli.DepsRegistrySupport.CliResult;
import static dev.kof.cli.DepsRegistrySupport.envOf;
import static dev.kof.cli.DepsRegistrySupport.runWithEnv;
import static dev.kof.cli.DepsRegistrySupport.serveFakeRegistry;
import static org.junit.jupiter.api.Assertions.*;

/**
 * #566, opção (b) (DECISIONS.md, D-RELEASE-0.5.0-GATE addendum), lado PRODUTOR: a release do
 * {@code kof deploy} carrega as FONTES ({@code src/<caminho>.kf}, cobertas pelo SHA256SUMS) e um
 * módulo sem fontes no topo (só árvore de pacotes) é uma BIBLIOTECA — publica só as fontes.
 * O último teste fecha o ciclo: deploy → registry (shape real do GitHub) → `kof run --deps`.
 */
class CmdDeploySourcesTest {

    private static final String GREETER = """
        package regsmoke

        class Greeter {
            String greet(String who) { return "hello, " + who }
        }
        """;

    private static Path write(Path file, String content) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }

    private static CliResult deploy(Path dir, String... extra) throws Exception {
        List<String> args = new ArrayList<>(List.of("deploy", "src", "--target", "jvm",
                "--output", "dist", "--name", "hello", "--version", "1.2.3"));
        args.addAll(List.of(extra));
        return runWithEnv(dir, Map.of("HOMEOF", dir.resolve("home").toString()),
                args.toArray(new String[0]));
    }

    /** Nomes dos entries do tar.gz, na ordem. */
    private static List<String> tarNames(Path tgz) throws Exception {
        List<String> names = new ArrayList<>();
        try (InputStream in = new GZIPInputStream(Files.newInputStream(tgz))) {
            byte[] h = new byte[512];
            while (in.readNBytes(h, 0, 512) == 512) {
                int end = 0;
                while (end < 100 && h[end] != 0) end++;
                if (end == 0) break;
                names.add(new String(h, 0, end, StandardCharsets.UTF_8));
                long size = Long.parseLong(new String(h, 124, 11, StandardCharsets.US_ASCII).trim(), 8);
                in.skipNBytes(((size + 511) / 512) * 512);
            }
        }
        return names;
    }

    @Test
    void applicationReleaseCarriesItsSourcesCoveredByTheSums(@TempDir Path tmp) throws Exception {
        write(tmp.resolve("src/Main.kf"), """
            import regsmoke.Greeter
            main() { println(Greeter().greet("app")) }
            """);
        write(tmp.resolve("src/regsmoke/Greeter.kf"), GREETER);
        write(tmp.resolve("src/tests/smoke.kf"), "main() { println(\"test\") }\n");
        write(tmp.resolve("src/.hidden/Secret.kf"), "class Secret { }\n");
        write(tmp.resolve("src/notes.txt"), "not a source\n");
        CliResult r = deploy(tmp);
        assertEquals(0, r.exit(), "deploy:\n" + r.out());
        Path rel = tmp.resolve("dist/deploy/hello-1.2.3");
        List<String> names = tarNames(tmp.resolve("dist/deploy/hello-1.2.3.tar.gz"));
        assertEquals("hello-1.2.3.jar", names.get(0), "artefato da face continua o 1o entry");
        assertTrue(names.contains("src/Main.kf") && names.contains("src/regsmoke/Greeter.kf"),
                "as fontes viajam na release: " + names);
        assertTrue(names.stream().noneMatch(n -> n.contains("tests") || n.contains(".hidden")
                        || n.endsWith(".txt")),
                "tests/, ocultos e nao-fonte NAO sao publicados: " + names);
        assertEquals("RELEASE.md", names.get(names.size() - 2));
        assertEquals("SHA256SUMS", names.get(names.size() - 1));
        String sums = Files.readString(rel.resolve("SHA256SUMS"));
        for (String src : List.of("src/Main.kf", "src/regsmoke/Greeter.kf")) {
            assertTrue(sums.contains(CmdDeploy.sha256Hex(rel.resolve(src)) + "  " + src),
                    "SHA256SUMS cobre " + src + ":\n" + sums);
        }
        assertTrue(Files.readString(rel.resolve("RELEASE.md")).contains("sources"),
                "RELEASE.md registra as fontes");
    }

    @Test
    void libraryWithoutTopLevelSourcesPublishesOnlyItsSources(@TempDir Path tmp) throws Exception {
        write(tmp.resolve("src/regsmoke/Greeter.kf"), GREETER);
        CliResult r = deploy(tmp);
        assertEquals(0, r.exit(), "biblioteca (so arvore de pacotes) faz deploy:\n" + r.out());
        List<String> names = tarNames(tmp.resolve("dist/deploy/hello-1.2.3.tar.gz"));
        assertEquals(List.of("src/regsmoke/Greeter.kf", "RELEASE.md", "SHA256SUMS"), names,
                "biblioteca: so fontes + metadados, sem jar");
        Path rel = tmp.resolve("dist/deploy/hello-1.2.3");
        assertFalse(Files.exists(rel.resolve("hello-1.2.3.jar")), "sem artefato executavel");
        assertTrue(Files.readString(rel.resolve("RELEASE.md")).contains("library"),
                "RELEASE.md diz que e biblioteca");
        assertTrue(Files.readString(rel.resolve("SHA256SUMS")).contains("  src/regsmoke/Greeter.kf"));
    }

    @Test
    void libraryWithABrokenSourceFailsAndPublishesNothing(@TempDir Path tmp) throws Exception {
        write(tmp.resolve("src/regsmoke/Greeter.kf"), """
            package regsmoke

            class Greeter {
                String greet(String who) { return naoExiste }
            }
            """);
        CliResult r = deploy(tmp);
        assertNotEquals(0, r.exit(), "fonte invalida nao vira release:\n" + r.out());
        assertTrue(r.out().contains("SEM011") && r.out().contains("compilation failed"),
                "falha PELO diagnostico do compilador (a biblioteca e validada):\n" + r.out());
        assertFalse(Files.exists(tmp.resolve("dist/deploy/hello-1.2.3.tar.gz")),
                "nenhum tar.gz de uma biblioteca que nao compila");
    }

    @Test
    void moduleWithNoSourcesAtAllStillFailsHonestly(@TempDir Path tmp) throws Exception {
        Files.createDirectories(tmp.resolve("src"));
        write(tmp.resolve("src/readme.txt"), "nada\n");
        CliResult r = deploy(tmp);
        assertNotEquals(0, r.exit());
        assertTrue(r.out().contains("no .kf/.kof files found"), r.out());
    }

    @Test
    void fullCycleDeployRegistryAndConsumerRunsAgainstTheSources(@TempDir Path tmp) throws Exception {
        Path producer = tmp.resolve("producer");
        write(producer.resolve("src/regsmoke/Greeter.kf"), GREETER);
        assertEquals(0, deploy(producer).exit());
        byte[] tgz = Files.readAllBytes(producer.resolve("dist/deploy/hello-1.2.3.tar.gz"));
        HttpServer server = serveFakeRegistry(tmp, "hello", "1.2.3", tgz, true);
        try {
            Path proj = tmp.resolve("consumer");
            Files.createDirectories(proj);
            Map<String, String> env = envOf(server, tmp.resolve("home"));
            assertEquals(0, runWithEnv(proj, env, "deps", "add", "acme/hello@1.2.3").exit());
            CliResult res = runWithEnv(proj, env, "deps", "resolve");
            assertEquals(0, res.exit(), "resolve do pacote-biblioteca:\n" + res.out());
            write(proj.resolve("Main.kf"), """
                import regsmoke.Greeter
                main() { println(Greeter().greet("cycle")) }
                """);
            CliResult run = runWithEnv(proj, env, "run", "Main.kf", "--deps");
            assertEquals(0, run.exit(), "consumidor roda contra as fontes publicadas:\n" + run.out());
            assertTrue(run.out().contains("hello, cycle"), run.out());
        } finally {
            server.stop(0);
        }
    }
}
