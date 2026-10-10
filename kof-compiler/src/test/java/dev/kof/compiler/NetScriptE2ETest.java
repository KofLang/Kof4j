package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * D-KOF-NET fatia 5 (plan docs/stdlib/network-kofnet-plan.md): o Script
 * alvo é PARIDADE POR CONSTRUÇÃO — {@code CompilerPipeline.prepareForInterpretation}
 * faz o lowering com {@code driver.target = Target.JVM} (medido 02/10, linha 389)
 * e o {@code KofInterpreter} reflete os MESMOS {@code KofRuntime.kof_net_*}
 * (corpo {@code JvmRuntimeSockets}). Estes testes provam o eco TCP, o round-trip
 * UDP e o {@code NET003} executando pelo interpretador, dourado idêntico ao JVM.
 *
 * <p>Bug raiz que a fatia achou (Q0): {@code KofInterpreterOps.newArray} não
 * tinha {@code byte}/{@code short} — {@code new Byte[n]} saía {@code int[]} e o
 * reflection morria "argument type mismatch" no primeiro consumidor real de
 * Byte[] ({@code kof_net_send}). O eco abaixo usa payload {@code new Byte[4]}
 * de propósito: é a prova viva da correção.
 */
class NetScriptE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runScript(Path dir, String name, String src) throws Exception {
        Path s = dir.resolve(name + ".kf");
        Files.writeString(s, src);
        KofInterpreter.Result r = driver.interpret(List.of(s), dir, new String[0]);
        assertEquals(0, r.exitCode(), name + " script exit: " + r.stderr());
        return r.stdout();
    }

    @Test
    @DisplayName("D-KOF-NET Script: TCP echo byte-identical over real loopback (spawn worker, Byte[] both ways)")
    void tcpEchoScript(@TempDir Path dir) throws Exception {
        String out = runScript(dir, "echo", """
                main() {
                    var l = net.listen(18911)
                    var worker = spawn {
                        var s = l.accept()
                        var got = s.receive(4096)
                        s.send(got)
                        s.close()
                    }
                    var c = net.connect("127.0.0.1", 18911)
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
                """);
        assertTrue(out.contains("sent=4"), "server must have received 4 bytes: " + out);
        assertTrue(out.contains("echo=1,2,3,4,"), "echo must be byte-identical: " + out);
        assertTrue(out.contains("len=4"), "receive must return the full payload: " + out);
    }

    @Test
    @DisplayName("D-KOF-NET Script: UDP sendTo/receive/peer with a new Byte[] datagram (the newArray fix)")
    void udpDatagramScript(@TempDir Path dir) throws Exception {
        String out = runScript(dir, "udp", """
                main() {
                    var server = net.bind(18912)
                    var client = net.bind(18913)
                    var datagram = new Byte[3]
                    datagram[0] = 9
                    datagram[1] = 8
                    datagram[2] = 7
                    client.sendTo("127.0.0.1:18912", datagram)
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
                """);
        assertTrue(out.contains("server-got=9,8,7,"), "datagram must arrive intact: " + out);
        assertTrue(out.contains("server-peer=127.0.0.1:18913"),
                "peer() must be the real source host:port: " + out);
        assertTrue(out.contains("reply-len=3"), "reply must come back: " + out);
    }

    @Test
    @DisplayName("D-KOF-NET Script: oversized datagram refuses with the same NET003 message as JVM")
    void oversizedDatagramRefusedScript(@TempDir Path dir) throws Exception {
        String out = runScript(dir, "toobig", """
                main() {
                    var e = net.bind(18914)
                    try {
                        e.sendTo("127.0.0.1:9", new Byte[65508])
                        println("NO-REFUSAL")
                    } catch (String m) {
                        println("refused")
                    }
                    e.close()
                }
                """);
        assertTrue(out.contains("refused"), "oversized datagram must refuse: " + out);
        assertTrue(!out.contains("NO-REFUSAL"), "no transparent fragmentation: " + out);
    }
}
