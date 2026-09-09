package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GitHub #67 — a descoberta de arquivos-fonte aceita .kf E .kof (extensões
 * oficiais declaradas em editor/kof.tmLanguage.json: fileTypes [kf, kof]).
 * Antes só .kf: `kof build <dir>` respondia "no .kf files found" para um
 * diretório com .kof, enquanto run/check/test/fmt aceitavam.
 */
class KofSourceDiscoveryTest {

    @Test
    void collectAcceptsKofAndKf(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("p.kof"), "main() { println(\"ok\") }\n");
        Files.writeString(dir.resolve("q.kf"), "main() { println(\"ok\") }\n");
        Files.writeString(dir.resolve("notes.txt"), "não é fonte\n");
        List<Path> files = KofCliSupport.collect(dir);
        assertEquals(2, files.size(), "esperava p.kof + q.kf, got: " + files);
        assertTrue(files.stream().anyMatch(p -> p.toString().endsWith(".kof")));
        assertTrue(files.stream().anyMatch(p -> p.toString().endsWith(".kf")));
    }

    @Test
    void collectShallowAcceptsKof(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("p.kof"), "main() { println(\"ok\") }\n");
        List<Path> files = KofCliSupport.collectShallow(dir);
        assertEquals(1, files.size());
    }

    @Test
    void caseInsensitiveExtension(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("P.KOF"), "main() { println(\"ok\") }\n");
        List<Path> files = KofCliSupport.collect(dir);
        assertEquals(1, files.size());
    }
}
