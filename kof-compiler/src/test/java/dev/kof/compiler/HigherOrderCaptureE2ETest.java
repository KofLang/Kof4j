package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * memory-safety fase 4.3 (#662, D-MEMORY-SAFETY, spec B-06): a forma idiomática
 * canônica do corpus é a lambda como CALLBACK de `map/filter/reduce` sobre
 * estado capturado. `KofHigherOrderTest` trava as operações de stdlib só no
 * JVM e SEM captura; `LambdaE2ETest` (4.1) trava a chamada direta da lambda.
 * A interseção callback × caixa × 4 alvos nunca foi travada — esta classe é a
 * medição honesta (pin se verde na raiz; RED = fix no mesmo pacote).
 */
class HigherOrderCaptureE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path source, Path outDir) throws IOException {
        CompilationResult r = driver.compile(source, outDir, Target.JVM);
        assertTrue(r.success(), "JVM compile: " + r.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "JVM exit, output: '" + out + "'");
            return out;
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    private String runScript(Path source, Path outDir) throws IOException {
        KofInterpreter.Result r = new CompilerDriver().interpret(List.of(source), outDir, new String[0]);
        String out = r.stdout().replace("\r\n", "\n").trim();
        assertEquals(0, r.exitCode(), "SCRIPT exit, output: '" + out + "'");
        return out;
    }

    private String runJs(Path source, Path outDir) throws IOException {
        CompilationResult r = driver.compile(source, outDir, Target.JS);
        assertTrue(r.success(), "JS compile: " + r.diagnostics().getDiagnostics());
        Path entry = null;
        try (var s = Files.walk(outDir)) {
            entry = s.filter(p -> p.getFileName().toString().equals("Default.mjs")).findFirst().orElse(null);
        }
        assertNotNull(entry, "entrada JS");
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(entry, buf,
                java.io.InputStream.nullInputStream(), new java.io.ByteArrayOutputStream());
        String out = buf.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
        assertEquals(0, ec, "JS exit, output: '" + out + "'");
        return out;
    }

    private String runNative(Path source, Path outDir) throws IOException, InterruptedException {
        CompilationResult r = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(r.success(), "Native compile: " + r.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binario Native");
        ProcessBuilder pb = new ProcessBuilder(bin.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
            .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "Native exit, output: '" + out + "'");
        return out;
    }

    private void assert4(String name, String src, String golden) throws Exception {
        Path t = Files.createTempDirectory(name);
        Path source = t.resolve("Main.kf");
        Files.writeString(source, src);
        assertEquals(golden, runJvm(source, t.resolve("jvm")), name + " JVM");
        assertEquals(golden, runScript(source, t.resolve("script")), name + " SCRIPT");
        assertEquals(golden, runJs(source, t.resolve("js")), name + " JS");
        assertEquals(golden, runNative(source, t.resolve("native")), name + " NATIVE");
    }

    // (a) map lendo captura (sem mutacao): desce sem caixa por construcao
    // (4.2) — a paridade do valor e o que se trava.
    private static final String MAP_READ = """
            main() {
                var off = 10
                var r = listOf(1, 2, 3).map((x: Int) -> x + off)
                var s = 0
                for (var v in r) { s = s + v }
                println(s)
            }
            """;

    @Test
    void mapReadCaptureParity() throws Exception {
        assert4("map-read", MAP_READ, "36");
    }

    // (b) map ESCREVENDO captura mutavel: exercita CapturedVarBox ATE o
    // callback de stdlib (4.1 so exercitava chamada direta).
    private static final String MAP_WRITE = """
            main() {
                var count = 0
                var doubled = listOf(1, 2, 3).map((x: Int) -> { count = count + 1; return x * 2 })
                println(count)
                println(doubled.get(2))
            }
            """;

    @Test
    void mapWriteCaptureParity() throws Exception {
        assert4("map-write", MAP_WRITE, "3\n6");
    }

    // (c) reduce acumulando sobre captura externa mutavel (callback binario
    // + caixa + valor de retorno do accumulate).
    private static final String REDUCE_ACC = """
            main() {
                var total = 0
                var sum = listOf(1, 2, 3, 4).reduce(0, (a: Int, x: Int) -> { total = total + x; return a + x })
                println(sum)
                println(total)
            }
            """;

    @Test
    void reduceCaptureParity() throws Exception {
        assert4("reduce-acc", REDUCE_ACC, "10\n10");
    }

    // (d) filtro com escrita de captura (mesma caixa do (b), aridade/canal
    // diferente — o corpus recomenda filter junto com map).
    private static final String FILTER_WRITE = """
            main() {
                var hits = 0
                var evens = listOf(1, 2, 3, 4).filter((x: Int) -> { if (x % 2 == 0) { hits = hits + 1 } return x % 2 == 0 })
                println(hits)
                println(evens.size)
            }
            """;

    @Test
    void filterWriteCaptureParity() throws Exception {
        assert4("filter-write", FILTER_WRITE, "2\n2");
    }
}
