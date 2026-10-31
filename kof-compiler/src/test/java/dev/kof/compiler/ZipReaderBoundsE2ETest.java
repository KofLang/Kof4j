package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #852 — the numbers behind the streaming reader's promises. Nothing here is a
 * claim read off the code: each one is MEASURED on a real archive.
 *
 * <ul>
 *   <li><b>no whole-file read</b> — a 12 MB archive is listed and streamed under
 *       a 40 MB heap. The same archive through the legacy `Zip(path)` (one
 *       `readBytes`, an `Int[]` four times the file) dies with
 *       `OutOfMemoryError` under the SAME heap, so the bound is not vacuous;
 *       `bytesRead()`/`readCalls()` count what the reader asked the file for;</li>
 *   <li><b>the limit STOPS the decoder</b> — an entry that lies about its size,
 *       a consumer that quits early and an exhausted archive budget all leave the
 *       rest of the compressed data unread (`bytesRead()` stays at the first
 *       blocks of a 1.5 MB payload), and no byte above the limit is ever handed
 *       out.</li>
 * </ul>
 */
class ZipReaderBoundsE2ETest {

    @TempDir Path tmp;

    private final ArchiveKofRunner kof = new ArchiveKofRunner();

    private static final int STORED_BYTES = 12_000_000;
    private static final int ZEROS_BYTES = 6_000_000;

    private static byte[] random(int n, long seed) {
        byte[] b = new byte[n];
        new Random(seed).nextBytes(b);
        return b;
    }

