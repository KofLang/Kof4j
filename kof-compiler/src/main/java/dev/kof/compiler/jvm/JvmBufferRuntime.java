package dev.kof.compiler.jvm;

/** JVM runtime for {@code kof.buffer} / the nominal {@code Buffer(U8)} type
 *  (D-R3-BUFFER / D6-3, maintainer 21/09). Incremental slice (R6-SCOPE):
 *  a real {@code KofRuntime$Buffer} object wrapping a {@code byte[]}; the
 *  runtime helper allocates and exposes a copy of the bytes. Native/JS never
 *  reach this file (honest gap upstream). */
public final class JvmBufferRuntime {
    private JvmBufferRuntime() {}

    static String source() {
        return """
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
    }
}
