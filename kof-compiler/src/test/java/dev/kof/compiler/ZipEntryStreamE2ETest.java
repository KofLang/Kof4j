package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * #852 — the streaming entry reader (`ZipReader.open` + `ZipEntryStream.readChunk`)
 * over the RESUMABLE `image.InflateStream`.
 *
 * <p>Oracle: `java.util.zip` writes the archives (the host's bytes are the
 * expected bytes); each entry is read back in chunks and reduced to
 * {@code length + polynomial hash + chunk count}, so one line proves the whole
 * entry byte for byte. The chunk sizes are chosen to make a DEFLATE back-reference
 * and a STORED block straddle a `readChunk()` boundary (the case a resumable
 * decoder exists for). The malformed shapes are hand-written bit streams: no
 * real compressor emits them, so a hand-built oracle is the only honest one.
 * Every face runs on the JVM AND the Script interpreter and the text must match.
 */
class ZipEntryStreamE2ETest {

    @TempDir Path tmp;

    private final ArchiveKofRunner kof = new ArchiveKofRunner();

    private static final int NO_LIMIT = 100_000_000;

    // --- oracle fixtures -------------------------------------------------------

    private static byte[] words(int bytes, long seed) {
        String[] vocabulary = {"alfa", "beta", "gama", "delta", "kof", "zip", "inflate", "stream", "chunk", "janela"};
        Random random = new Random(seed);
        StringBuilder sb = new StringBuilder();
        while (sb.length() < bytes) {
            sb.append(vocabulary[random.nextInt(vocabulary.length)]).append(' ');
        }
        return sb.substring(0, bytes).getBytes(StandardCharsets.UTF_8);
    }

    private static final byte[] FIXED = "hello hello hello hello".getBytes(StandardCharsets.UTF_8);
    private static final byte[] DYNAMIC = words(12_000, 7);
    private static final byte[] STORED_BLOCKS = words(70_000, 11);       // > 65535: two stored DEFLATE blocks
    private static final byte[] PLAIN = words(5_000, 13);

    /** The five entry kinds the reader must handle, written by the JDK. */
    private static Path contentFixture(Path dir) throws Exception {
        Path zip = dir.resolve("content.zip");
        try (OutputStream raw = Files.newOutputStream(zip); ZipOutputStream out = new ZipOutputStream(raw)) {
            deflated(out, "fixed.txt", FIXED, Deflater.DEFAULT_COMPRESSION);
            deflated(out, "dynamic.txt", DYNAMIC, Deflater.DEFAULT_COMPRESSION);
            deflated(out, "stored-blocks.txt", STORED_BLOCKS, Deflater.NO_COMPRESSION);   // BTYPE 0 inside DEFLATE
            ZipEntry plain = new ZipEntry("plain.bin");
            plain.setMethod(ZipEntry.STORED);
            plain.setSize(PLAIN.length);
            plain.setCompressedSize(PLAIN.length);
            plain.setCrc(ArchiveFixtureSupport.crc32(PLAIN));
            out.putNextEntry(plain);
            out.write(PLAIN);
            out.closeEntry();
            ZipEntry empty = new ZipEntry("empty.txt");
            empty.setMethod(ZipEntry.STORED);
            empty.setSize(0);
            empty.setCompressedSize(0);
            empty.setCrc(0);
            out.putNextEntry(empty);
            out.closeEntry();
            deflated(out, "empty-d.txt", new byte[0], Deflater.DEFAULT_COMPRESSION);
        }
        return zip;
    }

    private static void deflated(ZipOutputStream out, String name, byte[] data, int level) throws Exception {
        out.setLevel(level);
        out.putNextEntry(new ZipEntry(name));
        out.write(data);
        out.closeEntry();
    }

