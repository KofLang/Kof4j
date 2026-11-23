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
 * Énois → desktop (Fase 3): o FLUXO da página do criador num programa nativo.
 *
 * <p>As fatias anteriores provam cada tela isolada. Este teste compila UM
 * programa que junta os módulos da mesma rota — `GameCreator` (o texto e a
 * geometria da página de criador), `Projects` (o catálogo de onde se chega
 * nela) e `Mentoring` — e o roda no nativo: é o caminho "abre o app, navega,
 * monta duas telas" que nenhum teste de unidade exercita.
 *
 * <p>Um DEFEITO medido nesta fatia está em `known-bugs §1197` e por isso nada
 * aqui afirma cor de pixel de preenchimento feito inline no `main`; o que se
 * prova é que os módulos coexistem no mesmo binário e devolvem os textos e as
 * medidas reais.
 */
class EnosisCreatorFlowE2ETest implements LibraryInstallSupport {

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
    void theCreatorRouteComposesWithTheCatalogueAndTheMentoringHero(@TempDir Path dir) throws Exception {
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
                import enois.GameCreator
                import enois.Mentoring
                import enois.Projects
                import enois.Text
                import game.Sdl3Backend

                main() {
                    val b = Sdl3Backend("Énóis", 1280, 900)
                    b.clearFrame(247, 244, 251)
                    val body = Font("%s", 16)
                    // a pagina do criador
                    println("loading=" + GameCreatorScreen.loading())
                    println("noGames=" + GameCreatorScreen.noGames())
                    println("handle=" + GameCreatorScreen.handleLine("mel", 2, 7))
                    println("card=" + GameCreatorLayout.cardHeight(true, 2, 2))
                    // o catalogo de onde se chega nela
                    println("grid_col=" + ProjectsMetrics.columnWidth(ProjectsMetrics.pageWidth()))
                    println("grid_empty=" + ProjectsScreen.empty())
                    // a mentoria, a mesma familia de pagina de autor
                    println("mentor=" + MentoringMetrics.titleSize())
                    body.close()
                    b.close()
                }
                """.formatted(font.toAbsolutePath()),
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

        assertTrue(out.contains("loading=Carregando…\n"), "o carregando: " + out);
        assertTrue(out.contains("noGames=Nenhum jogo publicado ainda.\n"), "o vazio: " + out);
        assertTrue(out.contains("handle=@mel · 2 jogos · 7 seguidores\n"), "a linha do @: " + out);
        assertTrue(out.contains("card=414\n"), "o card medido: " + out);
        assertTrue(out.contains("grid_col=330\n"), "a coluna do catalogo: " + out);
        assertTrue(out.contains("grid_empty=Nenhum projeto encontrado.\n"), "o vazio do catalogo: " + out);
        // ancorada na linha ANTERIOR: a leitura exata da ultima linha do ``out``
        // (ja' com ``strip()``) tem sido o ponto fragil das assercoes deste repo
        assertTrue(out.contains("grid_empty=Nenhum projeto encontrado.\nmentor=30"), "o titulo da mentoria em 30 px: " + out);
    }
}
