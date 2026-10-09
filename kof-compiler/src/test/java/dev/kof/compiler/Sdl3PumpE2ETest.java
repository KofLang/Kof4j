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
 * Graphics/gaming slice 3.1 remainder (`D-GRAPHICS-GAMING`, `G1` SDL3 in
 * `D-MAINT-BATCH-0510`): the backend event pump over the measured SDL3
 * binding, on JVM + Native x86-64 + the cross backends riscv64/aarch64
 * (under qemu), headless (`SDL_VIDEODRIVER=dummy`).
 *
 * <p>The raw ABI (init/create/title/destroy/quit) is already pinned by
 * {@link Sdl3FfiCrossE2ETest}; this test pins the layer above it — the pump
 * the pure {@code Clock}/{@code Keys} snapshots plug into: drain the queue
 * with {@code SDL_PollEvent} into a 128-byte {@code Buffer(U8)} (the
 * {@code SDL_Event} size, asserted upstream), push a synthetic event,
 * pace frames with {@code SDL_Delay}/{@code SDL_GetTicks}, and step two
 * virtual-clock frames. No new Kof API: the program wires measured
 * {@code extern} shapes to the pure `libs/game` modules.
 *
 * <p>Honest boundaries, all measured (never guessed):
 * <ul>
 *   <li>SDL drops a pushed zero (type-0) event: {@code SDL_PushEvent} on a
 *   zeroed buffer returns {@code true} but the following {@code PollEvent}
 *   returns {@code 0} — the golden pins exactly this. Real scancode-carrying
 *   events cannot be synthesized from Kof yet because the {@code Buffer(U8)}
 *   surface is alloc+read only (no indexed byte write); the pump side that
 *   IS expressible — drain, pace, quit — is what runs here on every target.
 *   Real backend-generated events (e.g. {@code 0x404 MOUSE_ADDED} at window
 *   creation) exist but their count varies by environment, so the drain
 *   loop counts silently and the golden asserts structure, never counts.
 *   <li>{@code SDL_Delay(50)} guarantees <em>at least</em> 50ms, so
 *   {@code (t1 - t0) >= 50} is deterministic (printed as a boolean, never a
 *   raw tick count).
 *   <li>The frame loop runs on a virtual timestamp source (the {@code Clock}
 *   contract), so {@code dt} stays {@code 0,1} on every target.
 * </ul>
 *
 * <p>The cross face needs the SDL3 libs vendored into the cross sysroot:
 * {@code scripts/provision-cross-sdl3.sh}. Absent library/toolchain →
 * {@code assumeTrue} skip with a named reason (R6, never a false green).
 */
class Sdl3PumpE2ETest implements NativeToolchainAssumptions, LibraryInstallSupport {

    private final CompilerDriver driver = new CompilerDriver();

    @Override public String libraryName() { return "game"; }

    @Override public java.util.List<String> libraryMarkers() {
        return java.util.List.of("Clock.kf", "Keys.kf");
    }

    private static final String GOLDEN =
            "init=true\npush=true\npoll=0\npaced=true\nframes=2\nquit=true";

