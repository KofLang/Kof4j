package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §1207 (23/11, app do Énois): um arquivo do módulo nomeia num parâmetro um
 * tipo de BIBLIOTECA (`Sdl3Backend b`) sem importar — o mesmo tipo mora na
 * cópia do MESMO arquivo na biblioteca oficial, que o importa. O arquivo da
 * biblioteca nunca é lido (a cópia do módulo vence o import de diretório), então
 * o import dele precisa entrar na fila: é o que a busca fazia só para arquivos
 * ainda não compilados.
 */
class LibraryTypeInSiblingParameterE2ETest {

    @Test
    void aLibraryCopyOfACompiledFileStillContributesItsImports(@TempDir Path tempDir) throws IOException {
        Path root = tempDir.resolve("proj");
        Path lib = tempDir.resolve("inst/lib/kof-libs");
        Files.createDirectories(lib.resolve("game"));
        Files.createDirectories(lib.resolve("enois"));
        Files.createDirectories(root.resolve("app"));
        Files.createDirectories(root.resolve("enois"));
        Files.writeString(lib.resolve("game/Lib.kf"), """
            package game

            class Lib {
                static Int n() { return 1 }
            }
            """);
        String dup = """
            package enois

            import game.Lib

            class Dup {
                static Int m(Lib b) { return b.n() }
            }
            """;
        Files.writeString(root.resolve("enois/Dup.kf"), dup);
        Files.writeString(lib.resolve("enois/Dup.kf"), dup);
        Files.writeString(root.resolve("enois/Esq.kf"), """
            package enois

            class Esq {
                static Int m(Lib w) { return 2 }
            }
            """);
        Files.writeString(root.resolve("app/Main.kf"), """
            import enois.Esq

            main() {
                println("x")
            }
            """);
        String previous = System.getProperty("kof.install.dir");
        System.setProperty("kof.install.dir", tempDir.resolve("inst").toString());
        try {
            CompilationResult r = new CompilerDriver().compileSources(
                    List.of(root.resolve("app/Main.kf")), root.resolve("out"), Target.NATIVE, root);
            assertTrue(r.success(), () -> r.diagnostics().getDiagnostics().toString());
        } finally {
            if (previous == null) System.clearProperty("kof.install.dir");
            else System.setProperty("kof.install.dir", previous);
        }
    }
}
