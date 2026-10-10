package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #598 / APP002 (D-APP): o {@code kof new --type backend} gera
 * {@code [server] port} no {@code kof.toml}, mas o valor era ignorado em
 * silêncio. A Opção 1 ratificada: o {@code kof serve} lê o manifesto (a CLI
 * lê o manifesto, não o runtime) e repassa a porta ao app como
 * {@code KOF_SERVER_PORT} quando nenhum veio do ambiente —
 * {@code config.int("server.port", ...)} lê essa env por convenção.
 *
 * <p>Cobre: (1) a porta do manifesto é honrada; (2) {@code KOF_SERVER_PORT}
 * explícito do usuário tem precedência sobre o manifesto. Roda a CLI real
 * como subprocesso e exercita por HTTP.
 */
class ServeManifestPortE2ETest {

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

    private Process startCli(Path workDir, Map<String, String> env, String... cliArgs)
            throws IOException {
        List<String> cmd = new java.util.ArrayList<>();
        cmd.add(javaBin());
        cmd.add("-cp");
        cmd.add(classpath());
        cmd.add("dev.kof.cli.Main");
        cmd.addAll(List.of(cliArgs));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir.toFile());
        // O caso sob teste é "nenhum KOF_SERVER_PORT veio do ambiente": remove o
        // herdado para o resultado não depender do host/CI.
        pb.environment().remove("KOF_SERVER_PORT");
        pb.environment().putAll(env);
        pb.redirectErrorStream(true);
        return pb.start();
    }

    private record HttpResult(int code, String body) {}

    private static HttpResult get(int port, String path) throws IOException {
        HttpURLConnection c = (HttpURLConnection) URI.create(
                "http://127.0.0.1:" + port + path).toURL().openConnection();
        c.setConnectTimeout(2000);
        c.setReadTimeout(5000);
        c.setRequestProperty("X-Forwarded-For", "127.0.0.1");
        int code = c.getResponseCode();
        InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String body = is == null ? "" : new String(is.readAllBytes(), StandardCharsets.UTF_8);
        c.disconnect();
        return new HttpResult(code, body);
    }

    private static boolean waitUp(Process server, int port) throws Exception {
        for (int i = 0; i < 60; i++) {
            if (!server.isAlive()) return false;
            try {
                if (get(port, "/api/ping").code == 200) return true;
            } catch (IOException e) {
                CliAwaitFixture.pause(500);
            }
        }
        return false;
    }

    private static void killTree(Process p) {
        p.descendants().forEach(ProcessHandle::destroyForcibly);
        p.destroy();
        try {
            if (!p.waitFor(5, TimeUnit.SECONDS)) p.destroyForcibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Gera {@code kof new demo --type backend} e devolve a raiz do projeto. */
    private Path newBackendApp(Path tmp) throws Exception {
        Process created = startCli(tmp, Map.of(), "new", "demo", "--type", "backend");
        String out = new String(created.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(created.waitFor(120, TimeUnit.SECONDS), "kof new timeout\n" + out);
        assertEquals(0, created.exitValue(), "kof new rc\n" + out);
        Path app = tmp.resolve("demo");
        assertTrue(Files.exists(app.resolve("kof.toml")), "kof.toml gerado");
        return app;
    }

    private static void writeManifestPort(Path app, int port) throws IOException {
        Files.writeString(app.resolve("kof.toml"), """
                [project]
                name = "demo"

                [backend]
                target = "jvm"

                [server]
                port = %d
                """.formatted(port));
    }

    @Test
    void serveHonorsManifestPortWhenEnvUnset(@TempDir Path tmp) throws Exception {
        Path app = newBackendApp(tmp);
        int port = freePort();
        writeManifestPort(app, port);

        Process server = startCli(app, Map.of(), "serve", "src/Main.kf");
        try {
            assertTrue(waitUp(server, port),
                    "servidor não subiu na porta do kof.toml (" + port + ") — manifesto ignorado");
            HttpResult ping = get(port, "/api/ping");
            assertEquals(200, ping.code, "GET /api/ping na porta do manifesto");
            assertTrue(ping.body.contains("pong"), "JSON backend: " + ping.body);
        } finally {
            killTree(server);
        }
    }

    @Test
    void envKofServerPortOverridesManifest(@TempDir Path tmp) throws Exception {
        Path app = newBackendApp(tmp);
        int manifestPort = freePort();
        int envPort = freePort();
        while (envPort == manifestPort) envPort = freePort();
        writeManifestPort(app, manifestPort);

        Process server = startCli(app, Map.of("KOF_SERVER_PORT", String.valueOf(envPort)),
                "serve", "src/Main.kf");
        try {
            assertTrue(waitUp(server, envPort),
                    "KOF_SERVER_PORT do ambiente deve prevalecer sobre o manifesto (" + envPort + ")");
            assertEquals(200, get(envPort, "/api/ping").code, "env vence o manifesto");
        } finally {
            killTree(server);
        }
    }
}
