package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * #852 Q5 — `ZipReader.kf`/`ZipEntryStream.kf`/`InflateStream.kf` are pure Kof
 * built on `File.readRange`, which has no JS host binding (`IOJS001`, no
 * whole-file fallback — the library's own header says so). That refusal is
 * INHERITED from the generic `File.readRange` case already pinned in
 * {@code DomainGapCodesTest}, but nothing had proven it directly for THIS
 * library — the one case in `libs/file/*` that has an individual pin for every
 * class (see {@code docs/stdlib/kof-file-plan.md}). This closes that asymmetry.
 */
class ZipReaderJsGapE2ETest {

    @TempDir Path tmp;

    private final ArchiveKofRunner kof = new ArchiveKofRunner();

    private Path probe(String body) throws Exception {
        Path root = tmp.resolve("probe-" + System.nanoTime());
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), body);
        return root;
    }

    @Test
    void zipReaderConstructorOnJsIsIojs001() throws Exception {
        // ZipReader(path) calls load() -> pull() -> File.readRange during its own
        // construction, before any entry is ever opened.
        Path root = probe("""
                import archive.ZipReader

                main() {
                    var z = ZipReader("/tmp/kof-zip-probe.zip")
                    println(z.entries.size())
                }
                """);
        kof.jsGap(root, "IOJS001");
    }

    @Test
    void zipEntryStreamReadChunkOnJsIsIojs001() throws Exception {
        // open()/readChunk() reach File.readRange both directly (STORED entries,
        // ZipEntryStream.stepStored) and through InflateStream.refill() (DEFLATE
        // entries) — this proves the refusal reaches both paths, not just
        // construction.
        Path root = probe("""
                import archive.ZipReader

                main() {
                    var z = ZipReader("/tmp/kof-zip-probe.zip")
                    var e = z.find("a")
                    if (e != null) {
                        var s = z.open(e, 1000)
                        println(s.readChunk())
                    }
                }
                """);
        kof.jsGap(root, "IOJS001");
    }
}
