package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * §6 provider gate of the testing-platform plan (`D-MAINT-BATCH-0610B`/C as
 * refined by the maintainer's third chat poll, 10/10): the project manifest
 * `kof-test.kofmd` declares the browser provider + version; `kof test
 * --tag browser` reads it and gates — a declared provider whose binary probes
 * runs, otherwise the honest refusal with the named reason. A run with no
 * browser tests is not gated (the manifest is advisory there).
 *
 * <p>RED-first: pre-slice `kof test --tag browser` ignored the gate entirely
 * (no manifest concept) — a browser run with no provider was a false green.
 */
class CmdTestProviderGateTest {

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

    private static Path browserSuite(Path dir) throws Exception {
        Path tests = dir.resolve("tests");
        Files.createDirectories(tests);
        Files.writeString(tests.resolve("ui.kf"),
                "test \"navegador\", \"browser\" {\n    assert(1 == 1)\n}\n");
        return tests;
    }

    @Test
    void browserTaggedRunWithoutManifestRefusesHonest(@TempDir Path dir) throws Exception {
        Path tests = browserSuite(dir);
        Cli r = cli(dir, "test", tests.toString(), "--tag", "browser");
        assertEquals(1, r.exit(), "browser run sem manifesto deve recusar:\n" + r.out());
        assertTrue(r.out().contains("require a provider declared in"
                + " kof-test.kofmd"), r.out());
        assertTrue(r.out().contains("0 of 1 files ran"), r.out());
    }

    private static boolean nodeAvailable() {
        String path = System.getenv("PATH");
        if (path == null) return false;
        for (String d : path.split(java.io.File.pathSeparator)) {
            if (!d.isEmpty() && Files.isExecutable(Path.of(d, "node"))) return true;
        }
        return false;
    }

    @Test
    void browserTaggedRunWithDeclaredProviderAndBinaryRuns(@TempDir Path dir) throws Exception {
        assumeTrue(nodeAvailable(), "node/npx ausente — o gate do provider precisa do binário");
        Path tests = browserSuite(dir);
        Files.writeString(dir.resolve("kof-test.kofmd"),
                "provider: playwright\nversion: 1.64.0\n");
        Cli r = cli(dir, "test", tests.toString(), "--tag", "browser");
        assertEquals(0, r.exit(), "manifesto + binário probeável devem rodar:\n" + r.out());
        assertTrue(r.out().contains("browser provider: playwright 1.64.0"), r.out());
    }

    @Test
    void runWithoutBrowserTestsIgnoresTheManifest(@TempDir Path dir) throws Exception {
        Path tests = dir.resolve("tests");
        Files.createDirectories(tests);
        Files.writeString(tests.resolve("math.kf"),
                "test \"soma\" {\n    assert(2 + 2 == 4)\n}\n");
        Cli r = cli(dir, "test", tests.toString());
        assertEquals(0, r.exit(), "run sem testes browser NÃO é gateada:\n" + r.out());
        assertTrue(!r.out().contains("browser provider"), r.out());
    }

    @Test
    void manifestParseIsFieldColonValueWithWarnings(@TempDir Path dir) {
        KofTestManifest m = KofTestManifest.parse("provider: playwright\nversion: 1.64.0\n# comment\n");
        assertEquals("playwright", m.provider());
        assertEquals("1.64.0", m.version());
        assertTrue(m.warnings().isEmpty());
        KofTestManifest bad = KofTestManifest.parse("provider playwright\nwhatever: x\n");
        assertTrue(!bad.declaresProvider(), "linha sem ':' é warning, nunca exceção");
        assertEquals(2, bad.warnings().size());
        assertTrue(KofTestManifest.load(null).declaresProvider() == false);
    }
}
