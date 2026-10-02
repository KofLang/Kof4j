package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * D-SECRETS face 1 (Stage 5 / tracker 3.6): the {@code Secret} value type.
 * Incremental slice (R6-SCOPE, R7): JVM first; JS/Native/Script/Android stay an
 * honest compile-time gap {@code SECN008} (never a silent stub). A {@code Secret}
 * prints redacted, exposes the raw value only through the greppable
 * {@code reveal()}, and compares constant-time.
 */
class SecretE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void printsRedactedAndRevealIsTheOnlyExportJvm(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("sec.kf");
        Files.writeString(src, """
                main() {
                    val s = secrets.of("topsecret")
                    println(s)
                    println(s.redacted())
                    println(s.reveal())
                }
                """);
        CompilationResult r = driver.compile(src, dir.resolve("out-sec"), Target.JVM);
        assertTrue(r.success(), "secrets.of must bind on JVM: " + r.diagnostics().getDiagnostics());
        assertEquals("Secret(*** )\n***\ntopsecret", runJvm(dir.resolve("out-sec")),
                "println prints the REDACTED form; redacted() is ***; reveal() is the only raw export");
    }

    @Test
    void secretThroughGenericContainerErasureJvm(@TempDir Path dir) throws IOException {
        // Erasure family (JvmTypeMapper.toInternalName): a Secret read back out
        // of a `List` (checkcast in OWNER position) must map to KofRuntime$Secret,
        // not the non-existent `kof/Secret` (NoClassDefFoundError).
        Path src = dir.resolve("sec-erasure.kf");
        Files.writeString(src, """
                main() {
                    val s = secrets.of("topsecret")
                    val box = listOf(s)
                    println(box.get(0))
                }
                """);
        CompilationResult r = driver.compile(src, dir.resolve("out-sec-erasure"), Target.JVM);
        assertTrue(r.success(), "Secret through a List must compile: " + r.diagnostics().getDiagnostics());
        assertEquals("Secret(*** )", runJvm(dir.resolve("out-sec-erasure")),
                "Secret read out of a generic container still redacts (erasure owner maps to the runtime type)");
    }

    @Test
    void concatenationAndEqualityNeverLeakJvm(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("sec2.kf");
        Files.writeString(src, """
                main() {
                    val a = secrets.of("hunter2")
                    val b = secrets.of("hunter2")
                    val c = secrets.of("other")
                    println("v=" + a)
                    println(a == b)
                    println(a == c)
                }
                """);
        CompilationResult r = driver.compile(src, dir.resolve("out-sec2"), Target.JVM);
        assertTrue(r.success(), "string concat + == must compile: " + r.diagnostics().getDiagnostics());
        assertEquals("v=Secret(*** )\ntrue\nfalse", runJvm(dir.resolve("out-sec2")),
                "concat cannot leak; == is content (constant-time) equality");
    }

    @Test
    void secretFromEnvNeverLeaksJvm(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("sec3.kf");
        Files.writeString(src, """
                main() {
                    val s = secrets.secret("KOF_SECRET_TEST_UNSET_XYZ")
                    println(s.reveal() == "")
                    println(s)
                }
                """);
        CompilationResult r = driver.compile(src, dir.resolve("out-sec3"), Target.JVM);
        assertTrue(r.success(), "secrets.secret must bind on JVM: " + r.diagnostics().getDiagnostics());
        assertEquals("true\nSecret(*** )", runJvm(dir.resolve("out-sec3")),
                "unset env -> empty Secret; println still redacts");
    }

    @Test
    void secretGapsOnEveryNonJvmTarget(@TempDir Path dir) throws IOException {
        String kof = """
                main() {
                    println(secrets.of("x"))
                }
                """;
        // §278: ANDROID now runs kof.security (reuses the JVM) — no longer a gap.
        for (Target t : new Target[] { Target.JS, Target.NATIVE, Target.NATIVE_RISCV64,
                Target.NATIVE_AARCH64 }) {
            Path src = dir.resolve("secgap-" + t.name() + ".kf");
            Files.writeString(src, kof);
            CompilationResult r = driver.compile(src, dir.resolve("out-secgap-" + t.name()), t);
            assertFalse(r.success(), t + ": Secret must stay an honest gap (no silent stub)");
            assertTrue(r.diagnostics().getDiagnostics().toString().contains("SECN008"),
                    t + ": expected SECN008, got: " + r.diagnostics().getDiagnostics());
        }
    }

    @Test
    void fromBytesBuildsASecretThatStillRedactsJvm(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("sec4.kf");
        Files.writeString(src, """
                main() {
                    var b = new Int[3]
                    b[0] = 97
                    b[1] = 98
                    b[2] = 99
                    val s = secrets.fromBytes(b)
                    println(s.reveal())
                    println(s)
                }
                """);
        CompilationResult r = driver.compile(src, dir.resolve("out-sec4"), Target.JVM);
        assertTrue(r.success(), "secrets.fromBytes(Int[]) must bind on JVM: " + r.diagnostics().getDiagnostics());
        assertEquals("abc\nSecret(*** )", runJvm(dir.resolve("out-sec4")),
                "bytes -> Latin-1 text; reveal() is the only raw export and println redacts");
    }

    @Test
    void jsonEncodeOfASecretIsRedactedNotFieldDumpedJvm(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("sec5.kf");
        Files.writeString(src, """
                main() {
                    val s = secrets.of("topsecret")
                    println(json.encode(s))
                }
                """);
        CompilationResult r = driver.compile(src, dir.resolve("out-sec5"), Target.JVM);
        assertTrue(r.success(), "json.encode(Secret) must compile on JVM: " + r.diagnostics().getDiagnostics());
        assertEquals("\"Secret(*** )\"", runJvm(dir.resolve("out-sec5")),
                "runtime redaction (P2): the serializer must never dump the Secret field");
    }

    @Test
    void revealingIntoLogOrJsonWarnsButStillCompilesJvm(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("sec6.kf");
        Files.writeString(src, """
                main() {
                    val s = secrets.of("topsecret")
                    log.info(s.reveal())
                    println(json.encode(s.reveal()))
                }
                """);
        CompilationResult r = driver.compile(src, dir.resolve("out-sec6"), Target.JVM);
        assertTrue(r.success(), "lint não-fatal: deve continuar compilando: " + r.diagnostics().getDiagnostics());
        long secn009 = r.diagnostics().getDiagnostics().stream()
                .filter(d -> "SECN009".equals(d.code())).count();
        assertTrue(secn009 >= 1,
                "reveal() rumo a log/json deve emitir o warning SECN009: " + r.diagnostics().getDiagnostics());
    }

    private String runJvm(Path outDir) throws IOException {
        try {
            String javaHome = System.getProperty("java.home");
            ProcessBuilder pb = new ProcessBuilder(
                    Path.of(javaHome, "bin", "java").toString(),
                    "-cp", outDir.toString(),
                    "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "JVM exit code, output: " + output);
            return output;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }
}
