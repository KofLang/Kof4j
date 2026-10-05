package dev.kof.compiler;

import java.util.Base64;

/**
 * Independent data source for AVIF slice 3f (the AV1 dequantization stage,
 * {@code libs/image/Av1Quant.kf}). The Kof module embeds {@code Dc_Qlookup} and
 * {@code Ac_Qlookup} exactly as published in the AV1 spec §7.12.2; this class
 * embeds the same 1536 numbers independently derived from libaom's
 * {@code av1/common/quant_common.c} (the {@code dc_qlookup_*} / {@code ac_qlookup_*}
 * arrays), decoded from big-endian uint16 base64 blobs. It reproduces the
 * dequantization of §7.12.2 step 1 (the non-qmatrix path) for a deterministic
 * quantized-coefficient input across the 19 transform sizes, so {@link #golden()}
 * can be compared value-for-value with the Kof probe.
 */
final class Av1QuantSupport {

    private Av1QuantSupport() {}

    private static final String DC_B64 =
        "AAQACAAIAAkACgALAAwADAANAA4ADwAQABEAEgATABMAFAAVABYAFwAYABkAGgAaABsAHAAdAB4AHwAgACAAIQAiACMAJAAlACYAJgAnACgAKQAqACsAKwAsAC0ALgAvADAAMAAxADIAMwA0ADUANQA2ADcAOAA5ADkAOgA7ADwAPQA+AD4APwBAAEEAQgBCAEMARABFAEYARgBHAEgASQBKAEoASwBMAE0ATgBOAE8AUABRAFEAUgBTAFQAVQBVAFcAWABaAFwAXQBfAGAAYgBjAGUAZgBoAGkAawBsAG4AbwBxAHIAdAB1AHYAeAB5AHsAfQB/AIEAgwCGAIgAigCMAI4AkACSAJQAlgCYAJoAnACeAKEApACmAKkArACuALEAtAC2ALkAuwC+AMAAwwDHAMoAzQDQANMA1gDZANwA3wDiAOYA6QDtAPAA8wD3APoA/QEBAQUBCQENARABFAEYARwBIAEkASgBLAEwATUBOQE9AUIBRgFKAU8BVAFYAV0BYgFnAWwBcQF2AXsBgAGFAYsBkAGWAZsBoQGnAa0BswG5Ab8BxgHNAdMB2wHiAekB8QH5AgECCgISAhsCJQIvAjkCQwJOAloCZgJyAoACjgKcAqwCvALNAuAC8wMHAxwDMwNLA2UDgAOdA7sD3AP+BCIESgRzBKAE0AUCBTgABAAJAAoADQAPABEAFAAWABkAHAAfACIAJQAoACsALwAyADUAOQA8AEAARABHAEsATgBSAFYAWgBdAGEAZQBpAG0AcQB0AHgAfACAAIQAiACMAI8AkwCXAJsAnwCjAKYAqgCuALIAtgC5AL0AwQDFAMgAzADQANQA1wDbAN8A4gDmAOkA7QDxAPQA+AD7AP8BAwEGAQoBDQERARQBGAEbAR8BIgElASkBLAEwATMBNgE6AT0BQQFEAUcBSwFOAVEBVwFeAWQBagFxAXcBfQGDAYoBkAGWAZwBogGoAa4BtAG6AcABxgHMAdIB2AHeAeQB6gHzAfsCBAINAhUCHgImAi8CNwJAAkgCUAJZAmECaQJxAnoChAKPApoCpAKvAroCxALOAtkC4wLtAvcDAgMOAxsDJwMzAz8DTANYA2QDcAN7A4oDmAOlA7MDwQPPA9wD6QP3BAYEFQQlBDQEQgRRBGAEcQSBBJIEogSyBMIE1ATlBPcFCAUaBSsFPgVRBWMFdgWIBZwFsAXEBdgF7AYBBhcGLAZBBlgGbwaGBpwGtQbNBuYG/wcZBzQHTwdsB4kHpgfGB+UIBggoCEsIbwiVCLwI5AkPCTsJagmaCcwKAQo4CnMKsQryCzcLgAvMDB4MdAzQDS8Nlg4CDnYO7w9xD/kQjBEqEc8SgRNBFAoU4wAEAAwAEgAZACEAKQAyADwARgBQAFsAZwBzAH8AjACZAKYAtADCANAA3gDtAPsBCgEZASgBOAFHAVcBZgF2AYYBlQGlAbUBxQHVAeQB9AIEAhQCJAI0AkQCVAJjAnMCgwKTAqICsgLCAtEC4QLwAwADDwMeAy4DPQNMA1sDagN5A4gDlwOmA7UDxAPSA+ED8AP+BA0EGwQpBDgERgRUBGIEcAR/BI0EmwSoBLYExATSBOAE7QT7BQgFFgUjBTEFPgVYBXEFiwWkBb0F1gXvBggGIQY6BlIGawaEBpwGtQbNBuUG/QcWBy4HRgddB3UHjQelB8gH6wgNCDAIUgh1CJcIuQjbCPwJHgk/CWAJggmjCcMJ5AoPCjoKZQqQCroK5AsOCzgLYQuKC7ML3AwEDDcMaQyaDMsM/A0tDV0NjQ29De0OJQ5dDpUOzA8DDzkPbw+lD9oQFxBVEJEQzREJEUQRfxHCEgQSRRKGEscTBxNOE5UT2xQhFGYUqxT3FUIVjRXXFiEWcRbBFxEXYBevGAUYWhivGQQZXxm7GhYacRrTGzYbmBv7HGUc0B07Ha8eIx6YHxYflSAWIKAhLCG7IlQi8SOQJDsk6iWnJmgnLygFKOEpzirCK8os2i4ALzEweTHOMz40vTZZOAc51zu6PcQ/5EIvRKdHPUoFTQZQKVOL";

