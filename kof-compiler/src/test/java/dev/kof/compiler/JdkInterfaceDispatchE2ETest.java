package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §557 — JVM interop: a call on a Java INTERFACE resolved through the
 * declared-type arm emitted {@code invokevirtual}, and the class load died
 * with {@code IncompatibleClassChangeError: Found interface ..., but class
 * was expected} (the launcher then masked it behind the JavaFX message, §556).
 *
 * <p>Root cause (measured): the external/JDK member-resolution arm carried no
 * interface flag — {@code MemberCallTyper} always stamped
 * {@code DispatchKind.INSTANCE} even though the resolved
 * {@code ExternalClasspath.MethodSignature} already knew
 * {@code ownerIsInterface()}, and the instance-call lowering only consulted the
 * Kof-local {@code SemanticAnalyzer.interfaceNames} set, which by definition
 * contains no JDK type. The emitter then defaulted to {@code INVOKEVIRTUAL}
 * ({@code JvmOpEmitter}), while the JVM requires {@code INVOKEINTERFACE} for
 * interface methods.
 *
 * <p>Proof strategy: (1) E2E — the exact repro runs green and prints the
 * measured value; (2) bytecode — the call site must carry the
 * {@code invokeinterface} opcode and must NOT carry {@code invokevirtual} on
 * the same interface owner; (3) negative control — a JDK CLASS receiver
 * ({@code java.lang.StringBuilder}) must keep {@code invokevirtual}, so the
 * fix cannot regress the class path into a broken {@code invokeinterface}.
 */
