package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F3 (plataforma, APPLICATION_MODEL I2 DoD): Cenário A full-stack E2E sobre
 * o exemplo canônico {@code examples/fullstack/}. Roda a CLI real como
 * subprocesso (build + serve) e exercita por HTTP:
 *   GET /api/ping   → JSON do backend
 *   GET /           → bundle frontend (app.serveDir("/", KOF_WEB_OUT))
 *   GET /static/app.css → estático (KOF_STATIC_OUT)
 *   GET /../src/Main.kf → traversal bloqueado (404)
 *   build --backend native com web/ → APP001 honesto (R6)
 *   monólito (sem web/) → build normal (zero regressão)
 */
class FullStackE2ETest {

    private static final Path EXAMPLE = locateExample();

    private static Path locateExample() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParent()) {
            Path cand = dir.resolve("examples").resolve("fullstack");
            if (Files.isDirectory(cand)) return cand;
        }
        throw new IllegalStateException("examples/fullstack não encontrado a partir de "
                + System.getProperty("user.dir"));
    }

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

    private static Path copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> walk = Files.walk(from)) {
            for (Path p : walk.toList()) {
                Path dst = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(dst);
                } else {
                    Files.createDirectories(dst.getParent());
                    Files.copy(p, dst, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        return to;
    }

    private static void deleteTree(Path root) {
        if (!Files.exists(root)) return;
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
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

    private Process startCli(Path workDir, String... cliArgs) throws IOException {
        return startCli(workDir, java.util.Map.of(), cliArgs);
    }

    private Process startCli(Path workDir, java.util.Map<String, String> env, String... cliArgs)
            throws IOException {
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(javaBin());
        cmd.add("-cp");
        cmd.add(classpath());
        cmd.add("dev.kof.cli.Main");
        cmd.addAll(java.util.List.of(cliArgs));
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir.toFile());
        pb.environment().putAll(env);
        pb.redirectErrorStream(true);
        return pb.start();
    }

    @Test
    void buildFullStack_producesBackendAndFrontend(@TempDir Path tmp) throws Exception {
        Path app = copyTree(EXAMPLE, tmp.resolve("app"));
        Process p = startCli(app, "build", "src", "--output", "dist");
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(120, TimeUnit.SECONDS), "build timeout\n" + out);
        assertEquals(0, p.exitValue(), "build rc\n" + out);
        assertTrue(Files.exists(app.resolve("dist/backend/Default/Main.class")),
                "backend .class: " + out);
        assertTrue(Files.exists(app.resolve("dist/frontend/index.html")),
                "frontend bundle: " + out);
        assertTrue(Files.exists(app.resolve("dist/static/app.css")),
                "estáticos copiados: " + out);
    }

    @Test
    void buildNativeWithFrontend_isApp001(@TempDir Path tmp) throws Exception {
        Path app = copyTree(EXAMPLE, tmp.resolve("app"));
        Process p = startCli(app, "build", "src", "--backend", "native");
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(120, TimeUnit.SECONDS), "build timeout\n" + out);
        assertNotEquals(0, p.exitValue(), "APP001 deve falhar (R6, não silenciar)");
        assertTrue(out.contains("APP001"), "esperava APP001, foi: " + out);
    }

    @Test
    void serveFullStack_servesApiBundleAndStatic(@TempDir Path tmp) throws Exception {
        Path app = copyTree(EXAMPLE, tmp.resolve("app"));
        int port = freePort();
        // o app full-stack (web.app) escuta config.int("server.port") → env
        // KOF_SERVER_PORT; o --port da CLI só vale no ramo legacy in-process.
        Process server = startCli(app, java.util.Map.of("KOF_SERVER_PORT", String.valueOf(port)),
                "serve", "src/Main.kf");
        try {
            // espera o servidor responder
            boolean up = false;
            for (int i = 0; i < 60 && !up; i++) {
                if (!server.isAlive()) {
                    String log = new String(server.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                    fail("servidor morreu ao subir:\n" + log);
                }
                try {
                    if (get(port, "/api/ping").code == 200) up = true;
                } catch (IOException e) {
                    Thread.sleep(500);
                }
            }
            assertTrue(up, "servidor não subiu na porta " + port);

            HttpResult ping = get(port, "/api/ping");
            assertEquals(200, ping.code, "GET /api/ping");
            assertTrue(ping.body.contains("pong"), "JSON backend: " + ping.body);

            HttpResult root = get(port, "/");
            assertEquals(200, root.code, "GET / (bundle via KOF_WEB_OUT)");
            assertTrue(root.body.contains("<html") || root.body.contains("<!DOCTYPE"),
                    "index.html do bundle: " + root.body.substring(0, Math.min(80, root.body.length())));

            HttpResult css = get(port, "/static/app.css");
            assertEquals(200, css.code, "GET /static/app.css (via KOF_STATIC_OUT)");
            assertTrue(css.body.contains("--accent"), "css servido: " + css.body);

            HttpResult traversal = get(port, "/..%2fsrc%2fMain.kf");
            assertEquals(404, traversal.code, "path traversal bloqueado (R6)");
            assertFalse(traversal.body.contains("web.app()"), "fonte do app não vaza");
        } finally {
            server.destroy();
            if (!server.waitFor(5, TimeUnit.SECONDS)) server.destroyForcibly();
        }
    }

    @Test
    void monolithBuild_unaffected(@TempDir Path tmp) throws Exception {
        Path app = tmp.resolve("mono");
        Files.createDirectories(app);
        Files.writeString(app.resolve("Main.kf"), "main() { println(6 * 7) }\n");
        Process p = startCli(app, "build", ".", "--output", "out");
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(120, TimeUnit.SECONDS), "build timeout\n" + out);
        assertEquals(0, p.exitValue(), "build monólito rc\n" + out);
        assertTrue(Files.exists(app.resolve("out/Default/Main.class")), "monólito compila: " + out);
        assertFalse(Files.exists(app.resolve("out/frontend")), "sem web/ → sem frontend (zero regressão)");
    }
}
