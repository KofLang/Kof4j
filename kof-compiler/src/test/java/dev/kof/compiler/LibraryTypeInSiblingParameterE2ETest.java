package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §1207 (23/11, app do Énois): com um tipo de BIBLIOTECA em parâmetros de
 * membros, um IRMÃO do mesmo pacote que deixa de nomear esse tipo num parâmetro
 * é recusado com SEM011 "Undefined variable or type" quando a entrada está num
 * pacote (a raiz de import sobe, §1203). Sem o irmão da biblioteca, o mesmo
 * arquivo compila.
 */
class LibraryTypeInSiblingParameterE2ETest {

    @Test
    void aSiblingMayNameALibraryTypeInAParameter(@TempDir Path tempDir) throws IOException {
        compile(tempDir, true);
    }

    @Test
    void theSameModuleWithoutTheLibrarySiblingStillCompiles(@TempDir Path tempDir) throws IOException {
        compile(tempDir, false);
    }

    private static void compile(Path tempDir, boolean withLibrarySibling) throws IOException {
        Path root = tempDir.resolve("proj");
        Path lib = tempDir.resolve("inst/lib/kof-libs");
        Files.createDirectories(lib.resolve("game"));
        Files.createDirectories(root.resolve("app"));
        Files.createDirectories(root.resolve("enois"));
        Files.writeString(lib.resolve("game/Lib.kf"), """
            package game

            class Lib {
                static Int n() { return 1 }
            }
            """);
        Files.writeString(root.resolve("enois/Uso.kf"), """
            package enois

            import game.Lib

            class Uso {
                static Int m(Lib x) { return x.n() }
            }
            """);
        if (withLibrarySibling) {
            Files.createDirectories(lib.resolve("enois"));
            Files.writeString(lib.resolve("enois/Aux.kf"), """
                package enois

                import game.Lib

                class Aux {
                    static Int k(Lib w) { return 2 }
                }
                """);
        }
        Files.writeString(root.resolve("app/Main.kf"), """
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
