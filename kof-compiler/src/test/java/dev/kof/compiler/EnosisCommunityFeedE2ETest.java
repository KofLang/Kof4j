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
 * Énois → desktop (Fase 3): o FEED da comunidade — a primeira tela que COMPÕE
 * shell + header de comunidade + coluna de conteúdo + coluna lateral.
 *
 * <p>Medido em `comunidades.$communityId.tsx`: o grid é
 * `[minmax(0,1fr)_280px] gap-6` (linha 875) — o aside da comunidade é **280 px**
 * (o feed GLOBAL usa 320); o título "Feed" é `text-lg` = 18 px (877); a lista
 * vazia é `surface-card p-8 text-sm` com os textos de `texts.ts` (2332/2333).
 */
class EnosisCommunityFeedE2ETest implements LibraryInstallSupport {

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
    void theCommunityFeedComposesTheContentAndTheAside(@TempDir Path dir) throws Exception {
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
                import enois.CommunityFeed
                import enois.Shell
                import enois.Text
                import game.Sdl3Backend

                main() {
                    val W = 1280
                    val b = Sdl3Backend("Énóis", W, 800)
                    val top = Shell.paint(b, W, 800)
                    val cw = CommunityFeed.contentWidth(W)
                    println("conteudo_w=" + cw)
                    val head = Font("%s", 18)
                    val body = Font("%s", 16)
                    val author = Font("%s", 12)
                    val small = Font("%s", 14)
                    val fy = top + 16
                    // sem posts: o texto de membro do site
                    val eh = CommunityFeed.paintFeed(b, head, body, 16, fy, cw, "Feed",
                        listOf<String>(), listOf<String>(),
                        "Nenhum post ainda. Seja o primeiro!", author)
                    println("vazio_h=" + eh)
                    val ec = b.readPixel(16 + cw - 40, fy + 31)
                    println("card=" + (ec[0] & 255) + "," + (ec[1] & 255) + "," + (ec[2] & 255))
                    // com um post
                    val fh = CommunityFeed.paintFeed(b, head, body, 16, fy, cw, "Feed",
                        listOf<String>("Maria Silva"), listOf<String>("Primeiro post da comunidade."),
                        "", author)
                    println("com_post_h=" + fh)
                    // o aside de 280
                    val ax = 16 + cw + 24
                    val ah = CommunityFeed.paintAside(b, small, small, ax, fy, 300,
                        listOf<String>("Membros"), listOf<String>("12"))
                    println("aside_h=" + ah)
                    // o aside NAO pode invadir a coluna de conteudo
                    println("aside_x=" + ax)
                    val ac = b.readPixel(ax + 3, fy + 3)
                    println("aside_card=" + (ac[0] & 255) + "," + (ac[1] & 255) + "," + (ac[2] & 255))
                    head.close(); body.close(); author.close(); small.close()
                    b.close()
                }
                """.formatted(font.toAbsolutePath(), font.toAbsolutePath(), font.toAbsolutePath(),
                font.toAbsolutePath()), StandardCharsets.UTF_8);

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

        // 1280 - 32 (p-4 dos dois lados) - 280 (aside) - 24 (gap-6) = 944
        assertTrue(out.contains("conteudo_w=944"), "o grid da comunidade (280 no aside): " + out);
        // p-8 (32) x2 + a linha do texto = 112 quando vazia
        assertTrue(out.contains("vazio_h=112"), "a lista vazia: " + out);
        assertTrue(out.contains("card=255,255,255"), "o surface-card da lista vazia: " + out);
        assertTrue(out.contains("aside_h=62"), "a secao do aside: " + out);
        // o aside comeca DEPOIS da coluna de conteudo + o gap
        assertTrue(out.contains("aside_x=984"), "16 + 944 + 24: " + out);
        assertTrue(out.contains("aside_card=255,255,255"), "o card do aside: " + out);
    }
}
