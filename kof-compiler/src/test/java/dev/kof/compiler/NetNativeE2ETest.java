package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * D-KOF-NET fatia 3 (plan {@code docs/stdlib/network-kofnet-plan.md}): o
 * front `kof.net` roda no Native x86-64 sobre os handles opacos emitidos por
 * {@link dev.kof.compiler.nat.NativeNetFront}. O ORÁCULO é o mesmo da fatia 2:
 * eco de bytes sobre loopback real — só que agora sem JVM.
 *
 * <p>Paridade com {@code JvmRuntimeSockets}: `receive` volta assim que HÁ dado
 * (nunca enche `maxBytes`), `[]` no EOF, `peer()` devolve `"host:port"` real,
 * limite UDP 65507 com recusa `NET003`, endereço malformado `NET004`, `close`
 * polimórfico com tag errada `NET005`. Native é IPC/loopback, então o teste
 * usa porta fixa e payload por `new Byte[n]` (não há `byteArrayOf`).
 */
class NetNativeE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runNative(String src, Path dir, String name) throws Exception {
        Path s = dir.resolve(name + ".kf");
        Files.writeString(s, src);
        CompilationResult r = driver.compile(s, dir.resolve("out-" + name), Target.NATIVE);
        assertTrue(r.success(), name + " must compile: " + r.diagnostics().getDiagnostics());
        Path bin = dir.resolve("out-" + name).resolve("Default").resolve("Main");
        assertTrue(Files.exists(bin), "no native Main emitted for " + name);
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        java.util.concurrent.Future<byte[]> reader;
        boolean done;
        try (var ex = Executors.newSingleThreadExecutor(run -> {
                Thread t = new Thread(run, "net-native-out"); t.setDaemon(true); return t; })) {
            reader = ex.submit(() -> p.getInputStream().readAllBytes());
            done = p.waitFor(30, TimeUnit.SECONDS);
            if (!done) {
                p.destroyForcibly();
                p.waitFor(5, TimeUnit.SECONDS);
            }
        }
        String out = new String(reader.get(5, TimeUnit.SECONDS), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertTrue(done, name + " native binary did not exit in 30s (worker leak?); out=" + out);
        return out;
    }

