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
 * Graphics/gaming slice 3.3c (`D-GRAPHICS-GAMING`, `G1` SDL3 in
 * `D-MAINT-BATCH-0510`): the SDL3 audio <em>stream submit path</em> over the
 * vendored stack, on JVM + Native x86-64 + the cross backends riscv64/aarch64
 * (under qemu), headless (`SDL_AUDIODRIVER=dummy`, device left paused so no
 * background thread consumes the queue — every assertion below is exact).
 *
 * <p>{@link Sdl3AudioE2ETest} pins the device negotiation; this test pins
 * the data path that a future mixer-voice routing will submit through:
 * open a device stream on the negotiated device format, {@code Put} bytes,
 * observe the converted-available count, {@code Get} them back, then
 * {@code Clear} and destroy. No audible output is produced or asserted.
 *
 * <p>Measured SDL/FFI facts (never guessed):
 * <ul>
 *   <li>A zeroed 12-byte spec opens the device (dummy defaults), and the
 *   negotiated device format read back with
 *   {@code SDL_GetAudioDeviceFormat} is reused as the stream spec — a zeroed
 *   spec is <em>rejected</em> for streams ({@code src_spec->format is
 *   invalid}).
 *   <li>The stream converts to SDL's internal F32 mixer format regardless:
 *   {@code SDL_GetAudioStreamFormat} reads back {@code src=S16/2ch/44100}
 *   but {@code dst=F32/2ch/44100}, so 16 submitted bytes surface as exactly
 *   32 available/returned bytes — pinned, documents the mixer stage. The
 *   converted bytes themselves are pinned too ({@code 'A'} = S16
 *   {@code 0x4141} → F32 {@code 0,130,2,63} repeating): the S16→F32 path is
 *   exact IEEE arithmetic, identical on every FPU, so any backend change
 *   fails loudly. A {@code \0}-in-source silent cycle was measured and
 *   dropped instead: NUL bytes marshal fine on JVM but break the native
 *   assembler (raw control bytes in the generated {@code .s}), and Kof has
 *   no runtime NUL-string constructor — an honest expressibility boundary,
 *   recorded here, not a silent gap.
 *   <li>Passing the same {@code Buffer(U8)} twice in one call trips the
 *   honest {@code MEM020} writable-borrow guard — the test fills two buffers
 *   with two {@code GetAudioDeviceFormat} calls instead.
 * </ul>
 *
 * <p>The cross face needs the SDL3 libs vendored into the cross sysroot:
 * {@code scripts/provision-cross-sdl3.sh}. Absent library/toolchain →
 * {@code assumeTrue} skip with a named reason (R6, never a false green).
 */
class Sdl3AudioStreamE2ETest implements NativeToolchainAssumptions {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String GOLDEN = "init=true\nstream=true\n"
            + "srcfmt=32784,2,44100\ndstfmt=33056,2,44100\n"
            + "put=true\navailable=32\ngot=32\nexact=true\n"
            + "clear=true\nempty=true\nquit=true";

