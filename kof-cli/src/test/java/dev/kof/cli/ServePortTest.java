package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #35.3 (R6): o banner da CLI nunca mente sobre a porta.
 * - App Kof-native (web.app + app.listen): O APP é dono da porta; --port
 *   da CLI é ignorado e a CLI AVISA (antes: imprimia "server ready at
 *   CLI:port" que não era a porta real).
 * - App legacy (handle): a porta REAL é a da CLI (--port).
 */
class ServePortTest {

    private final HttpClient client = HttpClient.newHttpClient();

    private static String classpath() {
        return System.getProperty("java.class.path");
    }

    private static String javaBin() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    private Process startCli(Path workDir, String... cliArgs) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin());
        cmd.add("-cp");
        cmd.add(classpath());
        cmd.add("dev.kof.cli.Main");
        cmd.addAll(List.of(cliArgs));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);
        return pb.start();
    }

    /** Lê a saída do processo até aparecer a marca (ou timeout). */
    private String readUntil(Process p, String marker, long timeoutMs) throws IOException, InterruptedException {
        StringBuilder all = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
            String line;
            while ((line = r.readLine()) != null) {
                all.append(line).append('\n');
                if (all.indexOf(marker) >= 0) return all.toString();
                if (System.nanoTime() > deadline) break;
            }
        }
        return all.toString();
    }

    private int code(int port, String path) {
        try {
            HttpResponse<Void> r = client.send(HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            return r.statusCode();
        } catch (Exception e) {
            return -1;
        }
    }

    @Test
    void nativeAppPortIsOwnedByApp_cliPortFlagIsIgnoredWithNotice(@TempDir Path tmp)
            throws Exception {
        int appPort = freePort();
        int cliPort = freePort();
        Files.writeString(tmp.resolve("App.kf"), """
                main() {
                    var app = web.app()
                    app.get("/ping") { return "pong" }
                    app.listen(%d)
                }
                """.formatted(appPort));

        Process p = startCli(tmp, "serve", "App.kf", "--port", String.valueOf(cliPort));
        // §390: o `kof serve` de app kof-native gera um JVM FILHO (o app
        // servido). Mata-se a ÁRVORE no teardown: `destroyForcibly` na CLI
        // (SIGKILL) não roda o shutdown hook, então o filho ficava ÓRFÃO
        // (19 JVMs vazados em 2 dias, `ppid=1`).
        List<ProcessHandle> served = List.of();
        try {
            String out = readUntil(p, "note:", 30_000);
            assertTrue(out.contains("--port " + cliPort + " is ignored"),
                    "CLI deve avisar que --port é ignorado no app kof-native:\n" + out);
            // a porta REAL é a do app.listen, não a da CLI
            assertTrue(CliAwaitFixture.awaitTrue(40, 500, () -> code(appPort, "/ping") == 200),
                    "app deve responder na porta do app.listen(" + appPort + ")");
            assertTrue(code(cliPort, "/ping") != 200,
                    "a porta --port da CLI NUNCA deve responder neste modo");
            served = p.descendants().toList();
            assertFalse(served.isEmpty(),
                    "§390: `kof serve` de app kof-native deve ter o JVM filho (o app servido)");
        } finally {
            p.descendants().forEach(ProcessHandle::destroyForcibly);
            p.destroyForcibly();
        }
        // §390 (RED-first): o filho NÃO pode sobreviver ao teardown.
        for (ProcessHandle h : served) {
            assertTrue(CliAwaitFixture.awaitExit(h, 5000),
                    "§390: o app servido ficou órfão após o teste");
        }
    }

    @Test
    void legacyAppServesOnCliPort(@TempDir Path tmp) throws Exception {
        int cliPort = freePort();
        Files.writeString(tmp.resolve("Legacy.kf"), """
                handle(String method, String path, String body): String {
                    return "legacy-ok"
                }
                """);

        Process p = startCli(tmp, "serve", "Legacy.kf", "--port", String.valueOf(cliPort));
        try {
            String out = readUntil(p, "listening on", 30_000);
            assertTrue(out.contains("http://0.0.0.0:" + cliPort),
                    "banner legacy deve reportar a porta real da CLI:\n" + out);
            assertTrue(CliAwaitFixture.awaitTrue(40, 500, () -> code(cliPort, "/") == 200),
                    "legacy deve responder na porta --port");
        } finally {
            p.destroyForcibly();
        }
    }
}
