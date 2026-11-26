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
 * O glifo ITÁLICO do Énois: a textura inclinada do backend nativo.
 *
 * <p>O Manrope do site não tem face itálica; o navegador aplica o `oblique`
 * sintetizado (`<em className="italic">`, rich-text.tsx:246). O backend entrega
 * o mesmo por `drawTextureSheared` — cada linha `r` da textura sai `shear*r/h`
 * px à direita. A prova é de PIXEL e DERIVADA da linha, não escolhida à mão:
 * uma textura de 4x16 tinta de branco (fundo preto) desenhada em (0,0) com 8 de
 * inclinação põe a 1ª linha em x=0 e a última em x≈7,5 — e o controle sem
 * inclinação (`drawTexture`, shear 0) põe a mesma última linha em x=0. O `§1197` só afeta o
 * `fillRect`; `drawTexture` é exatamente o caminho que o teste de imagem já lê.
 */
class Sdl3ShearedTextureE2ETest implements LibraryInstallSupport {

    public String libraryName() { return "game"; }

    public List<String> libraryMarkers() { return List.of("Window.kf", "Draw.kf"); }

    private static boolean hostSdl3() {
        return new File("/usr/lib64/libSDL3.so.0").exists()
                || new File("/usr/lib/x86_64-linux-gnu/libSDL3.so.0").exists();
    }

    @Test
    void theShearedTextureLeansRowByRow(@TempDir Path dir) throws Exception {
        assumeTrue(hostSdl3(), "SDL3 host absent");
        assumeTrue(NativeToolchainAssumptions.hasTool("as", "ld"), "native toolchain absent");

        Path libs = dir.resolve("kof-install/lib/kof-libs");
        copyLibrary(libs);
        Path repo = findLibsRoot().getParent();
        Path img = repo.resolve("libs/image");
        try (var files = Files.walk(img)) {
            for (Path f : files.filter(Files::isRegularFile).toList()) {
                Path dst = libs.resolve("image").resolve(img.relativize(f));
                Files.createDirectories(dst.getParent());
                Files.copy(f, dst);
            }
        }

        Files.writeString(dir.resolve("Main.kf"), """
                import game.Sdl3Backend

                main() {
                    val b = Sdl3Backend("italico", 64, 64)
                    val tex = b.createTexture(4, 16)
                    // branco opaco nas 4 colunas (BGRA, o formato do backbuffer)
                    val px = buffer.alloc(4 * 16 * 4)
                    var i = 0
                    while (i < 4 * 16) {
                        buffer.poke8(px, i * 4, 255)
                        buffer.poke8(px, i * 4 + 1, 255)
                        buffer.poke8(px, i * 4 + 2, 255)
                        buffer.poke8(px, i * 4 + 3, 255)
                        i = i + 1
                    }
                    SDL_UpdateTexture(tex, 0L, px, 4 * 4)
                    b.clearFrame(0, 0, 0)
                    b.drawTextureSheared(tex, 0, 0, 4, 16, 8)
                    // linha r sai em x = 8*r/16 = r/2
                    val topo = b.readPixel(1, 0)
                    val meio = b.readPixel(5, 8)
                    val fim = b.readPixel(11, 15)
                    // a coluna de cima NAO foi inclinada no meio: x=0 e' vazio
                    val vazio = b.readPixel(0, 15)
                    // a CONTROLE: sem inclinacao a linha 15 fica em x=0..3
                    b.clearFrame(0, 0, 0)
                    b.drawTexture(tex, 0, 0, 4, 16)
                    val reto = b.readPixel(0, 15)
                    println("p0=" + (topo[0] & 255))
                    println("p8=" + (meio[0] & 255))
                    println("p15=" + (fim[0] & 255))
                    println("vazio=" + (vazio[0] & 255))
                    println("reto=" + (reto[0] & 255))
                    b.close()
                }
                """, StandardCharsets.UTF_8);

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
        var p = pb.start();
        String out = new String(p.getInputStream().readAllBytes()).strip();
        assertTrue(p.waitFor(180, TimeUnit.SECONDS), "o programa termina");
        assertEquals(0, p.exitValue(), "saida limpa: " + out);
        assertEquals("p0=255\np8=255\np15=255\nvazio=0\nreto=255", out,
                "a textura inclinada: cada linha r deslocada shear*r/h");
    }
}
