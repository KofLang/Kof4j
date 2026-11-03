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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * TLS hardening slice 4 (increment 5),
 * {@code docs/development/tls-hardening-plan.md}: the PEBBLE E2E — real
 * issuance against the standard ACME test server. Pebble (Go) serves
 * {@code https://localhost:14000/dir}; pebble-challtestsrv manages the
 * DNS-01 TXT (:8053 DNS with Pebble {@code -dnsserver}, :8055 management).
 * Split out of {@code KofAcmeClientTest} (check_500/test-hygiene): the stub
 * layer tests and the real-server E2E are separate responsibilities.
 * Requires /tmp/start-acme-servers.sh up + the Pebble minica trust store
 * (/tmp/pebble-trust.p12); the test skips HONESTLY (assumeTrue) otherwise.
 */
class KofAcmePebbleE2ETest extends ServerProcessSupport {

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

    /** HTTPS request via trust-all (a prova da serve da fatia 2). */
    private String httpsRequest(int port, String raw) throws IOException {
        javax.net.ssl.SSLSocketFactory factory = TlsTrustAllSupport.insecureFactory();
        try (javax.net.ssl.SSLSocket socket =
                     (javax.net.ssl.SSLSocket) factory.createSocket("localhost", port)) {
            socket.setSoTimeout(5000);
            socket.startHandshake();
            socket.getOutputStream().write(raw.getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            StringBuilder response = new StringBuilder();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = socket.getInputStream().read(buffer)) != -1) {
                response.append(new String(buffer, 0, n, StandardCharsets.UTF_8));
            }
            return response.toString();
        }
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
        p2.waitFor(90, TimeUnit.SECONDS);
        return output;
    }

    // ── TLS hardening slice 4 (increment 5): the Pebble E2E ────────────────
    // issuance against the REAL ACME test server: Pebble (Go) on
    // https://127.0.0.1:14000/dir + pebble-challtestsrv managing the DNS-01
    // TXT via its :8055 management server (-dnsserver points Pebble's DNS
    // at :8053). The flow: newAccount → newOrder → dns01Txt → the TEST
    // publishes the TXT via /set-txt (the operator role) → acme.challenge →
    // poll until valid → finalize → the downloaded chain serves via
    // listenSecure (slice 2). Requires: /tmp/start-acme-servers.sh up +
    // the Pebble-root trust store.

    private static final boolean PEBBLE_UP =
            new java.io.File("/tmp/pebble-trust.p12").isFile();

    @Test
    void pebbleIssuesThePairAndItServes(@TempDir Path tempDir) throws Exception {
        assumeTrue(PEBBLE_UP, "Pebble E2E precisa do harness em /tmp/start-acme-servers.sh + /tmp/pebble-trust.p12 (skip honesto)");
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

        String domain = "kof-e2e.example.com";
        String challengeHost = "_acme-challenge." + domain + ".";
        String dirUrl = "https://localhost:14000/dir";
        String[] trust = {
                "-Djavax.net.ssl.trustStore=/tmp/pebble-trust.p12",
                "-Djavax.net.ssl.trustStoreType=PKCS12",
                "-Djavax.net.ssl.trustStorePassword=changeit"};

        // 0) o TXT STALE de runs anteriores sai primeiro (o challtestsrv
        // ACUMULA valores por host; um host limpo = determinismo)
        java.net.http.HttpClient mgmt = java.net.http.HttpClient.newHttpClient();
        mgmt.send(java.net.http.HttpRequest.newBuilder(
                        java.net.URI.create("http://127.0.0.1:8055/clear-txt"))
                .header("Content-Type", "application/json")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(
                        "{\"host\":\"" + challengeHost + "\"}"))
                .build(),
                java.net.http.HttpResponse.BodyHandlers.discarding());

        // 1) newAccount — o PROGRAMA Kof fala com o Pebble
        String kidOut = runHttpClient(tempDir, "main() {\n"
                + "    println(acme.newAccount(\"" + dirUrl + "\", \"" + kofLiteral(keyPem)
                + "\", \"mailto:ops@example.com\"))\n"
                + "}\n", trust);
        String pebbleKid = kidOut.strip();
        assertTrue(pebbleKid.startsWith("https://localhost:14000/my-account/"),
                "newAccount against Pebble: " + kidOut);

        // 2-6) UM processo: a MESMA ordem precisa atravessar
        // dns01Txt → challenge → finalize (a cache de ordens é POR PROCESSO —
        // um processo novo criaria OUTRA ordem com outro token e o TXT já
        // publicado nunca casaria). O http.post ao challtestsrv :8055 é o
        // papel do OPERADOR (a API do provedor DNS; o runtime acme continua
        // sem tocar DNS).
        String flow = runHttpClient(tempDir, "main() {\n"
                + "    var kid = \"" + pebbleKid + "\"\n"
                + "    var key = \"" + kofLiteral(keyPem) + "\"\n"
                + "    var dir = \"" + dirUrl + "\"\n"
                + "    var order = acme.newOrder(kid, key, dir, \"" + domain + "\")\n"
                + "    println(\"order:\" + order)\n"
                + "    var txt = acme.dns01Txt(kid, key, dir, \"" + domain + "\")\n"
                + "    println(\"txt:\" + txt)\n"
                + "    var setBody = \"{\\\"host\\\":\\\"_acme-challenge." + domain
                + ".\\\",\\\"value\\\":\\\"\" + txt + \"\\\"}\"\n"
                + "    println(\"settxt:\" + http.post(\"http://127.0.0.1:8055/set-txt\", setBody))\n"
                + "    var authz = acme.challenge(kid, key, dir, \"" + domain + "\")\n"
                + "    println(\"authz:\" + authz)\n"
                + "    var pair = acme.finalize(kid, key, dir, \"" + domain + "\")\n"
                + "    println(\"pair:\" + pair)\n"
                + "}\n", trust);
        assertTrue(flow.contains("order:https://localhost:14000/my-order/"),
                "newOrder against Pebble: " + flow);
        assertTrue(flow.contains("txt:"), "the flow output: " + flow);
        String txtValue = flow.substring(flow.indexOf("txt:") + 4).split("\n")[0].strip();
        assertTrue(txtValue.matches("[A-Za-z0-9_-]{43}"), "the DNS-01 TXT shape: " + txtValue);
        assertTrue(flow.contains("settxt:"), "the TXT publication: " + flow);
        assertTrue(flow.contains("authz:https://localhost:14000/authZ/"),
                "the challenge trigger: " + flow);
        int pairIdx = flow.indexOf("pair:");
        assertTrue(pairIdx >= 0, "the pair missing (the flow died early): " + flow);
        String pair = flow.substring(pairIdx + 5).strip();
        assertTrue(pair.startsWith("-----BEGIN CERTIFICATE-----")
                && pair.contains("-----BEGIN PRIVATE KEY-----"),
                "the issued pair: " + pair);
        // 7) o par EMITE serve via listenSecure (a fatia 2)
        String certPem = pair.split("\\n\\n")[0];
        String newKeyPem = pair.split("\\n\\n")[1];
        int tlsPort = freePort();
        Path server = tempDir.resolve("TlsApp.kf");
        Files.writeString(server, "main() {\n"
                + "    var app = web.app()\n"
                + "    app.get(\"/hello\") { return \"Hello Pebble\" }\n"
                + "    app.listenSecure(" + tlsPort + ", \"" + kofLiteral(certPem)
                + "\", \"" + kofLiteral(newKeyPem) + "\")\n"
                + "}\n");
        Path outDir = tempDir.resolve("tls_classes");
        CompilationResult r = driver.compile(server, outDir, Target.JVM);
        assertTrue(r.success(), "the issued pair compiles into the server: "
                + r.diagnostics().getDiagnostics());
        Process pb = new ProcessBuilder(JAVA_BIN, "-cp", outDir.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        serverProcess = pb;
        TestServerFixture.await(pb, tlsPort, 60, 100, () -> {
            try {
                javax.net.ssl.SSLSocketFactory f = TlsTrustAllSupport.insecureFactory();
                try (javax.net.ssl.SSLSocket sock =
                             (javax.net.ssl.SSLSocket) f.createSocket("localhost", tlsPort)) {
                    sock.setSoTimeout(200);
                    sock.startHandshake();
                    return true;
                }
            } catch (Exception e) {
                return false;
            }
        });
        String body = httpsRequest(tlsPort, "GET /hello HTTP/1.1\r\nHost: x\r\n\r\n");
        assertTrue(body.contains("Hello Pebble"), "the ISSUED pair serves: " + body);
        pb.destroyForcibly();
    }
}
