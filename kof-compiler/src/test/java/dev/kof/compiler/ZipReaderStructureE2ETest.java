package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * #852 — `ZipReader`, the incremental reader: what it lists, and what it REFUSES.
 *
 * <p>The listing is compared with the host's `java.util.zip.ZipFile` (the oracle:
 * its entries are the expected entries). The refusal faces are byte surgery on a
 * small archive — a JDK writer only produces honest archives — and each case
 * asserts the library's own message, on the JVM and on the Script interpreter.
 * Every patch is by an offset computed from the archive itself (never a literal),
 * because a wrong offset is exactly how a refusal test reads as a false green.
 */
class ZipReaderStructureE2ETest {

    @TempDir Path tmp;

    private final ArchiveKofRunner kof = new ArchiveKofRunner();

    private static final byte[] ALFA = "alfa".getBytes(StandardCharsets.UTF_8);
    private static final byte[] BRAVO = "bravo bravo bravo bravo".getBytes(StandardCharsets.UTF_8);

    /** One STORED entry (`a.txt`) — the base for the header surgery. */
    private static byte[] single() {
        return new ZipFixtureBuilder().add("a.txt", 0, ALFA, ALFA).build();
    }

    /** Two entries, one STORED and one DEFLATED — the healthy archive. */
    private static ZipFixtureBuilder healthyBuilder() {
        return new ZipFixtureBuilder().add("a.txt", 0, ALFA, ALFA).add("b.txt", 8, ZipFixtureBuilder.rawDeflate(BRAVO), BRAVO);
    }

    private static int u16(byte[] b, int at) {
        return (b[at] & 255) | ((b[at + 1] & 255) << 8);
    }

    private static long u32(byte[] b, int at) {
        return (b[at] & 255L) | ((b[at + 1] & 255L) << 8) | ((b[at + 2] & 255L) << 16) | ((b[at + 3] & 255L) << 24);
    }

