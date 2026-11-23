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
 * Énois → desktop (Fase 3): `vagas.$jobId` — o detalhe da vaga.
 *
 * <p>Medido em `vagas.$jobId.tsx`: o container `mx-auto w-full max-w-2xl
 * space-y-4` = **672 px** centrado, com 16 px entre os blocos; o card
 * `surface-card p-4 sm:p-6` = 24 px; o avatar `h-12 w-12 rounded-xl
 * bg-surface-2`; o título `text-xl font-semibold tracking-tight` = 20 px.
 * Os textos são os do site: "Voltar para vagas", "Sobre a vaga"
 * (`texts.ts`, bloco `jobs`).
 */
class EnosisJobDetailE2ETest implements LibraryInstallSupport {

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
    void theJobDetailPaintsTheCentredColumnAndBothBlocks(@TempDir Path dir) throws Exception {
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
                import enois.Jobs
                import enois.Text
                import game.Sdl3Backend

                main() {
                    val b = Sdl3Backend("Énóis", 900, 500)
                    b.clearFrame(247, 244, 251)
                    val back = Font("%s", 14)
                    val title = Font("%s", 20)
                    val body = Font("%s", 14)
                    val h = JobDetail.paint(b, back, title, body, 900, 16,
                        "Voltar para vagas", "Desenvolvedor Java", "Énois", "Sobre a vaga",
                        listOf<String>("Trabalhe na rede sem anuncio.", "Remoto, CLT."))
                    println("detail_h=" + h)
                    // max-w-2xl = 672 centrado numa janela de 900
                    println("x=" + (900 - 672) / 2)
                    // o avatar em (114+24, 16+20+16) mede 48: o CENTRO e' +24,+24
                    val av = b.readPixel(114 + 24 + 24, 16 + 20 + 16 + 24)
                    println("avatar=" + (av[0] & 255) + "," + (av[1] & 255) + "," + (av[2] & 255))
                    // o card ao lado do avatar
                    val cd = b.readPixel(114 + 500, 16 + 20 + 16 + 8)
                    println("card=" + (cd[0] & 255) + "," + (cd[1] & 255) + "," + (cd[2] & 255))
                    // o intervalo space-y-4 entre o cabecalho e o "Sobre"
                    val gap = b.readPixel(114 + 300, 16 + 20 + 16 + 96 + 4)
                    println("gap=" + (gap[0] & 255) + "," + (gap[1] & 255) + "," + (gap[2] & 255))
                    back.close(); title.close(); body.close()
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

        assertTrue(out.contains("x=114"), "o max-w-2xl centrado: " + out);
        // --surface-1 (#f9f8ff) no avatar da vaga
        assertTrue(out.contains("avatar=249,248,255"), "o --surface-1 do avatar: " + out);
        assertTrue(out.contains("card=255,255,255"), "o --surface-card: " + out);
        // o intervalo entre os blocos mostra o --background de fora
        assertTrue(out.contains("gap=247,244,251"), "o space-y-4 entre os blocos: " + out);
        // o link de voltar + os dois cards: 20 + 16 + 96 + 16 + (24x2 + 16 + 16 + 2x24)
        var h = java.util.regex.Pattern.compile("detail_h=(\\d+)").matcher(out);
        assertTrue(h.find(), "a altura: " + out);
        assertTrue(Integer.parseInt(h.group(1)) > 200, "o detalhe ocupa a coluna: " + out);
    }
}
