package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WEB001-T1 (13/09): o servidor web Kof roda no target JS (GraalJS hostless).
 *
 * Prova E2E real: compila um programa Kof com rotas ({@code app.get/post +
 * app.listen(port)}) para Target.JS, executa via {@link dev.kof.runtime.KofJsRunner}
 * (thread daemon — listen é bloqueante, idem JVM) e exercita as rotas por
 * sockets HTTP reais.
 *
 * Bugs de runtime cobertos aqui (regressão):
 * <ul>
 *   <li>Context GraalJS é thread-confined — handler HTTP implementado em JS
 *       morre silenciosamente na thread do dispatcher; o dispatcher agora é
 *       Java puro ({@code KofJsWebQueue}) e o request é processado na main
 *       thread (event-loop em kofWebListen).</li>
 *   <li>O RETORNO do handler é o body 200 (idem JVM JvmRuntimeWebDispatch);
 *       antes era descartado (empty reply).</li>
 *   <li>Rota com {@code :param}: HttpServer não tem matching de params — o
 *       createContext registra o PREFIXO estático e o match real acontece no
 *       dispatch (chave "METHOD:path" não pode ser quebrada em ":" dentro do
 *       path, ex.: "GET:/u/:id").</li>
 *   <li>UTF-8: encoder puro JS ({@code kofWebUtf8Bytes}) — interop de
 *       INSTÂNCIA Java host (String.getBytes) não expõe métodos nesta build;
 *       statics e Java.to funcionam.</li>
 * </ul>
 */
