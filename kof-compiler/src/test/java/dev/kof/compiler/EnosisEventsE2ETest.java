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
 * Énois → desktop (Fase 3): `eventos` — a agenda da comunidade.
 *
 * <p>Medido em `eventos.tsx`: a grade
 * `grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3` — **3 colunas no
 * desktop** — e o card `surface-card space-y-3 p-4` = 16 px. Os textos são os
 * do site (`texts.ts` bloco `events`): título "Eventos da comunidade", os
 * formatos "Online"/"Presencial"/"Híbrido" e o vazio "Nenhum evento por aqui
 * ainda.".
 *
 * <p>A prova da GRADE é a GEOMETRIA: a largura da coluna sai da conta
 * `(conteudo - 2*gap) / 3`, e o segundo card precisa começar EXATAMENTE uma
 * coluna + o gap à direita do primeiro — se a grade caísse para 1 coluna, a
 * amostra do segundo card pegaria o fundo.
 */
class EnosisEventsE2ETest implements LibraryInstallSupport {

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
    void theEventsGridLaysOutThreeColumnsWithTheirFormats(@TempDir Path dir) throws Exception {
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
                import enois.Events
                import enois.Text
                import game.Sdl3Backend

                main() {
                    val b = Sdl3Backend("Énóis", 1280, 600)
                    b.clearFrame(247, 244, 251)
                    val title = Font("%s", 16)
                    val meta = Font("%s", 12)
                    val cw = 904
                    val colW = EventsScreen.columnWidth(cw)
                    println("cols=" + 3)
                    println("col_w=" + colW)
                    println("f=" + EventsScreen.formatLabel(0) + "|"
                        + EventsScreen.formatLabel(1) + "|" + EventsScreen.formatLabel(2))
                    println("vazio=" + EventsScreen.emptyText())
                    val h = EventsScreen.paint(b, title, meta, 16, 16, cw,
                        listOf<String>("Meetup Java", "Workshop Kof", "Encontro", "Ritual"),
                        listOf<Int>(0, 1, 2, 0),
                        listOf<String>("23 nov · 08:00", "24 nov · 19:00",
                                       "25 nov · 14:00", "26 nov · 20:00"))
                    println("grade_h=" + h)
                    // o 1o card e o 2o (uma coluna + o gap a direita) — longe do texto
                    val c1 = b.readPixel(16 + colW - 20, 16 + 130)
                    println("card1=" + (c1[0] & 255) + "," + (c1[1] & 255) + "," + (c1[2] & 255))
                    val c2 = b.readPixel(16 + colW + 16 + colW - 20, 16 + 130)
                    println("card2=" + (c2[0] & 255) + "," + (c2[1] & 255) + "," + (c2[2] & 255))
                    // o GAP entre eles e' o fundo
                    val gp = b.readPixel(16 + colW + 8, 16 + 130)
                    println("gap=" + (gp[0] & 255) + "," + (gp[1] & 255) + "," + (gp[2] & 255))
                    // a 2a linha comeca em y = 16 + 140 + 16
                    val c4 = b.readPixel(16 + colW - 20, 16 + 140 + 16 + 130)
                    println("card4=" + (c4[0] & 255) + "," + (c4[1] & 255) + "," + (c4[2] & 255))
                    title.close(); meta.close()
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

        // (904 - 2*16) / 3 = 290.66 -> 290
        assertTrue(out.contains("col_w=290"), "a largura da coluna LG: " + out);
        // os TRES formatos do site
        assertTrue(out.contains("f=Online|Presencial|Híbrido"), "os formatos: " + out);
        assertTrue(out.contains("vazio=Nenhum evento por aqui ainda."), "o vazio: " + out);
        // 2 linhas de 140 + 1 gap de 16 = 296
        assertTrue(out.contains("grade_h=296"), "a altura da grade: " + out);
        // o 2o card esta na COLUNA 2 (3 colunas; se caísse para 1, aqui seria o fundo)
        assertTrue(out.contains("card1=255,255,255"), "o 1o card: " + out);
        assertTrue(out.contains("card2=255,255,255"), "o 2o card NA COLUNA 2: " + out);
        assertTrue(out.contains("gap=247,244,251"), "o gap-4 entre as colunas: " + out);
        assertTrue(out.contains("card4=255,255,255"), "a 2a linha: " + out);
    }
}
