package dev.kof.compiler;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

/**
 * Independent data source for AVIF slice 3n (AV1 block-size &amp; partition
 * descriptor, {@code libs/image/Av1Block.kf}). The Kof module carries the §9.3
 * conversion tables the partition walk and the intra mode-info reader index by
 * block size ({@code Num_4x4_Blocks_Wide/High}, {@code Mi_Width/Height_Log2},
 * {@code Block_Width/Height}, {@code Size_Group}, {@code Num_Pels_Log2},
 * {@code Max_Tx_Size_Rect}, {@code Max_Tx_Size}, {@code Partition_Subsize}) and
 * the partition-symbol CDF selection the recursive {@code decode_partition}
 * performs ({@code av1PartitionCtx}, the 2-symbol {@code split_or_horz}/
 * {@code split_or_vert} gathered CDFs).
 *
 * <p>The golden is derived from libaom: the tables from
 * {@code av1/common/common_data.h} ({@code subsize_lookup} expanded to the spec
 * {@code Partition_Subsize} shape), cross-checked value-for-value against the
 * spec §10 tables; the gathered CDFs from the verbatim libaom
 * {@code partition_gather_horz_alike}/{@code partition_gather_vert_alike} +
 * {@code cdf_element_prob} over libaom's {@code default_partition_cdf},
 * normalised to the spec forward shape ({@code [prob, 32768, 0]}). The dump is
 * gzip-compressed and base64-encoded as {@code av1_block_golden.txt.b64}.
 */
final class Av1BlockSupport {

    private Av1BlockSupport() {}

    static final String RESOURCE = "/av1_block_golden.txt.b64";

    /** Expected SHA-256 of the decoded golden text (stripped), stale-resource guard. */
    static final String GOLDEN_SHA256 =
        "c14252b3c35df8025aff5a21bbe6a0aa6a81cdfdd458986349e5c389f7730e9c";

    /** The canonical dump the Kof probe must reproduce. */
    static String golden() {
        try (InputStream raw = Av1BlockSupport.class.getResourceAsStream(RESOURCE)) {
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

    /** Kof probe source that dumps every block table row and gathered partition CDF. */
    static String kofProbe() {
        return PROBE;
    }

    private static final String PROBE = """
import image.Av1Block
import image.Av1ModeCdf

main() {
    var bs = 0
    while (bs < 22) {
        var line = "BLK " + bs
        line = line + " " + av1BlockNum4x4Wide(bs)
        line = line + " " + av1BlockNum4x4High(bs)
        line = line + " " + av1BlockWidth(bs)
        line = line + " " + av1BlockHeight(bs)
        line = line + " " + av1BlockSizeGroup(bs)
        line = line + " " + av1BlockNumPelsLog2(bs)
        line = line + " " + av1BlockMiWidthLog2(bs)
        line = line + " " + av1BlockMiHeightLog2(bs)
        line = line + " " + av1BlockMaxTxRect(bs)
        line = line + " " + av1BlockMaxTx(bs)
        println(line)
        bs = bs + 1
    }
    var p = 0
    while (p < 10) {
        bs = 0
        while (bs < 22) {
            println("SUB " + p + " " + bs + " " + av1BlockPartitionSubsize(p, bs))
            bs = bs + 1
        }
        p = p + 1
    }
    var m = Av1ModeCdf()
    var L = 2
    while (L <= 5) {
        var ctx = 0
        while (ctx < 4) {
            var row = m.partitionW(L, ctx)
            var h = av1PartitionGatherHorzAlike(row, ctxBlock(L))
            var v = av1PartitionGatherVertAlike(row, ctxBlock(L))
            println("GH " + L + " " + ctx + " " + h[0])
            println("GV " + L + " " + ctx + " " + v[0])
            ctx = ctx + 1
        }
        L = L + 1
    }
}

Int ctxBlock(Int L) {
    if (L == 2) {
        return 6
    }
    if (L == 3) {
        return 9
    }
    if (L == 4) {
        return 12
    }
    return 15
}
""";
}
