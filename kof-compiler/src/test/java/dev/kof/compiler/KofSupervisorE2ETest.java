package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OTP núcleo (issue #83) — o menor supervisor funcional em Kof puro, entregue
 * como pacote virtual {@code kof.supervisor} (host {@code dev/kof/supervisor-host.kf}
 * escrito EM KOF, injetado só quando o usuário importa — análogo ao android-host).
 *
 * Gate DD-OTP-11 (contenção): worker que falha 2× e termina na 3ª → o supervisor
 * reinicia, o programa completa, restarts==2 (contagem deste núcleo: reinicio=1
 * por relançamento; o worker chamado 3× ⇒ 2 reinícios). Escopo: observar falha,
 * reiniciar individualmente, respeitar limite e encerrar controlado — nunca
 * árvore/heartbeat (DD-OTP-04/05 adiados no plano).
 *
 * Paridade honesta (regra 6 / R6): NATIVE e JS bloqueiam no compile-time com
 * OTP001 (§109 longjmp cross-thread) / OTP002 (§112 event-loop single-thread),
 * nunca fallback silencioso. JVM (runJvm) + Script (interpret) executam o núcleo.
 */
class KofSupervisorE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String APP = """
            import kof.supervisor

            class WF implements KofWorkerFactory {
                Int lim
                Int chamadas
                constructor(Int lim) { this.lim = lim; this.chamadas = 0 }
                KofWorker novo() {
                    chamadas = chamadas + 1
                    return WK(chamadas, lim)
                }
            }
            class WK implements KofWorker {
                Int tentativa
                Int lim
                constructor(Int tentativa, Int lim) { this.tentativa = tentativa; this.lim = lim }
                Object run() {
                    if (tentativa <= lim) { throw "falha-" + tentativa }
                    return "" + tentativa
                }
            }
            class Esc implements KofEscalate {
                Int chamadas = 0
                Void disparou(String id, String motivo, Int reinicios) { chamadas = chamadas + 1 }
            }
            main() {
                var esc = Esc()
                var wf = WF(2)
                var s = supervisor("t").child("w", wf, "transient").escalate(esc)
                s.start()
                var t = 0
                while (s.stats().vivos > 0 && t < 200) { time.sleep(10); t = t + 1 }
                var st = s.stats()
                println("restarts=" + st.restarts + " escaladas=" + esc.chamadas + " fabrica=" + wf.chamadas)
                s.stop(1000)
                println("parou vivos=" + s.stats().vivos)
            }
            """;

    private Path write(Path tmp, String src) throws IOException {
        Path main = tmp.resolve("Main.kf");
        Files.writeString(main, src);
        return main;
    }

    private Path writeNamed(Path tmp, String name, String src) throws IOException {
        Path main = tmp.resolve(name);
        Files.writeString(main, src);
        return main;
    }

    private String runJvm(Path tmp, String src) throws IOException {
        Path out = tmp.resolve("out");
        CompilationResult r = driver.compile(write(tmp, src), out, Target.JVM);
        assertTrue(r.success(), "JVM compila: " + r.diagnostics().getDiagnostics());
        try {
            Process p = new ProcessBuilder("java", "-cp", out.toString(), "Default.Main")
                    .redirectErrorStream(true).start();
            String os = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM roda limpo: " + os);
            return os;
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    // ---- gate DD-OTP-11 no JVM (alvo de referência) ----
    @Test
    void supervisorReiniciaWorkerQueFalhaECompleta(@TempDir Path tmp) throws IOException {
        String os = runJvm(tmp, APP);
        assertTrue(os.contains("restarts=2"), "2 reinicios (worker chamado 3x): " + os);
        assertTrue(os.contains("escaladas=2"), "escalate a cada falha: " + os);
        assertTrue(os.contains("fabrica=3"), "factory NOVA por reinicio (DD-OTP-06): " + os);
        assertTrue(os.contains("parou vivos=0"), "stop encerra controlado (DD-OTP-08): " + os);
    }

    // ---- mesma semantica no interpretador (paridade por construcao) ----
    @Test
    void supervisorNoInterpretadorParidade(@TempDir Path tmp) throws IOException {
        Path main = write(tmp, APP);
        KofInterpreter.Result ir = driver.interpret(List.of(main), tmp, new String[0]);
        String os = ir.stdout() + ir.stderr();
        assertEquals(0, ir.exitCode(), "script roda limpo: " + os);
        assertTrue(os.contains("restarts=2"), "paridade interpretador: " + os);
        assertTrue(os.contains("fabrica=3"), "factory nova por reinicio: " + os);
        assertTrue(os.contains("parou vivos=0"), "stop cooperativo: " + os);
    }

    // ---- limite de reinicios: worker que NUNCA termina ----
    @Test
    void limiteDeReiniciosParaSemEscalarSemCallback(@TempDir Path tmp) throws IOException {
        String src = """
                import kof.supervisor
                class WF2 implements KofWorkerFactory {
                    Int chamadas
                    constructor() { this.chamadas = 0 }
                    KofWorker novo() { chamadas = chamadas + 1; return WK2(chamadas) }
                }
                class WK2 implements KofWorker {
                    Int n
                    constructor(Int n) { this.n = n }
                    Object run() { throw "sempre-" + n }
                }
                main() {
                    var wf = WF2()
                    var s = supervisor("r").child("w", wf, "permanent").restartLimit(2)
                    s.start()
                    var t = 0
                    while (s.stats().vivos > 0 && t < 200) { time.sleep(10); t = t + 1 }
                    println("parou vivos=" + s.stats().vivos + " fabrica=" + wf.chamadas)
                    s.stop(500)
                }
                """;
        String os = runJvm(tmp, src);
        assertTrue(os.contains("parou vivos=0"), "supervisor para de reiniciar no limite: " + os);
        assertTrue(os.contains("fabrica=3"), "3 tentativas = 1 + 2 reinicios (max=2): " + os);
    }

    // ---- R6: nativos/JS bloqueiam no compile-time com codigo claro, nunca silencio ----
    @Test
    void nativeGateOtp001(@TempDir Path tmp) throws IOException {
        CompilationResult r = driver.compile(writeNamed(tmp, "N.kf", "import kof.supervisor\nmain(){ supervisor(\"x\") }"),
                tmp.resolve("o"), Target.NATIVE);
        assertFalse(r.success(), "NATIVE nao deve compilar supervisor hoje");
        assertTrue(r.diagnostics().getDiagnostics().stream().anyMatch(d -> "OTP001".equals(d.code())),
                "esperava OTP001, foi: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void jsGateOtp002(@TempDir Path tmp) throws IOException {
        CompilationResult r = driver.compile(writeNamed(tmp, "J.kf", "import kof.supervisor\nmain(){ supervisor(\"x\") }"),
                tmp.resolve("o"), Target.JS);
        assertFalse(r.success(), "JS nao deve compilar supervisor hoje");
        assertTrue(r.diagnostics().getDiagnostics().stream().anyMatch(d -> "OTP002".equals(d.code())),
                "esperava OTP002, foi: " + r.diagnostics().getDiagnostics());
    }

    // ---- regressao: sem o import, nada muda (supervisor invisivel, programa limpo) ----
    @Test
    void semImportNadaInjeta(@TempDir Path tmp) throws IOException {
        Path out = tmp.resolve("o");
        CompilationResult r = driver.compile(writeNamed(tmp, "P.kf",
                "main(){ var s = supervisor(\"x\") }"), out, Target.JVM);
        assertFalse(r.success(), "sem 'import kof.supervisor', supervisor e desconhecido: "
                + r.diagnostics().getDiagnostics());
    }
}
