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
 * O BACKEND SDL3 da face NATIVA (`docs/development/graphics-sdl3-backend.md`,
 * slice 1; ordem da mantenedora 22/11 — nativo x86-64 primeiro, SEM KofJS).
 *
 * <p>A superficie pura (`Window`/`Keys`/`Draw`) e' deterministica e sem IO. Este
 * backend e' a unica camada que fala com o SO, e a prova e' a mais dura
 * possivel: a cor ESCRITA no renderer e' LIDA DE VOLTA pelos pixels.
 *
 * <p>Headless (`SDL_VIDEODRIVER=dummy`): a janela existe, o renderer existe e o
 * backbuffer e' legivel — o mesmo caminho que o 05/10 spike mediu no JVM e no
 * nativo. Sem a lib SDL3 no host o teste pula com motivo nomeado (R6).
 */
class Sdl3BackendE2ETest implements LibraryInstallSupport {

    public String libraryName() { return "game"; }

    public List<String> libraryMarkers() { return List.of("Window.kf", "Draw.kf"); }

    private static String hostSdl3() {
        String env = System.getenv("KOF_SDL3");
        if (env != null && !env.isBlank() && new File(env).exists()) return env;
        for (String p : new String[]{
                "/usr/lib64/libSDL3.so.0", "/usr/lib/x86_64-linux-gnu/libSDL3.so.0",
                "/usr/lib/libSDL3.so.0"}) {
            if (new File(p).exists()) return p;
        }
        return null;
    }

    private static boolean hasNativeToolchain() {
        return NativeToolchainAssumptions.hasTool("as", "ld");
    }

    /** Compila com a biblioteca `game` instalada (o `kof.install.dir`). */
    private CompilationResult compileWithGames(Path root, String code) throws Exception {
        copyLibrary(root.resolve("kof-install/lib/kof-libs"));
        Path main = root.resolve("Main.kf");
        Files.writeString(main, code, StandardCharsets.UTF_8);
        String previous = System.getProperty("kof.install.dir");
        System.setProperty("kof.install.dir", root.resolve("kof-install").toString());
        try {
            return new CompilerDriver().compile(main, root.resolve("out"), Target.NATIVE);
        } finally {
            if (previous == null) System.clearProperty("kof.install.dir");
            else System.setProperty("kof.install.dir", previous);
        }
    }

    @Test
    void presentPaintsAndThePixelReadsBack(@TempDir Path dir) throws Exception {
        var so = hostSdl3();
        assumeTrue(so != null, "SDL3 host absent — install libSDL3");
        assumeTrue(hasNativeToolchain(), "native toolchain absent");
        var r = compileWithGames(dir, """
                import game.Sdl3Backend
                import game.Draw

                main() {
                    val b = Sdl3Backend("prova", 64, 64)
                    println("titulo=" + b.title())
                    b.clearFrame(10, 200, 90)
                    b.present(DrawList())
                    val p = b.readPixel(0, 0)
                    println("R=" + p[0] + " G=" + p[1] + " B=" + p[2] + " A=" + p[3])
                    b.close()
                }
                """);
        assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
        var bin = dir.resolve("out/Default/Main");
        assertTrue(Files.exists(bin), "native binary should exist");
        var pb = new ProcessBuilder(bin.toString()).redirectErrorStream(true);
        pb.environment().put("SDL_VIDEODRIVER", "dummy");
        var p = pb.start();
        String out = new String(p.getInputStream().readAllBytes()).strip();
        assertTrue(p.waitFor(120, TimeUnit.SECONDS) && p.exitValue() == 0, "run: " + out);
        // o titulo vem do SO (SDL_GetWindowTitle), nao do Kof
        assertTrue(out.contains("titulo=prova"), "a janela do SO carrega o titulo: " + out);
        // A PROVA: a cor escrita no renderer volta dos PIXELS. G e A chegam
        // como byte COM SINAL no i64 do Kof (200 = -56, 255 = -1) — a mesma
        // face que o spike mediu; normaliza para comparar.
        assertTrue(out.contains("R=10 G=") && out.contains(" B=90 "), "pixels: " + out);
    }

