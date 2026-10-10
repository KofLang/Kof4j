package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Faithful Java port of libaom's {@code od_ec} range encoder
 * ({@code aom_dsp/entenc.c}) used by the AVIF slice-3x oracle
 * ({@link Av1CoeffPlaneSupport}). The port was validated byte-for-byte against
 * the pinned slice-3e coefficient streams before it was used to synthesise the
 * slice-3x golden. Kept apart from the support reader so each class stays under
 * the 500-line test-hygiene target.
 */
final class Av1CoeffPlaneEnc {

    final List<Integer> out = new ArrayList<>();
    long low = 0;
    int rng = 0x8000;
    int cnt = -9;

    void normalize(long low, int rng) {
        int d = 16 - (32 - Integer.numberOfLeadingZeros(rng));
        int c = cnt;
        int s = c + d;
        if (s >= 40) {
            int numBytesReady = (s >> 3) + 1;
            c += 24 - (numBytesReady << 3);
            long output = low >>> c;
            low = low & ((1L << c) - 1);
            long mask = 1L << (numBytesReady << 3);
            long carry = output & mask;
            mask = mask - 1;
            output = output & mask;
            writeEncData(out, output, carry, numBytesReady);
            s = c + d - 24;
        }
        this.low = low << d;
        this.rng = rng << d;
        this.cnt = s;
    }

    static void writeEncData(List<Integer> out, long output, long carry, int numBytesReady) {
        for (int i = numBytesReady - 1; i >= 0; i--) {
            out.add((int) ((output >>> (i * 8)) & 0xFF));
        }
        if (carry != 0) {
            int off = out.size() - numBytesReady - 1;
            while (off >= 0) {
                int sum = out.get(off) + 1;
                out.set(off, sum & 0xFF);
                if ((sum >> 8) == 0) break;
                off--;
            }
        }
    }

    void encodeQ15(int fl, int fh, int s, int nsyms) {
        long l = low;
        int r = rng;
        int N = nsyms - 1;
        if (fl < 32768) {
            int u = ((r >> 8) * (fl >> 6) >> 1) + 4 * (N - (s - 1));
            int v = ((r >> 8) * (fh >> 6) >> 1) + 4 * (N - s);
            l += r - u;
            r = u - v;
        } else {
            r -= ((r >> 8) * (fh >> 6) >> 1) + 4 * (N - s);
        }
        normalize(l, r);
    }

    void encodeBool(int val, int f) {
        long l = low;
        int r = rng;
        int v = ((r >> 8) * (f >> 6) >> 1) + 4;
        if (val != 0) l += r - v;
        r = val != 0 ? v : r - v;
        normalize(l, r);
    }

    void symbol(int[] cdf, int nsyms, int s) {
        int[] icdf = new int[nsyms];
        for (int i = 0; i < nsyms; i++) icdf[i] = 32768 - cdf[i];
        encodeQ15(s > 0 ? icdf[s - 1] : 32768, icdf[s], s, nsyms);
        Av1CoeffsSupport.updateCdf(cdf, s, nsyms);
    }

    void bit(int b) { encodeBool(b, 16384); }

    int[] done() {
        long l = low;
        int c = cnt;
        int s = 10;
        long m = 0x3FFF;
        long e = ((l + m) & ~m) | (m + 1);
        s += c;
        int base = out.size();
        if (s > 0) {
            long nn = (1L << (c + 16)) - 1;
            int offs = base;
            do {
                int val = (int) (e >>> (c + 16));
                out.add(val & 0xFF);
                if ((val & 0x0100) != 0) {
                    int o = offs - 1;
                    while (o >= 0) {
                        int sum = out.get(o) + 1;
                        out.set(o, sum & 0xFF);
                        if ((sum >> 8) == 0) break;
                        o--;
                    }
                }
                offs++;
                e &= nn;
                s -= 8;
                c -= 8;
                nn >>>= 8;
            } while (s > 0);
        }
        int[] res = new int[out.size()];
        for (int i = 0; i < res.length; i++) res[i] = out.get(i);
        return res;
    }
}
