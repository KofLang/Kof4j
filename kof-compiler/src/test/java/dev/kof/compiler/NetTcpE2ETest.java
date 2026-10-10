package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * D-KOF-NET fatia 2 (plan {@code docs/stdlib/network-kofnet-plan.md}):
 * o runtime de sockets JVM é REAL. O ORÁCULO é a sonda de interop de 01/10
 * (eco de bytes sobre porta real) — só que agora em Kof puro, sem
 * {@code java.net} no código do usuário.
 *
 * Contrato congelado por {@code D-KOF-NET}: verbos BLOQUEANTES (o paralelismo
 * é {@code spawn}), {@code Byte[]} nas duas mãos nos dois transportes, worker
 * por conexão, UDP endereçado por {@code "host:port"} e 64 KiB de limite.
 *
 * Estilo: um teste E2E = compila a fonte, roda a classe de verdade e compara o
 * stdout. Nenhuma superfície inventada: porta fixa por teste (o contrato v1 não
 * tem `Listener.port()`) e payload por `new Byte[n]` (a superfície de array do
 * Kof — ver `training/idioms/interop.md`, não existe `byteArrayOf`).
 */
class NetTcpE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(String src, Path dir, String name) throws Exception {
        Path s = dir.resolve(name + ".kf");
        Files.writeString(s, src);
        CompilationResult r = driver.compile(s, dir.resolve("out-" + name), Target.JVM);
        assertTrue(r.success(), name + " must compile: " + r.diagnostics().getDiagnostics());
        Path classes = dir.resolve("out-" + name);
        Path main = classes.resolve("Default/Main.class");
        assertTrue(Files.exists(main), "no Main.class emitted for " + name);
        ProcessBuilder pb = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "--enable-native-access=ALL-UNNAMED",
                "-cp", classes.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        // Um worker bloqueado em accept() mantém o JVM vivo PARA SEMPRE e segura
        // a porta; sem este limite, uma falha do teste vira uma cascata (a
        // próxima execução encontra a porta ocupada e falha por um motivo
        // falso). O processo é SEMPRE derrubado no fim.
        try {
            if (!p.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new AssertionError(name + " did not terminate in 20s (worker leak?)");
            }
            return new String(p.getInputStream().readAllBytes());
        } finally {
            if (p.isAlive()) p.destroyForcibly();
        }
    }

    @Test
    @DisplayName("TCP echo over a REAL loopback socket: spawn worker + Byte[] both ways")
    void tcpEchoRoundTrip(@TempDir Path dir) throws Exception {
        String out = runJvm("""
                main() {
                    var l = net.listen(18891)
                    var worker = spawn {
                        var s = l.accept()
                        var got = s.receive(4096)
                        s.send(got)
                        s.close()
                    }
                    var c = net.connect("127.0.0.1", 18891)
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
    @DisplayName("UDP: sendTo + receive + peer() gives the real source \"host:port\"")
    void udpDatagramRoundTrip(@TempDir Path dir) throws Exception {
        String out = runJvm("""
                main() {
                    var server = net.bind(18872)
                    var client = net.bind(18873)
                    var datagram = new Byte[3]
                    datagram[0] = 9
                    datagram[1] = 8
                    datagram[2] = 7
                    client.sendTo("127.0.0.1:18872", datagram)
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
        assertTrue(out.contains("server-peer=127.0.0.1:18873"),
                "peer() must be the real source host:port: " + out);
        assertTrue(out.contains("reply-len=3"), "reply must come back: " + out);
    }

    @Test
    @DisplayName("UDP 64 KiB bound: an oversized datagram REFUSES with NET003, no fragmentation")
    void oversizedDatagramRefused(@TempDir Path dir) throws Exception {
        String out = runJvm("""
                main() {
                    var e = net.bind(18874)
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
    @DisplayName("UDP: a 65507-byte payload is ACCEPTED (the measured IPv4 bound is exclusive)")
    void maxDatagramAccepted(@TempDir Path dir) throws Exception {
        String out = runJvm("""
                main() {
                    var e = net.bind(18875)
                    try {
                        e.sendTo("127.0.0.1:18875", new Byte[65507])
                        println("accepted")
                    } catch (String m) {
                        println("refused=" + m)
                    }
                    e.close()
                }
                """, dir, "atbound");

        assertTrue(out.contains("accepted"),
                "65507 (65535-20-8, the real IPv4 UDP payload bound) must be "
                        + "INSIDE the bound: " + out);
    }

    @Test
    @DisplayName("UDP: a bad address is refused by name, never resolved to 0.0.0.0")
    void badAddressRefused(@TempDir Path dir) throws Exception {
        String out = runJvm("""
                main() {
                    var e = net.bind(18876)
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
    @DisplayName("TCP: the peer closing is an honest empty Byte[] on receive, never a hang")
    void eofIsEmptyArray(@TempDir Path dir) throws Exception {
        String out = runJvm("""
                main() {
                    var l = net.listen(18877)
                    var worker = spawn {
                        var s = l.accept()
                        var n = s.receive(4096)
                        println("server-got-len=" + n.length)
                        s.close()
                    }
                    var c = net.connect("127.0.0.1", 18877)
                    c.close()
                    await worker
                    l.close()
                }
                """, dir, "eof");

        assertTrue(out.contains("server-got-len=0"),
                "receive after peer close must be an empty array, not a block: " + out);
    }

    @Test
    @DisplayName("close() accepts every handle type and closes each real socket")
    void closeEveryHandleType(@TempDir Path dir) throws Exception {
        String out = runJvm("""
                main() {
                    var l = net.listen(18878)
                    var server = net.bind(18879)
                    var c = net.connect("127.0.0.1", 18878)
                    c.close()
                    server.close()
                    l.close()
                    println("closed-both")
                }
                """, dir, "close");

        assertTrue(out.contains("closed-both"),
                "conn/endpoint/listener must all close cleanly: " + out);
    }

    @Test
    @DisplayName("Cross targets bind the socket surface (slice 4b); JS refuses per D-NET-JS-V1")
    void everyArtifactTargetBinds(@TempDir Path dir) throws Exception {
        Path s = dir.resolve("x.kf");
        Files.writeString(s, "main() { var l = net.listen(18880) }");
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            CompilationResult r = driver.compile(s, dir.resolve("out-" + t), t);
            assertTrue(r.success(), t + " must bind now: " + r.diagnostics().getDiagnostics());
        }
        CompilationResult js = driver.compile(s, dir.resolve("out-js"), Target.JS);
        assertFalse(js.success(), "JS must refuse the net front (D-NET-JS-V1)");
        assertTrue(js.diagnostics().getDiagnostics().toString().contains("NETN001"),
                "must name NETN001: " + js.diagnostics().getDiagnostics());
    }

    @Test
    @DisplayName("every verb resolves at CLASS LOAD (no NoSuchMethodError — the fatia-1 trap)")
    void everyVerbResolvesAtClassLoad(@TempDir Path dir) throws Exception {
        // Se algum verbo não existisse no runtime gerado, o class load falharia
        // aqui (a armadilha que a fatia 1 mediu: verde de compilação, método
        // inexistente). Compilar não basta — ESTE programa roda.
        String out = runJvm("""
                main() {
                    var l = net.listen(18881)
                    var e = net.bind(18882)
                    var c = net.connect("127.0.0.1", 18881)
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
        assertTrue(out.contains("verbs-live"), "every verb must resolve at class load: " + out);
    }
}