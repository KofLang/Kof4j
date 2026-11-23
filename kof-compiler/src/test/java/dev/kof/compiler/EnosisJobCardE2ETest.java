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
 * Énois → desktop (Fase 3): o CARD de vaga (o item da listagem).
 *
 * <p>Medido no `visibleJobs.map` de `vagas.tsx`: o card é
 * `surface-card flex flex-col p-5`, o avatar `h-12 w-12 rounded-xl` = **48 px
 * com raio 12** — um quadrado ARREDONDADO, não um círculo (o avatar de USUÁRIO
 * é o círculo; o da VAGA é `rounded-xl`), o título `font-semibold`, a empresa
 * `text-sm text-muted-foreground`, e as pílulas `px-2.5 py-1 text-xs` (o match
 * em `--primary-soft`, local/salário em `--muted`).
 */
class EnosisJobCardE2ETest implements LibraryInstallSupport {

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
    void theJobCardPaintsTheRoundedXlAvatarAndTheMatchChip(@TempDir Path dir) throws Exception {
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
                import enois.Jobs
                import enois.Text
                import game.Sdl3Backend

                main() {
                    val b = Sdl3Backend("Énóis", 900, 300)
                    b.clearFrame(247, 244, 251)
                    val title = Font("%s", 16)
                    val body = Font("%s", 14)
                    val chip = Font("%s", 12)
                    val h = JobCard.paint(b, title, body, chip, 16, 16, 868,
                        "Desenvolvedor Java", "Énois",
                        listOf<String>("87%% match", "Remoto", "Sênior"))
                    println("card_h=" + h)
                    // o centro do avatar 48x48 em (36,36) -> (60,60)
                    val av = b.readPixel(16 + 20 + 24, 16 + 20 + 24)
                    println("avatar=" + (av[0] & 255) + "," + (av[1] & 255) + "," + (av[2] & 255))
                    // o CANTO superior esquerdo do avatar: cortado pelo raio 12
                    val corner = b.readPixel(16 + 20, 16 + 20)
                    println("canto=" + (corner[0] & 255) + "," + (corner[1] & 255) + "," + (corner[2] & 255))
                    // a pilula de match: o topo dela (y = 16+20+48+12 = 96), 3 px da esquerda
                    val ch = b.readPixel(16 + 20 + 3, 16 + 20 + 48 + 12 + 2)
                    println("pilula=" + (ch[0] & 255) + "," + (ch[1] & 255) + "," + (ch[2] & 255))
                    title.close(); body.close(); chip.close()
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

        // p-5 (20) x2 + o avatar (48) + o respiro (12) + a pilula (22) = 122
        assertTrue(out.contains("card_h=122"), "a conta do card: " + out);
        // --primary-soft no centro do avatar
        assertTrue(out.contains("avatar=238,232,255"), "o fundo do avatar: " + out);
        // o CANTO do avatar e' cortado pelo raio 12 -> o --surface-card
        assertTrue(out.contains("canto=255,255,255"), "o raio 12 do rounded-xl: " + out);
        // a pilula de match
        assertTrue(out.contains("pilula=238,232,255"), "o --primary-soft do match: " + out);
    }
}
