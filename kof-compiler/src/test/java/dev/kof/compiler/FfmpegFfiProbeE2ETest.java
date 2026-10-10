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
 * Graphics/gaming slice 3.4a backend (`D-GRAPHICS-GAMING`, decision F row in
 * {@code D-MAINT-BATCH-0510}, ORDERED 08/10): the vendored upstream LGPL FFmpeg
 * binds and RUNS from Kof through {@code extern} on JVM + Native x86-64.
 *
 * <p>Decision F: the distro GPL build is NEVER taken as-is — discovery considers
 * only {@code KOF_FFMPEG} or the vendored {@code ~/.local/share/kof-ffmpeg} tree
 * ({@code scripts/provision-ffmpeg.sh}); the probe license must NOT be GPLv3+
 * before any decode lands. Absent library → {@code assumeTrue} skip with a named
 * reason (R6, never a false green).
 *
 * <p>The cross faces (riscv64/aarch64) need per-arch FFmpeg builds vendored into
 * the cross sysroot — not part of this slice; they skip with the named reason
 * until {@code scripts/provision-cross-ffmpeg.sh} lands.
 */
class FfmpegFfiProbeE2ETest implements NativeToolchainAssumptions {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String GOLDEN = "avcodec=4129126\nlicense=LGPL version 2.1 or later"
            + "\navformat=4129126\navutil=3998054\nswscale=655718";

    private static final String PROGRAM_TEMPLATE = """
            extern "%s" avcodec_version(): Int
            extern "%s" avcodec_license(): String
            extern "%s" avformat_version(): Int
            extern "%s" avutil_version(): Int
            extern "%s" swscale_version(): Int

            main() {
                println("avcodec=" + avcodec_version())
                println("license=" + avcodec_license())
                println("avformat=" + avformat_version())
                println("avutil=" + avutil_version())
                println("swscale=" + swscale_version())
            }
            """;

    /**
     * The vendored LGPL versioned {@code lib<name>.so.*}, or {@code null} if not
     * provisioned. Decision F: the distro GPL build is NEVER a candidate.
     */
    private static Path vendoredLibDir() {
        String env = System.getenv("KOF_FFMPEG");
        Path libDir = (env != null && !env.isBlank())
                ? Path.of(env, "lib")
                : Path.of(System.getProperty("user.home"), ".local", "share", "kof-ffmpeg", "usr", "lib");
        return Files.isDirectory(libDir) ? libDir : null;
    }

    private static String vendoredLib(String name) {
        Path libDir = vendoredLibDir();
        if (libDir == null) return null;
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

    private static String program() {
        String codec = vendoredLib("avcodec");
        String format = vendoredLib("avformat");
        String util = vendoredLib("avutil");
        String scale = vendoredLib("swscale");
        return PROGRAM_TEMPLATE.formatted(codec, codec, format, util, scale);
    }

    private static void ffmpegLibs(ProcessBuilder pb) {
        Path libDir = vendoredLibDir();
        pb.environment().put("LD_LIBRARY_PATH", libDir.toString());
    }

    @Test
    void ffmpegLicenseIsLgplAndVersionsBindJvm(@TempDir Path tempDir) throws Exception {
        String so = vendoredLib("avcodec");
        assumeTrue(so != null, "FFmpeg LGPL vendido ausente — rode scripts/provision-ffmpeg.sh");
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, program());
        Path out = tempDir.resolve("out-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "jvm compile: " + r.diagnostics().getDiagnostics());
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder pb = new ProcessBuilder(java, "--enable-native-access=ALL-UNNAMED",
                "-cp", out.toString(), "Default.Main");
        ffmpegLibs(pb);
        pb.redirectErrorStream(true);
        String output = run(pb);
        assertTrue(output.contains("license=LGPL"), "decision F: probe license must be LGPL, got: " + output);
        assertTrue(!output.contains("GPLv3"), "decision F: probe license must NOT be GPLv3+, got: " + output);
        assertEquals(GOLDEN, output, "vendored LGPL FFmpeg probe on JVM");
    }

    @Test
    void ffmpegLicenseIsLgplAndVersionsBindNativeX86(@TempDir Path tempDir) throws Exception {
        assumeNativeX86_64();
        String so = vendoredLib("avcodec");
        assumeTrue(so != null, "FFmpeg LGPL vendido ausente — rode scripts/provision-ffmpeg.sh");
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, program());
        Path out = tempDir.resolve("out-nat");
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), "x86-64 compile: " + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), "native binary should exist");
        ProcessBuilder pb = new ProcessBuilder(bin.toString());
        ffmpegLibs(pb);
        pb.redirectErrorStream(true);
        String output = run(pb);
        assertTrue(output.contains("license=LGPL"), "decision F: probe license must be LGPL, got: " + output);
        assertTrue(!output.contains("GPLv3"), "decision F: probe license must NOT be GPLv3+, got: " + output);
        assertEquals(GOLDEN, output, "vendored LGPL FFmpeg probe on Native x86-64");
    }

    @Test
    void ffmpegCrossRiscv64NeedsPerArchBuild(@TempDir Path tempDir) {
        assumeNativeRiscv64WithSysroot();
        String so = vendoredLib("avcodec");
        assumeTrue(so == null,
                "FFmpeg host-vendado presente, mas o face cross riscv64 ainda não é desta fatia: "
                        + "as libs per-arch (riscv64) não estão vendidas no sysroot cross — "
                        + "aguarde scripts/provision-cross-ffmpeg.sh");
    }

    @Test
    void ffmpegCrossAarch64NeedsPerArchBuild(@TempDir Path tempDir) {
        assumeNativeAarch64WithSysroot();
        String so = vendoredLib("avcodec");
        assumeTrue(so == null,
                "FFmpeg host-vendado presente, mas o face cross aarch64 ainda não é desta fatia: "
                        + "as libs per-arch (aarch64) não estão vendidas no sysroot cross — "
                        + "aguarde scripts/provision-cross-ffmpeg.sh");
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