    /** D-KOF-NET fatia 4b: compila p/ o cross e roda sob qemu. */
    private String runCross(Target target, String arch, String src, Path dir, String name)
            throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
                NativeRiscv64E2ETest.hasToolchain(arch),
                "cross toolchain " + arch + " + qemu ausente — pulando (NET002)");
        Path s = dir.resolve(name + "-" + arch + ".kf");
        Files.writeString(s, src);
        CompilationResult r = driver.compile(s, dir.resolve("out-" + name + "-" + arch), target);
        assertTrue(r.success(), name + " " + arch + " must compile: "
                + r.diagnostics().getDiagnostics());
        Path bin = dir.resolve("out-" + name + "-" + arch).resolve("Default").resolve("Main");
        assertTrue(Files.exists(bin), "no cross Main emitted for " + name);
        return NativeRiscv64E2ETest.runQemu(arch, bin);
    }

    @Test
    @DisplayName("Native x86-64 TCP echo over REAL loopback: spawn worker + Byte[] both ways")
    void nativeTcpEchoRoundTrip(@TempDir Path dir) throws Exception {
        String out = runNative("""
                main() {
                    var l = net.listen(18931)
                    var worker = spawn {
                        var s = l.accept()
                        var got = s.receive(4096)
                        s.send(got)
                        s.close()
                    }
                    var c = net.connect("127.0.0.1", 18931)
                    var payload = new Byte[4]
                    payload[0] = 1
                    payload[1] = 2
                    payload[2] = 3
                    payload[3] = 4
                    var sent = c.send(payload)
                    var back = c.receive(4096)
                    c.close()
                    await worker
                    l.close()
                    println("sent=" + sent)
                    print("echo=")
                    for (var b in back) {
                        print("" + b + ",")
                    }
                    println("")
                    println("len=" + back.length)
                }
                """, dir, "echo");

        assertTrue(out.contains("sent=4"), "server must have received 4 bytes: " + out);
        assertTrue(out.contains("echo=1,2,3,4,"), "echo must be byte-identical: " + out);
        assertTrue(out.contains("len=4"), "receive must return the full payload: " + out);
    }

    @Test
    @DisplayName("Native x86-64 UDP: sendTo + receive + peer() gives the real source \"host:port\"")
    void nativeUdpDatagramRoundTrip(@TempDir Path dir) throws Exception {
        String out = runNative("""
                main() {
                    var server = net.bind(18932)
                    var client = net.bind(18933)
                    var datagram = new Byte[3]
                    datagram[0] = 9
                    datagram[1] = 8
                    datagram[2] = 7
                    client.sendTo("127.0.0.1:18932", datagram)
                    var got = server.receive(4096)
                    print("server-got=")
                    for (var b in got) {
                        print("" + b + ",")
                    }
                    println("")
                    println("server-peer=" + server.peer())
                    server.sendTo(server.peer(), datagram)
                    var reply = client.receive(4096)
                    println("reply-len=" + reply.length)
                    client.close()
                    server.close()
                }
                """, dir, "udp");

        assertTrue(out.contains("server-got=9,8,7,"), "datagram must arrive intact: " + out);
        assertTrue(out.contains("server-peer=127.0.0.1:18933"),
                "peer() must be the real source host:port: " + out);
        assertTrue(out.contains("reply-len=3"), "reply must come back: " + out);
    }

    @Test
    @DisplayName("Native x86-64 UDP 64 KiB bound: oversized datagram REFUSES with NET003")
    void nativeOversizedDatagramRefused(@TempDir Path dir) throws Exception {
        String out = runNative("""
                main() {
                    var e = net.bind(18934)
                    try {
                        e.sendTo("127.0.0.1:9", new Byte[65508])
                        println("NO-REFUSAL")
                    } catch (String m) {
                        println("refused")
                    }
                    e.close()
                }
                """, dir, "toobig");

        assertTrue(out.contains("refused"), "oversized datagram must refuse: " + out);
        assertTrue(!out.contains("NO-REFUSAL"), "no transparent fragmentation: " + out);
    }

    @Test
    @DisplayName("Native x86-64 UDP: a malformed address is refused by name (NET004)")
    void nativeBadAddressRefused(@TempDir Path dir) throws Exception {
        String out = runNative("""
                main() {
                    var e = net.bind(18935)
                    try {
                        e.sendTo("no-colon-here", new Byte[1])
                        println("NO-REFUSAL")
                    } catch (String m) {
                        println("refused")
                    }
                    e.close()
                }
                """, dir, "badaddr");

        assertTrue(out.contains("refused"), "a malformed host:port must refuse: " + out);
    }

    @Test
    @DisplayName("Native x86-64 TCP: the peer closing is an honest empty Byte[] on receive")
    void nativeEofIsEmptyArray(@TempDir Path dir) throws Exception {
        String out = runNative("""
                main() {
                    var l = net.listen(18936)
                    var worker = spawn {
                        var s = l.accept()
                        var n = s.receive(4096)
                        println("server-got-len=" + n.length)
                        s.close()
                    }
                    var c = net.connect("127.0.0.1", 18936)
                    c.close()
                    await worker
                    l.close()
                }
                """, dir, "eof");

        assertTrue(out.contains("server-got-len=0"),
                "receive after peer close must be an empty array, not a block: " + out);
    }

    @Test
    @DisplayName("Native x86-64: every verb resolves at LINK time (no undefined kof_net_*)")
    void everyVerbResolvesAtLinkTime(@TempDir Path dir) throws Exception {
        // Se algum símbolo `kof_net_*` não existisse no runtime emitido, o `ld`
        // falharia aqui — a armadilha da fatia 1 (verde de compilação, símbolo
        // inexistente) no mundo nativo é o LINK, não o class load.
        String out = runNative("""
                main() {
                    var l = net.listen(18937)
                    var e = net.bind(18938)
                    var c = net.connect("127.0.0.1", 18937)
                    c.send(new Byte[1])
                    c.close()
                    println("peer-empty=[" + e.peer() + "]")
                    e.close()
                    l.close()
                    println("verbs-live")
                }
                """, dir, "live");

        assertTrue(out.contains("peer-empty=[]"),
                "peer() before any receive is the empty string, never null: " + out);
        assertTrue(out.contains("verbs-live"), "every verb must resolve at link time: " + out);
    }

    @Test
    @DisplayName("Native cross (riscv64/aarch64) TCP echo over REAL loopback: spawn worker + Byte[] both ways")
    void crossTcpEchoRoundTrip(@TempDir Path dir) throws Exception {
        String src = """
                main() {
                    var l = net.listen(18951)
                    var worker = spawn {
                        var s = l.accept()
                        var got = s.receive(4096)
                        s.send(got)
                        s.close()
                    }
                    var c = net.connect("127.0.0.1", 18951)
                    var payload = new Byte[4]
                    payload[0] = 1
                    payload[1] = 2
                    payload[2] = 3
                    payload[3] = 4
                    var sent = c.send(payload)
                    var back = c.receive(4096)
                    c.close()
                    await worker
                    l.close()
                    println("sent=" + sent)
                    print("echo=")
                    for (var b in back) {
                        print("" + b + ",")
                    }
                    println("")
                    println("len=" + back.length)
                }
                """;
        for (var t : new Object[][]{
                {Target.NATIVE_RISCV64, "riscv64"}, {Target.NATIVE_AARCH64, "aarch64"}}) {
            Target target = (Target) t[0];
            String arch = (String) t[1];
            String out = runCross(target, arch, src, dir, "echo");
            assertTrue(out.contains("sent=4"), arch + " server must have received 4 bytes: " + out);
            assertTrue(out.contains("echo=1,2,3,4,"), arch + " echo must be byte-identical: " + out);
            assertTrue(out.contains("len=4"), arch + " receive must return the full payload: " + out);
        }
    }

    @Test
    @DisplayName("Native cross (riscv64/aarch64) UDP: sendTo + receive + peer() gives the real source \"host:port\"")
    void crossUdpDatagramRoundTrip(@TempDir Path dir) throws Exception {
        String src = """
                main() {
                    var server = net.bind(18952)
                    var client = net.bind(18953)
                    var datagram = new Byte[3]
                    datagram[0] = 9
                    datagram[1] = 8
                    datagram[2] = 7
                    client.sendTo("127.0.0.1:18952", datagram)
                    var got = server.receive(4096)
                    print("server-got=")
                    for (var b in got) {
                        print("" + b + ",")
                    }
                    println("")
                    println("server-peer=" + server.peer())
                    server.sendTo(server.peer(), datagram)
                    var reply = client.receive(4096)
                    println("reply-len=" + reply.length)
                    client.close()
                    server.close()
                }
                """;
        for (var t : new Object[][]{
                {Target.NATIVE_RISCV64, "riscv64"}, {Target.NATIVE_AARCH64, "aarch64"}}) {
            Target target = (Target) t[0];
            String arch = (String) t[1];
            String out = runCross(target, arch, src, dir, "udp");
            assertTrue(out.contains("server-got=9,8,7,"), arch + " datagram must arrive intact: " + out);
            assertTrue(out.contains("server-peer=127.0.0.1:18953"),
                    arch + " peer() must be the real source host:port: " + out);
            assertTrue(out.contains("reply-len=3"), arch + " reply must come back: " + out);
        }
    }

    @Test
    @DisplayName("Native cross (riscv64/aarch64): every verb resolves at LINK time (no undefined kof_net_*)")
    void crossEveryVerbResolvesAtLinkTime(@TempDir Path dir) throws Exception {
        String src = """
                main() {
                    var l = net.listen(18954)
                    var e = net.bind(18955)
                    var c = net.connect("127.0.0.1", 18954)
                    c.send(new Byte[1])
                    c.close()
                    println("peer-empty=[" + e.peer() + "]")
                    e.close()
                    l.close()
                    println("verbs-live")
                }
                """;
        for (var t : new Object[][]{
                {Target.NATIVE_RISCV64, "riscv64"}, {Target.NATIVE_AARCH64, "aarch64"}}) {
            Target target = (Target) t[0];
            String arch = (String) t[1];
            String out = runCross(target, arch, src, dir, "live");
            assertTrue(out.contains("peer-empty=[]"),
                    arch + " peer() before any receive is the empty string, never null: " + out);
            assertTrue(out.contains("verbs-live"), arch + " every verb must resolve at link time: " + out);
        }
    }
}
