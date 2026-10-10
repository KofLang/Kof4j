package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * D-KOF-SIGN (C1, mantenedora 03/10): Ed25519 sobre a primitiva do JDK.
 * `keyExchange.privateKey("Ed25519")` gera Secret (hex PKCS8||SPKI — nunca
 * revelado, R8); `crypto.sign(Secret, Byte[])` devolve 128-hex; `verify`
 * aceita o Secret privado OU um de 64-hex público via `secrets.of(hex)`.
 * JS/Native/cross recusam com o gap nomeado SECN013.
 */
class CryptoSignE2ETest extends TargetGapRefusalSupport {

    private static String program() {
        return """
                Byte[] abc() {
                    var b = new Byte[3]
                    b[0] = 97
                    b[1] = 98
                    b[2] = 99
                    return b
                }

                main() {
                    var k = keyExchange.privateKey("Ed25519")
                    var pub = keyExchange.publicKey(k)
                    println("publen=" + pub.length())
                    var sig = crypto.sign(k, abc())
                    println("siglen=" + sig.length())
                    println("verify=" + crypto.verify(k, abc(), sig))
                    println("verifyPub=" + crypto.verify(secrets.of(pub), abc(), sig))
                    var tampered = abc()
                    tampered[0] = 98
                    println("tamper=" + !crypto.verify(secrets.of(pub), tampered, sig))
                    var badsig = "ff" + sig.substring(2)
                    println("badsig=" + !crypto.verify(secrets.of(pub), abc(), badsig))
                    var k2 = keyExchange.privateKey("Ed25519")
                    println("distinct=" + (crypto.sign(k2, abc()) != sig))
                    println(k2)
                    println("SIGN-OK")
                }
                """;
    }

    private void assertGolden(String out) {
        assertTrue(out.contains("publen=64"), "64-hex raw public: " + out);
        assertTrue(out.contains("siglen=128"), "128-hex signature: " + out);
        assertTrue(out.contains("verify=true"), out);
        assertTrue(out.contains("verifyPub=true"), "public-only verify: " + out);
        assertTrue(out.contains("tamper=true"), "flipped message refuses: " + out);
        assertTrue(out.contains("badsig=true"), "flipped signature refuses: " + out);
        assertTrue(out.contains("distinct=true"), "nondeterministic keygen: " + out);
        assertTrue(out.contains("Secret(***"), "R8 redaction on println: " + out);
        assertFalse(out.matches("(?s).*[0-9a-f]{40}.*"), "key material must never leak: " + out);
        assertTrue(out.contains("SIGN-OK"), out);
    }

    @Test
    @DisplayName("D-KOF-SIGN JVM: Ed25519 sign/verify round-trip with named refusals")
    void jvmSignVerify(@TempDir Path dir) throws Exception {
        Path s = dir.resolve("Sign.kf");
        Files.writeString(s, program());
        CompilationResult r = driver.compile(s, dir.resolve("out-jvm"), Target.JVM);
        assertTrue(r.success(), "must compile: " + r.diagnostics().getDiagnostics());
        var proc = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                "-cp", dir.resolve("out-jvm").toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, proc.waitFor(), "exit wrong: " + out);
        assertGolden(out);
    }

    @Test
    @DisplayName("D-KOF-SIGN Script: same golden under the interpreter")
    void scriptSignVerify(@TempDir Path dir) throws Exception {
        Path s = dir.resolve("Sign.kf");
        Files.writeString(s, program());
        KofInterpreter.Result r = driver.interpret(java.util.List.of(s), dir, new String[0]);
        assertEquals(0, r.exitCode(), "script exit: " + r.stderr());
        assertGolden(r.stdout());
    }

    @Override
    protected String gapProgram() {
        return program();
    }

    @Override
    protected String gapCode() {
        return "SECN013";
    }

    @Override
    protected String gapLabel() {
        return "Ed25519 face";
    }
}
