package dev.kof.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Opens a kof.ui window in the system webview (or the default browser as
 * fallback). The webview runs the real application, not a snapshot: the
 * compiled module and the runtimes are copied next to a fresh index.html, so
 * DOM events (button clicks) execute inside the WebKit page itself.
 */
final class KofJsWebview {

    private KofJsWebview() {}

    static void openInWebview(Path moduleFile, String html) throws IOException {
        Path appDir = Files.createTempDirectory("kof-ui-");
        Files.writeString(appDir.resolve("kof-ui.html"), html);
        String entry = moduleFile.getFileName().toString();
        Files.writeString(appDir.resolve(entry), Files.readString(moduleFile));
        Path runtime = moduleFile.resolveSibling("kof-runtime.mjs");
        if (Files.exists(runtime)) {
            Files.copy(runtime, appDir.resolve("kof-runtime.mjs"));
        }
        Path ioRuntime = moduleFile.resolveSibling("kof-runtime-io.mjs");
        if (Files.exists(ioRuntime)) {
            Files.copy(ioRuntime, appDir.resolve("kof-runtime-io.mjs"));
        }
        String page = "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n  <meta charset=\"utf-8\">\n"
                + "  <title>Kof</title>\n  <style>\n"
                + "    body { margin: 0; font-family: system-ui, sans-serif; }\n"
                + "    #kof-root { display: flex; flex-direction: column; gap: 8px;\n"
                + "                 padding: 16px; min-height: 100vh; box-sizing: border-box; }\n"
                + "    .kof-label { font-size: 16px; }\n"
                + "    .kof-button { font-size: 16px; padding: 8px 16px; cursor: pointer; }\n"
                + "    .kof-input { font-size: 16px; padding: 6px 10px; }\n"
                + "    .kof-column { display: flex; flex-direction: column; gap: 8px; }\n"
                + "    .kof-row { display: flex; flex-direction: row; gap: 8px; align-items: center; }\n"
                + "    .kof-view { box-sizing: border-box; }\n"
                + "    .kof-window { box-sizing: border-box; padding: 16px; border-radius: 8px;\n"
                + "      border: 1px solid #ccc; display: flex; flex-direction: column; gap: 8px; }\n"
                + "  </style>\n</head>\n<body>\n  <div id=\"kof-root\"></div>\n"
                + "  <script type=\"module\" src=\"" + entry + "\"></script>\n</body>\n</html>\n";
        Files.writeString(appDir.resolve("index.html"), page);
        Path pagePath = appDir.resolve("index.html").toAbsolutePath();
        Path shim = findWebviewShim();
        if (shim != null) {
            try {
                // O webview WebKit/GTK habilita acesso a módulos ESM via file://
                // (webkit_settings_set_allow_file_access_from_file_urls) — o
                // caminho file:// funciona aqui. `kof run` fica vivo até a janela
                // fechar, como um aplicativo desktop.
                Process webview = new ProcessBuilder(shim.toString(), pagePath.toString()).start();
                try {
                    webview.waitFor();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return;
            } catch (IOException e) {
                System.err.println("kof: native webview failed (" + e.getMessage() + ") — falling back");
            }
        }
        // Navegador de sistema (Chrome/Firefox): módulos ESM NÃO carregam via
        // file:// (CORS) — servir o appDir por HTTP em 127.0.0.1 e abrir a URL.
        serveAndOpen(appDir);
    }

    /**
     * Serve {@code appDir} por HTTP em 127.0.0.1 (porta efêmera) e abre o
     * navegador de sistema. Mantém o servidor vivo pelo tempo de vida do
     * processo (Ctrl-C encerra) — como um dev-server local.
     */
    private static void serveAndOpen(Path appDir) {
        com.sun.net.httpserver.HttpServer server;
        try {
            server = com.sun.net.httpserver.HttpServer.create(
                    new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            System.err.println("kof: failed to start local server: " + e.getMessage());
            return;
        }
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/")) path = "/index.html";
            Path file = appDir.resolve(path.substring(1)).normalize();
            if (!file.startsWith(appDir) || !Files.isRegularFile(file)) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            try {
                byte[] body = Files.readAllBytes(file);
                exchange.getResponseHeaders().set("Content-Type", mimeOf(file.getFileName().toString()));
                exchange.sendResponseHeaders(200, body.length);
                try (java.io.OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            } catch (IOException e) {
                exchange.close();
            }
        });
        server.start();
        int port = server.getAddress().getPort();
        String url = "http://127.0.0.1:" + port + "/";
        System.err.println("kof: window at " + url + " (Ctrl-C para encerrar)");
        openInSystemBrowser(url);
        try {
            // mantém o processo (e o servidor) vivo — encerra no Ctrl-C
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        server.stop(0);
    }

    private static void openInSystemBrowser(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported()
                    && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
                return;
            }
        } catch (Exception ignored) {
        }
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start();
            } else if (os.contains("mac")) {
                new ProcessBuilder("open", url).start();
            } else {
                new ProcessBuilder("xdg-open", url).start();
            }
        } catch (IOException ignored) {
            System.err.println("kof: open " + url + " to view the window");
        }
    }

    private static String mimeOf(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".html") || n.endsWith(".htm")) return "text/html; charset=utf-8";
        if (n.endsWith(".mjs") || n.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (n.endsWith(".css")) return "text/css; charset=utf-8";
        if (n.endsWith(".map") || n.endsWith(".json")) return "application/json; charset=utf-8";
        return "application/octet-stream";
    }

    private static Path findWebviewShim() {
        String install = System.getProperty("kof.install.dir", "");
        if (!install.isEmpty()) {
            Path p = Path.of(install, "bin", "kof-webview");
            if (Files.isExecutable(p)) return p;
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("linux")) return null;
        Path p = Path.of("bin", "kof-webview");
        if (Files.isExecutable(p)) return p.toAbsolutePath();
        return null;
    }
}
