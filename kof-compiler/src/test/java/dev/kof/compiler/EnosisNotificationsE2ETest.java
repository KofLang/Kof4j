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
 * Énois → desktop (Fase 3): `notificacoes.index` — a lista de avisos.
 *
 * <p>Medido em `notificacoes.index.tsx` + o design system: a coluna
 * `mx-auto w-full max-w-2xl` = **672 px**, o item
 * `flex items-start gap-3 px-4 py-3.5` (14 px vertical), o ícone
 * `h-11 w-11 rounded-full` = **44 px**, o nome `font-extrabold`. Os textos são
 * os do site: os rótulos de dia **"Hoje"** / **"Ontem"**
 * (`notificationsPage.today`/`yesterday`).
 *
 * <p>O CONTRATO é o AGRUPAMENTO: um rótulo de dia aparece quando o grupo MUDA,
 * não a cada item — três itens em dois grupos dão DOIS rótulos. O teste prova
 * pela altura: 2 rótulos (13+8+8 cada) + 3 itens de 72 = 274.
 */
class EnosisNotificationsE2ETest implements LibraryInstallSupport {

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
    void theListGroupsTheNoticesByDayWithOneLabelPerGroup(@TempDir Path dir) throws Exception {
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
                import enois.Notifications
                import enois.Text
                import game.Sdl3Backend

                main() {
                    val b = Sdl3Backend("Énóis", 900, 500)
                    b.clearFrame(247, 244, 251)
                    val grp = Font("%s", 13)
                    val name = Font("%s", 15)
                    val body = Font("%s", 14)
                    // os rotulos de dia do site
                    println("d0=" + NotificationsScreen.dayLabel(0))
                    println("d1=" + NotificationsScreen.dayLabel(1))
                    // 3 itens, 2 grupos -> 2 rotulos
                    val h = NotificationsScreen.paint(b, grp, name, body, 900, 16,
                        listOf<Int>(0, 0, 1),
                        listOf<String>("Maria curtiu", "Joao comentou", "Ana seguiu"),
                        listOf<String>("seu post", "no seu post", "voce"))
                    println("h2grupos=" + h)
                    // 3 itens no MESMO grupo -> 1 rotulo (a prova do agrupamento)
                    val h1 = NotificationsScreen.paint(b, grp, name, body, 900, 400,
                        listOf<Int>(0, 0, 0),
                        listOf<String>("A", "B", "C"),
                        listOf<String>("x", "y", "z"))
                    println("h1grupo=" + h1)
                    println("x=" + (900 - 672) / 2)
                    // o 1o item: o rotulo "Hoje" ocupa 13+8+8 = 29, entao o item
                    // comeca em 16+29 = 45; o icone 44x44 em (114+16, 45+14)
                    val ic = b.readPixel(114 + 16 + 22, 45 + 14 + 22)
                    println("icone=" + (ic[0] & 255) + "," + (ic[1] & 255) + "," + (ic[2] & 255))
                    val cd = b.readPixel(114 + 600, 45 + 8)
                    println("card=" + (cd[0] & 255) + "," + (cd[1] & 255) + "," + (cd[2] & 255))
                    grp.close(); name.close(); body.close()
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

        // os rotulos de dia do site
        assertTrue(out.contains("d0=Hoje"), "Hoje: " + out);
        assertTrue(out.contains("d1=Ontem"), "Ontem: " + out);
        // A PROVA DO AGRUPAMENTO: 2 grupos -> 274, 1 grupo -> 245
        // (3 itens = 216, + 29 por rotulo)
        assertTrue(out.contains("h2grupos=274"), "2 rotulos + 3 itens: " + out);
        assertTrue(out.contains("h1grupo=245"), "1 rotulo + 3 itens: " + out);
        // max-w-2xl = 672 -> x = 114 numa janela de 900
        assertTrue(out.contains("x=114"), "a coluna: " + out);
        // o icone do aviso: --primary-soft
        assertTrue(out.contains("icone=238,232,255"), "o quadro do icone: " + out);
        assertTrue(out.contains("card=255,255,255"), "o --surface-card do item: " + out);
    }
}
