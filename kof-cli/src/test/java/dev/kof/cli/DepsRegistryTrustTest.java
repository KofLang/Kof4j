package dev.kof.cli;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.kof.cli.DepsRegistrySupport.CliResult;
import static dev.kof.cli.DepsRegistrySupport.FakeAsset;
import static dev.kof.cli.DepsRegistrySupport.Order;
import static org.junit.jupiter.api.Assertions.*;

/**
 * D-ARTIFACT-TRUST (c) — a politica de verificacao INTEGRADA ao `kof deps resolve` real
 * (processo filho contra um registry falso, o mesmo harness do {@link DepsRegistryTest}).
 * Prova o contrato de ponta a ponta do que da para provar SEM atestacao real: oficial sem
 * evidencia = bloqueado e NADA instalado; comunitario = aviso visivel e instala; o rollout
 * (observe) nunca bloqueia mas diz o que bloquearia. O E2E com atestacao verdadeira depende
 * do formato que o attest+verify da fila (b) produzir.
 */
class DepsRegistryTrustTest {

    private static final String JAR = ".kof/deps/kof/acme/hello/1.2.3/hello-1.2.3.jar";

    private static final List<FakeAsset> TARBALL_ONLY =
            List.of(new FakeAsset(123, "hello-1.2.3.tar.gz"));
    private static final List<FakeAsset> WITH_EVIDENCE = List.of(
            new FakeAsset(123, "hello-1.2.3.tar.gz"),
            new FakeAsset(124, "hello-1.2.3.tar.gz.sigstore.json"));

    /** Roda `kof deps add acme/hello@1.2.3` + `resolve` contra o registry falso com o env extra. */
    private static CliResult resolve(Path tmp, List<FakeAsset> assets, Map<String, String> extraEnv,
                                     Path homeOut) throws Exception {
        byte[] tgz = DepsRegistrySupport.buildPackage(tmp.resolve("rel"), "hello", "1.2.3", true, true, true);
        HttpServer server = DepsRegistrySupport.serveFakeRegistry("hello", "1.2.3", tgz, true, assets,
                Order.GITHUB, false, false);
        try {
            Path proj = tmp.resolve("proj");
            Files.createDirectories(proj);
            Map<String, String> env = new HashMap<>(DepsRegistrySupport.envOf(server, homeOut));
            env.putAll(extraEnv);
            DepsRegistrySupport.runWithEnv(proj, env, "deps", "add", "acme/hello@1.2.3");
            return DepsRegistrySupport.runWithEnv(proj, env, "deps", "resolve");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void officialWithoutEvidenceIsBlockedAndNothingIsInstalledWhenEnforcing(@TempDir Path tmp) throws Exception {
        Path home = tmp.resolve("home");
        CliResult r = resolve(tmp, TARBALL_ONLY,
                Map.of("KOF_DEPS_OFFICIAL_OWNERS", "acme", "KOF_DEPS_TRUST", "enforce"), home);
        assertEquals(1, r.exit(), "oficial sem evidencia = exit != 0:\n" + r.out());
        assertTrue(r.out().contains("REG005"), "diagnostico REG005:\n" + r.out());
        assertFalse(Files.exists(home.resolve(JAR)), "nada instalado quando o gate bloqueia");
    }

    @Test
    void officialWithEvidenceButNoWiredVerifierFailsClosedReg007(@TempDir Path tmp) throws Exception {
        Path home = tmp.resolve("home");
        CliResult r = resolve(tmp, WITH_EVIDENCE,
                Map.of("KOF_DEPS_OFFICIAL_OWNERS", "acme", "KOF_DEPS_TRUST", "enforce"), home);
        assertEquals(1, r.exit(), "evidencia presente mas sem verificador NAO vira 'verificado':\n" + r.out());
        assertTrue(r.out().contains("REG007"), "diagnostico REG007:\n" + r.out());
        assertFalse(Files.exists(home.resolve(JAR)), "nada instalado");
    }

    @Test
    void communityWithoutEvidenceWarnsVisiblyAndStillInstalls(@TempDir Path tmp) throws Exception {
        Path home = tmp.resolve("home");
        CliResult r = resolve(tmp, TARBALL_ONLY, Map.of("KOF_DEPS_TRUST", "enforce"), home);
        assertEquals(0, r.exit(), "comunitario nao bloqueia nem em enforce:\n" + r.out());
        assertTrue(r.out().contains("REG005"), "aviso honesto, nunca silencio (R6):\n" + r.out());
        assertFalse(r.out().toLowerCase().contains("would block"), r.out());
        assertTrue(Files.exists(home.resolve(JAR)), "instalou");
    }

    @Test
    void observeModeInstallsButShoutsWhatEnforcementWouldBlock(@TempDir Path tmp) throws Exception {
        Path home = tmp.resolve("home");
        CliResult r = resolve(tmp, TARBALL_ONLY, Map.of("KOF_DEPS_OFFICIAL_OWNERS", "acme"), home);
        assertEquals(0, r.exit(), "rollout: observe nunca bloqueia:\n" + r.out());
        assertTrue(r.out().contains("REG005") && r.out().toLowerCase().contains("would block"),
                "o aviso diz o que bloquearia:\n" + r.out());
        assertTrue(Files.exists(home.resolve(JAR)), "instalou");
    }

    @Test
    void environmentCanOnlyTightenNeverRelax(@TempDir Path tmp) throws Exception {
        // valores que "parecem" afrouxar nao afrouxam nem apertam: modo segue observe (instala + grita)
        Path home = tmp.resolve("home");
        CliResult r = resolve(tmp, TARBALL_ONLY,
                Map.of("KOF_DEPS_OFFICIAL_OWNERS", "acme", "KOF_DEPS_TRUST", "off"), home);
        assertEquals(0, r.exit(), r.out());
        assertTrue(r.out().contains("REG005"), "sem chave que silencie o aviso:\n" + r.out());
    }
}
