package dev.kof.cli;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static dev.kof.cli.DepsRegistrySupport.CliResult;
import static dev.kof.cli.DepsRegistrySupport.envOf;
import static dev.kof.cli.DepsRegistrySupport.runWithEnv;
import static dev.kof.cli.DepsRegistrySupport.serveFakeRegistry;
import static org.junit.jupiter.api.Assertions.*;

/**
 * #566, opção (b) (DECISIONS.md, D-RELEASE-0.5.0-GATE addendum): o pacote publicado é consumido
 * como MÓDULO-FONTE. {@code kof deps resolve} instala as fontes VERIFICADAS (SHA256SUMS cobre cada
 * uma; só .kf/.kof) e {@code kof run/build --deps} entregam essas raízes ao compilador — o
 * {@code import} do consumidor resolve contra as fontes, não contra o jar. Fake registry local
 * com o shape REAL do GitHub (nunca rede no gate).
 */
class DepsSourceModuleTest {

    private static final String GREETER = """
        package regsmoke

        class Greeter {
            String greet(String who) { return "hello, " + who }
        }
        """;

    private static final String CONSUMER = """
        import regsmoke.Greeter
        main() { println(Greeter().greet("consumer")) }
        """;

    private enum Sums { GOOD, TAMPERED, UNLISTED, LISTED_BUT_MISSING }

    /** Pacote no formato novo: (jar opcional) + src/<rel> + RELEASE.md + SHA256SUMS. */
    private static byte[] buildSourcePackage(Path dir, String repo, String version, boolean withJar,
                                             Map<String, String> sources, Sums mode) throws Exception {
        Files.createDirectories(dir);
        List<Path> files = new ArrayList<>();
        StringBuilder sums = new StringBuilder();
        if (withJar) {
            Path jar = dir.resolve(repo + "-" + version + ".jar");
            try (JarOutputStream jo = new JarOutputStream(Files.newOutputStream(jar))) {
                jo.putNextEntry(new JarEntry("Hello.txt"));
                jo.write("hello-kof".getBytes(StandardCharsets.UTF_8));
                jo.closeEntry();
            }
            files.add(jar.getFileName());
            sums.append(CmdDeploy.sha256Hex(jar)).append("  ").append(jar.getFileName()).append("\n");
        }
        boolean first = true;
        for (var e : sources.entrySet()) {
            Path f = dir.resolve("src").resolve(e.getKey());
            Files.createDirectories(f.getParent());
            Files.writeString(f, e.getValue());
            files.add(Path.of("src/" + e.getKey()));
            String sha = CmdDeploy.sha256Hex(f);
            if (mode == Sums.TAMPERED && first) sha = "0".repeat(64);
            if (!(mode == Sums.UNLISTED && first)) {
                sums.append(sha).append("  src/").append(e.getKey()).append("\n");
            }
            first = false;
        }
        if (mode == Sums.LISTED_BUT_MISSING) {
            sums.append("0".repeat(64)).append("  src/ghost/Missing.kf\n");
        }
        Files.writeString(dir.resolve("RELEASE.md"), "# " + repo + " " + version + "\n");
        Files.writeString(dir.resolve("SHA256SUMS"), sums.toString());
        files.add(Path.of("RELEASE.md"));
        files.add(Path.of("SHA256SUMS"));
        Path tgz = dir.resolve(repo + "-" + version + ".tar.gz");
        CmdDeploy.writeTarGz(tgz, dir, files, 0644);
        return Files.readAllBytes(tgz);
    }

    private static Path versionDir(Path tmp) {
        return tmp.resolve("home/.kof/deps/kof/acme/hello/1.2.3");
    }

    private static CliResult resolve(Path tmp, HttpServer server) throws Exception {
        Path proj = tmp.resolve("proj");
        Files.createDirectories(proj);
        Map<String, String> env = envOf(server, tmp.resolve("home"));
        CliResult a = runWithEnv(proj, env, "deps", "add", "acme/hello@1.2.3");
        assertEquals(0, a.exit(), a.out());
        return runWithEnv(proj, env, "deps", "resolve");
    }

