package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fase 2 (plataforma): parser mínimo de kof.toml — seções [project],
 * [backend], [frontend], [server]; chaves desconhecidas viram WARNING
 * (nunca silencioso, nunca erro).
 */
class KofProjectConfigTest {

    @Test
    void parsesFullManifest() {
        var cfg = KofProjectConfig.parse("""
                [project]
                name = "my-app"

                [backend]
                target = "jvm"

                [frontend]
                target = "kofjs"

                [server]
                port = 8080
                """);
        assertEquals("my-app", cfg.projectName());
        assertEquals("jvm", cfg.backendTarget());
        assertEquals("kofjs", cfg.frontendTarget());
        assertEquals(8080, cfg.serverPort());
        assertTrue(cfg.warnings().isEmpty(), "sem warnings: " + cfg.warnings());
    }

    @Test
    void unknownKeysWarnButDoNotFail() {
        var cfg = KofProjectConfig.parse("""
                [project]
                name = "x"
                author = "mel"

                [banco]
                target = "native"
                """);
        assertEquals("x", cfg.projectName());
        assertEquals(2, cfg.warnings().size(), "warnings: " + cfg.warnings());
    }

    @Test
    void missingFileIsEmpty() throws IOException {
        var cfg = KofProjectConfig.load(Path.of("/nonexistent-kof-toml"));
        assertNull(cfg.projectName());
        assertNull(cfg.backendTarget());
    }

    @Test
    void loadFromProjectRoot(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("kof.toml"),
                "[backend]\ntarget = \"native\"\n");
        var cfg = KofProjectConfig.load(tmp);
        assertEquals("native", cfg.backendTarget());
    }

    @Test
    void malformedPortWarns() {
        var cfg = KofProjectConfig.parse("[server]\nport = \"abc\"\n");
        assertNull(cfg.serverPort());
        assertEquals(1, cfg.warnings().size());
    }

    @Test
    void parsesSeparateSourceRoots() {
        // #708: [sources] app/test — raízes separadas de aplicação e testes.
        var cfg = KofProjectConfig.parse("""
                [sources]
                app = "src/main/kof"
                test = "src/test/kof"
                """);
        assertEquals("src/main/kof", cfg.sourceApp());
        assertEquals("src/test/kof", cfg.sourceTest());
        assertTrue(cfg.warnings().isEmpty(), "sem warnings: " + cfg.warnings());
    }

    @Test
    void resolveSourceRootIsRelativeToProjectRoot(@TempDir Path tmp) {
        Path resolved = KofProjectConfig.resolveSourceRoot(
                tmp, "src/main/kof", null);
        assertNotNull(resolved);
        assertEquals(tmp.resolve("src/main/kof").toAbsolutePath().normalize(), resolved);
        // sem declaração → fallback (comportamento atual inalterado)
        Path fallback = tmp.resolve("fallback");
        assertEquals(fallback, KofProjectConfig.resolveSourceRoot(tmp, null, fallback));
        assertEquals(fallback, KofProjectConfig.resolveSourceRoot(tmp, "  ", fallback));
    }
}
