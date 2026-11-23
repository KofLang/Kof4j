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
 * Énois → desktop (Fase 2): a tela FEED — o composer e a LISTA de cards.
 *
 * <p>As medidas vêm dos fontes, MEDIDAS: o grid `[minmax(0,1fr)_320px] gap-6`
 * (`feed.tsx:1011`), o composer `h-[3.625rem]` = 58 px, `rounded-full`,
 * `text-[15px]` (`composer-launcher.tsx`), e os textos reais de `src/texts.ts`
 * (`composer.placeholder` = "Escreva sua publicação").
 */
class EnosisFeedE2ETest implements LibraryInstallSupport {

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
    void theFeedPaintsTheComposerAndTheCardList(@TempDir Path dir) throws Exception {
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
                import enois.Feed
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
                    val ui = Font("%s", 15)
                    val author = Font("%s", 12)
                    val body = Font("%s", 16)
                    val ch = Feed.paintComposer(b, ui, x, top, cw, "Escreva sua publicação")
                    println("composer_h=" + ch)
                    val cardsTop = top + ch + 24
                    val total = Feed.paintCards(b, author, body, x, cardsTop, cw,
                        listOf<String>("Maria Silva", "Joao Souza"),
                        listOf<String>("Uma rede social que nao vive da sua atencao.",
                                       "Sem anuncio, sem plano pago, e o feed termina quando o conteudo acaba."))
                    println("cards_h=" + total)
                    println("card1_h=" + Feed.lastCardHeight)
                    // pontos LIMPOS (fora das letras): a superficie do composer
                    val p1 = b.readPixel(200, top + 5)
                    println("pill=" + (p1[0] & 255) + "," + (p1[1] & 255) + "," + (p1[2] & 255))
                    // o card logo abaixo do composer
                    val p2 = b.readPixel(x + 300, cardsTop + 4)
                    println("card=" + (p2[0] & 255) + "," + (p2[1] & 255) + "," + (p2[2] & 255))
                    // o fundo entre os cards (o gap de 24) — a altura do ULTIMO
                    // card e' MEDIDA pelo Feed, nunca fixa no teste
                    val p3 = b.readPixel(x + 300, cardsTop + Feed.lastCardHeight + 12)
                    println("gap=" + (p3[0] & 255) + "," + (p3[1] & 255) + "," + (p3[2] & 255))
                    var tinta = 0
                    var yy = top
                    while (yy < cardsTop + total) {
                        var xx = x
                        while (xx < x + cw) {
                            val q = b.readPixel(xx, yy)
                            if ((q[0] & 255) < 200) { tinta = tinta + 1 }
                            xx = xx + 8
                        }
                        yy = yy + 8
                    }
                    println("tinta=" + tinta)
                    ui.close()
                    author.close()
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
        assertTrue(out.contains("composer_h=58"), "h-[3.625rem] do site: " + out);
        assertTrue(out.contains("pill=255,255,255"), "o --surface-card do composer: " + out);
        assertTrue(out.contains("card=255,255,255"), "o card logo abaixo: " + out);
        assertTrue(out.contains("gap=247,244,251"), "o gap-6 mostra o --background: " + out);
        var m = java.util.regex.Pattern.compile("tinta=(\\d+)").matcher(out);
        assertTrue(m.find(), "o teste mede tinta: " + out);
        assertTrue(Integer.parseInt(m.group(1)) > 50,
                "o placeholder e os cards pintaram texto: " + out);
    }
}
