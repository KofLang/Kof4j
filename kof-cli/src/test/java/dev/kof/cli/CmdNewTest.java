package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D-APP I1 ({@code DECISIONS.md} §D-APP): {@code kof new} — esqueletos por
 * tipo nascendo compiláveis + APP003 honesto (R6: nunca silencioso).
 *
 * <p>Q3: cada tipo gera os arquivos esperados e o manifesto é parseável por
 * {@code KofProjectConfig}; esqueleto backend/full-stack COMPILA de verdade
 * (gateway JVM do driver); bordas: kof.toml existente, tipo inválido, flag
 * desconhecida, dir existente sem conflito.</p>
 */
class CmdNewTest {

    @Test
    void backendSkeletonHasManifestAndApi(@TempDir Path dir) throws IOException {
        assertEquals(0, CmdNew.run(with(dir, "--type", "backend")));
        assertTrue(Files.exists(dir.resolve("kof.toml")), "manifesto deve existir");
        assertTrue(Files.exists(dir.resolve("src/Main.kf")), "backend deve nascer com src/Main.kf");
        String manifest = Files.readString(dir.resolve("kof.toml"));
        assertTrue(manifest.contains("[backend]"), "seção [backend]: " + manifest);
        var cfg = dev.kof.compiler.KofProjectConfig.parse(manifest);
        assertEquals("jvm", cfg.backendTarget(), "target do backend");
        assertTrue(Files.readString(dir.resolve("src/Main.kf")).contains("web.app()"),
                "esqueleto backend usa kof.web (plataforma, não re-implementação)");

    }

    @Test
    void frontendSkeletonHasKofjsTarget(@TempDir Path dir) throws IOException {
        assertEquals(0, CmdNew.run(with(dir, "--type", "frontend")));
        var cfg = dev.kof.compiler.KofProjectConfig.parse(Files.readString(dir.resolve("kof.toml")));
        assertEquals("kofjs", cfg.frontendTarget());
        assertTrue(Files.exists(dir.resolve("src/web/Index.kf")));
    }

    @Test
    void fullStackSkeletonHasBothSectionsAndBackendCompiles(@TempDir Path dir) throws IOException {
        assertEquals(0, CmdNew.run(with(dir, "--type", "full-stack")));
        var cfg = dev.kof.compiler.KofProjectConfig.parse(Files.readString(dir.resolve("kof.toml")));
        assertEquals("jvm", cfg.backendTarget());
        assertEquals("kofjs", cfg.frontendTarget());
        assertTrue(Files.exists(dir.resolve("src/web/Index.kf")));
        assertTrue(Files.exists(dir.resolve("src/static/app.css")));
        assertEquals("/* app styles */\n", Files.readString(dir.resolve("src/static/app.css")),
                "css do scaffold em English (D-DIAG-EN)");
        // O esqueleto nasce COMPILÁVEL (prova real, não só arquivos no disco).
        var result = new dev.kof.compiler.CompilerDriver()
                .compile(dir.resolve("src/Main.kf"), dir.resolve("target/probe"), dev.kof.compiler.Target.JVM);
        assertTrue(result.success(),
                "esqueleto full-stack deve compilar JVM: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void backendSkeletonCompiles(@TempDir Path dir) throws IOException {
        assertEquals(0, CmdNew.run(with(dir, "--type", "backend")));
        var result = new dev.kof.compiler.CompilerDriver()
                .compile(dir.resolve("src/Main.kf"), dir.resolve("target/probe"), dev.kof.compiler.Target.JVM);
        assertTrue(result.success(),
                "esqueleto backend deve compilar JVM: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void everySkeletonCompilesWithoutASingleDiagnostic(@TempDir Path dir) throws IOException {
        // success() sozinho esconde WARNING — foi assim que o scaffold backend
        // passou meses emitindo MEM014 contra si mesmo (L-05 em app.listen).
        // O scaffold do próprio Kof é o pior lugar possível para ruído: o
        // usuário conclui que escreveu errado algo que o template escreveu.
        record Shape(String type, String entry, dev.kof.compiler.Target target) {}
        var shapes = java.util.List.of(
                new Shape("mono", "src/Main.kf", dev.kof.compiler.Target.JVM),
                new Shape("backend", "src/Main.kf", dev.kof.compiler.Target.JVM),
                new Shape("frontend", "src/web/Index.kf", dev.kof.compiler.Target.JS),
                new Shape("full-stack", "src/Main.kf", dev.kof.compiler.Target.JVM));
        for (var shape : shapes) {
            Path project = dir.resolve(shape.type());
            assertEquals(0, CmdNew.run(new String[]{project.toString(), "--type", shape.type()}),
                    "kof new --type " + shape.type());
            var result = new dev.kof.compiler.CompilerDriver()
                    .compile(project.resolve(shape.entry()), project.resolve("target/probe"), shape.target());
            assertTrue(result.success(), "esqueleto " + shape.type() + " deve compilar: "
                    + result.diagnostics().getDiagnostics());
            assertTrue(result.diagnostics().getDiagnostics().isEmpty(),
                    "esqueleto '" + shape.type() + "' não pode emitir NENHUM diagnóstico "
                            + "(o usuário não escreveu o template): "
                            + result.diagnostics().getDiagnostics());
        }
    }

    @Test
    void unknownTypeIsHonestGapApp003(@TempDir Path dir) {
        assertNotEquals(0, CmdNew.run(with(dir, "--type", "microservices")));
        assertFalse(Files.exists(dir.resolve("kof.toml")), "falha não deve criar projeto parcial");
    }

    @Test
    void existingManifestIsRefusedApp003(@TempDir Path dir) throws IOException {
        assertEquals(0, CmdNew.run(with(dir, "--type", "mono")));
        // D-DIAG-EN: o scaffold gerado é tooling — comentário 100% EN, nunca PT.
        String mainKf = Files.readString(dir.resolve("src/Main.kf"));
        assertTrue(mainKf.contains("// Kof project — run with: kof run src/Main.kf"),
                "header do scaffold mono é English (D-DIAG-EN)");
        assertFalse(mainKf.contains("rode com"), "nenhum PT vivo no scaffold (D-DIAG-EN)");
        String before = Files.readString(dir.resolve("kof.toml"));
        assertNotEquals(0, CmdNew.run(with(dir, "--type", "backend")));
        assertEquals(before, Files.readString(dir.resolve("kof.toml")), "nunca sobrescrever");
    }

    @Test
    void unknownFlagIsRefusedApp003(@TempDir Path dir) {
        assertNotEquals(0, CmdNew.run(with(dir, "--rust")));
        assertFalse(Files.exists(dir.resolve("kof.toml")));
    }

    @Test
    void missingDirIsUsageNotCrash() {
        assertNotEquals(0, CmdNew.run(new String[0]));
    }

    private static String[] with(Path dir, String... rest) {
        String[] out = new String[1 + rest.length];
        out[0] = dir.toString();
        System.arraycopy(rest, 0, out, 1, rest.length);
        return out;
    }
}
