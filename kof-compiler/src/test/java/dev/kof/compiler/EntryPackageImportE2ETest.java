package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §1203 (23/11, app do Énois): o arquivo de ENTRADA vive num pacote
 * (`app/Main.kf`) e importa um IRMÃO (`enois/X`, ao lado de `app/`). O LCA
 * dos pais da fonte é o diretório do irmão, e o import morria em PKG006: a
 * raiz de import nunca é o pacote da entrada — se o LCA é ele, sobe um nível.
 */
class EntryPackageImportE2ETest {

    @Test
    void anEntryInsideAPackageImportsItsSibling(@TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("proj");
        Files.createDirectories(root.resolve("app"));
        Files.createDirectories(root.resolve("enois"));
        Files.writeString(root.resolve("enois/Texts.kf"), """
            package enois

            record Texts(String title)
            """);
        Files.writeString(root.resolve("app/Main.kf"), """
            import enois.Texts

            main() {
                val t = Texts("Comunidades")
                println(t.title())
            }
            """);
        CompilationResult r = new CompilerDriver().compile(
                root.resolve("app/Main.kf"), root.resolve("out"), Target.NATIVE);
        assertTrue(r.success(), () -> r.diagnostics().getDiagnostics().toString());
    }

    @Test
    void theEntryInTheSameDirectoryAsThePackageKeepsThatDirectoryAsRoot(@TempDir Path tempDir) throws IOException {
        // regressão: `Main.kf` ao lado de `enois/` (o layout do harness) já
        // resolvia — o arquivo de entrada NÃO está num subdiretório.
        Files.createDirectories(tempDir.resolve("enois"));
        Files.writeString(tempDir.resolve("enois/Texts.kf"), """
            package enois

            record Texts(String title)
            """);
        Files.writeString(tempDir.resolve("Main.kf"), """
            import enois.Texts

            class Uso {
                static Int n() { return 1 }
            }
            """);
        CompilationResult r = new CompilerDriver().compileSources(
                List.of(tempDir.resolve("Main.kf")), tempDir.resolve("out"), Target.NATIVE);
        assertTrue(r.success(), () -> r.diagnostics().getDiagnostics().toString());
    }
}
