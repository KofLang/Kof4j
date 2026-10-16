package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Row()/Column() vazios: o construtor sem args criava um global nu no JS
 *  (`let r = Row()`) — ReferenceError na primeira linha, janela morta vazia.
 *  A forma vazia + bind é o idioma de layout (cria vazio, anexa filhos);
 *  ela deve baixar para kof_ui_row_new/kof_ui_column_new com lista vazia. */
class RowColumnE2ETest {

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
    void emptyRowAndColumnLowerToRuntimeConstructors(@TempDir Path tempDir) throws IOException {
        String program = """
                main() {
                    var w = Window("T")
                    var r = Row()
                    var c = Column()
                    r.bind(Label("a"))
                    c.bind(Label("b"))
                    w.bind(r)
                    w.bind(c)
                    w.show()
                    println("done")
                }
                """;
        Path source = tempDir.resolve("rowcol.kf");
        Files.writeString(source, program);
        String out = runJvm(source, tempDir.resolve("jvm"));
        assertEquals("done", out, "JVM should run the program");
        CompilationResult result = driver.compile(source, tempDir.resolve("js"), Target.JS);
        assertTrue(result.success(), "JS compile should succeed: " + result.diagnostics().getDiagnostics());
        String emitted = Files.readString(tempDir.resolve("js/Default.mjs"));
        assertTrue(emitted.contains("kofUiRowNew"), "empty Row() must lower to the runtime constructor");
        assertTrue(emitted.contains("kofUiColumnNew"), "empty Column() must lower to the runtime constructor");
        assertFalse(emitted.contains("= Row("), "must not emit a bare Row global (ReferenceError at runtime)");
        assertFalse(emitted.contains("= Column("), "must not emit a bare Column global (ReferenceError at runtime)");
        String runtime = Files.readString(tempDir.resolve("js/kof-runtime.mjs"));
        assertTrue(runtime.contains("kofUiRowBind"), "row bind must ship in the runtime bundle");
        assertTrue(runtime.contains("kofUiColumnBind"), "column bind must ship in the runtime bundle");
    }
}