    private static void assertNothingInstalled(Path tmp, String why) {
        Path v = versionDir(tmp);
        assertFalse(Files.exists(v.resolve("src")), why + ": nenhuma fonte instalada");
        assertFalse(Files.exists(v.resolve("hello-1.2.3.jar")), why + ": nenhum jar instalado");
    }

    @Test
    void libraryPackageWithoutJarInstallsVerifiedSourcesAndIsIdempotent(@TempDir Path tmp)
            throws Exception {
        byte[] tgz = buildSourcePackage(tmp.resolve("rel"), "hello", "1.2.3", false,
                Map.of("regsmoke/Greeter.kf", GREETER), Sums.GOOD);
        HttpServer server = serveFakeRegistry(tmp, "hello", "1.2.3", tgz, true);
        try {
            CliResult r = resolve(tmp, server);
            assertEquals(0, r.exit(), "biblioteca so com fontes resolve:\n" + r.out());
            assertEquals(GREETER, Files.readString(versionDir(tmp).resolve("src/regsmoke/Greeter.kf")),
                    "fonte instalada intacta");
            assertFalse(Files.exists(versionDir(tmp).resolve("hello-1.2.3.jar")), "sem jar");
            CliResult again = runWithEnv(tmp.resolve("proj"), envOf(server, tmp.resolve("home")),
                    "deps", "resolve");
            assertEquals(0, again.exit(), again.out());
            assertFalse(again.out().contains("baixado"), "2a resolve NAO re-baixa:\n" + again.out());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void runWithDepsResolvesTheImportAgainstTheInstalledSources(@TempDir Path tmp) throws Exception {
        byte[] tgz = buildSourcePackage(tmp.resolve("rel"), "hello", "1.2.3", false,
                Map.of("regsmoke/Greeter.kf", GREETER), Sums.GOOD);
        HttpServer server = serveFakeRegistry(tmp, "hello", "1.2.3", tgz, true);
        try {
            assertEquals(0, resolve(tmp, server).exit());
            Path proj = tmp.resolve("proj");
            Files.writeString(proj.resolve("Main.kf"), CONSUMER);
            Map<String, String> env = envOf(server, tmp.resolve("home"));
            CliResult ok = runWithEnv(proj, env, "run", "Main.kf", "--deps");
            assertEquals(0, ok.exit(), "run --deps consome o modulo-fonte:\n" + ok.out());
            assertTrue(ok.out().contains("hello, consumer"),
                    "Greeter() sem new (classe-fonte KOF) roda:\n" + ok.out());
            CliResult noDeps = runWithEnv(proj, env, "run", "Main.kf");
            assertNotEquals(0, noDeps.exit(), "sem --deps a dependencia nao esta no modulo");
            assertTrue(noDeps.out().contains("PKG006"), "PKG006 honesto sem --deps:\n" + noDeps.out());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void buildWithDepsCompilesTheSourceModuleForJvmAndForJs(@TempDir Path tmp) throws Exception {
        byte[] tgz = buildSourcePackage(tmp.resolve("rel"), "hello", "1.2.3", false,
                Map.of("regsmoke/Greeter.kf", GREETER), Sums.GOOD);
        HttpServer server = serveFakeRegistry(tmp, "hello", "1.2.3", tgz, true);
        try {
            assertEquals(0, resolve(tmp, server).exit());
            Path proj = tmp.resolve("proj");
            Files.createDirectories(proj.resolve("src"));
            Files.writeString(proj.resolve("src/Main.kf"), CONSUMER);
            Map<String, String> env = envOf(server, tmp.resolve("home"));
            CliResult jvm = runWithEnv(proj, env, "build", "src", "--target", "jvm", "--deps",
                    "--output", "dist");
            assertEquals(0, jvm.exit(), "build jvm --deps:\n" + jvm.out());
            Process p = new ProcessBuilder(
                    Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-cp", proj.resolve("dist").toString(), "Default.Main")
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(0, p.waitFor(), out);
            assertEquals("hello, consumer", out.trim(),
                    "o artifact roda sem jar de dependencia (as classes foram compiladas da fonte)");
            CliResult js = runWithEnv(proj, env, "build", "src", "--target", "js", "--deps",
                    "--output", "distjs");
            assertEquals(0, js.exit(), "as MESMAS fontes compilam para JS:\n" + js.out());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void packageWithSourcesKeepsItsJarOutOfTheClasspath(@TempDir Path tmp) throws Exception {
        byte[] tgz = buildSourcePackage(tmp.resolve("rel"), "hello", "1.2.3", true,
                Map.of("regsmoke/Greeter.kf", GREETER), Sums.GOOD);
        HttpServer server = serveFakeRegistry(tmp, "hello", "1.2.3", tgz, true);
        try {
            CliResult r = resolve(tmp, server);
            assertEquals(0, r.exit(), r.out());
            assertTrue(Files.exists(versionDir(tmp).resolve("hello-1.2.3.jar")), "jar tambem instalado");
            assertTrue(Files.exists(versionDir(tmp).resolve("src/regsmoke/Greeter.kf")), "fontes instaladas");
            assertFalse(r.out().lines().anyMatch(l -> l.contains("hello-1.2.3.jar")
                            && !l.startsWith("baixado")),
                    "consumido como fonte: o jar NAO vai ao classpath (duplicaria as classes):\n" + r.out());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void tamperedSourceIsReg002AndNothingIsInstalled(@TempDir Path tmp) throws Exception {
        byte[] tgz = buildSourcePackage(tmp.resolve("rel"), "hello", "1.2.3", true,
                Map.of("regsmoke/Greeter.kf", GREETER), Sums.TAMPERED);
        HttpServer server = serveFakeRegistry(tmp, "hello", "1.2.3", tgz, true);
        try {
            CliResult r = resolve(tmp, server);
            assertEquals(1, r.exit());
            assertTrue(r.out().contains("REG002") && r.out().contains("SHA256 mismatch")
                    && r.out().contains("src/regsmoke/Greeter.kf"), "REG002 por fonte adulterada:\n" + r.out());
            assertNothingInstalled(tmp, "soma violada");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sourceMissingFromSumsIsReg004AndNothingIsInstalled(@TempDir Path tmp) throws Exception {
        byte[] tgz = buildSourcePackage(tmp.resolve("rel"), "hello", "1.2.3", true,
                new LinkedHashMap<>(Map.of("regsmoke/Greeter.kf", GREETER)), Sums.UNLISTED);
        HttpServer server = serveFakeRegistry(tmp, "hello", "1.2.3", tgz, true);
        try {
            CliResult r = resolve(tmp, server);
            assertEquals(1, r.exit());
            assertTrue(r.out().contains("REG004") && r.out().contains("not listed"),
                    "integridade nao e opcional para fontes:\n" + r.out());
            assertNothingInstalled(tmp, "fonte fora do SHA256SUMS");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sumsListingAMissingSourceIsReg004(@TempDir Path tmp) throws Exception {
        byte[] tgz = buildSourcePackage(tmp.resolve("rel"), "hello", "1.2.3", true,
                Map.of("regsmoke/Greeter.kf", GREETER), Sums.LISTED_BUT_MISSING);
        HttpServer server = serveFakeRegistry(tmp, "hello", "1.2.3", tgz, true);
        try {
            CliResult r = resolve(tmp, server);
            assertEquals(1, r.exit());
            assertTrue(r.out().contains("REG004") && r.out().contains("missing"),
                    "fonte listada e ausente (remocao) e recusada:\n" + r.out());
            assertNothingInstalled(tmp, "fonte listada e ausente");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void onlyKfFilesAreInstalledFromSources(@TempDir Path tmp) throws Exception {
        Map<String, String> srcs = new LinkedHashMap<>();
        srcs.put("regsmoke/Greeter.kf", GREETER);
        srcs.put("regsmoke/evil.sh", "echo pwned\n");
        byte[] tgz = buildSourcePackage(tmp.resolve("rel"), "hello", "1.2.3", true, srcs, Sums.GOOD);
        HttpServer server = serveFakeRegistry(tmp, "hello", "1.2.3", tgz, true);
        try {
            CliResult r = resolve(tmp, server);
            assertEquals(1, r.exit());
            assertTrue(r.out().contains("REG004") && r.out().contains("only .kf/.kof"),
                    "so .kf/.kof entram, mesmo listado no SHA256SUMS:\n" + r.out());
            assertNothingInstalled(tmp, "arquivo que nao e fonte");
        } finally {
            server.stop(0);
        }
    }
}
