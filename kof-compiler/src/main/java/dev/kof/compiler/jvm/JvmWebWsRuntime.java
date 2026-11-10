package dev.kof.compiler.jvm;

/**
 * Fragmento do source do KofRuntime gerado (REFACTOR-500 Fase 8): o handshake
 * e o framing WebSocket — {@code wsAccept}, {@code WsSender} e
 * {@code WsFrame}. Extraídos de {@code JvmWebCoreRuntime} quando o #1012
 * (Set-Cookie repetido, CR/LF recusado) o levou a 608 linhas; a concatenação
 * em {@code JvmRuntime.source()} mantém a ordem, então nada do runtime
 * gerado muda de posição visível.
 */
public final class JvmWebWsRuntime {

    private JvmWebWsRuntime() {}

    static String source() {
        return """
                public static String wsAccept(String secWebSocketKey) {
                    try {
                        String concat = secWebSocketKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
                        java.security.MessageDigest sha1 = java.security.MessageDigest.getInstance("SHA-1");
                        byte[] hash = sha1.digest(concat.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        return java.util.Base64.getEncoder().encodeToString(hash);
                    } catch (Exception e) {
                        throw new RuntimeException("SHA-1 unavailable on JVM", e);
                    }
                }
                /** Interface para o KofRuntime acessar o envio WS sem ciclo de import. */
                public interface WsSender {
                    void sendText(String s);
                }
                public static final class WsFrame {
                    public final boolean fin;
                    public final int opcode;        // 0x1=TEXT, 0x2=BINARY, 0x8=CLOSE, 0x9=PING, 0xA=PONG, 0x0=continuation
                    public final byte[] payload;

                    WsFrame(boolean fin, int opcode, byte[] payload) {
                        this.fin = fin;
                        this.opcode = opcode;
                        this.payload = payload;
                    }

                    /**
                     * Encode a frame WITHOUT masking (server -> client per RFC 6455).
                     * FIN=1 by default; use overload for FIN=0.
                     */
                    public static byte[] encode(int opcode, byte[] payload, boolean fin) {
                        // Header byte 1: FIN(1) RSV(3) OPCODE(4)
                        int b1 = (fin ? 0x80 : 0x00) | (opcode & 0x0F);
                        // Header byte 2: MASK(1) + LEN(7)
                        long len = payload.length;
                        byte[] header;
                        int headerLen;
                        if (len <= 125) {
                            header = new byte[2];
                            header[1] = (byte) len;       // MASK=0
                            headerLen = 2;
                        } else if (len <= 0xFFFF) {
                            header = new byte[4];
                            header[1] = (byte) 126;       // MASK=0
                            header[2] = (byte) ((len >> 8) & 0xFF);
                            header[3] = (byte) (len & 0xFF);
                            headerLen = 4;
                        } else {
                            header = new byte[10];
                            header[1] = (byte) 127;       // MASK=0
                            for (int i = 0; i < 8; i++) {
                                header[2 + i] = (byte) ((len >> (56 - i * 8)) & 0xFF);
                            }
                            headerLen = 10;
                        }
                        header[0] = (byte) b1;
                        byte[] out = new byte[headerLen + (int) len];
                        System.arraycopy(header, 0, out, 0, headerLen);
                        System.arraycopy(payload, 0, out, headerLen, (int) len);
                        return out;
                    }

                    /**
                     * Decode a server-received (client -> server) frame. Payload is UNMASKED in
                     * the returned WsFrame. RFC 6455 requires client->server masking; we reject
                     * (close 1002) frames missing the mask bit.
                     */
                    public static WsFrame decodeClient(byte[] buf) throws java.io.IOException {
                        if (buf.length < 2) throw new java.io.IOException("frame too short: " + buf.length);
                        boolean fin = (buf[0] & 0x80) != 0;
                        int opcode = buf[0] & 0x0F;
                        boolean masked = (buf[1] & 0x80) != 0;
                        if (!masked) throw new java.io.IOException("client frame must be masked (RFC 6455 §5.1)");
                        long len = buf[1] & 0x7F;
                        int idx = 2;
                        if (len == 126) {
                            if (buf.length < 4) throw new java.io.IOException("frame truncated on extended length");
                            len = ((long)(buf[2] & 0xFF) << 8) | (buf[3] & 0xFF);
                            idx = 4;
                        } else if (len == 127) {
                            if (buf.length < 10) throw new java.io.IOException("frame truncated on extended length");
                            len = 0;
                            for (int i = 0; i < 8; i++) {
                                len = (len << 8) | (buf[2 + i] & 0xFF);
                            }
                            idx = 10;
                        }
                        if (len > maxFrameBytes.get()) {
                            throw new java.io.IOException("frame too large: " + len + " > " + maxFrameBytes.get());
                        }
                        if (buf.length < idx + 4 + len) throw new java.io.IOException("frame truncated (payload)");
                        byte[] mask = new byte[4];
                        System.arraycopy(buf, idx, mask, 0, 4);
                        idx += 4;
                        byte[] payload = new byte[(int) len];
                        for (long i = 0; i < len; i++) {
                            payload[(int) i] = (byte) (buf[idx + (int) i] ^ mask[(int) (i % 4)]);
                        }
                        return new WsFrame(fin, opcode, payload);
                    }

                    public static final java.util.concurrent.atomic.AtomicLong maxFrameBytes =
                            new java.util.concurrent.atomic.AtomicLong(1L << 20);        // 1 MiB
                    public static final java.util.concurrent.atomic.AtomicLong maxMessageBytes =
                            new java.util.concurrent.atomic.AtomicLong(8L << 20);     // 8 MiB
                    public static final int CLOSE_TOO_BIG = 1009;
                    public static final int CLOSE_PROTOCOL_ERROR = 1002;
                    public static final int CLOSE_UNSUPPORTED = 1003;
                }
""";
    }
}
