package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class KofAwaitTest {
    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void awaitJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                String trabalho() {
                    return "feito"
                }

                main() {
                    val r = spawn trabalho()
                    assert(r != null)
                    val v = await r
                    println(v)
                }
                """, "feito");
    }

    @Test
    void awaitJs(@TempDir Path tmp) throws Exception {
        // JS CONC003: spawn fire-and-forget enfileira microtask — o código
        // síncrono depois do spawn roda antes do corpo despachado (paridade
        // de intenção com JVM/Native: spawn não bloqueia quem chama).
        runJs(tmp, """
                String calc() {
                    return "js-ok"
                }

                Int soma(a: Int, b: Int) {
                    return a + b
                }

                main() {
                    val r1 = spawn calc()
                    println(await r1)
                    val r2 = spawn soma(2, 3)
                    println((await r2) == 5)
                    spawn { println("fire") }
                    println("done")
                }
                """, "js-ok\ntrue\ndone\nfire");
    }

    @Test
    void awaitNativeRuns(@TempDir Path tmp) throws Exception {
        // CONC001 fechado (31/08): spawn-expr/await com pthread no Native
        Path file = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, """
                String t() { return "x" }
                main() {
                    val r = spawn t()
                    val v = await r
                    println(v)
                }
                """);
        CompilationResult result = driver.compile(file, tmp.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "Native spawn-expr/await deve compilar: "
                + result.diagnostics().getDiagnostics());
        Path bin = tmp.resolve("out").resolve("Default/Main");
        ProcessBuilder pb = new ProcessBuilder(bin.toString()).redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        assertEquals(0, p.waitFor(), "exit code, output: " + output);
        assertTrue(output.contains("x"), "await devolve o valor: " + output);
    }

    @Test
    void awaitPrimitiveJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                Int n() { return 42 }
                Bool flag() { return true }

                main() {
                    val r1 = spawn n()
                    val r3 = spawn flag()
                    assert((await r1) == 42)
                    assert(await r3)
                    println(await r1)
                }
                """, "42");
    }

    private String runJvm(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        try {
            Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                    "-cp", outDir.toString(), "Default.Main").redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code, output: " + output);
            assertEquals(expected, output, "JVM output");
            return output;
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }

    private String runJs(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JS);
        assertTrue(result.success(), "JS compile failed: " + result.diagnostics().getDiagnostics());
        try (java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream()) {
            int ec = dev.kof.runtime.KofJsRunner.run(findJsEntry(outDir), buf,
                    java.io.InputStream.nullInputStream(), new java.io.ByteArrayOutputStream());
            String output = buf.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
            assertEquals(0, ec, "JS exit code, output: " + output);
            assertEquals(expected, output, "JS output");
            return output;
        }
    }

    private static Path findJsEntry(Path dir) throws java.io.IOException {
        try (var s = Files.walk(dir)) {
            var opt = s.filter(p -> p.getFileName().toString().equals("Default.mjs")).findFirst();
            if (opt.isPresent()) return opt.get();
        }
        try (var s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(".mjs"))
                    .findFirst().orElseThrow(() -> new java.io.IOException("no .mjs in " + dir));
        }
    }

    @Test
    void awaitExceptionPropagatesClean(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                Int quebra() {
                    throw "boom-interno"
                }

                main() {
                    val r = spawn quebra()
                    try {
                        await r
                        println("não deveria chegar")
                    } catch (String e) {
                        println("peguei: " + e)
                    }
                }
                """, "peguei: boom-interno");
    }

    @Test
    void pollDoneJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                Int trabalho() { return 7 }

                main() {
                    val r = spawn trabalho()
                    // poll em primitivo não-pronto devolve o default (0):
                    // use done() para esperar sem bloquear
                    var loops = 0
                    while (!done(r) && loops < 1000) {
                        time.sleep(1)
                        loops++
                    }
                    assert(done(r))
                    assert(poll(r) == 7)
                    println("ok-poll")
                }
                """, "ok-poll");
    }

    @Test
    void pollDoneJs(@TempDir Path tmp) throws Exception {
        // CONC003: poll/done síncronos logo após spawn veem task ainda não
        // iniciada; após await o valor fica disponível.
        runJs(tmp, """
                Int trabalho() { return 7 }

                main() {
                    val r = spawn trabalho()
                    println(done(r))
                    println(poll(r))
                    await r
                    println(done(r))
                    println(poll(r))
                    println("ok")
                }
                """, "false\n0\ntrue\n7\nok");
    }

    @Test
    void pollDoneAsyncSemanticsJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                Int lenta() { time.sleep(50); return 7 }

                main() {
                    val r = spawn lenta()
                    println(done(r))
                    println(poll(r))
                    await r
                    println(done(r))
                    println(poll(r))
                    println("ok")
                }
                """, "false\n0\ntrue\n7\nok");
    }

}
