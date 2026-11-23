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
 * Énois → desktop (Fase 3): `grupos.$slug` — a página de UM grupo.
 *
 * <p>Medido em `grupos.$slug.tsx`: o container `mx-auto w-full max-w-3xl
 * space-y-6` = **768 px**, o banner `h-40 w-full object-cover` = **160 px**, o
 * logo `h-12 w-12 rounded-xl` = **48 px — quadrado arredondado, NÃO círculo**
 * (o mesmo raciocínio do card de vaga), o título `text-xl` = 20 px e a pílula
 * `rounded-full bg-primary-soft text-primary`.
 *
 * <p>O CONTRATO desta tela é a VARIAÇÃO de texto: `members(1)` = "1 membro" e
 * `members(5)` = "5 membros"; `joinModeLabel` distingue aberta/aprovação;
 * `actionLabel` distingue entrar/pendente/participa. O teste afirma as TRÊS
 * variações de cada, não uma.
 */
class EnosisGroupE2ETest implements LibraryInstallSupport {

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
    void theGroupVariesItsTextsByModeAndParticipation(@TempDir Path dir) throws Exception {
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
        Path banner = EnosisTestPng.gradient(dir, "banner.png", 240, 120, 180);

        Files.writeString(dir.resolve("Main.kf"), """
                import enois.Groups
                import enois.Text
                import game.Sdl3Backend

                main() {
                    val b = Sdl3Backend("Énóis", 900, 500)
                    b.clearFrame(247, 244, 251)
                    val title = Font("%s", 20)
                    val body = Font("%s", 14)
                    val chip = Font("%s", 12)
                    // o PLURAL dos membros (contrato do site)
                    println("m1=" + GroupScreen.members(1))
                    println("m5=" + GroupScreen.members(5))
                    // o MODO de entrada
                    println("modo0=" + GroupScreen.joinModeLabel(0))
                    println("modo1=" + GroupScreen.joinModeLabel(1))
                    // o ESTADO da acao
                    println("a0=" + GroupScreen.actionLabel(0))
                    println("a1=" + GroupScreen.actionLabel(1))
                    println("a2=" + GroupScreen.actionLabel(2))
                    val h = GroupScreen.paint(b, title, body, chip, 900, 16,
                        "Turma do Java", 5, 1, 0, "%s")
                    println("grupo_h=" + h)
                    println("x=" + (900 - 768) / 2)
                    // o banner e' IMAGEM: (300, 60) dentro dele
                    val bn = b.readPixel(66 + 300, 16 + 60)
                    println("banner=" + (bn[0] & 255) + "," + (bn[1] & 255) + "," + (bn[2] & 255))
                    // o logo em (66+20, 16+160+20) mede 48: o centro e' +24,+24
                    val lg = b.readPixel(66 + 20 + 24, 16 + 160 + 20 + 24)
                    println("logo=" + (lg[0] & 255) + "," + (lg[1] & 255) + "," + (lg[2] & 255))
                    title.close(); body.close(); chip.close()
                    b.close()
                }
                """.formatted(font.toAbsolutePath(), font.toAbsolutePath(), font.toAbsolutePath(),
                banner.toAbsolutePath()), StandardCharsets.UTF_8);

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

        // o PLURAL: 1 membro / 5 membros
        assertTrue(out.contains("m1=1 membro"), "o singular: " + out);
        assertTrue(out.contains("m5=5 membros"), "o plural: " + out);
        // os DOIS modos de entrada sao textos distintos
        assertTrue(out.contains("modo0=Entrada aberta"), "o modo aberto: " + out);
        assertTrue(out.contains("modo1=Entrada por aprovação"), "o modo aprovacao: " + out);
        // os TRES estados de acao
        assertTrue(out.contains("a0=Entrar no grupo"), "entrar: " + out);
        assertTrue(out.contains("a1=Pedido pendente"), "pendente: " + out);
        assertTrue(out.contains("a2=Você participa"), "participa: " + out);
        // a geometria: max-w-3xl = 768 -> x = 66 numa janela de 900
        assertTrue(out.contains("x=66"), "a coluna do max-w-3xl: " + out);
        // h-40 (160) + o corpo
        assertTrue(out.contains("grupo_h=338"), "banner 160 + o corpo: " + out);
        // o banner e' um pixel da IMAGEM (nao cor solida)
        var bn = java.util.regex.Pattern.compile("banner=(\\d+),(\\d+),(\\d+)").matcher(out);
        assertTrue(bn.find(), "o banner pintou: " + out);
        assertNotEquals("238", bn.group(1), "o banner NAO e' o --primary-soft de fallback");
        // o logo e' o --primary-soft
        assertTrue(out.contains("logo=238,232,255"), "o logo: " + out);
    }
}