    private static final String PROGRAM_TEMPLATE = """
            extern "%s" SDL_Init(Int flags): Bool
            extern "%s" SDL_OpenAudioDevice(Int devid, Buffer(U8) spec): Int
            extern "%s" SDL_GetAudioDeviceFormat(Int devid, Buffer(U8) spec, Buffer(U8) frames): Bool
            extern "%s" SDL_OpenAudioDeviceStream(Int devid, Buffer(U8) spec, Long cb, Long ud): Long
            extern "%s" SDL_GetAudioStreamFormat(Long stream, Buffer(U8) src, Buffer(U8) dst): Bool
            extern "%s" SDL_PutAudioStreamData(Long stream, String buf, Int len): Bool
            extern "%s" SDL_GetAudioStreamAvailable(Long stream): Int
            extern "%s" SDL_GetAudioStreamData(Long stream, Buffer(U8) buf, Int len): Int
            extern "%s" SDL_ClearAudioStream(Long stream): Bool
            extern "%s" SDL_PauseAudioDevice(Int devid): Bool
            extern "%s" SDL_DestroyAudioStream(Long stream): void
            extern "%s" SDL_CloseAudioDevice(Int devid): void
            extern "%s" SDL_Quit(): void

            Int streamUbyte(Byte[] bs, Int i) {
                var v = bs[i] as Int
                if (v < 0) { v = v + 256 }
                return v
            }

            Int streamU32(Byte[] bs, Int off) {
                return streamUbyte(bs, off) + streamUbyte(bs, off + 1) * 256
                    + streamUbyte(bs, off + 2) * 65536 + streamUbyte(bs, off + 3) * 16777216
            }

            String streamSpec(Byte[] bs) {
                return streamU32(bs, 0) + "," + streamU32(bs, 4) + "," + streamU32(bs, 8)
            }

            Int streamExpByte(Int j) {
                var k = j - ((j / 4) * 4)
                if (k == 0) { return 0 }
                if (k == 1) { return 130 }
                if (k == 2) { return 2 }
                return 63
            }

            Bool streamExactBytes(Byte[] bs, Int n) {
                var i = 0
                while (i < n) {
                    if (streamUbyte(bs, i) != streamExpByte(i)) { return false }
                    i = i + 1
                }
                return true
            }

            String streamFlag(Bool v) {
                if (v) { return "true" }
                return "false"
            }

            main() {
                println("init=" + SDL_Init(16))
                var dev = SDL_OpenAudioDevice(0 - 1, buffer.alloc(12))
                SDL_PauseAudioDevice(dev)
                var devSpec = buffer.alloc(12)
                SDL_GetAudioDeviceFormat(dev, devSpec, buffer.alloc(4))
                var stream = SDL_OpenAudioDeviceStream(dev, devSpec, 0, 0)
                println("stream=" + (stream != 0))
                var srcBuf = buffer.alloc(12)
                var dstBuf = buffer.alloc(12)
                SDL_GetAudioStreamFormat(stream, srcBuf, dstBuf)
                println("srcfmt=" + streamSpec(srcBuf.bytes()))
                println("dstfmt=" + streamSpec(dstBuf.bytes()))
                println("put=" + SDL_PutAudioStreamData(stream, "AAAAAAAAAAAAAAAA", 16))
                println("available=" + SDL_GetAudioStreamAvailable(stream))
                var back = buffer.alloc(32)
                println("got=" + SDL_GetAudioStreamData(stream, back, 32))
                println("exact=" + streamFlag(streamExactBytes(back.bytes(), 32)))
                println("clear=" + SDL_ClearAudioStream(stream))
                println("empty=" + (SDL_GetAudioStreamAvailable(stream) == 0))
                SDL_DestroyAudioStream(stream)
                SDL_CloseAudioDevice(dev)
                SDL_Quit()
                println("quit=true")
            }
            """;

    /** Host SDL3 `.so` (JVM + Native x86-64), or {@code null} if not installed. */
    private static String hostSdl3() {
        String env = System.getenv("KOF_SDL3");
        if (env != null && !env.isBlank() && new File(env).exists()) return env;
        for (String p : new String[]{
                "/usr/lib64/libSDL3.so.0", "/usr/lib/x86_64-linux-gnu/libSDL3.so.0",
                "/usr/lib/libSDL3.so.0"}) {
            if (new File(p).exists()) return p;
        }
        return null;
    }

    /** Cross SDL3 `.so` for {@code arch} inside the resolved sysroot, or null. */
    private static String crossSdl3(String arch) {
        String sysroot = System.getenv("KOF_CROSS_SYSROOT");
        if (sysroot == null || sysroot.isBlank()) {
            if (new File("/tmp/opencode/x/usr/" + arch + "-linux-gnu/lib/libSDL3.so.0").exists()) {
                sysroot = "/tmp/opencode/x";
            } else {
                return null;
            }
        }
        Path p = Path.of(sysroot, "usr", arch + "-linux-gnu", "lib", "libSDL3.so.0");
        return Files.exists(p) ? p.toString() : null;
    }

