package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Spacer() vazio: mesma classe do Row()/Box() — construtor sem args criava
 *  um global nu no JS (`let s = Spacer()`) — ReferenceError na primeira linha.
 *  Baixa para kof_ui_spacer_new com tamanho padrão (flex 1). */
class SpacerE2ETest {

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
    void emptySpacerLowersToRuntimeConstructor(@TempDir Path tempDir) throws IOException {
        String program = """
                main() {
                    var w = Window("T")
                    var s = Spacer()
                    w.bind(s)
                    w.show()
                    println("done")
                }
                """;
        Path source = tempDir.resolve("spacer.kf");
        Files.writeString(source, program);
        String out = runJvm(source, tempDir.resolve("jvm"));
        assertEquals("done", out, "JVM should run the program");
        CompilationResult result = driver.compile(source, tempDir.resolve("js"), Target.JS);
        assertTrue(result.success(), "JS compile should succeed: " + result.diagnostics().getDiagnostics());
        String emitted = Files.readString(tempDir.resolve("js/Default.mjs"));
        assertTrue(emitted.contains("kofUiSpacerNew"), "empty Spacer() must lower to the runtime constructor");
        assertFalse(emitted.contains("= Spacer("), "must not emit a bare Spacer global (ReferenceError at runtime)");
    }
}