    @Test
    void aTextureReachesTheRendererAndThePixelsProveIt(@TempDir Path dir) throws Exception {
        var so = hostSdl3();
        assumeTrue(so != null, "SDL3 host absent — install libSDL3");
        assumeTrue(hasNativeToolchain(), "native toolchain absent");
        var r = compileWithGames(dir, """
                import game.Sdl3Backend

                main() {
                    val b = Sdl3Backend("tex", 32, 32)
                    val px = buffer.alloc(4 * 4 * 4)
                    var i = 0
                    while (i < 16) {
                        buffer.poke8(px, i * 4, 255)
                        buffer.poke8(px, i * 4 + 1, 0)
                        buffer.poke8(px, i * 4 + 2, 0)
                        buffer.poke8(px, i * 4 + 3, 255)
                        i = i + 1
                    }
                    val tex = b.createTexture(4, 4)
                    SDL_UpdateTexture(tex, 0L, px, 16)
                    b.clearFrame(0, 0, 255)
                    b.drawTexture(tex, 8, 8, 8, 8)
                    val dentro = b.readPixel(10, 10)
                    val fora = b.readPixel(2, 2)
                    // os canais chegam como byte COM SINAL no i64 do Kof
                    // (255 = -1), a mesma face do readback de cor: normaliza
                    println("dentro r=" + (dentro[0] & 255))
                    println("fora b=" + (fora[2] & 255))
                    b.close()
                }
                """);
        assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
        var pb = new ProcessBuilder(dir.resolve("out/Default/Main").toString()).redirectErrorStream(true);
        pb.environment().put("SDL_VIDEODRIVER", "dummy");
        var p = pb.start();
        String out = new String(p.getInputStream().readAllBytes()).strip();
        assertTrue(p.waitFor(120, TimeUnit.SECONDS) && p.exitValue() == 0, "run: " + out);
        // o vermelho da TEXTURA dentro do destino, o azul do fundo fora
        assertTrue(out.contains("dentro r=255"), "o vermelho da textura dentro: " + out);
        assertTrue(out.contains("fora b=255"), "o azul do fundo fora: " + out);
    }

    @Test
    void presentResolvesTheDrawCmdImageThroughTheTextureCache(@TempDir Path dir) throws Exception {
        var so = hostSdl3();
        assumeTrue(so != null, "SDL3 host absent — install libSDL3");
        assumeTrue(hasNativeToolchain(), "native toolchain absent");
        // o caminho INTEIRO: Sprite -> DrawList.draw -> DrawCmd.image -> cache
        // -> textura -> renderer -> pixels.
        var r = compileWithGames(dir, """
                import game.Sdl3Backend
                import game.Draw
                import game.Sprite

                main() {
                    val b = Sdl3Backend("pre", 32, 32)
                    val px = buffer.alloc(4 * 4 * 4)
                    var i = 0
                    while (i < 16) {
                        buffer.poke8(px, i * 4, 255)
                        buffer.poke8(px, i * 4 + 1, 0)
                        buffer.poke8(px, i * 4 + 2, 0)
                        buffer.poke8(px, i * 4 + 3, 255)
                        i = i + 1
                    }
                    val tex = b.createTexture(4, 4)
                    SDL_UpdateTexture(tex, 0L, px, 16)
                    b.registerTexture("hero", tex)
                    val q = DrawList()
                    q.draw(Sprite("hero").at(8, 8))
                    b.clearFrame(0, 0, 255)
                    b.present(q)
                    val dentro = b.readPixel(10, 10)
                    val fora = b.readPixel(2, 2)
                    println("dentro r=" + (dentro[0] & 255))
                    println("fora b=" + (fora[2] & 255))
                    println("sem-imagem=" + (b.textureFor("nada") == 0))
                    b.close()
                }
                """);
        assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
        var pb = new ProcessBuilder(dir.resolve("out/Default/Main").toString()).redirectErrorStream(true);
        pb.environment().put("SDL_VIDEODRIVER", "dummy");
        var p = pb.start();
        String out = new String(p.getInputStream().readAllBytes()).strip();
        assertTrue(p.waitFor(120, TimeUnit.SECONDS) && p.exitValue() == 0, "run: " + out);
        assertTrue(out.contains("dentro r=255"), "a textura do DrawCmd dentro: " + out);
        assertTrue(out.contains("fora b=255"), "o fundo fora: " + out);
        assertTrue(out.contains("sem-imagem=true"), "imagem desconhecida = handle 0: " + out);
    }

