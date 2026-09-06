package dev.kof.compiler;

/**
 * Servidor web do runtime JVM: listen/listenSecure/ssl/handle/ws-handshake.
 * Extraído de JvmRuntime.source (REFACTOR-500 Fase 5) — fragmento de source
 * do KofRuntime gerado; concatenação preserva ordem e conteúdo byte-a-byte.
 */
final class JvmRuntimeWebServer {

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

                public static void kof_web_listen_secure(String appId, int port) {
                    WebApp app = kof_web_app(appId);
                    if (app.serverSocket != null) {
                        throw new IllegalStateException("app already listening: " + appId);
                    }
                    try {
                        javax.net.ssl.SSLContext ctx = kof_web_ssl_context();
                        javax.net.ssl.SSLServerSocketFactory ssf = ctx.getServerSocketFactory();
                        javax.net.ssl.SSLServerSocket ss = (javax.net.ssl.SSLServerSocket) ssf.createServerSocket(port, 64,
                                java.net.InetAddress.getByName("0.0.0.0"));
                        ss.setNeedClientAuth(false);
                        app.serverSocket = ss;
                    } catch (Exception e) {
                        throw new RuntimeException("cannot bind TLS port " + port + ": " + e.getMessage(), e);
                    }
                    app.running = true;
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> kof_web_close(appId)));
                    while (app.running) {
                        try {
                            java.net.Socket client = app.serverSocket.accept();
                            client.setSoTimeout(15000);
                            Thread.startVirtualThread(() -> kof_web_handle(app, client));
                        } catch (java.io.IOException e) {
                            if (!app.running) break;
                        }
                    }
                }

                private static javax.net.ssl.SSLContext kof_web_ssl_context() throws Exception {
                    String ksPath = System.getProperty("java.io.tmpdir") + "/kof-tls-" + System.nanoTime() + ".jks";
                    try {
                        Process p = new ProcessBuilder("keytool", "-genkeypair", "-alias", "kof", "-keyalg", "RSA", "-keysize", "2048", "-validity", "365", "-dname", "CN=localhost, OU=Kof, O=Kof, L=Test, ST=Test, C=US", "-ext", "SAN=IP:127.0.0.1,DNS:localhost", "-keystore", ksPath, "-storepass", "changeit", "-keypass", "changeit", "-storetype", "JKS", "-noprompt").redirectErrorStream(true).start();
                        String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                        int ec = p.waitFor();
                        if (ec != 0) throw new RuntimeException("keytool failed: " + out);
                        java.security.KeyStore ks = java.security.KeyStore.getInstance("JKS");
                        try (java.io.FileInputStream fis = new java.io.FileInputStream(ksPath)) { ks.load(fis, "changeit".toCharArray()); }
                        javax.net.ssl.KeyManagerFactory kmf = javax.net.ssl.KeyManagerFactory.getInstance("SunX509");
                        kmf.init(ks, "changeit".toCharArray());
                        javax.net.ssl.TrustManager[] trustAll = new javax.net.ssl.TrustManager[]{new javax.net.ssl.X509TrustManager() {
                            public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
                            public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}
                            public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}
                        }};
                        javax.net.ssl.SSLContext ctx = javax.net.ssl.SSLContext.getInstance("TLS");
                        ctx.init(kmf.getKeyManagers(), trustAll, new java.security.SecureRandom());
                        return ctx;
                    } finally {
                        try { java.nio.file.Files.deleteIfExists(java.nio.file.Path.of(ksPath)); } catch (Exception ignored) {}
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
                            WebRequest req = readRequest(client.getInputStream());
                            WebDispatchResult result = kof_web_dispatch(app, req);
                            if (result.kind == RouteKind.SSE) {
                                java.io.OutputStream out = client.getOutputStream();
                                out.write(("HTTP/1.1 200 OK\\r\\n"
                                        + "Content-Type: text/event-stream\\r\\n"
                                        + "Cache-Control: no-cache\\r\\n"
                                        + "Connection: keep-alive\\r\\n"
                                        + "X-Accel-Buffering: no\\r\\n"
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
                                return;
                            }
                            if (result.kind == RouteKind.WS) {
                                kof_web_ws_handshake(result, req, client);
                                return;
                            }
                            client.getOutputStream().write(result.response.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            if (result.body() != null) {
                                // arquivo estático: head na response + bytes crus
                                client.getOutputStream().write(result.body());
                            }
                            client.getOutputStream().flush();
                        } catch (Exception e) {
                            System.err.println("kof web connection error: " + e.getMessage());
                        }
                    } finally {
                        app.activeConnections.decrementAndGet();
                    }
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
