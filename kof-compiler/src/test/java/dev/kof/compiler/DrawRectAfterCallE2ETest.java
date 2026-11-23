package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * known-bugs §1197 — o retângulo do `SDL_RenderFillRect` vem VAZIO quando o
 * chamador fez outros CALLS antes: a cor chega certa (gdb em `SDL_SetRenderDrawColor`:
 * ESI/EDX/ECX/R8D = 10/20/30/255), a referência em C pinta, os campos do
 * `SDL_Surface` leem o que foi pintado — e mesmo assim o retângulo não entra.
 *
 * <p>O teste isola a variável: o mesmo desenho, uma vez logo depois do
 * `clearFrame` (o caso que funciona) e uma vez depois de um `prints`, cujo
 * corpo só faz calls. Se o segundo não pintar, o emissor está perdendo algo no
 * rastro de temporários do corpo que chamou o extern.
 */
class DrawRectAfterCallE2ETest {

    private static boolean hostReady() {
        if (!(new File("/usr/lib64/libSDL3.so.0").exists()
                || new File("/usr/lib/x86_64-linux-gnu/libSDL3.so.0").exists())) {
            return false;
        }
        return NativeToolchainAssumptions.hasTool("as", "ld");
    }

    @Test
    void aFillRectAfterUnrelatedCallsStillPaints(@TempDir Path dir) throws Exception {
        assumeTrue(hostReady(), "SDL3 or native toolchain absent");
        Files.writeString(dir.resolve("Main.kf"), """
                import game.Sdl3Backend

                extern "libSDL3.so.0" SDL_SetRenderDrawColor(Long ren, Int r, Int g, Int b): Bool

                static Int probe(Int n) {
                    println("probe n=" + n)
                    return n
                }

                main() {
                    val b = Sdl3Backend("rect", 300, 300)
                    b.clearFrame(10, 20, 30)
                    SDL_SetRenderDrawColor(b.rendererHandle, 255, 0, 0)
                    // 1: logo depois do clear, sem call nenhum no meio
                    b.fillRect(20, 20, 120, 80)
                    val a = b.readPixel(21, 21)
                    println("after-clear=" + (a[0] & 255) + "," + (a[1] & 255) + "," + (a[2] & 255))
                    // 2: depois de um corpo que só faz calls (o probe imprime = chama)
                    val k = probe(7)
                    SDL_SetRenderDrawColor(b.rendererHandle, 0, 255, 0)
                    b.fillRect(160, 20, 120, 80)
                    val c = b.readPixel(161, 21)
                    println("after-call=" + (c[0] & 255) + "," + (c[1] & 255) + "," + (c[2] & 255))
                    println("k=" + k)
                    b.close()
                }
                """, StandardCharsets.UTF_8);

        // a fixture usa o backend do Kof (libs/game), instalado ao lado
        Path kofLibs = Path.of(System.getProperty("kof.libs", "../libs"))
                .toAbsolutePath().normalize();
        if (!Files.isDirectory(kofLibs.resolve("game"))) {
            kofLibs = Path.of("libs").toAbsolutePath().normalize();
        }
        Path install = dir.resolve("install/lib/kof-libs");
        for (String lib : new String[]{"game", "image"}) {
            Path src = kofLibs.resolve(lib);
            assumeTrue(Files.isDirectory(src), lib + " ausente em " + kofLibs);
            try (var files = Files.walk(src)) {
                for (Path f : files.filter(Files::isRegularFile).toList()) {
                    Path dst = install.resolve(lib).resolve(src.relativize(f));
                    Files.createDirectories(dst.getParent());
                    Files.copy(f, dst);
                }
            }
        }
        System.setProperty("kof.install.dir", dir.resolve("install").toString());
        var r = new CompilerDriver().compile(dir.resolve("Main.kf"), dir.resolve("out"), Target.NATIVE);
        assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
        var pb = new ProcessBuilder(dir.resolve("out/Default/Main").toString()).redirectErrorStream(true);
        pb.environment().put("SDL_VIDEODRIVER", "dummy");
        var p = pb.start();
        String out = new String(p.getInputStream().readAllBytes()).strip();
        assertTrue(p.waitFor(120, TimeUnit.SECONDS), "termina: " + out);
        assertEquals(0, p.exitValue(), "saida limpa: " + out);

        assertTrue(out.contains("after-clear=255,0,0"), "o fill logo apos o clear pinta: " + out);
        assertTrue(out.contains("after-call=0,255,0"), "o fill APOS um call tambem pinta: " + out);
    }
}