    private static byte[] letters(int n, long seed) {
        Random r = new Random(seed);
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) {
            b[i] = (byte) ('a' + r.nextInt(16));
        }
        return b;
    }

    private Path probe(String name, String body) throws Exception {
        Path root = tmp.resolve(name);
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), body);
        return root;
    }

    // --- no whole-file read ----------------------------------------------------

    private static Path bigArchive(Path dir) throws Exception {
        Path zip = dir.resolve("big.zip");
        byte[] stored = random(STORED_BYTES, 42);
        try (OutputStream raw = Files.newOutputStream(zip); ZipOutputStream out = new ZipOutputStream(raw)) {
            out.putNextEntry(new ZipEntry("head.txt"));
            out.write("head".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            ZipEntry big = new ZipEntry("stored-big.bin");
            big.setMethod(ZipEntry.STORED);
            big.setSize(stored.length);
            big.setCompressedSize(stored.length);
            big.setCrc(ArchiveFixtureSupport.crc32(stored));
            out.putNextEntry(big);
            out.write(stored);
            out.closeEntry();
            out.setLevel(Deflater.BEST_COMPRESSION);
            out.putNextEntry(new ZipEntry("zeros.bin"));
            out.write(new byte[ZEROS_BYTES]);
            out.closeEntry();
            out.putNextEntry(new ZipEntry("tail.txt"));
            out.write("tail".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return zip;
    }

    private static final String STATS_DRAIN = """
            import archive.Zip
            import archive.ZipReader

            String drainStats(ZipReader z, String name, Int chunk) {
                var e = z.find(name)
                if (e == null) {
                    return name + " MISSING"
                }
                var s = z.open(e, 100000000, chunk)
                var h = 0
                var len = 0
                var chunks = 0
                var c = s.readChunk()
                while (c != null) {
                    var i = 0
                    while (i < c.length) {
                        h = (h * 31 + c[i]) % 1000003
                        i = i + 1
                    }
                    len = len + c.length
                    chunks = chunks + 1
                    c = s.readChunk()
                }
                return name + " len=" + len + " hash=" + h + " chunks=" + chunks + " verified=" + s.verified()
                    + " streamBytes=" + s.bytesRead() + " streamCalls=" + s.readCalls()
            }
            """;

    @Test
    void anArchiveLargerThanTheHeapIsListedAndStreamedAndTheLegacyReaderIsNot() throws Exception {
        Path zip = bigArchive(tmp);
        long fileSize = Files.size(zip);
        assertTrue(fileSize > 12_000_000, "the archive must dwarf the heap: " + fileSize);
        long zerosCompressed;
        try (ZipFile jdk = new ZipFile(zip.toFile())) {
            zerosCompressed = jdk.getEntry("zeros.bin").getCompressedSize();
        }

        String main = STATS_DRAIN + """

                main() {
                    var z = ZipReader("%s")
                    println("listing entries=" + z.entries.size() + " bytes=" + z.bytesRead() + " calls=" + z.readCalls())
                    println(drainStats(z, "stored-big.bin", 65536))
                    println(drainStats(z, "zeros.bin", 65536))
                    println(drainStats(z, "tail.txt", 4096))
                }
                """.formatted(zip.toAbsolutePath());
        String out = kof.jvm(probe("big", main), "-Xmx40m");

        String[] lines = out.split("\n");
        Matcher listing = Pattern.compile("listing entries=4 bytes=(\\d+) calls=(\\d+)").matcher(lines[0]);
        assertTrue(listing.matches(), "listing line: " + lines[0]);
        long listed = Long.parseLong(listing.group(1));
        assertTrue(listed <= 70_000, "the listing read " + listed + " bytes of a " + fileSize + "-byte archive");
        assertTrue(Integer.parseInt(listing.group(2)) <= 4, "the listing took " + listing.group(2) + " reads");
        byte[] storedBytes = random(STORED_BYTES, 42);
        assertEquals("stored-big.bin len=" + STORED_BYTES + " hash=" + ArchiveFixtureSupport.hash(storedBytes)
                + " chunks=" + ((STORED_BYTES + 65535) / 65536) + " verified=true streamBytes=" + STORED_BYTES
                + " streamCalls=" + ((STORED_BYTES + 65535) / 65536), lines[1], "STORED, under -Xmx40m");
        assertEquals("zeros.bin len=" + ZEROS_BYTES + " hash=" + ArchiveFixtureSupport.hash(new byte[ZEROS_BYTES])
                + " chunks=" + ((ZEROS_BYTES + 65535) / 65536) + " verified=true streamBytes=" + zerosCompressed
                + " streamCalls=" + ((zerosCompressed + 8191) / 8192), lines[2], "DEFLATE, under -Xmx40m");

        // The contrast: the legacy reader loads the whole file as an Int[] — 4 bytes per byte.
        String legacy = """
                import archive.Zip

                main() {
                    var z = Zip("%s")
                    println("legacy entries=" + z.entries.size())
                }
                """.formatted(zip.toAbsolutePath());
        ArchiveKofRunner.Run run = kof.jvmRaw(probe("legacy", legacy), "-Xmx40m");
        assertNotEquals(0, run.exit(), "the legacy reader must NOT survive this heap: " + run.out());
        assertTrue(run.out().contains("OutOfMemoryError"), "expected an OutOfMemoryError: " + run.out());
    }

    // --- the limit stops the decoder ---------------------------------------------

    private Path interruptArchive() throws Exception {
        byte[] text = letters(3_000_000, 5);
        byte[] deflated = ZipFixtureBuilder.rawDeflate(text);
        byte[] raw = random(300_000, 9);
        Path zip = tmp.resolve("interrupt.zip");
        Files.write(zip, new ZipFixtureBuilder()
                .add("letters.txt", 8, deflated, text)
                .addDeclaring("liar.txt", 8, deflated, ArchiveFixtureSupport.crc32(text), 1000)     // declares 1000, inflates 3 MB
                .add("raw.bin", 0, raw, raw)
                .build());
        return zip;
    }

    private String interruptProbe(Path zip) {
        return """
                import archive.Zip
                import archive.ZipReader

                main() {
                    var z = ZipReader("%1$s")
                    var e = z.find("letters.txt")
                    if (e != null) {
                        var s = z.open(e, 4000000, 4096)
                        var c = s.readChunk()
                        while (c != null && s.produced() < 20000) {
                            c = s.readChunk()
                        }
                        s.close()
                        println("early produced=" + s.produced() + " bytesRead=" + s.bytesRead() + " calls=" + s.readCalls()
                            + " verified=" + s.verified() + " csize=" + e.compressedSize)
                    }
                    var l = z.find("liar.txt")
                    if (l != null) {
                        var s2 = z.open(l, 5000, 4096)
                        var first = s2.readChunk()
                        if (first != null) {
                            println("liar first=" + first.length)
                        }
                        try {
                            var second = s2.readChunk()
                            println("liar second: no error")
                        } catch (String m) {
                            println("liar second: " + m)
                        }
                        println("liar produced=" + s2.produced() + " bytesRead=" + s2.bytesRead() + " calls=" + s2.readCalls())
                    }
                    var z2 = ZipReader("%1$s", 100, 10000L)
                    var e2 = z2.find("letters.txt")
                    if (e2 != null) {
                        var s3 = z2.open(e2, 4000000, 4096)
                        var sizes = ""
                        try {
                            var c3 = s3.readChunk()
                            while (c3 != null) {
                                sizes = sizes + c3.length + ","
                                c3 = s3.readChunk()
                            }
                        } catch (String m) {
                            println("budget: " + m)
                        }
                        println("budget chunks=" + sizes + " produced=" + s3.produced())
                    }
                    var z3 = ZipReader("%1$s", 100, 100000L)
                    var e3 = z3.find("raw.bin")
                    if (e3 != null) {
                        var s4 = z3.open(e3, 4000000, 65536)
                        var sizes2 = ""
                        try {
                            var c4 = s4.readChunk()
                            while (c4 != null) {
                                sizes2 = sizes2 + c4.length + ","
                                c4 = s4.readChunk()
                            }
                        } catch (String m) {
                            println("rawbudget: " + m)
                        }
                        println("rawbudget chunks=" + sizes2 + " produced=" + s4.produced())
                    }
                }
                """.formatted(zip.toAbsolutePath());
    }

    @Test
    void theLimitStopsTheDecoderAndNoByteAboveItIsEverHandedOut() throws Exception {
        Path root = probe("interrupt", interruptProbe(interruptArchive()));
        String jvm = kof.jvm(root);

        String[] lines = jvm.split("\n");
        Matcher early = Pattern.compile(
                "early produced=(\\d+) bytesRead=(\\d+) calls=(\\d+) verified=false csize=(\\d+)").matcher(lines[0]);
        assertTrue(early.matches(), "early line: " + lines[0]);
        assertEquals(20480, Long.parseLong(early.group(1)), "five chunks of 4096, then the consumer quit");
        long pulled = Long.parseLong(early.group(2));
        long compressed = Long.parseLong(early.group(4));
        assertTrue(pulled <= 32768, "an early stop pulled " + pulled + " compressed bytes");
        assertTrue(pulled * 20 < compressed, "pulled " + pulled + " of " + compressed + ": the rest must stay unread");

        String rest = String.join("\n", java.util.Arrays.copyOfRange(lines, 1, lines.length));
        assertEquals(String.join("\n",
                "liar first=1000",
                "liar second: archive: entry 'liar.txt' inflates past its declared size of 1000 bytes — refused",
                "liar produced=1000 bytesRead=8192 calls=1",
                "budget: archive: the archive-level byte budget ran out while reading 'letters.txt' — refused",
                "budget chunks=4096,4096,1808, produced=10000",
                "rawbudget: archive: the archive-level byte budget ran out while reading 'raw.bin' — refused",
                "rawbudget chunks=65536,34464, produced=100000"), rest, "JVM: the limits");
        assertEquals(jvm, kof.script(root), "Script: the same numbers and the same text");
    }

    // --- the bound ARGUMENTS themselves (#852 F1) -------------------------------

    private static final String NEGATIVE_BOUNDS_PROBE = """
            import archive.ZipReader

            main() {
                try {
                    var z = ZipReader("/does/not/exist.zip", -1, 100L)
                    println("no error (negative entries)")
                } catch (String m) {
                    println("entries: " + m)
                }
                try {
                    var z2 = ZipReader("/does/not/exist.zip", 0, 100L)
                    println("no error (zero entries)")
                } catch (String m) {
                    println("zero: " + m)
                }
                try {
                    var z3 = ZipReader("/does/not/exist.zip", 10, -1L)
                    println("no error (negative bytes)")
                } catch (String m) {
                    println("bytes: " + m)
                }
            }
            """;

    @Test
    void negativeOrZeroConstructorBoundsRefuseWithTheLibrarysOwnTextBeforeTouchingTheFile() throws Exception {
        // F1: before this fix, maxEntries<=0/maxTotalBytes<0 reached load()/the
        // budget unvalidated and a negative byte budget surfaced as a platform
        // message ("cannot read file") instead of the library's own text. Both
        // limits are now checked before `this.path` is even read — proven here by
        // a path that does not exist: if validation ran after any file access,
        // these would fail with a platform error instead of the messages below.
        Path root = probe("bounds", NEGATIVE_BOUNDS_PROBE);
        String jvm = kof.jvm(root);
        assertEquals(String.join("\n",
                "entries: archive: the entry limit must be positive",
                "zero: archive: the entry limit must be positive",
                "bytes: archive: the byte budget must not be negative"), jvm, "JVM: the library's own text, not a platform message");
        assertEquals(jvm, kof.script(root), "Script: the same text");
    }
}
