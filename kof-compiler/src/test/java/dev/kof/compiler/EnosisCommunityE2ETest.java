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
 * Énois → desktop (Fase 3): `comunidades.$communityId`.
 *
 * <p>As medidas vêm do fonte, MEDIDAS: a capa `h-48` = 192 px
 * (`comunidades.$communityId.tsx:418`), o avatar `h-16 w-16` = 64 px com
 * `ring-4` (linha 430), o título `text-2xl font-semibold` = 24 px (linha 439).
 * A capa é uma IMAGEM REAL quando carrega, e o gradiente `--primary-soft`
 * quando não há capa.
 */
class EnosisCommunityE2ETest implements LibraryInstallSupport {

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
    void theCommunityPaintsARealCoverAndFallsBackWithoutOne(@TempDir Path dir) throws Exception {
        assumeTrue(hostReady(), "SDL3 or FreeType absent");
        assumeTrue(NativeToolchainAssumptions.hasTool("as", "ld"), "native toolchain absent");
        Path cover = EnosisTestPng.gradient(dir, "cover.png", 240, 120, 180);
        Path repo = findLibsRoot().getParent();
        Path font = repo.resolve("assets/fonts/Manrope.ttf");
        Path shim = repo.resolve("libs/enois/libkof_text.so");
        assumeTrue(Files.exists(font), "assets/fonts/Manrope.ttf absent");
        assumeTrue(Files.exists(shim), "libkof_text.so absent — run scripts/gen-enois-text.sh");

        Path libs = dir.resolve("kof-install/lib/kof-libs");
        copyLibrary(libs);
        Path img = repo.resolve("libs/image");
        try (var files = Files.walk(img)) {
            for (Path f : files.filter(Files::isRegularFile).toList()) {
                Path dst = libs.resolve("image").resolve(img.relativize(f));
                Files.createDirectories(dst.getParent());
                Files.copy(f, dst);
            }
        }
        Path enois = repo.resolve("libs/enois");
        try (var files = Files.walk(enois)) {
            for (Path f : files.filter(Files::isRegularFile).toList()) {
                Path dst = libs.resolve("enois").resolve(enois.relativize(f));
                Files.createDirectories(dst.getParent());
                Files.copy(f, dst);
            }
        }
        Path text = libs.resolve("enois/Text.kf");
        Files.writeString(text, Files.readString(text, StandardCharsets.UTF_8)
                .replace("\"@KOF_TEXT_SHIM@\"", "\"" + shim + "\"")
                .replace("\"kof_text_shim\"", "\"" + shim + "\""), StandardCharsets.UTF_8);

        Files.writeString(dir.resolve("Main.kf"), """
                import enois.Community
                import enois.Shell
                import enois.Text
                import game.Sdl3Backend

                main() {
                    val W = 1280
                    val b = Sdl3Backend("Énóis", W, 800)
                    val top = Shell.paint(b, W, 800)
                    val x = 16
                    val cw = Shell.contentWidth(W)
                    val back = Font("%s", 14)
                    val title = Font("%s", 24)
                    val h = CommunityHeader.paint(b, back, title, x, top, cw,
                        "Voltar", "Programacao", "%s")
                    println("header_h=" + h)
                    // dentro da capa: o card comeca em top + (14 + 12); a
                    // amostra (400, 60) DENTRO dele cai na fonte (106.2, 37.5)
                    // da imagem 240x120 esticada para 904x192
                    val cover = b.readPixel(x + 400, top + 26 + 60)
                    println("capa=" + (cover[0] & 255) + "," + (cover[1] & 255) + "," + (cover[2] & 255))
                    // sem capa: o gradiente --primary-soft
                    CommunityHeader.paint(b, back, title, x, top + h + 20, cw,
                        "Voltar", "Sem capa", "%s")
                    val grad = b.readPixel(x + 400, top + h + 20 + 60)
                    println("sem_capa=" + (grad[0] & 255) + "," + (grad[1] & 255) + "," + (grad[2] & 255))
                    var tinta = 0
                    var yy = top
                    while (yy < top + h) {
                        var xx = x
                        while (xx < x + cw) {
                            val p = b.readPixel(xx, yy)
                            if ((p[0] & 255) < 100) { tinta = tinta + 1 }
                            xx = xx + 8
                        }
                        yy = yy + 8
                    }
                    println("tinta=" + tinta)
                    back.close()
                    title.close()
                    b.close()
                }
                """.formatted(font.toAbsolutePath(), font.toAbsolutePath(), cover.toAbsolutePath(),
                dir.resolve("nada.png").toAbsolutePath()), StandardCharsets.UTF_8);

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
        // h-48 (192) + o corpo com o avatar de 64
        assertTrue(out.contains("header_h=338"), "h-48 + avatar 64 + titulo: " + out);
        // A capa (240x120) e' esticada para 904x192; a amostra (400, 60) do
        // CARD cai na fonte (106.2, 37.5) -> (112, 79, 180). O numero sai da
        // ESCALA, nao de um literal; a prova de que e' a IMAGEM (e nao cor
        // solida) e' o R e o G variando juntos ao longo da capa.
        assertTrue(out.contains("capa=112,"), "o canal X da imagem (nao cor solida): " + out);
        assertTrue(out.contains(",180"), "o canal B constante do gradiente: " + out);
        // sem capa: --primary-soft #eee8ff
        assertTrue(out.contains("sem_capa=238,232,255"), "o gradiente do site: " + out);
        var m = java.util.regex.Pattern.compile("tinta=(\\d+)").matcher(out);
        assertTrue(m.find(), "o teste mede tinta: " + out);
        assertTrue(Integer.parseInt(m.group(1)) > 20, "o titulo pintou: " + out);
    }
}