class KofWebJsE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String WEB_APP = """
            main() {
                var app = web.app()
                app.get("/hello") {
                    return "Hello from Kof"
                }
                app.get("/users/:id") {
                    return "user " + param("id") + " q=" + query("name")
                }
                app.get("/me") {
                    return method() + " " + path()
                }
                app.post("/echo") {
                    return "got:" + body()
                }
                app.listen(PORT)
            }
            """;

    private int freePort() throws IOException {
        try (ServerSocket probe = new ServerSocket(0)) {
            return probe.getLocalPort();
        }
    }

    private static Path findJsEntry(Path dir) throws IOException {
        try (var s = Files.walk(dir)) {
            var opt = s.filter(p -> p.getFileName().toString().equals("Default.mjs")).findFirst();
            if (opt.isPresent()) return opt.get();
        }
        try (var s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(".mjs"))
                    .findFirst().orElseThrow(() -> new IOException("no .mjs in " + dir));
        }
    }

    /** HTTP/1.0 request mínimo — lê a resposta inteira e fecha. */
    private String request(int port, String raw) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(10000);
            OutputStream out = socket.getOutputStream();
            out.write(raw.getBytes(StandardCharsets.UTF_8));
            out.flush();
            InputStream in = socket.getInputStream();
            StringBuilder response = new StringBuilder();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) {
                response.append(new String(buffer, 0, n, StandardCharsets.UTF_8));
            }
            return response.toString();
        }
    }

    private static String bodyOf(String rawResponse) {
        int idx = rawResponse.indexOf("\r\n\r\n");
        return idx >= 0 ? rawResponse.substring(idx + 4) : rawResponse;
    }

    @Test
    void jsWebServesRoutes(@TempDir Path tempDir) throws Exception {
        int port = freePort();
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, WEB_APP.replace("PORT", String.valueOf(port)));
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "compilação JS web deve suceder: "
                + result.diagnostics().getDiagnostics());

        // listen é bloqueante (idem JVM) — roda o runner em thread daemon.
        // §176b: o stderr do runner é CAPTURADO e anexado às asserções —
        // sem isto, morte do listener virava "porta não abriu" às cegas.
        java.io.ByteArrayOutputStream serverErr = new java.io.ByteArrayOutputStream();
        Thread serverThread = new Thread(() -> {
            try {
                dev.kof.runtime.KofJsRunner.run(findJsEntry(outDir),
                        java.io.OutputStream.nullOutputStream(),
                        java.io.InputStream.nullInputStream(),
                        serverErr);
            } catch (Exception ignored) {
                // o socket fechar no fim do teste interrompe o accept/poll
            }
        }, "kof-web-js-e2e");
        serverThread.setDaemon(true);
        serverThread.start();

        // espera o server abrir a porta
        boolean listening = TestServerFixture.awaitPort(port, 50, 100);
        assertTrue(listening, "server JS não abriu a porta " + port
                + " | stderr do runner: " + serverErr.toString(StandardCharsets.UTF_8));

        String hello = request(port, "GET /hello HTTP/1.0\r\nHost: x\r\n\r\n");
        assertTrue(hello.startsWith("HTTP/1.1 200") || hello.startsWith("HTTP/1.0 200"), hello);
        assertEquals("Hello from Kof", bodyOf(hello).trim(), "rota exata: " + hello);

        String param = request(port, "GET /users/42?name=mel HTTP/1.0\r\nHost: x\r\n\r\n");
        assertTrue(param.startsWith("HTTP/1.1 200") || param.startsWith("HTTP/1.0 200"), param);
        assertEquals("user 42 q=mel", bodyOf(param).trim(), "rota :param + query: " + param);

        // Paridade JVM x JS (§2.4/§12 pagination-plan): mesma decodificacao do
        // valor no JVM (`%20` -> espaco, `%2B` -> `+`).
        String enc = request(port, "GET /users/42?name=a%20b%2Bc HTTP/1.0\r\nHost: x\r\n\r\n");
        assertEquals("user 42 q=a b+c", bodyOf(enc).trim(), "query decode: " + enc);

        String me = request(port, "GET /me HTTP/1.0\r\nHost: x\r\n\r\n");
        assertEquals("GET /me", bodyOf(me).trim(), "method()+path(): " + me);

        String echo = request(port, "POST /echo HTTP/1.0\r\nHost: x\r\nContent-Length: 5\r\n\r\nhello");
        assertTrue(echo.startsWith("HTTP/1.1 200") || echo.startsWith("HTTP/1.0 200"), echo);
        assertEquals("got:hello", bodyOf(echo).trim(), "POST body: " + echo);
    }

    // §265 (JS, 16/09 lane .18): `status()`/`headerSet()` dentro de um handler
    // web SILENCIOSAMENTE não faziam nada no JS — `kofWebStatus` lia
    // `kofWebRequest.response`, campo que `kofWebRequest` nunca teve, e o guard
    // engolia a chamada (R6). O handler `return status(201, body)` dava 200, não
    // 201, e o `headerSet("X","y")` não chegava na resposta. Fix = deferir
    // status/header para o pump aplicar no envio (idem JVM). Prova: esta célula
    // (VERMELHO pré-fix: 200 sem X-Custom).
    @Test
    void jsWebStatusAndHeaderReachTheWire(@TempDir Path tempDir) throws Exception {
        int port = freePort();
        Path source = tempDir.resolve("StatusApp.kf");
        Files.writeString(source, """
                main() {
                    var app = web.app()
                    app.get("/created") {
                        headerSet("X-Custom", "abc")
                        return status(201, "made")
                    }
                    app.get("/plain") {
                        return "ok"
                    }
                    app.listen(PORT)
                }
                """.replace("PORT", String.valueOf(port)));
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "compilação JS web deve suceder: "
                + result.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream serverErr = new java.io.ByteArrayOutputStream();
        Thread serverThread = new Thread(() -> {
            try {
                dev.kof.runtime.KofJsRunner.run(findJsEntry(outDir),
                        java.io.OutputStream.nullOutputStream(),
                        java.io.InputStream.nullInputStream(), serverErr);
            } catch (Exception ignored) {
            }
        }, "kof-web-js-status");
        serverThread.setDaemon(true);
        serverThread.start();
        boolean listening = TestServerFixture.awaitPort(port, 50, 100);
        assertTrue(listening, "server JS não abriu a porta " + port
                + " | stderr: " + serverErr.toString(StandardCharsets.UTF_8));

        String created = request(port, "GET /created HTTP/1.0\r\nHost: x\r\n\r\n");
        assertTrue(created.startsWith("HTTP/1.1 201") || created.startsWith("HTTP/1.0 201"),
                "status(201) deve chegar na linha de status: " + created);
        assertTrue(created.toLowerCase().contains("x-custom: abc"),
                "headerSet(X-Custom) deve chegar nos headers: " + created);
        assertEquals("made", bodyOf(created).trim(), "body do status(201): " + created);

        String plain = request(port, "GET /plain HTTP/1.0\r\nHost: x\r\n\r\n");
        assertTrue(plain.startsWith("HTTP/1.1 200") || plain.startsWith("HTTP/1.0 200"),
                "handler sem status() default 200: " + plain);
        assertEquals("ok", bodyOf(plain).trim(), "body default: " + plain);
    }

    // ── 16/09 (WEB001 fatia honestidade): context-fns web sem runtime no JS
    //    baixavam para kofWebStub e retornavam 0 em silêncio (R6). SSE ganhou
    //    runtime handler-scoped no host JS (16/09) e saiu da lista; restam
    //    wsSend/wsMessage/stats (WEB004/WEB001). ──
    @Test
    void jsUnsupportedContextFnsReportGapAtCompile(@TempDir Path tempDir) throws IOException {
        // aridade exata de cada context-fn (KofWeb.contextCall) — chamada
        // com aridade errada não chega ao gate (resolve null antes).
        String[] fns = {"wsSend", "wsMessage", "stats"};
        String[] calls = {"wsSend(\"hello\")", "wsMessage()", "stats(\"reqs\")"};
        for (int i = 0; i < fns.length; i++) {
            Path source = tempDir.resolve(fns[i] + ".kf");
            Files.writeString(source, """
                main() {
                    var app = web.app()
                    app.get("/x") {
                        %s
                        return "ok"
                    }
                }
                """.replace("%s", calls[i]));
            CompilationResult r = driver.compile(source, tempDir.resolve(fns[i] + "-out"), Target.JS);
            assertFalse(r.success(), fns[i] + " no JS deve falhar em compile (gap), não compilar p/ kofWebStub");
            assertTrue(r.diagnostics().getDiagnostics().toString().matches("(?s).*WEB00[134].*"),
                    fns[i] + " deve reportar WEB00x: " + r.diagnostics().getDiagnostics());
        }
    }

    @Test
    void jsSupportedContextFnsStillCompile(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var app = web.app()
                app.get("/users/:id") {
                    var u = param("id")
                    var n = query("name")
                    var h = header("x-a")
                    var b = body()
                    var m = method()
                    var p = path()
                    return u + n + h + b + m + p
                }
            }
            """);
        CompilationResult r = driver.compile(source, tempDir.resolve("out"), Target.JS);
        assertTrue(r.success(), "context-fns com runtime no JS devem compilar: "
                + r.diagnostics().getDiagnostics());
        assertFalse(r.diagnostics().getDiagnostics().toString().contains("WEB001"),
                r.diagnostics().getDiagnostics().toString());
    }

    // ── 16/09 (WEB001 residual — SSE no host GraalJS, HANDLER-SCOPED) ──
    // O pump JS é single-thread: o stream SSE vive DURANTE o corpo do handler
    // e fecha no retorno dele (push pós-return = WEB003 residual). Forma
    // provada: contexto-fn sse(text) e o objeto injetado no handler
    // (app.sse(path) { sse.send(...) }) — idem stdlib-web/JVM framing.
    private static final String SSE_APP = """
            main() {
                var app = web.app()
                app.sse("/events") {
                    HANDLER
                }
                app.listen(PORT)
            }
            """;

    private int startSseServer(Path tempDir, String handlerBody) throws Exception {
        int port = freePort();
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, SSE_APP.replace("PORT", String.valueOf(port))
                .replace("HANDLER", handlerBody));
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "compilação JS sse deve suceder: "
                + result.diagnostics().getDiagnostics());
        java.io.ByteArrayOutputStream serverErr = new java.io.ByteArrayOutputStream();
        java.io.ByteArrayOutputStream serverOut = new java.io.ByteArrayOutputStream();
        sseServerOut = serverOut;
        Thread serverThread = new Thread(() -> {
            try {
                dev.kof.runtime.KofJsRunner.run(findJsEntry(outDir),
                        serverOut, java.io.InputStream.nullInputStream(), serverErr);
            } catch (Exception ignored) {
            }
        }, "kof-sse-js-" + port);
        serverThread.setDaemon(true);
        serverThread.start();
        if (!TestServerFixture.awaitPort(port, 50, 100)) {
            if (!serverThread.isAlive()) {
                throw new IOException("runner MORREU | out: [" + serverOut.toString(StandardCharsets.UTF_8)
                        + "] err: [" + serverErr.toString(StandardCharsets.UTF_8) + "]");
            }
            throw new IOException("server SSE JS não abriu a porta " + port
                    + " | out: [" + serverOut.toString(StandardCharsets.UTF_8)
                    + "] err: [" + serverErr.toString(StandardCharsets.UTF_8) + "]");
        }
        return port;
    }

    private java.io.ByteArrayOutputStream sseServerOut;

    /**
     * Resposta SSE crua (HTTP/1.1 + Connection: close — o HttpServer do host
     * mantém keep-alive aberto após o stream, sem close o read travaria).
     * De-chunks quando o transporte chunkou; corpo raw caso contrário.
     */
    private String[] sseRaw(int port) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5000);
            socket.getOutputStream().write(
                    ("GET /events HTTP/1.1\r\nHost: 127.0.0.1\r\n"
                            + "Accept: text/event-stream\r\nConnection: close\r\n\r\n")
                            .getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            InputStream in = socket.getInputStream();
            java.io.ByteArrayOutputStream all = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) all.write(buf, 0, n);
            byte[] raw = all.toByteArray();
            int sep = indexOfSeq(raw, new byte[]{'\r', '\n', '\r', '\n'}, 0);
            String head = new String(raw, 0, sep, StandardCharsets.UTF_8);
            byte[] body = java.util.Arrays.copyOfRange(raw, sep + 4, raw.length);
            String out = new String(body, StandardCharsets.UTF_8);
            if (!head.toLowerCase().contains("transfer-encoding: chunked")) {
                return new String[]{head, out};
            }
            StringBuilder de = new StringBuilder();
            int p = 0;
            while (p < body.length) {
                int lineEnd = indexOfSeq(body, new byte[]{'\r', '\n'}, p);
                if (lineEnd < 0) break;
                String sizeToken = new String(body, p, lineEnd - p, StandardCharsets.UTF_8).trim();
                // §258/#777: um tamanho de chunk nao-hex (resposta malformada)
                // chutaria NumberFormatException solta no meio do oraculo —
                // robustez do harness de teste, nunca um false-green silencioso.
                if (sizeToken.isEmpty() || !sizeToken.matches("(?i)[0-9a-f]+")) break;
                // §258/#780: o guard hex acima ACEITA tokens que estouram o
                // Integer.parseInt radix-16 — nao so >8 digitos (0x100000000),
                // mas qualquer valor acima do MAX_INT (0x80000000/0xffffffff,
                // 8 digitos, medido: "under radix 16"). Um tamanho desses NUNCA
                // e um chunk valido (o corpo ja vem estourado no buffer); tratar
                // como malformado => break, em vez do NFE solto.
                int size;
                try {
                    size = Integer.parseInt(sizeToken, 16);
                } catch (NumberFormatException malformed) {
                    break;
                }
                if (size == 0) break;
                de.append(new String(body, lineEnd + 2, size, StandardCharsets.UTF_8));
                p = lineEnd + 2 + size + 2;
            }
            return new String[]{head, de.toString()};
        }
    }

    private static int indexOfSeq(byte[] data, byte[] seq, int from) {
        outer:
        for (int i = from; i <= data.length - seq.length; i++) {
            for (int j = 0; j < seq.length; j++) {
                if (data[i + j] != seq[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private String[] sseBody(int port) throws IOException {
        String[] resp = sseRaw(port);
        if (resp[1].isEmpty()) {
            fail("corpo SSE vazio | runner stdout: ["
                    + sseServerOut.toString(StandardCharsets.UTF_8)
                    + "] | head: [" + resp[0].replace("\r", "~") + "]");
        }
        return resp;
    }

    @Test
    void jsSseSingleDataEvent(@TempDir Path tempDir) throws Exception {
        int port = startSseServer(tempDir, "sse(\"hello\")");
        String[] resp = sseBody(port);
        String head = resp[0].toLowerCase();
        assertTrue(resp[0].contains("200"), resp[0]);
        // O com.sun HttpServer canonicaliza o CASE dos header names
        // ("x-accel-buffering" vs. o raw-socket JVM "X-Accel-Buffering") —
        // clients SSE são case-insensitive por spec; o VALOR importa.
        assertTrue(head.contains("content-type: text/event-stream"), resp[0]);
        assertTrue(head.contains("cache-control: no-cache"), resp[0]);
        assertTrue(head.contains("x-accel-buffering: no"), resp[0]);
        assertEquals("data: hello\n\n", resp[1], "framing do evento único");
    }

    @Test
    void jsSseMemberSendNamedAndMultiLine(@TempDir Path tempDir) throws Exception {
        int port = startSseServer(tempDir, """
            sse.send("one")
            sse.event("tick", "hello")
            sse.send("a\\nb")
            """);
        String[] resp = sseBody(port);
        assertEquals("data: one\n\nevent: tick\ndata: hello\n\ndata: a\ndata: b\n\n",
                resp[1], "framing send/event/multi-line");
    }

    @Test
    void jsSseHandlerScopedAndClosesAfterReturn(@TempDir Path tempDir) throws Exception {
        // o stream fecha no retorno do handler (handler-scoped): a resposta é
        // lida inteira e EOF chega sem hang — prova de que o pump não trava.
        int port = startSseServer(tempDir, """
            sse.send("a")
            sse.send("b")
            assert(sse.isOpen())
            """);
        String[] resp = sseBody(port);
        assertEquals("data: a\n\ndata: b\n\n", resp[1]);
    }
}
