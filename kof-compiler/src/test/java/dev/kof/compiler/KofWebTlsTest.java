package dev.kof.compiler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E TLS tests for G12 — web.app().listenSecure(port) with self-signed cert.
 */
class KofWebTlsTest {

    private static final String JAVA_BIN = Path.of(
            System.getProperty("java.home"), "bin", "java").toString();

    private final CompilerDriver driver = new CompilerDriver();
    private Process serverProcess;

    @AfterEach
    void stopServer() {
        if (serverProcess != null) {
            serverProcess.destroy();
            try {
                serverProcess.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}
            serverProcess.destroyForcibly();
            serverProcess = null;
        }
    }

    private static final String TLS_APP = """
            main() {
                var app = web.app()
                app.get("/hello") { return "Hello TLS" }
                app.get("/secure") { return "secure " + header("x-test") }
                app.listenSecure(PORT)
            }
            """;

    private int startTlsServer(Path tempDir) throws IOException {
        int port = freePort();
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, TLS_APP.replace("PORT", String.valueOf(port)));
        Path outDir = tempDir.resolve("classes");
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "TLS compilation should succeed: " + result.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(JAVA_BIN, "-cp", outDir.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        serverProcess = pb.start();
        TestServerFixture.await(serverProcess, port, 60, 100, () -> {
            javax.net.ssl.SSLSocketFactory factory = insecureFactory();
            try (javax.net.ssl.SSLSocket probe = (javax.net.ssl.SSLSocket) factory.createSocket("localhost", port)) {
                probe.setSoTimeout(200);
                probe.startHandshake();
                return true;
            }
        });
        return port;
    }

    private int freePort() throws IOException {
        try (ServerSocket probe = new ServerSocket(0)) {
            return probe.getLocalPort();
        }
    }

