package dev.kof.compiler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

/**
 * Fixture bytes for the archive suites (`ZipLibraryE2ETest`,
 * `GzipLibraryE2ETest`): the little-endian writers, the signature search and
 * the CRC-32 of a host-built archive.
 *
 * <p>Why it lives here: corrupting a fixture BY OFFSET is how the refusal
 * faces are built (a wrong CRC, a lying declared size, a foreign method, an
 * encrypted flag) and the two suites would otherwise carry byte-identical
 * copies — the same consolidation `LibraryInstallSupport` did for the library
 * install pair. Byte-offset patching is also exactly where a wrong offset
 * reads as a false green, so it should be written once and named.
 */
final class ArchiveFixtureSupport {

    /** End Of Central Directory (ZIP). */
    static final int EOCD = 0x06054b50;
    /** Local file header (ZIP). */
    static final int LOCAL = 0x04034b50;
    /** Central directory entry (ZIP). */
    static final int CENTRAL = 0x02014b50;

    private ArchiveFixtureSupport() {
    }

    static byte[] read(Path p) throws Exception {
        return Files.readAllBytes(p);
    }

    /** Writes a little-endian u16 — the ZIP/GZIP byte order throughout. */
    static void putU16(byte[] b, int at, int value) {
        b[at] = (byte) (value & 255);
        b[at + 1] = (byte) ((value >>> 8) & 255);
    }

    /** Writes a little-endian u32. */
    static void putU32(byte[] b, int at, long value) {
        for (int i = 0; i < 4; i++) {
            b[at + i] = (byte) ((value >>> (8 * i)) & 255);
        }
    }

    /** Offset of the FIRST `sig` at or after `from`, or -1. */
    static int find(byte[] b, int sig, int from) {
        for (int i = from; i + 4 <= b.length; i++) {
            if (matches(b, i, sig)) {
                return i;
            }
        }
        return -1;
    }

    /** Offset of the LAST `sig`, or -1. */
    static int rfind(byte[] b, int sig) {
        for (int i = b.length - 4; i >= 0; i--) {
            if (matches(b, i, sig)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean matches(byte[] b, int i, int sig) {
        return (b[i] & 255) == (sig & 255) && (b[i + 1] & 255) == ((sig >>> 8) & 255)
                && (b[i + 2] & 255) == ((sig >>> 16) & 255) && (b[i + 3] & 255) == ((sig >>> 24) & 255);
    }

    /** The host's CRC-32 — the oracle the Kof side must agree with. */
    static long crc32(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data);
        return crc.getValue();
    }
}