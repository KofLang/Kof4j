package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Paridade media no x86-64 (linha 4 do ledger D-FULL-PARITY-050, MEDIA001):
 * as faces portaveis de Audio (WAV PCM 16-bit) e Video (MP4/MOV) tem o
 * MESMO comportamento byte a byte no nativo que no oraculo JVM
 * (JvmMediaCoreRuntime, ja com o largesize 64-bit do #624 portado para o asm
 * em RuntimeMedia/RuntimeMediaWav).
 *
 * <p>Divergencias DECLARADAS (nao cobertas pelo golden de stdout):
 * (1) cap de 64 handles no nativo (JVM usa mapas sem limite) — travada no
 * teste honesto nativo; (2) mensagem de io-fail do Audio embute o path em
 * vez do texto da IOException; (3) WAV malformado que na JVM estoura em
 * AIOOBE/NegativeArraySize (crash nao-capturavel) vira erro honesto da mesma
 * familia no nativo; (4) path relativo resolve por CWD no nativo e por
 * kof.root na JVM — por isso o app so usa caminhos absolutos.
 */
class MediaNativeE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static boolean toolchain() {
        try {
            return new ProcessBuilder("as", "--version").redirectErrorStream(true)
                    .start().waitFor() == 0
                    && new ProcessBuilder("ld", "--version").redirectErrorStream(true)
                    .start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    // ── fixtures (bytes EXATOS, escritos por Java — nunca por memoria) ────

    private static int be32(byte[] b, int off, long v) {
        b[off] = (byte) (v >> 24); b[off + 1] = (byte) (v >> 16);
        b[off + 2] = (byte) (v >> 8); b[off + 3] = (byte) v;
        return off + 4;
    }

    private static void type4(byte[] b, int off, String t) {
        for (int i = 0; i < 4; i++) b[off + i] = (byte) t.charAt(i);
    }

    private static byte[] clipMp4() {
        // ftyp(20) + free EXTENDIDO (size=1 + largesize=32, payload com bytes
        // >0x7F p/ exercitar o SIGNED no bytes()) + moov(108)/mvhd v0
        // (timescale=1000, duration=3000 -> 3000ms)
        byte[] b = new byte[20 + 32 + 108];
        be32(b, 0, 20); type4(b, 4, "ftyp"); type4(b, 8, "isom");
        be32(b, 20, 1); type4(b, 24, "free"); be32(b, 28, 0); be32(b, 32, 32);
        b[36] = (byte) 0xB8; b[37] = (byte) 0xFE; b[38] = 0x01; b[39] = (byte) 0x80;
        be32(b, 52, 108); type4(b, 56, "moov");
        be32(b, 60, 100); type4(b, 64, "mvhd");
        b[68] = 0;                                // version 0, flags 0
        be32(b, 80, 1000);                        // timescale
        be32(b, 84, 3000);                        // duration
        return b;
    }

    private static byte[] clipZeroSizeMp4() {
        // free com size==0 (box ate o fim do container) ANTES do moov: o scan
        // consome o resto do arquivo e NUNCA ve o moov -> dur 0 nos dois lados
        byte[] b = new byte[16 + 108];
        be32(b, 0, 0); type4(b, 4, "free");
        be32(b, 16, 108); type4(b, 20, "moov");
        be32(b, 24, 100); type4(b, 28, "mvhd");
        be32(b, 44, 1000); be32(b, 48, 3000);
        return b;
    }

    private static byte[] wav(int format, int channels, int rate, int bits, byte[] data) {
        byte[] b = new byte[44 + data.length];
        type4(b, 0, "RIFF"); be32le(b, 4, 36 + data.length); type4(b, 8, "WAVE");
        type4(b, 12, "fmt "); be32le(b, 16, 16);
        le16(b, 20, format); le16(b, 22, channels); le32(b, 24, rate);
        le32(b, 28, rate * channels * 2); le16(b, 32, channels * 2); le16(b, 34, bits);
        type4(b, 36, "data"); be32le(b, 40, data.length);
        System.arraycopy(data, 0, b, 44, data.length);
        return b;
    }

    private static void le16(byte[] b, int off, int v) { b[off] = (byte) v; b[off + 1] = (byte) (v >> 8); }
    private static void le32(byte[] b, int off, int v) { le16(b, off, v & 0xFFFF); le16(b, off + 2, v >> 16); }
    private static void be32le(byte[] b, int off, int v) { le32(b, off, v); } // LE 32 no header WAV

    // ── runners ───────────────────────────────────────────────────────────

    private String runJvm(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM must compile: " + r.diagnostics().getDiagnostics());
        var buf = new ByteArrayOutputStream();
        var old = System.out;
        System.setOut(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try {
            ClassLoader cl = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(old);
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    private String runNative(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), "NATIVE must compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(out.resolve("Default/Main").toString())
                .directory(tmp.toFile()).redirectErrorStream(true).start();
        String s = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int ec = p.waitFor();
        assertEquals(0, ec, "native binary exit " + ec + ":\n" + s);
        return s;
    }

    // ── golden byte a byte ────────────────────────────────────────────────

    @Test
    void audioVideoOnNativeMatchesJvmGolden() throws Exception {
        assumeTrue(toolchain(), "as/ld ausente — paridade nativa non-host-provable");
        Files.write(tmp.resolve("clip.mp4"), clipMp4());
        Files.write(tmp.resolve("clip0.mp4"), clipZeroSizeMp4());
        byte[] pcm = {0x12, 0x34, (byte) 0xDC, (byte) 0xFE, 0, 0,
                (byte) 0xFF, 0x7F, 0, (byte) 0x80, (byte) 0xFF, 0,
                (byte) 0x80, 0, 0, 0x01};
        Files.write(tmp.resolve("note.wav"), wav(1, 1, 8000, 16, pcm));
        Files.write(tmp.resolve("float.wav"), wav(3, 1, 8000, 16, pcm));
        Files.write(tmp.resolve("pcm24.wav"), wav(1, 1, 8000, 24, pcm));
        Files.write(tmp.resolve("bad.wav"), "not riff at all......".getBytes(StandardCharsets.US_ASCII));
        String t = tmp.toString().replace('\\', '/');

        Files.writeString(tmp.resolve("MED.kf"), """
                main() {
                    var v = Video.open("%1$s/clip.mp4")
                    println(v.format())
                    println(v.durationMs())
                    println(v.size())
                    println(v.bytes())
                    println(v.path())
                    v.close()
                    try { v.size() } catch (String e) { println(e) }
                    try { Video.open("%1$s/nope.mp4") } catch (String e) { println(e) }
                    var z = Video.open("%1$s/clip0.mp4")
                    println(z.durationMs())
                    z.close()
                    var a = Audio.openWav("%1$s/note.wav")
                    println(a.sampleRate())
                    println(a.durationMs())
                    println(a.pcmBytes())
                    println(a.saveWav("%1$s/deep-x/out.wav"))
                    try { Audio.openWav("%1$s/float.wav") } catch (String e) { println(e) }
                    try { Audio.openWav("%1$s/pcm24.wav") } catch (String e) { println(e) }
                    try { Audio.openWav("%1$s/bad.wav") } catch (String e) { println(e) }
                    var n = Audio.openWav("%1$s/note.wav")
                    try { n.saveWav("%1$s/deep-x/deeper/out2.wav") } catch (String e) { println("BAD") }
                    println(n.durationMs())
                }
                """.formatted(t));

        String jvm = runJvm(tmp.resolve("MED.kf"), tmp.resolve("o-med-jvm"));
        Files.move(tmp.resolve("deep-x/out.wav"), tmp.resolve("out-jvm.wav"),
                StandardCopyOption.REPLACE_EXISTING);
        Files.move(tmp.resolve("deep-x/deeper/out2.wav"), tmp.resolve("out2-jvm.wav"),
                StandardCopyOption.REPLACE_EXISTING);
        String nat = runNative(tmp.resolve("MED.kf"), tmp.resolve("o-med-nat"));
        assertEquals(jvm, nat, "media JVM == NATIVE byte-for-byte");

        // golden da FORMA (medido no JVM) — trava o conteudo, nao so a igualdade
        assertTrue(jvm.startsWith("mp4\n3000\n"), "forma esperada: " + jvm);
        assertTrue(jvm.contains("invalid video: 1"), jvm);
        assertTrue(jvm.contains("file not found: " + t + "/nope.mp4"), jvm);
        assertTrue(jvm.contains("[18, 52, -36, -2, 0, 0, -1, 127, 0, -128, -1, 0, -128, 0, 0, 1]"),
                "bytes SIGNED dos dois lados: " + jvm);
        assertTrue(jvm.contains("unsupported WAV: codec 3 (needs PCM 1)"), jvm);
        assertTrue(jvm.contains("unsupported WAV: needs PCM 16-bit (bits=24)"), jvm);
        assertTrue(jvm.contains("not a WAV RIFF: " + t + "/bad.wav"), jvm);

        // o WAV gravado deve ser BIT-FOR-BIT igual nos dois alvos (o par do
        // deeper prova que o mkdirs RECURSIVO do nativo criou a cadeia inteira)
        assertArrayEquals(Files.readAllBytes(tmp.resolve("out-jvm.wav")),
                Files.readAllBytes(tmp.resolve("deep-x/out.wav")),
                "saveWav gerou bytes diferentes");
        assertArrayEquals(Files.readAllBytes(tmp.resolve("out2-jvm.wav")),
                Files.readAllBytes(tmp.resolve("deep-x/deeper/out2.wav")),
                "saveWav/deeper gerou bytes diferentes");
        assertFalse(jvm.contains("BAD"), "saveWav em deep dir deve funcionar nos dois");
    }

    // ── honestidade nativa declarada: cap de 64 handles ───────────────────

    @Test
    void mediaCap64HonestOnNative() throws Exception {
        assumeTrue(toolchain(), "as/ld ausente — paridade nativa non-host-provable");
        Files.write(tmp.resolve("clip.mp4"), clipMp4());
        String t = tmp.toString().replace('\\', '/');
        Files.writeString(tmp.resolve("CAP.kf"), """
                main() {
                    var i = 0
                    var held = 0
                    while (i < 66) {
                        try {
                            var v = Video.open("%1$s/clip.mp4")
                            held = held + 1
                        } catch (String e) {
                            println("THROW:" + e)
                        }
                        i = i + 1
                    }
                    println("held=" + held)
                }
                """.formatted(t));
        CompilationResult jr = driver.compile(tmp.resolve("CAP.kf"), tmp.resolve("o-cap-jvm"), Target.JVM);
        assertTrue(jr.success(), "JVM compiles: " + jr.diagnostics().getDiagnostics());
        // JVM sem limite: 66 abertos, nenhum throw (divergencia DECLARADA — o
        // teste nao compara saidas, trava so a honestidade do nativo)
        String nat = runNative(tmp.resolve("CAP.kf"), tmp.resolve("o-cap-nat"));
        assertTrue(nat.contains("THROW:media: too many open handles (max 64)"),
                "65o handle deve lancar com diagnostico exato: " + nat);
        assertEquals(2, nat.split("THROW:").length - 1, "65o e 66o laniam; os 64 primeiros ok");
        assertTrue(nat.contains("held=64"), nat);
    }

    @Test
    void imageMicStayGapAndCrossNeverLeaks() throws Exception {
        assumeTrue(toolchain(), "as/ld ausente — face x86 non-host-provable");
        String img = "main() {\n    var i = Image.open(\"x.png\")\n    println(i.width())\n}\n";
        String mic = "main() {\n    var a = Mic.record(1)\n    println(a.durationMs())\n}\n";
        String vid = "main() {\n    var v = Video.open(\"x.mp4\")\n    println(v.durationMs())\n}\n";
        Files.writeString(tmp.resolve("GAP-IMG.kf"), img);
        Files.writeString(tmp.resolve("GAP-MIC.kf"), mic);
        Files.writeString(tmp.resolve("GAP-VID.kf"), vid);

        // (a) Image/Mic continuam gap no x86 (decoder/encoder = regra 6, nunca aberta)
        for (String[] c : new String[][]{{"IMG", "MEDIA001"}, {"MIC", "MEDIA003"}}) {
            CompilationResult r = driver.compile(tmp.resolve("GAP-" + c[0] + ".kf"),
                    tmp.resolve("o-gap-" + c[0]), Target.NATIVE);
            assertFalse(r.success(), c[0] + " deve continuar " + c[1] + " no x86");
            final String code = c[1];
            assertTrue(r.diagnostics().getDiagnostics().stream()
                            .anyMatch(d -> code.equals(d.code())),
                    c[0] + " x86: esperava " + code + ": " + r.diagnostics().getDiagnostics());
        }
        // (b) Video COMPILA no x86 (controle positivo do gate)
        CompilationResult ok = driver.compile(tmp.resolve("GAP-VID.kf"),
                tmp.resolve("o-gap-vid-x86"), Target.NATIVE);
        assertTrue(ok.success(), "Video deve compilar no x86: " + ok.diagnostics().getDiagnostics());
        // (c) o gate NAO vaza: no cross, Video/Audio = MEDIA001 (runtime nao portado la)
        for (Target cross : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            CompilationResult r = driver.compile(tmp.resolve("GAP-VID.kf"),
                    tmp.resolve("o-gap-vid-" + cross), cross);
            assertFalse(r.success(), cross + " deve recusar Video (runtime nao portado la)");
            assertTrue(r.diagnostics().getDiagnostics().stream()
                            .anyMatch(d -> "MEDIA001".equals(d.code())),
                    cross + ": esperava MEDIA001: " + r.diagnostics().getDiagnostics());
        }
    }
}
