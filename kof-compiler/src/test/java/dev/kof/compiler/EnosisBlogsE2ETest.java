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
 * Énois → desktop (Fase 3): `blogs` — o diretório de blogs.
 *
 * <p>Medido em `blogs.tsx`: o card `surface-card block p-4 sm:p-5` = 20 px, o
 * eyebrow `text-xs font-semibold uppercase tracking-widest text-primary`, o
 * título `mt-2 text-base font-semibold`, a descrição `mt-1 text-sm`. Os textos
 * são os do site (`texts.ts` bloco `blog`): título "Blogs", a descrição
 * "Cantinhos de quem escreve por aqui…", e os estados vazios
 * `emptyDirectory` / `searchEmpty`.
 *
 * <p>COMO O TESTE MEDE O TEXTO: um glifo de 12 px tem bordas ANTI-ALIASED — o
 * primeiro pixel não-branco de um "M" é uma MISTURA de `--primary` com o card
 * (medido: `154,132,248`), não a cor pura. Por isso o eyebow é afirmado pelo
 * seu PAPEL (o canal AZUL domina e o R cai, i.e. a família do `--primary`), não
 * por um valor de pixel que nenhuma varredura fixa garantiria.
 */
class EnosisBlogsE2ETest implements LibraryInstallSupport {

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
    void theBlogsDirectoryPaintsItsHeaderAndCard(@TempDir Path dir) throws Exception {
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
                import enois.Blogs
                import enois.Text
                import game.Sdl3Backend

                main() {
                    val b = Sdl3Backend("Énóis", 900, 600)
                    b.clearFrame(247, 244, 251)
                    val title = Font("%s", 24)
                    val body = Font("%s", 14)
                    val eyebrow = Font("%s", 12)
                    val cardTitle = Font("%s", 16)
                    val hh = BlogsScreen.paintHeader(b, title, body, 16, 16, 868, "Blogs",
                        "Cantinhos de quem escreve por aqui: portfólio, diário, receitas, o que a pessoa quiser")
                    println("header_h=" + hh)
                    val ch = BlogsScreen.paintCard(b, eyebrow, cardTitle, body,
                        16, 16 + hh + 16, 868, "MARIA SILVA", "Um diário de construção",
                        "O que aprendi escrevendo este app.")
                    println("card_h=" + ch)
                    // o eyebrow: varre a faixa e devolve o PRIMEIRO pixel nao-branco
                    val ey0 = 16 + hh + 16 + 20
                    var found = false
                    var hit = ""
                    var xx = 16 + 20
                    while (xx < 16 + 200 && !found) {
                        var yy = ey0
                        while (yy < ey0 + 14 && !found) {
                            val q = b.readPixel(xx, yy)
                            if ((q[0] & 255) < 250 || (q[1] & 255) < 250 || (q[2] & 255) < 250) {
                                found = true
                                hit = (q[0] & 255) + "," + (q[1] & 255) + "," + (q[2] & 255)
                            }
                            yy = yy + 1
                        }
                        xx = xx + 1
                    }
                    println("eyebrow=" + (if (found) hit else "nada"))
                    val cd = b.readPixel(16 + 600, 16 + hh + 16 + 100)
                    println("card=" + (cd[0] & 255) + "," + (cd[1] & 255) + "," + (cd[2] & 255))
                    title.close(); body.close(); eyebrow.close(); cardTitle.close()
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

        // o cabecalho: o titulo (24) + a linha base + a descricao quebrada
        var h = java.util.regex.Pattern.compile("header_h=(\\d+)").matcher(out);
        assertTrue(h.find(), "o cabecalho mede: " + out);
        assertTrue(Integer.parseInt(h.group(1)) > 40, "o titulo e a descricao: " + out);
        // o card sm:p-5 = 120 px
        assertTrue(out.contains("card_h=120"), "a altura do card: " + out);
        assertTrue(out.contains("card=255,255,255"), "o --surface-card: " + out);
        // o eyebrow EXISTE e esta na FAMILIA do --primary (azul domina o R)
        var e = java.util.regex.Pattern.compile("eyebrow=(\\d+),(\\d+),(\\d+)").matcher(out);
        assertTrue(e.find(), "o eyebrow pintou: " + out);
        int er = Integer.parseInt(e.group(1));
        int eb = Integer.parseInt(e.group(3));
        assertTrue(eb > er + 40,
                "o eyebrow e' a familia do --primary (B domina R): " + e.group(0));
    }
}
