package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GitHub #67 — a descoberta de arquivos-fonte aceita .kf E .kof (extensões
 * oficiais declaradas em editor/kof.tmLanguage.json: fileTypes [kf, kof]).
 * Antes só .kf: `kof build <dir>` respondia "no .kf files found" para um
 * diretório com .kof, enquanto run/check/test/fmt aceitavam.
 */
class KofSourceDiscoveryTest {

    @Test
    void collectAcceptsKofAndKf(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("p.kof"), "main() { println(\"ok\") }\n");
        Files.writeString(dir.resolve("q.kf"), "main() { println(\"ok\") }\n");
        Files.writeString(dir.resolve("notes.txt"), "não é fonte\n");
        List<Path> files = KofCliSupport.collect(dir);
        assertEquals(2, files.size(), "esperava p.kof + q.kf, got: " + files);
        assertTrue(files.stream().anyMatch(p -> p.toString().endsWith(".kof")));
        assertTrue(files.stream().anyMatch(p -> p.toString().endsWith(".kf")));
    }

    @Test
    void collectShallowAcceptsKof(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("p.kof"), "main() { println(\"ok\") }\n");
        List<Path> files = KofCliSupport.collectShallow(dir);
        assertEquals(1, files.size());
    }

    @Test
    void caseInsensitiveExtension(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("P.KOF"), "main() { println(\"ok\") }\n");
        List<Path> files = KofCliSupport.collect(dir);
        assertEquals(1, files.size());
    }

    private record Cli(int exit, String out) {}

    private static Cli cli(Path workDir, String... args) throws Exception {
        List<String> cmd = new java.util.ArrayList<>();
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

    @Test
    void buildWithNoSourcesFailsExplicitlyInsteadOfSilentNoOp(@TempDir Path dir) throws Exception {
        // #708 (R6): a directory whose only sources live deeper (e.g.
        // src/main/kof/exemplo/) has no file at the top level. Discovery is
        // non-recursive for build, so this used to print "no .kf/.kof files
        // found" and exit 0 — a silent no-op that looked like a green build.
        Files.createDirectories(dir.resolve("src/exemplo"));
        Files.writeString(dir.resolve("src/exemplo/Main.kf"), "main() { println(\"hi\") }\n");
        Cli r = cli(dir, "build", "src", "--target", "jvm", "--output", "out");
        assertEquals(1, r.exit(), "build sem fontes deve falhar (nao exit 0):\n" + r.out());
        assertTrue(r.out().contains("no .kf/.kof files found"), r.out());
    }

    @Test
    void testWithNoSourcesFailsExplicitlyInsteadOfSilentPass(@TempDir Path dir) throws Exception {
        // #708 (R6): an empty test root used to exit 0, indistinguishable from
        // "all tests passed". Zero discovered tests is not a success.
        Files.createDirectories(dir.resolve("tests"));
        Files.writeString(dir.resolve("tests/readme.txt"), "nada\n");
        Cli r = cli(dir, "test", "tests", "--target", "jvm");
        assertEquals(1, r.exit(), "test sem fontes deve falhar (nao exit 0):\n" + r.out());
        assertTrue(r.out().contains("no .kf/.kof files found"), r.out());
    }
}
