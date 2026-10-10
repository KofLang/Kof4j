package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §132 / #83-JS — sleep cooperativo no back-end JS (18/09).
 *
 * O `time.sleep` agora é um ponto de yield: o compilador colore o método como
 * async (via o fixpoint {@code computeAsyncColoring} que já regia {@code await})
 * e emite {@code await kof_time_sleep(ms)}; no runtime {@code kofTimeSleep}
 * devolve uma Promise (node/browser: {@code setTimeout} real; GraalJS: fila de
 * sleepers resolvida pela bomba do host em {@code KofJsRunner.drainActiveTasks},
 * que é o mini-event-loop que uma thread única do guest não consegue ser). Assim,
 * enquanto uma tarefa dorme, as demais tarefas spawned/async PROGRIDEM — o idiom
 * síncrono {@code while (!done(h)) { time.sleep(10) }} deixa de ser starvation.
 *
 * O relógio real é preservado (time.now()/Date.now() não mudam): a bomba do host
 * faz uma espera bloqueante em direção ao deadline, então KofTimeE2ETest continua honesto.
 * Ancoram o contrato: o poll síncrono que antes travava (§132) e o caso título
 * tarefa-dentro-de-tarefa; o supervisor real roda em paridade em
 * {@link KofSupervisorE2ETest#supervisorJsParity}.
 */
class AsyncSleepJsE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private Path jsEntry(Path dir) throws IOException {
        try (var s = Files.walk(dir)) {
            return s.filter(p -> p.getFileName().toString().equals("Default.mjs"))
                    .findFirst().orElseThrow();
        }
    }

    private String runJs(Path tmp, String src) throws IOException {
        Path out = tmp.resolve("out");
        Path main = tmp.resolve("Main.kf");
        Files.writeString(main, src);
        CompilationResult r = driver.compile(main, out, Target.JS);
        assertTrue(r.success(), "JS compila: " + r.diagnostics().getDiagnostics());
        try (var buf = new java.io.ByteArrayOutputStream();
             var eb = new java.io.ByteArrayOutputStream()) {
            int ec = dev.kof.runtime.KofJsRunner.run(jsEntry(out), buf,
                    java.io.InputStream.nullInputStream(), eb);
            String os = buf.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
            assertEquals(0, ec, "exit " + ec + " err=" + eb.toString(java.nio.charset.StandardCharsets.UTF_8));
            return os;
        }
    }

    // poll síncrono com time.sleep: o worker dorme 5ms e retorna 42; o laço da
    // main dorme 10ms por volta. Antes de §132 o worker NUNCA rodava (a main
    // ocupava a única thread com busy-wait) => done=false. Agora => true/42.
    @Test
    void syncPollWithSleepNowProgresses(@TempDir Path tmp) throws Exception {
        String os = runJs(tmp, """
                Int worker() { time.sleep(5); return 42 }
                main() {
                    val h = spawn worker()
                    var t = 0
                    while (!done(h) && t < 100) { time.sleep(10); t = t + 1 }
                    println(done(h))
                    println(poll(h))
                }
                """);
        assertEquals("true\n42", os);
    }

    // caso título §132: tarefa dentro de tarefa. outer faz spawn(inner) e faz
    // poll-cooperativo; main faz spawn(outer) e faz poll-cooperativo. As duas
    // camadas progridem sob sleep async.
    @Test
    void nestedTaskInTaskProgresses(@TempDir Path tmp) throws Exception {
        String os = runJs(tmp, """
                Int inner() { time.sleep(3); return 7 }
                Int outer() {
                    var h = spawn inner()
                    var t = 0
                    while (!done(h) && t < 100) { time.sleep(5); t = t + 1 }
                    return poll(h)
                }
                main() {
                    var h = spawn outer()
                    var t = 0
                    while (!done(h) && t < 200) { time.sleep(5); t = t + 1 }
                    println(done(h))
                    println(poll(h))
                }
                """);
        assertEquals("true\n7", os);
    }

    // regressão: sleep simples (sem concorrência) continua funcionando e o programa
    // termina normalmente (a bomba do host encerra quando não há sleepers nem tasks).
    @Test
    void plainSleepStillTerminates(@TempDir Path tmp) throws Exception {
        String os = runJs(tmp, """
                main() {
                    time.sleep(20)
                    println("slept")
                }
                """);
        assertEquals("slept", os);
    }
}
