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
 * TLS hardening slice 4 ({@code docs/development/tls-hardening-plan.md}): the
 * ACME client — the JWS ES256 core, the ACCOUNT layer and the ORDER layer,
 * proven against a TEST-LOCAL ACME stub (Pebble is not on this host). Split
 * out of {@code KofWebTlsVerifyTest} for the check_500 ratchet; the helpers
 * are the same test-harness pattern.
 */
class KofAcmeClientTest extends ServerProcessSupport {

    private static final String JAVA_BIN = Path.of(
            System.getProperty("java.home"), "bin", "java").toString();

    private final CompilerDriver driver = new CompilerDriver();

    private int freePort() throws IOException {
        try (ServerSocket probe = new ServerSocket(0)) {
            return probe.getLocalPort();
        }
    }

    private static String mime(byte[] der) {
        return java.util.Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(der) + "\n";
    }

    /** Escapa um PEM para um literal String do Kof (mesma linha, \n). */
    private static String kofLiteral(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    /** Compila+roda um cliente kof com props extras; devolve a saída. */
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
        cmd.add(outDir.toString());
        cmd.add("Default.Main");
        Process p2 = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String output = new String(p2.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        p2.waitFor(30, TimeUnit.SECONDS);
        return output;
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

    // ── TLS hardening slice 4 (increment 2): acme.newAccount ───────────────
    // acme.newAccount(directoryUrl, accountKeyPem, contact) walks the ACME
    // account layer: GET directory → newNonce (Replay-Nonce capture) →
    // newAccount JWS with the EMBEDDED JWK + the RFC 7638 thumbprint →
    // returns the account kid (Location). Proven against a TEST-LOCAL ACME
    // stub (Pebble is not on this host — the stub implements the three
    // endpoints the account layer needs).

    /** Stub ACME mínimo: directory + newNonce + newAccount (captura o JWS). */
    private record AcmeStub(com.sun.net.httpserver.HttpServer server, int port,
                            java.util.List<String> seenJws) implements AutoCloseable {
        @Override public void close() { server.stop(0); }
    }

    private AcmeStub startAcmeStub() throws Exception {
        java.util.List<String> seen = new java.util.ArrayList<>();
        var server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/directory", ex -> {
            byte[] body = ("{\"newNonce\":\"http://127.0.0.1:" + server.getAddress().getPort()
                    + "/nonce\",\"newAccount\":\"http://127.0.0.1:" + server.getAddress().getPort()
                    + "/newAccount\",\"newOrder\":\"http://127.0.0.1:" + server.getAddress().getPort()
                    + "/newOrder\"}").getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.createContext("/nonce", ex -> {
            ex.getResponseHeaders().add("Replay-Nonce", "stub-nonce-1");
            ex.sendResponseHeaders(204, -1);
            ex.close();
        });
        server.createContext("/newAccount", ex -> {
            seen.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            ex.getResponseHeaders().add("Location", "https://acme.example/acct/7");
            ex.sendResponseHeaders(201, -1);
            ex.close();
        });
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        server.createContext("/newOrder", ex -> {
            seen.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            ex.getResponseHeaders().add("Location", base + "/order/9");
            byte[] body = ("{\"status\":\"pending\",\"authorizations\":[\"" + base
                    + "/authz/5\"]}").getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(201, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.createContext("/order/9", ex -> {
            // o poll devolve `valid` DEPOIS do finalize (contador simples)
            String poll = seen.stream().filter(x -> x.startsWith("finalize-poll:")).count() > 0
                    ? "valid" : "pending";
            byte[] body = ("{\"status\":\"" + poll + "\",\"authorizations\":[\"" + base
                    + "/authz/5\"]" + (poll.equals("valid") ? ",\"certificate\":\"" + base
                    + "/cert/1\"" : "") + "}").getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.createContext("/order/9/finalize", ex -> {
            seen.add("finalize-poll:" + new String(ex.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        server.createContext("/cert/1", ex -> {
            byte[] body = ("-----BEGIN CERTIFICATE-----\n" + java.util.Base64.getMimeEncoder(64,
                    "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(
                    "stub-cert-bytes".getBytes(StandardCharsets.US_ASCII))
                    + "-----END CERTIFICATE-----\n").getBytes(StandardCharsets.US_ASCII);
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.createContext("/authz/5", ex -> {
            // DNS-01 challenge com token fixo (o teste calcula o keyauth esperado)
            byte[] body = ("{\"status\":\"pending\",\"challenges\":[{\"type\":\"dns-01\","
                    + "\"url\":\"" + base + "/challenge/5\",\"token\":\"stub-token-9\"}]}")
                    .getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.createContext("/challenge/5", ex -> {
            seen.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            ex.getResponseHeaders().add("Location", base + "/authz/5");
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        server.start();
        return new AcmeStub(server, server.getAddress().getPort(), seen);
    }

    @Test
    void acmeNewAccountReturnsTheKid(@TempDir Path tempDir) throws Exception {
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
        String keyPem = "-----BEGIN PRIVATE KEY-----\n" + mime(
                ((java.security.PrivateKey) store.getKey("acme", "changeit".toCharArray())).getEncoded())
                + "-----END PRIVATE KEY-----\n";

        try (AcmeStub stub = startAcmeStub()) {
            String out = runHttpClient(tempDir, "main() {\n"
                    + "    println(acme.newAccount(\"http://127.0.0.1:" + stub.port()
                    + "/directory\", \"" + kofLiteral(keyPem) + "\", \"mailto:ops@example.com\"))\n"
                    + "}\n");
            assertEquals("https://acme.example/acct/7", out.strip(),
                    "newAccount returns the account kid: " + out);
            assertEquals(1, stub.seenJws().size(), "one newAccount POST: " + stub.seenJws());
            String jws = stub.seenJws().get(0);
            String[] parts = jws.split("\\.");
            assertEquals(3, parts.length, "the newAccount body is a JWS: " + jws);
            String headerJson = new String(java.util.Base64.getUrlDecoder().decode(parts[0]),
                    StandardCharsets.UTF_8);
            assertTrue(headerJson.contains("\"jwk\""), "the newAccount JWS embeds the JWK: " + headerJson);
            assertTrue(headerJson.contains("\"nonce\":\"stub-nonce-1\""),
                    "the JWS carries the captured Replay-Nonce: " + headerJson);
            String payloadJson = new String(java.util.Base64.getUrlDecoder().decode(parts[1]),
                    StandardCharsets.UTF_8);
            assertTrue(payloadJson.contains("\"termsOfServiceAgreed\":true"),
                    "the payload agrees to the ToS: " + payloadJson);
            assertTrue(payloadJson.contains("mailto:ops@example.com"), payloadJson);
        }
    }

    @Test
    void acmeOrderLayerWalksTheFlow(@TempDir Path tempDir) throws Exception {
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
        String keyPem = "-----BEGIN PRIVATE KEY-----\n" + mime(
                ((java.security.PrivateKey) store.getKey("acme", "changeit".toCharArray())).getEncoded())
                + "-----END PRIVATE KEY-----\n";

        try (AcmeStub stub = startAcmeStub()) {
            String dirUrl = "http://127.0.0.1:" + stub.port() + "/directory";
            String kid = "https://acme.example/acct/7";
            String orderUrl = runHttpClient(tempDir, "main() {\n"
                    + "    println(acme.newOrder(\"" + kid + "\", \"" + kofLiteral(keyPem)
                    + "\", \"" + dirUrl + "\", \"edge.example.com\"))\n"
                    + "}\n").strip();
            assertTrue(orderUrl.endsWith("/order/9"),
                    "newOrder returns the order URL: " + orderUrl);

            String txt = runHttpClient(tempDir, "main() {\n"
                    + "    println(acme.dns01Txt(\"" + kid + "\", \"" + kofLiteral(keyPem)
                    + "\", \"" + dirUrl + "\", \"edge.example.com\"))\n"
                    + "}\n").strip();
            // keyauth = token || "." || thumbprint(b64url); TXT = base64url(SHA-256(keyauth))
            // (captura via stub — o stub serve o token fixo "stub-token-9")
            String token = "stub-token-9";
            String thumb = computeThumbFor(tempDir, keyPem);
            String keyauth = token + "." + thumb;
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(keyauth.getBytes(StandardCharsets.UTF_8));
            String expected = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
            assertEquals(expected, txt, "the DNS-01 TXT is base64url(SHA-256(token.thumbprint)): " + txt);

            String authz = runHttpClient(tempDir, "main() {\n"
                    + "    println(acme.challenge(\"" + kid + "\", \"" + kofLiteral(keyPem)
                    + "\", \"" + dirUrl + "\", \"edge.example.com\"))\n"
                    + "}\n").strip();
            assertTrue(authz.endsWith("/authz/5"),
                    "the challenge trigger returns the authorization URL: " + authz);
            assertTrue(stub.seenJws().size() >= 3, "the flow made the JWS POSTs: "
                    + stub.seenJws().size());
        }
    }

    /** O thumbprint RFC 7638 da chave de conta (o mesmo cálculo do runtime). */
    private String computeThumbFor(Path tempDir, String keyPem) throws Exception {
        java.security.KeyFactory kf = java.security.KeyFactory.getInstance("EC");
        byte[] der = java.util.Base64.getMimeDecoder().decode(keyPem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", ""));
        java.security.PrivateKey priv = kf.generatePrivate(new java.security.spec.PKCS8EncodedKeySpec(der));
        java.security.interfaces.ECPrivateKey ec = (java.security.interfaces.ECPrivateKey) priv;
        java.security.spec.ECParameterSpec spec = ec.getParams();
        java.math.BigInteger p = ((java.security.spec.ECFieldFp) spec.getCurve().getField()).getP();
        java.math.BigInteger a = spec.getCurve().getA();
        java.security.spec.ECPoint g = spec.getGenerator();
        java.math.BigInteger px = null;
        java.math.BigInteger py = null;
        java.math.BigInteger s = ec.getS();
        for (int i = s.bitLength() - 1; i >= 0; i--) {
            if (px != null) {
                java.math.BigInteger lambda = px.pow(2).multiply(java.math.BigInteger.valueOf(3))
                        .add(a).multiply(py.shiftLeft(1).modInverse(p)).mod(p);
                java.math.BigInteger nx = lambda.pow(2).subtract(px.shiftLeft(1)).mod(p);
                java.math.BigInteger ny = lambda.multiply(px.subtract(nx)).subtract(py).mod(p);
                px = nx.mod(p); py = ny.mod(p);
            }
            if (s.testBit(i)) {
                if (px == null) { px = g.getAffineX(); py = g.getAffineY(); }
                else {
                    java.math.BigInteger lambda = g.getAffineY().subtract(py)
                            .multiply(g.getAffineX().subtract(px).modInverse(p)).mod(p);
                    java.math.BigInteger nx = lambda.pow(2).subtract(px).subtract(g.getAffineX()).mod(p);
                    java.math.BigInteger ny = lambda.multiply(px.subtract(nx)).subtract(py).mod(p);
                    px = nx.mod(p); py = ny.mod(p);
                }
            }
        }
        String x = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                leftPad32(px.toByteArray()));
        String y = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                leftPad32(py.toByteArray()));
        String canonical = "{\"crv\":\"P-256\",\"kty\":\"EC\",\"x\":\"" + x
                + "\",\"y\":\"" + y + "\"}";
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                java.security.MessageDigest.getInstance("SHA-256")
                        .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    }

    private byte[] leftPad32(byte[] v) {
        byte[] out = new byte[32];
        if (v.length > 32) System.arraycopy(v, v.length - 32, out, 0, 32);
        else System.arraycopy(v, 0, out, 32 - v.length, v.length);
        return out;
    }

    @Test
    void acmeFinalizeIssuesThePair(@TempDir Path tempDir) throws Exception {
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
        String keyPem = "-----BEGIN PRIVATE KEY-----\n" + mime(
                ((java.security.PrivateKey) store.getKey("acme", "changeit".toCharArray())).getEncoded())
                + "-----END PRIVATE KEY-----\n";

        try (AcmeStub stub = startAcmeStub()) {
            String dirUrl = "http://127.0.0.1:" + stub.port() + "/directory";
            String out = runHttpClient(tempDir, "main() {\n"
                    + "    println(acme.finalize(\"https://acme.example/acct/7\", \""
                    + kofLiteral(keyPem) + "\", \"" + dirUrl + "\", \"edge.example.com\"))\n"
                    + "}\n");
            String[] parts = out.strip().split("\n\n");
            assertEquals(2, parts.length, "finalize returns certPem + keyPem: " + out);
            assertTrue(parts[0].startsWith("-----BEGIN CERTIFICATE-----"),
                    "the cert PEM first: " + parts[0].substring(0, 30));
            assertTrue(parts[1].startsWith("-----BEGIN PRIVATE KEY-----"),
                    "the key PEM second: " + parts[1].substring(0, 30));
            // o CSR foi carregado no POST finalize (a chave de cert é NOVA, não a de conta)
            java.util.List<String> fin = stub.seenJws().stream()
                    .filter(x -> x.startsWith("finalize-poll:")).toList();
            assertEquals(1, fin.size(), "one finalize POST: " + stub.seenJws());
        }
    }
}