    /** BTYPE of the first DEFLATE block of `name` — so the fixture PROVES which block kinds it covers. */
    private static int firstBlockType(byte[] zip, String name) {
        int at = -1;
        while ((at = ArchiveFixtureSupport.find(zip, ArchiveFixtureSupport.LOCAL, at + 1)) >= 0) {
            int nameLen = (zip[at + 26] & 255) | ((zip[at + 27] & 255) << 8);
            int extraLen = (zip[at + 28] & 255) | ((zip[at + 29] & 255) << 8);
            if (new String(zip, at + 30, nameLen, StandardCharsets.UTF_8).equals(name)) {
                return (zip[at + 30 + nameLen + extraLen] >> 1) & 3;
            }
        }
        throw new AssertionError("no local header named " + name);
    }

    private static String line(String name, int chunk, byte[] data) {
        int chunks = (data.length + chunk - 1) / chunk;
        return name + " chunk=" + chunk + " len=" + data.length + " hash=" + ArchiveFixtureSupport.hash(data)
                + " chunks=" + chunks + " verified=true";
    }

    // --- Kof probes ------------------------------------------------------------

    private static final String DRAIN = ArchiveKofRunner.DRAIN;

    /** The drain helper + a `main` that opens `zip` as `z` and runs `body`. */
    private static String drainProbe(Path zip, String body) {
        return DRAIN + "\nmain() {\n    var z = ZipReader(\"" + zip.toAbsolutePath() + "\")\n" + body + "}\n";
    }

