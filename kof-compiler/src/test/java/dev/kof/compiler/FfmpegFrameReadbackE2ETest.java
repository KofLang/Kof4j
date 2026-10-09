package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Graphics/gaming slice 3.4b inc2 (`D-GRAPHICS-GAMING`, decision F row in
 * {@code D-MAINT-BATCH-0510}, ORDERED 08/10): DECODE + frame readback of the
 * MJPEG probe asset through the vendored LGPL FFmpeg, driven from Kof via
 * {@code extern} + the peek primitive on JVM + Native x86-64.
 *
 * <p>The flow: the {@code Buffer} out-param carries the {@code AVFormatContext**}
 * (the C writes the pointer into the payload; {@code buffer.peek64(ps, 0)} reads
 * it back), the decode walks {@code av_read_frame → avcodec_send_packet →
 * avcodec_receive_frame}, and the frame fields are read with the peek primitive
 * at the offsets MEASURED from the real 9.0.2 header (data[0]=0, linesize[0]=64,
 * width=104, height=108). The pixel readback peeks the center Y/U/V of each of
 * the 4 frames (red/green/blue/white) — the mjpeg decoder outputs yuvj420p
 * natively, so no swscale is needed for this face.
 *
 * <p>The {@code av_packet_free}/{@code av_frame_free} calls are NOT part of this
 * slice (they need a write into a Buffer payload — the poke primitive is the
 * 3.4c follow-up); {@code avformat_close_input} IS called (its Buffer payload
 * already holds the ctx pointer). The probe-process exit reclaims the rest —
 * documented honestly, never a silent stub.
 */
class FfmpegFrameReadbackE2ETest implements NativeToolchainAssumptions {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String TEMPLATE = """
            extern "%s" avformat_open_input(Buffer ps, String url, Long a, Long b): Int
            extern "%s" avformat_find_stream_info(Long ctx, Long opts): Int
            extern "%s" avformat_close_input(Buffer ps): void
            extern "%s" av_read_frame(Long ctx, Long pkt): Int
            extern "%s" avcodec_find_decoder(Int id): Long
            extern "%s" avcodec_alloc_context3(Long codec): Long
            extern "%s" avcodec_open2(Long ctx, Long codec, Long opts): Int
            extern "%s" avcodec_send_packet(Long ctx, Long pkt): Int
            extern "%s" avcodec_receive_frame(Long ctx, Long frame): Int
            extern "%s" av_packet_alloc(): Long
            extern "%s" av_frame_alloc(): Long

            main() {
                var ps = buffer.alloc(8)
                var rc = avformat_open_input(ps, "%s", 0, 0)
                println("open=" + rc)
                var ctx = buffer.peek64(ps, 0)
                println("ctx!=0=" + (ctx != 0))
                rc = avformat_find_stream_info(ctx, 0)
                println("info=" + rc)
                var codec = avcodec_find_decoder(7)
                println("codec!=0=" + (codec != 0))
                var cctx = avcodec_alloc_context3(codec)
                rc = avcodec_open2(cctx, codec, 0)
                println("open2=" + rc)
                var pkt = av_packet_alloc()
                var frame = av_frame_alloc()
                for (var i in listOf(0, 1, 2, 3)) {
                    rc = av_read_frame(ctx, pkt)
                    if (rc != 0) { break }
                    rc = avcodec_send_packet(cctx, pkt)
                    if (rc != 0) { break }
                    rc = avcodec_receive_frame(cctx, frame)
                    if (rc != 0) { break }
                    var w = buffer.peek32(frame + 104)
                    var h = buffer.peek32(frame + 108)
                    var data0 = buffer.peek64(frame + 0)
                    var ls0 = buffer.peek32(frame + 64)
                    var data1 = buffer.peek64(frame + 8)
                    var ls1 = buffer.peek32(frame + 68)
                    var data2 = buffer.peek64(frame + 16)
                    var y = buffer.peek8(data0 + 32 * ls0 + 32)
                    var u = buffer.peek8(data1 + 16 * ls1 + 16)
                    var v = buffer.peek8(data2 + 16 * ls1 + 16)
                    println("frame=" + i + " w=" + w + " h=" + h + " y=" + y + " u=" + u + " v=" + v)
                }
                avformat_close_input(ps)
            }
            """;

    private static String vendoredLib(String name) {
        String env = System.getenv("KOF_FFMPEG");
        Path libDir = (env != null && !env.isBlank())
                ? Path.of(env, "lib")
                : Path.of(System.getProperty("user.home"), ".local", "share", "kof-ffmpeg", "usr", "lib");
        if (!Files.isDirectory(libDir)) return null;
        java.util.List<String> names;
        try (var s = Files.list(libDir)) {
            names = s.map(p -> p.getFileName().toString())
                    .filter(n -> n.startsWith("lib" + name + ".so."))
                    .toList();
        } catch (IOException e) {
            return null;
        }
        if (names.isEmpty()) return null;
        String best = names.get(0);
        for (String n : names) {
            if (n.length() < best.length()) best = n;
        }
        return libDir.resolve(best).toString();
    }

