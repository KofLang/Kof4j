package dev.kof.compiler.jvm;
import dev.kof.compiler.Type;

/**
 * Servidor web do runtime JVM: listen/listenSecure/ssl/handle/ws-handshake.
 * Extraído de JvmRuntime.source (REFACTOR-500 Fase 5) — fragmento de source
 * do KofRuntime gerado; concatenação preserva ordem e conteúdo byte-a-byte.
 */
public final class JvmRuntimeWebServer {

    private JvmRuntimeWebServer() {}

    static String source() {
        return """
                public static void kof_web_listen(String appId, int port) {
                    WebApp app = kof_web_app(appId);
                    if (app.serverSocket != null) {
                        throw new IllegalStateException("app already listening: " + appId);
                    }
                    try {
                        app.serverSocket = new java.net.ServerSocket(port, 64,
                                java.net.InetAddress.getByName("0.0.0.0"));
                    } catch (java.io.IOException e) {
                        throw new RuntimeException("cannot bind port " + port + ": " + e.getMessage(), e);
                    }
                    app.running = true;
                    kof_web_warn_security_default(app);
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> kof_web_close(appId)));
                    while (app.running) {
                        try {
                            java.net.Socket client = app.serverSocket.accept();
                            // 15s default protects HTTP `readRequest` against
                            // client half-open sockets. WS raises this after
                            // the handshake.
                            client.setSoTimeout(15000);
                            Thread.startVirtualThread(() -> kof_web_handle(app, client));
                        } catch (java.io.IOException e) {
                            if (!app.running) break;
                        }
                    }
                }

                private static final java.util.concurrent.ExecutorService KOF_SSE_HANDLERS =
                        java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor();

                private static void kof_web_handle(WebApp app, java.net.Socket client) {
                    if (app.activeConnections.incrementAndGet() > app.maxConnections) {
                        app.activeConnections.decrementAndGet();
                        try (client) {
                            client.getOutputStream().write(("HTTP/1.1 503 Service Unavailable\\r\\n"
                                    + "Content-Length: 0\\r\\n"
                                    + "Connection: close\\r\\n"
                                    + "\\r\\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            client.getOutputStream().flush();
                            // Drain the rejected request so close sends FIN, not RST.
                            client.setSoTimeout(200);
                            byte[] drain = new byte[4096];
                            try {
                                while (client.getInputStream().read(drain) >= 0) {
                                }
                            } catch (java.net.SocketTimeoutException ignored) {
                            }
                        } catch (java.io.IOException ignored) {
                        }
                        return;
                    }
                    try {
                        try (client) {

                            java.io.InputStream in = new java.io.BufferedInputStream(client.getInputStream());
                            String remote = client.getInetAddress() == null
                                    ? null : client.getInetAddress().getHostAddress();
                            boolean idle = false;
                            while (true) {
                                WebRequest req = readRequest(in, remote,
                                        client instanceof javax.net.ssl.SSLSocket, idle);
                                if (req == null) return;
                                if (kof_web_serve(app, req, client)) return;
                                idle = true;
                                client.setSoTimeout(KEEP_ALIVE_IDLE_MS);
                            }
                        } catch (Exception e) {
                            System.err.println("kof web connection error: " + e.getMessage());
                        }
                    } finally {
                        app.activeConnections.decrementAndGet();
                    }
                }

                // A persistent connection waits this long for its next request.
                private static final int KEEP_ALIVE_IDLE_MS = 5000;

                /** Serves one request; true when the connection was taken over (SSE/WS) or must close. */
                private static boolean kof_web_serve(WebApp app, WebRequest req, java.net.Socket client)
                        throws Exception {
                    WebDispatchResult result = kof_web_dispatch(app, req);
                    if (result.kind == RouteKind.SSE) {
                        java.io.OutputStream out = client.getOutputStream();
                        out.write(("HTTP/1.1 200 OK\\r\\n"
                                + "Content-Type: text/event-stream\\r\\n"
                                + "Cache-Control: no-cache\\r\\n"
                                + "Connection: keep-alive\\r\\n"
                                + "X-Accel-Buffering: no\\r\\n"
                                + (result.response != null ? result.response : "")
                                + "\\r\\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        out.flush();
                        SseConnection sse = new SseConnection(out);
                        SSE_CONNECTIONS_ACTIVE.incrementAndGet();
                        try {
                            java.util.concurrent.Future<?> handler = KOF_SSE_HANDLERS.submit(() -> {
                                try {
                                    KOF_WEB_REQUEST.set(req);
                                    KOF_SSE_SENDER.set(sse);
                                    try {
                                        kof_web_invoke(result.route.handler, sse);
                                    } finally {
                                        KOF_SSE_SENDER.remove();
                                        KOF_WEB_REQUEST.remove();
                                    }
                                } catch (Exception e) {
                                    System.err.println("kof web sse handler error: "
                                            + e.getMessage());
                                } finally {
                                    sse.close();
                                }
                            });
                            try {
                                handler.get(KofRuntime.idleMs.get() * 4L,
                                        java.util.concurrent.TimeUnit.MILLISECONDS);
                            } catch (java.util.concurrent.TimeoutException timeout) {
                                System.err.println("kof web sse handler timeout");
                                sse.close();
                                handler.cancel(true);
                            }
                        } finally {
                            SSE_CONNECTIONS_ACTIVE.decrementAndGet();
                        }
                        return true;
                    }
                    if (result.kind == RouteKind.WS) {
                        kof_web_ws_handshake(result, req, client);
                        return true;
                    }
                    boolean keepAlive = app.running && kof_web_keep_alive(req);
                    String response = result.response;
                    int headEnd = response.indexOf("\\r\\n\\r\\n");
                    if (headEnd >= 0) {
                        response = response.substring(0, headEnd + 2)
                                + (keepAlive ? "Connection: keep-alive\\r\\n" : "Connection: close\\r\\n")
                                + response.substring(headEnd + 2);
                    }
                    java.io.OutputStream out = client.getOutputStream();
                    out.write(response.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    if (result.body() != null) {
                        // arquivo estático: head na response + bytes crus
                        out.write(result.body());
                    }
                    out.flush();
                    return !keepAlive;
                }

                /**
                 * RFC 9112 §9.3: HTTP/1.1 is persistent unless `Connection: close`;
                 * HTTP/1.0 only with `Connection: keep-alive`. A request with
                 * Transfer-Encoding is not framed by readRequest, so it closes.
                 */
                private static boolean kof_web_keep_alive(WebRequest req) {
                    if (req.headers.containsKey("transfer-encoding")) return false;
                    String conn = req.headers.getOrDefault("connection", "").toLowerCase();
                    java.util.List<String> tokens = java.util.Arrays.stream(conn.split(","))
                            .map(String::trim).toList();
                    if (tokens.contains("close")) return false;
                    int eol = req.rawHeaders.indexOf("\\r\\n");
                    String requestLine = eol >= 0 ? req.rawHeaders.substring(0, eol) : req.rawHeaders;
                    return requestLine.endsWith("HTTP/1.1") || tokens.contains("keep-alive");
                }

                /**
                 * Reads exactly one request (headers, then Content-Length body bytes),
                 * leaving any pipelined bytes in {@code in}. Null when an idle
                 * persistent connection ends (EOF or idle timeout) before a new request.
                 */
                private static WebRequest readRequest(java.io.InputStream in, String remoteAddr,
                        boolean secure, boolean idle) throws java.io.IOException {
                    java.io.ByteArrayOutputStream head = new java.io.ByteArrayOutputStream();
                    int matched = 0;
                    while (matched < 4) {
                        int b;
                        try {
                            b = in.read();
                        } catch (java.net.SocketTimeoutException timeout) {
                            if (idle && head.size() == 0) return null;
                            throw timeout;
                        }
                        if (b == -1) {
                            if (idle && head.size() == 0) return null;
                            throw new java.io.IOException("connection closed before headers");
                        }
                        head.write(b);
                        if (head.size() > 65536) throw new java.io.IOException("headers too large");
                        matched = (b == (matched % 2 == 0 ? '\\r' : '\\n')) ? matched + 1
                                : (b == '\\r' ? 1 : 0);
                    }
                    byte[] seen = head.toByteArray();
                    String headerBlock = new String(seen, 0, seen.length - 4,
                            java.nio.charset.StandardCharsets.UTF_8);

                    int contentLength = 0;
                    for (String line : headerBlock.split("\\r\\n")) {
                        if (line.toLowerCase().startsWith("content-length:")) {
                            try {
                                contentLength = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    }
                    // O corpo é contado em BYTES (Content-Length), não em chars:
                    // um corpo UTF-8 multibyte ("Olá") tem menos chars que bytes.
                    byte[] bodyBytes = in.readNBytes(Math.max(0, contentLength));
                    String body = new String(bodyBytes, java.nio.charset.StandardCharsets.UTF_8);

                    String[] lines = headerBlock.split("\\r\\n");
                    String[] parts = lines.length > 0 ? lines[0].split(" ") : new String[0];
                    String method = parts.length > 0 ? parts[0] : "GET";
                    String fullPath = parts.length > 1 ? parts[1] : "/";
                    String path = fullPath;
                    String query = "";
                    int q = fullPath.indexOf('?');
                    if (q >= 0) {
                        path = fullPath.substring(0, q);
                        query = fullPath.substring(q + 1);
                    }
                    return new WebRequest(method, path, query, headerBlock, body, remoteAddr, secure);
                }

                private static final java.util.concurrent.atomic.AtomicInteger idleMs =
                        new java.util.concurrent.atomic.AtomicInteger(300_000);

                private static void kof_web_ws_handshake(WebDispatchResult result, WebRequest req,
                        java.net.Socket client)
                        throws java.io.IOException {
                    String upgradeVal = req.headers.get("upgrade");
                    String connectionVal = req.headers.get("connection");
                    String version = req.headers.get("sec-websocket-version");
                    String key = req.headers.get("sec-websocket-key");

                    boolean hasUpgrade = containsToken(upgradeVal, "websocket");
                    boolean hasConnection = containsToken(connectionVal, "upgrade");
                    boolean valid = hasUpgrade
                            && hasConnection
                            && "13".equals(version)
                            && key != null;

                    if (!valid) {
                        java.io.OutputStream out = client.getOutputStream();
                        out.write("HTTP/1.1 400 Bad Request\\r\\n\\r\\n"
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        out.flush();
                        return;
                    }

                    String accept = wsAccept(key);
                    java.io.OutputStream out = client.getOutputStream();
                    // O contador é incrementado ANTES do 101 chegar ao cliente:
                    // senão há race entre o handshake visível e o /stats do
                    // observador (bug 28 — flake ws_connection_counter).
                    WS_CONNECTIONS_ACTIVE.incrementAndGet();
                    try {
                        out.write(("HTTP/1.1 101 Switching Protocols\\r\\n"
                                + "Upgrade: websocket\\r\\n"
                                + "Connection: Upgrade\\r\\n"
                                + "Sec-WebSocket-Accept: " + accept + "\\r\\n"
                                + "\\r\\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        out.flush();

                        // Frame loop (PR4): handles RFC 6455 frame codec.
                        // PING -> PONG; CLOSE -> ack CLOSE; oversize -> CLOSE 1009.
                        // TEXT -> invokes the Kof handler once per message.
                        client.setSoTimeout(idleMs.get());
                        WsConnection conn = new WsConnection(client.getOutputStream());
                        frameLoop: while (true) {
                            // 1) Read until we have at least 2 bytes for the header
                            int headerBytes = 0;
                            byte[] headerBuf = new byte[2];
                            while (headerBytes < 2) {
                                int n = client.getInputStream().read(headerBuf, headerBytes, 2 - headerBytes);
                                if (n < 0) break frameLoop;
                                headerBytes += n;
                            }
                            boolean fin = (headerBuf[0] & 0x80) != 0;
                            int op = headerBuf[0] & 0x0F;
                            boolean masked = (headerBuf[1] & 0x80) != 0;
                            long plen = headerBuf[1] & 0x7F;
                            // 2) Extended length
                            if (plen == 126) {
                                byte[] ext = new byte[2];
                                readFully(client.getInputStream(), ext);
                                plen = ((long)(ext[0] & 0xFF) << 8) | (ext[1] & 0xFF);
                            } else if (plen == 127) {
                                byte[] ext = new byte[8];
                                readFully(client.getInputStream(), ext);
                                plen = 0;
                                for (int i = 0; i < 8; i++) plen = (plen << 8) | (ext[i] & 0xFF);
                            }
                            if (!fin) {
                                conn.close(WsFrame.CLOSE_UNSUPPORTED, "fragmented frames not supported");
                                break frameLoop;
                            }
                            // 3) Mask key (must be present on client->server)
                            byte[] mask = new byte[4];
                            if (masked) {
                                readFully(client.getInputStream(), mask);
                            } else {
                                conn.close(WsFrame.CLOSE_PROTOCOL_ERROR, "client frame must be masked");
                                break frameLoop;
                            }
                            // 4) Payload (with size cap)
                            if (plen > WsFrame.maxFrameBytes.get()) {
                                conn.close(WsFrame.CLOSE_TOO_BIG, "frame too large");
                                break frameLoop;
                            }
                            byte[] payload = new byte[(int) plen];
                            if (plen > 0) {
                                readFully(client.getInputStream(), payload);
                                if (masked) for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];
                            }
                            // 5) React
                            if (op == 0x9 /* PING */) { conn.pong(payload); }
                            else if (op == 0xA /* PONG */) { /* ignore */ }
                            else if (op == 0x8 /* CLOSE */) { conn.close(1000, ""); break frameLoop; }
                            else if (op == 0x1 /* TEXT */) {
                                WS_MESSAGES_RECEIVED.incrementAndGet();
                                String text = new String(payload, java.nio.charset.StandardCharsets.UTF_8);
                                KOF_WEB_REQUEST.set(req);
                                KOF_WS_CONNECTION.set(conn);
                                KOF_WS_MESSAGE.set(text);
                                try {
                                    result.route.handler.getClass().getMethod("invoke").invoke(result.route.handler);
                                } catch (Exception e) {
                                    System.err.println("kof web ws handler error: " + e.getMessage());
                                } finally {
                                    KOF_WEB_REQUEST.remove();
                                    KOF_WS_CONNECTION.remove();
                                    KOF_WS_MESSAGE.remove();
                                }
                            }
                        }
                    } catch (java.net.SocketTimeoutException idle) {
                        // graceful close after idleMs
                    } catch (java.io.IOException eof) {
                        // client closed
                    } finally {
                        WS_CONNECTIONS_ACTIVE.decrementAndGet();
                    }
                }

""";
    }
}
