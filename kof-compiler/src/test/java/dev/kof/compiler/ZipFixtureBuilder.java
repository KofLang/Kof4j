package dev.kof.compiler;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Hand-built ZIP archives for the streaming-reader suites. A `ZipOutputStream`
 * only writes honest archives; the refusal faces need entries whose payload is
 * an arbitrary (often malformed) DEFLATE stream and whose headers may lie, so
 * the local header, the data, the central directory and the EOCD are written
 * field by field here. Flags and sizes are written IDENTICALLY in both headers
 * (a consistent archive); each suite then patches the one field it needs by
 * offset, the way `ArchiveFixtureSupport` documents.
 */
final class ZipFixtureBuilder {

    private record Entry(String name, int method, int flags, byte[] payload, long crc, long uncompressed) {
    }

    private final List<Entry> entries = new ArrayList<>();
    private byte[] comment = new byte[0];

    /** An entry whose payload is exactly `payload`; CRC-32 and size are those of `content`. */
    ZipFixtureBuilder add(String name, int method, byte[] payload, byte[] content) {
        return addDeclaring(name, method, payload, ArchiveFixtureSupport.crc32(content), content.length);
    }

    /** An entry that DECLARES `crc` and `uncompressed` regardless of what the payload holds. */
    ZipFixtureBuilder addDeclaring(String name, int method, byte[] payload, long crc, long uncompressed) {
        entries.add(new Entry(name, method, 0, payload, crc, uncompressed));
        return this;
    }

    /** A STORED entry. */
    ZipFixtureBuilder stored(String name, String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        return add(name, 0, data, data);
    }

    /** The EOCD comment (at most 65535 bytes). */
    ZipFixtureBuilder comment(byte[] comment) {
        this.comment = comment;
        return this;
    }

    byte[] build() {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        for (Entry e : entries) {
            offsets.add(body.size());
            byte[] nm = e.name().getBytes(StandardCharsets.UTF_8);
            byte[] local = new byte[30 + nm.length];
            ArchiveFixtureSupport.putU32(local, 0, ArchiveFixtureSupport.LOCAL);
            ArchiveFixtureSupport.putU16(local, 4, 20);
            ArchiveFixtureSupport.putU16(local, 6, e.flags());
            ArchiveFixtureSupport.putU16(local, 8, e.method());
            ArchiveFixtureSupport.putU32(local, 14, e.crc());
            ArchiveFixtureSupport.putU32(local, 18, e.payload().length);
            ArchiveFixtureSupport.putU32(local, 22, e.uncompressed());
            ArchiveFixtureSupport.putU16(local, 26, nm.length);
            System.arraycopy(nm, 0, local, 30, nm.length);
            body.writeBytes(local);
            body.writeBytes(e.payload());
        }
        int cdOffset = body.size();
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            byte[] nm = e.name().getBytes(StandardCharsets.UTF_8);
            byte[] cd = new byte[46 + nm.length];
            ArchiveFixtureSupport.putU32(cd, 0, ArchiveFixtureSupport.CENTRAL);
            ArchiveFixtureSupport.putU16(cd, 4, 20);
            ArchiveFixtureSupport.putU16(cd, 6, 20);
            ArchiveFixtureSupport.putU16(cd, 8, e.flags());
            ArchiveFixtureSupport.putU16(cd, 10, e.method());
            ArchiveFixtureSupport.putU32(cd, 16, e.crc());
            ArchiveFixtureSupport.putU32(cd, 20, e.payload().length);
            ArchiveFixtureSupport.putU32(cd, 24, e.uncompressed());
            ArchiveFixtureSupport.putU16(cd, 28, nm.length);
            ArchiveFixtureSupport.putU32(cd, 42, offsets.get(i));
            System.arraycopy(nm, 0, cd, 46, nm.length);
            body.writeBytes(cd);
        }
        int cdSize = body.size() - cdOffset;
        byte[] eocd = new byte[22];
        ArchiveFixtureSupport.putU32(eocd, 0, ArchiveFixtureSupport.EOCD);
        ArchiveFixtureSupport.putU16(eocd, 8, entries.size());
        ArchiveFixtureSupport.putU16(eocd, 10, entries.size());
        ArchiveFixtureSupport.putU32(eocd, 12, cdSize);
        ArchiveFixtureSupport.putU32(eocd, 16, cdOffset);
        ArchiveFixtureSupport.putU16(eocd, 20, comment.length);
        body.writeBytes(eocd);
        body.writeBytes(comment);
        return body.toByteArray();
    }

    /** RAW DEFLATE (no zlib wrapper) of `data` — what a ZIP method-8 entry carries. */
    static byte[] rawDeflate(byte[] data) {
        java.util.zip.Deflater d = new java.util.zip.Deflater(java.util.zip.Deflater.DEFAULT_COMPRESSION, true);
        d.setInput(data);
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        while (!d.finished()) {
            out.write(buf, 0, d.deflate(buf));
        }
        d.end();
        return out.toByteArray();
    }

    /** Offset of the Nth central-directory record (0-based) in a built archive. */
    static int centralAt(byte[] zip, int n) {
        int at = -1;
        for (int i = 0; i <= n; i++) {
            at = ArchiveFixtureSupport.find(zip, ArchiveFixtureSupport.CENTRAL, at + 1);
        }
        return at;
    }

    /** Offset of the Nth local header (0-based) in a built archive. */
    static int localAt(byte[] zip, int n) {
        int at = -1;
        for (int i = 0; i <= n; i++) {
            at = ArchiveFixtureSupport.find(zip, ArchiveFixtureSupport.LOCAL, at + 1);
        }
        return at;
    }

    /** Offset of the (last) EOCD of a built archive without a comment-borne decoy. */
    static int eocdAt(byte[] zip) {
        return ArchiveFixtureSupport.rfind(zip, ArchiveFixtureSupport.EOCD);
    }
}
