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
 * Énois → desktop (Fase 3): `salvos` — os posts guardados.
 *
 * <p>Medido em `salvos.tsx` + o `PageHeader` do design system: o cabeçalho é
 * `surface-card mb-4 p-4 sm:p-6` = 24 px com 16 px abaixo, o título
 * `text-2xl` = 24 px com o ícone `h-5 w-5 text-primary` = 20 px, e a descrição
 * é a CONTAGEM. O texto do site tem DOIS plurais que mudam JUNTOS:
 * `count(n)` = "N post(s) salvo(s)".
 */
class EnosisSavedE2ETest implements LibraryInstallSupport {

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
    void theSavedPagePluralisesBothWordsTogether(@TempDir Path dir) throws Exception {
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
                import enois.Saved
                import enois.Text
                import game.Sdl3Backend

                main() {
                    val b = Sdl3Backend("Énóis", 900, 500)
                    b.clearFrame(247, 244, 251)
                    val title = Font("%s", 24)
                    val body = Font("%s", 14)
                    // o DUPLO plural: os dois sufixos mudam JUNTOS
                    println("c0=" + SavedScreen.count(0))
                    println("c1=" + SavedScreen.count(1))
                    println("c7=" + SavedScreen.count(7))
                    println("vazio=" + SavedScreen.emptyText())
                    val h = SavedScreen.paint(b, title, body, 16, 16, 868, 0, SavedScreen.emptyText())
                    println("pagina_h=" + h)
                    // o PageHeader em (16, 60); o icone 20x20 em (16+24, 60+24+4)
                    val ic = b.readPixel(16 + 24 + 10, 60 + 24 + 4 + 10)
                    println("icone=" + (ic[0] & 255) + "," + (ic[1] & 255) + "," + (ic[2] & 255))
                    val cd = b.readPixel(16 + 600, 60 + 8)
                    println("card=" + (cd[0] & 255) + "," + (cd[1] & 255) + "," + (cd[2] & 255))
                    // o mb-4 abaixo do header: o header vai de 60 a 146, o gap
                    // vai de 146 a 162 -> amostra em 154
                    val gp = b.readPixel(16 + 600, 154)
                    println("gap=" + (gp[0] & 255) + "," + (gp[1] & 255) + "," + (gp[2] & 255))
                    title.close(); body.close()
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

        // os DOIS sufixos mudam juntos — nunca "1 posts salvo"
        assertTrue(out.contains("c0=0 posts salvos"), "zero: " + out);
        assertTrue(out.contains("c1=1 post salvo"), "um: " + out);
        assertTrue(out.contains("c7=7 posts salvos"), "sete: " + out);
        assertTrue(out.contains("vazio=Nenhum post salvo ainda."), "o vazio do site: " + out);
        // --primary no icone do PageHeader
        assertTrue(out.contains("icone=103,70,245"), "o h-5 w-5 text-primary: " + out);
        assertTrue(out.contains("card=255,255,255"), "o --surface-card: " + out);
        // o mb-4 abaixo do header mostra o --background
        assertTrue(out.contains("gap=247,244,251"), "o mb-4 do PageHeader: " + out);
    }
}
