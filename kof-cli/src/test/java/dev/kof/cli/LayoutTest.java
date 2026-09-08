package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F3 (plataforma): detecção do layout de aplicação full-stack
 * (APPLICATION_MODEL P6): src/ = backend, src/web/ = frontend,
 * src/static/ = estáticos. Aditivo — sem web/ com .kf é monólito.
 */
class LayoutTest {

    @Test
    void monolith_hasNoFrontend(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("Main.kf"), "main() { }");
        KofCliSupport.Layout l = KofCliSupport.detectLayout(dir);
        assertFalse(l.fullStack());
        assertNull(l.frontendDir());
        assertEquals(dir, l.backendDir());
    }

    @Test
    void fullstack_layout_detected(@TempDir Path dir) throws IOException {
        write(dir, "src/Main.kf", "main() { }");
        write(dir, "src/web/Index.kf", "main() { }");
        write(dir, "src/static/style.css", "body{}");
        KofCliSupport.Layout l = KofCliSupport.detectLayout(dir);
        assertTrue(l.fullStack());
        assertEquals(dir.resolve("src"), l.backendDir());
        assertEquals(dir.resolve("src/web"), l.frontendDir());
        assertEquals(dir.resolve("src/static"), l.staticDir());
    }

    @Test
    void webDirWithoutKof_isNotFrontend(@TempDir Path dir) throws IOException {
        // web/ com só um css não é um módulo frontend (sem .kf) → monólito
        write(dir, "Main.kf", "main() { }");
        write(dir, "web/readme.txt", "x");
        KofCliSupport.Layout l = KofCliSupport.detectLayout(dir);
        assertFalse(l.fullStack());
    }

    @Test
    void flatWebDir_atProjectRoot(@TempDir Path dir) throws IOException {
        // projeto sem src/: web/ direto na raiz também conta
        write(dir, "Main.kf", "main() { }");
        write(dir, "web/Index.kf", "main() { }");
        KofCliSupport.Layout l = KofCliSupport.detectLayout(dir);
        assertTrue(l.fullStack());
        assertEquals(dir, l.backendDir());
        assertEquals(dir.resolve("web"), l.frontendDir());
    }

    private static void write(Path dir, String rel, String content) throws IOException {
        Path f = dir.resolve(rel);
        Files.createDirectories(f.getParent());
        Files.writeString(f, content);
    }

    @Test
    void copyTree_preservesStructure(@TempDir Path a, @TempDir Path b) throws IOException {
        Path src = a.resolve("static");
        Files.createDirectories(src.resolve("css"));
        Files.writeString(src.resolve("a.css"), ".a{}");
        Files.writeString(src.resolve("css/b.css"), ".b{}");
        int n = KofCliSupport.copyTree(src, b.resolve("out"));
        assertEquals(2, n);
        assertEquals(".a{}", Files.readString(b.resolve("out/a.css")));
        assertEquals(".b{}", Files.readString(b.resolve("out/css/b.css")));
    }
}
