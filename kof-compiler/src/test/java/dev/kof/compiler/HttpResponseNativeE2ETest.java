package dev.kof.compiler;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * known-bugs §1198 — no nativo x86-64, `http.send(...)` (a forma idiomática de
 * ler status e corpo juntos, que o JVM já tinha) falhava no LINK: os símbolos
 * `kof_http_send_response`/`kof_http_response_status`/`kof_http_response_body`
 * não eram emitidos.
 *
 * <p>RED-first: com o runtime ausente o `ld` recusa ("referência não definida");
 * com ele, o status e o corpo de uma resposta local saem iguais ao JVM.
 */
class HttpResponseNativeE2ETest {

    private static HttpServer startServer(int port) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/me", ex -> {
            byte[] body = "{\"code\":\"UNAUTHENTICATED\",\"message\":\"Sessao invalida ou expirada.\"}"
                    .getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(401, body.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(body); }
        });
        server.createContext("/ok", ex -> {
            byte[] body = "{\"username\":\"mel\",\"name\":\"Mel Santos\"}".getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, body.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(body); }
        });
        server.start();
        return server;
    }

    @Test
    void httpSendStatusAndBodyWorkOnNative(@TempDir Path dir) throws Exception {
        assumeTrue(NativeToolchainAssumptions.hasTool("as", "ld"), "native toolchain ausente");
        int port;
        try (var probe = new java.net.ServerSocket(0)) { port = probe.getLocalPort(); }
        HttpServer server = startServer(port);
        try {
            Files.writeString(dir.resolve("Main.kf"), """
                    main() {
                        val unauth = http.send("GET", "http://127.0.0.1:PORT/me", null, "Origin: https://enois.ia.br")
                        println("status=" + unauth.status())
                        println("code=" + unauth.body())
                        val ok = http.send("GET", "http://127.0.0.1:PORT/ok", null, null)
                        println("ok=" + ok.status() + " " + ok.body())
                    }
                    """.replace("PORT", String.valueOf(port)), StandardCharsets.UTF_8);
            var r = new CompilerDriver().compile(dir.resolve("Main.kf"), dir.resolve("out"), Target.NATIVE);
            assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
            var pb = new ProcessBuilder(dir.resolve("out/Default/Main").toString()).redirectErrorStream(true);
            var p = pb.start();
            String out = new String(p.getInputStream().readAllBytes()).strip();
            assertTrue(p.waitFor(120, TimeUnit.SECONDS), "termina: " + out);
            assertEquals(0, p.exitValue(), "saida limpa: " + out);
            assertTrue(out.contains("status=401"), "o 401 do backend vira status lido: " + out);
            assertTrue(out.contains("UNAUTHENTICATED"), "o corpo do 401 e legivel: " + out);
            assertTrue(out.contains("ok=200 {\"username\":\"mel\",\"name\":\"Mel Santos\"}"),
                    "o 200 devolve status e corpo: " + out);
        } finally {
            server.stop(0);
        }
    }
}