    private static String program(String so) {
        return PROGRAM_TEMPLATE.formatted(so, so, so, so, so, so, so, so, so, so, so, so, so);
    }

    private static void streamDummyAudio(ProcessBuilder pb) {
        pb.environment().put("SDL_AUDIODRIVER", "dummy");
    }

    @Test
    void sdl3AudioStreamJvm(@TempDir Path tempDir) throws Exception {
        String so = hostSdl3();
        assumeTrue(so != null, "SDL3 host ausente — rode scripts/provision-cross-sdl3.sh ou instale libSDL3");
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, program(so));
        Path out = tempDir.resolve("out-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "jvm compile: " + r.diagnostics().getDiagnostics());
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder pb = new ProcessBuilder(java, "--enable-native-access=ALL-UNNAMED",
                "-cp", out.toString(), "Default.Main");
        streamDummyAudio(pb);
        pb.redirectErrorStream(true);
        assertEquals(GOLDEN, run(pb), "SDL3 audio stream on JVM (headless dummy driver)");
    }

    @Test
    void sdl3AudioStreamNativeX86(@TempDir Path tempDir) throws Exception {
        assumeNativeX86_64();
        String so = hostSdl3();
        assumeTrue(so != null, "SDL3 host ausente — instale libSDL3");
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, program(so));
        Path out = tempDir.resolve("out-nat");
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), "x86-64 compile: " + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), "native binary should exist");
        ProcessBuilder pb = new ProcessBuilder(bin.toString());
        streamDummyAudio(pb);
        pb.redirectErrorStream(true);
        assertEquals(GOLDEN, run(pb), "SDL3 audio stream on Native x86-64 (headless)");
    }

    @Test
    void sdl3AudioStreamRiscv64(@TempDir Path tempDir) throws Exception {
        assumeNativeRiscv64WithSysroot();
        String so = crossSdl3("riscv64");
        assumeTrue(so != null, "SDL3 cross riscv64 ausente — rode scripts/provision-cross-sdl3.sh");
        assertEquals(GOLDEN, runCross(tempDir, "riscv64", Target.NATIVE_RISCV64, so),
                "SDL3 audio stream on riscv64 under qemu (headless)");
    }

    @Test
    void sdl3AudioStreamAarch64(@TempDir Path tempDir) throws Exception {
        assumeNativeAarch64WithSysroot();
        String so = crossSdl3("aarch64");
        assumeTrue(so != null, "SDL3 cross aarch64 ausente — rode scripts/provision-cross-sdl3.sh");
        assertEquals(GOLDEN, runCross(tempDir, "aarch64", Target.NATIVE_AARCH64, so),
                "SDL3 audio stream on aarch64 under qemu (headless)");
    }

    @Test
    void sdl3AudioStreamCrossTargetsAgree(@TempDir Path tempDir) throws Exception {
        assumeNativeRiscv64WithSysroot();
        assumeNativeAarch64WithSysroot();
        String risc = crossSdl3("riscv64");
        String arm = crossSdl3("aarch64");
        assumeTrue(risc != null && arm != null,
                "SDL3 cross ausente em uma arch — rode scripts/provision-cross-sdl3.sh");
        assertEquals(runCross(tempDir, "riscv64", Target.NATIVE_RISCV64, risc),
                runCross(tempDir, "aarch64", Target.NATIVE_AARCH64, arm),
                "regra 5: mesmo stream SDL3, mesma saida nos dois cross");
    }

    private String runCross(Path tempDir, String arch, Target t, String so) throws IOException {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, program(so));
        Path out = tempDir.resolve("out-" + arch);
        CompilationResult r = driver.compile(src, out, t);
        assertTrue(r.success(), arch + " compile: " + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binary should exist for " + arch);
        ProcessBuilder pb = NativeRiscv64E2ETest.qemu(arch, bin);
        streamDummyAudio(pb);
        pb.redirectErrorStream(true);
        return run(pb);
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
