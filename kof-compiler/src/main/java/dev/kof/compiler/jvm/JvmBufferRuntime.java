package dev.kof.compiler.jvm;

/** JVM runtime for {@code kof.buffer} / the nominal {@code Buffer(U8)} type
 *  (D-R3-BUFFER / D6-3, maintainer 21/09). Incremental slice (R6-SCOPE):
 *  a real {@code KofRuntime$Buffer} object wrapping a {@code byte[]}; the
 *  runtime helper allocates and exposes a copy of the bytes. Native/JS never
 *  reach this file (honest gap upstream). */
public final class JvmBufferRuntime {
    private JvmBufferRuntime() {}

    static String source() {
        return source(true);
    }

    /** §640: the RAW peek/poke forms touch absolute addresses through the FFM
     *  ({@code java.lang.foreign}). ANDROID/ART has NO FFM and the surface is
     *  already refused at compile time there ({@code KofBuffer.supportedOn}),
     *  so the ANDROID runtime must OMIT the raw forms entirely — an emitted
     *  KofRuntime can never reference FFM on that target (§278 invariant,
     *  pinned by {@code GpuAndroidE2ETest}). The Buffer forms are pure
     *  {@code byte[]} math and stay on every target. */
    static String source(boolean rawForms) {
        return HEAD + (rawForms ? RAW : "") + BUFFER_FORMS;
    }

    private static final String HEAD = """
            // ── kof.buffer — Buffer(U8) out-buffer (D-R3-BUFFER, JVM) ──
            // D-MEM030-BORROW-RUNTIME: the runtime writable-borrow state of
            // B-03 (MEM020). An `extern` INOUT write acquires an exclusive
            // borrow; a second concurrent acquisition (two spawns, or
            // worker×parent without join) throws MEM020.
            public static final class Buffer {
                public final byte[] data;
                private Thread borrowOwner;
                public Buffer(int n) { this.data = new byte[n < 0 ? 0 : n]; }
                public int length() { return data.length; }
                public synchronized void kof_borrow_acquire() {
                    if (borrowOwner != null) {
                        throw new IllegalStateException(
                                "MEM020: Buffer(U8) writable borrow already held by another task");
                    }
                    borrowOwner = Thread.currentThread();
                }
                public synchronized void kof_borrow_release() { borrowOwner = null; }
                @Override public String toString() { return "Buffer[" + data.length + "]"; }
            }

            public static Buffer kof_buffer_alloc(int n) {
                return new Buffer(n);
            }

            public static byte[] kof_buffer_bytes(Buffer b) {
                return b == null ? new byte[0] : b.data.clone();
            }

            public static void kof_buffer_borrow_acquire(Buffer b) {
                if (b != null) b.kof_borrow_acquire();
            }

            public static void kof_buffer_borrow_release(Buffer b) {
                if (b != null) b.kof_borrow_release();
            }

            """;

    private static final String RAW = """
            // ── peek primitive (graphics slice 3.4b inc1, decision F row
            // ORDERED 08/10): read memory at a raw address. Raw form = the
            // opaque C structs (AVFrame); the raw form has no bounds (the
            // address is the caller's — same contract as the C dereference).
            private static long kof_peek_raw64(long addr) {
                // byte-by-byte LE composition — PARITY with the native `ld`
                // (the FFM JAVA_LONG read requires 8-byte alignment; the raw
                // peek reads at ANY address, same semantics as the asm).
                var seg = java.lang.foreign.MemorySegment.ofAddress(addr).reinterpret(8);
                long v = 0;
                for (int i = 7; i >= 0; i--) {
                    v = (v << 8) | (seg.get(java.lang.foreign.ValueLayout.JAVA_BYTE, i) & 0xFFL);
                }
                return v;
            }

            private static int kof_peek_raw32(long addr) {
                var seg = java.lang.foreign.MemorySegment.ofAddress(addr).reinterpret(4);
                int v = 0;
                for (int i = 3; i >= 0; i--) {
                    v = (v << 8) | (seg.get(java.lang.foreign.ValueLayout.JAVA_BYTE, i) & 0xFF);
                }
                return v;
            }

            private static int kof_peek_raw8(long addr) {
                // & 0xFF: UNSIGNED byte — parity with the native `lbu`
                // (the FFM JAVA_BYTE read is signed; the peek contract is 0..255).
                return java.lang.foreign.MemorySegment.ofAddress(addr).reinterpret(1)
                        .get(java.lang.foreign.ValueLayout.JAVA_BYTE, 0L) & 0xFF;
            }

            public static long kof_buffer_peek64(long addr) {
                return kof_peek_raw64(addr);
            }

            public static int kof_buffer_peek32(long addr) {
                return kof_peek_raw32(addr);
            }

            public static int kof_buffer_peek8(long addr) {
                return kof_peek_raw8(addr);
            }

            // ── poke primitive (3.4c): the write counterpart of peek. Raw
            // form = write 8/4/1 bytes LE at any address. The value
            // truncates to the byte width (`as Byte` mod-256 wrap).
            private static void kof_poke_raw64(long addr, long v) {
                var seg = java.lang.foreign.MemorySegment.ofAddress(addr).reinterpret(8);
                for (int i = 0; i < 8; i++) {
                    seg.set(java.lang.foreign.ValueLayout.JAVA_BYTE, i, (byte) (v >> (8 * i)));
                }
            }

            private static void kof_poke_raw32(long addr, int v) {
                var seg = java.lang.foreign.MemorySegment.ofAddress(addr).reinterpret(4);
                for (int i = 0; i < 4; i++) {
                    seg.set(java.lang.foreign.ValueLayout.JAVA_BYTE, i, (byte) (v >> (8 * i)));
                }
            }

            private static void kof_poke_raw8(long addr, int v) {
                var seg = java.lang.foreign.MemorySegment.ofAddress(addr).reinterpret(1);
                seg.set(java.lang.foreign.ValueLayout.JAVA_BYTE, 0L, (byte) v);
            }

            public static void kof_buffer_poke64(long addr, long v) {
                kof_poke_raw64(addr, v);
            }

            public static void kof_buffer_poke32(long addr, int v) {
                kof_poke_raw32(addr, v);
            }

            public static void kof_buffer_poke8(long addr, int v) {
                kof_poke_raw8(addr, v);
            }

            """;

