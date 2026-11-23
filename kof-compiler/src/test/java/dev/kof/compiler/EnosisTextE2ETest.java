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
 * Énois → desktop (Fase 2, a fundação): TEXTO no backend nativo.
 *
 * <p>O site é tipografia (`--font-sans: "Manrope"`) e o backend não tinha uma
 * letra. O caminho medido: Manrope TTF → FreeType (`libkof_text.so`, o shim do
 * known-bugs §1191: o handle atravessa por VALOR) → bitmap → textura SDL3 →
 * pixel na tela.
 */
class EnoisTextE2ETest implements LibraryInstallSupport {

    public String libraryName() { return "game"; }

    public List<String> libraryMarkers() { return List.of("Window.kf", "Draw.kf"); }

    private static boolean hostReady() {
        boolean sdl = new File("/usr/lib64/libSDL3.so.0").exists()
                || new File("/usr/lib/x86_64-linux-gnu/libSDL3.so.0").exists();
        boolean ft = new File("/usr/lib64/libfreetype.so.6").exists()
                || new File("/usr/lib/x86_64-linux-gnu/libfreetype.so.6").exists();
        return sdl && ft;
    }

    @Test
    void theManropeGlyphsPaintRealInk(@TempDir Path dir) throws Exception {
        assumeTrue(hostReady(), "SDL3 or FreeType absent");
        assumeTrue(NativeToolchainAssumptions.hasTool("as", "ld"), "native toolchain absent");
        Path repo = findLibsRoot().getParent();
        Path font = repo.resolve("assets/fonts/Manrope.ttf");
        assumeTrue(Files.exists(font), "assets/fonts/Manrope.ttf absent");
        Path shim = repo.resolve("libs/enois/libkof_text.so");
        assumeTrue(Files.exists(shim), "libkof_text.so absent — run scripts/gen-enois-text.sh");

        Path libs = dir.resolve("kof-install/lib/kof-libs");
        copyLibrary(libs);
        // `game.Sdl3Backend` importa `image.Raster` (o carregador de imagem
        // em Kof puro): a lib `image` vai junto para o build resolver
        Path imageLib = findLibsRoot().getParent().resolve("libs/image");
        try (var imgFiles = Files.walk(imageLib)) {
            for (Path f : imgFiles.filter(Files::isRegularFile).toList()) {
                Path dst = libs.resolve("image").resolve(imageLib.relativize(f));
                Files.createDirectories(dst.getParent());
                Files.copy(f, dst);
            }
        }
        // a lib `enois` (fora de `libs/`) + o shim compilado ao lado dela
        try (var files = Files.walk(repo.resolve("libs/enois"))) {
            for (Path f : files.filter(Files::isRegularFile).toList()) {
                Path dst = libs.resolve("enois").resolve(repo.resolve("libs/enois").relativize(f));
                Files.createDirectories(dst.getParent());
                Files.copy(f, dst);
            }
        }
        Files.writeString(dir.resolve("Main.kf"), """
                import enois.Text
                import game.Sdl3Backend

                main() {
                    val b = Sdl3Backend("texto", 400, 120)
                    b.clearFrame(255, 255, 255)
                    val f = Font("%s", 32)
                    println("line=" + f.lineHeight())
                    println("adv_A=" + f.advanceOf(65))
                    val adv = f.drawText(b, "Énóis", 20, 60)
                    println("draw_adv=" + adv)
                    var tinta = 0
                    var y = 20
                    while (y < 90) {
                        var x = 20
                        while (x < 220) {
                            val p = b.readPixel(x, y)
                            if ((p[0] & 255) < 250) { tinta = tinta + 1 }
                            x = x + 1
                        }
                        y = y + 1
                    }
                    println("pixels_de_tinta=" + tinta)
                    f.close()
                    b.close()
                }
                """.formatted(font.toAbsolutePath()), StandardCharsets.UTF_8);

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
        pb.environment().put("LD_LIBRARY_PATH", shim.getParent().toString());
        var p = pb.start();
        String out = new String(p.getInputStream().readAllBytes()).strip();
        assertTrue(p.waitFor(180, TimeUnit.SECONDS), "o programa termina");
        assertEquals(0, p.exitValue(), "saida limpa: " + out);
        // a linha do Manrope em 32 px e' ~45 (ascender+descender); o avanco de
        // "Énóis" e' positivo; e o que importa — HÁ TINTA na tela
        assertTrue(out.contains("line=") && !out.contains("line=0"), "lineHeight: " + out);
        assertTrue(out.contains("adv_A=") && !out.contains("adv_A=0"), "avanco: " + out);
        var m = java.util.regex.Pattern.compile("pixels_de_tinta=(\\d+)").matcher(out);
        assertTrue(m.find(), "o teste mede tinta: " + out);
        int ink = Integer.parseInt(m.group(1));
        assertTrue(ink > 200, "as letras pintaram tinta de verdade: " + ink + " px (" + out + ")");
    }
}
