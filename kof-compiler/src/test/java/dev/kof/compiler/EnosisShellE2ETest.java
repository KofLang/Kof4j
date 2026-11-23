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
 * Énois → desktop (Fase 0+1): os TOKENS e o SHELL do app rodam no nativo, com
 * os valores e as medidas MEDIDOS no site (`enois.ia.br`).
 *
 * <p>Fonte das medidas: `enois-design-system/src/styles.css` (`:root`) para as
 * cores; `design-system/src/domain/shell/authenticated-app-header.tsx` (`h-11`
 * = 44 px) para o header; `enois-frontend/src/routes/feed.tsx:1011`
 * (`grid-cols-[minmax(0,1fr)_320px] gap-6`) para o grid.
 */
class EnosisShellE2ETest implements LibraryInstallSupport {

    public String libraryName() { return "game"; }

    public List<String> libraryMarkers() { return List.of("Window.kf", "Draw.kf"); }

    private static String hostSdl3() {
        for (String p : new String[]{"/usr/lib64/libSDL3.so.0", "/usr/lib/x86_64-linux-gnu/libSDL3.so.0",
                "/usr/lib/libSDL3.so.0"}) {
            if (new File(p).exists()) return p;
        }
        return null;
    }

    @Test
    void theTokensMatchTheSiteExactly(@TempDir Path dir) throws Exception {
        // os valores do :root, conferidos um a um contra o CSS do site
        var r = compile(dir, """
                import enois.Tokens
                main() {
                    val b = Tokens.Background()
                    val p = Tokens.Primary()
                    val c = Tokens.Card()
                    val f = Tokens.Foreground()
                    println("bg=" + b.r() + "," + b.g() + "," + b.b())
                    println("primary=" + p.r() + "," + p.g() + "," + p.b())
                    println("card=" + c.r() + "," + c.g() + "," + c.b())
                    println("fg=" + f.r() + "," + f.g() + "," + f.b())
                    println("radius=" + Tokens.RadiusPx())
                }
                """);
        assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
        String out = run(dir, dir.resolve("out/Default/Main").toString());
        // #f7f4fb, #6746f5, oklch(1 0 0), #21173a, --radius: 1rem
        assertTrue(out.contains("bg=247,244,251"), "o --background do site: " + out);
        assertTrue(out.contains("primary=103,70,245"), "o --primary do site: " + out);
        assertTrue(out.contains("card=255,255,255"), "o --card do site: " + out);
        assertTrue(out.contains("fg=33,23,58"), "o --foreground do site: " + out);
        assertTrue(out.contains("radius=16"), "o --radius do site: " + out);
    }

    @Test
    void theShellPaintsTheSiteLayout(@TempDir Path dir) throws Exception {
        assumeTrue(hostSdl3() != null, "SDL3 host absent");
        assumeTrue(NativeToolchainAssumptions.hasTool("as", "ld"), "native toolchain absent");
        var r = compile(dir, """
                import enois.Shell
                import game.Sdl3Backend

                main() {
                    val W = 1280
                    val H = 800
                    val b = Sdl3Backend("Énóis", W, H)
                    val top = Shell.paint(b, W, H)
                    println("top=" + top)
                    val bg = b.readPixel(2, H - 2)
                    val hd = b.readPixel(2, 2)
                    val aside = b.readPixel(W - 200, top + 40)
                    println("fundo=" + (bg[0] & 255) + "," + (bg[1] & 255) + "," + (bg[2] & 255))
                    println("header=" + (hd[0] & 255) + "," + (hd[1] & 255) + "," + (hd[2] & 255))
                    println("aside=" + (aside[0] & 255) + "," + (aside[1] & 255) + "," + (aside[2] & 255))
                    b.close()
                }
                """);
        assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
        String out = run(dir, dir.resolve("out/Default/Main").toString());
        // header 44 + padding 16 = o topo da coluna em 60
        assertTrue(out.contains("top=60"), "h-11 + p-4: " + out);
        assertTrue(out.contains("fundo=247,244,251"), "o fundo do site: " + out);
        assertTrue(out.contains("header=255,255,255"), "o --card no header: " + out);
        assertTrue(out.contains("aside=255,255,255"), "o --card na coluna de 320 px: " + out);
    }

    private CompilationResult compile(Path dir, String code) throws Exception {
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
        // a lib `enois` mora no repositorio, nao em `libs/` (que o
        // LibraryInstallSupport instala): copia ao lado
        Path src = findLibsRoot().getParent().resolve("libs/enois");
        try (var files = Files.walk(src)) {
            for (Path f : files.filter(Files::isRegularFile).toList()) {
                Path dst = libs.resolve("enois").resolve(src.relativize(f));
                Files.createDirectories(dst.getParent());
                Files.copy(f, dst);
            }
        }
        Path main = dir.resolve("Main.kf");
        Files.writeString(main, code, StandardCharsets.UTF_8);
        String previous = System.getProperty("kof.install.dir");
        System.setProperty("kof.install.dir", dir.resolve("kof-install").toString());
        try {
            return new CompilerDriver().compile(main, dir.resolve("out"), Target.NATIVE);
        } finally {
            if (previous == null) System.clearProperty("kof.install.dir");
            else System.setProperty("kof.install.dir", previous);
        }
    }

    private static String run(Path dir, String bin) throws Exception {
        var pb = new ProcessBuilder(bin).redirectErrorStream(true);
        pb.environment().put("SDL_VIDEODRIVER", "dummy");
        var p = pb.start();
        String out = new String(p.getInputStream().readAllBytes()).strip();
        assertTrue(p.waitFor(120, TimeUnit.SECONDS), "o programa termina");
        assertEquals(0, p.exitValue(), "saida limpa: " + out);
        return out;
    }
}
