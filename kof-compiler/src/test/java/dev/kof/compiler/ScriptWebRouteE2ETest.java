package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * §574: web route handlers sob o interpretador — o servidor Script binda e
 * aceita, mas cada rota respondia HTTP 500 porque o dispatch do runtime
 * gerado chama o handler por reflexão host ("invoke") e o closure
 * interpretado é KofObj (sem método host). A ponte é InterpretedCallable
 * instalada na fronteira runtime do interpretador (kof_web_route/_opts).
 */
class ScriptWebRouteE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static int freePort() throws IOException {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    @Test
    @DisplayName("§574 Script web: interpreted route handlers answer 200 (plain + :param)")
    void interpretedWebRouteHandlersAnswer(@TempDir Path dir) throws Exception {
        int port = freePort();
        Path src = dir.resolve("ScriptWeb.kf");
        Files.writeString(src, """
                main() {
                    var app = web.app()
                    app.get("/ping") {
                        return "pong"
                    }
                    app.get("/echo/:id") {
                        return "id=" + param("id")
                    }
                    spawn {
                        app.listen(PORT)
                    }
                    var got = ""
                    var i = 0
                    while (i < 100) {
                        try {
                            got = http.get("http://127.0.0.1:PORT/ping")
                            if (got == "pong") {
                                i = 100
                            }
                        } catch (String e) {
                            i = i + 1
                        }
                    }
                    println("plain=" + (got == "pong"))
                    var echoed = http.get("http://127.0.0.1:PORT/echo/abc")
                    println("param=" + (echoed == "id=abc"))
                    app.close()
                    println("SCRIPTWEB-OK")
                }
                """.replace("PORT", String.valueOf(port)));
        KofInterpreter.Result r = driver.interpret(List.of(src), dir, new String[0]);
        assertEquals(0, r.exitCode(), "script exit: " + r.stderr());
        assertTrue(r.stdout().contains("plain=true"), "handler 500 (regression §574): " + r.stdout() + r.stderr());
        assertTrue(r.stdout().contains("param=true"), "param route broken: " + r.stdout());
        assertTrue(r.stdout().contains("SCRIPTWEB-OK"), r.stdout());
    }
}
