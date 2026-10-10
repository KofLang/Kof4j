package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Fixtures + independent second reader for AVIF slice 3a (the AV1 symbol /
 * entropy decoder, {@code libs/image/Av1Symbol.kf}). The fixtures are the
 * exact byte streams produced by libaom's entropy encoder
 * ({@code aom_dsp/entenc.c}) for a chosen symbol sequence and CDF, with
 * libaom's decoder as the round-trip check (built on the dev host 04/10, not
 * committed). {@link #javaDecode} is a second, independent implementation of
 * the AV1 spec §9.3.4 process in plain Java — the Kof library must agree with
 * both.
 */
final class Av1SymbolSupport {

    private Av1SymbolSupport() {}

    /** name, nsyms, update, initial spec CDF (N+1, cdf[N]=0), symbols. */
    record Fixture(String name, int nsyms, boolean update, int[] cdf, int[] syms) {}

    static List<Fixture> fixtures() {
        List<Fixture> out = new ArrayList<>();
        out.add(new Fixture("literal20", 2, false, new int[]{16384, 32768, 0},
                new int[]{1,0,1,1,0,0,1,0,1,1,1,0,0,1,0,1,1,0,1,0}));
        out.add(new Fixture("literal12", 2, false, new int[]{16384, 32768, 0},
                new int[]{1,0,1,1,0,1,0,0,1,0,1,1}));
        out.add(new Fixture("sym4fixed", 4, false, new int[]{8000, 16000, 24000, 32768, 0},
                new int[]{0,3,2,1,0,2,2,3,1,0}));
        out.add(new Fixture("sym5adapt", 5, true, new int[]{5000, 11000, 18000, 26000, 32768, 0},
                new int[]{2,2,2,0,4,3,2,2,1,2,4,2}));
        out.add(new Fixture("sym2adapt", 2, true, new int[]{12000, 32768, 0},
                new int[]{1,0,0,1,0,0,0,1,1,0,0,0,0,1,0,0}));
        out.add(new Fixture("sym8adapt", 8, true,
                new int[]{2000, 5000, 9000, 14000, 19000, 24000, 29000, 32768, 0},
                new int[]{7,0,3,3,6,1,5,2,3,3,4,0,7,3}));
        return out;
    }

    /** Byte streams produced by libaom's encoder for {@link #fixtures()}. */
    static int[] bytesFor(String name) {
        return switch (name) {
            case "literal20" -> new int[]{178, 224, 216};
            case "literal12" -> new int[]{180, 184};
            case "sym4fixed" -> new int[]{55, 16, 172};
            case "sym5adapt" -> new int[]{108, 129, 29, 16};
            case "sym2adapt" -> new int[]{103, 228, 192};
            case "sym8adapt" -> new int[]{227, 150, 2, 43, 226, 48};
            default -> throw new IllegalArgumentException(name);
        };
    }

    static String golden() {
        StringBuilder sb = new StringBuilder();
        for (Fixture f : fixtures()) {
            sb.append(f.name());
            for (int s : f.syms()) sb.append(' ').append(s);
            sb.append('\n');
        }
        return sb.toString().strip();
    }

    /** Java probe source that decodes every fixture with the Kof library. */
    static String caseProbe() {
        StringBuilder sb = new StringBuilder();
        sb.append("import image.Av1Symbol\n\n");
        sb.append("main() {\n");
        for (Fixture f : fixtures()) {
            sb.append("    run(\"").append(f.name()).append("\", ");
            sb.append(array(bytesFor(f.name()))).append(", ");
            sb.append(array(f.cdf())).append(", ");
            sb.append(f.nsyms()).append(", ");
            sb.append(f.update() ? "true" : "false").append(", ");
            sb.append(f.syms().length).append(")\n");
        }
        sb.append("}\n\n");
        sb.append("void run(String name, Int[] data, Int[] cdf, Int n, Bool adapt, Int count) {\n");
        sb.append("    var d = Av1Symbol(data)\n");
        sb.append("    d.setAdapt(adapt)\n");
        sb.append("    var sb = \"\"\n");
        sb.append("    var i = 0\n");
        sb.append("    while (i < count) {\n");
        sb.append("        sb = sb + \" \" + d.readSymbol(cdf, n)\n");
        sb.append("        i = i + 1\n");
        sb.append("    }\n");
        sb.append("    println(name + sb)\n");
        sb.append("}\n\n");
        sb.append(arrayHelpers());
        return sb.toString();
    }

    private static String array(int[] values) {
        // Kof has no array literal: build element by element.
        StringBuilder sb = new StringBuilder();
        String var = "a" + Math.abs(java.util.Arrays.hashCode(values) % 100000);
        sb.append("/*arr*/");
        // Inline via a helper call chain is impossible without literals; emit a
        // block expression is also unavailable, so the probe builds them with
        // explicit `new` in `arrN` helpers below.
        sb.setLength(0);
        sb.append("mk").append(values.length).append('(');
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(values[i]);
        }
        sb.append(')');
        return sb.toString();
    }

    /** Helper builders appended to the probe for every array length used. */
    static String arrayHelpers() {
        StringBuilder sb = new StringBuilder();
        java.util.Set<Integer> lengths = new java.util.TreeSet<>();
        for (Fixture f : fixtures()) {
            lengths.add(bytesFor(f.name()).length);
            lengths.add(f.cdf().length);
        }
        for (int n : lengths) {
            sb.append("Int[] mk").append(n).append('(');
            for (int i = 0; i < n; i++) {
                if (i > 0) sb.append(", ");
                sb.append("Int v").append(i);
            }
            sb.append(") { var x = new Int[").append(n).append("]; ");
            for (int i = 0; i < n; i++) {
                sb.append("x[").append(i).append("] = v").append(i).append("; ");
            }
            sb.append("return x }\n");
        }
        return sb.toString();
    }

    /**
     * Independent Java implementation of the AV1 §9.3.4 symbol decoder, used
     * as a second reader. Returns the decoded symbols.
     */
    static int[] javaDecode(int[] data, int[] cdf, int n, boolean update, int count) {
        int[] c = cdf.clone();
        int bitPos = 0;
        int value;
        int range = 1 << 15;
        int numBits = Math.min(data.length * 8, 15);
        int buf = 0;
        for (int i = 0; i < numBits; i++) {
            buf = (buf << 1) | readBit(data, bitPos++);
        }
        value = ((1 << 15) - 1) ^ (buf << (15 - numBits));
        int maxBits = 8 * data.length - 15;
        int[] out = new int[count];
        for (int k = 0; k < count; k++) {
            int cur = range;
            int symbol = -1;
            int prev;
            do {
                symbol++;
                prev = cur;
                int f = (1 << 15) - c[symbol];
                cur = (((range >> 8) * (f >> 6)) >> 1) + 4 * (n - symbol - 1);
            } while (value < cur);
            range = prev - cur;
            value -= cur;
            int bits = 15 - floorLog2(range);
            range <<= bits;
            int nb = Math.min(bits, Math.max(0, maxBits));
            int newData = 0;
            for (int i = 0; i < nb; i++) {
                newData = (newData << 1) | readBit(data, bitPos++);
            }
            int padded = newData << (bits - nb);
            value = padded ^ (((value + 1) << bits) - 1);
            maxBits -= bits;
            if (update) {
                int rate = 3 + (c[n] > 15 ? 1 : 0) + (c[n] > 31 ? 1 : 0) + Math.min(floorLog2(n), 2);
                int tmp = 0;
                for (int i = 0; i < n - 1; i++) {
                    if (i == symbol) tmp = 1 << 15;
                    if (tmp < c[i]) c[i] -= (c[i] - tmp) >> rate;
                    else c[i] += (tmp - c[i]) >> rate;
                }
                if (c[n] < 32) c[n]++;
            }
            out[k] = symbol;
        }
        return out;
    }

    /** Java decode of the first {@code bools} booleans and {@code literals} bytes. */
    static String javaRealFacts(int[] tile, int bools, int literals) {
        int[] cdf = {1 << 14, 1 << 15, 0};
        int[] all = javaDecode(tile, cdf, 2, false, bools + literals * 8);
        StringBuilder sb = new StringBuilder("BOOLS");
        for (int i = 0; i < bools; i++) sb.append(' ').append(all[i]);
        sb.append(" LIT");
        for (int i = 0; i < literals; i++) {
            int v = 0;
            for (int j = 0; j < 8; j++) v = (v << 1) | all[bools + i * 8 + j];
            sb.append(' ').append(v);
        }
        return sb.toString();
    }

    private static int readBit(int[] data, int bitPos) {
        int byteIndex = bitPos >> 3;
        if (byteIndex >= data.length) return 0;
        return (data[byteIndex] >> (7 - (bitPos & 7))) & 1;
    }

    private static int floorLog2(int v) {
        int r = 0;
        while ((v >>= 1) != 0) r++;
        return r;
    }

    static final String REAL_AVIF = "/home/mel/.vscode/extensions/openai.chatgpt-26.930.31730-linux-x64"
            + "/webview/assets/fallen-pet-28c3cddba20a.avif";

    /**
     * Independent Java extraction of the real AVIF's primary tile payload
     * (item 1, the OBU_FRAME inline tile group), for the second-reader check.
     */
    static int[] realTileBytes() throws java.io.IOException {
        byte[] b = java.nio.file.Files.readAllBytes(java.nio.file.Path.of(REAL_AVIF));
        int itemStart = 14723;
        int itemEnd = 56883;
        int pos = itemStart + 13;              // OBU_FRAME at item offset 13
        int i = pos + 1;                       // after the OBU header byte
        int size = 0;
        int shift = 0;
        while (true) {
            int x = b[i++] & 0xFF;
            size |= (x & 0x7F) << shift;
            shift += 7;
            if ((x & 0x80) == 0) break;
        }
        int headerBytes = 11;
        int tileLen = size - headerBytes;
        int[] out = new int[tileLen];
        for (int k = 0; k < tileLen; k++) out[k] = b[i + headerBytes + k] & 0xFF;
        if (itemEnd - itemStart < 13 + (i - (itemStart + 13)) + size) {
            throw new IllegalStateException("item bounds");
        }
        return out;
    }
}
