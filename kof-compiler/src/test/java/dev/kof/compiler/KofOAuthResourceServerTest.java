package dev.kof.compiler;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D-SEC camada 16: OAuth2 resource server (JWKS + RS/ES) — validação de JWT
 * de terceiro. Um JWKS local é servido em {@code com.sun.net.httpserver} e o
 * token é assinado RS256 no teste; o programa Kof busca as chaves e valida.
 */
class KofOAuthResourceServerTest {

    private static final String JAVA_BIN = Path.of(
            System.getProperty("java.home"), "bin", "java").toString();

    private final CompilerDriver driver = new CompilerDriver();
    private HttpServer jwksServer;
    private Process serverProcess;

    @AfterEach
    void cleanup() {
        if (serverProcess != null) {
            serverProcess.destroy();
            try {
                serverProcess.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
            serverProcess.destroyForcibly();
            serverProcess = null;
        }
        if (jwksServer != null) {
            jwksServer.stop(0);
            jwksServer = null;
        }
    }

    private static String b64url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String b64url(String s) {
        return b64url(s.getBytes(StandardCharsets.UTF_8));
    }

    /** JWKS local com a chave pública RSA do par de teste. */
    private String startJwks(RSAPublicKey pub) throws IOException {
        String n = b64url(pub.getModulus().toByteArray()[0] == 0
                ? java.util.Arrays.copyOfRange(pub.getModulus().toByteArray(), 1,
                        pub.getModulus().toByteArray().length)
                : pub.getModulus().toByteArray());
        String e = b64url(pub.getPublicExponent().toByteArray());
        String jwks = "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"test-key\",\"use\":\"sig\","
                + "\"alg\":\"RS256\",\"n\":\"" + n + "\",\"e\":\"" + e + "\"}]}";
        jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jwksServer.createContext("/jwks", exchange -> {
            byte[] body = jwks.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        jwksServer.start();
        return "http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/jwks";
    }

    private String signRs256(KeyPair kp, String claimsJson) throws Exception {
        long now = System.currentTimeMillis() / 1000;
        String head = claimsJson.substring(0, claimsJson.lastIndexOf('}')).trim();
        String sep = head.isEmpty() || head.endsWith("{") ? "" : ",";
        String payload = head + sep + "\"iat\":" + now + ",\"exp\":" + (now + 3600) + "}";
        String headerB64 = b64url("{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"test-key\"}");
        String payloadB64 = b64url(payload);
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(kp.getPrivate());
        sig.update((headerB64 + "." + payloadB64).getBytes(StandardCharsets.UTF_8));
        return headerB64 + "." + payloadB64 + "." + b64url(sig.sign());
    }

    private String runJvm(Path tempDir, String source, String expected) throws IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "compile: " + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder(JAVA_BIN, "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "exit, output: " + output);
            if (expected != null) assertEquals(expected, output, "output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    @Test
    void resourceServerVerifiesThirdPartyJwt(@TempDir Path tempDir) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair kp = gen.generateKeyPair();
        String jwksUrl = startJwks((RSAPublicKey) kp.getPublic());

        String valid = signRs256(kp, "{\"sub\":\"u1\",\"iss\":\"https://issuer.example\","
                + "\"aud\":\"my-api\",\"roles\":[\"admin\"]}");
        String wrongIss = signRs256(kp, "{\"sub\":\"u1\",\"iss\":\"https://evil.example\","
                + "\"aud\":\"my-api\"}");
        String wrongAud = signRs256(kp, "{\"sub\":\"u1\",\"iss\":\"https://issuer.example\","
                + "\"aud\":\"other-api\"}");

        String source = """
                main() {
                    var ok = auth.resourceServer("%s", "https://issuer.example", "my-api")
                    println(ok)
                    println(auth.resourceServerVerify("%s") != null)
                    println(auth.resourceServerVerify("%s") != null)
                    println(auth.resourceServerVerify("%s") != null)
                    println(auth.resourceServerVerify("not-a-jwt") != null)
                    println(auth.resourceServerVerify(null) != null)
                }
                """.formatted(jwksUrl, valid, wrongIss, wrongAud);
        runJvm(tempDir, source, "true\ntrue\nfalse\nfalse\nfalse\nfalse");
    }

    @Test
    void resourceServerRejectsAlgConfusionAndTamper(@TempDir Path tempDir) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair kp = gen.generateKeyPair();
        String jwksUrl = startJwks((RSAPublicKey) kp.getPublic());

        String valid = signRs256(kp, "{\"sub\":\"u1\"}");
        String[] parts = valid.split("\\.");
        // alg=none com a assinatura original — nunca passa.
        String none = b64url("{\"alg\":\"none\",\"typ\":\"JWT\"}") + "." + parts[1] + ".";
        // assinatura adulterada.
        String tampered = parts[0] + "." + parts[1] + "." + b64url("AAAA");

        String source = """
                main() {
                    var ok = auth.resourceServer("%s", "", "")
                    println(auth.resourceServerVerify("%s") != null)
                    println(auth.resourceServerVerify("%s") != null)
                    println(auth.resourceServerVerify("%s") != null)
                }
                """.formatted(jwksUrl, valid, none, tampered);
        runJvm(tempDir, source, "true\nfalse\nfalse");
    }

    @Test
    void resourceServerIntegratesWithAppSecurity(@TempDir Path tempDir) throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair kp = gen.generateKeyPair();
        String jwksUrl = startJwks((RSAPublicKey) kp.getPublic());
        String token = signRs256(kp, "{\"sub\":\"u1\",\"iss\":\"https://issuer.example\","
                + "\"aud\":\"my-api\"}");

        int port = freePort();
        String app = """
                main() {
                    var ok = auth.resourceServer("%s", "https://issuer.example", "my-api")
                    var app = web.app()
                    var o = mapOf()
                    o.put("auth", true)
                    app.security(o)
                    app.get("/me") { return "hi " + auth.user() }
                    app.listen(%d)
                }
                """.formatted(jwksUrl, port);
        Path file = tempDir.resolve("App.kf");
        Files.writeString(file, app);
        Path outDir = tempDir.resolve("classes");
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "compile: " + result.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(JAVA_BIN, "-cp", outDir.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        serverProcess = pb.start();
        waitListening(port);

        assertTrue(request(port, "GET /me HTTP/1.1\r\nHost: x\r\n\r\n")
                .startsWith("HTTP/1.1 401 Unauthorized"));
        assertTrue(request(port, "GET /me HTTP/1.1\r\nHost: x\r\nAuthorization: Bearer " + token
                + "\r\n\r\n").startsWith("HTTP/1.1 200 OK"));
    }

    @Test
    void resourceServerGapOnNativeAndJs(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    var ok = auth.resourceServer("http://x/jwks", "", "")
                    println(ok)
                }
                """);
        for (Target target : new Target[] {Target.NATIVE, Target.JS}) {
            CompilationResult r = driver.compile(source, tempDir.resolve("out-" + target), target);
            assertFalse(r.success(), target + " deve reportar SECN007");
            assertTrue(r.diagnostics().getDiagnostics().toString().contains("SECN007"),
                    target + ": " + r.diagnostics().getDiagnostics());
        }
    }

    private void waitListening(int port) throws IOException {
        if (serverProcess == null) {
            throw new IOException("server process was not started");
        }
        TestServerFixture.awaitListening(serverProcess, port);
    }

    private int freePort() throws IOException {
        try (ServerSocket probe = new ServerSocket(0)) {
            return probe.getLocalPort();
        }
    }

    private String request(int port, String raw) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5000);
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
}
