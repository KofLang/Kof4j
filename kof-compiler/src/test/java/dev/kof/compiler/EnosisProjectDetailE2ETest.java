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
 * Énois → desktop (Fase 3): `projetos.$username.$projectSlug` — o detalhe.
 *
 * <p>Medido na rota e no design system: container `max-w-3xl space-y-6`
 * (768/24); o artigo `surface-card overflow-hidden`; a capa `h-56 sm:h-72` =
 * **288** com imagem e `h-40` = **160** sem; o corpo `space-y-4 p-5 sm:p-6`
 * (vão 16, `p-6` = 24); o selo e as tags a 12; o título `text-2xl` (24/32); a
 * autoria, a sinopse e a descrição a 14 (descrição `leading-relaxed` = 24/linha);
 * os botões `px-4 py-2` = 36 de altura. Os textos são os de `texts.projects`.
 */
class EnosisProjectDetailE2ETest implements LibraryInstallSupport {

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
    void theDetailPinsTheMeasuredArticleAndTexts(@TempDir Path dir) throws Exception {
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
                import enois.ProjectDetail
                import game.Sdl3Backend

                main() {
                    val b = Sdl3Backend("Énóis", 800, 900)
                    b.clearFrame(247, 244, 251)
                    println("back=" + ProjectDetailScreen.back())
                    println("nf=" + ProjectDetailScreen.notFound())
                    println("by=" + ProjectDetailScreen.submittedBy("mel"))
                    println("act=" + ProjectDetailScreen.openProject() + "/" + ProjectDetailScreen.openRepo())
                    println("sub=" + ProjectDetailScreen.statusLabel("SUBMITTED"))
                    println("rej=" + ProjectDetailScreen.statusLabel("REJECTED"))
                    println("susp=" + ProjectDetailScreen.statusLabel("SUSPENDED"))
                    println("showsPub=" + ProjectDetailScreen.showsStatus("PUBLISHED"))
                    println("showsSub=" + ProjectDetailScreen.showsStatus("SUBMITTED"))
                    println("tags=" + ProjectDetailScreen.tagCount("a, b,,  c "))
                    println("tags0=" + ProjectDetailScreen.tagCount(""))
                    println("coverImg=" + ProjectDetailScreen.coverHeight(true))
                    println("coverNone=" + ProjectDetailScreen.coverHeight(false))
                    println("head_cat=" + ProjectDetailScreen.headingHeight(true, 1))
                    // o corpo do artigo, com e sem os blocos (`articleHeight` =
                    // capa + corpo; a capa ja' esta' presa acima)
                    println("body_min=" + ProjectDetailScreen.articleBody(
                        false, 1, false, 0, 0, false, false, false))
                    // com tudo: p-6 x2 48 + titulo 84 + sinopse 20 + desc 2x24
                    // + tags 24 + acoes 40 + 4 vaos de 16 = 328
                    println("body_full=" + ProjectDetailScreen.articleBody(
                        true, 1, true, 2, 2, true, true, false))
                    b.close()
                }
                """,
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

        assertTrue(out.contains("back=Voltar\n"), "o link de volta: " + out);
        assertTrue(out.contains("nf=Projeto não encontrado.\n"), "o 404: " + out);
        assertTrue(out.contains("by=por mel\n"), "a autoria: " + out);
        assertTrue(out.contains("act=Abrir projeto/Ver repositório\n"), "as acoes: " + out);
        assertTrue(out.contains("sub=Em revisão\n"), "SUBMITTED: " + out);
        assertTrue(out.contains("rej=Recusado\n"), "REJECTED: " + out);
        assertTrue(out.contains("susp=Suspenso\n"), "SUSPENDED: " + out);
        assertTrue(out.contains("showsPub=false\n"), "PUBLISHED nao mostra: " + out);
        assertTrue(out.contains("showsSub=true\n"), "SUBMITTED mostra: " + out);
        assertTrue(out.contains("tags=3\n"), "3 tags com vazias fora: " + out);
        assertTrue(out.contains("coverImg=288\n"), "h-72 no sm: " + out);
        assertTrue(out.contains("coverNone=160\n"), "h-40 sem imagem: " + out);
        assertTrue(out.contains("head_cat=84\n"), "selo 24 + 4 + titulo 32 + 4 + autoria 20: " + out);
        assertTrue(out.contains("head_cat=84\nbody_min=104"), "o corpo minimo (p-6 48 + titulo 56 + 0 vaos): " + out);
        // ultima linha do programa: ancorada na anterior (§1197)
        assertTrue(out.contains("body_min=104\nbody_full=328"), "o corpo com todos os blocos: " + out);
    }
}
