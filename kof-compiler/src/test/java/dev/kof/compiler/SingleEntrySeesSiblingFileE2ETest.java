package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §1209 (23/11, revisão do §1203): o layout mais comum do Kof — `src/Main.kf`
 * usando o `src/Shared.kf` SEM import, a convenção Go-like — compila quando a
 * entrada chega COM os vizinhos (`kof run` coleta a pasta). MEDIDO, e serve de
 * trava: a raiz do módulo fica no diretório da entrada; quem sobe é a BUSCA de
 * import (`CompilerImports`, §1203).
 */
class SingleEntrySeesSiblingFileE2ETest {

    @Test
    void aSoloEntryStillSeesTheFilesOfItsOwnDirectory(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(src.resolve("Shared.kf"), """
            class Shared {
                static Int v() { return 7 }
            }
            """);
        Files.writeString(src.resolve("Main.kf"), """
            main() {
                println(Shared.v())
            }
            """);
        // A CLI (`kof run`) compila a PASTA: a entrada mais os vizinhos.
        CompilationResult r = new CompilerDriver().compileSources(
                java.util.List.of(src.resolve("Main.kf"), src.resolve("Shared.kf")),
                tempDir.resolve("out"), Target.NATIVE);
        assertTrue(r.success(), () -> r.diagnostics().getDiagnostics().toString());
    }
}
