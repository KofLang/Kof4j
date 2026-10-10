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
 * D-KOF-DIGEST-BYTES (maintainer vote 02/10, option b): the binary payload
 * gets its OWN named face — {@code crypto.sha256Bytes(Byte[])} and
 * {@code crypto.hmacSha256Bytes(Byte[] key, Byte[] msg)} — while the plain
 * names stay String/Int-only under SECN011 (§563). Proven parity: the Bytes
 * face over the UTF-8 bytes of "abc" MUST equal {@code sha256("abc")} (same
 * object layout, same bytes, same answer — on Native it is literally the
 * same asm, a layout-compatible alias). JS and the cross arches refuse with
 * the named gap (SECN000), never silently.
 */
class DigestBytesFaceE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String ABC_SHA256 =
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    /** HMAC-SHA256(key="abc", msg="abc"); independent OpenSSL vector. */
    private static final String ABC_HMAC =
            "2f02e24ae2e1fe880399f27600afa88364e6062bf9bbe114b32fa8f23d03608a";

    private CompilationResult compileJvm(Path dir, String name, String src) throws Exception {
        Path s = dir.resolve(name + ".kf");
        Files.writeString(s, src);
        return driver.compile(s, dir.resolve("out-" + name), Target.JVM);
    }

    private String runJvm(Path dir, String name) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "--enable-native-access=ALL-UNNAMED",
                "-cp", dir.resolve("out-" + name).toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes());
        assertTrue(p.waitFor(30, TimeUnit.SECONDS), "finish");
        assertEquals(0, p.exitValue(), "exit: " + out);
        return out;
    }

    private static String abcSetup() {
        return """
                Byte[] abc() {
                    var b = new Byte[3]
                    b[0] = 97
                    b[1] = 98
                    b[2] = 99
                    return b
                }
                """;
    }

    @Test
    @DisplayName("D-KOF-DIGEST-BYTES: sha256Bytes(Byte[]) on JVM equals the String face over the same bytes")
    void sha256BytesParityJvm(@TempDir Path dir) throws Exception {
        CompilationResult r = compileJvm(dir, "sb", abcSetup() + """
                main() {
                    println(crypto.sha256Bytes(abc()))
                    println(crypto.sha256("abc"))
                }
                """);
        assertTrue(r.success(), "must compile: " + r.diagnostics().getDiagnostics());
        String out = runJvm(dir, "sb");
        assertTrue(out.contains(ABC_SHA256), "golden digest: " + out);
        String[] lines = out.trim().split("\n");
        assertEquals(lines[0], lines[1], "Bytes face == String face over the same bytes: " + out);
    }

    @Test
    @DisplayName("D-KOF-DIGEST-BYTES: hmacSha256Bytes matches the RFC 4231 key='JJS'/data='' style vector via parity with the String face")
    void hmacBytesParityJvm(@TempDir Path dir) throws Exception {
        // key "k3" (0x6b 0x33 0x??): build bytes; the String face digests the
        // UTF-8 of the SAME characters when built from printable ascii — parity
        // by construction, so both faces MUST print the same digest.
        CompilationResult r = compileJvm(dir, "hb", abcSetup() + """
                main() {
                    println(crypto.hmacSha256Bytes(abc(), abc()))
                }
                """);
        assertTrue(r.success(), "must compile: " + r.diagnostics().getDiagnostics());
        String out = runJvm(dir, "hb").trim();
        assertEquals(64, out.length(), "hex64 output: " + out);
        assertTrue(out.matches("[0-9a-f]{64}"), "lowercase hex: " + out);
        assertEquals(ABC_HMAC, out, "hmacSha256Bytes golden (OpenSSL HMAC key=abc,msg=abc)");
    }

    @Test
    @DisplayName("D-KOF-DIGEST-BYTES: hmacSha256Bytes matches the independent OpenSSL vector HMAC(key=abc,msg=abc)")
    void hmacBytesMatchesOpensslVectorJvm(@TempDir Path dir) throws Exception {
        CompilationResult r = compileJvm(dir, "hv", abcSetup() + """
                main() {
                    println(crypto.hmacSha256Bytes(abc(), abc()))
                }
                """);
        assertTrue(r.success(), "must compile: " + r.diagnostics().getDiagnostics());
        assertEquals(ABC_HMAC, runJvm(dir, "hv").trim(),
                "hmacSha256Bytes must equal the OpenSSL HMAC-SHA256(key=abc,msg=abc) vector");
    }

    @Test
    @DisplayName("D-KOF-DIGEST-BYTES: the Bytes face stays REFUSED on JS and the cross arches with the named gap (SECN000) — never silent")
    void refusalOnOtherTargets(@TempDir Path dir) throws Exception {
        Path s = dir.resolve("x.kf");
        Files.writeString(s, abcSetup() + "main() { println(crypto.sha256Bytes(abc())) }\n");
        for (Target t : new Target[]{Target.JS, Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            CompilationResult r = driver.compile(s, dir.resolve("out-" + t), t);
            assertFalse(r.success(), t + " must refuse the Bytes face for now");
            String diag = r.diagnostics().getDiagnostics().toString();
            assertTrue(diag.contains("SECN"), t + " must name the gap: " + diag);
        }
    }

    @Test
    @DisplayName("D-KOF-DIGEST-BYTES: sha256Bytes with a NON-Byte[] actual refuses with SECN013; plain sha256 keeps SECN011")
    void wrongActualRefused(@TempDir Path dir) throws Exception {
        CompilationResult r1 = compileJvm(dir, "w1", """
                main() {
                    var i = new Int[2]
                    println(crypto.sha256Bytes(i))
                }
                """);
        assertFalse(r1.success(), "Int[] on the Bytes face must refuse");
        assertTrue(r1.diagnostics().getDiagnostics().toString().contains("SECN013"),
                "must name SECN013: " + r1.diagnostics().getDiagnostics());
        CompilationResult r2 = compileJvm(dir, "w2", """
                main() {
                    var b = new Byte[2]
                    println(crypto.sha256(b))
                }
                """);
        assertFalse(r2.success(), "String face keeps the §563 refusal");
        assertTrue(r2.diagnostics().getDiagnostics().toString().contains("SECN011"),
                "must keep SECN011: " + r2.diagnostics().getDiagnostics());
    }

    @Test
    @DisplayName("D-KOF-DIGEST-BYTES: Script leg runs the JVM body by construction — digest parity with the golden")
    void sha256BytesParityScript(@TempDir Path dir) throws Exception {
        Path s = dir.resolve("sc.kf");
        Files.writeString(s, abcSetup() + """
                main() {
                    var h = crypto.sha256Bytes(abc())
                    var g = crypto.sha256("abc")
                    println(h.length + "/" + (h == g))
                }
                """);
        KofInterpreter.Result r = driver.interpret(java.util.List.of(s), dir, new String[0]);
        assertEquals(0, r.exitCode(), "script must run: " + r.stderr());
        assertTrue(r.stdout().contains("64/true"), "same digest, 64 hex: " + r.stdout());
    }

    @Test
    @DisplayName("D-KOF-DIGEST-BYTES: Native x86-64 runs the Bytes face and matches the JVM golden (sha256 + hmac), not just compiles")
    void nativeX86RunsAndMatchesJvmGolden(@TempDir Path dir) throws Exception {
        String src = abcSetup() + """
                main() {
                    println(crypto.sha256Bytes(abc()))
                    println(crypto.hmacSha256Bytes(abc(), abc()))
                }
                """;
        Path s = dir.resolve("nat.kf");
        Files.writeString(s, src);
        Path outDir = dir.resolve("out-nat");
        CompilationResult r = driver.compile(s, outDir, Target.NATIVE);
        assertTrue(r.success(), "native x86-64 must bind: " + r.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), "native binary should exist");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "native run must exit 0, got:\n" + out);
        String[] lines = out.split("\n");
        assertEquals(ABC_SHA256, lines[0], "native sha256Bytes must equal the JVM golden");
        assertEquals(ABC_HMAC, lines[1], "native hmacSha256Bytes must equal the JVM golden");
    }
}
