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
 * D-KOF-NET fatia 1 (plan docs/stdlib/network-kofnet-plan.md): the frozen
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
            var payload = new Byte[3]
            var n = c.send(payload)
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
    @DisplayName("D-KOF-NET JVM: the frozen contract COMPILES now that slice 2 emits the runtime")
    void jvmSurfaceCompiles() throws Exception {
        // Fatia 1 recusava em TODO alvo porque o runtime GERADO não tinha o
        // método (verde falso: o class load morreria NoSuchMethodError). A
        // fatia 2 emite `JvmRuntimeSockets` com corpo java.net real, então o
        // portão abre no JVM — e o E2E `NetTcpE2ETest` prova que os verbos
        // RESOLVEM e rodam (compilar não basta).
        CompilationResult r = compile(CONTRACT, Target.JVM);
        assertTrue(r.success(), "JVM must bind now: " + r.diagnostics().getDiagnostics());
    }

    @Test
    @DisplayName("D-KOF-NET: cross targets BIND now that slice 4b emits the riscv/aarch runtime")
    void crossSurfaceCompiles() throws Exception {
        // Fatia 4b: o front x86 (NativeNetFront*) foi portado para riscv64 e o
        // aarch64 herda pelo tradutor; os símbolos existem no runtime gerado,
        // então o portão abre. O E2E `NetNativeE2ETest` prova que os verbos
        // RESOLVEM no link e rodam sob qemu.
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            CompilationResult r = compile(CONTRACT, t);
            assertTrue(r.success(), t + " must bind now: " + r.diagnostics().getDiagnostics());
        }
    }

    @Test
    @DisplayName("D-KOF-NET Native x86-64: the frozen contract BINDS now that slice 3 emits the runtime")
    void nativeSurfaceCompiles() throws Exception {
        // Fatia 3 emite `NativeNetFront*` com corpo real (sockets/handles); o
        // E2E `NetNativeE2ETest` prova que os verbos RESOLVEM e rodam.
        CompilationResult r = compile(CONTRACT, Target.NATIVE);
        assertTrue(r.success(), "native x86-64 must bind now: "
                + r.diagnostics().getDiagnostics());
    }

    @Test
    @DisplayName("D-NET-JS-V1: the whole kof.net contract REFUSES at compile on JS (NETN001)")
    void jsSurfaceRefused() throws Exception {
        // D-NET-JS-V1 (mantenedora 02/10, voto "(c)"): a perna JS do plano
        // 4a foi RECUSADA no v1 — o compilador nomeia NETN001 antes de
        // qualquer artefato; a ponte `KofJsNetBridge` fica como mecanismo
        // interno sem face de linguagem. Prova de recusa em NetJsV1RefusalE2ETest.
        CompilationResult r = compile(CONTRACT, Target.JS);
        assertFalse(r.success(), "js must refuse the net front");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("NETN001"),
                "must name NETN001: " + r.diagnostics().getDiagnostics());
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
    @DisplayName("D-NET-JS-V1: URI accessors stay green on JVM/Native; the JS leg refuses with the whole front")
    void uriAccessorsUnchanged() throws Exception {
        String uri = "main() { println(net.host(\"http://x.io/p\")) }";
        for (Target t : new Target[]{Target.JVM, Target.NATIVE}) {
            assertTrue(compile(uri, t).success(), t + " uri face must stay green");
        }
        CompilationResult js = compile(uri, Target.JS);
        assertFalse(js.success(), "JS uri is part of the refused net front");
        assertTrue(js.diagnostics().getDiagnostics().toString().contains("NETN001"),
                "must name NETN001: " + js.diagnostics().getDiagnostics());
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
