package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * §1189 — MULTIDISTRO: o link cross escolhe o sysroot que TEM as libs externas
 * do programa, e o `-lc` resolve nos DOIS layouts.
 *
 * <p>Antes, um programa com `extern` SDL3 exigia `KOF_CROSS_SYSROOT` na mao e o
 * `extern` no caminho absoluto do host: o `sysrootFor` preferia o SYS-ROOT do
 * SUSE, que tem o loader mas NAO a libc nem o SDL3, e o `ld` do SUSE nao
 * procura em {@code <sysroot>/usr/<arch>-linux-gnu/lib} (ele expande `-lc`
 * para {@code /usr/<arch>-linux-gnu/lib}, medido). Agora:
 * <ul>
 *   <li>um candidato so' vale se tem a libc E as externas pedidas;</li>
 *   <li>a linha de link ganha {@code -L<sysroot>/usr/<arch>-linux-gnu/lib},
 *       que cobre Debian e SUSE sem depender do prefixo do ld.</li>
 * </ul>
 */
class NativeCrossMultidistroE2ETest implements LibraryInstallSupport {

    public String libraryName() { return "game"; }

    public List<String> libraryMarkers() { return List.of("Window.kf", "Draw.kf"); }

    @Test
    void aProgramWithAnSdl3ExternLinksWithoutAnyEnvVar(@TempDir Path dir) throws Exception {
        // O UNICO caminho que o usuario faria: `extern "libSDL3.so.0"` (o nome
        // do SONAME, nao um caminho absoluto) e nenhuma variavel de ambiente.
        assumeTrue(System.getenv("KOF_CROSS_SYSROOT") == null,
                "KOF_CROSS_SYSROOT set in this shell — the point is the path WITHOUT it");
        var home = System.getProperty("user.home");
        var cross = Path.of(home, ".local/share/kof-cross");
        assumeTrue(Files.isDirectory(cross), "kof-cross sysroot absent");
        for (String[] pair : new String[][]{{"aarch64", "NATIVE_AARCH64"}, {"riscv64", "NATIVE_RISCV64"}}) {
            String arch = pair[0];
            assumeTrue(new File("/usr/bin/" + arch + "-linux-gnu-ld").exists()
                            && new File("/usr/bin/" + arch + "-linux-gnu-as").exists()
                            && new File("/usr/bin/qemu-" + arch).exists(),
                    "cross " + arch + " toolchain absent");

            Path libs = dir.resolve("kof-install/lib/kof-libs");
            copyLibrary(libs);
            // o extern do `game` e' `libSDL3.so.0` (o NOME) — se algum dia
            // voltar a ser um caminho absoluto do host, este teste falha.
            Path sdl = libs.resolve("game/Sdl3Backend.kf");
            String text = Files.readString(sdl, StandardCharsets.UTF_8);
            assertTrue(text.contains("\"libSDL3.so.0\""),
                    "o extern usa o NOME da lib, nao um caminho do host");

            Path main = dir.resolve("Main-" + arch + ".kf");
            Files.writeString(main, """
                    import game.Sdl3Backend

                    main() {
                        val b = Sdl3Backend("multi", 32, 32)
                        b.clearFrame(10, 200, 90)
                        val p = b.readPixel(0, 0)
                        println("r=" + (p[0] & 255) + " g=" + (p[1] & 255) + " b=" + (p[2] & 255))
                        b.close()
                    }
                    """, StandardCharsets.UTF_8);
            String previous = System.getProperty("kof.install.dir");
            System.setProperty("kof.install.dir", dir.resolve("kof-install").toString());
            CompilationResult r;
            try {
                r = new CompilerDriver().compile(main, dir.resolve("out-" + arch),
                        Target.valueOf(pair[1]));
            } finally {
                if (previous == null) System.clearProperty("kof.install.dir");
                else System.setProperty("kof.install.dir", previous);
            }
            assertTrue(r.success(), arch + " link sem KOF_CROSS_SYSROOT: "
                    + r.diagnostics().getDiagnostics());

            var pb = new ProcessBuilder("/usr/bin/qemu-" + arch,
                    "-L", cross.resolve("usr/" + arch + "-linux-gnu").toString(),
                    "-E", "LD_LIBRARY_PATH=" + cross.resolve("usr/" + arch + "-linux-gnu/lib"),
                    dir.resolve("out-" + arch + "/Default/Main").toString())
                    .redirectErrorStream(true);
            pb.environment().put("SDL_VIDEODRIVER", "dummy");
            var p = pb.start();
            String out = new String(p.getInputStream().readAllBytes()).strip();
            assertTrue(p.waitFor(120, TimeUnit.SECONDS), arch + " finishes");
            assertEquals(0, p.exitValue(), arch + " runs: " + out);
            assertEquals("r=10 g=200 b=90", out, arch + " desenha a cor certa sob o qemu");
        }
    }
}
