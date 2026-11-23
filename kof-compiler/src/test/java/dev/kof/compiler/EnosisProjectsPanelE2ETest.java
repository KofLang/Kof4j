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
 * Énois → desktop (Fase 3): projetos.painel — o painel de quem publica.
 *
 * <p>Medido em src/routes/projetos.painel.tsx e no design system: container
 * max-w-3xl space-y-6 (768/24), cabeçalho com o título text-2xl (24/32), a
 * descrição text-sm (14/20) e o space-y-1 (4) = 56; a lista space-y-3 (12) de
 * itens surface-card space-y-2 p-4 (padding 16, vão 8); as ações Button
 * size="sm" (32) na mesma linha do título. Os textos são os de texts.projects
 * (panel.* e editor.editTitle).
 */

class EnosisProjectsPanelE2ETest implements LibraryInstallSupport {

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
    void thePanelPinsTheMeasuredHeaderItemsAndTexts(@TempDir Path dir) throws Exception {
        assumeTrue(hostReady(), "SDL3 or FreeType absent");
        assumeTrue(NativeToolchainAssumptions.hasTool("as", "ld"), "native toolchain absent");
        Path repo = findLibsRoot().getParent();
        Path shim = repo.resolve("libs/enois/libkof_text.so");
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
                import enois.ProjectsPanel
                import game.Sdl3Backend

                main() {
                    val b = Sdl3Backend("Énóis", 800, 800)
                    b.clearFrame(247, 244, 251)
                    println("back=" + ProjectsPanelScreen.back())
                    println("title=" + ProjectsPanelScreen.title())
                    println("desc=" + ProjectsPanelScreen.description())
                    println("submit=" + ProjectsPanelScreen.submit())
                    println("empty=" + ProjectsPanelScreen.empty())
                    println("edit=" + ProjectsPanelScreen.edit())
                    println("remove=" + ProjectsPanelScreen.remove())
                    println("resubmit=" + ProjectsPanelScreen.resubmit())
                    println("actsRej=" + ProjectsPanelScreen.actionCount("REJECTED"))
                    println("actsPub=" + ProjectsPanelScreen.actionCount("PUBLISHED"))
                    println("note=" + ProjectsPanelScreen.reviewNote("REJECTED", "faltou link"))
                    println("notePub=[" + ProjectsPanelScreen.reviewNote("PUBLISHED", "x") + "]")
                    println("toast=" + ProjectsPanelScreen.removedToast())
                    println("page=" + ProjectsPanelMetrics.pageWidth())
                    println("header_h=" + ProjectsPanelScreen.headerHeight())
                    println("item_full=" + ProjectsPanelScreen.itemHeight(true, 2))
                    println("item_plain=" + ProjectsPanelScreen.itemHeight(false, 0))
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

        assertTrue(out.contains("title=Meus projetos\n"), "o titulo: " + out);
        assertTrue(out.contains("desc=Envie, edite, reenvie recusados e apague seus projetos.\n"), "a descricao: " + out);
        assertTrue(out.contains("submit=Enviar projeto\n"), "o envio: " + out);
        assertTrue(out.contains("edit=Editar projeto\n"), "o editar: " + out);
        assertTrue(out.contains("remove=Excluir\n"), "o excluir: " + out);
        assertTrue(out.contains("actsRej=3\n"), "3 acoes no recusado: " + out);
        assertTrue(out.contains("actsPub=2\n"), "2 acoes no publicado: " + out);
        assertTrue(out.contains("note=Recusado: faltou link\n"), "a nota: " + out);
        assertTrue(out.contains("notePub=[]\n"), "sem nota fora de REJECTED: " + out);
        assertTrue(out.contains("toast=Projeto excluído.\n"), "o toast: " + out);
        assertTrue(out.contains("page=768\n"), "o max-w-3xl: " + out);
        assertTrue(out.contains("header_h=56\n"), "titulo 32 + vao 4 + descricao 20: " + out);
        // item: p-4 32 + titulo 24 + vao 8 + status 16 + nota (12+8) + sinopse (2x20+8)
        assertTrue(out.contains("item_full=148\n"), "o item completo: " + out);
        // ultima linha: ancorada na anterior (§1197)
        assertTrue(out.contains("item_full=148\nitem_plain=80"), "o item sem nota nem sinopse: " + out);
    }
}
