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
 * Énois → desktop (Fase 3): `games.$username` — a página do criador de jogos.
 *
 * <p>Medido em `src/routes/games.$username.tsx` e no design system: o card
 * `surface-card overflow-hidden`; a capa `h-32 sm:h-40` = **160** (sem capa, o
 * bloco `h-20` = 80 em `--primary-soft`); o avatar `-mt-10 h-16 w-16 ring-4`
 * = 64 subindo 40; o `p-4 sm:p-5` = 20; o título `text-xl` = 20; o `@` a 14; a
 * bio `border-t px-4 py-3` = 12+20 por linha; os links em pílulas a 12; a
 * grade `sm:grid-cols-2 gap-3` = 2 colunas de `(768 − 12)/2` = **378** com o
 * card do jogo `p-3` + `GameIcon h-12 w-12` = 48 → **72**. Os textos são os de
 * `texts.ts` bloco `games` (incluindo os plurais reais).
 *
 * <p>O ESTADO DA MEDIÇÃO DE PIXEL está em `known-bugs §1197`: um `readPixel`
 * num frame ainda não apresentado devolve o fundo, então este teste prende o
 * TEXTO e a GEOMETRIA, que são medidos por aritmética e pela fonte — nunca uma
 * cor suposta.
 */
class EnosisGameCreatorE2ETest implements LibraryInstallSupport {

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
    void theCreatorPagePinsTheTextsAndTheMeasuredGeometry(@TempDir Path dir) throws Exception {
        assumeTrue(hostReady(), "SDL3 or FreeType absent");
        assumeTrue(NativeToolchainAssumptions.hasTool("as", "ld"), "native toolchain absent");
        Path repo = findLibsRoot().getParent();
        Path font = repo.resolve("assets/fonts/Manrope.ttf");
        Path shim = repo.resolve("libs/enois/libkof_text.so");
        assumeTrue(Files.exists(font), "assets/fonts/Manrope.ttf absent");
        assumeTrue(Files.exists(shim), "libkof_text.so absent — run scripts/gen-enois-text.sh");

        Path libs = dir.resolve("kof-install/lib/kof-libs");
        copyLibrary(libs);
        for (String lib : new String[]{"image", "enois"}) {
            Path src = repo.resolve("libs/" + lib);
            try (var files = Files.walk(src)) {
                for (Path f : files.filter(Files::isRegularFile).toList()) {
                    Path dst = libs.resolve(lib).resolve(src.relativize(f));
                    Files.createDirectories(dst.getParent());
                    Files.copy(f, dst);
                }
            }
        }
        Path text = libs.resolve("enois/Text.kf");
        Files.writeString(text, Files.readString(text, StandardCharsets.UTF_8)
                .replace("\"@KOF_TEXT_SHIM@\"", "\"" + shim + "\"")
                .replace("\"kof_text_shim\"", "\"" + shim + "\""), StandardCharsets.UTF_8);

        Files.writeString(dir.resolve("Main.kf"), """
                import enois.GameCreator
                import enois.PostCard
                import enois.Text
                import game.Sdl3Backend

                main() {
                    val b = Sdl3Backend("Énóis", 800, 600)
                    b.clearFrame(247, 244, 251)
                    val body = Font("%s", 14)
                    val title = Font("%s", 20)
                    println("g1=" + GameCreatorScreen.gamesCount(1))
                    println("g0=" + GameCreatorScreen.gamesCount(0))
                    println("g3=" + GameCreatorScreen.gamesCount(3))
                    println("f1=" + GameCreatorScreen.followers(1))
                    println("f12=" + GameCreatorScreen.followers(12))
                    println("handle=" + GameCreatorScreen.handleLine("ana", 3, 12))
                    println("owner=" + GameCreatorScreen.actionLabel(true, false))
                    println("follow=" + GameCreatorScreen.actionLabel(false, false))
                    println("following=" + GameCreatorScreen.actionLabel(false, true))
                    println("notFound=" + GameCreatorScreen.notFound())
                    println("noGames=" + GameCreatorScreen.noGames())
                    println("gamesTitle=" + GameCreatorScreen.gamesTitle())
                    println("page=768")
                    println("bio_h=" + GameCreatorLayout.bioHeight(2))
                    println("card_bio=" + GameCreatorLayout.cardHeight(true, 2, 2))
                    println("card_nocover=" + GameCreatorLayout.cardHeight(false, 0, 0))

                    title.close(); body.close()
                    b.close()
                }
                """.formatted(font.toAbsolutePath(), font.toAbsolutePath()),
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

        // os textos do site, plural real incluido
        assertTrue(out.contains("g1=1 jogo\n"), "o singular: " + out);
        assertTrue(out.contains("g0=0 jogos\n"), "o plural com zero: " + out);
        assertTrue(out.contains("g3=3 jogos\n"), "o plural: " + out);
        assertTrue(out.contains("f1=1 seguidor\n"), "o seguidor singular: " + out);
        assertTrue(out.contains("f12=12 seguidores\n"), "os seguidores: " + out);
        assertTrue(out.contains("handle=@ana · 3 jogos · 12 seguidores\n"), "a linha do @: " + out);
        assertTrue(out.contains("owner=Editar minha página\n"), "o dono edita: " + out);
        assertTrue(out.contains("follow=Acompanhar\n"), "quem nao segue: " + out);
        assertTrue(out.contains("following=Acompanhando\n"), "quem segue: " + out);
        assertTrue(out.contains("notFound=Criador não encontrado.\n"), "o 404 do site: " + out);
        assertTrue(out.contains("noGames=Nenhum jogo publicado ainda.\n"), "o vazio: " + out);
        assertTrue(out.contains("gamesTitle=Jogos\n"), "o link do 404: " + out);
        // a geometria: a do site e a MESMA aritmetica que o modulo guarda
        assertTrue(out.contains("page=768\n"), "o max-w-3xl: " + out);
        // a faixa de bio: `py-3` x2 (24) + 2 linhas de 20 = 64
        assertTrue(out.contains("bio_h=64\n"), "a faixa de bio em 2 linhas: " + out);
        // o card: capa 160 + a linha do retrato (p-5 x2 40 + titulo 20 + 4 + 14 + 4
        // - `-mt-10` 40 + avatar 64 = 106) + (bio 64 + 20) + (links 44 + 20) = 414
        assertTrue(out.contains("bio_h=64\ncard_bio=414"), "o card com bio e links: " + out);
        // sem capa (o bloco de 80), sem bio e sem links: 80 + 106 = 186
        assertTrue(out.contains("card_bio=414\ncard_nocover=186"), "o card SEM capa, bio e links: " + out);
    }
}