    private static final String BUFFER_FORMS = """
            // ── Buffer forms (pure byte[] math — NO FFM, every JVM-family
            // target incl. ANDROID). Bounds: negative offset or offset+n
            // beyond the cap → honest trap (§640: these never reference
            // java.lang.foreign, so the ANDROID runtime stays FFM-free).
            public static long kof_buffer_peek64_buf(Buffer b, int off) {
                if (b == null || off < 0 || off + 8 > b.data.length) {
                    throw new IllegalArgumentException(
                            "kof.buffer.peek64: offset " + off + " fora do payload de "
                                    + (b == null ? 0 : b.data.length) + " bytes");
                }
                long v = 0;
                for (int i = 7; i >= 0; i--) v = (v << 8) | (b.data[off + i] & 0xFFL);
                return v;
            }

            public static int kof_buffer_peek32_buf(Buffer b, int off) {
                if (b == null || off < 0 || off + 4 > b.data.length) {
                    throw new IllegalArgumentException(
                            "kof.buffer.peek32: offset " + off + " fora do payload de "
                                    + (b == null ? 0 : b.data.length) + " bytes");
                }
                int v = 0;
                for (int i = 3; i >= 0; i--) v = (v << 8) | (b.data[off + i] & 0xFF);
                return v;
            }

            public static int kof_buffer_peek8_buf(Buffer b, int off) {
                if (b == null || off < 0 || off + 1 > b.data.length) {
                    throw new IllegalArgumentException(
                            "kof.buffer.peek8: offset " + off + " fora do payload de "
                                    + (b == null ? 0 : b.data.length) + " bytes");
                }
                return b.data[off] & 0xFF;
            }

            public static void kof_buffer_poke64_buf(Buffer b, int off, long v) {
                if (b == null || off < 0 || off + 8 > b.data.length) {
                    throw new IllegalArgumentException(
                            "kof.buffer.poke64: offset " + off + " fora do payload de "
                                    + (b == null ? 0 : b.data.length) + " bytes");
                }
                for (int i = 0; i < 8; i++) b.data[off + i] = (byte) (v >> (8 * i));
            }

            public static void kof_buffer_poke32_buf(Buffer b, int off, int v) {
                if (b == null || off < 0 || off + 4 > b.data.length) {
                    throw new IllegalArgumentException(
                            "kof.buffer.poke32: offset " + off + " fora do payload de "
                                    + (b == null ? 0 : b.data.length) + " bytes");
                }
                for (int i = 0; i < 4; i++) b.data[off + i] = (byte) (v >> (8 * i));
            }

            public static void kof_buffer_poke8_buf(Buffer b, int off, int v) {
                if (b == null || off < 0 || off + 1 > b.data.length) {
                    throw new IllegalArgumentException(
                            "kof.buffer.poke8: offset " + off + " fora do payload de "
                                    + (b == null ? 0 : b.data.length) + " bytes");
                }
                b.data[off] = (byte) v;
            }

            """;
}
