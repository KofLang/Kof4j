package dev.kof.compiler;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §259 fatia 4 (native cross): os 3 knobs de resiliência do kof.http no
 * riscv64/aarch64. O core riscv recebe O MESMO mecanismo validado no x86 por
 * medição (connect nonblock + ppoll de deadline + getsockopt SO_ERROR +
 * SO_RCVTIMEO/SO_SNDTIMEO + loop de tentativas + circuit) — aarch64 herda via
 * translateRiscvToAarch64 (NATIVA002). A prova roda os binários sob qemu com
 * o mesmo servidor blackhole/flaky dos E2E x86 (mesma mensagem de erro em
 * todos os 4 targets = paridade cross, regra 5 do freeze).
 */
class KofHttpNativeResilienceCrossTest {

    private final CompilerDriver driver = new CompilerDriver();
    private HttpServer server;
    private ExecutorService pool;
    private ServerSocket blackhole;
    private volatile boolean bhRunning = true;
    private final java.util.List<Socket> bhHeld = new java.util.ArrayList<>();
    private final AtomicInteger flakyHits = new AtomicInteger();

    @AfterEach
    void stop() throws IOException {
        bhRunning = false;
        if (blackhole != null) blackhole.close();
        synchronized (bhHeld) {
            for (Socket s : bhHeld) { try { s.close(); } catch (IOException ignored) {} }
        }
        if (server != null) server.stop(0);
        if (pool != null) pool.shutdownNow();
    }

    private void assumeArchToolchain(String arch) {
        Assumptions.assumeTrue(NativeRiscv64E2ETest.hasToolchain(arch),
                arch + " cross + qemu ausente (NATIVE002)");
    }

    /** Porta que aceita e NUNCA responde (deadline de leitura). */
    private int startBlackhole() throws IOException {
        blackhole = new ServerSocket(0, 16, InetAddress.getLoopbackAddress());
        Thread t = new Thread(() -> {
            while (bhRunning) {
                try {
                    Socket s = blackhole.accept();
                    synchronized (bhHeld) { bhHeld.add(s); }
                } catch (IOException e) {
                    return;
                }
            }
        }, "blackhole-accept-cross");
        t.setDaemon(true);
        t.start();
        return blackhole.getLocalPort();
    }

