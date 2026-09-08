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
    void crossNativeMqQueueAndPubsub(@TempDir Path tempDir) throws Exception {
        // MQ001 no riscv64/aarch64 (port completo 05/09 — antes era gate
        // temporário porque o asm cross estava infuncional). Paridade de
        // output com o x86_64.
        String queueSrc = """
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
        String pubSrc = """
            main() {
                mq.subscribe("order.created", (msg) -> {
                    println("pedido: " + msg)
                })
                mq.publish("order.created", "12345")
                mq.publish("order.created", "67890")
            }
            """;
        String unSrc = """
            main() {
                val h = (msg) -> {
                    println("got:" + msg)
                }
                mq.subscribe("topic", h)
                mq.publish("topic", "one")
                mq.unsubscribe("topic", h)
                mq.publish("topic", "two")
            }
            """;
        boolean hasRiscv = hasCmd("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64");
        boolean hasAarch = hasCmd("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64");
        org.junit.jupiter.api.Assumptions.assumeTrue(hasRiscv && hasAarch,
                "cross toolchain riscv64/aarch64 + qemu ausente — pulando (NATIVE002)");
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            assertCross(tempDir, queueSrc, "2\njob-1\njob-2\nnull", t);
            assertCross(tempDir, pubSrc, "pedido: 12345\npedido: 67890", t);
            assertCross(tempDir, unSrc, "got:one", t);
        }
    }

    private static boolean hasCmd(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c).redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes()).trim();
                if (p.waitFor() != 0 || out.isEmpty()) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private void assertCross(Path tempDir, String src, String expected, Target t) throws Exception {
        Path source = tempDir.resolve("Main-" + t + "-" + java.util.UUID.randomUUID() + ".kf");
        Files.writeString(source, src);
        Path out = tempDir.resolve("cross-" + t + "-" + java.util.UUID.randomUUID());
        CompilationResult r = new CompilerDriver().compile(source, out, t);
        assertTrue(r.success(), t + ": " + r.diagnostics().getDiagnostics());
        String bin = out.resolve("Default/Main").toString();
        String arch = t == Target.NATIVE_RISCV64 ? "qemu-riscv64" : "qemu-aarch64";
        Process p = new ProcessBuilder(arch, bin).redirectErrorStream(true).start();
        String outStr = new String(p.getInputStream().readAllBytes()).trim();
        int ec = p.waitFor();
        assertEquals(0, ec, t + ": " + outStr);
        assertEquals(expected, outStr, t + " output divergiu do x86_64");
    }
}