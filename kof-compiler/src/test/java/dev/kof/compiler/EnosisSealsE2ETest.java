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
 * Énois → desktop (Fase 3): `selos` — os selos de contribuição.
 *
 * <p>Medido em `selos.tsx`: o container `mx-auto max-w-3xl space-y-8 py-10
 * sm:py-14` = **768 px** com 32 px entre as seções e 56 px de padding vertical,
 * o eyebrow `text-xs font-semibold uppercase tracking-widest text-primary`, o
 * título `text-3xl sm:text-4xl` = **36 px**, a intro `text-base
 * leading-relaxed` e as seções `text-lg`/`text-sm`.
 *
 * <p>O CONTRATO é `tierRange(min, max)` com TRÊS formas: `max` nulo → "N+",
 * `min == max` → "N", senão **"N–M" com EN DASH** (U+2013, não hífen). O teste
 * afirma as três.
 */
class EnosisSealsE2ETest implements LibraryInstallSupport {

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
    void theSealsPageHasTheThreeTierRangeForms(@TempDir Path dir) throws Exception {
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
                import enois.Seals
                import enois.Text
                import game.Sdl3Backend

                main() {
                    val b = Sdl3Backend("Énóis", 900, 900)
                    b.clearFrame(247, 244, 251)
                    val eb = Font("%s", 12)
                    val title = Font("%s", 36)
                    val body = Font("%s", 16)
                    val head = Font("%s", 18)
                    // as TRES formas de tierRange
                    println("t_open=" + SealsScreen.tierRange(10, -1))
                    println("t_one=" + SealsScreen.tierRange(5, 5))
                    println("t_range=" + SealsScreen.tierRange(3, 9))
                    val h = SealsScreen.paint(b, eb, title, body, head, 900, 16,
                        "CONTRIBUICAO", "Selos de contribuição",
                        "O Énóis reconhece quem ajuda a construir o projeto. Os selos vêm de contribuições verificadas.",
                        "Categorias e níveis",
                        listOf<String>("CODE", "ISSUES"),
                        listOf<String>("Pull requests integrados contam automaticamente.",
                                       "Só issues qualificadas contam."),
                        3, 9)
                    println("pagina_h=" + h)
                    println("x=" + (900 - 768) / 2)
                    // o eyebrow: varre a faixa e procura a tinta = o --primary
                    // (~103,70,245). A condicao e' "e' a TINTA", nao "nao e'
                    // branco": o fundo (247,244,251) tambem nao e' branco.
                    val ey0 = 16 + 56
                    var found = false
                    var hit = ""
                    var xx = 66
                    while (xx < 366 && !found) {
                        var yy = ey0
                        while (yy < ey0 + 20 && !found) {
                            val q = b.readPixel(xx, yy)
                            val r = q[0] & 255
                            val g = q[1] & 255
                            val bl = q[2] & 255
                            // --primary e' (103,70,245): canal AZUL alto e R
                            // BEM abaixo do azul. Nem o fundo nem o branco casam.
                            if (bl > 200 && r < 200 && bl > r + 60) {
                                found = true
                                hit = "" + r + "," + g + "," + bl
                            }
                            yy = yy + 1
                        }
                        xx = xx + 1
                    }
                    println("eyebrow=" + (if (found) hit else "nada"))
                    eb.close(); title.close(); body.close(); head.close()
                    b.close()
                }
                """.formatted(font.toAbsolutePath(), font.toAbsolutePath(), font.toAbsolutePath(),
                font.toAbsolutePath()), StandardCharsets.UTF_8);

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

        // as TRES formas: "N+", "N" e "N-M" com EN DASH
        assertTrue(out.contains("t_open=10+"), "o maximo aberto: " + out);
        assertTrue(out.contains("t_one=5"), "a faixa de um nivel: " + out);
        assertTrue(out.contains("t_range=3–9"), "o intervalo com EN DASH (U+2013): " + out);
        assertTrue(!out.contains("t_range=3-9"), "NAO pode ser o hifen ASCII: " + out);
        // max-w-3xl = 768 -> x = 66 numa janela de 900
        assertTrue(out.contains("x=66"), "a coluna: " + out);
        // o eyebrow foi pintado na FAMILIA do --primary
        var e = java.util.regex.Pattern.compile("eyebrow=(\\d+),(\\d+),(\\d+)").matcher(out);
        assertTrue(e.find(), "o eyebrow pintou: " + out);
        int er = Integer.parseInt(e.group(1));
        int eb = Integer.parseInt(e.group(3));
        assertTrue(eb > 200 && eb > er + 60, "o azul do --primary domina: " + e.group(0));
    }
}
