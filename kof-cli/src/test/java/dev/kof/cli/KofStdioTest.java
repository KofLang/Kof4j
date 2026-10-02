package dev.kof.cli;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regra de codificação da saída padrão (KofStdio): console → code page do
 * console; arquivo/pipe → UTF-8. Os casos são os medidos no Windows (console
 * cp850, sistema cp1252) e o Linux/macOS (tudo UTF-8, nada muda).
 */
class KofStdioTest {

    private static final Charset CP850 = Charset.forName("IBM850");
    private static final Charset CP1252 = Charset.forName("windows-1252");

    @Test
    void consoleKeepsItsCodePage() {
        assertEquals(CP850, KofStdio.streamCharset("cp850", "Cp1252", false));
        assertEquals(CP850, KofStdio.streamCharset("cp850", "Cp1252", true));
    }

    @Test
    void redirectedStreamGetsUtf8InsteadOfTheSystemCodePage() {
        assertEquals(StandardCharsets.UTF_8, KofStdio.streamCharset("Cp1252", "Cp1252", false));
    }

    @Test
    void terminalWithTheSystemCodePageIsStillAConsole() {
        // chcp 1252: console e sistema iguais — só o isTerminal() distingue do redirect
        assertEquals(CP1252, KofStdio.streamCharset("Cp1252", "Cp1252", true));
    }

    @Test
    void utf8SystemsAreUnchanged() {
        assertEquals(StandardCharsets.UTF_8, KofStdio.streamCharset("UTF-8", "UTF-8", false));
        assertEquals(StandardCharsets.UTF_8, KofStdio.streamCharset("UTF-8", "UTF-8", true));
    }

    @Test
    void missingOrUnsupportedEncodingFallsBackLikeTheJvm() {
        assertEquals(StandardCharsets.UTF_8, KofStdio.streamCharset(null, "Cp1252", true));
        assertEquals(StandardCharsets.UTF_8, KofStdio.streamCharset("x-kof-sem-suporte", "Cp1252", false));
    }

    @Test
    void installReplacesOnlyTheJvmOriginalStreamAndOnlyWhenNeeded() {
        assertTrue(KofStdio.replaces(CP1252, CP1252, StandardCharsets.UTF_8), "Windows redirecionado");
        assertFalse(KofStdio.replaces(CP850, CP850, CP850), "console: já está certo");
        assertFalse(KofStdio.replaces(StandardCharsets.UTF_8, CP1252, StandardCharsets.UTF_8),
                "System.out capturado por um teste não é o original da JVM");
    }

    @Test
    void capturedJvmAlwaysWritesUtf8() {
        assertTrue(KofStdio.capturedJvmFlags().contains("-Dstdout.encoding=UTF-8"));
        assertTrue(KofStdio.capturedJvmFlags().contains("-Dstderr.encoding=UTF-8"));
    }

    @Test
    void utf8TargetIsUsedAsIs() {
        PrintStream target = new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8);
        assertSame(target, KofStdio.fromUtf8(target));
    }

    @Test
    void utf8IsRewrittenInTheConsoleCodePage() throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        OutputStream out = KofStdio.fromUtf8(new PrintStream(sink, true, CP850));
        out.write("Olá".getBytes(StandardCharsets.UTF_8));
        out.flush();
        assertArrayEquals(new byte[] { 'O', 'l', (byte) 0xA0 }, sink.toByteArray(), "á em cp850 = A0");
    }

    @Test
    void multibyteSequenceSplitAcrossWritesIsCompleted() throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        OutputStream out = KofStdio.fromUtf8(new PrintStream(sink, true, CP850));
        for (byte b : "Olá".getBytes(StandardCharsets.UTF_8)) out.write(b);
        out.flush();
        assertArrayEquals(new byte[] { 'O', 'l', (byte) 0xA0 }, sink.toByteArray());
    }

    @Test
    void unmappableAndMalformedBecomeQuestionMark() throws Exception {
        ByteArrayOutputStream sink = new ByteArrayOutputStream();
        OutputStream out = KofStdio.fromUtf8(new PrintStream(sink, true, CP850));
        out.write("ok ✓".getBytes(StandardCharsets.UTF_8));
        out.write(new byte[] { (byte) 0xFF, '!' });
        out.flush();
        assertEquals("ok ??!", sink.toString(CP850));
    }
}
