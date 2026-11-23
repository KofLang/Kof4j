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
 * Énois → desktop (Fase 3): `comunidades.$communityId.canais.$channelId`.
 *
 * <p>Medido em `comunidades.$communityId.canais.$channelId.tsx`: o container
 * `mx-auto w-full max-w-2xl space-y-4 py-6` = **672 px**, 16 px entre os
 * blocos, 24 px de padding vertical; o card `surface-card p-5` = 20 px; o
 * ícone `h-10 w-10 rounded-full bg-primary-soft` = 40 px; o título `text-xl`.
 * Os textos são os do site (`texts.ts` bloco `channelPage`): "Voltar para a
 * comunidade", "Escreva para este canal...", "Publicar no canal" e o vazio
 * "Nada por aqui ainda. Comece a conversa.".
 */
class EnosisChannelE2ETest implements LibraryInstallSupport {

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
    void theChannelPaintsTheHeaderTheEmptyStateAndTheComposer(@TempDir Path dir) throws Exception {
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
                import enois.Channel
                import enois.Text
                import game.Sdl3Backend

                main() {
                    val b = Sdl3Backend("Énóis", 900, 500)
                    b.clearFrame(247, 244, 251)
                    val title = Font("%s", 20)
                    val body = Font("%s", 14)
                    val h = ChannelScreen.paint(b, title, body, 900, 16,
                        "Voltar para a comunidade", "geral", listOf<String>(),
                        "Nada por aqui ainda. Comece a conversa.",
                        "Escreva para este canal...")
                    println("canal_h=" + h)
                    println("x=" + (900 - 672) / 2)
                    // o icone 40x40 em (114+20, 16+24+20+16+20): o centro e' +20,+20
                    val ic = b.readPixel(114 + 20 + 20, 16 + 24 + 20 + 16 + 20 + 20)
                    println("icone=" + (ic[0] & 255) + "," + (ic[1] & 255) + "," + (ic[2] & 255))
                    val cd = b.readPixel(114 + 400, 16 + 24 + 20 + 16 + 8)
                    println("card=" + (cd[0] & 255) + "," + (cd[1] & 255) + "," + (cd[2] & 255))
                    title.close(); body.close()
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

        assertTrue(out.contains("x=114"), "o max-w-2xl centrado: " + out);
        // --primary-soft no centro do icone 40x40
        assertTrue(out.contains("icone=238,232,255"), "o bg-primary-soft do icone: " + out);
        assertTrue(out.contains("card=255,255,255"), "o --surface-card: " + out);
        // py-6 (24) + o voltar (20) + o gap + o cabecalho (20x2+40)
        // + o gap + o corpo (20x2+20) + o gap + o composer (48) = 280
        assertTrue(out.contains("canal_h=280"), "a soma dos blocos: " + out);
    }
}