class JdkInterfaceDispatchE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    /** Compila e roda no JVM via um runner reflexivo (contorna a máscara §556). */
    @SuppressWarnings("ProcessBuilderCommandInjection")
    private String runJvm(Path tempDir, String source) throws IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        try {
            Path runnerDir = tempDir.resolve("run-" + System.nanoTime());
            Files.createDirectories(runnerDir);
            Path runnerSrc = runnerDir.resolve("Run.java");
            Files.writeString(runnerSrc, """
                public class Run {
                    public static void main(String[] args) throws Exception {
                        Class.forName(args[0]).getMethod("main", String[].class)
                            .invoke(null, (Object) new String[0]);
                    }
                }
                """);
            Process pCompile = new ProcessBuilder("javac", "-d", runnerDir.toString(), runnerSrc.toString()).start();
            assertEquals(0, pCompile.waitFor());
            String javaCmd = System.getProperty("java.home") + "/bin/java";
            Process p = new ProcessBuilder(javaCmd, "-cp", outDir.toString() + java.io.File.pathSeparator + runnerDir.toString(), "Run", "Default.Main").redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code (IncompatibleClassChangeError = §557), output: " + output);
            return output;
        } catch (InterruptedException e) {
            throw new IOException("interrupted", e);
        }
    }

    /** Compila e devolve as linhas de `javap -c` do Main gerado. */
    @SuppressWarnings("ProcessBuilderCommandInjection")
    private List<String> javapCallSites(Path tempDir, String source, String methodName) throws Exception {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/javap",
                "-c", "-p", "-cp", outDir.toString(), "Default.Main").redirectErrorStream(true).start();
        String javap = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, p.waitFor(), "javap failed: " + javap);
        List<String> hits = new ArrayList<>();
        for (String line : javap.split("\n")) {
            if (line.contains(methodName)) hits.add(line.trim());
        }
        assertFalse(hits.isEmpty(), "no call site for '" + methodName + "' in bytecode:\n" + javap);
        return hits;
    }

    /** §557 repro exato: receptor em local tipado por interface importada. */
    @Test
    void importedInterfaceTypedLocalCallsInvokeInterfaceJvm(@TempDir Path tmp) throws Exception {
        String out = runJvm(tmp, """
                import java.security.KeyPairGenerator
                import java.security.PublicKey

                main() {
                    val kpg = KeyPairGenerator.getInstance("Ed25519")
                    val kp = kpg.generateKeyPair()
                    PublicKey pub = kp.getPublic()
                    val enc = pub.getEncoded()
                    println("len=" + enc.size)
                }
                """);
        assertEquals("len=44", out);
    }

    /** Bytecode: o call site precisa ser invokeinterface, nunca invokevirtual. */
    @Test
    void importedInterfaceCallSiteUsesInvokeInterfaceOpcode(@TempDir Path tmp) throws Exception {
        List<String> sites = javapCallSites(tmp, """
                import java.security.KeyPairGenerator
                import java.security.PublicKey

                main() {
                    val kpg = KeyPairGenerator.getInstance("Ed25519")
                    val kp = kpg.generateKeyPair()
                    PublicKey pub = kp.getPublic()
                    val enc = pub.getEncoded()
                    println(enc.size)
                }
                """, "getEncoded");
        for (String site : sites) {
            assertTrue(site.contains("invokeinterface"),
                    "§557: call on JDK interface must be invokeinterface, got: " + site);
            assertFalse(site.contains("invokevirtual"),
                    "§557: invokevirtual on an interface owner dies IncompatibleClassChangeError: " + site);
        }
    }

    /**
     * Segunda interface do JDK, forma `java.security.PrivateKey`:
     * prova que o conserto não é específico de um único owner.
     */
    @Test
    void secondJdkInterfaceFamilyUsesInvokeInterface(@TempDir Path tmp) throws Exception {
        List<String> sites = javapCallSites(tmp, """
                import java.security.KeyPairGenerator
                import java.security.PrivateKey

                main() {
                    val kpg = KeyPairGenerator.getInstance("Ed25519")
                    val kp = kpg.generateKeyPair()
                    PrivateKey prv = kp.getPrivate()
                    val enc = prv.getEncoded()
                    println(enc.size)
                }
                """, "getEncoded");
        for (String site : sites) {
            assertTrue(site.contains("invokeinterface"),
                    "§557: call on JDK interface must be invokeinterface, got: " + site);
        }
    }

    /**
     * E2E da segunda interface contra o ORACLE do JDK: Ed25519
     * PrivateKey.getEncoded() = 48 bytes (PKCS#8 prefix de 16 + 32 da seed).
     */
    @Test
    void privateKeyInterfaceCallExecutesAgainstJdkOracle(@TempDir Path tmp) throws Exception {
        String out = runJvm(tmp, """
                import java.security.KeyPairGenerator
                import java.security.PrivateKey

                main() {
                    val kpg = KeyPairGenerator.getInstance("Ed25519")
                    val kp = kpg.generateKeyPair()
                    PrivateKey prv = kp.getPrivate()
                    println("len=" + prv.getEncoded().size)
                }
                """);
        assertEquals("len=48", out);
    }

    /**
     * Controle NEGATIVO: um receptor que é JDK CLASSE (não interface) tem de
     * continuar `invokevirtual` — o conserto não pode virar tudo interface.
     */
    @Test
    void jdkClassReceiverKeepsInvokeVirtualOpcode(@TempDir Path tmp) throws Exception {
        List<String> sites = javapCallSites(tmp, """
                main() {
                    val sb = new java.lang.StringBuilder()
                    sb.append("a")
                    println(sb.toString())
                }
                """, "java/lang/StringBuilder.toString");
        for (String site : sites) {
            assertTrue(site.contains("invokevirtual"),
                    "a JDK CLASS receiver must stay invokevirtual, got: " + site);
            assertFalse(site.contains("invokeinterface"),
                    "a JDK CLASS receiver must not use invokeinterface: " + site);
        }
    }

    /**
     * `java.util.Base64`: import explícito per §558, encadeamento de métodos.
     */
    @Test
    void base64InterfaceInstanceChainStillGreen(@TempDir Path tmp) throws Exception {
        String out = runJvm(tmp, """
                import java.util.Base64

                main() {
                    println(Base64.getEncoder().encodeToString("kof".getBytes()))
                }
                """);
        assertEquals("a29m", out);
    }
}