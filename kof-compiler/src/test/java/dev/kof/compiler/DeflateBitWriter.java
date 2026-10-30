package dev.kof.compiler;

import java.io.ByteArrayOutputStream;

/**
 * Writes a raw DEFLATE (RFC 1951) bit stream by hand — the oracle for the
 * MALFORMED shapes, which no real compressor emits. Bits go LSB-first; a
 * Huffman code goes MSB-first (RFC 1951 §3.1.1), which is the one detail a
 * hand-written stream gets wrong.
 */
final class DeflateBitWriter {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private int cur;
    private int used;

    /** `n` bits of `value`, least significant first (header fields, extra bits). */
    DeflateBitWriter bits(int value, int n) {
        for (int i = 0; i < n; i++) {
            cur |= ((value >> i) & 1) << used;
            if (++used == 8) {
                flushByte();
            }
        }
        return this;
    }

    /** A Huffman code of `len` bits, most significant bit first. */
    DeflateBitWriter code(int code, int len) {
        for (int i = len - 1; i >= 0; i--) {
            bits((code >> i) & 1, 1);
        }
        return this;
    }

    /** A literal/length symbol of the FIXED code (RFC 1951 §3.2.6). */
    DeflateBitWriter fixedSymbol(int sym) {
        if (sym < 144) {
            return code(0x30 + sym, 8);
        } else if (sym < 256) {
            return code(0x190 + (sym - 144), 9);
        } else if (sym < 280) {
            return code(sym - 256, 7);
        }
        return code(0xC0 + (sym - 280), 8);
    }

    /** Pads the last byte with zero bits and returns the stream. */
    byte[] finish() {
        if (used > 0) {
            flushByte();
        }
        return out.toByteArray();
    }

    private void flushByte() {
        out.write(cur);
        cur = 0;
        used = 0;
    }
}
