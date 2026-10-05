package dev.kof.compiler;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #756 / D-MAINT-BATCH-0510 (HTTP1): a {@code kof.http} failure must be
 * catchable by {@code catch (String e)} — the frozen "Kof throws Strings"
 * contract ({@code learn/14-exceptions.md}). The JVM runtime threw a raw
 * {@code java.net.ConnectException} / {@code java.io.IOException}, which the
 * generated catch for a String type (a {@code java.lang.RuntimeException}
 * handler reading {@code getMessage()}) cannot see, so the program died with a
 * stack trace and neither the catch nor {@code after} ran.
 */
class KofHttpErrorContractE2ETest {

    private static final String JAVA_BIN = Path.of(
            System.getProperty("java.home"), "bin", "java").toString();

    private final CompilerDriver driver = new CompilerDriver();
    private HttpServer server;
    private ExecutorService pool;

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
        if (pool != null) pool.shutdownNow();
    }

    private void startContractServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        pool = Executors.newFixedThreadPool(2);
        server.setExecutor(pool);
        server.createContext("/ok", ex -> {
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.createContext("/boom", ex -> {
            byte[] body = "boom".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(501, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
    }

    private int basePort() {
        return server.getAddress().getPort();
    }

    private int closedPort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private String runJvm(Path tempDir, String source, String... args) throws IOException {
        Path file = tempDir.resolve("Main.kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("classes");
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        List<String> cmd = new ArrayList<>();
        cmd.add(JAVA_BIN);
        cmd.add("-Dfile.encoding=UTF-8");
        cmd.add("-cp");
        cmd.add(outDir.toString());
        cmd.add("Default.Main");
        cmd.addAll(List.of(args));
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code, output: " + output);
            return output;
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    /**
     * The issue's exact shape: a refused connection and a non-2xx response are
     * both caught by {@code catch (String e)}, and the program continues.
     */
    @Test
    void httpFailuresAreCatchableAsStringAndExecutionContinues(@TempDir Path tempDir) throws IOException {
        startContractServer();
        String closed = "http://127.0.0.1:" + closedPort() + "/";
        String source = """
                main(args: List<String>) {
                    var base = args.get(0)
                    var closedUrl = args.get(1)
                    var ok = http.get(base + "/ok")
                    println("ok=" + ok)
                    try {
                        var b = http.get(closedUrl)
                        println("refused-body=" + b)
                    } catch (String e) {
                        println("refused=" + e)
                    }
                    try {
                        var c = http.get(base + "/boom")
                        println("boom-body=" + c)
                    } catch (String e) {
                        println("boom=" + e)
                    }
                    println("after")
                }
                """;
        String out = runJvm(tempDir, source, "http://127.0.0.1:" + basePort(), closed);
        assertTrue(out.contains("ok=ok"), "success path must still return the body, got: " + out);
        assertTrue(out.contains("refused="), "connection refused must reach catch (String e), got: " + out);
        assertTrue(out.contains("boom=HTTP 501"), "non-2xx must reach catch (String e) with the message, got: " + out);
        assertTrue(out.contains("after"), "execution must continue after the catches, got: " + out);
        assertFalse(out.contains("Exception in thread"),
                "the raw Java exception must not escape to the stack trace, got: " + out);
        assertFalse(out.contains("\tat java.base/"),
                "no JVM stack trace may escape, got: " + out);
    }
}
