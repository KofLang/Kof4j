package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SourceExtensionValidationTest {

    @Test
    void acceptsSupportedExtensions() {
        assertTrue(KofCliSupport.isKofSourceFile(Path.of("Main.kf")));
        assertTrue(KofCliSupport.isKofSourceFile(Path.of("Main.kof")));
        assertTrue(KofCliSupport.isKofSourceFile(Path.of("Script.ks")));
        assertTrue(KofCliSupport.isKofSourceFile(Path.of("/some/path/App.KF")));
    }

    @Test
    void rejectsUnsupportedExtensions() {
        assertFalse(KofCliSupport.isKofSourceFile(Path.of("prog.txt")));
        assertFalse(KofCliSupport.isKofSourceFile(Path.of("script.sh")));
        assertFalse(KofCliSupport.isKofSourceFile(Path.of("code.py")));
        assertFalse(KofCliSupport.isKofSourceFile(Path.of("executable")));
    }

    @Test
    void formatsUnsupportedExtensionDiagnostic(@TempDir Path tmp) throws Exception {
        Path txt = Files.createFile(tmp.resolve("code.txt"));
        Path noExt = Files.createFile(tmp.resolve("binary"));
        Path dir = Files.createDirectory(tmp.resolve("pkg"));

        assertEquals("run: unsupported source extension '.txt' (expected .kf, .kof or .ks)",
                KofCliSupport.unsupportedSourceExtension("run", txt));
        assertEquals("script: unsupported source extension (none) (expected .kf, .kof or .ks)",
                KofCliSupport.unsupportedSourceExtension("script", noExt));
        assertNull(KofCliSupport.unsupportedSourceExtension("check", dir));
    }
}
