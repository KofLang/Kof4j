package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A face de SOFTWARE do backend no WASI (`docs/development/graphics-sdl3-backend.md`
 * §6; ordem da mantenedora 22/11 — WASI compatível).
 *
 * <p>O WASI preview1 nao tem janela: o frame vira um ARQUIVO PPM. Este teste
 * prova a face que o subset aceita HOJE — os pinceis de INSTANCIA (`at`/`inside`)
 * mais o desenho e a escrita no escopo de `_start` — e mede o limite do subset:
 * um metodo `static` de classe e' RECUSADO no WASI (o plano §6 registra).
 *
 * <p>Os pixels sao lidos de volta do PPM pelo proprio arquivo (o header + os
 * bytes), sem depender de imagem externa no host.
 */
class WasmWasiSoftwareBackendE2ETest implements LibraryInstallSupport {

    public String libraryName() { return "game"; }

    public List<String> libraryMarkers() { return List.of("Window.kf", "Draw.kf"); }

    private static Path wasmtime() {
        String home = System.getenv().getOrDefault("KOF_WASM_HOME",
                System.getProperty("user.home") + "/.local/share/kof-wasm");
        Path p = Path.of(home, "bin", "wasmtime");
        return Files.isExecutable(p) ? p : null;
    }

    @Test
    void aProgramDrawsAFrameAndWritesTheExactPpm(@TempDir Path dir) throws Exception {
        var wasmtime = wasmtime();
        assumeTrue(wasmtime != null, "wasmtime host absent — run scripts/provision-wasmtime.sh");
        copyLibrary(dir.resolve("kof-install/lib/kof-libs"));
        Files.writeString(dir.resolve("Main.kf"), """
                import game.SoftwareBackend
                import kof.io

                main() {
                    val W = 4
                    val H = 2
                    val fb = new Int[W * H * 3]
                    val b = SoftwareBackend()
                    var i = 0
                    while (i < W * H) {
                        val a = b.at(i % W, (i - i % W) / W, W)
                        fb[a] = 20
                        fb[a + 1] = 30
                        fb[a + 2] = 200
                        i = i + 1
                    }
                    if (b.inside(1, 1, W, H)) {
                        val a = b.at(1, 1, W)
                        fb[a] = 255
                        fb[a + 1] = 0
                        fb[a + 2] = 0
                    }
                    if (!b.inside(9, 9, W, H)) { println("recusa-fora") }
                    var body = ""
                    i = 0
                    while (i < W * H * 3) { body = body + String.valueOf((fb[i] & 255) as Char); i = i + 1 }
                    File("frame.ppm").writeText("P6\\n" + W + " " + H + "\\n255\\n" + body)
                }
                """, StandardCharsets.UTF_8);
        String previous = System.getProperty("kof.install.dir");
        System.setProperty("kof.install.dir", dir.resolve("kof-install").toString());
        CompilationResult r;
        try {
            r = new CompilerDriver().compile(dir.resolve("Main.kf"), dir.resolve("out"), Target.WASI);
        } finally {
            if (previous == null) System.clearProperty("kof.install.dir");
            else System.setProperty("kof.install.dir", previous);
        }
        assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
        var p = new ProcessBuilder(wasmtime.toString(), "run", "--dir", dir.toString(),
                dir.resolve("out/Default/Main.wasm").toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).strip();
        assertTrue(p.waitFor(120, TimeUnit.SECONDS) && p.exitValue() == 0, "run: " + out);
        assertTrue(out.contains("recusa-fora"), "inside() recusa o fora do frame: " + out);

        byte[] ppm = Files.readAllBytes(dir.resolve("frame.ppm"));
        String expectedHeader = "P6\n4 2\n255\n";
        int body = expectedHeader.length();
        String header = new String(ppm, 0, body, StandardCharsets.US_ASCII);
        assertEquals(expectedHeader, header, "o header P6 exato");
        // 4x2x3 = 24 bytes, R/G/B por pixel: (0,0) fundo, (1,1) vermelho
        assertEquals(24, ppm.length - body, "o corpo tem w*h*3 bytes");
        assertEquals(20, ppm[body] & 255, "R do (0,0)");
        assertEquals(30, ppm[body + 1] & 255, "G do (0,0)");
        assertEquals(200, ppm[body + 2] & 255, "B do (0,0)");
        int red = body + (1 * 4 + 1) * 3;
        assertEquals(255, ppm[red] & 255, "R do (1,1)");
        assertEquals(0, ppm[red + 1] & 255, "G do (1,1)");
        assertEquals(0, ppm[red + 2] & 255, "B do (1,1)");
    }

    @Test
    void aStaticClassMethodIsStillRefusedByTheWasiSubset(@TempDir Path dir) throws Exception {
        // o limite MEDIDO: `static` de classe recusa no WASI; a instancia nao.
        // (O plano §6 registra — nao e' regressao, e' a fronteira do subset.)
        Files.writeString(dir.resolve("Main.kf"),
                "class B { static Int at(Int x, Int w) { return x * w } }\nmain() { println(B.at(3, 4)) }\n",
                StandardCharsets.UTF_8);
        var r = new CompilerDriver().compile(dir.resolve("Main.kf"), dir.resolve("out"), Target.WASI);
        assertFalse(r.success(), "a chamada estatica recusa no subset 15.2");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "WASM002".equals(d.code())),
                "a recusa e' WASM002: " + r.diagnostics().getDiagnostics());
    }
}