    private static final String AC_B64 =
        "AAQACAAJAAoACwAMAA0ADgAPABAAEQASABMAFAAVABYAFwAYABkAGgAbABwAHQAeAB8AIAAhACIAIwAkACUAJgAnACgAKQAqACsALAAtAC4ALwAwADEAMgAzADQANQA2ADcAOAA5ADoAOwA8AD0APgA/AEAAQQBCAEMARABFAEYARwBIAEkASgBLAEwATQBOAE8AUABRAFIAUwBUAFUAVgBXAFgAWQBaAFsAXABdAF4AXwBgAGEAYgBjAGQAZQBmAGgAagBsAG4AcAByAHQAdgB4AHoAfAB+AIAAggCEAIYAiACKAIwAjgCQAJIAlACWAJgAmwCeAKEApACnAKoArQCwALMAtgC5ALwAvwDCAMUAyADLAM8A0wDXANsA3wDjAOcA6wDvAPMA9wD7AP8BBAEJAQ4BEwEYAR0BIgEnASwBMQE3AT0BQwFJAU8BVQFbAWEBZwFuAXUBfAGDAYoBkQGYAaABqAGwAbgBwAHIAdEB2gHjAewB9QH+AggCEgIcAiYCMAI7AkYCUQJcAmcCcwJ/AosClwKkArECvgLLAtkC5wL1AwMDEgMhAzADQANQA2ADcQOCA5MDpQO3A8kD3APvBAIEFgQqBD8EVARpBH8ElQSsBMME2wTzBQwFJQU/BVkFdAWPBasFxwXkBgEGHwY9BlwGfAacBr0G3wcBByQABAAJAAsADQAQABIAFQAYABsAHgAhACUAKAAsADAAMwA3ADsAPwBDAEcASwBPAFMAWABcAGAAZABpAG0AcgB2AHoAfwCDAIgAjACRAJUAmgCeAKMAqACsALEAtQC6AL4AwwDHAMwA0ADVANkA3gDiAOcA6wDwAPQA+QD9AQIBBgELAQ8BEwEYARwBIQElASkBLgEyATcBOwE/AUQBSAFMAVEBVQFZAV0BYgFmAWoBbwFzAXcBewGAAYQBiAGMAZEBmQGhAakBsQG5AcEBygHSAdoB4gHqAfIB+gICAgsCEwIbAiMCKwIzAjsCQwJMAlQCXAJoAnQCgAKMApgCpAKwArwCyQLVAuEC7QL5AwUDEQMdAykDOQNJA1kDaQN5A4kDmgOqA7oDygPaA+oD+gQOBCIENgRKBF4EcgSGBJoErgTCBNoE8gUKBSIFOgVSBWoFgwWbBbcF0wXvBgsGJwZDBl8GfwafBr8G3wb/Bx8HQwdnB4sHrwfTB/cIHwhHCG8Ilwi/COsJFwlDCW8JmwnLCfsKKwpbCo8Kwwr3CysLYwubC9MMCwxHDIMMvwz/DT8Nfw3DDgcOSw6TDtsPJA9wD7wQCBBYEKgQ/BFQEaQR/BJUErATDBNsE8wUMBSUFPwVZBXQFjwWrBccF5AYBBh8GPQZcBnwGnAa9Bt8HAQckAAEAA0AEwAbACMALAA2AEAASwBXAGMAcAB+AIsAmgCoALcAxwDWAOYA9wEHARgBKQE6AUsBXQFuAYABkgGkAbYByAHbAe0B/wISAiQCNwJKAlwCbwKCApQCpwK6AswC3wLxAwQDFwMpAzwDTgNhA3QDhgOYA6sDvQPQA+ID9AQGBBkEKwQ9BE8EYQRzBIUElwSpBLsEzQTeBPAFAgUTBSUFNwVIBVoFawV9BY4FoAWxBcIF0wXlBfYGBwYYBikGOwZbBnwGnQa9Bt4G/wcgB0AHYQeCB6IHwwfkCAQIJQhGCGYIhwioCMgI6QkJCSoJSglrCZsJzAn8Ci0KXQqNCr4K7gsfC08LfwuwC+AMEAxBDHEMogziDSINYg2jDeMOIw5kDqQO5A8kD2UPpQ/lEDUQhhDWESYRdhHGEhcSZxK3EwcTZxPIFCgUiBToFUgVqBYJFmkW2RdJF7kYKRiZGQoZehn6Gnoa+ht6G/oceh0LHZseKx67H0sf2yB7IRshuyJbIvwjrCRcJQwlvCZsJywn7CisKWwqPCsMK9wsrS2NLm0vTTAtMR0yDTL9M/00/TX9Nw04HTktOk07bTyOPb4+7kAeQV5CnkPuRT5GjkfuSU5KvkwuTa5PLlC+Uk5T7lWOVz5Y7lquXG5ePmAOYe5jz2W/Z79pv2vPbe9wD3I/";

