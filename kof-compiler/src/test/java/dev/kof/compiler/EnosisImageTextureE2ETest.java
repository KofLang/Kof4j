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
 * Énois → desktop: IMAGEM de verdade no backend nativo — o gap da capa.
 *
 * <p>`Sdl3Backend.loadImageTexture` decodifica PNG/PNM/BMP/QOI/GIF/WebP pela lib
 * `image` em KOF PURO (sem codec externo) e sobe uma textura RGBA. A prova é o
 * pixel: a capa sintética tem `(x*8 % 256, y*16 % 256, 200)`, então o pixel
 * lido dentro dela NÃO é uma cor sólida.
 */
class EnosisImageTextureE2ETest implements LibraryInstallSupport {

    public String libraryName() { return "game"; }

    public List<String> libraryMarkers() { return List.of("Window.kf", "Draw.kf"); }

    private static boolean hostSdl3() {
        return new File("/usr/lib64/libSDL3.so.0").exists()
                || new File("/usr/lib/x86_64-linux-gnu/libSDL3.so.0").exists();
    }




    @Test
    void aRealImageReachesTheScreenAsPixels(@TempDir Path dir) throws Exception {
        assumeTrue(hostSdl3(), "SDL3 host absent");
        assumeTrue(NativeToolchainAssumptions.hasTool("as", "ld"), "native toolchain absent");
        Path cover = EnosisTestPng.gradient(dir, "cover.png", 32, 16, 200);

        Path libs = dir.resolve("kof-install/lib/kof-libs");
        copyLibrary(libs);
        // a lib `image` (pura Kof) tambem vem do repositorio
        Path repo = findLibsRoot().getParent();
        Path img = repo.resolve("libs/image");
        try (var files = Files.walk(img)) {
            for (Path f : files.filter(Files::isRegularFile).toList()) {
                Path dst = libs.resolve("image").resolve(img.relativize(f));
                Files.createDirectories(dst.getParent());
                Files.copy(f, dst);
            }
        }

        Files.writeString(dir.resolve("Main.kf"), """
                import game.Sdl3Backend

                main() {
                    val b = Sdl3Backend("capa", 128, 128)
                    val tex = b.loadImageTexture("%s")
                    println("tex=" + (tex != 0L))
                    b.clearFrame(0, 0, 0)
                    b.drawTexture(tex, 0, 0, 32, 16)
                    // 1:1 (sem escala): o pixel (1,1) da fonte, direto
                    val p = b.readPixel(1, 1)
                    println("px=" + (p[0] & 255) + "," + (p[1] & 255) + "," + (p[2] & 255))
                    // um arquivo que nao existe: 0, nunca um crash
                    println("ausente=" + (b.loadImageTexture("%s") == 0L))
                    b.close()
                }
                """.formatted(cover.toAbsolutePath(), dir.resolve("nada.png").toAbsolutePath()),
                StandardCharsets.UTF_8);

        String previous = System.getProperty("kof.install.dir");
        System.setProperty("kof.install.dir", dir.resolve("kof-install").toString());
        CompilationResult r;
        try {
            r = new CompilerDriver().compile(dir.resolve("Main.kf"), dir.resolve("out"), Target.NATIVE);
        } finally {
            if (previous == null) System.clearProperty("kof.install.dir");
            else System.setProperty("kof.install.dir", previous);
        }
        assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());

        var pb = new ProcessBuilder(dir.resolve("out/Default/Main").toString()).redirectErrorStream(true);
        pb.environment().put("SDL_VIDEODRIVER", "dummy");
        var p = pb.start();
        String out = new String(p.getInputStream().readAllBytes()).strip();
        assertTrue(p.waitFor(180, TimeUnit.SECONDS), "o programa termina");
        assertEquals(0, p.exitValue(), "saida limpa: " + out);
        assertTrue(out.contains("tex=true"), "o PNG decodificou: " + out);
        // o pixel (1,1) da fonte, desenhado 1:1. O gradiente do helper e'
        // (x*255/w, y*255/h, blue) = (1*255/32, 1*255/16, 200) = (7, 15, 200)
        // — o valor e' DERIVADO da formula, nunca um literal escolhido a mao.
        assertTrue(out.contains("px=7,15,200"), "o pixel real da imagem: " + out);
        assertTrue(out.contains("ausente=true"), "arquivo ausente = 0, sem crash: " + out);
    }
}
