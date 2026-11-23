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
 * Énois → desktop (Fase 3): a seção de MEMBROS do aside da comunidade.
 *
 * <p>Medido em `comunidades.$communityId.tsx:898-906`: o bloco é
 * `surface-card p-5`, o título `h3 mb-3 text-sm font-semibold` com
 * `membersTitle(count)` = **"Membros (N)"** (`texts.ts:2334` — uma FUNÇÃO, não
 * um literal), e cada linha tem o avatar `h-9 w-9` = 36 px, o nome
 * `text-sm font-medium` e o badge `admin` = "ADMIN" (`texts.ts:2335`).
 */
class EnosisCommunityMembersE2ETest implements LibraryInstallSupport {

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
    void theMembersSectionPaintsTheCountAndTheCircularAvatars(@TempDir Path dir) throws Exception {
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
                import enois.CommunityFeed
                import enois.Text
                import game.Sdl3Backend

                main() {
                    val b = Sdl3Backend("Énóis", 400, 400)
                    b.clearFrame(247, 244, 251)
                    val head = Font("%s", 14)
                    val name = Font("%s", 14)
                    val badge = Font("%s", 12)
                    val h = CommunityMembers.paint(b, head, name, badge, 16, 16, 280,
                        listOf<String>("Maria Silva", "Joao Souza"),
                        listOf<String>("ADMIN", ""))
                    println("membros_h=" + h)
                    // a superficie do bloco: --surface-card
                    val c = b.readPixel(16 + 250, 16 + 10)
                    println("card=" + (c[0] & 255) + "," + (c[1] & 255) + "," + (c[2] & 255))
                    // o avatar CIRCULAR da 1a linha. A linha comeca em
                    // y = 16+20(pad)+14(titulo)+12(gap) = 62; o avatar 36x36
                    // fica em (36, 62) e o seu centro (54, 80) e' o GLIFO das
                    // iniciais — amostra 8 px a esquerda dele, ainda dentro
                    // do circulo (raio 18) e fora do texto
                    val av = b.readPixel(36 + 18 - 8, 62 + 18)
                    println("avatar=" + (av[0] & 255) + "," + (av[1] & 255) + "," + (av[2] & 255))
                    // e sem membros: a contagem e' 0
                    val h0 = CommunityMembers.paint(b, head, name, badge, 16, 200, 280,
                        listOf<String>(), listOf<String>())
                    println("vazia_h=" + h0)
                    head.close(); name.close(); badge.close()
                    b.close()
                }
                """.formatted(font.toAbsolutePath(), font.toAbsolutePath(), font.toAbsolutePath()),
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

        // p-5 (20) x2 + o titulo (14) + o gap (12) + 2 x (36 + 12) = 162
        assertTrue(out.contains("membros_h=162"), "a conta do p-5 + as linhas: " + out);
        // sem membros: 40 + 14 + 12 = 66
        assertTrue(out.contains("vazia_h=66"), "a secao vazia: " + out);
        assertTrue(out.contains("card=255,255,255"), "o --surface-card do bloco: " + out);
        assertTrue(out.contains("avatar=196,181,253"), "o circulo violeta do avatar: " + out);
    }
}
