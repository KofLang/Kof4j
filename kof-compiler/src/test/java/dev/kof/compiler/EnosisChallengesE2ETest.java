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
 * Énois → desktop (Fase 3): `desafios` — a lista de desafios.
 *
 * <p>Os LIMITES são contrato e foram MEDIDOS em `desafios-composer.tsx:11-15`:
 * `MAX_TITLE = 120`, `MAX_DESCRIPTION = 3000`, `MAX_DAYS = 31`; e o
 * `contentTooLongError` do site fixa a participação em **2000**. As RECUSAS do
 * site interpelam esses números, então o teste compara o texto COMPLETO contra
 * o número — um limite alterado quebraria a mensagem.
 *
 * <p>O PESO é contrato também: o subtítulo do site diz "Autoria nominal, sem
 * ranking e sem prêmio", e `hasRanking()` devolve `false`.
 *
 * <p>Medidas do card (`desafios-card.tsx`): `rounded-2xl border border-border
 * p-4` = 16 px com raio 16, título `text-base`, corpo `text-sm`.
 */
class EnosisChallengesE2ETest implements LibraryInstallSupport {

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
    void theChallengesCarryTheSiteLimitsAndRefusals(@TempDir Path dir) throws Exception {
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
                import enois.Challenges
                import enois.Text
                import game.Sdl3Backend

                main() {
                    val b = Sdl3Backend("Énóis", 900, 400)
                    b.clearFrame(247, 244, 251)
                    val title = Font("%s", 16)
                    val body = Font("%s", 14)
                    val hint = Font("%s", 12)
                    // os LIMITES medidos no desafios-composer.tsx
                    println("t=" + ChallengeLimits.maxTitle())
                    println("d=" + ChallengeLimits.maxDescription())
                    println("days=" + ChallengeLimits.maxDays())
                    println("c=" + ChallengeLimits.maxContent())
                    // as RECUSAS interpelam os numeros — a mensagem inteira
                    println("r_title=" + ChallengesScreen.titleRefusal())
                    println("r_deadline=" + ChallengesScreen.deadlineRefusal())
                    // o peso: sem ranking
                    println("ranking=" + ChallengesScreen.hasRanking())
                    val h = ChallengesScreen.paintCard(b, title, body, 16, 16, 868,
                        "Escreva um teste", "Cubra o caminho do erro tambem.", 5)
                    println("card_h=" + h)
                    // o canto cortado pelo raio 16 -> o fundo
                    val k = b.readPixel(16, 16)
                    println("canto=" + (k[0] & 255) + "," + (k[1] & 255) + "," + (k[2] & 255))
                    val cd = b.readPixel(16 + 600, 16 + 8)
                    println("card=" + (cd[0] & 255) + "," + (cd[1] & 255) + "," + (cd[2] & 255))
                    title.close(); body.close(); hint.close()
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

        // os QUATRO limites, cada um o do seu bloco de textos no site
        assertTrue(out.contains("t=120"), "o titulo: " + out);
        assertTrue(out.contains("d=3000"), "a descricao: " + out);
        assertTrue(out.contains("days=31"), "o prazo: " + out);
        assertTrue(out.contains("c=2000"), "a participacao: " + out);
        // as RECUSAS completas: o numero e' o MESMO da mensagem
        assertTrue(out.contains("r_title=O título aceita no máximo 120 caracteres."),
                "a recusa do titulo: " + out);
        assertTrue(out.contains("r_deadline=O prazo precisa ser uma data futura (até 31 dias)."),
                "a recusa do prazo: " + out);
        // o peso do desafio: autoria nominal, sem ranking
        assertTrue(out.contains("ranking=false"), "sem ranking: " + out);
        // o card: raio 16 cortado no canto, e o card branco longe dele
        assertTrue(out.contains("canto=247,244,251"), "o raio 16 do rounded-2xl: " + out);
        assertTrue(out.contains("card=255,255,255"), "o --surface-card: " + out);
    }
}
