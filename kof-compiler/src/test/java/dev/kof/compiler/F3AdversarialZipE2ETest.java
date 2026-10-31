package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.Inflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #852 F3 (adversarial corpus, NOT part of the production commit) — two gaps the
 * existing {@code ZipReader*E2ETest}/{@code ZipEntryStreamE2ETest} suites do not
 * exercise: a back-reference at the DEFLATE window's exact maximum distance
 * (32768), and the archive-level truncation guard in {@code ZipEntryStream.stepStored}
 * when the file on disk shrinks AFTER {@code ZipReader} already trusted its
 * central directory. This file is scratch validation only — it lives in an
 * isolated export, never in the shared tree, and proposes no production change.
 */
class F3AdversarialZipE2ETest {

    @TempDir Path tmp;

    private final ArchiveKofRunner kof = new ArchiveKofRunner();

    private static byte[] random(int n, long seed) {
        byte[] b = new byte[n];
        new Random(seed).nextBytes(b);
        return b;
    }

    private Path probe(String name, String body) throws Exception {
        Path root = tmp.resolve(name);
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), body);
        return root;
    }

    /** dsym=29 (fixed distance code, 5 bits) + 13 extra bits of 1 = distBase[29] + 8191 = 24577 + 8191 = 32768,
     *  the largest distance DEFLATE can express — the ring buffer (`Int[32768]`, masked `&32767`) wraps
     *  EXACTLY once. No real compressor is asked to pick this distance on purpose, so the only honest
     *  fixture is hand-built, and only an independent oracle (the JDK's own {@link Inflater}) can tell a
     *  Kof bug from a fixture bug. */
    @Test
    void backreferenceAtTheMaximumWindowDistanceDecodesCorrectly() throws Exception {
        DeflateBitWriter w = new DeflateBitWriter().bits(1, 1).bits(1, 2);     // BFINAL=1, BTYPE=1 (fixed)
        for (int i = 0; i < 32768; i++) {
            w.fixedSymbol(65);                                                  // 32768 literal 'A' bytes
        }
        w.fixedSymbol(257).code(29, 5).bits(8191, 13).fixedSymbol(256);         // length=3, distance=32768, EOB
        byte[] raw = w.finish();

        byte[] expected = new byte[32771];
        Arrays.fill(expected, (byte) 'A');

        Inflater oracle = new Inflater(true);
        oracle.setInput(raw);
        byte[] got = new byte[40_000];
        int n = oracle.inflate(got, 0, got.length);
        assertTrue(oracle.finished(), "the hand-built stream must be valid DEFLATE per the JDK's own decoder");
        assertEquals(expected.length, n, "the JDK oracle's own output length");
        assertEquals(0, Arrays.compare(expected, Arrays.copyOf(got, n)), "the JDK oracle's own bytes");

        Path zip = tmp.resolve("maxdist.zip");
        Files.write(zip, new ZipFixtureBuilder()
                .addDeclaring("maxdist", 8, raw, ArchiveFixtureSupport.crc32(expected), expected.length)
                .build());
        String main = ArchiveKofRunner.DRAIN + "\nmain() {\n"
                + "    var z = ZipReader(\"" + zip.toAbsolutePath() + "\")\n"
                + "    println(drainEntry(z, \"maxdist\", 4096))\n"
                + "}\n";
        Path root = probe("maxdist", main);
        String want = "maxdist chunk=4096 len=" + expected.length + " hash=" + ArchiveFixtureSupport.hash(expected)
                + " chunks=" + ((expected.length + 4095) / 4096) + " verified=true";
        assertEquals(want, kof.jvm(root), "JVM: exact-window-distance backreference");
        assertEquals(want, kof.script(root), "Script: the same");
    }

    /** `ZipReader(path)` trusts the central directory it read at construction; `ZipEntryStream.open`
     *  re-reads the LOCAL header (so a changed header is caught) but a STORED read still trusts the
     *  cached `compressedSize` against the cached `cdOffset`. If the file shrinks strictly INSIDE the
     *  entry's data region after construction, is that a refusal (`stepStored`'s own truncation check)
     *  or a silent short/wrong read? The Kof program truncates the file ITSELF (`writeBytes` back a
     *  shorter prefix it just read with `readRange`) so the race needs no OS-level concurrency. */
    @Test
    void aFileThatShrinksInsideTheEntryDataAfterConstructionIsRefusedNotSilentlyShort() throws Exception {
        byte[] data = random(50_000, 77);
        int dataStart = 30 + "big.bin".length();       // one entry, local header at offset 0, no extra field
        int keep = dataStart + 1000;                   // keep the header; keep 1000 of the 50000 declared bytes
        String want = "caught: archive: truncated data for 'big.bin'";

        // Destructive probe (it truncates its own fixture): a fresh file and a fresh
        // Main.kf per target, so the JVM run and the Script run never share the one
        // file the first run already shrank.
        assertEquals(want, kof.jvm(toctouProbe("toctou-jvm", data, keep)), "JVM: shrinking inside entry data is refused, not short-read silently");
        assertEquals(want, kof.script(toctouProbe("toctou-script", data, keep)), "Script: the same");
    }

    private Path toctouProbe(String name, byte[] data, int keep) throws Exception {
        Path zip = tmp.resolve(name + ".zip");
        try (OutputStream raw = Files.newOutputStream(zip); ZipOutputStream out = new ZipOutputStream(raw)) {
            ZipEntry big = new ZipEntry("big.bin");
            big.setMethod(ZipEntry.STORED);
            big.setSize(data.length);
            big.setCompressedSize(data.length);
            big.setCrc(ArchiveFixtureSupport.crc32(data));
            out.putNextEntry(big);
            out.write(data);
            out.closeEntry();
        }
        String main = """
                import archive.Zip
                import archive.ZipReader

                main() {
                    var z = ZipReader("%1$s")
                    var e = z.find("big.bin")
                    var shrunk = File("%1$s").readRange(0, %2$d)
                    File("%1$s").writeBytes(shrunk)
                    if (e != null) {
                        var s = z.open(e, 100000000, 8192)
                        try {
                            var c = s.readChunk()
                            println("no refusal: len=" + (if (c == null) -1 else c.length))
                        } catch (String m) {
                            println("caught: " + m)
                        }
                    }
                }
                """.formatted(zip.toAbsolutePath(), keep);
        return probe(name, main);
    }
}
