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
 * Énois → desktop (Fase 3): os TRÊS estados de acesso do canal.
 *
 * <p>Medido em `comunidades.$communityId.canais.$channelId.tsx`: o container
 * comum é `surface-card space-y-3 p-5` = 20 px, a mensagem
 * `text-sm text-text-subtle` = 14 px, e cada estado carrega a cor do seu PAPEL
 * — o privado em `--primary`, o erro em `--destructive`, e o visitante com o
 * botão de entrar em `--primary-soft`. As cores saem dos tokens que o site usa
 * para cada papel, não de uma escolha minha.
 */
class EnosisChannelAccessE2ETest implements LibraryInstallSupport {

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
    void eachAccessStateCarriesItsOwnRoleColour(@TempDir Path dir) throws Exception {
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
                    val b = Sdl3Backend("Énóis", 800, 500)
                    b.clearFrame(247, 244, 251)
                    val body = Font("%s", 14)
                    val h0 = ChannelAccess.paint(b, body, 64, 16, 672, 0,
                        "Este canal é privado: só membros da comunidade podem ler e publicar.", "")
                    println("privado_h=" + h0)
                    val h1 = ChannelAccess.paint(b, body, 64, 16 + h0 + 16, 672, 1,
                        "Entre na comunidade para acessar este canal.", "Entrar na comunidade")
                    println("visitante_h=" + h1)
                    val h2 = ChannelAccess.paint(b, body, 64, 16 + h0 + 16 + h1 + 16, 672, 2,
                        "Não foi possível carregar o canal.", "")
                    println("erro_h=" + h2)
                    // o acento: 1 px a direita da borda do card, sobre a barra de 4 px
                    val a0 = b.readPixel(64 + 1, 16 + 20)
                    println("privado=" + (a0[0] & 255) + "," + (a0[1] & 255) + "," + (a0[2] & 255))
                    val y2 = 16 + h0 + 16 + h1 + 16
                    val a2 = b.readPixel(64 + 1, y2 + 20)
                    println("erro=" + (a2[0] & 255) + "," + (a2[1] & 255) + "," + (a2[2] & 255))
                    // o botao do visitante: --primary-soft
                    val btn = b.readPixel(64 + 25, 16 + h0 + 16 + 51)
                    println("botao=" + (btn[0] & 255) + "," + (btn[1] & 255) + "," + (btn[2] & 255))
                    body.close()
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

        // p-5 (20) x2 + a mensagem (14) = 54 quando NAO ha botao
        assertTrue(out.contains("privado_h=54"), "o estado sem botao: " + out);
        assertTrue(out.contains("erro_h=54"), "o erro tambem sem botao: " + out);
        // com o botao: 54 + o gap (12) + o botao (36) = 102
        assertTrue(out.contains("visitante_h=102"), "o visitante com o botao: " + out);
        // --primary #6746f5 e --destructive #b30018 — a cor de cada PAPEL
        assertTrue(out.contains("privado=103,70,245"), "o --primary do privado: " + out);
        assertTrue(out.contains("erro=179,0,24"), "o --destructive do erro: " + out);
        // --primary-soft #eee8ff no botao do visitante
        assertTrue(out.contains("botao=238,232,255"), "o --primary-soft do botao: " + out);
    }
}
