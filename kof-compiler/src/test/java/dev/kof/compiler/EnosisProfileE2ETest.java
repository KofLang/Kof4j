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
 * Énois → desktop (Fase 3, a primeira ROTA): `perfil.$username`.
 *
 * <p>As medidas vêm do CSS do design system, MEDIDAS: `.cos-card__banner`
 * height 120 px (`styles.css:3503`), `.cos-card__avatar` 100×100
 * (`styles.css:4229`), `.cos-card__name` 25 px / 800 (`styles.css:4297`),
 * `.cos-card__bio` 14 px (`styles.css:4323`).
 */
class EnosisProfileE2ETest implements LibraryInstallSupport {

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
    void theProfileHeaderPaintsTheSiteNumbers(@TempDir Path dir) throws Exception {
        assumeTrue(hostReady(), "SDL3 or FreeType absent");
        assumeTrue(NativeToolchainAssumptions.hasTool("as", "ld"), "native toolchain absent");
        Path repo = findLibsRoot().getParent();
        Path font = repo.resolve("assets/fonts/Manrope.ttf");
        Path shim = repo.resolve("libs/enois/libkof_text.so");
        assumeTrue(Files.exists(font), "assets/fonts/Manrope.ttf absent");
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
        Path enois = repo.resolve("libs/enois");
        try (var files = Files.walk(enois)) {
            for (Path f : files.filter(Files::isRegularFile).toList()) {
                Path dst = libs.resolve("enois").resolve(enois.relativize(f));
                Files.createDirectories(dst.getParent());
                Files.copy(f, dst);
            }
        }
        Path text = libs.resolve("enois/Text.kf");
        Files.writeString(text, Files.readString(text, StandardCharsets.UTF_8)
                .replace("\"@KOF_TEXT_SHIM@\"", "\"" + shim + "\"")
                .replace("\"kof_text_shim\"", "\"" + shim + "\""), StandardCharsets.UTF_8);

        Files.writeString(dir.resolve("Main.kf"), """
                import enois.Profile
                import enois.Shell
                import enois.Text
                import game.Sdl3Backend

                main() {
                    val W = 1280
                    val H = 800
                    val b = Sdl3Backend("Énóis", W, H)
                    val top = Shell.paint(b, W, H)
                    val x = 16
                    val cw = Shell.contentWidth(W)
                    val name = Font("%s", 25)
                    val meta = Font("%s", 14)
                    val h = ProfileHeader.paint(b, name, meta, x, top, cw,
                        "Maria Silva", "@mariasilva", "Construindo uma rede sem anuncio.")
                    println("header_h=" + h)
                    val banner = b.readPixel(x + 400, top + 10)
                    println("banner=" + (banner[0] & 255) + "," + (banner[1] & 255) + "," + (banner[2] & 255))
                    val body = b.readPixel(x + 400, top + 140)
                    println("body=" + (body[0] & 255) + "," + (body[1] & 255) + "," + (body[2] & 255))
                    var tinta = 0
                    var yy = top + 110
                    while (yy < top + h) {
                        var xx = x
                        while (xx < x + cw) {
                            val p = b.readPixel(xx, yy)
                            if ((p[0] & 255) < 200) { tinta = tinta + 1 }
                            xx = xx + 8
                        }
                        yy = yy + 8
                    }
                    println("tinta=" + tinta)
                    name.close()
                    meta.close()
                    b.close()
                }
                """.formatted(font.toAbsolutePath(), font.toAbsolutePath()), StandardCharsets.UTF_8);

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
        assertTrue(p.waitFor(240, TimeUnit.SECONDS), "o programa termina");
        assertEquals(0, p.exitValue(), "saida limpa: " + out);
        // --primary-soft (#eee8ff) no banner; --surface-card no corpo
        assertTrue(out.contains("banner=238,232,255"), "o banner do site: " + out);
        assertTrue(out.contains("body=255,255,255"), "a superficie do card: " + out);
        // 120 (banner) + o corpo com pad/avatar/nome/meta/bio
        var h = java.util.regex.Pattern.compile("header_h=(\\d+)").matcher(out);
        assertTrue(h.find(), "a altura do cabecalho: " + out);
        int headerH = Integer.parseInt(h.group(1));
        assertTrue(headerH > 120 && headerH < 400, "banner 120 + corpo: " + headerH);
        var m = java.util.regex.Pattern.compile("tinta=(\\d+)").matcher(out);
        assertTrue(m.find(), "o teste mede tinta: " + out);
        assertTrue(Integer.parseInt(m.group(1)) > 5, "nome/handle/bio pintaram: " + out);
    }
}
