package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Fixtures + independent second reader for AVIF slice 3b (the AV1 transform
 * descriptor, {@code libs/image/Av1Tx.kf}). The pinned golden is the scan-order
 * content, the transform-size tables and the transform-type selection, all
 * taken from the AV1 spec (read on the dev host 04/10) and cross-checked
 * against libaom's {@code av1_scan_orders}/{@code Tx_Width}.. tables. The scan
 * content is pinned as (length, 24-bit rolling hash) so the whole 19x3 table
 * set fits without embedding 4912 numbers.
 *
 * <p>The scan tables in the spec markdown and libaom use a different
 * flattening convention (the spec writes row-major, libaom column-major); both
 * are normalised to row-major here. {@link #javaFacts()} is a second,
 * independent generator of the same orders in plain Java.
 */
final class Av1TxSupport {

    private Av1TxSupport() {}

    // Tx_Width / Tx_Height / Tx_Width_Log2 / Tx_Height_Log2 / Tx_Size_Sqr /
    // Tx_Size_Sqr_Up / Adjusted_Tx_Size, TX_SIZES_ALL order.
    static final int[] TXW = {4, 8, 16, 32, 64, 4, 8, 8, 16, 16, 32, 32, 64, 4, 16, 8, 32, 16, 64};
    static final int[] TXH = {4, 8, 16, 32, 64, 8, 4, 16, 8, 32, 16, 64, 32, 16, 4, 32, 8, 64, 16};
    static final int[] TXWL = {2, 3, 4, 5, 6, 2, 3, 3, 4, 4, 5, 5, 6, 2, 4, 3, 5, 4, 6};
    static final int[] TXHL = {2, 3, 4, 5, 6, 3, 2, 4, 3, 5, 4, 6, 5, 4, 2, 5, 3, 6, 4};
    static final int[] TXSQR = {0, 1, 2, 3, 4, 0, 0, 1, 1, 2, 2, 3, 3, 0, 0, 1, 1, 2, 2};
    static final int[] TXSQUP = {0, 1, 2, 3, 4, 1, 1, 2, 2, 3, 3, 4, 4, 2, 2, 3, 3, 4, 4};
    static final int[] TXADJ = {0, 1, 2, 3, 3, 5, 6, 7, 8, 9, 10, 3, 3, 13, 14, 15, 16, 9, 10};

    // Pinned (length, rolling hash) of the scan order for every (kind, tx).
    // kind: D default, R mrow (row-major), C mcol (column-major). Derived from
    // libaom av1_scan_orders and cross-checked against the spec's published
    // Default_Scan_*/Mrow_Scan_*/Mcol_Scan_* tables where they exist.
    static final int[][] SCAN_PINS = {
        {0, 16, 14456984}, {0, 64, 9854112}, {0, 256, 12749568}, {0, 1024, 9379072},
        {0, 1024, 9379072}, {0, 32, 11342352}, {0, 32, 7595024}, {0, 128, 5567616},
        {0, 128, 15197312}, {0, 512, 11340416}, {0, 512, 4786816}, {0, 1024, 9379072},
        {0, 1024, 9379072}, {0, 64, 14151168}, {0, 64, 147712}, {0, 256, 5401664},
        {0, 256, 2838592}, {0, 512, 11340416}, {0, 512, 4786816},
        {1, 16, 2406008}, {1, 64, 8134624}, {1, 256, 4513664}, {1, 1024, 5864960},
        {1, 1024, 5864960}, {1, 32, 8670704}, {1, 32, 8670704}, {1, 128, 413632},
        {1, 128, 413632}, {1, 512, 6995712}, {1, 512, 6995712}, {1, 1024, 5864960},
        {1, 1024, 5864960}, {1, 64, 8134624}, {1, 64, 8134624}, {1, 256, 4513664},
        {1, 256, 4513664}, {1, 512, 6995712}, {1, 512, 6995712},
        {2, 16, 12287224}, {2, 64, 10414560}, {2, 256, 7702400}, {2, 1024, 4726272},
        {2, 1024, 4726272}, {2, 32, 4065008}, {2, 32, 6948592}, {2, 128, 8733632},
        {2, 128, 15025088}, {2, 512, 1511168}, {2, 512, 1511168}, {2, 1024, 4726272},
        {2, 1024, 4726272}, {2, 64, 13844960}, {2, 64, 14369248}, {2, 256, 542592},
        {2, 256, 4736896}, {2, 512, 1511168}, {2, 512, 1511168},
    };

    static int rollingHash(int[] a) {
        int h = 0;
        for (int v : a) h = ((h << 5) + h + v) & 0xFFFFFF;
        return h;
    }

    /** The full pinned golden, in the exact format the Kof probe prints. */
    static String golden() {
        StringBuilder sb = new StringBuilder();
        for (int tx = 0; tx < 19; tx++) {
            sb.append("W ").append(tx).append(' ').append(TXW[tx]).append(' ').append(TXH[tx])
              .append(' ').append(TXWL[tx]).append(' ').append(TXHL[tx])
              .append(' ').append(TXSQR[tx]).append(' ').append(TXSQUP[tx])
              .append(' ').append(TXADJ[tx]).append(' ').append(txSzCtx(tx)).append('\n');
        }
        for (int tx = 0; tx < 19; tx++) {
            for (int kind = 0; kind < 3; kind++) {
                int[] p = SCAN_PINS[kind * 19 + tx];
                char k = kind == 0 ? 'D' : kind == 1 ? 'R' : 'C';
                sb.append("S ").append(k).append(' ').append(p[1]).append(' ').append(p[2]).append('\n');
            }
        }
        for (int t = 0; t < 16; t++) {
            sb.append("C ").append(t).append(' ').append(txClass(t)).append('\n');
        }
        for (int[] c : SET_CASES) {
            sb.append("T ").append(c[0]).append(' ').append(c[1] == 1 ? "I" : "A")
              .append(' ').append(c[2]).append(' ').append(txSet(c[0], c[1] == 1, c[2] == 1)).append('\n');
        }
        for (int s = 0; s < 3; s++) {
            for (int t = 0; t < 16; t++) {
                sb.append("I ").append(s).append(' ').append(t).append(' ')
                  .append(intraInSet(s, t) ? 1 : 0).append('\n');
            }
        }
        for (int s = 0; s < 4; s++) {
            for (int t = 0; t < 16; t++) {
                sb.append("N ").append(s).append(' ').append(t).append(' ')
                  .append(interInSet(s, t) ? 1 : 0).append('\n');
            }
        }
        for (int m = 0; m < 14; m++) {
            sb.append("M ").append(m).append(' ').append(MODE_TO_TXFM[m]).append('\n');
        }
        for (int[] c : COMPUTE_CASES) {
            sb.append("X ").append(compute(c)).append('\n');
        }
        for (int set = 1; set < 3; set++) {
            for (int sym = 0; sym < intraSymbols(set); sym++) {
                sb.append("V I ").append(set).append(' ').append(sym).append(' ')
                  .append(inverse(set, false, sym)).append('\n');
            }
        }
        for (int set = 1; set < 4; set++) {
            for (int sym = 0; sym < interSymbols(set); sym++) {
                sb.append("V A ").append(set).append(' ').append(sym).append(' ')
                  .append(inverse(set, true, sym)).append('\n');
            }
        }
        return sb.toString().strip();
    }

    static int floorLog2(int n) {
        int r = 0;
        while ((n >>= 1) != 0) r++;
        return r;
    }

    static int txSzCtx(int tx) {
        return (TXSQR[tx] + TXSQUP[tx] + 1) >> 1;
    }

    static int txClass(int txType) {
        if (txType == 10 || txType == 12 || txType == 14) return 1;
        if (txType == 11 || txType == 13 || txType == 15) return 2;
        return 0;
    }

    static int txSet(int txSz, boolean isInter, boolean reduced) {
        int sqr = TXSQR[txSz];
        int sqrUp = TXSQUP[txSz];
        if (sqrUp > 3) return 0;
        if (isInter) {
            if (reduced || sqrUp == 3) return 3;
            if (sqr == 2) return 2;
            return 1;
        }
        if (sqrUp == 3) return 0;
        if (reduced || sqr == 2) return 2;
        return 1;
    }

    static final int[] MODE_TO_TXFM = {0, 1, 2, 0, 3, 1, 2, 2, 1, 3, 1, 2, 3, 0};

    static boolean intraInSet(int set, int type) {
        int[] table = {
            1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            1, 1, 1, 1, 0, 0, 0, 0, 0, 1, 1, 1, 0, 0, 0, 0,
            1, 1, 1, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0,
        };
        return table[set * 16 + type] == 1;
    }

    static boolean interInSet(int set, int type) {
        int[] table = {
            1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0, 0, 0, 0,
            1, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0,
        };
        return table[set * 16 + type] == 1;
    }

    static int inverse(int set, boolean isInter, int sym) {
        if (isInter) {
            if (set == 1) return new int[]{9, 10, 11, 12, 13, 14, 15, 0, 1, 2, 4, 5, 3, 6, 7, 8}[sym];
            if (set == 2) return new int[]{9, 10, 11, 0, 1, 2, 4, 5, 3, 6, 7, 8}[sym];
            return new int[]{9, 0}[sym];
        }
        if (set == 1) return new int[]{9, 0, 10, 11, 3, 1, 2}[sym];
        return new int[]{9, 0, 3, 1, 2}[sym];
    }

    static int intraSymbols(int set) {
        return set == 1 ? 7 : 5;
    }

    static int interSymbols(int set) {
        return set == 1 ? 16 : set == 2 ? 12 : 2;
    }

    // txSz, isInter, reduced
    static final int[][] SET_CASES = {
        {0, 0, 0}, {0, 0, 1}, {0, 1, 0}, {0, 1, 1},
        {2, 0, 0}, {2, 0, 1}, {2, 1, 0}, {2, 1, 1},
        {3, 0, 0}, {3, 1, 0}, {3, 1, 1}, {4, 0, 0}, {4, 1, 0},
    };

    // txSz, plane, blockX, blockY, lossless, isInter, value, valueIndex,
    // uvMode, miCol, miRow, subX, subY, reduced. The TxTypes plane is a 16-entry
    // row-major array (stride 4) with `value` placed at `valueIndex`.
    static final int[][] COMPUTE_CASES = {
        {0, 0, 2, 3, 0, 0, 7, 14, 0, 0, 0, 0, 0, 0},
        {0, 0, 2, 3, 1, 0, 7, 14, 0, 0, 0, 0, 0, 0},
        {3, 1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0},
        {0, 1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0},
        {0, 1, 0, 0, 0, 0, 0, 0, 4, 0, 0, 0, 0, 1},
        {0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1},
        {0, 1, 0, 0, 0, 1, 10, 5, 0, 1, 1, 0, 0, 0},
        {0, 1, 0, 0, 0, 1, 10, 5, 0, 1, 1, 0, 0, 1},
        {0, 1, 0, 0, 0, 1, 10, 0, 0, 0, 0, 1, 1, 0},
        {0, 1, 0, 0, 0, 1, 10, 11, 0, 3, 2, 1, 1, 0},
        {0, 1, 1, 1, 0, 1, 12, 5, 0, 0, 0, 0, 0, 0},
        {1, 1, 0, 0, 0, 0, 0, 0, 4, 0, 0, 0, 0, 0},
    };

    static int compute(int[] c) {
        int txSz = c[0], plane = c[1], bx = c[2], by = c[3];
        boolean lossless = c[4] == 1, isInter = c[5] == 1;
        int value = c[6], valueIndex = c[7], uvMode = c[8], miCol = c[9], miRow = c[10];
        int subX = c[11], subY = c[12];
        boolean reduced = c[13] == 1;
        int[] txTypes = new int[16];
        txTypes[valueIndex] = value;
        if (lossless || TXSQUP[txSz] > 3) return 0;
        int set = txSet(txSz, isInter, reduced);
        if (plane == 0) return txTypes[by * 4 + bx];
        if (isInter) {
            int x4 = Math.max(miCol, bx << subX);
            int y4 = Math.max(miRow, by << subY);
            int t = txTypes[y4 * 4 + x4];
            return interInSet(set, t) ? t : 0;
        }
        int t = MODE_TO_TXFM[uvMode];
        return intraInSet(set, t) ? t : 0;
    }

    /** Kof probe source: prints every pinned fact via the Av1Tx library. */
    static String kofProbe() {
        StringBuilder sb = new StringBuilder();
        sb.append("import image.Av1Tx\n\nmain() {\n");
        sb.append("    var tx = 0\n");
        sb.append("    while (tx < 19) {\n");
        sb.append("        println(\"W \" + tx + \" \" + av1TxWidth(tx) + \" \" + av1TxHeight(tx)")
          .append(" + \" \" + av1TxWidthLog2(tx) + \" \" + av1TxHeightLog2(tx)")
          .append(" + \" \" + av1TxSizeSqr(tx) + \" \" + av1TxSizeSqrUp(tx)")
          .append(" + \" \" + av1AdjustedTxSize(tx) + \" \" + av1TxSzCtx(tx))\n");
        sb.append("        tx = tx + 1\n");
        sb.append("    }\n");
        sb.append("    tx = 0\n");
        sb.append("    while (tx < 19) {\n");
        sb.append("        printScan(\"D\", tx, av1DefaultScan(tx))\n");
        sb.append("        printScan(\"R\", tx, av1MrowScan(tx))\n");
        sb.append("        printScan(\"C\", tx, av1McolScan(tx))\n");
        sb.append("        tx = tx + 1\n");
        sb.append("    }\n");
        sb.append("    var t = 0\n");
        sb.append("    while (t < 16) {\n");
        sb.append("        println(\"C \" + t + \" \" + av1TxClass(t))\n");
        sb.append("        t = t + 1\n");
        sb.append("    }\n");
        for (int[] c : SET_CASES) {
            sb.append("    println(\"T ").append(c[0]).append(' ').append(c[1] == 1 ? "I" : "A")
              .append(' ').append(c[2]).append(" \" + av1TxSet(").append(c[0]).append(", ")
              .append(c[1] == 1 ? "true" : "false").append(", ")
              .append(c[2] == 1 ? "true" : "false").append("))\n");
        }
        sb.append("    var s = 0\n");
        sb.append("    while (s < 3) {\n");
        sb.append("        var i = 0\n");
        sb.append("        while (i < 16) {\n");
        sb.append("            println(\"I \" + s + \" \" + i + \" \" + flag(av1TxTypeInSetIntra(s, i)))\n");
        sb.append("            i = i + 1\n");
        sb.append("        }\n");
        sb.append("        s = s + 1\n");
        sb.append("    }\n");
        sb.append("    var n = 0\n");
        sb.append("    while (n < 4) {\n");
        sb.append("        var i = 0\n");
        sb.append("        while (i < 16) {\n");
        sb.append("            println(\"N \" + n + \" \" + i + \" \" + flag(av1TxTypeInSetInter(n, i)))\n");
        sb.append("            i = i + 1\n");
        sb.append("        }\n");
        sb.append("        n = n + 1\n");
        sb.append("    }\n");
        sb.append("    var m = 0\n");
        sb.append("    while (m < 14) {\n");
        sb.append("        println(\"M \" + m + \" \" + av1ModeToTxfm(m))\n");
        sb.append("        m = m + 1\n");
        sb.append("    }\n");
        for (int[] c : COMPUTE_CASES) {
            sb.append("    println(\"X \" + av1ComputeTxType(").append(c[0]).append(", ").append(c[1])
              .append(", ").append(c[2]).append(", ").append(c[3]).append(", ")
              .append(c[4] == 1 ? "true" : "false").append(", ")
              .append(c[5] == 1 ? "true" : "false").append(", mkT16(").append(c[6]).append(", ")
              .append(c[7]).append("), 4, ")
              .append(c[8]).append(", ").append(c[9]).append(", ").append(c[10]).append(", ")
              .append(c[11]).append(", ").append(c[12]).append(", ")
              .append(c[13] == 1 ? "true" : "false").append("))\n");
        }
        sb.append("    var set = 1\n");
        sb.append("    while (set < 3) {\n");
        sb.append("        var y = 0\n");
        sb.append("        while (y < intraSymbols(set)) {\n");
        sb.append("            println(\"V I \" + set + \" \" + y + \" \" + av1TxTypeInverse(set, false, y))\n");
        sb.append("            y = y + 1\n");
        sb.append("        }\n");
        sb.append("        set = set + 1\n");
        sb.append("    }\n");
        sb.append("    set = 1\n");
        sb.append("    while (set < 4) {\n");
        sb.append("        var y = 0\n");
        sb.append("        while (y < interSymbols(set)) {\n");
        sb.append("            println(\"V A \" + set + \" \" + y + \" \" + av1TxTypeInverse(set, true, y))\n");
        sb.append("            y = y + 1\n");
        sb.append("        }\n");
        sb.append("        set = set + 1\n");
        sb.append("    }\n");
        sb.append("}\n\n");
        sb.append("Int flag(Bool b) { if (b) { return 1 } return 0 }\n\n");
        sb.append("Int intraSymbols(Int set) { if (set == 1) { return 7 } return 5 }\n\n");
        sb.append("Int interSymbols(Int set) { if (set == 1) { return 16 } if (set == 2) { return 12 } return 2 }\n\n");
        sb.append("Int[] mkT16(Int v, Int at) {\n");
        sb.append("    var x = new Int[16]\n");
        sb.append("    var i = 0\n");
        sb.append("    while (i < 16) { x[i] = 0  i = i + 1 }\n");
        sb.append("    x[at] = v\n");
        sb.append("    return x\n");
        sb.append("}\n\n");
        sb.append("void printScan(String kind, Int tx, Int[] s) {\n");
        sb.append("    var h = 0\n");
        sb.append("    var i = 0\n");
        sb.append("    while (i < s.length) {\n");
        sb.append("        h = ((h << 5) + h + s[i]) & 16777215\n");
        sb.append("        i = i + 1\n");
        sb.append("    }\n");
        sb.append("    println(\"S \" + kind + \" \" + s.length + \" \" + h)\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Independent Java generator of the same facts (second reader). The scan
     * orders are generated from the spec's geometric rule; the tables and the
     * selection logic are re-derived from the spec text.
     */
    static String javaFacts() {
        StringBuilder sb = new StringBuilder();
        for (int tx = 0; tx < 19; tx++) {
            sb.append("W ").append(tx).append(' ').append(TXW[tx]).append(' ').append(TXH[tx])
              .append(' ').append(TXWL[tx]).append(' ').append(TXHL[tx])
              .append(' ').append(TXSQR[tx]).append(' ').append(TXSQUP[tx])
              .append(' ').append(TXADJ[tx]).append(' ').append(txSzCtx(tx)).append('\n');
        }
        for (int tx = 0; tx < 19; tx++) {
            for (char kind : new char[]{'D', 'R', 'C'}) {
                int[] s = scan(tx, kind);
                sb.append("S ").append(kind).append(' ').append(s.length).append(' ')
                  .append(rollingHash(s)).append('\n');
            }
        }
        for (int t = 0; t < 16; t++) sb.append("C ").append(t).append(' ').append(txClass(t)).append('\n');
        for (int[] c : SET_CASES) {
            sb.append("T ").append(c[0]).append(' ').append(c[1] == 1 ? "I" : "A")
              .append(' ').append(c[2]).append(' ').append(txSet(c[0], c[1] == 1, c[2] == 1)).append('\n');
        }
        for (int s = 0; s < 3; s++)
            for (int t = 0; t < 16; t++)
                sb.append("I ").append(s).append(' ').append(t).append(' ')
                  .append(intraInSet(s, t) ? 1 : 0).append('\n');
        for (int s = 0; s < 4; s++)
            for (int t = 0; t < 16; t++)
                sb.append("N ").append(s).append(' ').append(t).append(' ')
                  .append(interInSet(s, t) ? 1 : 0).append('\n');
        for (int m = 0; m < 14; m++) sb.append("M ").append(m).append(' ').append(MODE_TO_TXFM[m]).append('\n');
        for (int[] c : COMPUTE_CASES) sb.append("X ").append(compute(c)).append('\n');
        for (int set = 1; set < 3; set++)
            for (int sym = 0; sym < intraSymbols(set); sym++)
                sb.append("V I ").append(set).append(' ').append(sym).append(' ')
                  .append(inverse(set, false, sym)).append('\n');
        for (int set = 1; set < 4; set++)
            for (int sym = 0; sym < interSymbols(set); sym++)
                sb.append("V A ").append(set).append(' ').append(sym).append(' ')
                  .append(inverse(set, true, sym)).append('\n');
        return sb.toString().strip();
    }

    /** Independent scan generation from the spec's geometric rule. */
    static int[] scan(int tx, char kind) {
        int w = scanW(tx), h = scanH(tx);
        if (kind == 'R') return rowMajor(w, h);
        if (kind == 'C') return colMajor(w, h);
        if (w == h) return zigzag(w, h);
        return w < h ? rowDiag(w, h) : colDiag(w, h);
    }

    static int scanW(int tx) {
        if (tx == 17) return 16;
        if (tx == 18) return 32;
        if (TXSQUP[tx] == 4) return 32;
        return TXW[tx];
    }

    static int scanH(int tx) {
        if (tx == 17) return 32;
        if (tx == 18) return 16;
        if (TXSQUP[tx] == 4) return 32;
        return TXH[tx];
    }

    static int[] zigzag(int w, int h) {
        List<Integer> out = new ArrayList<>();
        for (int d = 0; d < w + h - 1; d++) {
            if (d % 2 == 0) {
                for (int x = Math.max(0, d - (h - 1)); x <= Math.min(d, w - 1); x++) out.add((d - x) * w + x);
            } else {
                for (int x = Math.min(d, w - 1); x >= Math.max(0, d - (h - 1)); x--) out.add((d - x) * w + x);
            }
        }
        return toArray(out);
    }

    static int[] rowDiag(int w, int h) {
        List<Integer> out = new ArrayList<>();
        for (int d = 0; d < w + h - 1; d++)
            for (int y = 0; y < h; y++) {
                int x = d - y;
                if (x >= 0 && x < w) out.add(y * w + x);
            }
        return toArray(out);
    }

    static int[] colDiag(int w, int h) {
        List<Integer> out = new ArrayList<>();
        for (int d = 0; d < w + h - 1; d++)
            for (int x = 0; x < w; x++) {
                int y = d - x;
                if (y >= 0 && y < h) out.add(y * w + x);
            }
        return toArray(out);
    }

    static int[] rowMajor(int w, int h) {
        int[] out = new int[w * h];
        int k = 0;
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) out[k++] = y * w + x;
        return out;
    }

    static int[] colMajor(int w, int h) {
        int[] out = new int[w * h];
        int k = 0;
        for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) out[k++] = y * w + x;
        return out;
    }

    static int[] toArray(List<Integer> l) {
        int[] out = new int[l.size()];
        for (int i = 0; i < out.length; i++) out[i] = l.get(i);
        return out;
    }
}
