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
 * Énois → desktop (Fase 3): `post.$postId` — o detalhe do post.
 *
 * <p>As medidas vêm do fonte, MEDIDAS: o link de voltar `text-sm` = 14 px em
 * `--muted-foreground`, o card `surface-card p-5 sm:p-8` = 32 px no desktop,
 * o título `text-lg` = 18 px, o corpo `text-base leading-relaxed`
 * (`post.$postId.tsx`).
 */
class EnosisPostDetailE2ETest implements LibraryInstallSupport {

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
    void thePostDetailPaintsTheCardAndItsText(@TempDir Path dir) throws Exception {
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
                import enois.PostDetail
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
                    val back = Font("%s", 14)
                    val title = Font("%s", 18)
                    val body = Font("%s", 16)
                    val h = PostDetail.paint(b, back, title, body, x, top, cw,
                        "Voltar para a pagina inicial",
                        "Uma rede social que nao vive da sua atencao",
                        "O feed termina quando o conteudo acaba. Sem anuncio, sem plano pago, sem contador infinito.")
                    println("detail_h=" + h)
                    // dentro do card (32 px abaixo do topo do card) e FORA das letras
                    val card = b.readPixel(x + 400, top + 80)
                    println("card=" + (card[0] & 255) + "," + (card[1] & 255) + "," + (card[2] & 255))
                    // o fundo acima do card (a area do link de voltar)
                    val backArea = b.readPixel(x + 400, top + 2)
                    println("fundo=" + (backArea[0] & 255) + "," + (backArea[1] & 255) + "," + (backArea[2] & 255))
                    var tinta = 0
                    var yy = top
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
                    back.close()
                    title.close()
                    body.close()
                    b.close()
                }
                """.formatted(font.toAbsolutePath(), font.toAbsolutePath(), font.toAbsolutePath()),
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
        pb.environment().put("LD_LIBRARY_PATH", shim.getParent().toString());
        var p = pb.start();
        String out = new String(p.getInputStream().readAllBytes()).strip();
        assertTrue(p.waitFor(240, TimeUnit.SECONDS), "o programa termina");
        assertEquals(0, p.exitValue(), "saida limpa: " + out);
        assertTrue(out.contains("card=255,255,255"), "o --surface-card do detalhe: " + out);
        assertTrue(out.contains("fundo=247,244,251"), "o --background acima do card: " + out);
        var m = java.util.regex.Pattern.compile("tinta=(\\d+)").matcher(out);
        assertTrue(m.find(), "o teste mede tinta: " + out);
        assertTrue(Integer.parseInt(m.group(1)) > 20,
                "o voltar, o titulo e o corpo pintaram: " + out);
    }
}