    private static final int[] DC = decode(DC_B64);
    private static final int[] AC = decode(AC_B64);

    // Tx_Width_Log2 / Tx_Height_Log2, TX_SIZES_ALL order.
    private static final int[] W_LOG2 =
        {2, 3, 4, 5, 6, 2, 3, 3, 4, 4, 5, 5, 6, 2, 4, 3, 5, 4, 6};
    private static final int[] H_LOG2 =
        {2, 3, 4, 5, 6, 3, 2, 4, 3, 5, 4, 6, 5, 4, 2, 5, 3, 6, 4};

    // (txSz, bitDepth, dcQuant, acQuant) cases exercised by the probe.
    private static final int[][] CASES = {
        {0, 8, 4, 4}, {1, 8, 100, 100}, {2, 8, 200, 200}, {3, 8, 140, 140},
        {4, 8, 255, 255}, {5, 8, 32, 32}, {6, 8, 32, 32}, {7, 10, 60, 60},
        {8, 10, 60, 60}, {9, 10, 60, 60}, {10, 10, 60, 60}, {11, 10, 80, 80},
        {12, 10, 80, 80}, {13, 12, 90, 90}, {14, 12, 90, 90}, {15, 12, 120, 120},
        {16, 12, 120, 120}, {17, 12, 150, 150}, {18, 12, 150, 150},
    };

    private static int[] decode(String b64) {
        byte[] raw = Base64.getDecoder().decode(b64);
        int[] out = new int[raw.length / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = ((raw[i * 2] & 0xFF) << 8) | (raw[i * 2 + 1] & 0xFF);
        }
        return out;
    }

    static int dcQ(int bitDepth, int b) {
        int idx = clip3(0, 255, b);
        return DC[((bitDepth - 8) >> 1) * 256 + idx];
    }

    static int acQ(int bitDepth, int b) {
        int idx = clip3(0, 255, b);
        return AC[((bitDepth - 8) >> 1) * 256 + idx];
    }

    static int dqDenom(int txSz) {
        if (txSz == 3 || txSz == 9 || txSz == 10 || txSz == 17 || txSz == 18) return 2;
        if (txSz == 4 || txSz == 11 || txSz == 12) return 4;
        return 1;
    }

