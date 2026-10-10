package dev.kof.cli;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 1.5.3-S2 (D2-A, DECISIONS.md) — o lado PULL do registry: `kof deps` resolve
 * `owner/repo[@ver]` lendo releases no formato exato do `kof deploy --publish`
 * (tar.gz empacotado pelo MESMO `CmdDeploy.writeTarGz`, jar + RELEASE.md +
 * SHA256SUMS conferido). Fake server local — nunca rede real no gate
 * (padrão CmdDeployTest). Faces Q3: happy+classpath, idempotência (2ª resolve
 * não re-baixa), latest→pin, 404=REG001, soma violada=REG002 e nada instalado,
 * pacote sem jar=REG003, sem SHA256SUMS=REG004.
 *
 * <p>O harness (fake server, pacote D2-A, runner de subprocesso) vive em
 * {@link DepsRegistrySupport} (Fase 3, {@code D-TEST-ARCHITECTURE-GO}); esta
 * classe só carrega os casos.
 */
class DepsRegistryTest extends DepsRegistrySupport {

    @Test
    void pullResolvesKofReleaseClasspathSeesTheJarAndIsIdempotent(@TempDir Path tmp)
            throws Exception {
        byte[] tgz = buildPackage(tmp.resolve("rel"), "hello", "1.2.3", true, true, true);
        HttpServer server = serveFakeRegistry(tmp, "hello", "1.2.3", tgz, true);
        try {
            Path proj = tmp.resolve("proj");
            Files.createDirectories(proj);
            Path home = tmp.resolve("home");
            Map<String, String> env = envOf(server, home);

            CliResult add = runWithEnv(proj, env, "deps", "add", "acme/hello@1.2.3");
            assertEquals(0, add.exit(), "add aceita formato kof:\n" + add.out());
            assertTrue(Files.readString(proj.resolve("kofdeps")).contains("acme/hello@1.2.3"),
                    "linha kof registrada em kofdeps");

            CliResult r = runWithEnv(proj, env, "deps", "resolve");
            assertEquals(0, r.exit(), "resolve feliz:\n" + r.out());
            Path cached = home.resolve(".kof/deps/kof/acme/hello/1.2.3/hello-1.2.3.jar");
            assertTrue(Files.exists(cached), "jar instalado no cache kof:\n" + r.out());
            String cp = r.out().lines()
                    .filter(l -> l.contains("hello-1.2.3.jar") && !l.startsWith("baixado"))
                    .findFirst().orElse(null);
            assertNotNull(cp, "classpath final traz o jar:\n" + r.out());
            assertTrue(cp.startsWith(cached.toString()), "classpath = caminho do cache: " + cp);

            CliResult r2 = runWithEnv(proj, env, "deps", "resolve");
            assertEquals(0, r2.exit(), r2.out());
            assertFalse(r2.out().contains("baixado"), "2ª resolve NAO re-baixa:\n" + r2.out());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void latestResolvesAndPinsConcreteVersion(@TempDir Path tmp) throws Exception {
        byte[] tgz = buildPackage(tmp.resolve("rel"), "hello", "2.0.0", true, true, true);
        HttpServer server = serveFakeRegistry(tmp, "hello", "2.0.0", tgz, true);
        try {
            Path proj = tmp.resolve("proj");
            Files.createDirectories(proj);
            Path home = tmp.resolve("home");
            Map<String, String> env = envOf(server, home);
            CliResult a = runWithEnv(proj, env, "deps", "add", "acme/hello");
            assertEquals(0, a.exit(), "add aceita formato kof sem versao:\n" + a.out());
            CliResult r = runWithEnv(proj, env, "deps", "resolve");
            assertEquals(0, r.exit(), "latest resolve:\n" + r.out());
            String declared = Files.readString(proj.resolve("kofdeps"));
            assertTrue(declared.contains("acme/hello@2.0.0"),
                    "latest pinou a versao concreta no kofdeps: " + declared);
            assertTrue(Files.exists(home.resolve(".kof/deps/kof/acme/hello/2.0.0/hello-2.0.0.jar")),
                    "jar 2.0.0 instalado");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void missingReleaseIsHonestReg001(@TempDir Path tmp) throws Exception {
        byte[] tgz = buildPackage(tmp.resolve("rel"), "hello", "1.2.3", true, true, true);
        HttpServer server = serveFakeRegistry(tmp, "hello", "1.2.3", tgz, false);
        try {
            Path proj = tmp.resolve("proj");
            Files.createDirectories(proj);
            Map<String, String> env = envOf(server, tmp.resolve("home"));
            runWithEnv(proj, env, "deps", "add", "acme/hello@9.9.9");
            CliResult r = runWithEnv(proj, env, "deps", "resolve");
            assertEquals(1, r.exit(), "tag inexistente falha com exit!=0");
            assertTrue(r.out().contains("REG001"), "diagnostico REG001:\n" + r.out());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void tamperedChecksumRefusesInstallReg002(@TempDir Path tmp) throws Exception {
        byte[] tgz = buildPackage(tmp.resolve("rel"), "hello", "1.2.3", true, false, true);
        HttpServer server = serveFakeRegistry(tmp, "hello", "1.2.3", tgz, true);
        try {
            Path proj = tmp.resolve("proj");
            Files.createDirectories(proj);
            Path home = tmp.resolve("home");
            Map<String, String> env = envOf(server, home);
            runWithEnv(proj, env, "deps", "add", "acme/hello@1.2.3");
            CliResult r = runWithEnv(proj, env, "deps", "resolve");
            assertEquals(1, r.exit(), "soma violada falha");
            assertTrue(r.out().contains("REG002") && r.out().contains("SHA256 mismatch"),
                    "diagnostico REG002 por SHA256 (nao por asset ausente):\n" + r.out());
            assertFalse(Files.exists(home.resolve(".kof/deps/kof/acme/hello/1.2.3/hello-1.2.3.jar")),
                    "nada instalado com checksum quebrado");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void releaseWithoutJarIsReg003(@TempDir Path tmp) throws Exception {
        byte[] tgz = buildPackage(tmp.resolve("rel"), "hello", "1.2.3", false, true, true);
        HttpServer server = serveFakeRegistry(tmp, "hello", "1.2.3", tgz, true);
        try {
            Path proj = tmp.resolve("proj");
            Files.createDirectories(proj);
            Map<String, String> env = envOf(server, tmp.resolve("home"));
            runWithEnv(proj, env, "deps", "add", "acme/hello@1.2.3");
            CliResult r = runWithEnv(proj, env, "deps", "resolve");
            assertEquals(1, r.exit());
            assertTrue(r.out().contains("REG003"), "diagnostico REG003:\n" + r.out());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void releaseWithoutSumsIsReg004(@TempDir Path tmp) throws Exception {
        byte[] tgz = buildPackage(tmp.resolve("rel"), "hello", "1.2.3", true, true, false);
        HttpServer server = serveFakeRegistry(tmp, "hello", "1.2.3", tgz, true);
        try {
            Path proj = tmp.resolve("proj");
            Files.createDirectories(proj);
            Map<String, String> env = envOf(server, tmp.resolve("home"));
            runWithEnv(proj, env, "deps", "add", "acme/hello@1.2.3");
            CliResult r = runWithEnv(proj, env, "deps", "resolve");
            assertEquals(1, r.exit());
            assertTrue(r.out().contains("REG004"), "integrity obrigatoria (REG004):\n" + r.out());
        } finally {
            server.stop(0);
        }
    }

    // ---- #564: contrato contra o shape REAL da API de Releases do GitHub ----

    @Test
    void realGithubShapeWithNestedUploaderResolves(@TempDir Path tmp) throws Exception {
        // T1: `uploader{...}` (com `url` proprio) vem ANTES de browser_download_url; sem download_url.
        byte[] tgz = buildPackage(tmp.resolve("rel"), "hello", "1.2.3", true, true, true);
        HttpServer server = serveFakeRegistry(tmp, "hello", "1.2.3", tgz, true);
        try {
            CliResult r = pull(tmp, server, "acme/hello@1.2.3", Map.of());
            assertEquals(0, r.exit(), "release no shape real do GitHub deve resolver:\n" + r.out());
            assertTrue(jarInstalled(tmp, "1.2.3"), "jar instalado:\n" + r.out());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fieldOrderAndDelimitersInsideStringsDoNotMatter(@TempDir Path tmp) throws Exception {
        // T2 (ordem dos campos invertida) + T3 (`}` `]` e aspas escapadas dentro de string).
        byte[] tgz = buildPackage(tmp.resolve("rel"), "hello", "1.2.3", true, true, true);
        HttpServer server = serveFakeRegistry("hello", "1.2.3", tgz, true,
                List.of(new FakeAsset(123, "hello-1.2.3.tar.gz")), Order.REVERSED, false, false);
        try {
            CliResult r = pull(tmp, server, "acme/hello@1.2.3", Map.of());
            assertEquals(0, r.exit(), "ordem invertida deve resolver igual:\n" + r.out());
            assertTrue(jarInstalled(tmp, "1.2.3"), r.out());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void assetIsDownloadedFromApiUrlWithOctetStreamAndPinnedHeaders(@TempDir Path tmp)
            throws Exception {
        // T4 (Accept octet-stream no binario) + T7 (User-Agent e versao da API pinados).
        byte[] tgz = buildPackage(tmp.resolve("rel"), "hello", "1.2.3", true, true, true);
        HttpServer server = serveFakeRegistry(tmp, "hello", "1.2.3", tgz, true);
        try {
            CliResult r = pull(tmp, server, "acme/hello@1.2.3", Map.of());
            assertEquals(0, r.exit(), r.out());
            List<String> log = seen(server);
            String meta = log.stream().filter(l -> l.startsWith("META|")).findFirst().orElse("");
            String asset = log.stream().filter(l -> l.startsWith("ASSET|")).findFirst().orElse("");
            assertTrue(meta.contains("accept=application/vnd.github+json"), "metadata: " + log);
            assertTrue(asset.contains("|123|accept=application/octet-stream"),
                    "binario pelo `url` do asset (API) com octet-stream: " + log);
            for (String l : List.of(meta, asset)) {
                assertTrue(l.contains("ua=kof-cli"), "User-Agent kof-cli: " + l);
                assertTrue(l.contains("ver=2022-11-28"), "X-GitHub-Api-Version pinada: " + l);
            }
            assertTrue(log.stream().noneMatch(l -> l.contains("/browser/")),
                    "cliente de API nao usa browser_download_url: " + log);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void redirectIsFollowedAndTokenNeverLeavesTheApiHost(@TempDir Path tmp) throws Exception {
        // T5 (asset API -> 302 -> CDN -> 200) + T6 (Authorization so no host da API).
        byte[] tgz = buildPackage(tmp.resolve("rel"), "hello", "1.2.3", true, true, true);
        HttpServer server = serveFakeRegistry("hello", "1.2.3", tgz, true,
                List.of(new FakeAsset(123, "hello-1.2.3.tar.gz")), Order.GITHUB, true, false);
        try {
            CliResult r = pull(tmp, server, "acme/hello@1.2.3",
                    Map.of("GH_TOKEN", "kof-test-token-564"));
            assertEquals(0, r.exit(), "redirect deve ser seguido:\n" + r.out());
            assertTrue(jarInstalled(tmp, "1.2.3"), r.out());
            List<String> log = seen(server);
            String meta = log.stream().filter(l -> l.startsWith("META|")).findFirst().orElse("");
            String asset = log.stream().filter(l -> l.startsWith("ASSET|")).findFirst().orElse("");
            String cdn = log.stream().filter(l -> l.startsWith("CDN|")).findFirst().orElse("");
            assertTrue(meta.contains("auth=Bearer kof-test-token-564"), "metadata autenticada: " + meta);
            assertTrue(asset.contains("auth=Bearer kof-test-token-564"), "asset API autenticada: " + asset);
            assertFalse(cdn.isEmpty(), "o redirect chegou ao CDN: " + log);
            assertTrue(cdn.contains("auth=-"), "token NAO vai para outro host no redirect: " + cdn);
            assertFalse(r.out().contains("kof-test-token-564"), "token nunca aparece na saida");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void tarballSelectionKeepsExactThenJvmThenFirstTarGz(@TempDir Path tmp) throws Exception {
        // T8: politica preservada — <repo>-<ver>.tar.gz > <repo>-<ver>-jvm.tar.gz > 1o *.tar.gz.
        byte[] tgz = buildPackage(tmp.resolve("rel"), "hello", "1.2.3", true, true, true);
        List<FakeAsset> all = List.of(new FakeAsset(101, "notes.txt"),
                new FakeAsset(102, "other.tar.gz"),
                new FakeAsset(103, "hello-1.2.3-jvm.tar.gz"),
                new FakeAsset(104, "hello-1.2.3.tar.gz"));
        String[][] cases = {
                {"exact", "104", "0,1,2,3"}, {"jvm", "103", "0,1,2"}, {"first", "102", "0,1"}};
        for (String[] c : cases) {
            List<FakeAsset> offered = new java.util.ArrayList<>();
            for (String i : c[2].split(",")) offered.add(all.get(Integer.parseInt(i)));
            Path t = tmp.resolve(c[0]);
            Files.createDirectories(t);
            HttpServer server = serveFakeRegistry("hello", "1.2.3", tgz, true, offered,
                    Order.GITHUB, false, false);
            try {
                CliResult r = pull(t, server, "acme/hello@1.2.3", Map.of());
                assertEquals(0, r.exit(), c[0] + ":\n" + r.out());
                String asset = seen(server).stream().filter(l -> l.startsWith("ASSET|"))
                        .findFirst().orElse("");
                assertTrue(asset.startsWith("ASSET|" + c[1] + "|"),
                        "selecao " + c[0] + " deve baixar o asset " + c[1] + ": " + seen(server));
            } finally {
                server.stop(0);
            }
        }
    }

    @Test
    void releaseWithoutTarGzAssetIsHonestReg002(@TempDir Path tmp) throws Exception {
        byte[] tgz = buildPackage(tmp.resolve("rel"), "hello", "1.2.3", true, true, true);
        HttpServer server = serveFakeRegistry("hello", "1.2.3", tgz, true,
                List.of(new FakeAsset(101, "notes.txt")), Order.GITHUB, false, false);
        try {
            CliResult r = pull(tmp, server, "acme/hello@1.2.3", Map.of());
            assertEquals(1, r.exit(), "sem .tar.gz falha");
            assertTrue(r.out().contains("REG002") && r.out().contains("no .tar.gz asset"),
                    "REG002 honesto (asset genuinamente ausente):\n" + r.out());
            assertFalse(jarInstalled(tmp, "1.2.3"), "nada instalado");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void malformedReleaseJsonIsHonestReg001(@TempDir Path tmp) throws Exception {
        // Q3 erro esperado: JSON invalido nao pode virar "sem asset" silencioso nem excecao crua.
        byte[] tgz = buildPackage(tmp.resolve("rel"), "hello", "1.2.3", true, true, true);
        HttpServer server = serveFakeRegistry("hello", "1.2.3", tgz, true,
                List.of(new FakeAsset(123, "hello-1.2.3.tar.gz")), Order.GITHUB, false, true);
        try {
            CliResult r = pull(tmp, server, "acme/hello@1.2.3", Map.of());
            assertEquals(1, r.exit(), "JSON invalido falha");
            assertTrue(r.out().contains("REG001") && r.out().contains("invalid"),
                    "REG001 com causa JSON invalido:\n" + r.out());
            assertFalse(r.out().contains("Exception"), "sem stack crua:\n" + r.out());
            assertFalse(jarInstalled(tmp, "1.2.3"), "nada instalado");
        } finally {
            server.stop(0);
        }
    }
}
