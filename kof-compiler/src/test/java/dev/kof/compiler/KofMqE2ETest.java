package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;


/**
 * End-to-end tests for {@code kof.mq} — in-memory messaging: pub/sub event
 * bus (Kof lambdas as handlers) and bounded queues.
 */
class KofMqE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path tempDir, String kofSource, String expected) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, kofSource);
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-Dfile.encoding=UTF-8",
                    "-Dstdout.encoding=UTF-8", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running JVM class", e);
        }
    }

    private String runNative(Path tempDir, String kofSource, String expected) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, kofSource);
        Path outDir = tempDir.resolve("out-native");
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Native compilation should succeed: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        try {
            ProcessBuilder pb = new ProcessBuilder(bin.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Native exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected native output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running native binary", e);
        }
    }

    @Test
    void publishDeliversToSubscribers(@TempDir Path tempDir) throws IOException {
        String src = """
                main() {
                    mq.subscribe("order.created", (msg) -> {
                        println("pedido: " + msg)
                    })
                    mq.publish("order.created", "12345")
                    mq.publish("order.created", "67890")
                }
                """;
        runJvm(tempDir, src, "pedido: 12345\npedido: 67890");
        // MQ001 (01/09): pub/sub no Native (in-process, invoke-com-arg).
        runNative(tempDir, src, "pedido: 12345\npedido: 67890");
    }

    @Test
    void unsubscribeStopsDelivery(@TempDir Path tempDir) throws IOException {
        String src = """
                main() {
                    var h = (msg) -> {
                        println("got:" + msg)
                    }
                    mq.subscribe("topic", h)
                    mq.publish("topic", "one")
                    mq.unsubscribe("topic", h)
                    mq.publish("topic", "two")
                }
                """;
        runJvm(tempDir, src, "got:one");
        runNative(tempDir, src, "got:one");
    }

    @Test
    void queuePushPopAndSize(@TempDir Path tempDir) throws IOException {
        String src = """
                main() {
                    var q = mq.queue()
                    mq.push(q, "job-1")
                    mq.push(q, "job-2")
                    println(mq.queueSize(q))
                    println(mq.pop(q))
                    println(mq.pop(q))
                    println(mq.pop(q))
                }
                """;
        runJvm(tempDir, src, "2\njob-1\njob-2\nnull");
        runNative(tempDir, src, "2\njob-1\njob-2\nnull");
    }

    @Test
    void jsAndNativeSupportMq(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    mq.subscribe("topic", (msg) -> {
                        println("got:" + msg)
                    })
                    mq.publish("topic", "hello")
                }
                """);
        // JS: suporta (setInterval-free, pub/sub in-process)
        CompilationResult jsResult = driver.compile(source, tempDir.resolve("js"), Target.JS);
        assertTrue(jsResult.success(), "JS should support mq.publish: " + jsResult.diagnostics().getDiagnostics());
        try (java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream()) {
            Path jsEntry = findJsEntry(tempDir.resolve("js"));
            int ec = dev.kof.runtime.KofJsRunner.run(jsEntry, buf,
                    java.io.InputStream.nullInputStream(), new java.io.ByteArrayOutputStream());
            String out = buf.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
            assertEquals(0, ec, "JS exit code, output: " + out);
            assertEquals("got:hello", out, "JS output");
        }
        // Native: MQ001 fechado (01/09) — compila e roda
        CompilationResult nativeResult = driver.compile(source, tempDir.resolve("native"), Target.NATIVE);
        assertTrue(nativeResult.success(),
                "Native should now support mq.publish (MQ001 fechado): "
                        + nativeResult.diagnostics().getDiagnostics());
    }

    private static Path findJsEntry(Path dir) throws IOException {
        try (var s = Files.walk(dir)) {
            var opt = s.filter(p -> p.getFileName().toString().equals("Default.mjs")).findFirst();
            if (opt.isPresent()) return opt.get();
        }
        try (var s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(".mjs"))
                    .findFirst().orElseThrow(() -> new IOException("no .mjs in " + dir));
        }
    }

    @Test
    void crossNativeReportsMq001(@TempDir Path tempDir) throws IOException {
        // R6: o runtime riscv64/aarch64 tem mq em asm mas com bugs (queue com
        // assinatura errada, pop não remove, queue_size ausente) e nenhum teste
        // cross — gate MQ001 em compile-time até o port completo (padrão DB001),
        // nunca segfault/undefined-reference silencioso.
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var q = mq.queue()
                mq.push(q, "job-1")
            }
            """);
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            CompilationResult r = new CompilerDriver().compile(source, tempDir.resolve("cross-" + t), t);
            assertFalse(r.success(), t + " should report MQ001");
            assertTrue(r.diagnostics().getDiagnostics().toString().contains("MQ001"),
                    t + ": " + r.diagnostics().getDiagnostics());
        }
    }
}