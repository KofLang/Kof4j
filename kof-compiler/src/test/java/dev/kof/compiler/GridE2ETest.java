package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Grid() vazio: mesma classe do Row()/Box() — construtor sem args criava
 *  um global nu no JS (`let g = Grid()`) — ReferenceError na primeira linha.
 *  Baixa para kof_ui_grid_new com 0 colunas (runtime cai para 1) e lista
 *  vazia; bind anexa filhos. */
class GridE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path source, Path outDir) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "Exit code should be 0, output:\n" + output);
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    @Test
    void emptyGridLowersToRuntimeConstructor(@TempDir Path tempDir) throws IOException {
        String program = """
                main() {
                    var w = Window("T")
                    var g = Grid()
                    g.bind(Label("a"))
                    w.bind(g)
                    w.show()
                    println("done")
                }
                """;
        Path source = tempDir.resolve("grid.kf");
        Files.writeString(source, program);
        String out = runJvm(source, tempDir.resolve("jvm"));
        assertEquals("done", out, "JVM should run the program");
        CompilationResult result = driver.compile(source, tempDir.resolve("js"), Target.JS);
        assertTrue(result.success(), "JS compile should succeed: " + result.diagnostics().getDiagnostics());
        String emitted = Files.readString(tempDir.resolve("js/Default.mjs"));
        assertTrue(emitted.contains("kofUiGridNew"), "empty Grid() must lower to the runtime constructor");
        assertFalse(emitted.contains("= Grid("), "must not emit a bare Grid global (ReferenceError at runtime)");
        String runtime = Files.readString(tempDir.resolve("js/kof-runtime.mjs"));
        assertTrue(runtime.contains("kofUiGridBind"), "grid bind must ship in the runtime bundle");
    }
}
