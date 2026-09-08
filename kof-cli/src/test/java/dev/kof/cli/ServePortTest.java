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
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        String line;
        while ((line = r.readLine()) != null) {
            all.append(line).append('\n');
            if (all.indexOf(marker) >= 0) return all.toString();
            if (System.nanoTime() > deadline) break;
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
        try {
            String out = readUntil(p, "note:", 30_000);
            assertTrue(out.contains("--port " + cliPort + " is ignored"),
                    "CLI deve avisar que --port é ignorado no app kof-native:\n" + out);
            // a porta REAL é a do app.listen, não a da CLI
            boolean up = false;
            for (int i = 0; i < 40 && !up; i++) {
                if (code(appPort, "/ping") == 200) up = true;
                else Thread.sleep(500);
            }
            assertTrue(up, "app deve responder na porta do app.listen(" + appPort + ")");
            assertTrue(code(cliPort, "/ping") != 200,
                    "a porta --port da CLI NUNCA deve responder neste modo");
        } finally {
            p.destroyForcibly();
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
            boolean up = false;
            for (int i = 0; i < 40 && !up; i++) {
                if (code(cliPort, "/") == 200) up = true;
                else Thread.sleep(500);
            }
            assertTrue(up, "legacy deve responder na porta --port");
        } finally {
            p.destroyForcibly();
        }
    }
}
