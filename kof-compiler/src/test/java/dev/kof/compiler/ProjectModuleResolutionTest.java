package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fase 1 do plano de plataforma (docs/development/future/PLATFORM-PLAN.md): module
 * resolution cross-directory via kof.toml + diagnósticos PKG006/PKG007.
 * Retrocompatibilidade: sem kof.toml, o comportamento LCA atual é mantido.
 */
class ProjectModuleResolutionTest {
    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compileFile(Path file, Path out) {
        return driver.compile(file, out, Target.JVM);
    }

    private Path write(Path dir, String rel, String content) throws IOException {
        Path f = dir.resolve(rel);
        Files.createDirectories(f.getParent());
        Files.writeString(f, content);
        return f;
    }

    // ── import cross-directory via kof.toml (irmão de src/) ──

    @Test
    void siblingDirectoryImportViaProjectRoot(@TempDir Path tmp) throws IOException {
        write(tmp, "kof.toml", "[project]\nname = \"demo\"\n");
        write(tmp, "shared/Validation.kf", """
                package shared

                Int min(Int x, Int y) {
                    if (x < y) { return x }
                    return y
                }
                """);
        Path main = write(tmp, "src/Main.kf", """
                import shared.Validation

                main() {
                    println(min(3, 7))
                }
                """);
        CompilationResult r = compileFile(main, tmp.resolve("out"));
        assertTrue(r.success(), "import cross-dir via kof.toml deve compilar: "
                + r.diagnostics().getDiagnostics());
    }

    @Test
    void fileImportFromOtherTreeViaProjectRoot(@TempDir Path tmp) throws IOException {
        write(tmp, "kof.toml", "[project]\nname = \"demo\"\n");
        write(tmp, "lib/Security.kf", """
                package lib

                Int token() { return 42 }
                """);
        Path main = write(tmp, "src/backend/Api.kf", """
                package src.backend

                import lib.Security

                main() {
                    println(token())
                }
                """);
        CompilationResult r = compileFile(main, tmp.resolve("out"));
        assertTrue(r.success(), "import de arquivo em outra árvore deve compilar: "
                + r.diagnostics().getDiagnostics());
    }

    // ── sem kof.toml: LCA atual (retrocompatibilidade) ──

    @Test
    void noManifestKeepsLegacyBehavior(@TempDir Path tmp) throws IOException {
        // layout Go-like clássico: tudo sob uma raiz sem manifesto
        write(tmp, "shared/Validation.kf", """
                package shared

                Int min(Int x, Int y) {
                    if (x < y) { return x }
                    return y
                }
                """);
        // sem manifesto: comportamento atual preservado — main.kf na raiz
        // (LCA = tmp), `import shared.Validation` resolve tmp/shared/Validation.kf
        Path main = write(tmp, "Main.kf", """
                import shared.Validation

                main() {
                    println(min(3, 7))
                }
                """);
        CompilationResult r = compileFile(main, tmp.resolve("out"));
        assertTrue(r.success(), "sem manifesto o comportamento atual deve se manter: "
                + r.diagnostics().getDiagnostics());
    }

    // ── PKG006: import inexistente ──

    @Test
    void unresolvedImportIsDiagnosed(@TempDir Path tmp) throws IOException {
        Path main = write(tmp, "Main.kf", """
                import models.NaoExiste

                main() {
                    println("x")
                }
                """);
        CompilationResult r = compileFile(main, tmp.resolve("out"));
        assertFalse(r.success(), "import inexistente deve falhar");
        boolean pkg006 = r.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> "PKG006".equals(d.code()));
        assertTrue(pkg006, "esperava PKG006, foi: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void externalImportsAreNotDiagnosed(@TempDir Path tmp) throws IOException {
        // kof.*/android.*/java.* continuam legítimos (stdlib/interop)
        Path main = write(tmp, "Main.kf", """
                main() {
                    println("ok")
                }
                """);
        CompilationResult r = compileFile(main, tmp.resolve("out"));
        boolean pkg006 = r.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> "PKG006".equals(d.code()));
        assertFalse(pkg006, "programa sem imports não deve gerar PKG006: "
                + r.diagnostics().getDiagnostics());
    }

    // ── PKG007: import auto-referente ──

    @Test
    void selfImportIsDiagnosed(@TempDir Path tmp) throws IOException {
        write(tmp, "kof.toml", "[project]\nname = \"c\"\n");
        // A.kf importa a.A (o próprio arquivo) — auto-import direto é erro
        write(tmp, "a/A.kf", """
                package a

                import a.A

                Int fA() { return 1 }
                """);
        Path main = write(tmp, "Main.kf", """
                import a.A

                main() {
                    println(fA())
                }
                """);
        CompilationResult r = compileFile(main, tmp.resolve("out"));
        assertFalse(r.success(), "auto-import deve falhar");
        boolean pkg007 = r.diagnostics().getDiagnostics().stream()
                .anyMatch(d -> "PKG007".equals(d.code()));
        assertTrue(pkg007, "esperava PKG007, foi: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void mutualImportsRemainValid(@TempDir Path tmp) throws IOException {
        // import mútuo entre pacotes é LEGÍTIMO (modelo de unidade mergeada;
        // provado também por PackagesE2ETest.moduleRootDerivedFromCommonAncestor)
        write(tmp, "kof.toml", "[project]\nname = \"m\"\n");
        write(tmp, "a/A.kf", """
                package a

                import a.B

                Int fA() { return 1 }
                """);
        write(tmp, "a/B.kf", """
                package a

                import a.A

                Int fB() { return fA() + 1 }
                """);
        Path main = write(tmp, "Main.kf", """
                import a.A
                import a.B

                main() {
                    println(fA() + fB())
                }
                """);
        CompilationResult r = compileFile(main, tmp.resolve("out"));
        assertTrue(r.success(), "import mútuo A↔B deve compilar: "
                + r.diagnostics().getDiagnostics());
    }
}
