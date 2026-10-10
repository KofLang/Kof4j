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
 * D-KOF-NET fatia 6 (handle types nomeados): os handles do front ({@code
 * Listener}, {@code Conn}, {@code Endpoint}) eram tipos ANÔNIMOS para o
 * usuário — {@code Int pump(Conn c)} morria em SEM011 porque o checker de
 * tipos declarados só isentava builtins/coleções/ui/media. Product code
 * (KofShare transfer core, D-KOFSHARE-100KOF) PRECISA passar handles entre
 * funções; todo o ecossistema anterior viveu de {@code var} + closures só
 * por isso. Fix: os três nomes entram no registro de tipos declarados
 * ({@code KofNet.typeByName} via {@code CompilerTypes.builtinDeclaredType},
 * mesma padronagem do {@code Label} de ui) e valem como parâmetro/retorno
 * em qualquer alvo onde o verbo corresponde liga.
 */
class NetHandleTypesE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    // Um programa só, três handles tipados: Conn (TCP), Listener (accept) e
    // Endpoint (UDP). Pré-fix: SEM011 nos três parâmetros.
    private static final String SRC = """
            Conn opener(String host, Int port) {
                return net.connect(host, port)
            }

            Int pumpIn(Conn c) {
                var b = c.receive(16)
                c.send(b)
                c.close()
                return b.length
            }

            Int acceptAndPump(Listener l) {
                var s = l.accept()
                return pumpIn(s)
            }

            Int udpRound(Endpoint srv, Endpoint cli) {
                var msg = new Byte[3]
                msg[0] = 7
                msg[1] = 8
                msg[2] = 9
                cli.sendTo("127.0.0.1:" + 18942, msg)
                var got = srv.receive(64)
                var from = srv.peer()
                srv.close()
                cli.close()
                if (from.length == 0) {
                    return 0
                }
                return got.length
            }

            main() {
                var l = net.listen(18941)
                var worker = spawn {
                    acceptAndPump(l)
                }
                var c = opener("127.0.0.1", 18941)
                var p = new Byte[5]
                var i = 0
                while (i < 5) {
                    p[i] = i + 1
                    i = i + 1
                }
                c.send(p)
                var back = c.receive(16)
                c.close()
                await worker
                l.close()
                println("tcp=" + back.length)
                var e1 = net.bind(18942)
                var e2 = net.bind(18943)
                println("udp=" + udpRound(e1, e2))
                println("HANDLES-OK")
            }
            """;

    private String runScript(Path dir, String src) throws Exception {
        Path s = dir.resolve("handles.kf");
        Files.writeString(s, src);
        KofInterpreter.Result r = driver.interpret(List.of(s), dir, new String[0]);
        assertEquals(0, r.exitCode(), "script exit: " + r.stderr() + r.stdout());
        return r.stdout();
    }

    @Test
    @DisplayName("D-KOF-NET: Conn/Listener/Endpoint are legal declared parameter types (Script)")
    void handleTypesScript(@TempDir Path dir) throws Exception {
        String out = runScript(dir, SRC);
        assertTrue(out.contains("tcp=5"), "typed Conn round-trip: " + out);
        assertTrue(out.contains("udp=3"), "typed Endpoint round-trip: " + out);
        assertTrue(out.contains("HANDLES-OK"), out);
    }

    @Test
    @DisplayName("D-KOF-NET: same program compiles and runs on the JVM target (parity)")
    void handleTypesJvm(@TempDir Path dir) throws Exception {
        Path s = dir.resolve("handles.kf");
        Files.writeString(s, SRC);
        CompilationResult r = driver.compile(s, dir.resolve("out"), Target.JVM);
        assertTrue(r.success(), "must compile: " + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "--enable-native-access=ALL-UNNAMED",
                "-cp", dir.resolve("out").toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        boolean done = p.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(done, "JVM run must finish");
        String out = new String(p.getInputStream().readAllBytes());
        assertEquals(0, p.exitValue(), "jvm exit: " + out);
        assertTrue(out.contains("tcp=5") && out.contains("udp=3") && out.contains("HANDLES-OK"), out);
    }
}
