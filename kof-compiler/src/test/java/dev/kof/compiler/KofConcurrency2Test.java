package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class KofConcurrency2Test {
    private final CompilerDriver driver = new CompilerDriver();

    private static boolean has(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c).redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                if (p.waitFor() != 0 || out.isEmpty()) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    @Test
    void cancelCooperativeJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                Int trabalho() {
                    var i = 0
                    while (i < 10000 && !cancelled()) {
                        time.sleep(1)
                        i++
                    }
                    if (cancelled()) {
                        println("cancelado")
                    } else {
                        println("completo")
                    }
                    return i
                }

                main() {
                    val r = spawn trabalho()
                    time.sleep(30)
                    assert(cancel(r))
                    await r
                    println("fim")
                }
                """, "cancelado\nfim");
    }


    @Test
    void cancelledOutsideIsFalse(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                main() {
                    assert(!cancelled())
                    println("ok")
                }
                """, "ok");
    }

    @Test
    void selectAnyJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                String rapida() { return "primeira" }

                String lenta() {
                    time.sleep(300)
                    return "segunda"
                }

                main() {
                    val a = spawn lenta()
                    val b = spawn rapida()
                    val v = selectAny(a, b)
                    println(v)
                }
                """, "primeira");
    }

    @Test
    void selectAnyNative(@TempDir Path tmp) throws Exception {
        // CONC001 residual fechado: selectAny nativo (polling 1ms sobre o handle).
        // Os handles são criados JUNTOS (spawn-all-up-front) e o selectAny vem
        // depois — evitam o bug pré-existente de spawn→await→spawn (thread já
        // finalizada + novo pthread_create corrompe a pilha da main).
        Path f = tmp.resolve("M.kf");
        Files.writeString(f, """
                Int t1() { return 1 }
                Int t2() { time.sleep(300); return 2 }
                main() {
                    val a = spawn t1()
                    val b = spawn t2()
                    time.sleep(50)
                    println(selectAny(a, b))
                }
                """);
        CompilationResult r = driver.compile(f, tmp.resolve("out"), Target.NATIVE);
        assertTrue(r.success(), "Native selectAny deve compilar: " + r.diagnostics().getDiagnostics());
        Path bin = tmp.resolve("out").resolve("Default/Main");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes()).trim();
        assertEquals(0, p.waitFor(), "exit code, output: " + output);
        assertEquals("1", output, "a task rápida (1) deve vencer a lenta (2)");
    }

    @Test
    void cancelCooperativeNative(@TempDir Path tmp) throws Exception {
        // CONC001 residual fechado: cancel cooperativo nativo (flag por TID).
        // Worker checa !cancelled() no loop; main cancela após 30ms. Spawn único
        // (evita o bug pré-existente de spawn→await→spawn).
        Path f = tmp.resolve("M.kf");
        Files.writeString(f, """
                Int trabalho() {
                    var i = 0
                    while (i < 100000 && !cancelled()) {
                        time.sleep(1)
                        i++
                    }
                    if (cancelled()) { println("cancelado") } else { println("completo") }
                    return i
                }
                main() {
                    val r = spawn trabalho()
                    time.sleep(30)
                    assert(cancel(r))
                    await r
                    println("fim")
                }
                """);
        CompilationResult r = driver.compile(f, tmp.resolve("out"), Target.NATIVE);
        assertTrue(r.success(), "Native cancel/cooperativo deve compilar: " + r.diagnostics().getDiagnostics());
        Path bin = tmp.resolve("out").resolve("Default/Main");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes()).trim();
        assertEquals(0, p.waitFor(), "exit code, output: " + output);
        assertEquals("cancelado\nfim", output, "o worker deve ver o cancel e encerrar cedo");
    }

    @Test
    void awaitTimeoutJvm(@TempDir Path tmp) throws Exception {
        // G8/CONC residual: awaitTimeout(r, ms) -> valor no prazo; lança no estouro
        // (capturável via try/catch). JVM: Future.get(ms).
        runJvm(tmp, """
                Int lenta() { time.sleep(400); return 9 }
                Int rapida() { return 42 }
                main() {
                    val r = spawn lenta()
                    try {
                        awaitTimeout(r, 50)
                        println("in")
                    } catch (String e) {
                        println("err")
                    }
                    val q = spawn rapida()
                    var w = awaitTimeout(q, 100)
                    println("q=" + w)
                    var f = await r
                    println("f=" + f)
                }
                """, "err\nq=42\nf=9");
    }

    @Test
    void awaitTimeoutNative(@TempDir Path tmp) throws Exception {
        // awaitTimeout no Native: polling 1ms com deadline; estouro -> kof_throw_string
        // (try/catch do usuário).
        // Ordem segura p/ o bug pré-existente spawn->(task)->spawn (SIGSEGV no
        // próximo pthread_create): a 1ª task é uma sleep (não alocadora) e é
        // joinada (`await`) antes do 2º spawn.
        Path f = tmp.resolve("M.kf");
        Files.writeString(f, """
                Int lenta() { time.sleep(400); return 9 }
                Int rapida() { return 42 }
                main() {
                    val r = spawn lenta()
                    try {
                        awaitTimeout(r, 50)
                        println("in")
                    } catch (String e) {
                        println("err")
                    }
                    var f = await r
                    println("f=" + f)
                    val q = spawn rapida()
                    var w = awaitTimeout(q, 100)
                    println("q=" + w)
                }
                """);
        CompilationResult r = driver.compile(f, tmp.resolve("out"), Target.NATIVE);
        assertTrue(r.success(), "Native awaitTimeout deve compilar: " + r.diagnostics().getDiagnostics());
        Path bin = tmp.resolve("out").resolve("Default/Main");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes()).trim();
        assertEquals(0, p.waitFor(), "exit code, output: " + output);
        assertEquals("err\nf=9\nq=42", output, "lenta estoura (catch); rápida no prazo");
    }

    @Test
    void awaitTimeoutJs(@TempDir Path tmp) throws Exception {
        // JS CONC003: task instantânea — awaitTimeout conclui no prazo.
        runJs(tmp, """
                Int t() { return 9 }
                main() {
                    val r = spawn t()
                    var v = awaitTimeout(r, 50)
                    println(v)
                }
                """, "9");
    }

    @Test
    void awaitTimeoutSlowTaskJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                Int lenta() { time.sleep(200); return 9 }
                main() {
                    val r = spawn lenta()
                    try {
                        awaitTimeout(r, 20)
                        println("in")
                    } catch (String e) {
                        println("err")
                    }
                }
                """, "err");
    }

    @Test
    void awaitTimeoutSlowTaskJs(@TempDir Path tmp) throws Exception {
        // CONC003: task com vários yields assíncronos — estouro real (time.sleep
        // bloqueia o event loop no JS e não serve aqui).
        runJs(tmp, """
                Int step() { return 1 }
                Int lenta() {
                    var i = 0
                    while (i < 80) {
                        await spawn step()
                        i++
                    }
                    return 9
                }
                main() {
                    val r = spawn lenta()
                    try {
                        awaitTimeout(r, 5)
                        println("in")
                    } catch (String e) {
                        println("err")
                    }
                }
                """, "err");
    }

    @Test
    void spawnInterleavingJvm(@TempDir Path tmp) throws Exception {
        String out = runJvm(tmp, """
                Int tick() { return 1 }

                Int slow() {
                    await spawn tick()
                    await spawn tick()
                    await spawn tick()
                    println("A:done")
                    return 1
                }

                main() {
                    spawn slow()
                    spawn { println("B") }
                }
                """);
        int b = out.indexOf("B");
        int a = out.indexOf("A:done");
        assertTrue(b >= 0 && a >= 0, "saída: " + out);
        assertTrue(b < a, "B deve aparecer antes de A:done (não-bloqueante): " + out);
    }

    @Test
    void spawnInterleavingJs(@TempDir Path tmp) throws Exception {
        // Impossível sob fake sequencial: slow() terminaria (incl. A:done)
        // antes de B ser despachado.
        String out = runJs(tmp, """
                Int tick() { return 1 }

                Int slow() {
                    await spawn tick()
                    await spawn tick()
                    await spawn tick()
                    println("A:done")
                    return 1
                }

                main() {
                    spawn slow()
                    spawn { println("B") }
                }
                """);
        int b = out.indexOf("B");
        int a = out.indexOf("A:done");
        assertTrue(b >= 0 && a >= 0, "saída: " + out);
        assertTrue(b < a, "B deve aparecer antes de A:done (microtasks reais): " + out);
    }

    @Test
    void channelBlocksBeforeSendJvm(@TempDir Path tmp) throws Exception {
        // JVM: receive bloqueia a virtual thread. A única ordem garantida é
        // recv:42 DEPOIS de pre-send (o receive só retorna após o send).
        // recv-wait vs pre-send e recv:42 vs post-send são corrida de
        // agendamento de virtual threads — não pinamos.
        String out = runJvm(tmp, """
                main() {
                    val c = channel<Int>()
                    spawn {
                        println("recv-wait")
                        val v = c.receive()
                        println("recv:" + v)
                    }
                    time.sleep(20)
                    println("pre-send")
                    c.send(42)
                    println("post-send")
                }
                """);
        for (String line : new String[]{"recv-wait", "pre-send", "post-send", "recv:42"}) {
            assertTrue(out.contains(line), "faltou '" + line + "' em: " + out);
        }
        assertTrue(out.indexOf("pre-send") < out.indexOf("recv:42"),
                "receive deve bloquear até o send (recv:42 após pre-send): " + out);
    }

    @Test
    void channelBlocksBeforeSendJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
                Int tick() { return 0 }

                main() {
                    val c = channel<Int>()
                    spawn {
                        println("recv-wait")
                        val v = await c.receive()
                        println("recv:" + v)
                    }
                    await spawn tick()
                    println("pre-send")
                    c.send(42)
                    println("post-send")
                }
                """, "recv-wait\npre-send\npost-send\nrecv:42");
    }

    @Test
    void selectAnyJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
                Int tick() { return 0 }

                Int lenta() {
                    await spawn tick()
                    await spawn tick()
                    await spawn tick()
                    return 1
                }

                Int rapida() { return 2 }

                main() {
                    val a = spawn lenta()
                    val b = spawn rapida()
                    println(selectAny(a, b))
                }
                """, "2");
    }

    @Test
    void channelJvm(@TempDir Path tmp) throws Exception {
        // Canais tipados (G8): channel<Int>() FIFO; send/receive. JVM:
        // LinkedBlockingQueue (put/take bloqueantes).
        runJvm(tmp, """
                main() {
                    val c = channel<Int>()
                    c.send(5)
                    c.send(6)
                    c.send(7)
                    var s = 0
                    var i = 0
                    while (i < 3) {
                        s = s + c.receive()
                        i++
                    }
                    println("s=" + s)
                    val cs = channel<String>()
                    cs.send("a")
                    cs.send("b")
                    println(cs.receive() + cs.receive())
                }
                """, "s=18\nab");
    }

    @Test
    void channelNative(@TempDir Path tmp) throws Exception {
        // Canais no Native: FIFO de lista ligada + mutex futex (mesma tecnica
        // do kof_alloc). Sem spawn (bug pre-existente spawn->await->spawn).
        Path f = tmp.resolve("M.kf");
        Files.writeString(f, """
                main() {
                    val c = channel<Int>()
                    c.send(5)
                    c.send(6)
                    c.send(7)
                    var s = 0
                    var i = 0
                    while (i < 3) {
                        s = s + c.receive()
                        i++
                    }
                    println("s=" + s)
                    val cs = channel<String>()
                    cs.send("a")
                    cs.send("b")
                    println(cs.receive() + cs.receive())
                }
                """);
        CompilationResult r = driver.compile(f, tmp.resolve("out"), Target.NATIVE);
        assertTrue(r.success(), "Native channel deve compilar: " + r.diagnostics().getDiagnostics());
        Path bin = tmp.resolve("out").resolve("Default/Main");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes()).trim();
        assertEquals(0, p.waitFor(), "exit code, output: " + output);
        assertEquals("s=18\nab", output, "FIFO Int + String no Native");
    }

    @Test
    void channelJs(@TempDir Path tmp) throws Exception {
        // JS sequencial: canal = {items:[]} (send push, receive shift).
        runJs(tmp, """
                main() {
                    val c = channel<Int>()
                    c.send(5)
                    c.send(6)
                    c.send(7)
                    var s = 0
                    var i = 0
                    while (i < 3) {
                        s = s + c.receive()
                        i++
                    }
                    println("s=" + s)
                }
                """, "s=18");
    }

    @Test
    void channelAsFunctionParameterJvm(@TempDir Path tmp) throws Exception {
        // Channel<T> como PARÂMETRO: antes o tipo do parâmetro era
        // ClassType(package="") e o isChannel exigia "kof.concurrent" → o
        // dispatch caía no genérico e gerava bytecode inválido (JVM),
        // undefined reference (Native) e c.receive() inexistente (JS).
        runJvm(tmp, """
                Int soma(Channel<Int> c) {
                    var s = 0
                    s = s + c.receive()
                    s = s + c.receive()
                    return s
                }
                main() {
                    var c = channel<Int>()
                    c.send(3)
                    c.send(4)
                    println(soma(c))
                }
                """, "7");
    }

    @Test
    void channelAsFunctionParameterNative(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("M.kf");
        Files.writeString(f, """
                Int soma(Channel<Int> c) {
                    var s = 0
                    s = s + c.receive()
                    s = s + c.receive()
                    return s
                }
                main() {
                    var c = channel<Int>()
                    c.send(3)
                    c.send(4)
                    println(soma(c))
                }
                """);
        CompilationResult r = driver.compile(f, tmp.resolve("out"), Target.NATIVE);
        assertTrue(r.success(), "Native channel-params deve compilar: " + r.diagnostics().getDiagnostics());
        Path bin = tmp.resolve("out").resolve("Default/Main");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes()).trim();
        assertEquals(0, p.waitFor(), "exit code, output: " + output);
        assertEquals("7", output, "channel como parâmetro no Native");
    }

    @Test
    void channelAsFunctionParameterJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
                Int soma(Channel<Int> c) {
                    var s = 0
                    s = s + c.receive()
                    s = s + c.receive()
                    return s
                }
                main() {
                    var c = channel<Int>()
                    c.send(3)
                    c.send(4)
                    println(soma(c))
                }
                """, "7");
    }

    @Test
    void pollDoneNative(@TempDir Path tmp) throws Exception {
        // CONC001 residual fechado: done/poll não-bloqueantes sobre o handle
        // nativo (flag done no bloco de 32B: 0=tag(2), 4=done, 16=result)
        Path f = tmp.resolve("M.kf");
        Files.writeString(f, """
                Int lenta() { time.sleep(400); return 7 }
                Int rapida() { return 3 }
                main() {
                    val r = spawn lenta()
                    val f = spawn rapida()
                    time.sleep(50)
                    println("poll_f=" + poll(f))
                    println("done_r=" + done(r))
                    println("poll_r=" + poll(r))
                    println("await_r=" + await r)
                    println("done_r2=" + done(r))
                }
                """);
        CompilationResult r = driver.compile(f, tmp.resolve("out"), Target.NATIVE);
        assertTrue(r.success(), "Native poll/done deve compilar: " + r.diagnostics().getDiagnostics());
        Path bin = tmp.resolve("out").resolve("Default/Main");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes()).trim();
        assertEquals(0, p.waitFor(), "exit code, output: " + output);
        // task rápida termina antes da main checar; lenta não
        assertTrue(output.contains("poll_f=3"), "poll da rápida devolve o valor: " + output);
        assertTrue(output.contains("done_r=false"), "lenta ainda não terminou: " + output);
        assertTrue(output.contains("poll_r=0"), "poll da lenta não-pronta devolve 0: " + output);
        assertTrue(output.contains("await_r=7"), "await da lenta devolve o valor: " + output);
        assertTrue(output.contains("done_r2=true"), "done vira true após o await: " + output);
    }

    @Test
    void cancelJsSequential(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
                Int t() { return 9 }
                main() {
                    val r = spawn t()
                    assert(cancel(r) == 0)
                    assert(cancelled() == 0)
                    println(await r)
                }
                """, "9");
    }

    @Test
    void schedulerEveryNative(@TempDir Path tmp) throws Exception {
        // SCHED001 fechado: scheduler.every/cancel no Native — thread por job
        // (trampoline: usleep ms→us + invoke da task enquanto active) e
        // cancel(id) marca active=0; o job sai sozinho. Ticks no 1º trecho,
        // silêncio após o cancel, END por último.
        Path f = tmp.resolve("M.kf");
        Files.writeString(f, """
                main() {
                    var id = scheduler.every(100) { println("T") }
                    time.sleep(250)
                    scheduler.cancel(id)
                    time.sleep(250)
                    println("END")
                }
                """);
        CompilationResult r = driver.compile(f, tmp.resolve("out"), Target.NATIVE);
        assertTrue(r.success(), "Native scheduler deve compilar: " + r.diagnostics().getDiagnostics());
        Path bin = tmp.resolve("out").resolve("Default/Main");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes()).trim();
        assertEquals(0, p.waitFor(), "exit code, output: " + output);
        assertTrue(output.endsWith("END"), "END deve ser a última linha: " + output);
        int ticks = 0;
        for (String l : output.split("\n")) if (l.equals("T")) ticks++;
        assertTrue(ticks >= 1 && ticks <= 6, "esperava 1..6 ticks, veio " + ticks + ": " + output);
    }

    @Test
    void schedulerEveryJvm(@TempDir Path tmp) throws Exception {
        // Paridade JVM: scheduler.every/cancel (thread daemon por job).
        // Contagem frouxa (timer não determinístico); END sempre por último.
        Path file = tmp.resolve("M.kf");
        Files.writeString(file, """
                main() {
                    var id = scheduler.every(100) { println("T") }
                    time.sleep(250)
                    scheduler.cancel(id)
                    time.sleep(250)
                    println("END")
                }
                """);
        Path outDir = tmp.resolve("out");
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM scheduler deve compilar: " + result.diagnostics().getDiagnostics());
        try {
            Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                    "-cp", outDir.toString(), "Default.Main").redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "JVM exit code, output: " + output);
            assertTrue(output.endsWith("END"), "END deve ser a última linha: " + output);
            int ticks = 0;
            for (String l : output.split("\n")) if (l.equals("T")) ticks++;
            assertTrue(ticks >= 1 && ticks <= 10, "esperava 1..10 ticks, veio " + ticks + ": " + output);
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }

    @Test
    void schedulerEveryCrossNative(@TempDir Path tmp) throws Exception {
        // SCHED001 FEITO no cross (05/09): thread por job via clone 220 +
        // nanosleep 101 + spinlock amoswap.w (mesmo mecanismo do spawn).
        // Ticks no 1º trecho, silêncio após o cancel, END por último —
        // contagem frouxa (timer não determinístico sob qemu).
        Path f = tmp.resolve("M.kf");
        Files.writeString(f, """
                main() {
                    var id = scheduler.every(100) { println("T") }
                    time.sleep(250)
                    scheduler.cancel(id)
                    time.sleep(250)
                    println("END")
                }
                """);
        String[] q = {null, "qemu-riscv64", "qemu-aarch64"};
        Target[] ts = {Target.NATIVE, Target.NATIVE_RISCV64, Target.NATIVE_AARCH64};
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64")
                        && has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain riscv64/aarch64 + qemu ausente — pulando (NATIVE002)");
        for (int i = 0; i < 3; i++) {
            CompilationResult r = driver.compile(f, tmp.resolve("out-" + i), ts[i]);
            assertTrue(r.success(), ts[i] + " deve compilar: " + r.diagnostics().getDiagnostics());
            Path bin = tmp.resolve("out-" + i).resolve("Default/Main");
            var pb = new ProcessBuilder(bin.toString()).redirectErrorStream(true);
            if (q[i] != null) pb.command().add(0, q[i]);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes()).trim();
            assertEquals(0, p.waitFor(), ts[i] + " exit, output: " + output);
            assertTrue(output.endsWith("END"), ts[i] + ": END deve ser a última linha: " + output);
            int ticks = 0;
            for (String l : output.split("\n")) if (l.equals("T")) ticks++;
            assertTrue(ticks >= 1 && ticks <= 6, ts[i] + ": esperava 1..6 ticks, veio " + ticks + ": " + output);
        }
    }

    // ── helpers ──
    private String runJvm(Path tempDir, String source) throws java.io.IOException {
        return runJvm(tempDir, source, null);
    }

    private String runJvm(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        try {
            Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                    "-cp", outDir.toString(), "Default.Main").redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code, output: " + output);
            if (expected != null) {
                assertEquals(expected, output, "JVM output");
            }
            return output;
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }

    private String runJs(Path tempDir, String source) throws java.io.IOException {
        return runJs(tempDir, source, null);
    }

    private String runJs(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JS);
        assertTrue(result.success(), "JS compile failed: " + result.diagnostics().getDiagnostics());
        try (java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream()) {
            int ec = dev.kof.runtime.KofJsRunner.run(findJsEntry(outDir), buf,
                    java.io.InputStream.nullInputStream(), new java.io.ByteArrayOutputStream());
            String output = buf.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
            assertEquals(0, ec, "JS exit code, output: " + output);
            if (expected != null) {
                assertEquals(expected, output, "JS output");
            }
            return output;
        }
    }

    private static Path findJsEntry(Path dir) throws java.io.IOException {
        try (var s = Files.walk(dir)) {
            var opt = s.filter(p -> p.getFileName().toString().equals("Default.mjs")).findFirst();
            if (opt.isPresent()) return opt.get();
        }
        try (var s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(".mjs"))
                    .findFirst().orElseThrow(() -> new java.io.IOException("no .mjs in " + dir));
        }
    }
}
