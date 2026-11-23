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
 * Énois → desktop (Fase 2): o CARD DE POST, com a tipografia real do site.
 *
 * <p>As medidas vêm do design system, MEDIDAS: `bg-surface-card p-4` e
 * `sm:p-5` (`post-card.tsx:100`), `rounded-xl`, `border-border-soft`,
 * `text-xs font-medium` no autor, `text-base` no corpo.
 */
class EnosisPostCardE2ETest implements LibraryInstallSupport {

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
    void thePostCardPaintsItsSurfaceAndRealText(@TempDir Path dir) throws Exception {
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
        // o Text.kf copiado ainda leva o marcador do gerador: preenche-o AQUI,
        // com o shim que este teste acabou de receber (o caminho do repo)
        Path text = libs.resolve("enois/Text.kf");
        String t = Files.readString(text, StandardCharsets.UTF_8)
                .replace("\"@KOF_TEXT_SHIM@\"", "\"" + shim + "\"")
                .replace("\"kof_text_shim\"", "\"" + shim + "\"");
        Files.writeString(text, t, StandardCharsets.UTF_8);

        Files.writeString(dir.resolve("Main.kf"), """
                import enois.PostCard
                import enois.Tokens
                import enois.Text
                import game.Sdl3Backend

                main() {
                    val b = Sdl3Backend("card", 700, 300)
                    val bg = Tokens.Background()
                    b.clearFrame(bg.r(), bg.g(), bg.b())
                    val author = Font("%s", 12)
                    val body = Font("%s", 16)
                    val h = PostCard.paint(b, author, body, 20, 20, 640,
                        "Maria Silva",
                        "Uma rede social que nao vive da sua atencao. Sem anuncio, sem plano pago, e o feed termina quando o conteudo acaba.")
                    println("card_h=" + h)
                    val i = b.readPixel(30, 30)
                    val o = b.readPixel(690, 290)
                    println("card=" + (i[0] & 255) + "," + (i[1] & 255) + "," + (i[2] & 255))
                    println("fundo=" + (o[0] & 255) + "," + (o[1] & 255) + "," + (o[2] & 255))
                    var tinta = 0
                    var yy = 25
                    while (yy < 20 + h - 5) {
                        var xx = 25
                        while (xx < 640) {
                            val p = b.readPixel(xx, yy)
                            if ((p[0] & 255) < 200) { tinta = tinta + 1 }
                            xx = xx + 1
                        }
                        yy = yy + 1
                    }
                    println("tinta=" + tinta)
                    author.close()
                    body.close()
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
        assertTrue(p.waitFor(180, TimeUnit.SECONDS), "o programa termina");
        assertEquals(0, p.exitValue(), "saida limpa: " + out);
        // --surface-card (#ffffff) dentro, --background (#f7f4fb) fora
        assertTrue(out.contains("card=255,255,255"), "a superficie do card: " + out);
        assertTrue(out.contains("fundo=247,244,251"), "o fundo do site: " + out);
        var m = java.util.regex.Pattern.compile("tinta=(\\d+)").matcher(out);
        assertTrue(m.find(), "o teste mede tinta: " + out);
        assertTrue(Integer.parseInt(m.group(1)) > 2000,
                "o autor e o corpo pintaram texto de verdade: " + out);
    }
}
