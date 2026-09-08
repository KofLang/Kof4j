package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * kof.security tests (docs/security.md §17-§18): hashing, verification,
 * timing-safe comparison, JWT, secrets, redaction, target gaps and
 * adversarial cases — on the JVM, Native and JS targets.
 */
class KofSecurityTest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String PASSWORDS_SOURCE = """
            main() {
                var hash = passwords.hash("hunter2")
                println(passwords.verify("hunter2", hash))
                println(passwords.verify("wrong", hash))
                println(passwords.needsRehash(hash))
            }
            """;

    @Test
    void passwordsJvm(@TempDir Path tempDir) throws IOException {
        runJvm(tempDir, PASSWORDS_SOURCE, "true\nfalse\nfalse");
    }

    @Test
    void passwordsJs(@TempDir Path tempDir) throws IOException {
        runJs(tempDir, PASSWORDS_SOURCE, "true\nfalse\nfalse");
    }

    @Test
    void passwordsNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, PASSWORDS_SOURCE);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "passwords.hash must work on Native: "
                + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    tempDir.resolve("out").resolve("Default").resolve("Main").toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "exit code, output: " + output);
            assertEquals("true\nfalse\nfalse", output.replace("\r\n", "\n").trim(),
                    "PBKDF2 native: hash/verify/needsRehash");
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    private static final String HASH_SOURCE = """
            main() {
                println(crypto.sha256("kof"))
                println(crypto.sha256(""))
                println(crypto.sha256("The quick brown fox jumps over the lazy dog"))
                println(crypto.hmacSha256("key", "data"))
            }
            """;

    @Test
    void hashesJvm(@TempDir Path tempDir) throws IOException {
        runJvm(tempDir, HASH_SOURCE,
                "06885364bb8722440a0651a4bfaf6e8427c1e1fee29fa8969b4e84fa8e7a6635\n"
                        + "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855\n"
                        + "d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592\n"
                        + "5031fe3d989c6d1537a013fa6e739da23463fdaec3b70137d828e36ace221bd0");
    }

    @Test
    void hashesJs(@TempDir Path tempDir) throws IOException {
        runJs(tempDir, HASH_SOURCE,
                "06885364bb8722440a0651a4bfaf6e8427c1e1fee29fa8969b4e84fa8e7a6635\n"
                        + "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855\n"
                        + "d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592\n"
                        + "5031fe3d989c6d1537a013fa6e739da23463fdaec3b70137d828e36ace221bd0");
    }

    @Test
    void hashesNative(@TempDir Path tempDir) throws IOException {
        runNative(tempDir, HASH_SOURCE,
                "06885364bb8722440a0651a4bfaf6e8427c1e1fee29fa8969b4e84fa8e7a6635\n"
                        + "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855\n"
                        + "d7a8fbb307d7809469ca9abcb0082e4f8d5651e46d3cdb762d02d0bf37c9e592\n"
                        + "5031fe3d989c6d1537a013fa6e739da23463fdaec3b70137d828e36ace221bd0");
    }

    private static final String CTE_SOURCE = """
            main() {
                println(security.constantTimeEquals("abc", "abc"))
                println(security.constantTimeEquals("abc", "abd"))
                println(security.constantTimeEquals("abc", "abcx"))
                println(security.constantTimeEquals("", ""))
            }
            """;

    @Test
    void constantTimeEqualsAllTargets(@TempDir Path tempDir) throws IOException {
        String expected = "true\nfalse\nfalse\ntrue";
        runJvm(tempDir, CTE_SOURCE, expected);
        runJs(tempDir, CTE_SOURCE, expected);
        runNative(tempDir, CTE_SOURCE, expected);
    }

    private static final String RANDOM_SOURCE = """
            main() {
                println(crypto.randomHex(16).length)
                println(crypto.randomHex(16).length)
                println(crypto.randomInt(100) >= 0)
                println(crypto.randomInt(100) < 100)
            }
            """;

    @Test
    void randomAllTargets(@TempDir Path tempDir) throws IOException {
        String expected = "32\n32\ntrue\ntrue";
        runJvm(tempDir, RANDOM_SOURCE, expected);
        runJs(tempDir, RANDOM_SOURCE, expected);
        runNative(tempDir, RANDOM_SOURCE, expected);
    }

    private static final String JWT_SOURCE = """
            main() {
                var token = jwt.create("{\\"sub\\":\\"u1\\",\\"roles\\":[\\"admin\\"]}", "s3cret")
                var claims = jwt.verify(token, "s3cret")
                println(claims.contains("\\"sub\\":\\"u1\\""))
                var bad = false
                try {
                    var x = jwt.verify(token, "wrong-secret")
                    println("never")
                } catch (String e) {
                    bad = true
                }
                println(bad)
                var ok = true
                try {
                    var y = jwt.verify(token + "x", "s3cret")
                    println("never2")
                } catch (String e) {
                    ok = true
                }
                println(ok)
            }
            """;

    @Test
    void jwtJvm(@TempDir Path tempDir) throws IOException {
        runJvm(tempDir, JWT_SOURCE, "true\ntrue\ntrue");
    }

    @Test
    void jwtJs(@TempDir Path tempDir) throws IOException {
        runJs(tempDir, JWT_SOURCE, "true\ntrue\ntrue");
    }

    @Test
    void jwtNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, JWT_SOURCE);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "JWT must work on Native: "
                + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    tempDir.resolve("out").resolve("Default").resolve("Main").toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "exit code, output: " + output);
            assertEquals("true\ntrue\ntrue", output, "JWT HS256 native: create/verify/try-catch");
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    private static final String JWT_CLAIMS_SOURCE = """
            main() {
                var token = jwt.create("{\\"sub\\":\\"u1\\",\\"iss\\":\\"kof\\",\\"aud\\":\\"api\\",\\"roles\\":[\\"admin\\"]}", "s3cret")
                println(jwt.verify(token, "s3cret", "kof", "api").contains("admin"))
                var bad = false
                try {
                    var x = jwt.verify(token, "s3cret", "other", "api")
                    println("never")
                } catch (String e) {
                    bad = true
                }
                println(bad)
                var bad2 = false
                try {
                    var y = jwt.verify(token, "s3cret", "kof", "other")
                    println("never2")
                } catch (String e) {
                    bad2 = true
                }
                println(bad2)
            }
            """;

    @Test
    void jwtIssuerAudienceJvm(@TempDir Path tempDir) throws IOException {
        runJvm(tempDir, JWT_CLAIMS_SOURCE, "true\ntrue\ntrue");
    }

    @Test
    void jwtIssuerAudienceJs(@TempDir Path tempDir) throws IOException {
        runJs(tempDir, JWT_CLAIMS_SOURCE, "true\ntrue\ntrue");
    }

    @Test
    void jwtExpiredJvm(@TempDir Path tempDir) throws IOException {
        // ttl=1s: the token must be rejected after the expiry window passes.
        // now() is kof.time; a busy-wait avoids any platform dependency.
        runJvm(tempDir, """
                main() {
                    var token = jwt.create("{\\"sub\\":\\"u1\\"}", "s3cret", 1)
                    var start = now()
                    while (now() - start < 2000) {
                    }
                    var bad = false
                    try {
                        var x = jwt.verify(token, "s3cret")
                        println("never")
                    } catch (String e) {
                        bad = true
                    }
                    println(bad)
                }
                """, "true");
    }

    @Test
    void jwtRejectsAlgorithmConfusionJvm(@TempDir Path tempDir) throws IOException {
        // A token whose header claims alg=none must be rejected: the
        // algorithm is never taken from the token.
        runJvm(tempDir, """
                main() {
                    var token = jwt.create("{\\"sub\\":\\"u1\\"}", "s3cret")
                    var parts = token.split(".")
                    var bad = false
                    try {
                        var x = jwt.verify("eyJhbGciOiJub25lIn0." + parts[1] + "." + parts[2], "s3cret")
                        println("never")
                    } catch (String e) {
                        bad = true
                    }
                    println(bad)
                }
                """, "true");
    }

    private static final String SECRETS_SOURCE = """
            main() {
                println(secrets.redact("sk-abcdefghijklmnop") == "sk-a********mnop")
                println(secrets.redact("short") == "********")
                println(secrets.get("NAO_EXISTE_KOF_XYZ", "fb") == "fb")
            }
            """;

    @Test
    void secretsRedactAllTargets(@TempDir Path tempDir) throws IOException {
        String expected = "true\ntrue\ntrue";
        runJvm(tempDir, SECRETS_SOURCE, expected);
        runJs(tempDir, SECRETS_SOURCE, expected);
        runNative(tempDir, SECRETS_SOURCE, expected);
    }

    @Test
    void secretsEnvJvm(@TempDir Path tempDir) throws IOException {
        String home = System.getenv("HOME");
        if (home == null) {
            home = System.getenv("USERPROFILE");
        }
        if (home == null) {
            return;
        }
        Path source = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(source, """
                main() {
                    var v = secrets.get("HOME")
                    println(v == "%s")
                }
                """.formatted(home));
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), result.diagnostics().getDiagnostics().toString());
        try {
            Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                    "-cp", outDir.toString(), "Default.Main").redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "exit code, output: " + output);
            assertEquals("true", output, "HOME must be readable via secrets.get");
        } catch (InterruptedException e) {
            throw new IOException("interrupted", e);
        }
    }

    @Test
    void aesGcmRoundTripJvm(@TempDir Path tempDir) throws IOException {
        runJvm(tempDir, """
                main() {
                    var key = crypto.randomHex(32)
                    var ct = crypto.encryptAesGcm("segredo", key)
                    println(crypto.decryptAesGcm(ct, key) == "segredo")
                    var bad = false
                    try {
                        var x = crypto.decryptAesGcm(ct + "AA", key)
                        println("never")
                    } catch (String e) {
                        bad = true
                    }
                    println(bad)
                    var bad2 = false
                    try {
                        var y = crypto.decryptAesGcm(ct, crypto.randomHex(32))
                        println("never2")
                    } catch (String e) {
                        bad2 = true
                    }
                    println(bad2)
                }
                """, "true\ntrue\ntrue");
    }

    @Test
    void aesGcmJsRoundTrip(@TempDir Path tempDir) throws IOException {
        // SECN002 fechado 01/09: AES-256-GCM puro JS (GraalJS + browser),
        // formato idêntico ao JVM/Native. Roundtrip + detecção de tamper +
        // chave errada (mesmo contrato do teste JVM).
        runJs(tempDir, """
                main() {
                    var key = crypto.randomHex(32)
                    var ct = crypto.encryptAesGcm("segredo", key)
                    println(crypto.decryptAesGcm(ct, key) == "segredo")
                    var bad = false
                    try {
                        var x = crypto.decryptAesGcm(ct + "AA", key)
                        println("never")
                    } catch (String e) {
                        bad = true
                    }
                    println(bad)
                    var bad2 = false
                    try {
                        var y = crypto.decryptAesGcm(ct, crypto.randomHex(32))
                        println("never2")
                    } catch (String e) {
                        bad2 = true
                    }
                    println(bad2)
                }
                """, "true\ntrue\ntrue");
    }

    @Test
    void aesGcmCrossTargetParityJvmToJs(@TempDir Path tempDir) throws IOException {
        // Paridade byte-a-byte: o ciphertext produzido no JVM (AES/GCM do JDK)
        // é decifrado pelo runtime JS puro — mesma chave, mesmo formato.
        String key = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";
        Path encSrc = tempDir.resolve("Enc.kf");
        Files.writeString(encSrc, """
            main() {
                var ct = crypto.encryptAesGcm("paridade-jvm-js", "%s")
                println(ct)
            }
            """.formatted(key));
        CompilationResult enc = driver.compile(encSrc, tempDir.resolve("out-enc"), Target.JVM);
        assertTrue(enc.success(), "JVM encrypt: " + enc.diagnostics().getDiagnostics());
        String ct;
        try {
            Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                    "-cp", tempDir.resolve("out-enc").toString(), "Default.Main")
                    .redirectErrorStream(true).start();
            ct = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            assertEquals(0, p.waitFor(), "JVM encrypt exit");
        } catch (InterruptedException e) {
            throw new IOException("interrupted", e);
        }
        assertTrue(ct.startsWith("aesgcm$"), "formato esperado: " + ct);
        // JS decifra o ciphertext do JVM
        String decSource = """
            main() {
                println(crypto.decryptAesGcm("%s", "%s"))
            }
            """.formatted(ct, key);
        runJs(tempDir, decSource, "paridade-jvm-js");
    }

    @Test
    void aesGcmCrossTargetParityJsToJvm(@TempDir Path tempDir) throws IOException {
        // Paridade inversa: o ciphertext produzido pelo runtime JS puro é
        // decifrado pelo JVM (AES/GCM do JDK).
        String key = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";
        Path encSrc = tempDir.resolve("EncJs.kf");
        Files.writeString(encSrc, """
            main() {
                var ct = crypto.encryptAesGcm("paridade-js-jvm", "%s")
                println(ct)
            }
            """.formatted(key));
        CompilationResult enc = driver.compile(encSrc, tempDir.resolve("out-encjs"), Target.JS);
        assertTrue(enc.success(), "JS encrypt: " + enc.diagnostics().getDiagnostics());
        String ct;
        try (java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream()) {
            int ec = dev.kof.runtime.KofJsRunner.run(findJsEntry(tempDir.resolve("out-encjs")), buf,
                    java.io.InputStream.nullInputStream(), new java.io.ByteArrayOutputStream());
            assertEquals(0, ec, "JS encrypt exit");
            ct = buf.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
        }
        assertTrue(ct.startsWith("aesgcm$"), "formato esperado: " + ct);
        // JVM decifra o ciphertext do JS
        String decSource = """
            main() {
                println(crypto.decryptAesGcm("%s", "%s"))
            }
            """.formatted(ct, key);
        runJvm(tempDir, decSource, "paridade-js-jvm");
    }

    @Test
    void aesGcmNativeRoundTrip(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("Main.kf");
        String key = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";
        Files.writeString(source, """
            main() {
                var key = "%s"
                var ct = crypto.encryptAesGcm("mensagem secreta", key)
                var pt = crypto.decryptAesGcm(ct, key)
                println(pt)
            }
            """.formatted(key));
        CompilationResult nativeResult = driver.compile(source, tempDir.resolve("out-n"), Target.NATIVE);
        assertTrue(nativeResult.success(), () -> nativeResult.diagnostics().getDiagnostics().toString());
        Path bin = tempDir.resolve("out-n/Default/Main");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        p.waitFor();
        assertTrue(output.contains("mensagem secreta"), () -> "output: " + output);
    }

    @Test
    void jwtIssAudNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, JWT_CLAIMS_SOURCE);
        CompilationResult nativeResult = driver.compile(source, tempDir.resolve("out-n"), Target.NATIVE);
        assertTrue(nativeResult.success(),
                "JWT iss/aud must work on Native: " + nativeResult.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    tempDir.resolve("out-n").resolve("Default").resolve("Main").toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "exit code, output: " + output);
            assertEquals("true\ntrue\ntrue", output, "JWT iss/aud native");
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
        CompilationResult jsResult = driver.compile(source, tempDir.resolve("out-j"), Target.JS);
        assertTrue(jsResult.success(), "JWT works on JS: " + jsResult.diagnostics().getDiagnostics());
        CompilationResult jvmResult = driver.compile(source, tempDir.resolve("out-v"), Target.JVM);
        assertTrue(jvmResult.success(), "JWT works on JVM: " + jvmResult.diagnostics().getDiagnostics());
    }

    @Test
    void sha512NativeVectors(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    println(crypto.sha512("abc"))
                    println(crypto.sha512(""))
                    println(crypto.sha512("The quick brown fox jumps over the lazy dog"))
                }
                """);
        CompilationResult result = driver.compile(source, tempDir.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "sha512 must work on Native: "
                + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder(tempDir.resolve("out").resolve("Default").resolve("Main").toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "exit code, output: " + output);
            String expected = "ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a2"
                    + "192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f\n"
                    + "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce4"
                    + "7d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e\n"
                    + "07e547d9586f6a73f73fbac0435ed76951218fb7d0c8d788a309d785436bbb642"
                    + "e93a252a954f23912547d1e8a3b5ed6e1bfd7097821233fa0538f3db854fee6";
            assertEquals(expected, output, "SHA-512 FIPS vectors on Native");
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    @Test
    void malformedJwtRejectedJvm(@TempDir Path tempDir) throws IOException {
        runJvm(tempDir, """
                main() {
                    var bad = false
                    try {
                        var x = jwt.verify("not-a-token", "s3cret")
                        println("never")
                    } catch (String e) {
                        bad = true
                    }
                    println(bad)
                    var bad2 = false
                    try {
                        var y = jwt.verify("a.b.c.d", "s3cret")
                        println("never2")
                    } catch (String e) {
                        bad2 = true
                    }
                    println(bad2)
                }
                """, "true\ntrue");
    }

    @Test
    void malformedJwtRejectedJs(@TempDir Path tempDir) throws IOException {
        runJs(tempDir, """
                main() {
                    var bad = false
                    try {
                        var x = jwt.verify("not-a-token", "s3cret")
                        println("never")
                    } catch (String e) {
                        bad = true
                    }
                    println(bad)
                    var bad2 = false
                    try {
                        var y = jwt.verify("a.b.c.d", "s3cret")
                        println("never2")
                    } catch (String e) {
                        bad2 = true
                    }
                    println(bad2)
                }
                """, "true\ntrue");
    }

    @Test
    void jwtCreateRejectsNonObjectClaims(@TempDir Path tempDir) throws IOException {
        runJvm(tempDir, """
                main() {
                    var bad = false
                    try {
                        var t = jwt.create("[1,2,3]", "s3cret")
                        println("never")
                    } catch (String e) {
                        bad = true
                    }
                    println(bad)
                }
                """, "true");
    }

    // ── harness ─────────────────────────────────────────────────────

    private String runJvm(Path tempDir, String source, String expected) throws IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        try {
            Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                    "-cp", outDir.toString(), "Default.Main").redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code, output: " + output);
            if (expected != null) assertEquals(expected, output, "JVM output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("interrupted", e);
        }
    }

    private String runNative(Path tempDir, String source, String expected) throws IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.NATIVE);
        assertTrue(result.success(), "Native compile failed: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        try {
            Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Native exit code, output: " + output);
            if (expected != null) assertEquals(expected, output, "Native output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("interrupted", e);
        }
    }

    private String runJs(Path tempDir, String source, String expected) throws IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JS);
        assertTrue(result.success(), "JS compile failed: " + result.diagnostics().getDiagnostics());
        try (java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream()) {
            int ec = dev.kof.runtime.KofJsRunner.run(findJsEntry(outDir), buf,
                    java.io.InputStream.nullInputStream(), new java.io.ByteArrayOutputStream());
            String output = buf.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
            assertEquals(0, ec, "JS exit code, output: " + output);
            if (expected != null) assertEquals(expected, output, "JS output");
            return output;
        }
    }

    private static Path findJsEntry(Path dir) throws IOException {
        try (var s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(".mjs"))
                    .filter(p -> !p.toString().contains("kof-runtime"))
                    .findFirst().orElseThrow();
        }
    }

    @Test
    void crossNativeReportsSecn000(@TempDir Path tmp) throws IOException {
        // R6: o runtime riscv64/aarch64 não tem kof_sec_* — gate SECN000 em
        // compile-time (antes: undefined-reference no ld, erro feio).
        Path source = tmp.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                println(crypto.sha256("abc"))
            }
            """);
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            CompilationResult r = driver.compile(source, tmp.resolve("cross-" + t), t);
            assertFalse(r.success(), t + " deve reportar SECN000");
            assertTrue(r.diagnostics().getDiagnostics().toString().contains("SECN000"),
                    t + ": " + r.diagnostics().getDiagnostics());
        }
    }
}