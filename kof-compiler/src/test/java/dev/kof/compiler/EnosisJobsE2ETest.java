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
 * Énois → desktop (Fase 3): `vagas` — a listagem de vagas.
 *
 * <p>Os TEXTOS vêm do código (`texts.ts`, bloco `jobs`): título
 * **"Oportunidades"**, busca **"Buscar vagas (ex.: java -estágio)"** e os
 * filtros `level`/`location`/`type` com os valores REAIS (`Remoto/Híbrido/
 * Presencial`, `Júnior/Pleno/Sênior/Liderança`).
 *
 * <p>As MEDIDAS vêm do markup (`vagas.tsx`): container `surface-card p-4 sm:p-6`
 * = 24 px, título `text-2xl` = 24 px, subtítulo `text-sm` = 14 px, pílula do
 * filtro `h-9` = 36 px com `border-border-soft`.
 */
class EnosisJobsE2ETest implements LibraryInstallSupport {

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
    void theJobsListingPaintsTheHeaderTheSearchAndTheFilters(@TempDir Path dir) throws Exception {
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
                    val b = Sdl3Backend("Énóis", 900, 400)
                    b.clearFrame(247, 244, 251)
                    val title = Font("%s", 24)
                    val body = Font("%s", 14)
                    val chip = Font("%s", 14)
                    val h = JobsScreen.paintHeader(b, title, body, 16, 16, 868,
                        "Oportunidades", "Vagas da comunidade e do mercado",
                        "Buscar vagas (ex.: java -estágio)")
                    println("header_h=" + h)
                    val fh = JobsScreen.paintFilters(b, chip, 16, 16 + h, 868,
                        listOf<String>("Nível: Júnior", "Local: Remoto", "Tipo: CLT", "Limpar filtros"))
                    println("filtros_h=" + fh)
                    // dentro do card e FORA do texto/borda
                    val c = b.readPixel(16 + 24 + 4, 16 + 24 + 4)
                    println("card=" + (c[0] & 255) + "," + (c[1] & 255) + "," + (c[2] & 255))
                    // a borda de cima da 1a pilula: y = 16 + h, x dentro dela
                    val bd = b.readPixel(16 + 20, 16 + h)
                    println("borda=" + (bd[0] & 255) + "," + (bd[1] & 255) + "," + (bd[2] & 255))
                    // um ponto onde NAO ha pilula (depois da ultima)
                    val free = b.readPixel(16 + 800, 16 + h)
                    println("vazio=" + (free[0] & 255) + "," + (free[1] & 255) + "," + (free[2] & 255))
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

        // p-6 (24) x2 + o titulo (24) + 8 + o subtitulo (14) + 16 + a busca (36) = 146
        assertTrue(out.contains("header_h=146"), "a conta do cabecalho: " + out);
        // a pilula h-9 (36) + o respiro (16) = 52
        assertTrue(out.contains("filtros_h=52"), "a linha de filtros: " + out);
        assertTrue(out.contains("card=255,255,255"), "o --surface-card: " + out);
        // --border-soft #e5e0eb
        assertTrue(out.contains("borda=229,224,235"), "a borda da pilula: " + out);
        // onde nao ha pilula, e' o card
        assertTrue(out.contains("vazio=255,255,255"), "o espaco depois das pilulas: " + out);
    }
}
