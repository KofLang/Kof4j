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
 * Énois → desktop (Fase 3): `explorar` — o hub das salas.
 *
 * <p>Os DADOS vêm do código, extraídos sem invenção: os itens e as seções de
 * `src/components/explore-items.ts` (13 / 2 / 3 / 5 = 23), os rótulos e as
 * descrições de `texts.explore.items`, e a ORDEM das seções de
 * `texts.explore.sections` (community, play, build, work — o comentário do
 * próprio fonte diz que a ordem das chaves é a ordem das seções).
 *
 * <p>As medidas vêm do `ExploreHub` do design system: ícone `h-10 w-10` = 40 px,
 * rótulo `text-[15px]`, descrição `text-[13px]`, container `max-w-3xl` = 768,
 * título de seção `text-[12px]`.
 */
class EnosisExploreE2ETest implements LibraryInstallSupport {

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
    void theHubHasTheSiteFourSectionsAndTwentyThreeItems(@TempDir Path dir) throws Exception {
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
                import enois.Explore
                import enois.Shell
                import enois.Text
                import game.Sdl3Backend

                main() {
                    val W = 1280
                    val b = Sdl3Backend("Énóis", W, 800)
                    val top = Shell.paint(b, W, 800)
                    val secs = ExploreHub.sections()
                    println("secoes=" + secs.size)
                    var i = 0
                    var total = 0
                    while (i < secs.size) {
                        println("s" + i + "=" + secs[i].title() + ":" + secs[i].tiles().size)
                        total = total + secs[i].tiles().size
                        i = i + 1
                    }
                    println("total=" + total)
                    println("primeiro=" + secs[0].tiles()[0].label())
                    println("segunda_secao=" + secs[1].title())
                    println("ultimo=" + secs[3].tiles()[secs[3].tiles().size - 1].label())
                    val label = Font("%s", 15)
                    val desc = Font("%s", 13)
                    val head = Font("%s", 12)
                    val h = ExploreHub.paint(b, label, desc, head, W, top)
                    println("hub_h=" + h)
                    // o tile e' --surface-card; o icone e' --primary-soft
                    val card = b.readPixel(W / 2 + 300, top + 60 + 5)
                    println("card=" + (card[0] & 255) + "," + (card[1] & 255) + "," + (card[2] & 255))
                    val icon = b.readPixel(W / 2 - 384 + 20, top + 60 + 36)
                    println("icone=" + (icon[0] & 255) + "," + (icon[1] & 255) + "," + (icon[2] & 255))
                    label.close(); desc.close(); head.close()
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

        // as 4 secoes e as contagens do explore-items.ts
        assertTrue(out.contains("secoes=4"), "texts.explore.sections tem 4: " + out);
        assertTrue(out.contains("s0=Comunidade:13"), "community tem 13: " + out);
        assertTrue(out.contains("s1=Brincar:2"), "play tem 2: " + out);
        assertTrue(out.contains("s2=Construir:3"), "build tem 3: " + out);
        assertTrue(out.contains("s3=Trabalho:5"), "work tem 5: " + out);
        assertTrue(out.contains("total=23"), "os 23 itens do fonte: " + out);
        // a ORDEM e os extremos da lista real
        assertTrue(out.contains("primeiro=Colagem"), "o 1o item de community: " + out);
        assertTrue(out.contains("segunda_secao=Brincar"), "a ordem declarada: " + out);
        assertTrue(out.contains("ultimo=Mentoria"), "o ultimo item de work: " + out);
        // as superfícies
        assertTrue(out.contains("card=255,255,255"), "o --surface-card do tile: " + out);
        assertTrue(out.contains("icone=238,232,255"), "o --primary-soft do quadrado: " + out);
    }
}
