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
 * O app de EXEMPLO do `kof.game` (`examples/game/Main.kf`) — as quatro faces
 * do backend SDL3 num programa so': a janela, o sprite que segue o mouse, o
 * teclado (ESC) e o mouse.
 *
 * <p>O exemplo aceita `--frames N` para a sessao terminar sozinha (o modo
 * deterministico; sem ele o loop roda ate o ESC, como um app de verdade).
 */
class GameExampleE2ETest implements LibraryInstallSupport {

    public String libraryName() { return "game"; }

    public List<String> libraryMarkers() { return List.of("Window.kf", "Draw.kf"); }

    private static String hostSdl3() {
        for (String p : new String[]{"/usr/lib64/libSDL3.so.0", "/usr/lib/x86_64-linux-gnu/libSDL3.so.0",
                "/usr/lib/libSDL3.so.0"}) {
            if (new File(p).exists()) return p;
        }
        return null;
    }

    @Test
    void theExampleRunsAllFourFacesAndStopsOnItsFrameLimit(@TempDir Path dir) throws Exception {
        assumeTrue(hostSdl3() != null, "SDL3 host absent — install libSDL3");
        assumeTrue(NativeToolchainAssumptions.hasTool("as", "ld"), "native toolchain absent");
        copyLibrary(dir.resolve("kof-install/lib/kof-libs"));
        // a raiz do repositorio (o cwd do modulo e kof-compiler; o `libs/`
        // esta um nivel acima — a mesma descoberta do LibraryInstallSupport)
        Path src = findLibsRoot().getParent().resolve("examples/game/Main.kf");
        Path main = dir.resolve("Main.kf");
        Files.copy(src, main);
        String previous = System.getProperty("kof.install.dir");
        System.setProperty("kof.install.dir", dir.resolve("kof-install").toString());
        CompilationResult r;
        try {
            r = new CompilerDriver().compile(main, dir.resolve("out"), Target.NATIVE);
        } finally {
            if (previous == null) System.clearProperty("kof.install.dir");
            else System.setProperty("kof.install.dir", previous);
        }
        assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());

        var pb = new ProcessBuilder(dir.resolve("out/Default/Main").toString(), "--frames", "3")
                .redirectErrorStream(true);
        pb.environment().put("SDL_VIDEODRIVER", "dummy");
        var p = pb.start();
        String out = new String(p.getInputStream().readAllBytes()).strip();
        assertTrue(p.waitFor(120, TimeUnit.SECONDS), "a sessao termina no limite de frames");
        assertEquals(0, p.exitValue(), "clean exit: " + out);
        assertTrue(out.contains("frames=3"), "o limite de frames encerra a sessao: " + out);
        assertTrue(out.contains("dt-clamp=250"), "o loop do Window entrega o dt clampado: " + out);
        assertTrue(out.contains("vsync=true"), "a decisao A2 (vsync) chega pela superficie: " + out);
    }
}