    private static javax.net.ssl.SSLSocketFactory insecureFactory() {
        try {
            javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("TLS");
            sc.init(null, new javax.net.ssl.TrustManager[]{new javax.net.ssl.X509TrustManager() {
                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}
                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}
            }}, new java.security.SecureRandom());
            return sc.getSocketFactory();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String httpsRequest(int port, String raw) throws IOException {
        javax.net.ssl.SSLSocketFactory factory = insecureFactory();
        try (javax.net.ssl.SSLSocket socket = (javax.net.ssl.SSLSocket) factory.createSocket("localhost", port)) {
            socket.setSoTimeout(5000);
            socket.startHandshake();
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

    private String bodyOf(String rawResponse) {
        int idx = rawResponse.indexOf("\r\n\r\n");
        return idx >= 0 ? rawResponse.substring(idx + 4) : rawResponse;
    }

    @Test
    void tlsHelloRoute(@TempDir Path tempDir) throws IOException {
        int port = startTlsServer(tempDir);
        String r = httpsRequest(port, "GET /hello HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(r.startsWith("HTTP/1.1 200 OK"), r);
        assertEquals("Hello TLS", bodyOf(r).trim(), r);
    }

    @Test
    void tlsHeadersAvailable(@TempDir Path tempDir) throws IOException {
        int port = startTlsServer(tempDir);
        String r = httpsRequest(port, "GET /secure HTTP/1.1\r\nHost: x\r\nX-Test: myval\r\n\r\n");
        assertTrue(r.startsWith("HTTP/1.1 200 OK"), r);
        assertEquals("secure myval", bodyOf(r).trim(), r);
    }

    @Test
    void httpClientOverTls(@TempDir Path tempDir) throws Exception {
        int port = startTlsServer(tempDir);
        // Use kof.http client inside a Kof program to fetch from the TLS server
        String kofSource = """
                main() {
                    // Give server a moment
                    val body = http.get("https://localhost:PORT/hello")
                    println(body)
                }
                """.replace("PORT", String.valueOf(port));
        Path source = tempDir.resolve("Client.kf");
        Files.writeString(source, kofSource);
        Path outDir = tempDir.resolve("client_classes");
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Client compilation should succeed: " + result.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(JAVA_BIN, "-cp", outDir.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        int ec = p.waitFor(10, TimeUnit.SECONDS) ? p.waitFor() : -1;
        // The http client should have fetched via TLS (insecure trust)
        assertTrue(output.contains("Hello TLS"), "Client output should contain Hello TLS, got: " + output);
    }

    @Test
    void tlsGapOnNative(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, """
                main() {
                    var app = web.app()
                    app.get("/x") { return "x" }
                    app.listenSecure(8443)
                }
                """);
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertFalse(result.success(), "Native TLS should report gap");
        assertTrue(result.diagnostics().getDiagnostics().stream().anyMatch(d -> d.code().equals("WEB002")),
                "Should have WEB002, got: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void tlsGapOnJs(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, """
                main() {
                    var app = web.app()
                    app.listenSecure(8443)
                }
                """);
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertFalse(result.success(), "JS TLS should report gap");
        assertTrue(result.diagnostics().getDiagnostics().stream().anyMatch(d -> d.code().equals("WEB001") || d.code().equals("WEB002")),
                "Should have WEB001/002, got: " + result.diagnostics().getDiagnostics());
    }

    // ── D-SEC: TLS com certificado PRÓPRIO (PKCS#8 PEM) ──────────────────
    // app.listenSecure(port, certPem, keyPem) — produção; o self-signed de
    // dev (1 arg) continua. Native/JS seguem WEB002 honesto.

    private record PemPair(String certPem, String keyPem) {}

    /** Gera um par cert/chave auto-assinado em PEM (PKCS#8) via keytool+JDK. */
    private static PemPair generatePem(Path dir) throws Exception {
        Path ks = dir.resolve("test.p12");
        Process p = new ProcessBuilder("keytool", "-genkeypair", "-alias", "kof",
                "-keyalg", "RSA", "-keysize", "2048", "-validity", "365",
                "-dname", "CN=localhost,OU=Kof,O=Kof,L=Test,ST=Test,C=US",
                "-ext", "SAN=IP:127.0.0.1,DNS:localhost",
                "-keystore", ks.toString(), "-storepass", "changeit",
                "-keypass", "changeit", "-storetype", "PKCS12", "-noprompt")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.waitFor(), "keytool falhou: " + out);
        java.security.KeyStore store = java.security.KeyStore.getInstance("PKCS12");
        try (var in = Files.newInputStream(ks)) {
            store.load(in, "changeit".toCharArray());
        }
        java.security.PrivateKey key = (java.security.PrivateKey)
                store.getKey("kof", "changeit".toCharArray());
        java.security.cert.Certificate cert = store.getCertificate("kof");
        return new PemPair(
                "-----BEGIN CERTIFICATE-----\n" + mime(cert.getEncoded()) + "-----END CERTIFICATE-----\n",
                "-----BEGIN PRIVATE KEY-----\n" + mime(key.getEncoded()) + "-----END PRIVATE KEY-----\n");
    }

    private static String mime(byte[] der) {
        return java.util.Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(der) + "\n";
    }

    /** Escapa um PEM para um literal String do Kof (mesma linha, \n). */
    private static String kofLiteral(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    @Test
    void tlsOwnCertificateServesHttps(@TempDir Path tempDir) throws Exception {
        PemPair pem = generatePem(tempDir);
        int port = freePort();
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, "main() {\n"
                + "    var app = web.app()\n"
                + "    app.get(\"/hello\") { return \"Hello PEM\" }\n"
                + "    app.listenSecure(" + port + ", \""
                + kofLiteral(pem.certPem()) + "\", \"" + kofLiteral(pem.keyPem()) + "\")\n"
                + "}\n");
        Path outDir = tempDir.resolve("classes");
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "PEM TLS deve compilar: " + result.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(JAVA_BIN, "-cp", outDir.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        serverProcess = pb.start();

        TestServerFixture.await(serverProcess, port, 60, 100, () -> {
            javax.net.ssl.SSLSocketFactory factory = insecureFactory();
            try (javax.net.ssl.SSLSocket probe =
                         (javax.net.ssl.SSLSocket) factory.createSocket("localhost", port)) {
                probe.setSoTimeout(300);
                probe.startHandshake();
                String r = httpsRequest(port, "GET /hello HTTP/1.1\r\nHost: x\r\n\r\n");
                assertTrue(r.startsWith("HTTP/1.1 200 OK"), r);
                assertEquals("Hello PEM", bodyOf(r).trim(), r);
                return true;
            }
        });
    }

    @Test
    void tlsOwnCertificateGapOnNative(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, """
                main() {
                    var app = web.app()
                    app.listenSecure(8443, "cert", "key")
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertFalse(result.success(), "Native PEM TLS deve reportar gap");
        assertTrue(result.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> d.code().equals("WEB002")),
                "esperava WEB002, foi: " + result.diagnostics().getDiagnostics());
    }
}
