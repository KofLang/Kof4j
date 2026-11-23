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
 * Énois → desktop (Fase 3): `projetos.$username` — a página do autor de projetos.
 *
 * <p>Medido em `src/routes/projetos.$username.tsx`: container `max-w-5xl
 * space-y-6` (1024 / 24), cabeçalho `space-y-1` com o nome `text-2xl` (24) e o
 * rótulo `text-sm` (14), grade `grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3`
 * (330 — as mesmas colunas do catálogo), cartão `surface-card block h-full p-4`
 * SEM capa nem selo, título e sinopse com `line-clamp-2`. Os textos são os de
 * `texts.projects` (o mesmo bloco do catálogo).
 *
 * <p>Este teste prende TEXTO e GEOMETRIA. Cor amostrada fica de fora: o
 * `known-bugs §1197` ainda não fecha a leitura de um `fillRect`.
 */
class EnosisProjectAuthorE2ETest implements LibraryInstallSupport {

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
    void theAuthorPagePinsTheMeasuredHeaderAndCards(@TempDir Path dir) throws Exception {
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
                import enois.ProjectAuthor
                import enois.Projects
                import enois.Text
                import game.Sdl3Backend

                main() {
                    val b = Sdl3Backend("Énóis", 1060, 700)
                    b.clearFrame(247, 244, 251)
                    val title = Font("%s", 16)
                    val body = Font("%s", 14)
                    println("label=" + ProjectAuthorScreen.label())
                    println("empty=" + ProjectAuthorScreen.empty())
                    println("hint=" + ProjectAuthorScreen.emptyHint())
                    println("synShort=" + ProjectAuthorScreen.synopsis("curta", "longa"))
                    println("synLong=" + ProjectAuthorScreen.synopsis("", "longa"))
                    println("synNone=[" + ProjectAuthorScreen.synopsis("", "") + "]")
                    println("col=" + ProjectsMetrics.columnWidth(ProjectAuthorMetrics.pageWidth()))
                    println("header_h=" + ProjectAuthorMetrics.headerHeight())
                    val t = "Uma pesquisa de campo sobre acessibilidade"
                    println("t_lines=" + ProjectAuthorScreen.titleLines(title, t, 330))
                    println("card=" + ProjectAuthorScreen.cardHeight(
                        ProjectAuthorScreen.titleLines(title, t, 330), 2))
                    println("card1=" + ProjectAuthorScreen.cardHeight(1, 1))
                    title.close(); body.close()
                    b.close()
                }
                """.formatted(font.toAbsolutePath(), font.toAbsolutePath()),
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

        assertTrue(out.contains("label=Projetos da comunidade\n"), "o rotulo do site: " + out);
        assertTrue(out.contains("empty=Nenhum projeto encontrado.\n"), "o vazio: " + out);
        assertTrue(out.contains("synShort=curta\n"), "a sinopse prefere a curta: " + out);
        assertTrue(out.contains("synLong=longa\n"), "a sinopse cai na longa: " + out);
        assertTrue(out.contains("synNone=[ ]\n"), "a sinopse vazia e um espaco: " + out);
        assertTrue(out.contains("col=330\n"), "a coluna 330 do catalogo: " + out);
        assertTrue(out.contains("header_h=56\n"), "h1 32 + vao 4 + rotulo 20: " + out);
        // o ÚLTIMO print do programa: ancorado na linha anterior (known-bugs §1197)
        assertTrue(out.contains("card=116\ncard1=76"), "p-4 x2 (32) + 2 linhas de 20 + mt-1 (4): " + out);
    }
}
