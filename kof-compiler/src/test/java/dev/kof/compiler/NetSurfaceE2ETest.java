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
 * D-KOF-NET fatia 1 (plan docs/development/network-kofnet-plan.md): the frozen
 * surface contract must BIND at compile time on the JVM and every non-implemented
 * target must REFUSE honestly with NET002 (no-silent-fallback). Handles are
 * strings composed as in {@code db}/{@code orm} (no new stdlib record — the
 * "scalars instead of records" precedent of {@code net} v1, plan §4 decisão 09/09).
 */
public class NetSurfaceE2ETest {

    @TempDir Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(String src, Target t) throws Exception {
        Path f = tmp.resolve("M.kf");
        Files.writeString(f, src);
        return driver.compile(f, tmp.resolve("out-" + t), t);
    }

    private static final String CONTRACT = """
        main() {
            var l = net.listen(8123)
            var c = net.connect("127.0.0.1", 8123)
            var n = c.send("abc".getBytes())
            var data = c.receive(4096)
            var e = net.bind(9123)
            e.sendTo("127.0.0.1:9123", data)
            var got = e.receive(4096)
            var addr = e.peer()
            c.close()
            e.close()
            l.close()
            println(n)
        }
        """;

    @Test
    @DisplayName("D-KOF-NET JVM: refuses the socket verbs until slice 2 emits the runtime (no false green)")
    void jvmRefusesUntilRuntimeExists() throws Exception {
        // Medido 01/10: o descritor JVM sai correto e o bytecode chama
        // `KofRuntime.kof_net_listen:(I)Ldev/kof/runtime/KofRuntime$NetListener;`,
        // mas o runtime GERADO não tem o método (`javap` devolve só os 8 verbos
        // de URI + split). Aceitar aqui seria um VERDE FALSO (Q5): o class load
        // morreria NoSuchMethodError. A honestidade exige recusar até a fatia 2.
        CompilationResult r = compile(CONTRACT, Target.JVM);
        assertFalse(r.success(), "JVM has no socket runtime yet — slice 2 lands it");
        String diag = r.diagnostics().getDiagnostics().toString();
        assertTrue(diag.contains("NET002"), diag);
    }

    @Test
    @DisplayName("D-KOF-NET: every socket verb is refused on EVERY target (no silent drop)")
    void everyVerbRefusedOnEveryTarget() throws Exception {
        String one = "main() { var l = net.%s }";
        String[] verbs = {"listen(8123)", "connect(\"h\", 1)", "bind(8123)",
                "accept(h)", "send(h, b)", "receive(h, 10)", "peer(e)", "close(h)"};
        for (Target t : new Target[]{Target.JVM, Target.NATIVE, Target.JS}) {
            for (String v : verbs) {
                CompilationResult r = compile(String.format(one, v), t);
                assertFalse(r.success(), t + " must refuse net." + v + ": "
                        + r.diagnostics().getDiagnostics());
            }
        }
    }

    @Test
    @DisplayName("D-KOF-NET Native: refuses the socket verbs honestly with NET002")
    void nativeRefusesWithNet002() throws Exception {
        CompilationResult r = compile(CONTRACT, Target.NATIVE);
        assertFalse(r.success(), "native has no socket runtime yet");
        String diag = r.diagnostics().getDiagnostics().toString();
        assertTrue(diag.contains("NET002"), diag);
    }

    @Test
    @DisplayName("D-KOF-NET JS: refuses the socket verbs honestly with NET002")
    void jsRefusesWithNet002() throws Exception {
        CompilationResult r = compile(CONTRACT, Target.JS);
        assertFalse(r.success(), "js host bridge has no net runtime yet");
        String diag = r.diagnostics().getDiagnostics().toString();
        assertTrue(diag.contains("NET002"), diag);
    }

    @Test
    @DisplayName("D-KOF-NET arity: net.listen with 0 args is a named diagnostic, never silent")
    void arityGuarded() throws Exception {
        CompilationResult r = compile("main() { var l = net.listen() }", Target.JVM);
        assertFalse(r.success());
        String diag = r.diagnostics().getDiagnostics().toString();
        assertTrue(diag.contains("SEM") || diag.contains("NET"), diag);
    }

    // Script nao tem rota de artifacts no CompilerDriver.compile (COMP003 —
    // `kof run --target script` interpreta IR direto). A gate de face para
    // Script e coberta na fatia 5 do plano network-kofnet.

    @Test
    @DisplayName("D-KOF-NET: URI accessors keep compiling on ALL targets (NET001-closed behavior)")
    void uriAccessorsUnchanged() throws Exception {
        String uri = "main() { println(net.host(\"http://x.io/p\")) }";
        for (Target t : new Target[]{Target.JVM, Target.NATIVE, Target.JS}) {
            assertTrue(compile(uri, t).success(), t + " uri face must stay green");
        }
    }

    @Test
    @DisplayName("D-KOF-NET: catalog lists exactly the bound verbs")
    void catalogConsistent() {
        assertEquals(true, KofNet.functions().containsAll(java.util.List.of(
                "scheme", "host", "port", "path", "query", "fragment",
                "queryEncode", "queryDecode",
                "listen", "connect", "bind")));
        for (String memberOnly : java.util.List.of(
                "accept", "send", "receive", "sendTo", "peer", "close")) {
            assertFalse(KofNet.functions().contains(memberOnly),
                    memberOnly + " e membro de handle, nao face de namespace");
        }
    }
}