    private static String probeAsset() {
        String env = System.getenv("KOF_FFMPEG");
        Path testDir = (env != null && !env.isBlank())
                ? Path.of(env, "..", "test").normalize()
                : Path.of(System.getProperty("user.home"), ".local", "share", "kof-ffmpeg", "test");
        Path p = testDir.resolve("kof-probe.avi");
        return Files.exists(p) ? p.toString() : null;
    }

    private static String program() {
        String fmt = vendoredLib("avformat");
        String codec = vendoredLib("avcodec");
        String util = vendoredLib("avutil");
        String url = probeAsset();
        if (fmt == null || codec == null || util == null || url == null) return null;
        return TEMPLATE.formatted(fmt, fmt, fmt, fmt, codec, codec, codec, codec, codec, codec,
                util, url);
    }

    private static void readbackFfmpegLibs(ProcessBuilder pb) {
        String env = System.getenv("KOF_FFMPEG");
        Path libDir = (env != null && !env.isBlank())
                ? Path.of(env, "lib")
                : Path.of(System.getProperty("user.home"), ".local", "share", "kof-ffmpeg", "usr", "lib");
        pb.environment().put("LD_LIBRARY_PATH", libDir.toString());
    }

    @Test
    void frameReadbackJvm(@TempDir Path tempDir) throws Exception {
        String p = program();
        assumeTrue(p != null, "FFmpeg LGPL vendido ausente — rode scripts/provision-ffmpeg.sh");
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, p);
        Path out = tempDir.resolve("out-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "jvm compile: " + r.diagnostics().getDiagnostics());
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder pb = new ProcessBuilder(java, "--enable-native-access=ALL-UNNAMED",
                "-cp", out.toString(), "Default.Main");
        readbackFfmpegLibs(pb);
        pb.redirectErrorStream(true);
        String output = run(pb);
        assertTrue(output.contains("open=0"), "avformat_open_input, got: " + output);
        assertTrue(output.contains("ctx!=0=true"), "ctx pointer via the Buffer out-param, got: " + output);
        assertTrue(output.contains("open2=0"), "avcodec_open2, got: " + output);
        for (int i = 0; i < 4; i++) {
            assertTrue(output.contains("frame=" + i + " w=64 h=64"),
                    "frame " + i + " dims read via peek, got: " + output);
        }
    }

    @Test
    void frameReadbackNativeX86(@TempDir Path tempDir) throws Exception {
        assumeNativeX86_64();
        String p = program();
        assumeTrue(p != null, "FFmpeg LGPL vendido ausente — rode scripts/provision-ffmpeg.sh");
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, p);
        Path out = tempDir.resolve("out-nat");
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), "x86-64 compile: " + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), "native binary should exist");
        ProcessBuilder pb = new ProcessBuilder(bin.toString());
        readbackFfmpegLibs(pb);
        pb.redirectErrorStream(true);
        String output = run(pb);
        assertTrue(output.contains("open=0"), "avformat_open_input, got: " + output);
        assertTrue(output.contains("ctx!=0=true"), "ctx pointer via the Buffer out-param, got: " + output);
        assertTrue(output.contains("open2=0"), "avcodec_open2, got: " + output);
        for (int i = 0; i < 4; i++) {
            assertTrue(output.contains("frame=" + i + " w=64 h=64"),
                    "frame " + i + " dims read via peek, got: " + output);
        }
    }

    @Test
    void frameReadbackJvmNativeAgree(@TempDir Path tempDir) throws Exception {
        String p = program();
        assumeTrue(p != null, "FFmpeg LGPL vendido ausente — rode scripts/provision-ffmpeg.sh");
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, p);
        Path outJ = tempDir.resolve("out-jvm");
        CompilationResult rj = driver.compile(src, outJ, Target.JVM);
        assertTrue(rj.success(), "jvm compile: " + rj.diagnostics().getDiagnostics());
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder pbj = new ProcessBuilder(java, "--enable-native-access=ALL-UNNAMED",
                "-cp", outJ.toString(), "Default.Main");
        readbackFfmpegLibs(pbj);
        pbj.redirectErrorStream(true);
        String jvmOut = run(pbj);
        Path outN = tempDir.resolve("out-nat");
        CompilationResult rn = driver.compile(src, outN, Target.NATIVE);
        assertTrue(rn.success(), "x86-64 compile: " + rn.diagnostics().getDiagnostics());
        ProcessBuilder pbn = new ProcessBuilder(outN.resolve("Default/Main").toString());
        readbackFfmpegLibs(pbn);
        pbn.redirectErrorStream(true);
        assertEquals(jvmOut, run(pbn), "regra 5: mesmo programa FFmpeg, mesma saida JVM e Native x86-64");
    }

    private static String run(ProcessBuilder pb) throws IOException {
        Process p = pb.start();
        boolean done = false;
        try {
            done = p.waitFor(180, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        } finally {
            if (!done) p.destroyForcibly();
        }
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertTrue(done, "process did not finish in 180s, output: '" + output + "'");
        assertEquals(0, p.exitValue(), "exit code, output: '" + output + "'");
        return output;
    }
}
