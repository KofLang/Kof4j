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
 * Graphics/gaming slice 3.1 (`D-GRAPHICS-GAMING`, `G1` SDL3 in
 * `D-MAINT-BATCH-0510`): the picked stack, driven from Kof through {@code extern}
 * on JVM + Native x86-64 + the cross backends riscv64/aarch64 (under qemu).
 *
 * <p>The window/loop API is NOT landed here (the loop semantics long-frame/limit/
 * pause/minimized/focus are still TBD by the maintainer — `D-GRAPHICS-WINDOW-FORM`);
 * this test pins the layer UNDER it: the raw SDL3 ABI binds and RUNS headless
 * (`SDL_VIDEODRIVER=dummy`) on every target the matrix covers, so the stack is
 * proven before any API lands. The measured golden — the same one the 05/10
 * spike produced on JVM + Native x86-64 — is now pinned on riscv64 + aarch64 too.
 *
 * <p>The cross face needs the SDL3 libs vendored into the cross sysroot:
 * {@code scripts/provision-cross-sdl3.sh}. Absent library/toolchain →
 * {@code assumeTrue} skip with a named reason (R6, never a false green).
 */
class Sdl3FfiCrossE2ETest implements NativeToolchainAssumptions {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String GOLDEN = "init=true\ndriver=dummy\ntitle=kof";

    private static final String PROGRAM_TEMPLATE = """
            extern "%s" SDL_Init(Int flags): Bool
            extern "%s" SDL_CreateWindow(String title, Int w, Int h, Long flags): Long
            extern "%s" SDL_GetWindowTitle(Long win): String
            extern "%s" SDL_GetCurrentVideoDriver(): String
            extern "%s" SDL_DestroyWindow(Long win): void
            extern "%s" SDL_Quit(): void

            main() {
                var ok = SDL_Init(32)
                println("init=" + ok)
                println("driver=" + SDL_GetCurrentVideoDriver())
                var win = SDL_CreateWindow("kof", 320, 240, 0)
                println("title=" + SDL_GetWindowTitle(win))
                SDL_DestroyWindow(win)
                SDL_Quit()
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
        return PROGRAM_TEMPLATE.formatted(so, so, so, so, so, so);
    }

    private static void dummyDrivers(ProcessBuilder pb) {
        pb.environment().put("SDL_VIDEODRIVER", "dummy");
        pb.environment().put("SDL_AUDIODRIVER", "dummy");
    }

    @Test
    void sdl3WindowLifecycleJvm(@TempDir Path tempDir) throws Exception {
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
        dummyDrivers(pb);
        pb.redirectErrorStream(true);
        assertEquals(GOLDEN, run(pb), "SDL3 window lifecycle on JVM (headless dummy driver)");
    }

    @Test
    void sdl3WindowLifecycleNativeX86(@TempDir Path tempDir) throws Exception {
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
        dummyDrivers(pb);
        pb.redirectErrorStream(true);
        assertEquals(GOLDEN, run(pb), "SDL3 window lifecycle on Native x86-64 (headless)");
    }

    @Test
    void sdl3WindowLifecycleRiscv64(@TempDir Path tempDir) throws Exception {
        assumeNativeRiscv64WithSysroot();
        String so = crossSdl3("riscv64");
        assumeTrue(so != null, "SDL3 cross riscv64 ausente — rode scripts/provision-cross-sdl3.sh");
        assertEquals(GOLDEN, runCross(tempDir, "riscv64", Target.NATIVE_RISCV64, so),
                "SDL3 window lifecycle on riscv64 under qemu (headless)");
    }

    @Test
    void sdl3WindowLifecycleAarch64(@TempDir Path tempDir) throws Exception {
        assumeNativeAarch64WithSysroot();
        String so = crossSdl3("aarch64");
        assumeTrue(so != null, "SDL3 cross aarch64 ausente — rode scripts/provision-cross-sdl3.sh");
        assertEquals(GOLDEN, runCross(tempDir, "aarch64", Target.NATIVE_AARCH64, so),
                "SDL3 window lifecycle on aarch64 under qemu (headless)");
    }

    @Test
    void sdl3WindowLifecycleCrossTargetsAgree(@TempDir Path tempDir) throws Exception {
        assumeNativeRiscv64WithSysroot();
        assumeNativeAarch64WithSysroot();
        String risc = crossSdl3("riscv64");
        String arm = crossSdl3("aarch64");
        assumeTrue(risc != null && arm != null,
                "SDL3 cross ausente em uma arch — rode scripts/provision-cross-sdl3.sh");
        assertEquals(runCross(tempDir, "riscv64", Target.NATIVE_RISCV64, risc),
                runCross(tempDir, "aarch64", Target.NATIVE_AARCH64, arm),
                "regra 5: mesmo programa SDL3, mesma saida nos dois cross");
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
        dummyDrivers(pb);
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
