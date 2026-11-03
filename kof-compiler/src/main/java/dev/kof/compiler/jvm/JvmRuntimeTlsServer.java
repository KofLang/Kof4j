package dev.kof.compiler.jvm;

/**
 * TLS do servidor web do runtime JVM (fatias do plano
 * {@code docs/development/tls-hardening-plan.md}, contrato congelado
 * {@code D-SEC.6}): o self-signed de dev, o PEM próprio de produção, a
 * seleção SNI (fatia 1) e os utilitários de keystore/PEM. Extraído de
 * {@code JvmRuntimeWebServer} para o check_500 (a fatia SNI empurrou a
 * classe além de 600 linhas); concatenação preserva ordem e conteúdo
 * byte-a-byte. {@code javax.net.ssl} só aparece AQUI (mesma higiene).
 */
public final class JvmRuntimeTlsServer {

    private JvmRuntimeTlsServer() {}

    static String source() {
        return """

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
                    kof_web_warn_security_default(app);
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

                /**
                 * D-SEC: TLS com certificado próprio (PKCS#8 PEM) —
                 * {@code app.listenSecure(port, certPem, keyPem)}. Diferente do
                 * self-signed de dev (kof_web_listen_secure), usa a chave/cert
                 * do usuário: produção. Native/JS seguem WEB002 honesto.
                 */
                public static void kof_web_listen_secure_pem(String appId, int port,
                        String certPem, String keyPem) {
                    WebApp app = kof_web_app(appId);
                    if (app.serverSocket != null) {
                        throw new IllegalStateException("app already listening: " + appId);
                    }
                    try {
                        javax.net.ssl.SSLContext ctx = kof_web_ssl_context_pem(certPem, keyPem);
                        javax.net.ssl.SSLServerSocketFactory ssf = ctx.getServerSocketFactory();
                        javax.net.ssl.SSLServerSocket ss = (javax.net.ssl.SSLServerSocket) ssf.createServerSocket(port, 64,
                                java.net.InetAddress.getByName("0.0.0.0"));
                        ss.setNeedClientAuth(false);
                        app.serverSocket = ss;
                    } catch (Exception e) {
                        throw new RuntimeException("cannot bind TLS port " + port + ": " + e.getMessage(), e);
                    }
                    app.running = true;
                    kof_web_warn_security_default(app);
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

                /**
                 * TLS hardening slice 1 (plan {@code tls-hardening-plan}): SNI
                 * certificate selection — {@code app.listenSecure(port,
                 * listOf(secureHost(host, certPem, keyPem), ...))}. One process
                 * serves many hostnames; the certificate is chosen from the
                 * ClientHello SNI. An UNKNOWN hostname is REFUSED with a named
                 * diagnostic and no certificate ({@code SSLHandshakeException:
                 * WEB007 ...}), never served the default cert (plan principle
                 * 3.1 — no silent downgrade, never the wrong certificate). A
                 * client without SNI gets the FIRST entry (documented default).
                 * The 1-arg and 3-arg surfaces are unchanged ({@code D-SEC.6},
                 * additive only).
                 */
                public static void kof_web_listen_secure_sni(String appId, int port,
                        String[] hosts, String[] certs, String[] keys) {
                    WebApp app = kof_web_app(appId);
                    if (app.serverSocket != null) {
                        throw new IllegalStateException("app already listening: " + appId);
                    }
                    int n = hosts == null ? 0 : hosts.length;
                    if (n == 0) {
                        throw new IllegalArgumentException(
                                "listenSecure(port, hosts): at least one secureHost(...) is required (WEB007)");
                    }
                    if (certs == null || keys == null || certs.length != n || keys.length != n) {
                        throw new IllegalArgumentException(
                                "listenSecure(port, hosts): hosts/certs/keys lengths disagree (WEB007)");
                    }
                    java.util.Map<String, javax.net.ssl.SSLContext> contexts = new java.util.LinkedHashMap<>();
                    String defaultHost;
                    try {
                        for (int i = 0; i < n; i++) {
                            String host = hosts[i] == null ? "" : hosts[i].trim().toLowerCase(java.util.Locale.ROOT);
                            if (host.isEmpty()) {
                                throw new IllegalArgumentException(
                                        "listenSecure(port, hosts): host name is empty at index " + i + " (WEB007)");
                            }
                            if (contexts.containsKey(host)) {
                                throw new IllegalArgumentException(
                                        "listenSecure(port, hosts): duplicate host '" + host + "' (WEB007)");
                            }
                            contexts.put(host, kof_web_ssl_context_pem(certs[i], keys[i]));
                        }
                        defaultHost = contexts.keySet().iterator().next();
                        // A PLAIN server socket: the TLS layer is applied per
                        // connection AFTER the ClientHello names the host.
                        app.serverSocket = new java.net.ServerSocket(port, 64,
                                java.net.InetAddress.getByName("0.0.0.0"));
                    } catch (Exception e) {
                        throw new RuntimeException("cannot bind TLS port " + port + ": " + e.getMessage(), e);
                    }
                    app.running = true;
                    kof_web_warn_security_default(app);
                    Runtime.getRuntime().addShutdownHook(new Thread(() -> kof_web_close(appId)));
                    final String defHost = defaultHost;
                    while (app.running) {
                        try {
                            java.net.Socket client = app.serverSocket.accept();
                            client.setSoTimeout(15000);
                            Thread.startVirtualThread(() -> {
                                java.net.Socket tls = kof_web_sni_wrap(client, contexts, defHost);
                                if (tls != null) kof_web_handle(app, tls);
                            });
                        } catch (java.io.IOException e) {
                            if (!app.running) break;
                        }
                    }
                }

                /**
                 * Picks the host's SSLContext from the ClientHello SNI and layers
                 * TLS over the accepted socket. An unknown SNI host is REFUSED
                 * (socket closed, no certificate) — plan principle 3.1: never the
                 * default cert. No SNI at all gets the documented default (first).
                 */
                private static java.net.Socket kof_web_sni_wrap(java.net.Socket raw,
                        java.util.Map<String, javax.net.ssl.SSLContext> contexts, String defaultHost) {
                    try {
                        // Read EXACTLY the first TLS record (5-byte header + its
                        // length) — a plain read() would block until the buffer
                        // filled, and a ClientHello is ~200 bytes.
                        java.io.InputStream rawIn = raw.getInputStream();
                        byte[] hdr = new byte[5];
                        int got = 0;
                        while (got < 5) {
                            int r = rawIn.read(hdr, got, 5 - got);
                            if (r < 0) { raw.close(); return null; }
                            got += r;
                        }
                        int recLen = ((hdr[3] & 0xff) << 8) | (hdr[4] & 0xff);
                        byte[] head = new byte[5 + recLen];
                        System.arraycopy(hdr, 0, head, 0, 5);
                        got = 0;
                        while (got < recLen) {
                            int r = rawIn.read(head, 5 + got, recLen - got);
                            if (r < 0) { raw.close(); return null; }
                            got += r;
                        }
                        int n = head.length;
                        String host = kof_web_sni_host(null, java.util.Arrays.copyOf(head, n));
                        String pick = host == null ? defaultHost
                                : (contexts.containsKey(host) ? host : null);
                        if (pick == null) {
                            raw.close();
                            System.err.println("WEB007: unknown TLS SNI hostname '" + host
                                    + "' — refused (no default certificate)");
                            return null;
                        }
                        // The JDK's SSLSocket uses the SocketImpl, not the passed
                        // Socket's streams, so the replayed ClientHello would be
                        // lost. The handshake runs on an SSLEngine over the raw
                        // socket instead and the result is exposed as a Socket.
                        return new KofTlsSocket(raw, head, contexts.get(pick));
                    } catch (Throwable e) {
                        // Throwable (not Exception): an Error in the engine path
                        // must also be visible — an uncaught death leaves the
                        // socket open and the CLIENT times out with no reason.
                        try { raw.close(); } catch (java.io.IOException ignored) { }
                        System.err.println("WEB007: TLS SNI handshake failed: " + e);
                        e.printStackTrace(System.err);
                        return null;
                    }
                }

                /**
                 * Reads the SNI hostname from a raw TLS ClientHello WITHOUT
                 * consuming the stream (a {@code PushbackInputStream} restores
                 * every byte the caller read). Returns {@code null} when the
                 * client sent no SNI. Malformed records return {@code null} and
                 * the caller drops the connection.
                 */
                private static String kof_web_sni_host(java.io.PushbackInputStream in, byte[] head) {
                    try {
                        // record(5) + handshake type(1) + handshake len(3) +
                        // client_version(2) + random(32)
                        int p = 5 + 1 + 3 + 2 + 32;
                        if (p >= head.length) return null;
                        int sidLen = head[p] & 0xff;
                        p += 1 + sidLen;
                        if (p + 2 > head.length) return null;
                        int csLen = ((head[p] & 0xff) << 8) | (head[p + 1] & 0xff);
                        p += 2 + csLen;
                        if (p >= head.length) return null;
                        int cmLen = head[p] & 0xff;
                        p += 1 + cmLen;
                        if (p + 2 > head.length) return null;
                        int extLen = ((head[p] & 0xff) << 8) | (head[p + 1] & 0xff);
                        p += 2;
                        int end = Math.min(head.length, p + extLen);
                        while (p + 4 <= end) {
                            int type = ((head[p] & 0xff) << 8) | (head[p + 1] & 0xff);
                            int len = ((head[p + 2] & 0xff) << 8) | (head[p + 3] & 0xff);
                            p += 4;
                            if (p + len > end) return null;
                            if (type == 0x0000 && len >= 5) { // server_name
                                int listLen = ((head[p] & 0xff) << 8) | (head[p + 1] & 0xff);
                                int q = p + 2;
                                int listEnd = Math.min(end, q + listLen);
                                while (q + 3 <= listEnd) {
                                    int nameType = head[q] & 0xff;
                                    int nameLen = ((head[q + 1] & 0xff) << 8) | (head[q + 2] & 0xff);
                                    q += 3;
                                    if (q + nameLen > listEnd) return null;
                                    if (nameType == 0) {
                                        return new String(head, q, nameLen,
                                                java.nio.charset.StandardCharsets.US_ASCII);
                                    }
                                    q += nameLen;
                                }
                            }
                            p += len;
                        }
                    } catch (RuntimeException ignored) {
                    }
                    return null;
                }

                /**
                 * A plain Socket over an SSLEngine handshake: the engine consumes
                 * the replayed ClientHello, completes the handshake with the
                 * chosen host's context, and the streams below encrypt/decrypt.
                 */
                private static final class KofTlsSocket extends java.net.Socket {
                    private final java.net.Socket raw;
                    private final java.io.InputStream netIn;
                    private final javax.net.ssl.SSLEngine engine;
                    private final java.nio.ByteBuffer net;
                    private final java.nio.ByteBuffer app;
                    private final java.io.InputStream plainIn;
                    private final java.io.OutputStream plainOut;

                    KofTlsSocket(java.net.Socket raw, byte[] replayed, javax.net.ssl.SSLContext ctx)
                            throws Exception {
                        this.raw = raw;
                        // The replayed first record first, then the raw stream —
                        // a ByteArrayInputStream never blocks, so the first
                        // fillNet cannot deadlock waiting for bytes the peer will
                        // only send after our ServerHello.
                        this.netIn = new java.io.SequenceInputStream(
                                new java.io.ByteArrayInputStream(replayed), raw.getInputStream());
                        this.engine = ctx.createSSLEngine();
                        this.engine.setUseClientMode(false);
                        this.net = java.nio.ByteBuffer.allocate(engine.getSession().getPacketBufferSize() * 2);
                        this.app = java.nio.ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
                        this.net.limit(0); // READ mode: no bytes yet
                        this.engine.beginHandshake();
                        handshake();
                        this.plainIn = new KofTlsInput();
                        this.plainOut = new KofTlsOutput();
                    }

                    private void handshake() throws Exception {
                        java.nio.ByteBuffer out = java.nio.ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
                        javax.net.ssl.SSLEngineResult.HandshakeStatus hs = engine.getHandshakeStatus();
                        while (hs != javax.net.ssl.SSLEngineResult.HandshakeStatus.FINISHED
                                && hs != javax.net.ssl.SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING) {
                            switch (hs) {
                                case NEED_TASK -> {
                                    Runnable t;
                                    int k = 0;
                                    while ((t = engine.getDelegatedTask()) != null) {
                                        Thread watchdog = null;
                                        try {
                                            t.run();
                                        } catch (Throwable te) {
                                            throw te;
                                        } finally {
                                            if (watchdog != null) watchdog.interrupt();
                                        }
                                    }
                                }
                                case NEED_WRAP -> {
                                    out.clear();
                                    engine.wrap(java.nio.ByteBuffer.allocate(0), out);
                                    out.flip();
                                    raw.getOutputStream().write(out.array(), 0, out.limit());
                                    raw.getOutputStream().flush();
                                }
                                case NEED_UNWRAP -> {
                                    long t0 = System.currentTimeMillis();
                                    if (!net.hasRemaining()) fillNet();
                                    long t1 = System.currentTimeMillis();
                                    app.clear();
                                    javax.net.ssl.SSLEngineResult ur = engine.unwrap(net, app);
                                    app.flip();
                                }
                                default -> { }
                            }
                            hs = engine.getHandshakeStatus();
                        }
                    }

                    /** Reads more ciphertext into `net` (READ mode). */
                    private void fillNet() throws java.io.IOException {
                        net.compact();
                        int rd = netIn.read(net.array(), net.position(), net.remaining());
                        if (rd < 0) throw new java.io.EOFException("TLS peer closed");
                        net.position(net.position() + rd);
                        net.flip();
                    }

                    private final class KofTlsInput extends java.io.InputStream {
                        @Override public int read() throws java.io.IOException {
                            byte[] one = new byte[1];
                            int n = read(one, 0, 1);
                            return n <= 0 ? -1 : (one[0] & 0xff);
                        }

                        @Override public int read(byte[] b, int off, int len) throws java.io.IOException {
                            while (true) {
                                if (app.hasRemaining()) {
                                    int n = Math.min(len, app.remaining());
                                    app.get(b, off, n);
                                    return n;
                                }
                                if (!net.hasRemaining()) fillNet();
                                app.clear();
                                javax.net.ssl.SSLEngineResult r = engine.unwrap(net, app);
                                app.flip();
                                if (r.getStatus() == javax.net.ssl.SSLEngineResult.Status.CLOSED) return -1;
                                if (app.hasRemaining()) {
                                    int n = Math.min(len, app.remaining());
                                    app.get(b, off, n);
                                    return n;
                                }
                            }
                        }
                    }

                    private final class KofTlsOutput extends java.io.OutputStream {
                        @Override public void write(int b) throws java.io.IOException { write(new byte[]{(byte) b}, 0, 1); }

                        @Override public void write(byte[] b, int off, int len) throws java.io.IOException {
                            java.nio.ByteBuffer src = java.nio.ByteBuffer.wrap(b, off, len);
                            java.nio.ByteBuffer out = java.nio.ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
                            while (src.hasRemaining()) {
                                out.clear();
                                engine.wrap(src, out);
                                out.flip();
                                raw.getOutputStream().write(out.array(), 0, out.limit());
                            }
                            raw.getOutputStream().flush();
                        }
                    }

                    @Override public java.io.InputStream getInputStream() { return plainIn; }
                    @Override public java.io.OutputStream getOutputStream() { return plainOut; }
                    @Override public java.net.InetAddress getInetAddress() { return raw.getInetAddress(); }
                    @Override public int getPort() { return raw.getPort(); }
                    @Override public boolean isConnected() { return raw.isConnected(); }
                    @Override public void setSoTimeout(int ms) throws java.net.SocketException { raw.setSoTimeout(ms); }
                    @Override public int getSoTimeout() throws java.net.SocketException { return raw.getSoTimeout(); }
                    @Override public void close() throws java.io.IOException { raw.close(); }
                    @Override public boolean isClosed() { return raw.isClosed(); }
                }


                /** The X509 key manager for one PEM pair (SNI slice 1). */
                private static javax.net.ssl.X509ExtendedKeyManager kof_web_key_manager_pem(
                        String certPem, String keyPem) throws Exception {
                    byte[] certDer = java.util.Base64.getMimeDecoder().decode(
                            kof_web_pem_body(certPem, "CERTIFICATE"));
                    java.security.cert.X509Certificate cert = (java.security.cert.X509Certificate)
                            java.security.cert.CertificateFactory.getInstance("X.509")
                                    .generateCertificate(new java.io.ByteArrayInputStream(certDer));
                    byte[] keyDer = java.util.Base64.getMimeDecoder().decode(
                            kof_web_pem_body(keyPem, "PRIVATE KEY"));
                    java.security.spec.PKCS8EncodedKeySpec spec =
                            new java.security.spec.PKCS8EncodedKeySpec(keyDer);
                    java.security.PrivateKey key = null;
                    for (String alg : new String[]{"RSA", "EC", "DSA"}) {
                        try {
                            key = java.security.KeyFactory.getInstance(alg).generatePrivate(spec);
                            break;
                        } catch (Exception ignored) {
                        }
                    }
                    if (key == null) {
                        throw new IllegalArgumentException("unsupported PKCS#8 private key (tried RSA/EC/DSA)");
                    }
                    java.security.KeyStore ks = java.security.KeyStore.getInstance("PKCS12");
                    ks.load(null, null);
                    ks.setKeyEntry("kof", key, "changeit".toCharArray(),
                            new java.security.cert.Certificate[]{cert});
                    javax.net.ssl.KeyManagerFactory kmf =
                            javax.net.ssl.KeyManagerFactory.getInstance("SunX509");
                    kmf.init(ks, "changeit".toCharArray());
                    for (javax.net.ssl.KeyManager k : kmf.getKeyManagers()) {
                        if (k instanceof javax.net.ssl.X509ExtendedKeyManager x) return x;
                    }
                    throw new IllegalStateException("no X509 key manager from the PEM pair (WEB007)");
                }

                private static javax.net.ssl.SSLContext kof_web_ssl_context_pem(String certPem, String keyPem)
                        throws Exception {
                    byte[] certDer = java.util.Base64.getMimeDecoder().decode(
                            kof_web_pem_body(certPem, "CERTIFICATE"));
                    java.security.cert.X509Certificate cert = (java.security.cert.X509Certificate)
                            java.security.cert.CertificateFactory.getInstance("X.509")
                                    .generateCertificate(new java.io.ByteArrayInputStream(certDer));
                    byte[] keyDer = java.util.Base64.getMimeDecoder().decode(
                            kof_web_pem_body(keyPem, "PRIVATE KEY"));
                    java.security.spec.PKCS8EncodedKeySpec spec =
                            new java.security.spec.PKCS8EncodedKeySpec(keyDer);
                    java.security.PrivateKey key = null;
                    for (String alg : new String[]{"RSA", "EC", "DSA"}) {
                        try {
                            key = java.security.KeyFactory.getInstance(alg).generatePrivate(spec);
                            break;
                        } catch (Exception ignored) {
                        }
                    }
                    if (key == null) {
                        throw new IllegalArgumentException("unsupported PKCS#8 private key (tried RSA/EC/DSA)");
                    }
                    java.security.KeyStore ks = java.security.KeyStore.getInstance("PKCS12");
                    ks.load(null, null);
                    ks.setKeyEntry("kof", key, "changeit".toCharArray(),
                            new java.security.cert.Certificate[]{cert});
                    javax.net.ssl.KeyManagerFactory kmf =
                            javax.net.ssl.KeyManagerFactory.getInstance("SunX509");
                    kmf.init(ks, "changeit".toCharArray());
                    javax.net.ssl.SSLContext ctx = javax.net.ssl.SSLContext.getInstance("TLS");
                    ctx.init(kmf.getKeyManagers(), null, new java.security.SecureRandom());
                    return ctx;
                }

                private static String kof_web_pem_body(String pem, String label) {
                    String body = pem.replace("-----BEGIN " + label + "-----", "")
                            .replace("-----END " + label + "-----", "");
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < body.length(); i++) {
                        char c = body.charAt(i);
                        if (!Character.isWhitespace(c)) sb.append(c);
                    }
                    return sb.toString();
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
        """;
}
