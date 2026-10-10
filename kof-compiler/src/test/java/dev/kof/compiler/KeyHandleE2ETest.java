package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * D-SECRETS P3 (Stage 5 / tracker 3.6): {@code KeyHandle}. A named key that
 * never exposes bytes to the guest — only the crypto algorithms consume it.
 * {@code rotate()} returns a new handle and revokes the old one; using the
 * revoked handle fails with {@code SECN010} (honest, never silent). JVM + Android
 * (parity by backend, §278); JS/Native/Script stay a compile-time gap {@code SECN008}.
 */
class KeyHandleE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String KEY32 =
            "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";

    @Test
    void hmacWithAHandleWorksAndTheHandleNeverPrintsItsBytesJvm(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("key1.kf");
        Files.writeString(src, """
                main() {
                    val k = secrets.keyFromHex("%s")
                    val a = crypto.hmacSha256(k, "data")
                    val b = crypto.hmacSha256(k, "data")
                    val c = crypto.hmacSha256(k, "other")
                    println(a == b)
                    println(a == c)
                    println(k)
                }
                """.formatted(KEY32));
        CompilationResult r = driver.compile(src, dir.resolve("out-key1"), Target.JVM);
        assertTrue(r.success(), "KeyHandle hmac must bind on JVM: " + r.diagnostics().getDiagnostics());
        assertEquals("true\nfalse\nKeyHandle(*** )", runJvm(dir.resolve("out-key1")),
                "deterministic MAC for the same key/data; println never leaks the key bytes");
    }

    @Test
    void keyHandleThroughGenericContainerErasureJvm(@TempDir Path dir) throws IOException {
        // Same erasure family as the Secret case: KeyHandle out of a `List`
        // must map its checkcast owner to KofRuntime$KeyHandle, not `kof/KeyHandle`.
        Path src = dir.resolve("key-erasure.kf");
        Files.writeString(src, """
                main() {
                    val k = secrets.keyFromHex("%s")
                    val box = listOf(k)
                    println(box.get(0))
                }
                """.formatted(KEY32));
        CompilationResult r = driver.compile(src, dir.resolve("out-key-erasure"), Target.JVM);
        assertTrue(r.success(), "KeyHandle through a List must compile: " + r.diagnostics().getDiagnostics());
        assertEquals("KeyHandle(*** )", runJvm(dir.resolve("out-key-erasure")),
                "KeyHandle read out of a generic container never leaks (erasure owner maps to the runtime type)");
    }

    @Test
    void aesGcmRoundTripWithAHandleJvm(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("key2.kf");
        Files.writeString(src, """
                main() {
                    val k = secrets.keyFromHex("%s")
                    val ct = crypto.encryptAesGcm("hello-secret", k)
                    println(crypto.decryptAesGcm(ct, k))
                }
                """.formatted(KEY32));
        CompilationResult r = driver.compile(src, dir.resolve("out-key2"), Target.JVM);
        assertTrue(r.success(), "aesgcm with KeyHandle must bind on JVM: " + r.diagnostics().getDiagnostics());
        assertEquals("hello-secret", runJvm(dir.resolve("out-key2")));
    }

    @Test
    void jwtCreateAndVerifyWithAHandleJvm(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("key3.kf");
        Files.writeString(src, """
                main() {
                    val k = secrets.keyFromHex("%s")
                    val token = jwt.create("{\\"sub\\":\\"1\\"}", k)
                    println(jwt.verify(token, k).contains("sub"))
                }
                """.formatted(KEY32));
        CompilationResult r = driver.compile(src, dir.resolve("out-key3"), Target.JVM);
        assertTrue(r.success(), "jwt with KeyHandle must bind on JVM: " + r.diagnostics().getDiagnostics());
        assertEquals("true", runJvm(dir.resolve("out-key3")));
    }

    @Test
    void rotateRevokesTheOldHandleJvm(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("key4.kf");
        Files.writeString(src, """
                main() {
                    val k = secrets.keyFromHex("%s")
                    val k2 = k.rotate()
                    println(k2)
                    println(crypto.hmacSha256(k, "x"))
                }
                """.formatted(KEY32));
        CompilationResult r = driver.compile(src, dir.resolve("out-key4"), Target.JVM);
        assertTrue(r.success(), "rotate must compile: " + r.diagnostics().getDiagnostics());
        String out = runJvmReflective(dir.resolve("out-key4"));
        assertTrue(out.contains("SECN010"), "using a revoked handle must fail naming SECN010: " + out);
        assertFalse(out.contains(KEY32), "the revoked key bytes must never appear");
    }

    @Test
    void keyHandleGapsOnEveryNonJvmTarget(@TempDir Path dir) throws IOException {
        String kof = """
                main() {
                    val k = secrets.keyFromHex("00")
                    println(crypto.hmacSha256(k, "x"))
                }
                """;
        // §278: ANDROID now runs kof.security (reuses the JVM) — no longer a gap.
        for (Target t : new Target[] { Target.JS, Target.NATIVE, Target.NATIVE_RISCV64,
                Target.NATIVE_AARCH64 }) {
            Path src = dir.resolve("keygap-" + t.name() + ".kf");
            Files.writeString(src, kof);
            CompilationResult r = driver.compile(src, dir.resolve("out-keygap-" + t.name()), t);
            assertFalse(r.success(), t + ": KeyHandle must stay an honest gap (no silent stub)");
            assertTrue(r.diagnostics().getDiagnostics().toString().contains("SECN008"),
                    t + ": expected SECN008, got: " + r.diagnostics().getDiagnostics());
        }
    }

    private String runJvm(Path outDir) throws IOException {
        return tailJvm(outDir).trim();
    }

    /** AGENTS JavaFX rule: the `java Default.Main` launcher swallows the real
     *  failure behind the JavaFX message. Reflection surfaces the true cause —
     *  the only way to observe the revoked-handle SECN010 hard failure. */
    private String runJvmReflective(Path outDir) throws IOException {
        try {
            String javaHome = System.getProperty("java.home");
            Files.writeString(outDir.resolve("Run.java"), """
                    public class Run {
                        public static void main(String[] args) throws Exception {
                            Class.forName("Default.Main")
                                    .getMethod("main", String[].class)
                                    .invoke(null, (Object) new String[0]);
                        }
                    }
                    """);
            Process javac = new ProcessBuilder(
                    Path.of(javaHome, "bin", "javac").toString(),
                    "-cp", outDir.toString(), "-d", outDir.toString(),
                    outDir.resolve("Run.java").toString())
                    .redirectErrorStream(true).start();
            javac.getInputStream().readAllBytes();
            if (javac.waitFor() != 0) throw new IOException("javac Run.java failed");
            Process p = new ProcessBuilder(
                    Path.of(javaHome, "bin", "java").toString(),
                    "-cp", outDir.toString(), "Run")
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
            p.waitFor();
            return out;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    private String tailJvm(Path outDir) throws IOException {
        try {
            String javaHome = System.getProperty("java.home");
            ProcessBuilder pb = new ProcessBuilder(
                    Path.of(javaHome, "bin", "java").toString(),
                    "-cp", outDir.toString(),
                    "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
            p.waitFor();
            return output;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }
}
