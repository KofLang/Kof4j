package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §1207 (23/11, app do Énois): o módulo tem duas cópias do MESMO arquivo de
 * biblioteca — uma no módulo (o pacote `enois/Uso.kf`, compilado como fonte
 * solta) e outra na biblioteca oficial (`enois/Uso.kf` no `kof-libs`). O tipo
 * que só o arquivo da biblioteca importa (`game.Lib` num parâmetro de
 * `Uso.m`) volta em SEM011 "Undefined variable or type" nos vizinhos, porque a
 * importação da fonte do módulo encerra a busca daquele pacote e a cópia da
 * biblioteca nunca é lida.
 */
class LibraryTypeInSiblingParameterE2ETest {

    @Test
    void aDuplicateLibraryFileDoesNotHideTheLibraryTypeOfItsNeighbour(@TempDir Path tempDir) throws IOException {
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
        String uso = """
            package enois

            import game.Lib

            class Uso {
                static Int m(Lib x) { return x.n() }
            }
            """;
        Files.writeString(root.resolve("enois/Uso.kf"), uso);
        Files.writeString(lib.resolve("enois/Uso.kf"), uso);
        Files.writeString(root.resolve("enois/Aux.kf"), """
            package enois

            class Aux {
                static Int k(Lib w) { return 2 }
            }
            """);
        Files.writeString(root.resolve("app/Main.kf"), """
            import enois.Aux
            import enois.Uso

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
