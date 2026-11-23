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
 * Énois → desktop (Fase 3): a FAIXA DE ABAS do perfil (`ProfileContentTabs`).
 *
 * <p>As medidas vêm do fonte, MEDIDAS: `px-2 py-3 text-sm font-medium` = 14 px
 * (`profile-content-tabs.tsx:43`), a ordem `ALL, POSTS, REPLIES, REPOSTS,
 * MEDIA, DIARIO` (linha 8), os rótulos "Todos/Posts/Respostas/Reposts/Mídia/
 * Diário" (`src/messages/defaults/profile.ts:345`) e o indicador
 * `absolute inset-x-0 bottom-0 h-1 rounded-full bg-primary` = 4 px em
 * `--primary`.
 */
class EnosisProfileTabsE2ETest implements LibraryInstallSupport {

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
    void theTabsStripPaintsTheActiveIndicatorInTheBrandColour(@TempDir Path dir) throws Exception {
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
                import enois.ProfileTabs
                import enois.Shell
                import enois.Text
                import game.Sdl3Backend

                main() {
                    val W = 1280
                    val b = Sdl3Backend("Énóis", W, 800)
                    val top = Shell.paint(b, W, 800)
                    val x = 16
                    val cw = Shell.contentWidth(W)
                    val f = Font("%s", 14)
                    val h = ProfileTabs.paint(b, f, x, top, cw, 0, true)
                    println("tabs_h=" + h)
                    val ind = b.readPixel(x + 60, top + h - 3)
                    println("indicador=" + (ind[0] & 255) + "," + (ind[1] & 255) + "," + (ind[2] & 255))
                    val inativa = b.readPixel(x + cw / 6 + 60, top + 10)
                    println("fundo=" + (inativa[0] & 255) + "," + (inativa[1] & 255) + "," + (inativa[2] & 255))
                    var tinta = 0
                    var yy = top
                    while (yy < top + h) {
                        var xx = x
                        while (xx < x + cw) {
                            val p = b.readPixel(xx, yy)
                            if ((p[0] & 255) < 200) { tinta = tinta + 1 }
                            xx = xx + 4
                        }
                        yy = yy + 4
                    }
                    println("tinta=" + tinta)
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
        assertTrue(p.waitFor(240, TimeUnit.SECONDS), "o programa termina");
        assertEquals(0, p.exitValue(), "saida limpa: " + out);
        // py-3 x2 + a linha do texto + o indicador h-1 = 48
        assertTrue(out.contains("tabs_h=48"), "py-3 + indicador h-1: " + out);
        // --primary #6746f5 no indicador da aba ativa
        assertTrue(out.contains("indicador=103,70,245"), "o bg-primary do site: " + out);
        assertTrue(out.contains("fundo=247,244,251"), "o fundo do site: " + out);
        var m = java.util.regex.Pattern.compile("tinta=(\\d+)").matcher(out);
        assertTrue(m.find(), "o teste mede tinta: " + out);
        assertTrue(Integer.parseInt(m.group(1)) > 20, "os rotulos pintaram: " + out);
    }
}
