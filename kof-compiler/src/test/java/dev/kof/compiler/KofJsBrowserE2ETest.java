package dev.kof.compiler;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * KofJS em um browser real (Chrome headless) — o alvo que o KofJS existe para
 * cobrir além do GraalJS embutido. Compila um programa kof.ui para JS, serve o
 * diretório de saída por HTTP local (módulos ESM não carregam via file://) e
 * captura o DOM com {@code google-chrome --headless --dump-dom}, afirmando que
 * a janela e os widgets renderizaram de verdade no DOM do browser.
 *
 * Pula (assume) quando nenhum Chrome/Chromium está instalado.
 */
class KofJsBrowserE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static Path findChrome() {
        List<String> candidates = List.of(
                "google-chrome", "google-chrome-stable", "chromium", "chromium-browser");
        String pathEnv = System.getenv("PATH");
        for (String name : candidates) {
            if (pathEnv == null) break;
            for (String dir : pathEnv.split(java.util.regex.Pattern.quote(String.valueOf(java.io.File.pathSeparatorChar)))) {
                Path p = Path.of(dir, name);
                if (Files.isExecutable(p)) return p;
            }
        }
        return null;
    }

    @Test
    void uiWindowRendersInRealBrowserDom(@TempDir Path tempDir) throws IOException {
        Path chrome = findChrome();
        assumeTrue(chrome != null, "Chrome/Chromium não instalado — pulando E2E de browser");

        String program = """
            main() {
                var titulo = Label("browser-ok")
                var btn = Button("clicar")
                var col = Column(listOf(titulo, btn))
                var w = Window("BrowserTest")
                w.bind(col)
                w.show()
            }
            """;
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, program);

        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "compilação JS deve passar: " + result.diagnostics().getDiagnostics());
        assertTrue(Files.exists(outDir.resolve("index.html")), "index.html gerado pelo backend JS");
        assertTrue(Files.exists(outDir.resolve("Default.mjs")), "módulo .mjs gerado pelo backend JS");

        HttpServer server = serve(outDir);
        int port = server.getAddress().getPort();
        try {
            String dom = dumpDom(chrome, "http://127.0.0.1:" + port + "/index.html");
            assertTrue(dom.contains("kof-window"), "janela kof.ui ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("browser-ok"), "label ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("kof-button"), "button kof.ui ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("clicar"), "texto do button ausente no DOM: " + excerpt(dom));
            assertFalse(dom.contains("Failed to load"), "módulo ESM falhou de carregar: " + excerpt(dom));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void inputPlaceholderRendersInRealBrowserDom(@TempDir Path tempDir) throws IOException {
        Path chrome = findChrome();
        assumeTrue(chrome != null, "Chrome/Chromium não instalado — pulando E2E de browser");

        String program = """
            main() {
                var campo = Input("")
                campo.setPlaceholder("digite aqui")
                var col = Column(listOf(campo))
                var w = Window("InputTest")
                w.bind(col)
                w.show()
            }
            """;
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, program);

        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "compilação JS deve passar: " + result.diagnostics().getDiagnostics());

        HttpServer server = serve(outDir);
        int port = server.getAddress().getPort();
        try {
            String dom = dumpDom(chrome, "http://127.0.0.1:" + port + "/index.html");
            assertTrue(dom.contains("kof-input"), "input kof.ui ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("placeholder=\"digite aqui\""),
                    "placeholder ausente no DOM: " + excerpt(dom));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void inputTypeRendersInRealBrowserDom(@TempDir Path tempDir) throws IOException {
        Path chrome = findChrome();
        assumeTrue(chrome != null, "Chrome/Chromium não instalado — pulando E2E de browser");

        String program = """
            main() {
                var senha = Input("")
                senha.setType("password")
                var col = Column(listOf(senha))
                var w = Window("InputTypeTest")
                w.bind(col)
                w.show()
            }
            """;
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, program);

        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "compilação JS deve passar: " + result.diagnostics().getDiagnostics());

        HttpServer server = serve(outDir);
        int port = server.getAddress().getPort();
        try {
            String dom = dumpDom(chrome, "http://127.0.0.1:" + port + "/index.html");
            assertTrue(dom.contains("kof-input"), "input kof.ui ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("type=\"password\""),
                    "type=password ausente no DOM: " + excerpt(dom));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void inputCheckboxCheckedRendersInRealBrowserDom(@TempDir Path tempDir) throws IOException {
        Path chrome = findChrome();
        assumeTrue(chrome != null, "Chrome/Chromium não instalado — pulando E2E de browser");

        String program = """
            main() {
                var aceitar = Input("")
                aceitar.setType("checkbox")
                aceitar.setChecked(true)
                var col = Column(listOf(aceitar))
                var w = Window("CheckboxTest")
                w.bind(col)
                w.show()
            }
            """;
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, program);

        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "compilação JS deve passar: " + result.diagnostics().getDiagnostics());

        HttpServer server = serve(outDir);
        int port = server.getAddress().getPort();
        try {
            String dom = dumpDom(chrome, "http://127.0.0.1:" + port + "/index.html");
            assertTrue(dom.contains("type=\"checkbox\""),
                    "type=checkbox ausente no DOM: " + excerpt(dom));
            assertTrue(dom.contains("checked"),
                    "checked ausente no DOM: " + excerpt(dom));
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer serve(Path dir) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/")) path = "/index.html";
            Path file = dir.resolve(path.substring(1)).normalize();
            if (!file.startsWith(dir) || !Files.isRegularFile(file)) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            byte[] body = Files.readAllBytes(file);
            String name = file.getFileName().toString().toLowerCase();
            String mime = name.endsWith(".html") ? "text/html; charset=utf-8"
                    : name.endsWith(".mjs") || name.endsWith(".js") ? "text/javascript; charset=utf-8"
                    : "application/octet-stream";
            exchange.getResponseHeaders().set("Content-Type", mime);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static String dumpDom(Path chrome, String url) throws IOException {
        // --virtual-time-budget: o módulo ESM é deferred — dá tempo virtual
        // para o script carregar e o kof.ui injetar os nós antes do dump.
        ProcessBuilder pb = new ProcessBuilder(
                chrome.toString(), "--headless=new", "--no-sandbox", "--disable-gpu",
                "--dump-dom", "--virtual-time-budget=8000", url);
        pb.redirectErrorStream(false);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        try {
            int code = p.waitFor();
            assertEquals(0, code, "chrome --dump-dom falhou: " + excerpt(out));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrompido no chrome headless", e);
        }
        return out;
    }

    private static String excerpt(String s) {
        if (s == null) return "<nulo>";
        return s.length() > 600 ? s.substring(0, 600) + "…" : s;
    }
}