    static int[] dequant(int[] quant, int txSz, int bitDepth, int dcQuant, int acQuant) {
        int w = 1 << W_LOG2[txSz];
        int h = 1 << H_LOG2[txSz];
        int tw = Math.min(32, w);
        int th = Math.min(32, h);
        int den = dqDenom(txSz);
        int hi = (1 << (7 + bitDepth)) - 1;
        int lo = -(1 << (7 + bitDepth));
        int[] out = new int[tw * th];
        for (int i = 0; i < th; i++) {
            for (int j = 0; j < tw; j++) {
                int pos = i * tw + j;
                int q = (i == 0 && j == 0) ? dcQuant : acQuant;
                int dq = quant[pos] * q;
                int sign = dq < 0 ? -1 : 1;
                int abs = dq < 0 ? -dq : dq;
                out[pos] = clip3(lo, hi, sign * ((abs & 0xFFFFFF) / den));
            }
        }
        return out;
    }

    private static int clip3(int low, int high, int v) {
        if (v < low) return low;
        if (v > high) return high;
        return v;
    }

    private static void quantRow(StringBuilder sb, int[] a) {
        for (int i = 0; i < a.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(a[i]);
        }
        sb.append('\n');
    }

    /** The canonical dump the Kof probe must reproduce. */
    static String golden() {
        StringBuilder sb = new StringBuilder();
        for (int bd : new int[]{8, 10, 12}) {
            int[] dc = new int[256];
            int[] ac = new int[256];
            for (int b = 0; b < 256; b++) {
                dc[b] = dcQ(bd, b);
                ac[b] = acQ(bd, b);
            }
            quantRow(sb, dc);
            quantRow(sb, ac);
        }
        int[] quant = new int[4096];
        for (int k = 0; k < 4096; k++) quant[k] = (k * 37 + 11) % 67 - 33;
        for (int[] c : CASES) {
            quantRow(sb, dequant(quant, c[0], c[1], c[2], c[3]));
        }
        return sb.toString().strip();
    }

    /** Kof probe source that dumps the tables and the dequantized blocks. */
    static String kofProbe() {
        return PROBE;
    }

    private static final String PROBE = """
import image.Av1Quant
import image.Av1Tx

void av1QuantRow(Int[] a) {
    var s = ""
    var i = 0
    while (i < a.size) {
        if (i > 0) {
            s = s + " "
        }
        s = s + a[i]
        i = i + 1
    }
    println(s)
}

Int[] av1QuantFill() {
    var out = new Int[4096]
    var k = 0
    while (k < 4096) {
        out[k] = (k * 37 + 11) % 67 - 33
        k = k + 1
    }
    return out
}

main() {
    var t = Av1QuantTables()
    var bds = listOf(8, 10, 12)
    for (var bd in bds) {
        var dc = new Int[256]
        var ac = new Int[256]
        var b = 0
        while (b < 256) {
            dc[b] = t.dcQ(bd, b)
            ac[b] = t.acQ(bd, b)
            b = b + 1
        }
        av1QuantRow(dc)
        av1QuantRow(ac)
    }
    var q = av1QuantFill()
    av1QuantRow(av1Dequant(q, 0, 8, 4, 4))
    av1QuantRow(av1Dequant(q, 1, 8, 100, 100))
    av1QuantRow(av1Dequant(q, 2, 8, 200, 200))
    av1QuantRow(av1Dequant(q, 3, 8, 140, 140))
    av1QuantRow(av1Dequant(q, 4, 8, 255, 255))
    av1QuantRow(av1Dequant(q, 5, 8, 32, 32))
    av1QuantRow(av1Dequant(q, 6, 8, 32, 32))
    av1QuantRow(av1Dequant(q, 7, 10, 60, 60))
    av1QuantRow(av1Dequant(q, 8, 10, 60, 60))
    av1QuantRow(av1Dequant(q, 9, 10, 60, 60))
    av1QuantRow(av1Dequant(q, 10, 10, 60, 60))
    av1QuantRow(av1Dequant(q, 11, 10, 80, 80))
    av1QuantRow(av1Dequant(q, 12, 10, 80, 80))
    av1QuantRow(av1Dequant(q, 13, 12, 90, 90))
    av1QuantRow(av1Dequant(q, 14, 12, 90, 90))
    av1QuantRow(av1Dequant(q, 15, 12, 120, 120))
    av1QuantRow(av1Dequant(q, 16, 12, 120, 120))
    av1QuantRow(av1Dequant(q, 17, 12, 150, 150))
    av1QuantRow(av1Dequant(q, 18, 12, 150, 150))
}
""";
}
