package dev.kof.compiler;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Suporte dos testes WebSocket ({@code KofWebHardeningTest}/{@code KofWebWsE2ETest}):
 * a máscara e os helpers de frame idênticos entre as duas classes. Vive fora
 * delas (Fase 4/harness, {@code D-TEST-ARCHITECTURE-GO}); os testes e os nomes
 * das classes seguem nos dois arquivos — zero drift de citação.
 */
abstract class WsFrameSupport extends ServerProcessSupport {

    protected static final byte[] MASK = {0x12, 0x34, 0x56, 0x78};

    protected static void writeMaskedFrame(OutputStream out, int opcode, byte[] payload) throws IOException {
        int len = payload.length;
        byte[] frame;
        int headerLen;
        if (len <= 125) {
            frame = new byte[2 + 4 + len];
            frame[1] = (byte) (0x80 | len);
            headerLen = 2;
        } else if (len <= 0xFFFF) {
            frame = new byte[4 + 4 + len];
            frame[1] = (byte) (0x80 | 126);
            frame[2] = (byte) ((len >> 8) & 0xFF);
            frame[3] = (byte) (len & 0xFF);
            headerLen = 4;
        } else {
            frame = new byte[10 + 4 + len];
            frame[1] = (byte) (0x80 | 127);
            for (int i = 0; i < 8; i++) {
                frame[2 + i] = (byte) ((len >> (56 - i * 8)) & 0xFF);
            }
            headerLen = 10;
        }
        frame[0] = (byte) (0x80 | opcode);
        System.arraycopy(MASK, 0, frame, headerLen, 4);
        for (int i = 0; i < len; i++) {
            frame[headerLen + 4 + i] = (byte) (payload[i] ^ MASK[i % 4]);
        }
        out.write(frame);
        out.flush();
    }

    protected static void readFully(java.io.InputStream in, byte[] buf, int off, int len) throws IOException {
        while (len > 0) {
            int n = in.read(buf, off, len);
            if (n < 0) throw new IOException("EOF reading frame");
            off += n;
            len -= n;
        }
    }
}