    private static final String PROGRAM_TEMPLATE = """
            import game.Clock
            import game.Keys

            extern "%s" SDL_Init(Int flags): Bool
            extern "%s" SDL_CreateWindow(String title, Int w, Int h, Long flags): Long
            extern "%s" SDL_PushEvent(Buffer(U8) ev): Bool
            extern "%s" SDL_PollEvent(Buffer(U8) ev): Int
            extern "%s" SDL_Delay(Int ms): void
            extern "%s" SDL_GetTicks(): Long
            extern "%s" SDL_DestroyWindow(Long win): void
            extern "%s" SDL_Quit(): void

            main() {
                var clock = Clock()
                var keys = Keys()
                var ok = SDL_Init(32)
                println("init=" + ok)
                var win = SDL_CreateWindow("kof", 320, 240, 0)
                var ev = buffer.alloc(128)
                while (SDL_PollEvent(ev) == 1) {
                }
                println("push=" + SDL_PushEvent(ev))
                println("poll=" + SDL_PollEvent(ev))
                var t0 = SDL_GetTicks()
                SDL_Delay(50)
                var t1 = SDL_GetTicks()
                println("paced=" + ((t1 - t0) >= 50))
                clock.start(1000)
                clock.beginFrame(1000)
                keys.beginFrame(listOf())
                clock.beginFrame(2000)
                keys.beginFrame(listOf())
                println("frames=2")
                SDL_DestroyWindow(win)
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
        return PROGRAM_TEMPLATE.formatted(so, so, so, so, so, so, so, so);
    }

    private static void pumpDummyDrivers(ProcessBuilder pb) {
        pb.environment().put("SDL_VIDEODRIVER", "dummy");
        pb.environment().put("SDL_AUDIODRIVER", "dummy");
    }

    @Test
    void sdl3PumpLoopJvm(@TempDir Path tempDir) throws Exception {
        String so = hostSdl3();
        assumeTrue(so != null, "SDL3 host ausente — rode scripts/provision-cross-sdl3.sh ou instale libSDL3");
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, program(so));
        Path out = tempDir.resolve("out-jvm");
        CompilationResult r = withGameLib(tempDir,
                () -> driver.compile(src, out, Target.JVM));
        assertTrue(r.success(), "jvm compile: " + r.diagnostics().getDiagnostics());
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder pb = new ProcessBuilder(java, "--enable-native-access=ALL-UNNAMED",
                "-cp", out.toString(), "Default.Main");
        pumpDummyDrivers(pb);
        pb.redirectErrorStream(true);
        assertEquals(GOLDEN, run(pb), "SDL3 pump loop on JVM (headless dummy driver)");
    }

    @Test
    void sdl3PumpLoopNativeX86(@TempDir Path tempDir) throws Exception {
        assumeNativeX86_64();
        String so = hostSdl3();
        assumeTrue(so != null, "SDL3 host ausente — instale libSDL3");
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, program(so));
        Path out = tempDir.resolve("out-nat");
        CompilationResult r = withGameLib(tempDir,
                () -> driver.compile(src, out, Target.NATIVE));
        assertTrue(r.success(), "x86-64 compile: " + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), "native binary should exist");
        ProcessBuilder pb = new ProcessBuilder(bin.toString());
        pumpDummyDrivers(pb);
        pb.redirectErrorStream(true);
        assertEquals(GOLDEN, run(pb), "SDL3 pump loop on Native x86-64 (headless)");
    }

    @Test
    void sdl3PumpLoopRiscv64(@TempDir Path tempDir) throws Exception {
        assumeNativeRiscv64WithSysroot();
        String so = crossSdl3("riscv64");
        assumeTrue(so != null, "SDL3 cross riscv64 ausente — rode scripts/provision-cross-sdl3.sh");
        assertEquals(GOLDEN, runCross(tempDir, "riscv64", Target.NATIVE_RISCV64, so),
                "SDL3 pump loop on riscv64 under qemu (headless)");
    }

    @Test
    void sdl3PumpLoopAarch64(@TempDir Path tempDir) throws Exception {
        assumeNativeAarch64WithSysroot();
        String so = crossSdl3("aarch64");
        assumeTrue(so != null, "SDL3 cross aarch64 ausente — rode scripts/provision-cross-sdl3.sh");
        assertEquals(GOLDEN, runCross(tempDir, "aarch64", Target.NATIVE_AARCH64, so),
                "SDL3 pump loop on aarch64 under qemu (headless)");
    }

    @Test
    void sdl3PumpLoopCrossTargetsAgree(@TempDir Path tempDir) throws Exception {
        assumeNativeRiscv64WithSysroot();
        assumeNativeAarch64WithSysroot();
        String risc = crossSdl3("riscv64");
        String arm = crossSdl3("aarch64");
        assumeTrue(risc != null && arm != null,
                "SDL3 cross ausente em uma arch — rode scripts/provision-cross-sdl3.sh");
        assertEquals(runCross(tempDir, "riscv64", Target.NATIVE_RISCV64, risc),
                runCross(tempDir, "aarch64", Target.NATIVE_AARCH64, arm),
                "regra 5: mesma bomba SDL3, mesma saida nos dois cross");
    }

    private <T> T withGameLib(Path root, PumpCheckedSupplier<T> action) throws IOException {
        try {
            copyLibrary(root.resolve("kof-install/lib/kof-libs"));
        } catch (Exception e) {
            throw new IOException("game library install failed", e);
        }
        String previous = System.getProperty("kof.install.dir");
        System.setProperty("kof.install.dir", root.resolve("kof-install").toString());
        try {
            return action.get();
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("compile failed", e);
        } finally {
            if (previous == null) System.clearProperty("kof.install.dir");
            else System.setProperty("kof.install.dir", previous);
        }
    }

    @FunctionalInterface
    private interface PumpCheckedSupplier<T> {
        T get() throws Exception;
    }

    private String runCross(Path tempDir, String arch, Target t, String so) throws IOException {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, program(so));
        Path out = tempDir.resolve("out-" + arch);
        CompilationResult r = withGameLib(tempDir, () -> driver.compile(src, out, t));
        assertTrue(r.success(), arch + " compile: " + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binary should exist for " + arch);
        ProcessBuilder pb = NativeRiscv64E2ETest.qemu(arch, bin);
        pumpDummyDrivers(pb);
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