    /** Flaky: N primeiros = 500, depois 200 (retry). */
    private int startFlaky(int failuresBeforeOk) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        pool = Executors.newFixedThreadPool(2);
        server.setExecutor(pool);
        server.createContext("/flaky", ex -> {
            int n = flakyHits.incrementAndGet();
            byte[] body = ("ok-" + n).getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(n <= failuresBeforeOk ? 500 : 200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
        return server.getAddress().getPort();
    }

    private int closedPort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private void runCross(String arch, Target target, Path tempDir, String kofSource,
                          java.util.function.Consumer<String> assertions) throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, kofSource);
        Path outDir = tempDir.resolve(arch);
        CompilationResult result = new CompilerDriver().compile(source, outDir, target);
        assertTrue(result.success(), arch + " should compile: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default").resolve("Main");
        assertTrue(Files.exists(bin), arch + " binary should exist");
        Process p = NativeRiscv64E2ETest.qemu(arch, bin).start();
        java.util.concurrent.Future<byte[]> reader;
        boolean done;
        try (var ex = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "cross-out-reader"); t.setDaemon(true); return t; })) {
            reader = ex.submit(() -> p.getInputStream().readAllBytes());
            done = p.waitFor(60, TimeUnit.SECONDS);
            if (!done) {
                p.destroyForcibly();
                p.waitFor(5, TimeUnit.SECONDS);
            }
        }
        String out = new String(reader.get(10, TimeUnit.SECONDS), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertTrue(done, arch + " binary did not exit in 60s (knob not enforced); out=" + out);
        assertEquals(0, p.exitValue(), arch + " exit code, out=" + out);
        assertions.accept(out);
    }

    @Test
    void riscv64TimeoutFires(@TempDir Path tempDir) throws Exception {
        assumeArchToolchain("riscv64");
        int bh = startBlackhole();
        runCross("riscv64", Target.NATIVE_RISCV64, tempDir, """
                main() {
                    http.timeout(1)
                    try {
                        println(http.get("http://127.0.0.1:%d/x"))
                        println("NO_TIMEOUT")
                    } catch (String e) {
                        println("tmo=" + e)
                    }
                }
                """.formatted(bh),
                out -> {
                    assertTrue(out.contains("tmo=kof.http: timeout"),
                            "riscv64 timeout must throw 'kof.http: timeout', got: " + out);
                    assertFalse(out.contains("NO_TIMEOUT"), "get() must not return, got: " + out);
                });
    }

    @Test
    void riscv64RetryAndCircuitParity(@TempDir Path tempDir) throws Exception {
        assumeArchToolchain("riscv64");
        int port = startFlaky(2); // 500,500,200
        int closed = closedPort();
        runCross("riscv64", Target.NATIVE_RISCV64, tempDir, """
                main() {
                    http.retry(2)
                    println("r=" + http.get("http://127.0.0.1:%d/flaky"))
                    http.retry(0)
                    http.circuit(1)
                    try { println(http.get("http://127.0.0.1:%d/x")) } catch (String e) { println("f=caught") }
                    try { println(http.get("http://127.0.0.1:%d/flaky")) } catch (String e) { println("o=" + e) }
                    http.circuit(0)
                    println("rec=ok")
                }
                """.formatted(port, closed, port),
                out -> {
                    assertTrue(out.contains("r=ok-3"), "riscv64 retry(2) must hit ok-3, got: " + out);
                    assertTrue(out.contains("f=caught"), "closed port must throw, got: " + out);
                    assertTrue(out.contains("o=kof.http circuit open"),
                            "riscv64 circuit must fail-fast after 1 conn-fail, got: " + out);
                    assertEquals(3, flakyHits.get(), "flaky hits must be 3 (no reconnects), got: " + flakyHits);
                });
    }

    @Test
    void aarch64TimeoutFires(@TempDir Path tempDir) throws Exception {
        assumeArchToolchain("aarch64");
        int bh = startBlackhole();
        runCross("aarch64", Target.NATIVE_AARCH64, tempDir, """
                main() {
                    http.timeout(1)
                    try {
                        println(http.get("http://127.0.0.1:%d/x"))
                        println("NO_TIMEOUT")
                    } catch (String e) {
                        println("tmo=" + e)
                    }
                }
                """.formatted(bh),
                out -> {
                    assertTrue(out.contains("tmo=kof.http: timeout"),
                            "aarch64 timeout must throw 'kof.http: timeout', got: " + out);
                    assertFalse(out.contains("NO_TIMEOUT"), "get() must not return, got: " + out);
                });
    }

    @Test
    void aarch64RetryAndCircuitParity(@TempDir Path tempDir) throws Exception {
        assumeArchToolchain("aarch64");
        int port = startFlaky(2);
        int closed = closedPort();
        runCross("aarch64", Target.NATIVE_AARCH64, tempDir, """
                main() {
                    http.retry(2)
                    println("r=" + http.get("http://127.0.0.1:%d/flaky"))
                    http.retry(0)
                    http.circuit(1)
                    try { println(http.get("http://127.0.0.1:%d/x")) } catch (String e) { println("f=caught") }
                    try { println(http.get("http://127.0.0.1:%d/flaky")) } catch (String e) { println("o=" + e) }
                    http.circuit(0)
                    println("rec=ok")
                }
                """.formatted(port, closed, port),
                out -> {
                    assertTrue(out.contains("r=ok-3"), "aarch64 retry(2) must hit ok-3, got: " + out);
                    assertTrue(out.contains("f=caught"), "closed port must throw, got: " + out);
                    assertTrue(out.contains("o=kof.http circuit open"),
                            "aarch64 circuit must fail-fast after 1 conn-fail, got: " + out);
                    assertEquals(3, flakyHits.get(), "flaky hits must be 3 (no reconnects), got: " + flakyHits);
                });
    }
}
