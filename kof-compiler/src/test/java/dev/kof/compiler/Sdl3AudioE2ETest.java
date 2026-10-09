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
 * Graphics/gaming slice 3.3b (`D-GRAPHICS-GAMING`, `G1` SDL3 in
 * `D-MAINT-BATCH-0510`): the SDL3 <em>audio</em> ABI over the vendored stack,
 * on JVM + Native x86-64 + the cross backends riscv64/aarch64 (under qemu),
 * headless (`SDL_AUDIODRIVER=dummy`).
 *
 * <p>The window/video ABI is pinned by {@link Sdl3FfiCrossE2ETest} and the
 * pump by {@link Sdl3PumpE2ETest}; this test pins the G1 audio half before
 * any playback API lands: init the audio subsystem, open the default
 * playback device, read back the negotiated device format
 * (`SDL_AudioSpec`: format/channels/rate + buffer frames), pause/resume the
 * device, close and quit. No audible output is produced or asserted (there
 * are no speakers in CI) — the device negotiation is the contract.
 *
 * <p>Measured Kof/FFI facts (never guessed):
 * <ul>
 *   <li>{@code SDL_AUDIO_DEVICE_DEFAULT_PLAYBACK} is {@code 0xFFFFFFFF};
 *   Kof has no unsigned literals, so the test passes {@code 0 - 1} (same low
 *   32 bits — the FFI truncates the 64-bit {@code Int} to the C
 *   {@code Uint32}).
 *   <li>Kof forbids the {@code null} literal (`SEM048`), so the nullable
 *   {@code spec} parameter cannot be spelled {@code NULL}; a zeroed 12-byte
 *   buffer (one {@code SDL_AudioSpec} of zeros) is accepted by SDL here and
 *   the dummy backend negotiates its defaults — measured, pinned.
 *   <li>The negotiated dummy-device format is S16 stereo 44100 Hz
 *   ({@code format=32784 channels=2 freq=44100}, 1024 buffer frames) on the
 *   vendored 3.4.16 — pinned exactly; a backend change would fail loudly,
 *   never silently.
 * </ul>
 *
 * <p>The cross face needs the SDL3 libs vendored into the cross sysroot:
 * {@code scripts/provision-cross-sdl3.sh}. Absent library/toolchain →
 * {@code assumeTrue} skip with a named reason (R6, never a false green).
 */
class Sdl3AudioE2ETest implements NativeToolchainAssumptions {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String GOLDEN = "init=true\nopen=true\nfmt=true\n"
            + "format=32784 channels=2 freq=44100\nframes=1024\n"
            + "pause=true resume=true\nquit=true";

    private static final String PROGRAM_TEMPLATE = """
            extern "%s" SDL_Init(Int flags): Bool
            extern "%s" SDL_OpenAudioDevice(Int devid, Buffer(U8) spec): Int
            extern "%s" SDL_GetAudioDeviceFormat(Int devid, Buffer(U8) spec, Buffer(U8) frames): Bool
            extern "%s" SDL_PauseAudioDevice(Int devid): Bool
            extern "%s" SDL_ResumeAudioDevice(Int devid): Bool
            extern "%s" SDL_CloseAudioDevice(Int devid): void
            extern "%s" SDL_Quit(): void

            Int audioUbyte(Byte[] bs, Int i) {
                var v = bs[i] as Int
                if (v < 0) { v = v + 256 }
                return v
            }

            Int audioU32(Byte[] bs, Int off) {
                return audioUbyte(bs, off) + audioUbyte(bs, off + 1) * 256
                    + audioUbyte(bs, off + 2) * 65536 + audioUbyte(bs, off + 3) * 16777216
            }

            main() {
                println("init=" + SDL_Init(16))
                var zero = buffer.alloc(12)
                var dev = SDL_OpenAudioDevice(0 - 1, zero)
                println("open=" + (dev != 0))
                var spec = buffer.alloc(12)
                var fr = buffer.alloc(4)
                var got = SDL_GetAudioDeviceFormat(dev, spec, fr)
                println("fmt=" + got)
                var sb = spec.bytes()
                println("format=" + audioU32(sb, 0) + " channels=" + audioU32(sb, 4) + " freq=" + audioU32(sb, 8))
                println("frames=" + audioU32(fr.bytes(), 0))
                println("pause=" + SDL_PauseAudioDevice(dev) + " resume=" + SDL_ResumeAudioDevice(dev))
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
        return PROGRAM_TEMPLATE.formatted(so, so, so, so, so, so, so);
    }

    private static void dummyAudio(ProcessBuilder pb) {
        pb.environment().put("SDL_AUDIODRIVER", "dummy");
    }

    @Test
    void sdl3AudioDeviceJvm(@TempDir Path tempDir) throws Exception {
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
        dummyAudio(pb);
        pb.redirectErrorStream(true);
        assertEquals(GOLDEN, run(pb), "SDL3 audio device on JVM (headless dummy driver)");
    }

    @Test
    void sdl3AudioDeviceNativeX86(@TempDir Path tempDir) throws Exception {
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
        dummyAudio(pb);
        pb.redirectErrorStream(true);
        assertEquals(GOLDEN, run(pb), "SDL3 audio device on Native x86-64 (headless)");
    }

    @Test
    void sdl3AudioDeviceRiscv64(@TempDir Path tempDir) throws Exception {
        assumeNativeRiscv64WithSysroot();
        String so = crossSdl3("riscv64");
        assumeTrue(so != null, "SDL3 cross riscv64 ausente — rode scripts/provision-cross-sdl3.sh");
        assertEquals(GOLDEN, runCross(tempDir, "riscv64", Target.NATIVE_RISCV64, so),
                "SDL3 audio device on riscv64 under qemu (headless)");
    }

    @Test
    void sdl3AudioDeviceAarch64(@TempDir Path tempDir) throws Exception {
        assumeNativeAarch64WithSysroot();
        String so = crossSdl3("aarch64");
        assumeTrue(so != null, "SDL3 cross aarch64 ausente — rode scripts/provision-cross-sdl3.sh");
        assertEquals(GOLDEN, runCross(tempDir, "aarch64", Target.NATIVE_AARCH64, so),
                "SDL3 audio device on aarch64 under qemu (headless)");
    }

    @Test
    void sdl3AudioDeviceCrossTargetsAgree(@TempDir Path tempDir) throws Exception {
        assumeNativeRiscv64WithSysroot();
        assumeNativeAarch64WithSysroot();
        String risc = crossSdl3("riscv64");
        String arm = crossSdl3("aarch64");
        assumeTrue(risc != null && arm != null,
                "SDL3 cross ausente em uma arch — rode scripts/provision-cross-sdl3.sh");
        assertEquals(runCross(tempDir, "riscv64", Target.NATIVE_RISCV64, risc),
                runCross(tempDir, "aarch64", Target.NATIVE_AARCH64, arm),
                "regra 5: mesmo dispositivo SDL3, mesma saida nos dois cross");
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
        dummyAudio(pb);
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
