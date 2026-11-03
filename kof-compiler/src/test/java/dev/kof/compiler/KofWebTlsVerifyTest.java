package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TLS hardening slice 3 ({@code docs/development/tls-hardening-plan.md}): the
 * verifying HTTP client — strict https is the DEFAULT for {@code kof.http}
 * (chain against the platform trust store WITH hostname verification);
 * trust-all requires the explicit opt-in {@code http.insecure()} that warns.
 * Split out of {@code KofWebTlsTest} for the check_500 ratchet (the CA dance
 * plus five tests exceeded the 500-line cap); the test-hygiene pattern is the
 * Phase 3 responsibility split.
 */
class KofWebTlsVerifyTest extends ServerProcessSupport {

    private static final String JAVA_BIN = Path.of(
            System.getProperty("java.home"), "bin", "java").toString();

    private final CompilerDriver driver = new CompilerDriver();


    private int freePort() throws IOException {
        try (ServerSocket probe = new ServerSocket(0)) {
            return probe.getLocalPort();
        }
    }

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

    private static String kofLiteral(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }


    private static String fingerprintOf(Path tempDir, PemPair pem) throws Exception {
        byte[] der = java.util.Base64.getMimeDecoder().decode(
                pem.certPem().replace("-----BEGIN CERTIFICATE-----", "")
                        .replace("-----END CERTIFICATE-----", ""));
        return java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(der));
    }

    private int startPemServer(Path tempDir, PemPair pem, String extraRoutes) throws Exception {
        int port = freePort();
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, "main() {\n"
                + "    var app = web.app()\n"
                + "    app.get(\"/hello\") { return \"Hello PEM\" }\n"
                + extraRoutes + "\n"
                + "    app.listenSecure(" + port + ", \"" + kofLiteral(pem.certPem())
                + "\", \"" + kofLiteral(pem.keyPem()) + "\")\n"
                + "}\n");
        Path outDir = tempDir.resolve("classes");
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "PEM TLS deve compilar: " + result.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(JAVA_BIN, "-cp", outDir.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        serverProcess = pb.start();
        TestServerFixture.await(serverProcess, port, 60, 100, () -> {
            try {
                servedFingerprint(port, null);
                return true;
            } catch (Exception e) {
                return false;
            }
        });
        return port;
    }

    /** Gera um par self-signed com CN/SAN do hostname dado (fingerprint distinto). */
    private static PemPair generatePemFor(Path dir, String tag, String host) throws Exception {
        Path ks = dir.resolve("test-" + tag + ".p12");
        Process p = new ProcessBuilder("keytool", "-genkeypair", "-alias", "kof",
                "-keyalg", "RSA", "-keysize", "2048", "-validity", "365",
                "-dname", "CN=" + host + ",OU=Kof,O=Kof,L=Test,ST=Test,C=US",
                "-ext", "SAN=DNS:" + host,
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

    private static String servedFingerprint(int port, String sniHost) throws Exception {
        javax.net.ssl.SSLSocketFactory factory = TlsTrustAllSupport.insecureFactory();
        try (java.net.Socket plain = new java.net.Socket("127.0.0.1", port);
             javax.net.ssl.SSLSocket socket =
                     (javax.net.ssl.SSLSocket) factory.createSocket(plain, "127.0.0.1", port, true)) {
            socket.setSoTimeout(5000);
            if (sniHost != null) {
                javax.net.ssl.SNIHostName name = new javax.net.ssl.SNIHostName(sniHost);
                javax.net.ssl.SSLParameters p = socket.getSSLParameters();
                p.setServerNames(java.util.List.of(name));
                socket.setSSLParameters(p); // getSSLParameters() returns a COPY — mutate the copy, set it back
            }
            socket.startHandshake();
            java.security.cert.Certificate c = socket.getSession().getPeerCertificates()[0];
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(c.getEncoded()));
        }
    }

    // ── TLS hardening slice 3: the verifying HTTP client (JVM) ─────────────
    // Strict https is the DEFAULT for kof.http (chain against the platform
    // trust store WITH hostname verification); trust-all requires the
    // explicit opt-in `http.insecure()` that warns. RED-first proof: strict
    // accepts a properly-chained test CA cert, rejects self-signed, rejects
    // hostname mismatch — each with a named diagnostic, none silent.

    /** Compila+roda um cliente kof.http com props extras; devolve a saída. */
    private String runHttpClient(Path tempDir, String body, String... jvmProps) throws Exception {
        Path source = tempDir.resolve("Client.kf");
        Files.writeString(source, body);
        Path outDir = tempDir.resolve("client_classes");
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "client compile: " + result.diagnostics().getDiagnostics());
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(JAVA_BIN);
        for (String prop : jvmProps) cmd.add(prop);
        cmd.add("-cp");
        // The KofRuntime helper is generated INSIDE outDir (ensureCompiled);
        // it only needs the outDir on the classpath.
        cmd.add(outDir.toString());
        cmd.add("Default.Main");
        Process p2 = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String output = new String(p2.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        p2.waitFor(30, TimeUnit.SECONDS);
        return output;
    }

    /** CA de teste + cert de servidor assinado (o par PEM + o trust store do cliente). */
    private record CaPair(PemPair server, Path trustStore) {}

    private CaPair generateChained(Path dir, String serverHost) throws Exception {
        Process p1 = new ProcessBuilder("keytool", "-genkeypair", "-alias", "ca",
                "-keyalg", "RSA", "-keysize", "2048", "-validity", "365",
                "-dname", "CN=TestCA", "-ext", "bc",
                "-keystore", dir.resolve("ca.p12").toString(), "-storepass", "changeit",
                "-keypass", "changeit", "-storetype", "PKCS12", "-noprompt")
                .redirectErrorStream(true).start();
        assertEquals(0, p1.waitFor(), "CA genkeypair");
        Path srvP12 = dir.resolve("srv.p12");
        Process p2 = new ProcessBuilder("keytool", "-genkeypair", "-alias", "kof",
                "-keyalg", "RSA", "-keysize", "2048", "-validity", "365",
                "-dname", "CN=" + serverHost,
                "-ext", "SAN=DNS:" + serverHost + ",IP:127.0.0.1",
                "-keystore", srvP12.toString(), "-storepass", "changeit",
                "-keypass", "changeit", "-storetype", "PKCS12", "-noprompt")
                .redirectErrorStream(true).start();
        assertEquals(0, p2.waitFor(), "server genkeypair");
        Process p3 = new ProcessBuilder("keytool", "-certreq", "-alias", "kof",
                "-keystore", srvP12.toString(), "-storepass", "changeit",
                "-file", dir.resolve("srv.csr").toString())
                .redirectErrorStream(true).start();
        assertEquals(0, p3.waitFor(), "certreq");
        Process p4 = new ProcessBuilder("keytool", "-gencert", "-rfc", "-alias", "ca",
                "-infile", dir.resolve("srv.csr").toString(),
                "-outfile", dir.resolve("srv.cer").toString(),
                "-keystore", dir.resolve("ca.p12").toString(),
                "-storepass", "changeit", "-keypass", "changeit",
                "-validity", "365", "-ext", "SAN=DNS:" + serverHost + ",IP:127.0.0.1")
                .redirectErrorStream(true).start();
        String out4 = new String(p4.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p4.waitFor(), "gencert: " + out4);
        Process p5 = new ProcessBuilder("keytool", "-exportcert", "-rfc", "-alias", "ca",
                "-keystore", dir.resolve("ca.p12").toString(), "-storepass", "changeit",
                "-file", dir.resolve("ca.pem").toString()).redirectErrorStream(true).start();
        assertEquals(0, p5.waitFor(), "export CA");
        // o trust store do cliente = srv.p12 com a CA importada como entrada confiável
        Process p6 = new ProcessBuilder("keytool", "-importcert", "-alias", "ca",
                "-file", dir.resolve("ca.pem").toString(), "-keystore", srvP12.toString(),
                "-storepass", "changeit", "-noprompt").redirectErrorStream(true).start();
        assertEquals(0, p6.waitFor(), "import CA");
        Process p7 = new ProcessBuilder("keytool", "-importcert", "-alias", "kof",
                "-file", dir.resolve("srv.cer").toString(), "-keystore", srvP12.toString(),
                "-storepass", "changeit", "-noprompt").redirectErrorStream(true).start();
        String out7 = new String(p7.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p7.waitFor(), "import signed: " + out7);
        // PEMs do par assinado (o PEM do cert = srv.cer; a chave via KeyStore).
        // O keytool escreve no charset do SISTEMA (não UTF-8): decode com o
        // charset padrão, nunca Files.readString.
        String certPem = new String(Files.readAllBytes(dir.resolve("srv.cer")),
                java.nio.charset.Charset.defaultCharset());
        java.security.KeyStore store = java.security.KeyStore.getInstance("PKCS12");
        try (var in = Files.newInputStream(srvP12)) {
            store.load(in, "changeit".toCharArray());
        }
        java.security.PrivateKey key = (java.security.PrivateKey)
                store.getKey("kof", "changeit".toCharArray());
        PemPair signed = new PemPair(certPem,
                "-----BEGIN PRIVATE KEY-----\n" + mime(key.getEncoded())
                        + "-----END PRIVATE KEY-----\n");
        return new CaPair(signed, srvP12);
    }

    @Test
    void strictClientRejectsSelfSigned(@TempDir Path tempDir) throws Exception {
        PemPair self = generatePemFor(tempDir, "self", "localhost");
        int port = startPemServer(tempDir, self, "");
        String out = runHttpClient(tempDir, """
                main() {
                    try {
                        println(http.get("https://localhost:PORT/hello"))
                    } catch (String e) {
                        println("ERR: " + e)
                    }
                }
                """.replace("PORT", String.valueOf(port)));
        assertTrue(out.startsWith("ERR: "), "strict must REJECT a self-signed server, got: " + out);
        assertTrue(out.contains("TLS-VERIFY: "), "the rejection must be a NAMED diagnostic: " + out);
    }

    @Test
    void strictClientAcceptsChainedCaCert(@TempDir Path tempDir) throws Exception {
        CaPair ca = generateChained(tempDir, "localhost");
        int port = startPemServer(tempDir, ca.server(), "");
        String out = runHttpClient(tempDir, """
                main() {
                    try {
                        println(http.get("https://localhost:PORT/hello"))
                    } catch (String e) {
                        println("ERR: " + e)
                    }
                }
                """.replace("PORT", String.valueOf(port)),
                "-Djavax.net.ssl.trustStore=" + ca.trustStore(),
                "-Djavax.net.ssl.trustStoreType=PKCS12",
                "-Djavax.net.ssl.trustStorePassword=changeit");
        assertTrue(out.contains("Hello PEM"), "strict must ACCEPT a properly-chained cert, got: " + out);
    }

    @Test
    void strictClientRejectsHostnameMismatch(@TempDir Path tempDir) throws Exception {
        PemPair other = generatePemFor(tempDir, "other", "other.example");
        int port = startPemServer(tempDir, other, "");
        String out = runHttpClient(tempDir, """
                main() {
                    try {
                        println(http.get("https://localhost:PORT/hello"))
                    } catch (String e) {
                        println("ERR: " + e)
                    }
                }
                """.replace("PORT", String.valueOf(port)));
        assertTrue(out.startsWith("ERR: "), "strict must REJECT a hostname mismatch, got: " + out);
        assertTrue(out.contains("TLS-VERIFY: "), "the rejection must be a NAMED diagnostic: " + out);
    }

    @Test
    void insecureOptInWarnsAndTrustsSelfSigned(@TempDir Path tempDir) throws Exception {
        PemPair self = generatePemFor(tempDir, "self", "localhost");
        int port = startPemServer(tempDir, self, "");
        String out = runHttpClient(tempDir, """
                main() {
                    http.insecure()
                    try {
                        println(http.get("https://localhost:PORT/hello"))
                    } catch (String e) {
                        println("ERR: " + e)
                    }
                }
                """.replace("PORT", String.valueOf(port)));
        assertTrue(out.contains("Hello PEM"), "the opt-in must trust the self-signed server, got: " + out);
    }

    @Test
    void insecureGapOnNative(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, """
                main() {
                    http.insecure()
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertFalse(result.success(), "Native deve recusar o opt-in honestamente");
        assertTrue(result.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> d.code().equals("HTTP002")),
                "esperava HTTP002, foi: " + result.diagnostics().getDiagnostics());
    }

    // ── TLS hardening slice 4 (increment 1): the ACME JWS ES256 core ───────
    // acme.jwsEs256(accountKeyPem, headerJson, payloadJson) produces a
    // RFC 7515 JWS: b64url(header).b64url(payload).ES256(r||s). The operator's
    // PKCS#8 EC P-256 key signs; the test verifies with the public key.

    @Test
    void acmeJwsEs256SignsAndVerifiesRoundTrip(@TempDir Path tempDir) throws Exception {
        // EC P-256 par (keytool), PKCS#8 + X.509 PEMs
        Process p = new ProcessBuilder("keytool", "-genkeypair", "-alias", "acme",
                "-keyalg", "EC", "-groupname", "secp256r1",
                "-validity", "365", "-dname", "CN=acme-test",
                "-keystore", tempDir.resolve("acme.p12").toString(),
                "-storepass", "changeit", "-keypass", "changeit",
                "-storetype", "PKCS12", "-noprompt")
                .redirectErrorStream(true).start();
        assertEquals(0, p.waitFor(), "EC genkeypair");
        java.security.KeyStore store = java.security.KeyStore.getInstance("PKCS12");
        try (var in = Files.newInputStream(tempDir.resolve("acme.p12"))) {
            store.load(in, "changeit".toCharArray());
        }
        java.security.PrivateKey key = (java.security.PrivateKey)
                store.getKey("acme", "changeit".toCharArray());
        String keyPem = "-----BEGIN PRIVATE KEY-----\n" + mime(key.getEncoded())
                + "-----END PRIVATE KEY-----\n";
        java.security.PublicKey pub = store.getCertificate("acme").getPublicKey();

        String out = runHttpClient(tempDir, "main() {\n"
                + "    println(acme.jwsEs256(\"" + kofLiteral(keyPem) + "\", "
                + "\"" + kofLiteral("{\"alg\":\"ES256\"}") + "\", "
                + "\"" + kofLiteral("{\"iss\":\"test\"}") + "\"))\n"
                + "}\n");
        String[] parts = out.strip().split("\\.");
        assertEquals(3, parts.length, "a JWS has three parts: " + out);
        String signingInput = parts[0] + "." + parts[1];
        byte[] jwsSig = java.util.Base64.getUrlDecoder().decode(parts[2]);
        // JWS ES256 = r||s (64 bytes); converte para DER e verifica
        assertTrue(jwsSig.length == 64, "ES256 JWS signature is r||s: " + jwsSig.length);
        java.math.BigInteger r = new java.math.BigInteger(1,
                java.util.Arrays.copyOfRange(jwsSig, 0, 32));
        java.math.BigInteger sv = new java.math.BigInteger(1,
                java.util.Arrays.copyOfRange(jwsSig, 32, 64));
        String der = java.util.Base64.getEncoder().encodeToString(toDerSig(r, sv));
        java.security.Signature sig = java.security.Signature.getInstance("SHA256withECDSA");
        sig.initVerify(pub);
        sig.update(signingInput.getBytes(StandardCharsets.UTF_8));
        assertTrue(sig.verify(java.util.Base64.getDecoder().decode(der)),
                "the JWS must verify against the operator key: " + out);
    }

    private static byte[] toDerSig(java.math.BigInteger r, java.math.BigInteger s) throws Exception {
        var out = new java.io.ByteArrayOutputStream();
        out.write(0x30);
        var body = new java.io.ByteArrayOutputStream();
        writeInt(out, body, r);
        writeInt(out, body, s);
        byte[] b = body.toByteArray();
        if (b.length > 127) {
            out.write(0x81);
        }
        out.write(b.length);
        out.write(b);
        return out.toByteArray();
    }

    private static void writeInt(java.io.ByteArrayOutputStream out,
                                 java.io.ByteArrayOutputStream body,
                                 java.math.BigInteger v) {
        byte[] b = v.toByteArray();
        if (b.length > 1 && b[0] == 0) b = java.util.Arrays.copyOfRange(b, 1, b.length);
        body.write(0x02);
        body.write(b.length);
        body.write(b, 0, b.length);
    }
}
