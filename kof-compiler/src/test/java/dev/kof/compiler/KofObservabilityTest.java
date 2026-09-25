package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class KofObservabilityTest {
    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void observabilityJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
            main() {
                assert(observability.health() == "UP")
                assert(observability.readiness())
                assert(observability.liveness())
                val c1 = observability.counter("obsJvmCounter")
                assert(c1 == 1)
                val c2 = observability.counter("obsJvmCounter")
                assert(c2 == 2)
                val c3 = observability.increment("obsJvmCounter", 3)
                assert(c3 == 5)
                observability.gauge("obsJvmGauge", 42)
                observability.histogram("obsJvmLatency", 10)
                observability.histogram("obsJvmLatency", 15)
                val m = observability.metrics()
                assert(m.contains("obsJvmCounter 5"))
                assert(m.contains("obsJvmGauge 42"))
                assert(m.contains("obsJvmLatency_count 2"))
                assert(m.contains("obsJvmLatency_sum 25"))
                val r1 = observability.requestId()
                assert(r1.length() > 0)
                val r2 = observability.correlationId()
                assert(r2.length() > 0)
                assert(r1 != r2)
                println("ok")
            }
            """, "ok");
    }

    @Test
    void observabilityNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
            main() {
                assert(observability.health() == "UP")
                assert(observability.readiness())
                assert(observability.liveness())
                val c1 = observability.counter("obsNativeCounter")
                assert(c1 == 1)
                val c2 = observability.counter("obsNativeCounter")
                assert(c2 == 2)
                val c3 = observability.increment("obsNativeCounter", 5)
                assert(c3 == 7)
                observability.gauge("obsNativeGauge", 99)
                // OBS002 fechado: histogram + export Prometheus no Native
                observability.histogram("obsNativeLatency", 10)
                observability.histogram("obsNativeLatency", 15)
                val m = observability.metrics()
                assert(m.contains("obsNativeCounter 7"))
                assert(m.contains("obsNativeGauge 99"))
                assert(m.contains("obsNativeLatency_count 2"))
                assert(m.contains("obsNativeLatency_sum 25"))
                val r1 = observability.requestId()
                assert(r1.length() > 0)
                val r2 = observability.correlationId()
                assert(r2.length() > 0)
                println("ok")
            }
            """, "ok");
    }

    @Test
    void observabilityJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
            main() {
                println(observability.health())
                println(observability.readiness())
                println(observability.liveness())
                println(observability.counter("obsJsCounter"))
                println(observability.counter("obsJsCounter"))
                println(observability.increment("obsJsCounter", 10))
                observability.gauge("obsJsGauge", 7)
                observability.histogram("obsJsLatency", 5)
                observability.histogram("obsJsLatency", 8)
                val m = observability.metrics()
                assert(m.contains("obsJsCounter 12"))
                assert(m.contains("obsJsGauge 7"))
                assert(m.contains("obsJsLatency_count 2"))
                assert(m.contains("obsJsLatency_sum 13"))
                println(observability.requestId().length() > 0)
                println(observability.correlationId().length() > 0)
                println("done")
            }
            """, "UP\ntrue\ntrue\n1\n2\n12\ntrue\ntrue\ndone");
    }

    @Test
    void tracingJvmNativeJs(@TempDir Path tmp) throws Exception {
        // W3C Trace Context: trace-id = 32 hex, span-id = 16 hex.
        runJvm(tmp, """
            main() {
                val t = observability.traceId()
                assert(t.length() == 32)
                val s = observability.spanId()
                assert(s.length() == 16)
                val t2 = observability.traceId()
                assert(t2 != t)
                println("ok")
            }
            """, "ok");
        runNative(tmp, """
            main() {
                val t = observability.traceId()
                assert(t.length() == 32)
                val s = observability.spanId()
                assert(s.length() == 16)
                println("ok")
            }
            """, "ok");
        runJs(tmp, """
            main() {
                println(observability.traceId().length())
                println(observability.spanId().length())
                println("done")
            }
            """, "32\n16\ndone");
    }

@Test
    void spansWithTiming(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
            main() {
                val h = observability.spanStart("op")
                var x = 0
                for (var i = 0; i < 1000; i++) { x = x + i }
                val json = observability.spanEnd(h)
                assert(json.contains("\\"traceId\\":"))
                assert(json.contains("\\"spanId\\":"))
                assert(json.contains("\\"durationMicros\\":"))
                assert(json.contains("\\"name\\":\\"op\\""))
                assert(observability.spanEnd(h) == "{}")
                println("ok")
            }
            """, "ok");
        runJs(tmp, """
            main() {
                val h = observability.spanStart("op")
                val json = observability.spanEnd(h)
                assert(json.contains("\\"traceId\\":"))
                assert(json.contains("\\"spanId\\":"))
                assert(json.contains("\\"name\\":\\"op\\""))
                println("done")
            }
            """, "done");
        runNative(tmp, """
            main() {
                val h = observability.spanStart("op")
                val json = observability.spanEnd(h)
                assert(json.contains("\\"traceId\\":"))
                assert(json.contains("\\"spanId\\":"))
                assert(json.contains("\\"name\\":\\"op\\""))
                assert(json.contains("\\"startMicros\\":"))
                assert(json.contains("\\"durationMicros\\":"))
                assert(json.contains("\\"parentSpanId\\":\\"\\""))
                assert(h.length() == 48)
                assert(json.contains(h.substring(0, 32)))
                assert(json.contains(h.substring(32)))
                assert(observability.spanEnd(h) == "{}")
                assert(observability.spanEnd("01234567890123456789012345678901234567890123456789") == "{}")
                println("ok")
            }
            """, "ok");
        // face (b) do registro de bugs 272: o nome precisa sobreviver ao
        // escape JSON (aspas e barra invertida no nome viram sequencias
        // escapadas no JSON), como no golden JVM.
        runNative(tmp, """
            main() {
                val h = observability.spanStart("a\\"b")
                val json = observability.spanEnd(h)
                assert(json.contains("\\"name\\":\\"a\\\\\\"b\\""))
                println("esc-ok")
            }
            """, "esc-ok");
    }

    @Test
    void spansCrossArchRiscv64(@TempDir Path tmp) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                NativeRiscv64E2ETest.hasToolchain("riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        runCross(tmp, Target.NATIVE_RISCV64, "riscv64");
    }

    @Test
    void spansCrossArchAarch64(@TempDir Path tmp) throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                NativeRiscv64E2ETest.hasToolchain("aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002)");
        runCross(tmp, Target.NATIVE_AARCH64, "aarch64");
    }

    // §272 face (c): riscv64 deixa de ser stub constante; aarch64 vem do MESMO
    // emissor riscv via tradutor (regra 5). Afirma o golden da face (b): shape
    // completo, IDs W3C reais (rand), handle 48, escape de nome, missing/dup
    // -> "{}" (nunca null silencioso — R6).
    private String runCross(Path tempDir, Target target, String arch) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, """
            main() {
                val h = observability.spanStart("op")
                assert(h.length() == 48)
                val json = observability.spanEnd(h)
                assert(json.contains("\\"traceId\\":\\""))
                assert(json.contains("\\"spanId\\":\\""))
                assert(json.contains("\\"parentSpanId\\":\\"\\""))
                assert(json.contains("\\"name\\":\\"op\\""))
                assert(json.contains("\\"startMicros\\":"))
                assert(json.contains("\\"endMicros\\":"))
                assert(json.contains("\\"durationMicros\\":"))
                assert(json.contains(h.substring(0, 32)))
                assert(json.contains(h.substring(32)))
                assert(observability.spanEnd(h) == "{}")
                assert(observability.spanEnd("01234567890123456789012345678901234567890123456789") == "{}")
                assert(observability.requestId().length() == 32)
                assert(observability.traceId().length() == 32)
                assert(observability.spanId().length() == 16)
                assert(observability.traceId() != observability.traceId())
                val e = observability.spanStart("a\\"b")
                assert(observability.spanEnd(e).contains("\\"name\\":\\"a\\\\\\"b\\""))
                assert(observability.health() == "UP")
                assert(observability.readiness())
                assert(observability.liveness())
                val c1 = observability.counter("obsCrossCounter")
                assert(c1 == 1)
                val c2 = observability.counter("obsCrossCounter")
                assert(c2 == 2)
                val c3 = observability.increment("obsCrossCounter", 5)
                assert(c3 == 7)
                observability.gauge("obsCrossGauge", 99)
                observability.histogram("obsCrossLatency", 10)
                observability.histogram("obsCrossLatency", 15)
                val m = observability.metrics()
                assert(m.contains("obsCrossCounter 7"))
                assert(m.contains("obsCrossGauge 99"))
                assert(m.contains("obsCrossLatency_count 2"))
                assert(m.contains("obsCrossLatency_sum 25"))
                println("x-ok")
            }
            """);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, target);
        assertTrue(result.success(), arch + " compile failed: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), arch + " binary should exist");
        String output = NativeRiscv64E2ETest.runQemu(arch, bin);
        assertEquals("x-ok", output, arch + " output");
        return output;
    }

