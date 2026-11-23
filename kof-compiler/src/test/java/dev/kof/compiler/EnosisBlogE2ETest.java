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
 * Énois → desktop (Fase 3): `blog.$username` — UM blog.
 *
 * <p>Medido em `blog.$username.tsx`: a capa `h-64 w-full` = **256 px** dentro de
 * `rounded-2xl border border-border-soft bg-surface-card`, o cabeçalho
 * `border-b border-border-soft p-5 sm:p-6` = 24 px com o título `text-2xl` =
 * 24 px, e o não-encontrado `surface-card p-10 text-center` = 40 px.
 *
 * <p>As FUNÇÕES de texto do site são migradas como funções:
 * `postsCount(count)` → "N post"/"N posts" e `readingTime(min)` →
 * "N min de leitura estimada" — o plural é parte do contrato, não decoração.
 */
class EnosisBlogE2ETest implements LibraryInstallSupport {

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
    void theBlogPaintsTheImageCoverWithTheRadiusCut(@TempDir Path dir) throws Exception {
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
        Path cover = EnosisTestPng.gradient(dir, "cover.png", 240, 120, 180);

        Files.writeString(dir.resolve("Main.kf"), """
                import enois.Blog
                import enois.Text
                import game.Sdl3Backend

                main() {
                    val b = Sdl3Backend("Énóis", 900, 700)
                    b.clearFrame(247, 244, 251)
                    val title = Font("%s", 24)
                    val meta = Font("%s", 14)
                    val body = Font("%s", 16)
                    val href = Font("%s", 14)
                    // as FUNCOES de texto do site, caso a caso
                    println("c1=" + BlogScreen.postsCount(1))
                    println("c3=" + BlogScreen.postsCount(3))
                    println("rt=" + BlogScreen.readingTime(5))
                    val ch = BlogScreen.paintCover(b, 16, 16, 868, "%s")
                    println("capa_h=" + ch)
                    val hh = BlogScreen.paintHeader(b, title, meta, 16, 16 + ch, 868,
                        "Diario", 3, 5)
                    println("header_h=" + hh)
                    val nh = BlogScreen.paintNotFound(b, body, href, 16, 16 + ch + hh, 868,
                        "Blog não encontrado.", "Ver todos os blogs")
                    println("nao_h=" + nh)
                    // a capa e' IMAGEM: (400, 60) dentro dela
                    val cv = b.readPixel(16 + 400, 16 + 60)
                    println("capa=" + (cv[0] & 255) + "," + (cv[1] & 255) + "," + (cv[2] & 255))
                    // o canto cortado pelo raio 16 -> o --background
                    val k = b.readPixel(16, 16)
                    println("canto=" + (k[0] & 255) + "," + (k[1] & 255) + "," + (k[2] & 255))
                    title.close(); meta.close(); body.close(); href.close()
                    b.close()
                }
                """.formatted(font.toAbsolutePath(), font.toAbsolutePath(), font.toAbsolutePath(),
                font.toAbsolutePath(), cover.toAbsolutePath()), StandardCharsets.UTF_8);

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

        // o PLURAL e' contrato: 1 post / 3 posts (o site faz o mesmo)
        assertTrue(out.contains("c1=1 post"), "o singular: " + out);
        assertTrue(out.contains("c3=3 posts"), "o plural: " + out);
        assertTrue(out.contains("rt=5 min de leitura estimada"), "a leitura: " + out);
        // h-64 = 256
        assertTrue(out.contains("capa_h=256"), "a capa do h-64: " + out);
        // a capa e' a IMAGEM 240x120 esticada para 868x256; (400,60) cai na
        // fonte (400*240/868, 60*120/256) = (110.599, 28.125) -> o pixel
        // INTEIRO (110, 28), e o gradiente do helper da' R=(110*255)//240=116
        // e G=(28*255)//120=59. O valor sai da FORMULA: arredondar 110.599 a
        // mao dava 117 e o teste falhou num off-by-one MEU, nao no desenho.
        assertTrue(out.contains("capa=116,59,180"), "o pixel real da imagem: " + out);
        // o raio 16 corta o canto -> o --background
        assertTrue(out.contains("canto=247,244,251"), "o rounded-2xl: " + out);
    }
}
