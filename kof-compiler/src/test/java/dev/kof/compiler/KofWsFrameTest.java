package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import dev.kof.compiler.jvm.JvmRuntime;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests for the RFC 6455 frame codec generated into KofRuntime.
 */
class KofWsFrameTest {

    private static final byte[] MASK = {0x12, 0x34, 0x56, 0x78};
    private static final Class<?> WS_FRAME = loadWsFrame();

    private static Class<?> loadWsFrame() {
        try {
            Path out = Files.createTempDirectory("kof-ws-frame-test");
            JvmRuntime.ensureCompiled(out, List.of(), false);
            URLClassLoader loader = new URLClassLoader(
                    new URL[]{out.toUri().toURL()},
                    KofWsFrameTest.class.getClassLoader());
            return Class.forName("dev.kof.runtime.KofRuntime$WsFrame", true, loader);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static byte[] encode(int opcode, byte[] payload, boolean fin) throws Exception {
        return (byte[]) WS_FRAME.getMethod("encode", int.class, byte[].class, boolean.class)
                .invoke(null, opcode, payload, fin);
    }

    private static Object decodeClient(byte[] frame) throws Exception {
        try {
            return WS_FRAME.getMethod("decodeClient", byte[].class)
                    .invoke(null, (Object) frame);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) throw ex;
            throw e;
        }
    }

    private static Object field(Object frame, String name) throws Exception {
        Field f = WS_FRAME.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(frame);
    }

    private static byte[] masked(byte[] serverFrame) {
        long len = serverFrame[1] & 0x7F;
        int headerLen;
        if (len == 126) headerLen = 4;
        else if (len == 127) headerLen = 10;
        else headerLen = 2;
        byte[] out = new byte[serverFrame.length + 4];
        System.arraycopy(serverFrame, 0, out, 0, headerLen);
        out[1] = (byte) (serverFrame[1] | 0x80);
        System.arraycopy(MASK, 0, out, headerLen, 4);
        for (int i = 0; i < serverFrame.length - headerLen; i++) {
            out[headerLen + 4 + i] = (byte) (serverFrame[headerLen + i] ^ MASK[i % 4]);
        }
        return out;
    }

    private static byte[] oversizedClientFrame() {
        byte[] out = new byte[14];
        out[0] = (byte) 0x81;
        out[1] = (byte) 0xFF;
        long len = (1L << 20) + 1;
        for (int i = 0; i < 8; i++) {
            out[2 + i] = (byte) ((len >> (56 - i * 8)) & 0xFF);
        }
        System.arraycopy(MASK, 0, out, 10, 4);
        return out;
    }

    private static byte[] payload(int size) {
        byte[] payload = new byte[size];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i % 251);
        }
        return payload;
    }

    private static void assertDecoded(byte[] payload, Object frame) throws Exception {
        assertTrue((Boolean) field(frame, "fin"));
        assertEquals(0x1, field(frame, "opcode"));
        assertArrayEquals(payload, (byte[]) field(frame, "payload"));
    }

    @Test
    void encode_decode_text_small_payload() throws Exception {
        byte[] payload = "hello ws".getBytes(StandardCharsets.UTF_8);
        assertDecoded(payload, decodeClient(masked(encode(0x1, payload, true))));
    }

    @Test
    void encode_decode_text_medium_payload() throws Exception {
        byte[] payload = payload(300);
        assertDecoded(payload, decodeClient(masked(encode(0x1, payload, true))));
    }

    @Test
    void encode_decode_text_huge_payload() throws Exception {
        byte[] payload = payload(70_000);
        assertDecoded(payload, decodeClient(masked(encode(0x1, payload, true))));
    }

    @Test
    void decode_rejects_unmasked_frame() {
        assertThrows(IOException.class,
                () -> decodeClient(encode(0x1, "x".getBytes(StandardCharsets.UTF_8), true)));
    }

    @Test
    void decode_rejects_oversized_frame() {
        assertThrows(IOException.class, () -> decodeClient(oversizedClientFrame()));
    }

    @Test
    void round_trip_text_with_correct_mask() throws Exception {
        byte[] payload = "masked".getBytes(StandardCharsets.UTF_8);
        byte[] client = masked(encode(0x1, payload, true));
        assertNotEquals(0, client[1] & 0x80);
        assertDecoded(payload, decodeClient(client));

        byte[] server = encode(0x1, payload, true);
        assertEquals(0, server[1] & 0x80);
        assertThrows(IOException.class, () -> decodeClient(server));
    }

    @Test
    void encode_unmasked_server_frame() throws Exception {
        for (int size : new int[]{0, 125, 126, 65_536}) {
            byte[] frame = encode(0x1, payload(size), true);
            assertEquals(0, frame[1] & 0x80, "mask bit for size " + size);
        }
    }
}