@Test
    void otlpExportJvm(@TempDir Path tmp) throws Exception {
        // OBS003/OTel: export OTLP/JSON dos spans concluídos (JVM). O nome
        // passado em spanStart precisa sobreviver ao spanEnd (bug corrigido:
        // antes virava sempre "span").
        runJvm(tmp, """
            main() {
                val empty = observability.exportSpans()
                assert(empty.contains("\\"resourceSpans\\""))
                assert(empty.contains("\\"spans\\":[]"))
                val h1 = observability.spanStart("db.query")
                val h2 = observability.spanStart("http.get")
                val hq = observability.spanStart("q\\"x")
                val j1 = observability.spanEnd(h1)
                val j2 = observability.spanEnd(h2)
                observability.spanEnd(hq)
                assert(j1.contains("\\"name\\":\\"db.query\\""))
                assert(j2.contains("\\"name\\":\\"http.get\\""))
                val otlp = observability.exportSpans()
                assert(otlp.contains("q\\\\\\"x"))
                assert(otlp.contains("\\"resourceSpans\\""))
                assert(otlp.contains("\\"service.name\\""))
                assert(otlp.contains("\\"scope\\":{\\"name\\":\\"kof.observability\\"}"))
                assert(otlp.contains("\\"name\\":\\"db.query\\""))
                assert(otlp.contains("\\"name\\":\\"http.get\\""))
                assert(otlp.contains("\\"kind\\":1"))
                assert(otlp.contains("\\"startTimeUnixNano\\":"))
                assert(otlp.contains("\\"endTimeUnixNano\\":"))
                println("ok")
            }
            """, "ok");
    }

    @Test
    void otlpExportJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
            main() {
                val h = observability.spanStart("op.js")
                val j = observability.spanEnd(h)
                assert(j.contains("\\"name\\":\\"op.js\\""))
                val otlp = observability.exportSpans()
                assert(otlp.contains("\\"resourceSpans\\""))
                assert(otlp.contains("\\"service.name\\""))
                assert(otlp.contains("\\"name\\":\\"op.js\\""))
                assert(otlp.contains("\\"startTimeUnixNano\\":"))
                println("done")
            }
            """, "done");
    }

    @Test
    void otlpExportNativeIsHonestGap(@TempDir Path tmp) throws Exception {
        // R6/R7: OTLP export não existe no Native ainda — recusa em tempo de
        // compilação com OBS003, nunca um stub silencioso.
        Path file = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, """
            main() {
                println(observability.exportSpans())
            }
            """);
        CompilationResult result = driver.compile(file, tmp.resolve("out"), Target.NATIVE);
        assertFalse(result.success(), "Native OTLP export should be rejected (OBS003)");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("OBS003"), "expected OBS003 in: " + diags);
    }

@Test
    void applicationLifecycle(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
            application {
                onStart {
                    println("started")
                }
                onShutdown {
                    println("stopped")
                }
            }
            main() {
                println("work")
            }
            """, "started\nwork\nstopped");
        runNative(tmp, """
            application {
                onStart {
                    println("started")
                }
                onShutdown {
                    println("stopped")
                }
            }
            main() {
                println("work")
            }
            """, "started\nwork\nstopped");
        runJs(tmp, """
            application {
                onStart {
                    println("started")
                }
                onShutdown {
                    println("stopped")
                }
            }
            main() {
                println("work")
            }
            """, "started\nwork\nstopped");
    }

    @Test
    void applicationLifecycleEmptyBlocks(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
            application {
            }
            main() {
                println("ok")
            }
            """, "ok");
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
            if (expected != null) assertEquals(expected, output, "JVM output");
            return output;
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }

    private String runNative(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.NATIVE);
        assertTrue(result.success(), "Native compile failed: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        try {
            Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Native exit code, output: " + output);
            if (expected != null) assertEquals(expected, output, "Native output");
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
            if (expected != null) assertEquals(expected, output, "JS output");
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
}
