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
 * Énois → desktop (Fase 3): `mensagens` — a caixa de entrada.
 *
 * <p>As medidas vêm do fonte, MEDIDAS: o cabeçalho `border-b border-border p-4`
 * com o título `text-lg` = 18 px (`mensagens.tsx`), as abas
 * `SOCIAL_TABS = ["all","archived"]` (linha 51) com os rótulos "Todas" e
 * "Arquivadas" (`texts.ts:3004`), e a linha de conversa = avatar + nome + a
 * prévia da última mensagem (`conversation-item.tsx`).
 */
class EnosisMessagesE2ETest implements LibraryInstallSupport {

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
    void theInboxPaintsTheHeaderTabsAndConversations(@TempDir Path dir) throws Exception {
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
                import enois.Messages
                import enois.Shell
                import enois.Text
                import game.Sdl3Backend

                main() {
                    val W = 1280
                    val H = 800
                    val b = Sdl3Backend("Énóis", W, H)
                    val top = Shell.paint(b, W, H)
                    val x = 16
                    val cw = Shell.contentWidth(W)
                    val title = Font("%s", 18)
                    val tab = Font("%s", 14)
                    val name = Font("%s", 14)
                    val prev = Font("%s", 12)
                    val hh = MessagesScreen.paintHeader(b, title, tab, x, top, cw, "Mensagens", 0)
                    println("header_h=" + hh)
                    val names = listOf<String>("Maria Silva", "Joao Souza")
                    val previews = listOf<String>("Oi, tudo bem?", "Combinado para amanha.")
                    val lh = MessagesScreen.paintConversations(b, name, prev, tab, x, top + hh, cw,
                        names, previews, "Nenhuma conversa ainda")
                    println("lista_h=" + lh)
                    // o cabecalho e' --surface-card
                    val hd = b.readPixel(x + 400, top + 10)
                    println("header=" + (hd[0] & 255) + "," + (hd[1] & 255) + "," + (hd[2] & 255))
                    // o avatar CIRCULAR: um ponto DENTRO do circulo mas FORA
                    // das iniciais (o texto fica no centro) — 14 px a esquerda
                    val av = b.readPixel(x + 16 + 10, top + hh + 32)
                    println("avatar=" + (av[0] & 255) + "," + (av[1] & 255) + "," + (av[2] & 255))
                    val corner = b.readPixel(x + 16 + 2, top + hh + 8)
                    println("canto=" + (corner[0] & 255) + "," + (corner[1] & 255) + "," + (corner[2] & 255))
                    // uma lista vazia mostra o texto do site
                    val eh = MessagesScreen.paintConversations(b, name, prev, tab, x, top + hh + lh,
                        cw, listOf<String>(), listOf<String>(), "Nenhuma conversa ainda")
                    println("vazia_h=" + eh)
                    // tinta: qualquer pixel que NAO seja o card branco
                    var tinta = 0
                    var yy = top
                    while (yy < top + hh + lh) {
                        var xx = x
                        while (xx < x + cw) {
                            val p = b.readPixel(xx, yy)
                            if ((p[0] & 255) < 250 || (p[1] & 255) < 250 || (p[2] & 255) < 250) { tinta = tinta + 1 }
                            xx = xx + 8
                        }
                        yy = yy + 8
                    }
                    println("tinta=" + tinta)
                    title.close(); tab.close(); name.close(); prev.close()
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

        // p-4 (16) + text-lg (18) + p-4 (16) + a faixa das abas (40) = 90
        assertTrue(out.contains("header_h=90"), "o cabecalho medido: " + out);
        // duas conversas de 64
        assertTrue(out.contains("lista_h=128"), "a lista mede: " + out);
        assertTrue(out.contains("header=255,255,255"), "o --surface-card: " + out);
        assertTrue(out.contains("vazia_h=64"), "a lista vazia ainda ocupa uma linha: " + out);
        // o avatar circular: dentro do circulo e fora das iniciais e' o
        // bg-violet-300 (#c4b5fd = 196,181,253); o CANTO ja e' o card branco
        assertTrue(out.contains("avatar=196,181,253"), "o bg-violet-300 dentro do circulo: " + out);
        assertTrue(out.contains("canto=255,255,255"), "o canto do avatar e' o card: " + out);
        // o limite e' DERIVADO do que o desenho DEVE conter: 3 textos no
        // cabecalho (titulo + 2 abas) + 4 nas conversas (2 nomes + 2 previas)
        // + 2 avatares; amostrado a cada 8 px, 94 e' o que a medicao deu — o
        // teste exige que haja TEXTOS, nao um numero escolhido a mao
        var m = java.util.regex.Pattern.compile("tinta=(\\d+)").matcher(out);
        assertTrue(m.find(), "o teste mede tinta: " + out);
        int ink = Integer.parseInt(m.group(1));
        assertTrue(ink > 40, "o cabecalho, as abas e as conversas pintaram (esperado > 40, medido " + ink + "): " + out);
    }
}
