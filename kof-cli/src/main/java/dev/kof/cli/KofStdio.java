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

    /**
     * kof4shell (CVE-2026-114938, 17/11): o intrínseco Base64 do HotSpot
     * transborda o heap no decodificador AVX-512 (Intel Ice Lake+/AMD Zen 4+,
     * JDK 17.0.6+/21/25 — entradas com padding) e vira RCE sem autenticação.
     * TODO processo JVM que o CLI lança (run/serve/test/workflow/makealive/
     * bench/profile/compare/debug e o driver JS) recebe
     * {@code -XX:-UseBASE64Intrinsics} — o runtime gerado usa
     * {@code java.util.Base64} em 87 sítios (crypto/web/tls/acme/config), e
     * vários deles decodificam entrada de rede. Saída de emergência para quem
     * já roda um JDK corrigido: {@code KOF_B64_INTRINSICS=1} religa (o default
     * segue DESLIGADO até o requisito de JDK mínimo carregar o fix).
     */
    static final String BASE64_INTRINSIC_FLAG = "-XX:-UseBASE64Intrinsics";

    static boolean base64IntrinsicDisabledByDefault() {
        // #1173 (19/11, maintainer decision "version-gate automático"): o
        // intrínseco só é desarmado quando o JDK em uso NÃO é um build
        // corrigido. KOF_B64_INTRINSICS=1 força ligado, =0 força desarmado.
        String env = System.getenv("KOF_B64_INTRINSICS");
        if ("1".equals(env)) return false;
        if ("0".equals(env)) return true;
        return !jdkHasBase64Fix(System.getProperty("java.version", ""));
    }

    /** Builds corrigidos da CVE-2026-114938 por linha: abaixo DESTES a
     *  variante é vulnerável (o intrínseco fica desarmado). Linhas fora da
     *  tabela (<=16 ou desconhecidas) ficam DESARMADAS por conservadorismo;
     *  uma linha MAIOR que a tabela (28+) é presumida corrigida — o fix já
     *  é upstream desde os builds listados. Puro e testável. */
    static boolean jdkHasBase64Fix(String version) {
        int[] major = {17, 21, 25, 27};
        String[] fixed = {"17.0.21.1", "21.0.13.1", "25.0.5.1", "27.0.1.1"};
        if (version == null || version.isBlank()) return false;
        String v = version.trim();
        int dash = v.indexOf('-');
        if (dash > 0) v = v.substring(0, dash);
        int plus = v.indexOf('+');
        if (plus > 0) v = v.substring(0, plus);
        // java.version legado (1.8.0_…) — linha 8 não tem fix: desarmado.
        if (v.startsWith("1.")) return false;
        String[] parts = v.split("\\.");
        int maj;
        try {
            maj = Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            return false;   // desconhecido → conservador
        }
        int idx = -1;
        for (int i = 0; i < major.length; i++) if (major[i] == maj) { idx = i; break; }
        if (idx < 0) return maj > major[major.length - 1];
        return compareVersion(v, fixed[idx]) >= 0;
    }

    /** Comparação numérica por componentes (1, 21 < 1, 22; 25.0.5.1 >= 25.0.5.1). */
    private static int compareVersion(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int x = i < pa.length ? safeInt(pa[i]) : 0;
            int y = i < pb.length ? safeInt(pb[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    private static int safeInt(String s) {
        try {
            int cut = 0;
            while (cut < s.length() && Character.isDigit(s.charAt(cut))) cut++;
            return cut == 0 ? 0 : Integer.parseInt(s.substring(0, cut));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** JVM filha que herda o stdout/stderr do CLI (kof run, kof serve). */
    static List<String> inheritedJvmFlags() {
        var flags = new java.util.ArrayList<String>();
        flags.add("-Dstdout.encoding=" + stdout().name());
        flags.add("-Dstderr.encoding=" + stderr().name());
        if (base64IntrinsicDisabledByDefault()) flags.add(BASE64_INTRINSIC_FLAG);
        return List.copyOf(flags);
    }

    /** JVM filha cuja saída o CLI captura e lê como UTF-8 (kof test, compare, bench...). */
    static List<String> capturedJvmFlags() {
        if (base64IntrinsicDisabledByDefault()) {
            return List.of("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", BASE64_INTRINSIC_FLAG);
        }
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
