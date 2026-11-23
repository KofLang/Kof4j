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
 * Énois → desktop (Fase 3): `mentoria` — o hero da mentoria.
 *
 * <p>Medido em `mentoria.tsx`: o card `surface-card relative overflow-hidden
 * p-5 sm:p-8` = **32 px**, o badge `rounded-full bg-primary-soft text-primary`,
 * o título `mt-3 text-3xl font-semibold tracking-tight` = **30 px**, a
 * descrição `mt-2 text-muted-foreground` e as pílulas `mt-5 flex flex-wrap
 * gap-2`. Os textos são os do site (`texts.ts` bloco `mentoring`).
 *
 * <p>A QUEBRA DE LINHA DO TÍTULO é MEDIDA, não suposta: a largura interna é
 * `868 - 2×32 = 804`, e o título de 30 px ocupa **744** — cabe em UMA linha.
 * Se a fonte mudasse de largura o suficiente, a quebra mudaria e a altura do
 * hero mudaria com ela (o teste prende a altura).
 */
class EnosisMentoringE2ETest implements LibraryInstallSupport {

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
    void theHeroWrapsTheTitleByItsMeasuredWidth(@TempDir Path dir) throws Exception {
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
                import enois.Mentoring
                import enois.Text
                import game.Sdl3Backend

                main() {
                    val b = Sdl3Backend("Énóis", 900, 500)
                    b.clearFrame(247, 244, 251)
                    val badge = Font("%s", 12)
                    val title = Font("%s", 30)
                    val body = Font("%s", 16)
                    // a largura do titulo e a interna do card, MEDIDAS
                    val t = "Aprenda com quem já passou por onde você quer chegar."
                    println("t_w=" + title.measure(t))
                    println("inner=" + (868 - 2 * 32))
                    println("linhas=" + MentoringScreen.wrapText(title, t, 804).size)
                    val h = MentoringScreen.paintHero(b, badge, title, body, 16, 16, 868,
                        "Mentoria nativa", t,
                        "Sessões reais com mentores verificados — sem performance, sem ruído. Avaliações honestas, trilhas de aprendizado e agendamento em um clique.",
                        listOf<String>("Encontrar mentor", "Tornar-me mentor"))
                    println("hero_h=" + h)
                    // o badge em (16+32, 16+32) mede 24: amostra o centro
                    val bd = b.readPixel(16 + 32 + 20, 16 + 32 + 12)
                    println("badge=" + (bd[0] & 255) + "," + (bd[1] & 255) + "," + (bd[2] & 255))
                    val cd = b.readPixel(16 + 700, 16 + 8)
                    println("card=" + (cd[0] & 255) + "," + (cd[1] & 255) + "," + (cd[2] & 255))
                    badge.close(); title.close(); body.close()
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

        // a quebra e' MEDIDA: 744 cabe em 804 -> 1 linha
        assertTrue(out.contains("t_w=744"), "a largura do titulo: " + out);
        assertTrue(out.contains("inner=804"), "a largura interna (868 - 2x32): " + out);
        assertTrue(out.contains("linhas=1"), "o titulo cabe numa linha: " + out);
        // a altura do hero trava a quebra com a DESCRICAO REAL do site:
        // 32x2 + badge 24 + mt-3 12 + 1 linha de 38 + mt-2 ajustado
        // + a descricao (2 linhas) + mt-5 20 + a pilula 36 = 254
        assertTrue(out.contains("hero_h=254"), "a altura do hero com a descricao inteira: " + out);
        // o badge em --primary-soft
        assertTrue(out.contains("badge=238,232,255"), "o primary-soft do badge: " + out);
        assertTrue(out.contains("card=255,255,255"), "o --surface-card: " + out);
    }
}
