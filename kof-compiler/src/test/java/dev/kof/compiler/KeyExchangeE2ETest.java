package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * D-KOF-X25519 (maintainer vote 02/10, option a): the session-key face is
 * X25519 (RFC 7748) + HKDF-SHA256 (RFC 5869). Scalars/shared secrets travel
 * ONLY inside {@code Secret} (never raw String — R8); the public value is
 * export hex; HKDF-Expand derives per-direction key material. JVM/Android/
 * Script real today; JS/Native/cross refuse with the named SECN012 gap —
 * never silent (R6). Proves the Alice/Bob agreement, the RFC 5869 case-1
 * golden, and the redaction of the shared secret.
 */
class KeyExchangeE2ETest extends TargetGapRefusalSupport {

    private static final String HKDF_CASE1 =
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865";

    private static String agreementProgram() {
        return """
                main() {
                    var a = keyExchange.privateKey()
                    var b = keyExchange.privateKey()
                    var pa = keyExchange.publicKey(a)
                    var pb = keyExchange.publicKey(b)
                    var s1 = keyExchange.shared(a, secrets.of(pb))
                    var s2 = keyExchange.shared(b, secrets.of(pa))
                    println(s1.reveal() == s2.reveal())
                    println(pa.length)
                    println(s1.redacted())
                    var k1 = keyExchange.hkdfSha256(s1, "000102030405060708090a0b0c", "f0f1f2f3f4f5f6f7f8f9", 42)
                    var k2 = keyExchange.hkdfSha256(s2, "000102030405060708090a0b0c", "f0f1f2f3f4f5f6f7f8f9", 42)
                    println(k1 == k2)
                }
                """;
    }

    private String runJvm(Path dir, String name) throws Exception {
        Path s = dir.resolve(name + ".kf");
        Files.writeString(s, agreementProgram());
        CompilationResult r = driver.compile(s, dir.resolve("out-" + name), Target.JVM);
        assertTrue(r.success(), "must compile: " + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "--enable-native-access=ALL-UNNAMED",
                "-cp", dir.resolve("out-" + name).toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes());
        assertTrue(p.waitFor(30, TimeUnit.SECONDS), "finish");
        assertEquals(0, p.exitValue(), "exit: " + out);
        return out.trim();
    }

    @Test
    @DisplayName("D-KOF-X25519: Alice/Bob agree on the shared secret; public is 64 hex; Secret prints REDACTED; HKDF of both sides matches")
    void agreementJvm(@TempDir Path dir) throws Exception {
        String out = runJvm(dir, "agree");
        String[] lines = out.split("\n");
        assertEquals("true", lines[0], "shared secrets must agree: " + out);
        assertEquals("64", lines[1], "public value is 64 hex chars: " + out);
        assertTrue(lines[2].contains("***") && !lines[2].contains("0b0b"),
                "shared secret must print redacted (R8): " + lines[2]);
        assertEquals("true", lines[3], "HKDF over the same secret both sides: " + out);
    }

    @Test
    @DisplayName("D-KOF-X25519: HKDF-Expand-SHA256 matches the RFC 5869 case-1 golden (42 bytes)")
    void hkdfGolden(@TempDir Path dir) throws Exception {
        Path s = dir.resolve("golden.kf");
        Files.writeString(s, "main() {\n"
                + "    println(keyExchange.hkdfSha256(secrets.of(\"0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b\"), \"000102030405060708090a0b0c\", \"f0f1f2f3f4f5f6f7f8f9\", 42))\n"
                + "}\n");
        CompilationResult r = driver.compile(s, dir.resolve("out-golden"), Target.JVM);
        assertTrue(r.success(), "must compile: " + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "--enable-native-access=ALL-UNNAMED", "-cp", dir.resolve("out-golden").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes());
        assertTrue(p.waitFor(30, TimeUnit.SECONDS), "finish");
        assertEquals(0, p.exitValue(), "exit: " + out);
        assertTrue(out.contains(HKDF_CASE1), "RFC 5869 case-1: " + out);
    }

    @Test
    @DisplayName("D-KOF-X25519: Script leg runs the JVM body by construction (agreement still true)")
    void agreementScript(@TempDir Path dir) throws Exception {
        Path s = dir.resolve("sc.kf");
        Files.writeString(s, agreementProgram());
        KofInterpreter.Result r = driver.interpret(java.util.List.of(s), dir, new String[0]);
        assertEquals(0, r.exitCode(), "script must run: " + r.stderr());
        assertTrue(r.stdout().contains("true\n64\n"), "agreement on Script: " + r.stdout());
    }

    @Test
    @DisplayName("D-KOF-X25519: wrong actual on the Secret face refuses with the named SECN014 (never identity-silent)")
    void secretFaceGuard(@TempDir Path dir) throws Exception {
        Path s = dir.resolve("g.kf");
        Files.writeString(s, """
                main() {
                    println(keyExchange.publicKey("0b0b"))
                }
                """);
        CompilationResult r = driver.compile(s, dir.resolve("out-g"), Target.JVM);
        assertFalse(r.success(), "raw String must refuse");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("SECN014"),
                "must name SECN014: " + r.diagnostics().getDiagnostics());
    }

    @Test
    @DisplayName("§569: Secret declared as a parameter/return type resolves — no SEM011, no spurious SECN014 (KofShare handshake regression)")
    void secretDeclaredParameterType(@TempDir Path dir) throws Exception {
        Path s = dir.resolve("param.kf");
        Files.writeString(s, """
                Secret derive(Secret priv) {
                    var pub = keyExchange.publicKey(priv)
                    return keyExchange.shared(priv, secrets.of(pub))
                }
                main() {
                    var p = keyExchange.privateKey()
                    var shared = derive(p)
                    println(shared.redacted())
                    println(keyExchange.hkdfSha256(shared, "00", "f0f1", 16).length)
                }
                """);
        CompilationResult r = driver.compile(s, dir.resolve("out-param"), Target.JVM);
        assertTrue(r.success(), "Secret param/return must compile: " + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "--enable-native-access=ALL-UNNAMED", "-cp", dir.resolve("out-param").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(30, TimeUnit.SECONDS), "finish");
        assertEquals(0, proc.exitValue(), "exit: " + out);
        String[] lines = out.trim().split("\n");
        assertTrue(lines[0].contains("***"), "shared secret must redact (R8): " + lines[0]);
        assertEquals("32", lines[1], "16 derived bytes = 32 hex chars: " + lines[1]);
    }

    @Override
    protected String gapProgram() {
        return agreementProgram();
    }

    @Override
    protected String gapCode() {
        return "SECN012";
    }

    @Override
    protected String gapLabel() {
        return "X25519 face";
    }
}
