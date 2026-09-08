package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fase 1 (plataforma) — Module System:
 * - kof.toml define a raiz do projeto (imports resolvem a partir dela).
 * - Imports cross-directory: mesmo dir, irmão, subdiretório, sob a raiz.
 * - PKG006: import que não resolve (e não é externo) → diagnóstico.
 * - Retrocompatibilidade: sem kof.toml, comportamento atual (LCA) intacto.
 */
class ModuleResolutionTest {
    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(List<Path> sources, Path out, Path root) throws IOException {
        return driver.compileSources(sources, out, Target.JVM, root);
    }

    @Test
    void importFromSiblingDirectoryUnderProjectRoot(@TempDir Path tmp) throws IOException {
        Path root = tmp.resolve("proj");
        Files.createDirectories(root.resolve("src"));
        Files.createDirectories(root.resolve("shared"));
        Files.writeString(root.resolve("kof.toml"), "[project]\nname = \"proj\"\n");
        Files.writeString(root.resolve("shared/Validation.kf"), """
                package shared

                fun min(Int a, Int b): Int {
                    return if (a < b) a else b
                }
                """.replace("fun min(Int a, Int b): Int {", "Int min(Int a, Int b) {").replace("}", "}"));
        Path main = root.resolve("src/main.kf");
        Files.writeString(main, """
                import shared.Validation

                main() {
                    println(min(3, 7))
                }
                """);
        CompilationResult r = compile(List.of(main), tmp.resolve("out"), root);
        assertTrue(r.success(), "import cross-directory deve resolver via raiz do projeto: "
                + r.diagnostics().getDiagnostics());
    }

    @Test
    void sameDirectoryImportStillWorks(@TempDir Path tmp) throws IOException {
        // modelo atual: arquivos irmãos são o MESMO módulo — declaração em
        // Lib.kf é visível a main.kf sem import (multi-file same directory)
        Path dir = tmp.resolve("m");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Lib.kf"), """
                Int dbl(Int x) { return x * 2 }
                """);
        Path main = dir.resolve("main.kf");
        Files.writeString(main, """
                main() {
                    println(dbl(21))
                }
                """);
        CompilationResult r = compile(List.of(main, dir.resolve("Lib.kf")), tmp.resolve("out"), dir);
        assertTrue(r.success(), "arquivos irmãos seguem compondo um módulo: "
                + r.diagnostics().getDiagnostics());
    }

    @Test
    void unresolvedImportGivesPKG006(@TempDir Path tmp) throws IOException {
        Path root = tmp.resolve("proj");
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("kof.toml"), "[project]\nname = \"proj\"\n");
        Path main = root.resolve("src/main.kf");
        Files.writeString(main, """
                import shared.NaoExiste

                main() {
                    println("x")
                }
                """);
        CompilationResult r = compile(List.of(main), tmp.resolve("out"), root);
        assertFalse(r.success(), "import inexistente deve falhar com PKG006");
        boolean found = r.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> "PKG006".equals(d.code()));
        assertTrue(found, "PKG006 esperado, foi: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void externalImportsAreNotPKG006(@TempDir Path tmp) throws IOException {
        Path root = tmp.resolve("proj");
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("kof.toml"), "[project]\nname = \"proj\"\n");
        Path main = root.resolve("src/main.kf");
        Files.writeString(main, """
                import android.view.View
                import java.util.List

                main() {
                    println("x")
                }
                """);
        CompilationResult r = compile(List.of(main), tmp.resolve("out"), root);
        boolean pkg006 = r.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> "PKG006".equals(d.code()));
        assertFalse(pkg006, "imports externos (android/java) não devem virar PKG006: "
                + r.diagnostics().getDiagnostics());
    }

    @Test
    void noManifestKeepsLegacyBehavior(@TempDir Path tmp) throws IOException {
        // sem kof.toml: fontes irmãs no mesmo diretório (modelo multi-file atual)
        Path dir = tmp.resolve("legacy");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Lib.kf"), """
                class Lib {
                    Int dbl(Int x) { return x * 2 }
                }
                """);
        Path main = dir.resolve("main.kf");
        Files.writeString(main, """
                main() {
                    var l = Lib()
                    println(l.dbl(5))
                }
                """);
        CompilationResult r = compile(List.of(main, dir.resolve("Lib.kf")), tmp.resolve("out"), dir);
        assertTrue(r.success(), "sem kof.toml o comportamento atual deve se manter: "
                + r.diagnostics().getDiagnostics());
    }

    @Test
    void projectRootDiscoveredAutomatically(@TempDir Path tmp) throws IOException {
        // sem passar root explicitamente: compileSources deve descobrir via kof.toml
        Path root = tmp.resolve("proj");
        Files.createDirectories(root.resolve("src"));
        Files.createDirectories(root.resolve("shared"));
        Files.writeString(root.resolve("kof.toml"), "[project]\nname = \"proj\"\n");
        Files.writeString(root.resolve("shared/Validation.kf"), """
                package shared

                Int min(Int a, Int b) { return if (a < b) a else b }
                """);
        Path main = root.resolve("src/main.kf");
        Files.writeString(main, """
                import shared.Validation

                main() {
                    println(min(3, 7))
                }
                """);
        CompilationResult r = driver.compileSources(List.of(main), tmp.resolve("out"), Target.JVM);
        assertTrue(r.success(), "raiz do projeto deve ser descoberta automaticamente: "
                + r.diagnostics().getDiagnostics());
    }
}
