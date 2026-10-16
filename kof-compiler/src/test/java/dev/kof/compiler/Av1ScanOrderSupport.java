package dev.kof.compiler;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

/**
 * Independent data source for the AV1 coefficient SCAN ORDER
 * ({@code libs/image/Av1Tx.kf}: {@code av1ScanOrder} / {@code av1DefaultScan} /
 * {@code av1MrowScan} / {@code av1McolScan}). The scan order maps a scan index to
 * a coefficient raster position, so it drives both the level walk's positions and
 * every coefficient context — a wrong table desyncs any block with a non-trivial
 * EOB, which is exactly the open {@code 3bd} symptom.
 *
 * <p>The golden is taken from the AV1 Bitstream &amp; Decoding Process
 * Specification §9.3 ({@code Default_Scan_*}, {@code Mrow_Scan_*},
 * {@code Mcol_Scan_*}), NOT from libaom: libaom's {@code scan.c} tables are the
 * TRANSPOSE of the spec's (libaom stores the coefficient buffer in a different
 * layout), so comparing Kof to libaom's numbers directly reports a false
 * divergence on every entry. Verified 15/10: all 14 spec default scans, and the
 * 9 spec {@code Mrow}/{@code Mcol} scans, equal the Kof generator output.
 *
 * <p>{@code Mrow}/{@code Mcol} are only listed by the spec up to 16x16 (1D
 * transform types are not allowed above 16x16), so those rows are covered for
 * exactly the sizes the spec defines.
 */
final class Av1ScanOrderSupport {

    private Av1ScanOrderSupport() {}

    static final String RESOURCE = "/av1_scan_order_golden.txt.b64";

    /** Expected SHA-256 of the decoded golden text, to catch a stale resource. */
    static final String GOLDEN_SHA256 =
        "5fb984f26d5e5bc4279311a8fe2d545fb46ad517e68075e632bdd53a1ae5b0f6";

    /** The canonical scan dump the Kof probe must reproduce. */
    static String golden() {
        try (InputStream raw = Av1ScanOrderSupport.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) {
                throw new IllegalStateException("missing test resource " + RESOURCE);
            }
            byte[] b64 = raw.readAllBytes();
            StringBuilder sb = new StringBuilder(b64.length);
            for (byte b : b64) {
                char c = (char) (b & 0xFF);
                if (c != '\n' && c != '\r' && c != ' ' && c != '\t') {
                    sb.append(c);
                }
            }
            byte[] gz = Base64.getDecoder().decode(sb.toString());
            try (GZIPInputStream in = new GZIPInputStream(new java.io.ByteArrayInputStream(gz))) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read " + RESOURCE, e);
        }
    }

    /** Kof probe source: the default, mrow and mcol scan for every transform size. */
    static String kofProbe() {
        return PROBE;
    }

    private static final String PROBE = """
import image.Av1Tx

String row(String tag, Int tx, Int[] c) {
    var line = tag + " " + tx
    var i = 0
    while (i < c.size) {
        line = line + " " + c[i]
        i = i + 1
    }
    return line
}

main() {
    // The transform sizes whose Mrow/Mcol scan the spec defines (<= 16x16, plus
    // the two 4x16/16x4). Above 16x16 a 1D transform type is not allowed, so the
    // spec lists no such table and none is required.
    var rc = setOf(0, 1, 2, 5, 6, 7, 8, 13, 14)
    var tx = 0
    while (tx < 19) {
        println(row("D", tx, av1ScanOrder(tx, 0)))
        if (rc.contains(tx)) {
            println(row("R", tx, av1ScanOrder(tx, 10)))
            println(row("C", tx, av1ScanOrder(tx, 11)))
        }
        tx = tx + 1
    }
}
""";
}
