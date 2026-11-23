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
 * Énois → desktop: o AVATAR (`UserAvatar` do design system).
 *
 * <p>Medido em `src/domain/avatar/user-avatar.tsx`: o `getInitials` (linha 11)
 * pega a primeira letra da primeira e da última palavra em maiúsculas (uma
 * palavra só -> aquela letra); o fallback é `bg-violet-300` (#c4b5fd) /
 * `text-violet-900` (#4c1d95) num `rounded-full` (linha 63).
 *
 * <p>O CÍRCULO é pintado pixel a pixel pela lei do raio — os cantos mostram o
 * fundo. A máscara de uma FOTO é o gap §1195.
 */
class EnosisAvatarE2ETest implements LibraryInstallSupport {

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
    void theAvatarDrawsTheSiteInitialsInsideARealCircle(@TempDir Path dir) throws Exception {
        assumeTrue(hostReady(), "SDL3 or FreeType absent");
        assumeTrue(NativeToolchainAssumptions.hasTool("as", "ld"), "native toolchain absent");
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
                import enois.Avatar
                import enois.Text
                import game.Sdl3Backend

                main() {
                    // o getInitials do site (user-avatar.tsx:11)
                    println("i1=" + Avatar.initialsOf("Maria Silva"))
                    println("i2=" + Avatar.initialsOf("pedro"))
                    println("i3=" + Avatar.initialsOf("ana paula lima"))
                    println("i4=" + Avatar.initialsOf("  Renata   Costa  "))

                    val b = Sdl3Backend("av", 200, 200)
                    val f = Font("%s", 14)
                    // sem foto: o circulo violeta com as iniciais
                    Avatar.paint(b, f, 20, 20, 100, "Maria Silva", "%s")
                    val cent = b.readPixel(70, 70)
                    println("centro=" + (cent[0] & 255) + "," + (cent[1] & 255) + "," + (cent[2] & 255))
                    val canto = b.readPixel(22, 22)
                    println("canto=" + (canto[0] & 255) + "," + (canto[1] & 255) + "," + (canto[2] & 255))
                    val borda = b.readPixel(70, 22)
                    println("topo=" + (borda[0] & 255) + "," + (borda[1] & 255) + "," + (borda[2] & 255))
                    f.close()
                    b.close()
                }
                """.formatted(font.toAbsolutePath(), dir.resolve("naoexiste.png").toAbsolutePath()),
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

        // o algoritmo do site, caso a caso
        assertTrue(out.contains("i1=MS"), "Maria Silva -> MS: " + out);
        assertTrue(out.contains("i2=P"), "uma palavra -> P: " + out);
        assertTrue(out.contains("i3=AL"), "ana paula lima -> AL: " + out);
        assertTrue(out.contains("i4=RC"), "espacos extras -> RC: " + out);

        // violet-300 (#c4b5fd) no centro
        assertTrue(out.contains("centro=196,181,253"), "o bg-violet-300 do site: " + out);
        // os CANTOS estao FORA do circulo -> o fundo (preto da janela limpa)
        assertTrue(out.contains("canto=0,0,0"), "o canto fora do circulo: " + out);
        // o TOPO do circulo (y=22 no centro x=70) ainda esta DENTRO (o circulo
        // toca a caixa no meio dos lados)
        assertTrue(out.contains("topo=196,181,253"), "o topo do circulo e' pintado: " + out);
    }
}
