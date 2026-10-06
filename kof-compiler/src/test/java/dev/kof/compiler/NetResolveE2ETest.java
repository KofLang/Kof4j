package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * #759 / NET1 ({@code D-MAINT-BATCH-0510}): {@code kof.net} gains name
 * resolution ({@code net.resolve}) and a connect to a vetted numeric address
 * while keeping the host name for Host/SNI/cert checks —
 * {@code net.connect(host, port, address)}.
 *
 * <p>The contract is an egress-policy primitive: a caller resolves a host,
 * validates every returned address against its policy, then connects to the
 * vetted literal without the runtime re-resolving (anti DNS-rebinding). This
 * suite pins the JVM runtime; the Native face is pinned by
 * {@code NetResolveNativeE2ETest} and the whole front stays refused on JS per
 * {@code D-NET-JS-V1} (NETN001).
 */
class NetResolveE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(String src, Path dir, String name) throws Exception {
        Path s = dir.resolve(name + ".kf");
        Files.writeString(s, src);
        CompilationResult r = driver.compile(s, dir.resolve("out-" + name), Target.JVM);
        assertTrue(r.success(), name + " must compile: " + r.diagnostics().getDiagnostics());
        Path classes = dir.resolve("out-" + name);
        assertTrue(Files.exists(classes.resolve("Default/Main.class")), "no Main.class for " + name);
        ProcessBuilder pb = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "--enable-native-access=ALL-UNNAMED",
                "-cp", classes.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try {
            if (!p.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new AssertionError(name + " did not terminate in 20s");
            }
            return new String(p.getInputStream().readAllBytes());
        } finally {
            if (p.isAlive()) p.destroyForcibly();
        }
    }

    @Test
    @DisplayName("NET1: net.resolve returns the host addresses; an unknown host refuses by name")
    void resolveReturnsAddresses(@TempDir Path dir) throws Exception {
        String out = runJvm("""
                main() {
                    var addrs = net.resolve("localhost")
                    println("count-positive=" + (addrs.size > 0))
                    println("has-loopback=" + addrs.contains("127.0.0.1"))
                    try {
                        net.resolve("no-such-host.invalid")
                        println("NO-REFUSAL")
                    } catch (String m) {
                        println("resolve-refused")
                    }
                }
                """, dir, "resolve");

        assertTrue(out.contains("count-positive=true"), "resolve must return addresses: " + out);
        assertTrue(out.contains("has-loopback=true"), "localhost must include 127.0.0.1: " + out);
        assertTrue(out.contains("resolve-refused"), "unknown host must refuse by name: " + out);
        assertTrue(!out.contains("NO-REFUSAL"), "no silent empty list for a bad host: " + out);
    }

    @Test
    @DisplayName("NET1: net.connect(host, port, address) dials the vetted literal, keeping host")
    void vettedAddressConnect(@TempDir Path dir) throws Exception {
        String out = runJvm("""
                main() {
                    var l = net.listen(18891)
                    var worker = spawn {
                        var s = l.accept()
                        var got = s.receive(4096)
                        s.send(got)
                        s.close()
                    }
                    var c = net.connect("localhost", 18891, "127.0.0.1")
                    var payload = new Byte[2]
                    payload[0] = 7
                    payload[1] = 8
                    var sent = c.send(payload)
                    var back = c.receive(4096)
                    c.close()
                    await worker
                    l.close()
                    println("sent=" + sent)
                    println("back-len=" + back.length)
                }
                """, dir, "vetted");

        assertTrue(out.contains("sent=2"), "vetted connect must send the payload: " + out);
        assertTrue(out.contains("back-len=2"), "vetted connect must round-trip: " + out);
    }

    @Test
    @DisplayName("NET1: an empty vetted address is refused by name (NET004), never re-resolved")
    void emptyAddressRefused(@TempDir Path dir) throws Exception {
        String out = runJvm("""
                main() {
                    var l = net.listen(18892)
                    try {
                        var c = net.connect("localhost", 18892, "")
                        println("NO-REFUSAL")
                        c.close()
                    } catch (String m) {
                        println("empty-refused")
                    }
                    l.close()
                }
                """, dir, "empty");

        assertTrue(out.contains("empty-refused"), "an empty address must refuse: " + out);
        assertTrue(!out.contains("NO-REFUSAL"), "never a silent re-resolution: " + out);
    }

    @Test
    @DisplayName("D-NET-JS-V1: resolve and the vetted connect are refused on JS (NETN001)")
    void jsRefused(@TempDir Path dir) throws Exception {
        for (String src : new String[]{
                "main() { var a = net.resolve(\"localhost\") }",
                "main() { var c = net.connect(\"localhost\", 80, \"127.0.0.1\") }"}) {
            Path s = dir.resolve("js.kf");
            Files.writeString(s, src);
            CompilationResult r = driver.compile(s, dir.resolve("out-js"), Target.JS);
            assertFalse(r.success(), "JS must refuse: " + src);
            assertTrue(r.diagnostics().getDiagnostics().toString().contains("NETN001"),
                    "must name NETN001: " + r.diagnostics().getDiagnostics());
        }
    }

    @Test
    @DisplayName("NET1 native: resolve and the vetted connect refuse honestly (NET002) until the DNS slice")
    void nativeRefused(@TempDir Path dir) throws Exception {
        // O connect v1 do Native e IPv4 dotted-quad (sem resolvedor); o
        // `resolve`/`connect_addr` so entram la com a fatia nativa. Ate entao
        // o gate recusa com NET002 — nunca um link quebrado (R6).
        for (String src : new String[]{
                "main() { var a = net.resolve(\"localhost\") }",
                "main() { var c = net.connect(\"localhost\", 80, \"127.0.0.1\") }"}) {
            Path s = dir.resolve("nat.kf");
            Files.writeString(s, src);
            for (Target t : new Target[]{Target.NATIVE, Target.NATIVE_RISCV64}) {
                CompilationResult r = driver.compile(s, dir.resolve("out-" + t), t);
                assertFalse(r.success(), t + " must refuse until the native slice: " + src);
                assertTrue(r.diagnostics().getDiagnostics().toString().contains("NET002"),
                        t + " must name NET002: " + r.diagnostics().getDiagnostics());
            }
        }
    }
}
