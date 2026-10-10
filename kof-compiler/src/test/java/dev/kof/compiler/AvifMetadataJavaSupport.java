package dev.kof.compiler;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.kof.compiler.AvifMetadataSupport.be16;
import static dev.kof.compiler.AvifMetadataSupport.be32;
import static dev.kof.compiler.AvifMetadataSupport.boxEnd;
import static dev.kof.compiler.AvifMetadataSupport.findBox;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SECOND, independent reader for AVIF slice 1/2j: it parses the box
 * grammar directly in plain Java and resolves the PRIMARY item's properties
 * through {@code ipma} (ISO 14496-12 §8.11.4) and the alpha association
 * through {@code iref/auxl} + the {@code auxC} URN (AVIF §4.1). Split from
 * {@link AvifMetadataSupport} by the test-hygiene ratchet (precedent:
 * {@code AvifMetaJavaSupport}).
 */
final class AvifMetadataJavaSupport {

    private AvifMetadataJavaSupport() {
    }

    static String javaFacts(Path file) throws Exception {
        byte[] b = Files.readAllBytes(file);
        int meta = findBox(b, 0, b.length, "meta");
        assertTrue(meta >= 0, "meta");
        int metaEnd = boxEnd(b, meta);
        int pitm = be16(b, findBox(b, meta + 12, metaEnd, "pitm") + 12);
        int iinf = findBox(b, meta + 12, metaEnd, "iinf");
        int items = be16(b, iinf + 12);
        int iprpAt = findBox(b, meta + 12, metaEnd, "iprp");
        int ipcoAt = findBox(b, iprpAt + 8, boxEnd(b, iprpAt), "ipco");
        int ipmaAt = findBox(b, iprpAt + 8, boxEnd(b, iprpAt), "ipma");
        Map<Integer, List<Integer>> assoc = javaIpma(b, ipmaAt);
        int ispe = javaProp(b, ipcoAt, assoc, pitm, "ispe");
        int width = be32(b, ispe + 12);
        int height = be32(b, ispe + 16);
        int av1 = javaProp(b, ipcoAt, assoc, pitm, "av1C");
        assertTrue((b[av1 + 8] & 255) == 0x81, "av1C marker");
        int b1 = b[av1 + 9] & 255, b2 = b[av1 + 10] & 255;
        int profile = b1 >> 5, level = b1 & 31;
        int high = (b2 >> 6) & 1, twelve = (b2 >> 5) & 1, mono = (b2 >> 4) & 1;
        int subX = (b2 >> 3) & 1, subY = (b2 >> 2) & 1, tier = b2 >> 7;
        int depth = (high == 0) ? 8 : (twelve == 1 ? 12 : 10);
        int iref = findBox(b, meta + 12, metaEnd, "iref");
        boolean alpha = javaAlpha(b, iref, ipcoAt, assoc, pitm);
        String brand = new String(b, 8, 4, StandardCharsets.US_ASCII);
        if (!brand.equals("avif") && !brand.equals("avis")) {
            brand = "";
            for (int i = 16; i + 4 <= be32(b, 0); i += 4) {
                String cb = new String(b, i, 4, StandardCharsets.US_ASCII);
                if (cb.equals("avif") || cb.equals("avis")) {
                    brand = cb;
                    break;
                }
            }
        }
        return brand + " " + width + "x" + height
                + " items=" + items + " primary=" + pitm
                + " alpha=" + (alpha ? 1 : 0) + " profile=" + profile
                + " level=" + level + " tier=" + tier + " mono=" + mono
                + " sub=" + subX + "/" + subY + " depth=" + depth + "/" + depth;
    }

    // ipma v0: ver+flags(4) entry_count(4) then per item: id(2) count(1)
    // idx(1)* (the essential high bit is masked off).
    private static Map<Integer, List<Integer>> javaIpma(byte[] b, int at) {
        Map<Integer, List<Integer>> out = new HashMap<>();
        if (at < 0) {
            return out;
        }
        int flags = ((b[at + 9] & 255) << 16) | ((b[at + 10] & 255) << 8) | (b[at + 11] & 255);
        int n = be32(b, at + 12);
        int p = at + 16;
        for (int i = 0; i < n; i++) {
            int id = be16(b, p);
            int count = b[p + 2] & 255;
            p += 3;
            List<Integer> idx = new ArrayList<>();
            for (int j = 0; j < count; j++) {
                int v = b[p++] & 0x7f;
                if ((flags & 1) != 0) {
                    v = (v << 8) | (b[p++] & 255);
                }
                idx.add(v);
            }
            out.put(id, idx);
        }
        return out;
    }

    // The primary item's 1-based property of `type` resolved through ipma (or
    // the unique one in ipco when there is no ipma).
    private static int javaProp(byte[] b, int ipcoAt, Map<Integer, List<Integer>> assoc,
                                int pitm, String type) {
        int ipcoEnd = boxEnd(b, ipcoAt);
        List<Integer> idx = assoc.get(pitm);
        int found = -1, count = 0;
        if (idx != null) {
            for (int i : idx) {
                if (i >= 1) {
                    int box = nthBox(b, ipcoAt + 8, ipcoEnd, i);
                    if (box >= 0 && new String(b, box + 4, 4, StandardCharsets.US_ASCII).equals(type)) {
                        found = box;
                        count++;
                    }
                }
            }
        } else {
            for (int box = ipcoAt + 8; box + 8 <= ipcoEnd; box += be32(b, box)) {
                if (new String(b, box + 4, 4, StandardCharsets.US_ASCII).equals(type)) {
                    found = box;
                    count++;
                }
            }
        }
        assertTrue(count == 1, "exactly one " + type + " for the primary");
        return found;
    }

    private static int nthBox(byte[] b, int from, int to, int n) {
        int p = from, k = 0;
        while (p + 8 <= to) {
            k++;
            if (k == n) {
                return p;
            }
            p += be32(b, p);
        }
        return -1;
    }

    // AVIF 4.1: an `auxl` reference from an aux item to the primary whose auxC
    // URN is the alpha type.
    private static boolean javaAlpha(byte[] b, int iref, int ipcoAt,
                                     Map<Integer, List<Integer>> assoc, int pitm) {
        if (iref < 0) {
            return false;
        }
        int end = boxEnd(b, iref);
        for (int p = iref + 12; p + 12 <= end; p += be32(b, p)) {
            if (!new String(b, p + 4, 4, StandardCharsets.US_ASCII).equals("auxl")) {
                continue;
            }
            int from = be16(b, p + 8);
            int refCount = be16(b, p + 10);
            for (int i = 0; i < refCount; i++) {
                if (be16(b, p + 12 + i * 2) == pitm && javaAuxAlpha(b, ipcoAt, assoc, from)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static final String ALPHA_URN = "urn:mpeg:mpegB:cicp:systems:auxiliary:alpha";

    private static boolean javaAuxAlpha(byte[] b, int ipcoAt,
                                        Map<Integer, List<Integer>> assoc, int id) {
        List<Integer> idx = assoc.get(id);
        if (idx == null) {
            return false;
        }
        int ipcoEnd = boxEnd(b, ipcoAt);
        for (int i : idx) {
            int box = nthBox(b, ipcoAt + 8, ipcoEnd, i);
            if (box >= 0 && new String(b, box + 4, 4, StandardCharsets.US_ASCII).equals("auxC")
                    && box + 12 + ALPHA_URN.length() <= b.length
                    && new String(b, box + 12, ALPHA_URN.length(), StandardCharsets.US_ASCII)
                            .equals(ALPHA_URN)) {
                return true;
            }
        }
        return false;
    }
}