    @Test
    void theEventPumpFeedsTheFrozenKeysContract(@TempDir Path dir) throws Exception {
        var so = hostSdl3();
        assumeTrue(so != null, "SDL3 host absent — install libSDL3");
        assumeTrue(hasNativeToolchain(), "native toolchain absent");
        // o caminho do INPUT: um evento sintetico -> o pump -> heldKeys() ->
        // o contrato CONGELADO `Keys.beginFrame` (pressed/down/released).
        var r = compileWithGames(dir, """
                import game.Sdl3Backend
                import game.Keys

                main() {
                    val b = Sdl3Backend("in", 32, 32)
                    b.pumpEvents()
                    val keys = Keys()
                    keys.beginFrame(b.heldKeys())
                    println("frame0 pressed=" + keys.pressed("space"))
                    println("push=" + b.pushKey(0x20))
                    b.pumpEvents()
                    println("held=" + b.heldKeys().size())
                    keys.beginFrame(b.heldKeys())
                    println("frame1 down=" + keys.down("space") + " pressed=" + keys.pressed("space"))
                    b.pumpEvents()
                    keys.beginFrame(b.heldKeys())
                    println("frame2 down=" + keys.down("space") + " released=" + keys.released("space"))
                    b.close()
                }
                """);
        assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
        var pb = new ProcessBuilder(dir.resolve("out/Default/Main").toString()).redirectErrorStream(true);
        pb.environment().put("SDL_VIDEODRIVER", "dummy");
        var p = pb.start();
        String out = new String(p.getInputStream().readAllBytes()).strip();
        assertTrue(p.waitFor(120, TimeUnit.SECONDS) && p.exitValue() == 0, "run: " + out);
        assertTrue(out.contains("frame0 pressed=false"), "nada segurado no inicio: " + out);
        assertTrue(out.contains("push=true"), "o evento sintetico entra: " + out);
        assertTrue(out.contains("held=1"), "o pump reporta a tecla segurada: " + out);
        assertTrue(out.contains("frame1 down=true pressed=true"), "a transicao pressed: " + out);
        assertTrue(out.contains("frame2 down=false released=true"), "a transicao released: " + out);
    }

    @Test
    void theEventPumpFeedsTheFrozenMouseContract(@TempDir Path dir) throws Exception {
        var so = hostSdl3();
        assumeTrue(so != null, "SDL3 host absent — install libSDL3");
        assumeTrue(hasNativeToolchain(), "native toolchain absent");
        // o caminho do MOUSE: uma motion sintetica -> o pump -> pointerX/Y +
        // heldButtons -> o contrato CONGELADO `Mouse.beginFrame(x, y, buttons)`.
        var r = compileWithGames(dir, """
                import game.Sdl3Backend
                import game.Mouse

                main() {
                    val b = Sdl3Backend("m", 32, 32)
                    b.pumpEvents()
                    val m = Mouse()
                    m.beginFrame(b.pointerX(), b.pointerY(), b.heldButtons())
                    println("f0 x=" + m.x() + " y=" + m.y() + " left=" + m.down("left"))
                    println("push=" + b.pushMouseMotion(12, 7, 1))
                    b.pumpEvents()
                    m.beginFrame(b.pointerX(), b.pointerY(), b.heldButtons())
                    println("f1 x=" + m.x() + " y=" + m.y() + " dx=" + m.dx() + " dy=" + m.dy()
                        + " down=" + m.down("left") + " pressed=" + m.pressed("left"))
                    println("push2=" + b.pushMouseMotion(12, 7, 0))
                    b.pumpEvents()
                    m.beginFrame(b.pointerX(), b.pointerY(), b.heldButtons())
                    println("f2 pressed=" + m.pressed("left") + " released=" + m.released("left"))
                    b.close()
                }
                """);
        assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
        var pb = new ProcessBuilder(dir.resolve("out/Default/Main").toString()).redirectErrorStream(true);
        pb.environment().put("SDL_VIDEODRIVER", "dummy");
        var p = pb.start();
        String out = new String(p.getInputStream().readAllBytes()).strip();
        assertTrue(p.waitFor(120, TimeUnit.SECONDS) && p.exitValue() == 0, "run: " + out);
        assertTrue(out.contains("f0 x=0 y=0 left=false"), "ponteiro zerado: " + out);
        assertTrue(out.contains("f1 x=12 y=7 dx=12 dy=7 down=true pressed=true"),
                "a motion sintetica chega com posicao, delta e clique: " + out);
        assertTrue(out.contains("f2 pressed=false released=true"),
                "soltar o botao vira released: " + out);
    }

    @Test
    void theBackendRecusesHonestlyWhereExternCannotBind(@TempDir Path dir) throws Exception {
        // O WASI nao liga `extern` (FFI001) — o backend nativo NAO pode virar
        // um modulo WASI silenciosamente; a recusa e' o contrato honrado.
        copyLibrary(dir.resolve("kof-install/lib/kof-libs"));
        Path main = dir.resolve("Main.kf");
        Files.writeString(main, "import game.Sdl3Backend\nmain() { val b = Sdl3Backend(\"x\", 8, 8) }\n",
                StandardCharsets.UTF_8);
        String previous = System.getProperty("kof.install.dir");
        System.setProperty("kof.install.dir", dir.resolve("kof-install").toString());
        CompilationResult r;
        try {
            r = new CompilerDriver().compile(main, dir.resolve("w"), Target.WASI);
        } finally {
            if (previous == null) System.clearProperty("kof.install.dir");
            else System.setProperty("kof.install.dir", previous);
        }
        assertFalse(r.success(), "o backend nativo nao liga no WASI");
        assertTrue(r.diagnostics().getDiagnostics().stream().anyMatch(d -> "FFI001".equals(d.code())),
                "a recusa e' FFI001, nao um erro generico: " + r.diagnostics().getDiagnostics());
    }
}
