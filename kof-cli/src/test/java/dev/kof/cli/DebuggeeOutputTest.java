package dev.kof.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §541: o repasse da saida do debuggee (`kof debug` JVM) copia os bytes
 * EXATAMENTE como foram lidos. O E2E pelo CLI real vive em
 * {@link KofDebugJvmTest}; aqui ficam as bordas que o pipe nao controla:
 * leitura menor depois de uma maior, multibyte partido entre leituras,
 * stream vazio, entrada maior que o buffer e pipe que morre no meio.
 */
class DebuggeeOutputTest {

    /** Um stream que entrega cada pedaco numa leitura separada, como um pipe. */
    private static InputStream chunks(byte[]... parts) {
        return new SequenceInputStream(Collections.enumeration(
                Arrays.stream(parts).map(p -> (InputStream) new ByteArrayInputStream(p)).toList()));
    }

    private static byte[] copied(InputStream from) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DebuggeeOutput.copy(from, new PrintStream(bytes, true, StandardCharsets.UTF_8));
        return bytes.toByteArray();
    }

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void shorterReadAfterLongerOneRelaysNoStaleBytesAndKeepsNewlines() {
        byte[] out = copied(chunks(utf8("Hello world\n"), utf8("Bye\n")));
        assertEquals("Hello world\nBye\n", new String(out, StandardCharsets.UTF_8));
    }

    @Test
    void multibyteSplitAcrossReadsArrivesIntact() {
        byte[] out = copied(chunks(new byte[] {'c', 'a', 'f', (byte) 0xC3}, new byte[] {(byte) 0xA9, '\n'}));
        assertArrayEquals(utf8("café\n"), out);
    }

    @Test
    void bytesAreForwardedUndecoded() {
        byte[] cp1252 = {'O', 'l', (byte) 0xE1, '\r', '\n'};
        assertArrayEquals(cp1252, copied(chunks(cp1252)),
                "o repasse nao reinterpreta os bytes: o charset e o do destino");
    }

    @Test
    void emptyStreamRelaysNothing() {
        assertEquals(0, copied(chunks()).length);
    }

    @Test
    void inputLargerThanTheBufferArrivesWhole() {
        byte[] big = new byte[5000];
        for (int i = 0; i < big.length; i++) {
            big[i] = (byte) ('a' + i % 26);
        }
        assertArrayEquals(big, copied(new ByteArrayInputStream(big)));
    }

    @Test
    void pipeThatDiesMidwayKeepsWhatAlreadyArrived() {
        InputStream dying = new SequenceInputStream(new ByteArrayInputStream(utf8("antes\n")), new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("Stream closed");
            }
        });
        assertEquals("antes\n", new String(copied(dying), StandardCharsets.UTF_8));
    }

    @Test
    void relayRunsOnADaemonThreadUntilEndOfStream() throws InterruptedException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Thread sink = DebuggeeOutput.relay(chunks(utf8("um\n"), utf8("dois\n")),
                new PrintStream(bytes, true, StandardCharsets.UTF_8));
        assertTrue(sink.isDaemon(), "o repasse nunca segura o CLI vivo");
        sink.join(10_000);
        assertFalse(sink.isAlive());
        assertEquals("um\ndois\n", bytes.toString(StandardCharsets.UTF_8));
    }

    @Test
    void debuggeeWritesInTheCharsetOfItsDestination() {
        PrintStream latin1 = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.ISO_8859_1);
        assertEquals(List.of("-Dstdout.encoding=ISO-8859-1", "-Dstderr.encoding=ISO-8859-1"),
                DebuggeeOutput.encodingFlags(latin1));
        PrintStream utf8 = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
        assertEquals(List.of("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8"),
                DebuggeeOutput.encodingFlags(utf8));
    }
}
