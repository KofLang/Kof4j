package dev.kof.cli;

import java.io.BufferedOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Codificação da saída padrão de tudo que o CLI executa — uma regra só para a
 * JVM filha, o KofJS in-process e o próprio CLI: stream ligado a um console usa
 * a codificação do console (é o que ele sabe mostrar); arquivo ou pipe recebe
 * UTF-8, como o Native e o KofJS já escrevem (paridade entre targets).
 *
 * <p>Sem a regra, no Windows a JVM escreve em arquivo/pipe na codificação do
 * sistema (cp1252: {@code á} = E1) e o KofJS escreve UTF-8 cru no console
 * (cp850: {@code á} vira {@code ├í}). Em Linux/macOS sistema e console já são
 * UTF-8 e nada muda.</p>
 *
 * <p>Detecção: a JVM só dá a {@code stdout.encoding}/{@code stderr.encoding}
 * um valor diferente de {@code native.encoding} quando aquele stream é um
 * console. {@link java.io.Console#isTerminal()} sozinho não serve (exige o
 * stdin no console também), mas quando é true o stdout é console com certeza —
 * cobre o console com a mesma code page do sistema ({@code chcp 1252}).</p>
 */
final class KofStdio {

    private KofStdio() {
    }

    /** A regra, sem estado: console → a codificação dele; arquivo/pipe → UTF-8. */
    static Charset streamCharset(String streamEncoding, String nativeEncoding, boolean terminal) {
        boolean console = streamEncoding != null
                && (terminal || !streamEncoding.equalsIgnoreCase(nativeEncoding));
        // code page que a JVM não suporta: a própria JVM cai no default (UTF-8)
        return console && Charset.isSupported(streamEncoding)
                ? Charset.forName(streamEncoding) : StandardCharsets.UTF_8;
    }

    static Charset stdout() {
        java.io.Console console = System.console();
        return streamCharset(System.getProperty("stdout.encoding"),
                System.getProperty("native.encoding"), console != null && console.isTerminal());
    }

    static Charset stderr() {
        return streamCharset(System.getProperty("stderr.encoding"),
                System.getProperty("native.encoding"), false);
    }

    /** JVM filha que herda o stdout/stderr do CLI (kof run, kof serve). */
    static List<String> inheritedJvmFlags() {
        return List.of("-Dstdout.encoding=" + stdout().name(), "-Dstderr.encoding=" + stderr().name());
    }

    /** JVM filha cuja saída o CLI captura e lê como UTF-8 (kof test, compare, bench...). */
    static List<String> capturedJvmFlags() {
        return List.of("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8");
    }

    /**
     * Aplica a regra ao System.out/System.err do próprio CLI. Só troca um
     * stream que ainda é o original da JVM (um teste que capturou System.out
     * fica intacto) e só quando a regra pede outra codificação — na prática,
     * Windows com a saída redirecionada.
     */
    static void install() {
        Charset out = stdout();
        if (replaces(System.out.charset(), jvmCharset("stdout.encoding"), out)) {
            System.setOut(open(FileDescriptor.out, out));
        }
        Charset err = stderr();
        if (replaces(System.err.charset(), jvmCharset("stderr.encoding"), err)) {
            System.setErr(open(FileDescriptor.err, err));
        }
    }

    static boolean replaces(Charset current, Charset jvmDefault, Charset wanted) {
        return current.equals(jvmDefault) && !current.equals(wanted);
    }

    /** O charset com que a JVM abriu o stream (mesmo fallback que ela usa). */
    private static Charset jvmCharset(String property) {
        String name = System.getProperty(property);
        return name != null && Charset.isSupported(name) ? Charset.forName(name) : Charset.defaultCharset();
    }

    private static PrintStream open(FileDescriptor fd, Charset charset) {
        return new PrintStream(new BufferedOutputStream(new FileOutputStream(fd), 8192), true, charset);
    }

    /**
     * Destino para quem escreve UTF-8 cru (o KofJS) num stream de outra
     * codificação: decodifica o UTF-8 e reescreve na do destino. Destino UTF-8
     * devolve o próprio stream. Sequência multibyte partida entre dois write()
     * é completada no seguinte; caractere que o destino não representa vira
     * {@code ?}, como no println da JVM.
     */
    static OutputStream fromUtf8(PrintStream target) {
        Charset charset = target.charset();
        if (charset.equals(StandardCharsets.UTF_8)) return target;
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        return new OutputStream() {
            private byte[] pending = new byte[0];

            @Override public void write(int b) {
                write(new byte[] { (byte) b }, 0, 1);
            }

            @Override public synchronized void write(byte[] buf, int off, int len) {
                ByteBuffer in = ByteBuffer.allocate(pending.length + len).put(pending).put(buf, off, len).flip();
                CharBuffer chars = CharBuffer.allocate(in.remaining());
                decoder.decode(in, chars, false);
                pending = new byte[in.remaining()];
                in.get(pending);
                target.writeBytes(chars.flip().toString().getBytes(charset));
            }

            @Override public void flush() { target.flush(); }
            @Override public void close() { target.flush(); /* dono real fecha */ }
        };
    }
}
