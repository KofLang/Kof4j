package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Canvas sem regra própria: o flex da janela esticava o canvas na
 *  largura mantendo a proporção — um tabuleiro quadrado sempre estourava a
 *  altura da janela (rolagem; conteúdo some embaixo). O canvas deve renderizar
 *  no tamanho do atributo (comportamento padrão do <canvas>), contido pela
 *  largura disponível. Regra espelhada no shell estático e no tema vivo. */
class CanvasFitE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void canvasKeepsAttributeSizeInBothShells(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("fit.kf");
        Files.writeString(source, """
                main() {
                    var w = Window("T")
                    var c = Canvas(264, 264)
                    w.bind(c)
                    w.show()
                    println("done")
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("js"), Target.JS);
        assertTrue(result.success(), "JS compile should succeed: " + result.diagnostics().getDiagnostics());
        String html = Files.readString(tempDir.resolve("js/index.html"));
        assertTrue(html.contains(".kof-canvas"), "static shell must style the canvas");
        assertTrue(html.contains("align-self"), "static shell must defeat the flex stretch");
        String runtime = Files.readString(tempDir.resolve("js/kof-runtime.mjs"));
        assertTrue(runtime.contains(".kof-canvas"), "live theme must style the canvas");
        assertTrue(runtime.contains("max-width"), "live theme must contain the canvas");
    }
}