    private Path probeRoot(String name, String body) throws Exception {
        Path root = tmp.resolve(name);
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), body);
        return root;
    }

    // --- listing --------------------------------------------------------------

    /** The listing agrees with the JDK entry by entry: names, methods, sizes, CRC-32; data descriptors included. */
    @Test
    void listsExactlyWhatTheJdkListsIncludingDescriptorsAndDirectories() throws Exception {
        Path zip = tmp.resolve("lote.zip");
        try (OutputStream raw = Files.newOutputStream(zip); ZipOutputStream out = new ZipOutputStream(raw)) {
            out.setComment("lote de teste");
            ZipEntry stored = new ZipEntry("alfa.txt");
            stored.setMethod(ZipEntry.STORED);
            stored.setSize(ALFA.length);
            stored.setCompressedSize(ALFA.length);
            stored.setCrc(ArchiveFixtureSupport.crc32(ALFA));
            out.putNextEntry(stored);
            out.write(ALFA);
            out.closeEntry();
            out.putNextEntry(new ZipEntry("dir/"));                 // a directory entry
            out.closeEntry();
            out.putNextEntry(new ZipEntry("dir/béta.txt"));    // DEFLATED + data descriptor, UTF-8 name
            out.write(BRAVO);
            out.closeEntry();
        }
        List<String> expected = new ArrayList<>();
        try (ZipFile jdk = new ZipFile(zip.toFile())) {
            jdk.stream().forEach(e -> expected.add(e.getName() + "|" + e.getMethod() + "|" + e.getCompressedSize()
                    + "|" + e.getSize() + "|" + (int) e.getCrc()));
        }
        String main = """
                import archive.Zip
                import archive.ZipReader

                main() {
                    var z = ZipReader("%s")
                    for (var e in z.entries) {
                        println(e.name + "|" + e.method + "|" + e.compressedSize + "|" + e.uncompressedSize + "|" + e.crc32)
                    }
                }
                """.formatted(zip.toAbsolutePath());
        Path root = probeRoot("listing", main);
        String want = String.join("\n", expected);
        assertEquals(want, kof.jvm(root), "JVM: the listing = the JDK's");
        assertEquals(want, kof.script(root), "Script: the same listing");
    }

    /** Names follow bit 11: UTF-8 when set, CP437 when clear — each against the JDK's own writer. */
    @Test
    void namesFollowTheUtf8FlagAndCp437ForLegacyArchives() throws Exception {
        String accented = "café.txt";
        Path cp437 = tmp.resolve("cp437.zip");
        try (OutputStream raw = Files.newOutputStream(cp437);
             ZipOutputStream out = new ZipOutputStream(raw, Charset.forName("IBM437"))) {
            out.putNextEntry(new ZipEntry(accented));
            out.write(ALFA);
            out.closeEntry();
        }
        Path utf8 = tmp.resolve("utf8.zip");
        try (OutputStream raw = Files.newOutputStream(utf8); ZipOutputStream out = new ZipOutputStream(raw)) {
            out.putNextEntry(new ZipEntry(accented));
            out.write(ALFA);
            out.closeEntry();
        }
        String main = """
                import archive.Zip
                import archive.ZipReader

                main() {
                    for (var p in listOf("%s", "%s")) {
                        var z = ZipReader(p)
                        for (var e in z.entries) {
                            println(e.name + "|" + (e.flags & 2048))
                        }
                    }
                }
                """.formatted(cp437.toAbsolutePath(), utf8.toAbsolutePath());
        Path root = probeRoot("names", main);
        String want = accented + "|0\n" + accented + "|2048";
        assertEquals(want, kof.jvm(root), "JVM: CP437 then UTF-8");
        assertEquals(want, kof.script(root), "Script: the same");
    }

    // --- archive-level refusals -------------------------------------------------

    private static String comment(byte[] filler, int n) {
        return new String(filler, 0, n, StandardCharsets.ISO_8859_1);
    }

    /** The End Of Central Directory and the central directory: every shape that must not be trusted. */
    @Test
    void refusesEveryBrokenArchiveShapeWithTheLibrarysOwnText() throws Exception {
        byte[] h = healthyBuilder().build();
        int eocd = ZipFixtureBuilder.eocdAt(h);
        int cdOffset = (int) u32(h, eocd + 16);
        int cd0 = ZipFixtureBuilder.centralAt(h, 0);
        int secondRecord = 46 + "b.txt".length();

        Map<String, byte[]> cases = new LinkedHashMap<>();
        Map<String, String> want = new LinkedHashMap<>();
        cases.put("healthy", h);
        want.put("healthy", "ok entries=2");

        cases.put("emptyArchive", new ZipFixtureBuilder().build());          // an EOCD and nothing else
        want.put("emptyArchive", "ok entries=0");

        byte[] maxComment = healthyBuilder().comment(new byte[65535]).build();
        cases.put("maxComment", maxComment);
        want.put("maxComment", "ok entries=2");

        byte[] fake = new byte[22];                                  // a decoy EOCD whose own comment length does not fit
        ArchiveFixtureSupport.putU32(fake, 0, ArchiveFixtureSupport.EOCD);
        ArchiveFixtureSupport.putU16(fake, 20, 5);
        byte[] decoyComment = new byte[32];
        System.arraycopy(fake, 0, decoyComment, 0, 22);
        cases.put("decoyInComment", healthyBuilder().comment(decoyComment).build());
        want.put("decoyInComment", "ok entries=2");

        byte[] ambiguous = new byte[27];                              // a decoy that DOES fit the end of the file
        byte[] fits = new byte[22];
        ArchiveFixtureSupport.putU32(fits, 0, ArchiveFixtureSupport.EOCD);
        System.arraycopy(fits, 0, ambiguous, 5, 22);
        cases.put("ambiguous", healthyBuilder().comment(ambiguous).build());
        want.put("ambiguous", "archive: ambiguous end of central directory (two candidates fit the end of the file)");

        String notFound = "archive: end of central directory not found (not a ZIP archive?)";
        cases.put("notZip", new byte[100]);
        want.put("notZip", notFound);
        cases.put("tooShort", new byte[10]);
        want.put("tooShort", notFound);
        cases.put("cutEocd", Arrays.copyOf(h, h.length - 10));
        want.put("cutEocd", notFound);

        byte[] zip64Total = h.clone();
        ArchiveFixtureSupport.putU16(zip64Total, eocd + 10, 0xFFFF);
        cases.put("zip64Total", zip64Total);
        want.put("zip64Total", "archive: ZIP64 sentinel in the end of central directory — ZIP64 is not supported");

        ByteArrayOutputStream withLocator = new ByteArrayOutputStream();
        withLocator.write(h, 0, eocd);
        byte[] locator = new byte[20];
        ArchiveFixtureSupport.putU32(locator, 0, 0x07064b50);
        withLocator.writeBytes(locator);
        withLocator.write(h, eocd, h.length - eocd);
        cases.put("zip64Locator", withLocator.toByteArray());
        want.put("zip64Locator", "archive: ZIP64 end-of-central-directory locator found — ZIP64 is not supported");

        byte[] multiDisk = h.clone();
        ArchiveFixtureSupport.putU16(multiDisk, eocd + 4, 1);
        cases.put("multiDisk", multiDisk);
        want.put("multiDisk", "archive: multi-disk archives are not supported");

        byte[] cdOutside = h.clone();
        ArchiveFixtureSupport.putU32(cdOutside, eocd + 16, 0x90000000L);   // > 2^31: must not wrap into range
        cases.put("cdOutside", cdOutside);
        want.put("cdOutside", "archive: the central directory lies outside the file (truncated?)");

        byte[] tooSmall = h.clone();
        ArchiveFixtureSupport.putU16(tooSmall, eocd + 8, 3);
        ArchiveFixtureSupport.putU16(tooSmall, eocd + 10, 3);
        cases.put("cdTooSmall", tooSmall);
        want.put("cdTooSmall", "archive: the central directory is too small for its 3 entries");

        byte[] unaccounted = h.clone();
        ArchiveFixtureSupport.putU16(unaccounted, eocd + 8, 1);
        ArchiveFixtureSupport.putU16(unaccounted, eocd + 10, 1);
        cases.put("cdUnaccounted", unaccounted);
        want.put("cdUnaccounted", "archive: the central directory holds " + secondRecord
                + " bytes its entries do not account for");

        byte[] badSig = h.clone();
        ArchiveFixtureSupport.putU32(badSig, cd0, 0x12345678);
        cases.put("badCdSignature", badSig);
        want.put("badCdSignature", "archive: bad central directory entry signature at " + cdOffset);

        byte[] entryZip64 = h.clone();
        ArchiveFixtureSupport.putU32(entryZip64, cd0 + 20, 0xFFFFFFFFL);
        cases.put("entryZip64", entryZip64);
        want.put("entryZip64", "archive: entry 'a.txt' carries a ZIP64 sentinel — ZIP64 is not supported");

        byte[] entryHuge = h.clone();
        ArchiveFixtureSupport.putU32(entryHuge, cd0 + 24, 0x80000000L);
        cases.put("entryHuge", entryHuge);
        want.put("entryHuge", "archive: entry 'a.txt' reaches past 2 GiB — not supported");

        byte[] cut = h.clone();                                      // b.txt: UTF-8 flag set, last name byte = a 2-byte lead
        int cd1 = ZipFixtureBuilder.centralAt(h, 1);
        ArchiveFixtureSupport.putU16(cut, cd1 + 8, 2048);
        cut[cd1 + 46 + "b.txt".length() - 1] = (byte) 0xC3;
        cases.put("utf8NameCut", cut);
        want.put("utf8NameCut", "archive: the name of the central directory entry at " + cd1
                + " is cut inside a UTF-8 sequence");

        byte[] runsPast = h.clone();
        ArchiveFixtureSupport.putU16(runsPast, cd0 + 28, 60000);
        cases.put("recordRunsPast", runsPast);
        want.put("recordRunsPast", "archive: the central directory entry at " + cdOffset
                + " runs past the end of the directory");

        StringBuilder calls = new StringBuilder();
        for (Map.Entry<String, byte[]> c : cases.entrySet()) {
            Files.write(tmp.resolve(c.getKey() + ".zip"), c.getValue());
            calls.append("    println(\"").append(c.getKey()).append(": \" + tryOpen(\"")
                    .append(tmp.resolve(c.getKey() + ".zip").toAbsolutePath()).append("\"))\n");
        }
        String main = """
                import archive.Zip
                import archive.ZipReader

                String tryOpen(String path) {
                    try {
                        var z = ZipReader(path)
                        return "ok entries=" + z.entries.size()
                    } catch (String m) {
                        return m
                    }
                }

                main() {
                %s
                    try {
                        var z = ZipReader("%s", 1, 1000L)
                        println("limit: ok")
                    } catch (String m) {
                        println("limit: " + m)
                    }
                }
                """.formatted(calls, tmp.resolve("healthy.zip").toAbsolutePath());
        Path root = probeRoot("shapes", main);

        List<String> lines = new ArrayList<>();
        want.forEach((k, v) -> lines.add(k + ": " + v));
        lines.add("limit: archive: the archive declares 2 entries, over the limit of 1");
        String expected = String.join("\n", lines);
        assertEquals(expected, kof.jvm(root), "JVM: every broken shape");
        assertEquals(expected, kof.script(root), "Script: the same refusals");
    }

    // --- entry-level refusals and validate() -------------------------------------

    private static final String ENTRY_PROBE = """
            import archive.Zip
            import archive.ZipReader

            String tryEntry(String path, String name, Int maxBytes) {
                try {
                    var z = ZipReader(path)
                    var e = z.find(name)
                    if (e == null) {
                        return "no entry"
                    }
                    var s = z.open(e, maxBytes)
                    var total = 0
                    var c = s.readChunk()
                    while (c != null) {
                        total = total + c.length
                        c = s.readChunk()
                    }
                    return "ok len=" + total
                } catch (String m) {
                    return m
                }
            }

            String tryValidate(String path) {
                try {
                    var z = ZipReader(path)
                    var checked = z.validate()
                    return "ok=" + checked
                } catch (String m) {
                    return m
                }
            }
            """;

    /** `open()` refuses what cannot be streamed, BEFORE reading any data. */
    @Test
    void openRefusesEncryptedForeignMethodAndLyingHeadersBeforeReadingData() throws Exception {
        byte[] base = single();
        int cd = ZipFixtureBuilder.centralAt(base, 0);
        int local = ZipFixtureBuilder.localAt(base, 0);

        Map<String, byte[]> cases = new LinkedHashMap<>();
        cases.put("ok", base);
        byte[] encrypted = base.clone();
        ArchiveFixtureSupport.putU16(encrypted, cd + 8, 1);
        cases.put("encrypted", encrypted);
        byte[] method9 = base.clone();
        ArchiveFixtureSupport.putU16(method9, cd + 10, 9);
        ArchiveFixtureSupport.putU16(method9, local + 8, 9);
        cases.put("method9", method9);
        byte[] mismatch = base.clone();
        ArchiveFixtureSupport.putU32(mismatch, cd + 24, 5);
        cases.put("storedMismatch", mismatch);
        byte[] outside = base.clone();
        ArchiveFixtureSupport.putU32(outside, cd + 42, 0x7FFFFFF0L);
        cases.put("localOutside", outside);
        byte[] badSig = base.clone();
        ArchiveFixtureSupport.putU32(badSig, local, 0x12345678);
        cases.put("badLocalSignature", badSig);
        byte[] localMethod = base.clone();
        ArchiveFixtureSupport.putU16(localMethod, local + 8, 8);
        cases.put("localMethod", localMethod);
        byte[] past = base.clone();
        ArchiveFixtureSupport.putU32(past, cd + 20, 1000);
        ArchiveFixtureSupport.putU32(past, cd + 24, 1000);
        ArchiveFixtureSupport.putU32(past, local + 18, 1000);
        ArchiveFixtureSupport.putU32(past, local + 22, 1000);
        cases.put("dataPastTheArea", past);

        List<String> lines = new ArrayList<>();
        StringBuilder calls = new StringBuilder();
        String[] want = {
            "ok len=4",
            "archive: entry 'a.txt' is encrypted (flag bit 0) — not supported",
            "archive: entry 'a.txt' uses compression method 9 (only 0 = stored and 8 = deflate are supported)",
            "archive: stored entry 'a.txt' declares 4 compressed and 5 uncompressed bytes — they must match",
            "archive: entry 'a.txt' has no readable local header",
            "archive: bad local header signature for 'a.txt'",
            "archive: local header method 8 disagrees with the central directory (0) for 'a.txt'",
            "archive: entry 'a.txt' data runs past the end of the archive's data area"};
        int i = 0;
        for (Map.Entry<String, byte[]> c : cases.entrySet()) {
            Path file = tmp.resolve(c.getKey() + ".zip");
            Files.write(file, c.getValue());
            calls.append("    println(\"").append(c.getKey()).append(": \" + tryEntry(\"")
                    .append(file.toAbsolutePath()).append("\", \"a.txt\", 100000))\n");
            lines.add(c.getKey() + ": " + want[i++]);
        }
        Path healthy = tmp.resolve("healthy-open.zip");
        byte[] h = healthyBuilder().build();
        Files.write(healthy, h);
        calls.append("    println(\"overLimitStored: \" + tryEntry(\"").append(tmp.resolve("ok.zip").toAbsolutePath())
                .append("\", \"a.txt\", 2))\n");
        calls.append("    println(\"overLimitDeflated: \" + tryEntry(\"").append(healthy.toAbsolutePath())
                .append("\", \"b.txt\", 3))\n");
        lines.add("overLimitStored: archive: entry 'a.txt' holds 4 bytes, over the limit of 2");
        lines.add("overLimitDeflated: archive: entry 'b.txt' declares " + BRAVO.length + " bytes, over the limit of 3");

        Path root = probeRoot("open", ENTRY_PROBE + "\nmain() {\n" + calls + "}\n");
        String expected = String.join("\n", lines);
        assertEquals(expected, kof.jvm(root), "JVM: open refusals");
        assertEquals(expected, kof.script(root), "Script: the same");
    }

    /** `validate()`: local x central consistency (now including the descriptor FLAG) and data overlap. */
    @Test
    void validateRefusesTheStructuralLiesAndToleratesTheLegitDescriptor() throws Exception {
        byte[] ok = single();
        int local = ZipFixtureBuilder.localAt(ok, 0);
        int cd = ZipFixtureBuilder.centralAt(ok, 0);

        Map<String, byte[]> cases = new LinkedHashMap<>();
        cases.put("ok", ok);
        byte[] name = ok.clone();
        name[local + 30] = 'X';
        cases.put("name", name);
        byte[] size = ok.clone();
        ArchiveFixtureSupport.putU32(size, local + 22, 999);
        cases.put("size", size);
        byte[] crc = ok.clone();
        ArchiveFixtureSupport.putU32(crc, local + 14, 12345);
        cases.put("crc", crc);
        cases.put("overlap", duplicatedCentralEntry(ok));
        byte[] descriptor = ok.clone();                         // the LEGITIMATE descriptor: flag in both, local zeroed
        ArchiveFixtureSupport.putU16(descriptor, local + 6, 8);
        ArchiveFixtureSupport.putU16(descriptor, cd + 8, 8);
        ArchiveFixtureSupport.putU32(descriptor, local + 14, 0);
        ArchiveFixtureSupport.putU32(descriptor, local + 18, 0);
        ArchiveFixtureSupport.putU32(descriptor, local + 22, 0);
        cases.put("descriptor", descriptor);
        byte[] onlyCentral = ok.clone();                        // bit 3 only in the central directory: skips the local checks
        ArchiveFixtureSupport.putU16(onlyCentral, cd + 8, 8);
        ArchiveFixtureSupport.putU32(onlyCentral, local + 22, 999);
        cases.put("flagOnlyCentral", onlyCentral);
        byte[] onlyLocal = ok.clone();
        ArchiveFixtureSupport.putU16(onlyLocal, local + 6, 8);
        cases.put("flagOnlyLocal", onlyLocal);
        byte[] localCut = ok.clone();                           // UTF-8 flag in the central entry, local name cut inside a sequence
        ArchiveFixtureSupport.putU16(localCut, cd + 8, 2048);
        localCut[local + 30 + "a.txt".length() - 1] = (byte) 0xC3;
        cases.put("localNameCut", localCut);

        String flagLie = "archive: entry 'a.txt' sets the data-descriptor flag (bit 3) in only one of its headers — inconsistent";
        String[] want = {
            "ok=1",
            "archive: entry 'a.txt' is named 'X.txt' in the local header — inconsistent with the central directory",
            "archive: entry 'a.txt' sizes differ between the local header (4/999) and the central directory (4/4)",
            "archive: entry 'a.txt' CRC-32 differs between the local header (12345) and the central directory ("
                    + (int) ArchiveFixtureSupport.crc32(ALFA) + ")",
            "archive: entries overlap in the file — the data at 35 is inside another entry's range (refused)",
            "ok=1", flagLie, flagLie,
            "archive: entry 'a.txt' has a local name cut inside a UTF-8 sequence"};
        StringBuilder calls = new StringBuilder();
        List<String> lines = new ArrayList<>();
        int i = 0;
        for (Map.Entry<String, byte[]> c : cases.entrySet()) {
            Path file = tmp.resolve("v-" + c.getKey() + ".zip");
            Files.write(file, c.getValue());
            calls.append("    println(\"").append(c.getKey()).append(": \" + tryValidate(\"")
                    .append(file.toAbsolutePath()).append("\"))\n");
            lines.add(c.getKey() + ": " + want[i++]);
        }
        Path root = probeRoot("validate", ENTRY_PROBE + "\nmain() {\n" + calls + "}\n");
        String expected = String.join("\n", lines);
        assertEquals(expected, kof.jvm(root), "JVM: validate");
        assertEquals(expected, kof.script(root), "Script: the same");
    }

    /** The SAME central record twice (same name, same local header): two entries over one data range. */
    private static byte[] duplicatedCentralEntry(byte[] zip) {
        int eocd = ZipFixtureBuilder.eocdAt(zip);
        int cdOffset = (int) u32(zip, eocd + 16);
        int cdSize = (int) u32(zip, eocd + 12);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(zip, 0, cdOffset);
        out.writeBytes(Arrays.copyOfRange(zip, cdOffset, cdOffset + cdSize));
        out.write(zip, cdOffset, zip.length - cdOffset);
        byte[] merged = out.toByteArray();
        int at = merged.length - 22;
        ArchiveFixtureSupport.putU16(merged, at + 8, 2);
        ArchiveFixtureSupport.putU16(merged, at + 10, 2);
        ArchiveFixtureSupport.putU32(merged, at + 12, cdSize * 2L);
        return merged;
    }
}