    private Path probe(String name, String body) throws Exception {
        Path root = tmp.resolve(name);
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), body);
        return root;
    }

    // --- faces -----------------------------------------------------------------

    /** Every block kind and both methods, at chunk sizes that split matches and stored blocks. */
    @Test
    void streamsEveryBlockKindByteForByteAcrossChunkBoundaries() throws Exception {
        Path zip = contentFixture(tmp);
        byte[] bytes = Files.readAllBytes(zip);
        assertEquals(1, firstBlockType(bytes, "fixed.txt"), "the fixture must cover a FIXED-Huffman block");
        assertEquals(2, firstBlockType(bytes, "dynamic.txt"), "the fixture must cover a DYNAMIC-Huffman block");
        assertEquals(0, firstBlockType(bytes, "stored-blocks.txt"), "the fixture must cover a STORED block");

        String main = drainProbe(zip, """
                    println(drainEntry(z, "fixed.txt", 1))
                    println(drainEntry(z, "fixed.txt", 8192))
                    println(drainEntry(z, "dynamic.txt", 7))
                    println(drainEntry(z, "dynamic.txt", 8192))
                    println(drainEntry(z, "stored-blocks.txt", 4093))
                    println(drainEntry(z, "stored-blocks.txt", 8192))
                    println(drainEntry(z, "plain.bin", 1000))
                    println(drainEntry(z, "plain.bin", 8192))
                    println(drainEntry(z, "empty.txt", 4))
                    println(drainEntry(z, "empty-d.txt", 4))
                """);
        Path root = probe("content", main);

        String expected = String.join("\n",
                line("fixed.txt", 1, FIXED), line("fixed.txt", 8192, FIXED),
                line("dynamic.txt", 7, DYNAMIC), line("dynamic.txt", 8192, DYNAMIC),
                line("stored-blocks.txt", 4093, STORED_BLOCKS), line("stored-blocks.txt", 8192, STORED_BLOCKS),
                line("plain.bin", 1000, PLAIN), line("plain.bin", 8192, PLAIN),
                line("empty.txt", 4, new byte[0]), line("empty-d.txt", 4, new byte[0]));
        assertEquals(expected, kof.jvm(root), "JVM: every block kind, chunked");
        assertEquals(expected, kof.script(root), "Script: the same text");
    }

    /** CRC-32 and size are checked only at the end; stopping early validates NOTHING and says so. */
    @Test
    void verifiedIsTrueOnlyAfterTheNullAndAnEarlyCloseValidatesNothing() throws Exception {
        Path zip = contentFixture(tmp);
        String main = """
                import archive.Zip
                import archive.ZipReader

                main() {
                    var z = ZipReader("%s")
                    var e = z.find("dynamic.txt")
                    if (e != null) {
                        var s = z.open(e, 100000000, 1000)
                        var first = s.readChunk()
                        s.close()
                        println("early: produced=" + s.produced() + " verified=" + s.verified() + " done=" + s.done())
                        try {
                            var again = s.readChunk()
                            println("after close: no error")
                        } catch (String m) {
                            println("after close: " + m)
                        }
                    }
                    var f = z.find("plain.bin")
                    if (f != null) {
                        var s2 = z.open(f, 100000000, 5000)
                        var c1 = s2.readChunk()
                        println("last data chunk: verified=" + s2.verified() + " done=" + s2.done())
                        var c2 = s2.readChunk()
                        println("null: " + (c2 == null) + " verified=" + s2.verified() + " done=" + s2.done())
                        var c3 = s2.readChunk()
                        println("again: " + (c3 == null) + " verified=" + s2.verified())
                    }
                }
                """.formatted(zip.toAbsolutePath());
        Path root = probe("verified", main);
        String expected = String.join("\n",
                "early: produced=1000 verified=false done=false",
                "after close: archive: the stream of 'dynamic.txt' was closed",
                "last data chunk: verified=false done=false",
                "null: true verified=true done=true",
                "again: true verified=true");
        assertEquals(expected, kof.jvm(root), "JVM: verified only after the null");
        assertEquals(expected, kof.script(root), "Script: the same text");
    }

    /** Hand-written bad streams and lying headers: each refusal is the library's own, deterministic text. */
    @Test
    void refusesEveryMalformedShapeWithTheSameTextOnJvmAndScript() throws Exception {
        byte[] hello = "hello deflate".getBytes(StandardCharsets.UTF_8);
        byte[] bigText = words(220, 3);
        byte[] twenty = words(20, 5);
        byte[] cutSource = words(20_000, 17);
        byte[] full = ZipFixtureBuilder.rawDeflate(cutSource);
        byte[] half = java.util.Arrays.copyOf(full, full.length / 2);

        ZipFixtureBuilder b = new ZipFixtureBuilder()
                .addDeclaring("reserved", 8, new byte[] {0x07}, 0, 5)
                .addDeclaring("badnlen", 8, new byte[] {1, 5, 0, 0, 0, 'h', 'e', 'l', 'l', 'o'}, 0, 5)
                .addDeclaring("farDistance", 8, new DeflateBitWriter().bits(1, 1).bits(1, 2).fixedSymbol(97)
                        .fixedSymbol(257).code(4, 5).bits(1, 1).fixedSymbol(256).finish(), 0, 4)
                .addDeclaring("badSymbol", 8, new DeflateBitWriter().bits(1, 1).bits(1, 2).fixedSymbol(286).finish(), 0, 4)
                .addDeclaring("oversub", 8, new DeflateBitWriter().bits(1, 1).bits(2, 2).bits(0, 5).bits(0, 5).bits(0, 4)
                        .bits(1, 3).bits(1, 3).bits(1, 3).bits(1, 3).finish(), 0, 4)
                .addDeclaring("hlit", 8, new DeflateBitWriter().bits(1, 1).bits(2, 2).bits(31, 5).bits(0, 5).bits(0, 4).finish(), 0, 4)
                .addDeclaring("hdist", 8, new DeflateBitWriter().bits(1, 1).bits(2, 2).bits(0, 5).bits(31, 5).bits(0, 4).finish(), 0, 4)
                .addDeclaring("repeat16", 8, new DeflateBitWriter().bits(1, 1).bits(2, 2).bits(0, 5).bits(0, 5).bits(0, 4)
                        .bits(1, 3).bits(1, 3).bits(0, 3).bits(0, 3).code(0, 1).finish(), 0, 4)
                .add("truncated", 8, half, cutSource)
                .addDeclaring("crc", 8, ZipFixtureBuilder.rawDeflate(hello), 12345, hello.length)
                .addDeclaring("undershoot", 8, ZipFixtureBuilder.rawDeflate(twenty), ArchiveFixtureSupport.crc32(twenty), 50)
                .addDeclaring("overshoot", 8, ZipFixtureBuilder.rawDeflate(bigText), ArchiveFixtureSupport.crc32(bigText), 10)
                .addDeclaring("storedCrc", 0, "abcd".getBytes(StandardCharsets.UTF_8), 777, 4);
        Path zip = tmp.resolve("malformed.zip");
        Files.write(zip, b.build());

        String main = drainProbe(zip, """
                    var names = listOf("reserved", "badnlen", "farDistance", "badSymbol", "oversub", "hlit", "hdist",
                        "repeat16", "truncated", "crc", "undershoot", "overshoot", "storedCrc")
                    for (var n in names) {
                        try {
                            println(n + ": " + drainEntry(z, n, 4096))
                        } catch (String m) {
                            println(n + ": " + m)
                        }
                    }
                """);
        Path root = probe("malformed", main);

        String bad = "archive: entry '%s' holds invalid DEFLATE data — ";
        String expected = String.join("\n",
                "reserved: " + bad.formatted("reserved") + "reserved block type 3",
                "badnlen: " + bad.formatted("badnlen") + "stored block length check failed (LEN/NLEN)",
                "farDistance: " + bad.formatted("farDistance") + "a distance reaches before the start of the output",
                "badSymbol: " + bad.formatted("badSymbol") + "invalid length symbol 286",
                "oversub: " + bad.formatted("oversub") + "invalid code-length code (oversubscribed)",
                "hlit: " + bad.formatted("hlit") + "too many length/literal codes (HLIT 288)",
                "hdist: " + bad.formatted("hdist") + "too many distance codes (HDIST 32)",
                "repeat16: " + bad.formatted("repeat16") + "a repeat code with no previous length",
                "truncated: " + bad.formatted("truncated") + "truncated stream (the compressed data ended inside a block)",
                "crc: archive: CRC-32 mismatch for 'crc' (archive 12345, computed " + (int) ArchiveFixtureSupport.crc32(hello) + ")",
                "undershoot: archive: entry 'undershoot' did not inflate to its declared 50 bytes — refused",
                "overshoot: archive: entry 'overshoot' inflates past its declared size of 10 bytes — refused",
                "storedCrc: archive: CRC-32 mismatch for 'storedCrc' (archive 777, computed "
                        + (int) ArchiveFixtureSupport.crc32("abcd".getBytes(StandardCharsets.UTF_8)) + ")");
        assertEquals(expected, kof.jvm(root), "JVM: the refusals");
        assertEquals(expected, kof.script(root), "Script: the same refusals, byte for byte");
    }

    /** The sticky failure: after a refusal every later call raises the same message. */
    @Test
    void aRefusalIsStickyAndOpenRefusesWhatCannotBeStreamed() throws Exception {
        byte[] hello = "hello deflate".getBytes(StandardCharsets.UTF_8);
        Path zip = tmp.resolve("sticky.zip");
        Files.write(zip, new ZipFixtureBuilder()
                .addDeclaring("crc", 8, ZipFixtureBuilder.rawDeflate(hello), 12345, hello.length).build());
        String main = """
                import archive.Zip
                import archive.ZipReader

                main() {
                    var z = ZipReader("%s")
                    var e = z.find("crc")
                    if (e != null) {
                        var s = z.open(e, 100000000, 4)
                        var bytes = 0
                        try {
                            var c = s.readChunk()
                            while (c != null) {
                                bytes = bytes + c.length
                                c = s.readChunk()
                            }
                        } catch (String m) {
                            println("first: " + m)
                        }
                        println("delivered=" + bytes + " verified=" + s.verified())
                        try {
                            var c = s.readChunk()
                            println("second: no error")
                        } catch (String m) {
                            println("second: " + m)
                        }
                    }
                }
                """.formatted(zip.toAbsolutePath());
        Path root = probe("sticky", main);
        String message = "archive: CRC-32 mismatch for 'crc' (archive 12345, computed "
                + (int) ArchiveFixtureSupport.crc32(hello) + ")";
        String expected = String.join("\n", "first: " + message, "delivered=13 verified=false", "second: " + message);
        assertEquals(expected, kof.jvm(root), "JVM: sticky refusal");
        assertEquals(expected, kof.script(root), "Script: sticky refusal");
    }
}
