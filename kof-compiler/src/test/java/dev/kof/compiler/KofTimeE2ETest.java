package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;


/**
 * End-to-end tests for {@code kof.time} — sleep, now e scheduler.
 */
class KofTimeE2ETest {

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

    private String runJvm(Path tempDir, String kofSource, String expected) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, kofSource);
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        try {
            String javaCmd = TestJdk.javaBin();
            ProcessBuilder pb = new ProcessBuilder(javaCmd, "-Dfile.encoding=UTF-8",
                    "-Dstdout.encoding=UTF-8", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running JVM class", e);
        }
    }

    private String runNative(Path tempDir, String kofSource, String expected) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, kofSource);
        Path outDir = tempDir.resolve("out-native");
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Native compile should succeed: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        try {
            ProcessBuilder pb = new ProcessBuilder(bin.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Native exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected native output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running native binary", e);
        }
    }

    @Test
    void sleepPausesForMs(@TempDir Path tempDir) throws IOException {
        runJvm(tempDir, """
                main() {
                    var t0 = time.now()
                    time.sleep(250)
                    var t1 = time.now()
                    println(t1 - t0 >= 200)
                }
                """, "true");
    }

    // #108 (13/09): `sleep(ms)` sem receiver resolve como `time.sleep(ms)`
    // (opção 1 — espelha o `now()` sem receiver). Paridade JVM+Native+JS.
    @Test
    void sleepUnqualifiedResolvesAsTimeSleep(@TempDir Path tempDir) throws IOException {
        runJvm(tempDir, """
                main() {
                    var t0 = now()
                    sleep(250)
                    var t1 = now()
                    println(t1 - t0 >= 200)
                }
                """, "true");
        runNative(tempDir, """
                main() {
                    var t0 = now()
                    sleep(250)
                    var t1 = now()
                    println(t1 - t0 >= 200)
                }
                """, "true");
    }

    @Test
    void intervalRunsPeriodicallyUntilCancelled(@TempDir Path tempDir) throws IOException {
        String src = """
                main() {
                    var ticks = 0
                    var job = time.interval(100, () -> {
                        ticks = ticks + 1
                    })
                    time.sleep(450)
                    time.cancel(job)
                    var after = ticks
                    time.sleep(300)
                    println(ticks == after)
                    println(ticks >= 2)
                }
                """;
        runJvm(tempDir, src, "true\ntrue");
        // TIME001 (01/09): Native reusa o scheduler.every/cancel (SCHED001) —
        // mutação por referência da captura (ticks) é validada aqui.
        runNative(tempDir, src, "true\ntrue");
    }

    // §253 face A (16/09): `var id = time.interval(…, () -> cancel(id))` — a
    // lambda do PRÓPRIO inicializador lê o handle que está sendo declarado.
    // Antes dava SEM011 nos 3 alvos (escopo define id só DEPOIS de tipar o
    // init). Face A abre JVM+JS (pre-define + box com store antes do init);
    // NATIVE fica em SEM092 (face B — leitura do handle nativo SIGSEGVa).
    @Test
    void selfReferencingIntervalHandleCancelsItself(@TempDir Path tempDir) throws IOException {
        String src = """
                main() {
                    var ticks = 0
                    var id = time.interval(100, () -> {
                        ticks = ticks + 1
                        if (ticks >= 3) {
                            time.cancel(id)
                        }
                    })
                    time.sleep(450)
                    println(ticks == 3)
                    var after = ticks
                    time.sleep(300)
                    println(ticks == after)
                }
                """;
        runJvm(tempDir, src, "true\ntrue");
        runJs(tempDir, src, "true\ntrue");
        // Script (interpretador): roda o mesmo KofRuntime do host JVM — a
        // leitura self-ref do handle no job é a mesma captura do frame.
        Path sfile = tempDir.resolve("SelfScript-" + System.nanoTime() + ".kf");
        Files.writeString(sfile, src);
        dev.kof.compiler.KofInterpreter.Result ir = new CompilerDriver()
                .interpret(java.util.List.of(sfile), sfile.getParent(), new String[0]);
        assertEquals(0, ir.exitCode(), "Script exit/stderr: " + ir.stdout() + " " + ir.stderr());
        assertEquals("true\ntrue", ir.stdout().trim(), "Script output");
    }

    @Test
    void selfReferencingIntervalHandleIsNativeGateSem092(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    var id = time.interval(100, () -> {
                        time.cancel(id)
                    })
                    time.sleep(250)
                }
                """);
        for (Target t : new Target[]{Target.NATIVE, Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            CompilationResult r = driver.compile(source, tempDir.resolve("sem092-" + t), t);
            assertFalse(r.success(), t + " self-ref handle deve falhar (face B pendente)");
            String diags = r.diagnostics().getDiagnostics().toString();
            assertTrue(diags.contains("SEM092"), t + " deve reportar SEM092: " + diags);
        }
    }

    @Test
    void capturedBoxAssignAcrossCallRunsNativeX86(@TempDir Path tempDir) throws IOException {
        // §253 face B — o workaround pré-declarado (var id = ""; id =
        // time.interval(job que le id)) SIGSEGVava no x86: o receiver-box
        // ficava na pilha de maquina durante os calls do RHS e o SSE da libc
        // (movaps no pthread_create do scheduler) faultava com rsp%16==8.
        // Forma exata do reproducao da matriz (t3): job le a String capturada
        // (got = got + id.length) e o handle e escrito na mesma atribuicao.
        // RED antes do fix: exit 139 no NATIVE.
        String src = """
                main() {
                    var got = 0
                    var id = ""
                    id = time.interval(10, () -> { got = got + id.length })
                    time.sleep(60)
                    println(got > 0)
                }
                """;
        runJvm(tempDir, src, "true");
        runJs(tempDir, src, "true");
        runNative(tempDir, src, "true");
        Path sfile = tempDir.resolve("BoxAssign-" + System.nanoTime() + ".kf");
        Files.writeString(sfile, src);
        dev.kof.compiler.KofInterpreter.Result ir = new CompilerDriver()
                .interpret(java.util.List.of(sfile), sfile.getParent(), new String[0]);
        assertEquals(0, ir.exitCode(), "Script exit/stderr: " + ir.stdout() + " " + ir.stderr());
        assertEquals("true", ir.stdout().trim(), "Script output");
    }

    @Test
    void instanceFieldAssignAcrossCallRunsNativeX86(@TempDir Path tempDir) throws IOException {
        // §253 face B — segunda face do MESMO mecanismo: `h.s = time.interval(…)`
        // empilhava o receiver `h` na pilha de maquina durante os calls do RHS
        // (mesmo desalinhamento rsp%16==8 do box capturado; SIGSEGV no
        // pthread_create do scheduler). RED antes do fix: exit 139 no NATIVE.
        String src = """
                class H {
                    String s
                    public constructor() {
                        this.s = ""
                    }
                }
                main() {
                    var ticked = false
                    var h = H()
                    h.s = time.interval(50, () -> { ticked = true })
                    time.sleep(200)
                    println(ticked)
                }
                """;
        runJvm(tempDir, src, "true");
        runNative(tempDir, src, "true");
        Path sfile = tempDir.resolve("FieldAssign-" + System.nanoTime() + ".kf");
        Files.writeString(sfile, src);
        dev.kof.compiler.KofInterpreter.Result ir = new CompilerDriver()
                .interpret(java.util.List.of(sfile), sfile.getParent(), new String[0]);
        assertEquals(0, ir.exitCode(), "Script exit/stderr: " + ir.stdout() + " " + ir.stderr());
        assertEquals("true", ir.stdout().trim());
    }

    @Test
    void capturedBoxAssignAcrossCallRunsCrossArchQemu(@TempDir Path tempDir) throws IOException {
        // riscv/aarch usam o MESMO lowering de box (receiver na pilha de
        // maquina cruzando o call do RHS) — a regressao de ordem vale nos 3
        // cross: executa via qemu. O SIGSEGV face B era o primeiro
        // pthread_create do scheduler (exit 139) — o que este test prova e
        // que a forma workaround compila e o job roda nos dois archs.
        // aarch: (histórico) o scheduler de intervalo ainda armado não
        // terminava sob qemu-aarch (gap §283 — thread sem join na saída) e o
        // cancel explícito era a forma que terminava; §283 está MORTO desde a
        // costura B-0 (kof_plat_exit_group = exit_group 94 mata as threads do
        // scheduler) — a forma sem cancel é pinada por
        // intervalArmedAtExitExitsCleanlyCrossArch, abaixo. O cancel fica
        // aqui (Kof legal, a forma continua valendo como prova do job).
        // riscv: join explicito no runtime — termina sem cancel.
        if (has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64")) {
            runQemu(tempDir, Target.NATIVE_RISCV64, "qemu-riscv64", """
                    main() {
                        var got = 0
                        var id = ""
                        id = time.interval(5, () -> { got = got + id.length })
                        time.sleep(25)
                        assert(got > 0)
                    }
                    """);
        } else {
            Assumptions.assumeTrue(false, "toolchain riscv64 ausente");
        }
        if (has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64")) {
            runQemu(tempDir, Target.NATIVE_AARCH64, "qemu-aarch64", """
                    main() {
                        var got = 0
                        var id = ""
                        id = time.interval(5, () -> { got = got + id.length })
                        time.sleep(25)
                        assert(got > 0)
                        time.cancel(id)
                    }
                    """);
        } else {
            Assumptions.assumeTrue(false, "toolchain aarch64 ausente");
        }
    }

    /** §283 (fechado 23/09): intervalo AINDA ARMADO no fim do main termina
     *  rc=0 nas duas archs cross — a costura B-0 (kof_plat_exit_group =
     *  exit_group 94) mata as threads do scheduler. Bounded de propósito
     *  (§418): uma regressão vira FALHA com timeout, não uma suíte pendurada. */
    @Test
    void intervalArmedAtExitExitsCleanlyCrossArch(@TempDir Path tempDir) throws IOException {
        String src = """
                main() {
                    var id = ""
                    id = time.interval(5, () -> { println("tick") })
                    time.sleep(25)
                    println("fim")
                }
                """;
        for (String arch : new String[]{"riscv64", "aarch64"}) {
            Assumptions.assumeTrue(has(arch + "-linux-gnu-as", arch + "-linux-gnu-ld", "qemu-" + arch),
                    "toolchain " + arch + " ausente");
            Path file = tempDir.resolve("Main-" + arch + "-" + System.nanoTime() + ".kf");
            Files.writeString(file, src);
            Path outDir = tempDir.resolve("out-" + arch + "-" + System.nanoTime());
            CompilationResult result = new CompilerDriver().compile(file, outDir, arch.equals("aarch64")
                    ? Target.NATIVE_AARCH64 : Target.NATIVE_RISCV64);
            assertTrue(result.success(), arch + " compile: " + result.diagnostics().getDiagnostics());
            Path bin = outDir.resolve("Default/Main");
            Process p = NativeRiscv64E2ETest.qemu(arch, bin).redirectErrorStream(true).start();
            String output;
            boolean done;
            try {
                done = p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
                output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted", e);
            } finally {
                if (!p.isAlive()) { /* já saiu */ } else { p.destroyForcibly(); }
            }
            assertTrue(done, "§283 regressão: " + arch + " pendurado com intervalo armado "
                    + "(o processo não saiu em 30s)");
            assertEquals(0, p.exitValue(), arch + " exit, out: " + output);
            assertTrue(output.contains("fim"), arch + " saída: " + output);
        }
    }

    @Test
    void nowReturnsEpochMillis(@TempDir Path tempDir) throws IOException {
        runJvm(tempDir, """
                main() {
                    println(time.now() > 1700000000000)
                }
                """, "true");
    }

    // ── STDLIB S7-wedge — calendário civil (isLeapYear/daysInMonth) ───────
    @Test
    void calendarJvm(@TempDir Path tempDir) throws IOException {
        runJvm(tempDir, """
                main() {
                    println(time.isLeapYear(2000))
                    println(time.isLeapYear(1900))
                    println(time.isLeapYear(2024))
                    println(time.isLeapYear(2023))
                    println(time.isLeapYear(-4))
                    println(time.daysInMonth(2024, 2))
                    println(time.daysInMonth(2023, 2))
                    println(time.daysInMonth(2024, 4))
                    println(time.daysInMonth(2024, 13))
                    println(time.daysInMonth(0, 5))
                    println(time.dayOfWeek(1970, 1, 1))
                    println(time.dayOfWeek(2026, 9, 9))
                    println(time.dayOfWeek(1, 1, 1))
                    println(time.dayOfWeek(9999, 12, 31))
                    println(time.dayOfWeek(2024, 2, 30))
                    println(time.daysBetween(2024, 1, 1, 2024, 3, 1))
                    println(time.daysBetween(2024, 3, 1, 2024, 1, 1))
                    println(time.daysBetween(2023, 2, 29, 2023, 3, 1))
                    println(time.isWeekend(2026, 9, 12))
                    println(time.isWeekend(2026, 9, 13))
                    println(time.isWeekend(2026, 9, 9))
                    println(time.isWeekend(2026, 2, 30))
                }
                """, "true\nfalse\ntrue\nfalse\nfalse\n29\n28\n30\n0\n0\n4\n3\n1\n5\n0\n60\n-60\n0\ntrue\ntrue\nfalse\nfalse");
    }

    @Test
    void calendarJs(@TempDir Path tempDir) throws IOException {
        runJs(tempDir, """
                main() {
                    println(time.isLeapYear(2000))
                    println(time.isLeapYear(1900))
                    println(time.daysInMonth(2024, 2))
                    println(time.daysInMonth(2023, 2))
                    println(time.daysInMonth(2024, 13))
                    println(time.daysInMonth(0, 5))
                    println(time.isWeekend(2026, 9, 12))
                    println(time.isWeekend(2026, 9, 9))
                }
                """, "true\nfalse\n29\n28\n0\n0\ntrue\nfalse");
    }

    @Test
    void calendarNative(@TempDir Path tempDir) throws IOException {
        runNative(tempDir, """
                main() {
                    println(time.isLeapYear(2000))
                    println(time.isLeapYear(1900))
                    println(time.isLeapYear(2024))
                    println(time.isLeapYear(-4))
                    println(time.daysInMonth(2024, 2))
                    println(time.daysInMonth(2023, 2))
                    println(time.daysInMonth(2024, 4))
                    println(time.daysInMonth(2024, 12))
                    println(time.daysInMonth(2024, 13))
                    println(time.isWeekend(2024, 2, 25))
                    println(time.isWeekend(2024, 2, 29))
                }
                 """, "true\nfalse\ntrue\nfalse\n29\n28\n30\n31\n0\ntrue\nfalse");
     }

     @Test
     void calendarAgeJvm(@TempDir Path tempDir) throws IOException {
         runJvm(tempDir, ageProgram(), ageGolden());
     }

     @Test
     void calendarAgeJs(@TempDir Path tempDir) throws IOException {
         runJs(tempDir, ageProgram(), ageGolden());
     }

     @Test
     void calendarAgeNative(@TempDir Path tempDir) throws IOException {
         runNative(tempDir, ageProgram(), ageGolden());
     }

     private static String ageProgram() {
         return """
                 main() {
                     println(time.age(2000, 5, 15, 2025, 5, 14))
                     println(time.age(2000, 5, 15, 2025, 5, 15))
                     println(time.age(2000, 5, 15, 2025, 5, 16))
                     println(time.age(2000, 5, 15, 2026, 1, 1))
                     println(time.age(2000, 2, 29, 2001, 2, 28))
                     println(time.age(2000, 2, 29, 2004, 2, 29))
                     println(time.age(2000, 2, 29, 2000, 2, 29))
                     println(time.age(2025, 12, 31, 2026, 1, 1))
                     println(time.age(2020, 1, 1, 2015, 1, 1))
                     println(time.age(2000, 13, 1, 2025, 1, 1))
                     println(time.age(2000, 1, 1, 2025, 1, 32))
                     println(time.age(0, 1, 1, 2025, 1, 1))
                     println(time.age(1925, 1, 1, 2025, 1, 1))
                 }
                 """;
     }

     private static String ageGolden() {
         // anos completos: não-reached / exato / after / mês-anterior / Feb29->nonleap
         // / Feb29 leap / same-date / virada / ref<birth(neg) / invalidas(0) / 100anos
         return "24\n25\n25\n25\n0\n4\n0\n0\n-5\n0\n0\n0\n100";
     }

     @Test
     void calendarCrossArchRuntimes(@TempDir Path tempDir) throws IOException {
        // PRIMEIRO teste de calendário que EXECUTA riscv/aarch (assert-only +
        // qemu; bug 59 é só no link do println).
        String src = """
                main() {
                    assert(time.isLeapYear(2000))
                    assert(!time.isLeapYear(1900))
                    assert(time.isLeapYear(2024))
                    assert(!time.isLeapYear(2023))
                    assert(!time.isLeapYear(-4))
                    assert(time.daysInMonth(2024, 2) == 29)
                    assert(time.daysInMonth(2023, 2) == 28)
                    assert(time.daysInMonth(2024, 4) == 30)
                    assert(time.daysInMonth(2024, 13) == 0)
                    assert(time.daysInMonth(0, 5) == 0)
                    assert(time.daysInMonth(2024, 12) == 31)
                    assert(time.dayOfWeek(1970, 1, 1) == 4)
                    assert(time.dayOfWeek(2026, 9, 9) == 3)
                    assert(time.dayOfWeek(1, 1, 1) == 1)
                    assert(time.dayOfWeek(9999, 12, 31) == 5)
                    assert(time.dayOfWeek(2024, 2, 30) == 0)
                    assert(time.dayOfWeek(10000, 1, 1) == 0)
                    assert(time.daysBetween(2024, 1, 1, 2024, 3, 1) == 60)
                    assert(time.daysBetween(2024, 3, 1, 2024, 1, 1) == -60)
                    assert(time.daysBetween(2020, 2, 28, 2020, 3, 1) == 2)
                    assert(time.daysBetween(2023, 2, 29, 2023, 3, 1) == 0)
                    assert(time.daysBetween(2024, 1, 1, 10000, 1, 1) == 0)
                    assert(time.isWeekend(2026, 9, 12))
                    assert(time.isWeekend(2026, 9, 13))
                    assert(!time.isWeekend(2026, 9, 9))
                    assert(!time.isWeekend(2026, 9, 7))
                    assert(!time.isWeekend(2026, 2, 30))
                     assert(time.isWeekend(2024, 2, 25))
                     assert(!time.isWeekend(2024, 2, 29))
                     assert(time.age(2000, 5, 15, 2025, 5, 14) == 24)
                     assert(time.age(2000, 5, 15, 2025, 5, 15) == 25)
                     assert(time.age(2000, 5, 15, 2025, 5, 16) == 25)
                     assert(time.age(2000, 5, 15, 2026, 1, 1) == 25)
                     assert(time.age(2000, 2, 29, 2001, 2, 28) == 0)
                     assert(time.age(2000, 2, 29, 2004, 2, 29) == 4)
                     assert(time.age(2000, 2, 29, 2000, 2, 29) == 0)
                     assert(time.age(2025, 12, 31, 2026, 1, 1) == 0)
                     assert(time.age(2020, 1, 1, 2015, 1, 1) == -5)
                     assert(time.age(2000, 13, 1, 2025, 1, 1) == 0)
                     assert(time.age(2000, 1, 1, 2025, 1, 32) == 0)
                     assert(time.age(0, 1, 1, 2025, 1, 1) == 0)
                     assert(time.age(1925, 1, 1, 2025, 1, 1) == 100)
                 }
                """;
        if (has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64")) {
            runQemu(tempDir, Target.NATIVE_RISCV64, "qemu-riscv64", src);
        } else {
            Assumptions.assumeTrue(false, "toolchain riscv64 ausente");
        }
        if (has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64")) {
            runQemu(tempDir, Target.NATIVE_AARCH64, "qemu-aarch64", src);
        } else {
            Assumptions.assumeTrue(false, "toolchain aarch64 ausente");
        }
    }

    private void runQemu(Path tempDir, Target target, String qemu, String kofSource)
            throws IOException {
        Path file = tempDir.resolve("Main-" + target + "-" + System.nanoTime() + ".kf");
        Files.writeString(file, kofSource);
        Path outDir = tempDir.resolve("qemu-" + target + "-" + System.nanoTime());
        CompilationResult result = new CompilerDriver().compile(file, outDir, target);
        assertTrue(result.success(), target + " compile: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        try {
            Process p = NativeRiscv64E2ETest.qemu(qemu.substring(5), bin).redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            assertEquals(0, p.waitFor(), target + " qemu exit, out: " + output);
        } catch (InterruptedException e) {
            throw new IOException("interrupted", e);
        }
    }

    private String runJs(Path tempDir, String kofSource, String expected) throws IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, kofSource);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JS);
        assertTrue(result.success(), "JS compile: " + result.diagnostics().getDiagnostics());
        Path entry;
        try (var s = Files.walk(outDir)) {
            entry = s.filter(p -> p.getFileName().toString().equals("Default.mjs")).findFirst()
                    .orElseThrow(() -> new IOException("no .mjs"));
        }
        try (java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream()) {
            int ec = dev.kof.runtime.KofJsRunner.run(entry, buf,
                    java.io.InputStream.nullInputStream(), new java.io.ByteArrayOutputStream());
            String output = buf.toString(StandardCharsets.UTF_8).trim();
            assertEquals(0, ec, "JS exit, out: " + output);
            assertEquals(expected, output, "JS output");
            return output;
        }
    }

    @Test
    void nativeAndJsSupportNowAndSleep(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    var t0 = time.now()
                    time.sleep(10)
                    var t1 = time.now()
                    println(t1 >= t0)
                }
                """);
        CompilationResult nativeResult = driver.compile(source, tempDir.resolve("native"), Target.NATIVE);
        assertTrue(nativeResult.success(), "Native should support time.now/sleep: " + nativeResult.diagnostics().getDiagnostics());
        Path nativeBin = tempDir.resolve("native").resolve("Default/Main");
        Process pn = new ProcessBuilder(nativeBin.toString()).redirectErrorStream(true).start();
        try {
            String out = new String(pn.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            int ec = pn.waitFor();
            assertEquals(0, ec, "Native exit code, output: " + out);
            assertEquals("true", out, "Native output");
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
        CompilationResult jsResult = driver.compile(source, tempDir.resolve("js"), Target.JS);
        assertTrue(jsResult.success(), "JS should support time.now/sleep: " + jsResult.diagnostics().getDiagnostics());
        try (java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream()) {
            Path jsEntry = findJsEntry(tempDir.resolve("js"));
            int ec = dev.kof.runtime.KofJsRunner.run(jsEntry, buf,
                    java.io.InputStream.nullInputStream(), new java.io.ByteArrayOutputStream());
            String out = buf.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
            assertEquals(0, ec, "JS exit code, output: " + out);
            assertEquals("true", out, "JS output");
        }
    }

    @Test
    void jsIntervalRunsPeriodicallyUntilCancelled(@TempDir Path tempDir) throws IOException {
        // TIME001 fechado (02/09): JS roda time.interval/cancel por fila
        // cooperativa bombeada dentro de time.sleep (GraalJS não tem
        // setInterval/event loop; browser/Node usam setInterval nativo).
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    var ticks = 0
                    var job = time.interval(100, () -> {
                        ticks = ticks + 1
                    })
                    time.sleep(450)
                    time.cancel(job)
                    var after = ticks
                    time.sleep(300)
                    println(ticks == after)
                    println(ticks >= 2)
                }
                """);
        CompilationResult jsResult = driver.compile(source, tempDir.resolve("js2"), Target.JS);
        assertTrue(jsResult.success(), "JS should now compile time.interval: "
                + jsResult.diagnostics().getDiagnostics());
        try (java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream()) {
            Path jsEntry = findJsEntry(tempDir.resolve("js2"));
            int ec = dev.kof.runtime.KofJsRunner.run(jsEntry, buf,
                    java.io.InputStream.nullInputStream(), new java.io.ByteArrayOutputStream());
            String out = buf.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
            assertEquals(0, ec, "JS exit code, output: " + out);
            assertEquals("true\ntrue", out, "JS output");
        }
    }

    private static Path findJsEntry(Path dir) throws IOException {
        try (var s = Files.walk(dir)) {
            var opt = s.filter(p -> p.getFileName().toString().equals("Default.mjs")).findFirst();
            if (opt.isPresent()) return opt.get();
        }
        try (var s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(".mjs"))
                    .findFirst().orElseThrow(() -> new IOException("no .mjs in " + dir));
        }
    }

    @Test
    void crossNativeTimeIntervalRuns(@TempDir Path tempDir) throws Exception {
        // TIME001 FEITO no cross (05/09): time.interval/cancel são alias do
        // scheduler (thread por job via clone+nanosleep). O callback dispara
        // e o cancel silencia; END por último.
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var id = time.interval(100, () -> println("tick"))
                time.sleep(250)
                time.cancel(id)
                time.sleep(250)
                println("END")
            }
            """);
        String[] q = {"qemu-riscv64", "qemu-aarch64"};
        Target[] ts = {Target.NATIVE_RISCV64, Target.NATIVE_AARCH64};
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64")
                        && has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain riscv64/aarch64 + qemu ausente — pulando (NATIVE002)");
        for (int i = 0; i < 2; i++) {
            CompilationResult r = new CompilerDriver().compile(source, tempDir.resolve("cross-" + i), ts[i]);
            assertTrue(r.success(), ts[i] + " deve compilar: " + r.diagnostics().getDiagnostics());
            Path bin = tempDir.resolve("cross-" + i).resolve("Default/Main");
            var qpb = NativeRiscv64E2ETest.qemu(q[i].substring(5), bin);
            qpb.command().add(0, "timeout");
            qpb.command().add(1, "10");
            var p = qpb.redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes()).trim();
            assertEquals(0, p.waitFor(), ts[i] + " exit, output: " + output);
            assertTrue(output.endsWith("END"), ts[i] + ": END por último: " + output);
            int ticks = 0;
            for (String l : output.split("\n")) if (l.equals("tick")) ticks++;
            assertTrue(ticks >= 1 && ticks <= 8, ts[i] + ": esperava 1..8 ticks, veio " + ticks + ": " + output);
        }
        // now/sleep continuam ok no cross (sem gate)
        Path ok = tempDir.resolve("Ok.kf");
        Files.writeString(ok, """
            main() {
                var t = time.now()
                println(t > 1000000000000)
            }
            """);
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            CompilationResult r = new CompilerDriver().compile(ok, tempDir.resolve("ok-" + t), t);
            assertTrue(r.success(), t + " time.now should compile: " + r.diagnostics().getDiagnostics());
        }
    }

    /**
     * STDLIB S7a — addDays/diffDays em data ISO (String).
     * Shape travado no JVM (java.time); Native/JS = gap honesto TIME002
     * (erro claro no compile, nunca fallback silencioso — R6).
     */
    @Test
    void timeAddDaysDiffDaysJvmShapeAndCrossArch(@TempDir Path tempDir) throws IOException {
        String src = """
            main() {
                println(time.addDays("2024-02-28", 1))
                println(time.addDays("2023-02-28", 1))
                println(time.addDays("2024-12-31", 1))
                println(time.addDays("2024-01-01", -1))
                println(time.addDays("2024-02-30", 1))
                println(time.addDays("garbage", 1))
                println(time.diffDays("2024-01-01", "2024-03-01"))
                println(time.diffDays("2024-03-01", "2024-01-01"))
                println(time.diffDays("x", "y"))
            }
            """;
        assertEquals("2024-02-29\n2023-03-01\n2025-01-01\n2023-12-31\n\n\n60\n-60\n0",
                runJvm(tempDir, src,
                        "2024-02-29\n2023-03-01\n2025-01-01\n2023-12-31\n\n\n60\n-60\n0"));
        Path gateSrc = tempDir.resolve("Gate.kf");
        Files.writeString(gateSrc, src);
        // S7b: JS FECHADO; S7c: x86 FECHADO (matriz stdtime2 roda local).
        // S7d (TIME002 fechado 11/09): riscv64/aarch64 — B33 (.Lu8_parse2/
        // .Lu8_civil/.Lu8_put*) port 1:1 do RuntimeTimeIso x86 reusando
        // kdv_valid/kdv_epoch (B14). Golden byte-idêntico sob qemu.
        String expected = "2024-02-29\n2023-03-01\n2025-01-01\n2023-12-31\n\n\n60\n-60\n0";
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String qemu = t == Target.NATIVE_RISCV64 ? "qemu-riscv64" : "qemu-aarch64";
            String[] tools = t == Target.NATIVE_RISCV64
                    ? new String[]{"riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"}
                    : new String[]{"aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"};
            assumeToolchain(tools);
            Path file = tempDir.resolve("Ad-" + t + "-" + System.nanoTime() + ".kf");
            Files.writeString(file, src);
            Path outDir = tempDir.resolve("ad-" + t + "-" + System.nanoTime());
            CompilationResult r = new CompilerDriver().compile(file, outDir, t);
            assertTrue(r.success(), t + " compile: " + r.diagnostics().getDiagnostics());
            Process p = NativeRiscv64E2ETest.qemu(qemu.substring(5), outDir.resolve("Default/Main"))
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec;
            try {
                ec = p.waitFor();
            } catch (InterruptedException e) {
                throw new IOException("interrupted", e);
            }
            assertEquals(0, ec, t + " qemu exit, out: " + out);
            assertEquals(expected, out, t + " golden addDays/diffDays");
        }
    }

    @Test
    void timeAddDaysDiffDaysCompilesOnAllTargets(@TempDir Path tempDir) throws IOException {
        // Gate SEMPRE-verde (mesmo sem qemu): o backend cross EMITE o asm; a
        // EXECUCAO e provada sob qemu no teste acima (S7c-1/TIME002 fechado).
        String src = """
            main() {
                println(time.addDays("2024-02-28", 1))
                println(time.diffDays("2024-01-01", "2024-03-01"))
            }
            """;
        Path gateSrc = tempDir.resolve("GateTime.kf");
        Files.writeString(gateSrc, src);
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            CompilationResult r = new CompilerDriver().compile(gateSrc, tempDir.resolve("gate-" + t), t);
            assertTrue(r.success(), t + " deve compilar addDays/diffDays (TIME002 fechado): "
                    + r.diagnostics().getDiagnostics());
        }
    }

    // STDLIB S7a-ext (front #1 da stdlib, D-DEV-PRIORITY): time.addMonths(iso, n)
    // -> String. clamp de fim de mês (dia=min(d, daysInMonth)), rolo de ano/mês,
    // bissexto 29/fev, n=0, n negativo, out-of-range e inválida => "" (a MESMA
    // política do addDays). Algoritmo inteiro puro (t=y*12+(m-1)+n; y1=t/12;
    // m1=t%12+1; d1=min(d,dim)), pré-guarda t em [12,119999] => byte-idêntico nos
    // 5 alvos. Oráculo: java.time (3M+ casos fuzz 0 mismatch).
    @Test
    void timeAddMonthsJvm(@TempDir Path tempDir) throws IOException {
        runJvm(tempDir, addMonthsSrc(), addMonthsGolden());
    }

    @Test
    void timeAddMonthsJs(@TempDir Path tempDir) throws IOException {
        runJs(tempDir, addMonthsSrc(), addMonthsGolden());
    }

    @Test
    void timeAddMonthsNative(@TempDir Path tempDir) throws IOException {
        runNative(tempDir, addMonthsSrc(), addMonthsGolden());
    }

    @Test
    void timeAddMonthsCrossArch(@TempDir Path tempDir) throws IOException {
        String src = addMonthsSrc();
        String expected = addMonthsGolden();
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String qemu = t == Target.NATIVE_RISCV64 ? "qemu-riscv64" : "qemu-aarch64";
            String[] tools = t == Target.NATIVE_RISCV64
                    ? new String[]{"riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"}
                    : new String[]{"aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"};
            assumeToolchain(tools);
            Path file = tempDir.resolve("Am-" + t + "-" + System.nanoTime() + ".kf");
            Files.writeString(file, src);
            Path outDir = tempDir.resolve("am-" + t + "-" + System.nanoTime());
            CompilationResult r = new CompilerDriver().compile(file, outDir, t);
            assertTrue(r.success(), t + " compile: " + r.diagnostics().getDiagnostics());
            Process p = NativeRiscv64E2ETest.qemu(qemu.substring(5), outDir.resolve("Default/Main"))
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec;
            try {
                ec = p.waitFor();
            } catch (InterruptedException e) {
                throw new IOException("interrupted", e);
            }
            assertEquals(0, ec, t + " qemu exit, out: " + out);
            assertEquals(expected, out, t + " golden addMonths");
        }
    }

    @Test
    void timeAddMonthsCompilesOnAllTargets(@TempDir Path tempDir) throws IOException {
        String src = """
            main() {
                println(time.addMonths("2024-01-31", 1))
            }
            """;
        Path gateSrc = tempDir.resolve("GateAddMonths.kf");
        Files.writeString(gateSrc, src);
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            CompilationResult r = new CompilerDriver().compile(gateSrc, tempDir.resolve("gate-am-" + t), t);
            assertTrue(r.success(), t + " deve compilar addMonths: " + r.diagnostics().getDiagnostics());
        }
    }

    private static String addMonthsSrc() {
        return """
            main() {
                println(time.addMonths("2024-02-28", 1))
                println(time.addMonths("2024-01-31", 1))
                println(time.addMonths("2023-01-31", 1))
                println(time.addMonths("2024-02-29", 12))
                println(time.addMonths("2024-12-31", 1))
                println(time.addMonths("2024-03-31", -1))
                println(time.addMonths("2024-01-15", 13))
                println(time.addMonths("2024-05-31", 1))
                println(time.addMonths("2024-06-15", 0))
                println(time.addMonths("2024-02-29", 0))
                println(time.addMonths("2023-01-01", -1))
                println(time.addMonths("2024-01-31", 999999))
                println(time.addMonths("2024-01-31", -999999))
                println(time.addMonths("2024-02-30", 1))
                println(time.addMonths("garbage", 1))
                println(time.addMonths("2023-02-28", 1))
            }
            """;
    }

    private static String addMonthsGolden() {
        return "2024-03-28\n2024-02-29\n2023-02-28\n2025-02-28\n2025-01-31\n2024-02-29"
                + "\n2025-02-15\n2024-06-30\n2024-06-15\n2024-02-29\n2022-12-01\n\n\n\n\n2023-03-28";
    }

    // STDLIB S7a-ext2 (front #1 da stdlib, D-DEV-PRIORITY): time.addYears(iso, n)
    // -> String. Anos em aritmética inteira simples (y1 = y + n; clamp de fim de
    // mês => dia=min(d, daysInMonth(y1, m))); inválida/out-of-range => "". Oráculo:
    // java.time (mesma política).
    @Test
    void timeAddYearsJvm(@TempDir Path tempDir) throws IOException {
        runJvm(tempDir, addYearsSrc(), addYearsGolden());
    }

    @Test
    void timeAddYearsJs(@TempDir Path tempDir) throws IOException {
        runJs(tempDir, addYearsSrc(), addYearsGolden());
    }

    @Test
    void timeAddYearsNative(@TempDir Path tempDir) throws IOException {
        runNative(tempDir, addYearsSrc(), addYearsGolden());
    }

    @Test
    void timeAddYearsCrossArch(@TempDir Path tempDir) throws IOException {
        String src = addYearsSrc();
        String expected = addYearsGolden();
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String qemu = t == Target.NATIVE_RISCV64 ? "qemu-riscv64" : "qemu-aarch64";
            String[] tools = t == Target.NATIVE_RISCV64
                    ? new String[]{"riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"}
                    : new String[]{"aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"};
            assumeToolchain(tools);
            Path file = tempDir.resolve("Ay-" + t + "-" + System.nanoTime() + ".kf");
            Files.writeString(file, src);
            Path outDir = tempDir.resolve("ay-" + t + "-" + System.nanoTime());
            CompilationResult r = new CompilerDriver().compile(file, outDir, t);
            assertTrue(r.success(), t + " compile: " + r.diagnostics().getDiagnostics());
            Process p = NativeRiscv64E2ETest.qemu(qemu.substring(5), outDir.resolve("Default/Main"))
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec;
            try {
                ec = p.waitFor();
            } catch (InterruptedException e) {
                throw new IOException("interrupted", e);
            }
            assertEquals(0, ec, t + " qemu exit, out: " + out);
            assertEquals(expected, out, t + " golden addYears");
        }
    }

    @Test
    void timeAddYearsCompilesOnAllTargets(@TempDir Path tempDir) throws IOException {
        String src = """
            main() {
                println(time.addYears("2024-02-29", 1))
            }
            """;
        Path gateSrc = tempDir.resolve("GateAddYears.kf");
        Files.writeString(gateSrc, src);
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            CompilationResult r = new CompilerDriver().compile(gateSrc, tempDir.resolve("gate-ay-" + t), t);
            assertTrue(r.success(), t + " deve compilar addYears: " + r.diagnostics().getDiagnostics());
        }
    }

    private static String addYearsSrc() {
        return """
            main() {
                println(time.addYears("2024-02-29", 1))
                println(time.addYears("2024-02-29", 4))
                println(time.addYears("2024-12-31", 1))
                println(time.addYears("2023-06-15", -1))
                println(time.addYears("2024-02-29", 0))
                println(time.addYears("2100-02-29", 0))
                println(time.addYears("0001-01-01", -1))
                println(time.addYears("9999-12-31", 1))
                println(time.addYears("0001-01-01", 9998))
                println(time.addYears("2024-02-29", 100))
                println(time.addYears("2024-02-29", -4))
                println(time.addYears("2024-02-30", 1))
                println(time.addYears("garbage", 1))
                println(time.addYears("2024-05-31", 8))
            }
            """;
    }

    private static String addYearsGolden() {
        return "2025-02-28\n2028-02-29\n2025-12-31\n2022-06-15\n2024-02-29\n\n\n\n"
                + "9999-01-01\n2124-02-29\n2020-02-29\n\n\n2032-05-31";
    }

    // STDLIB S7a-ext3 (front #1 da stdlib, D-DEV-PRIORITY): time.startOf(iso,unit)
    // / time.endOf(iso,unit) -> String, unit = day|week|month|year. Semana =
    // segunda..domingo (dayOfWeek ISO 1=seg..7=dom). Composta dos primitivos já
    // com paridade provada (dayOfWeek/addDays/daysInMonth) => byte-idêntica por
    // construção. Data inválida / unit desconhecida / resultado fora de
    // 1..9999 => "". Oráculo: java.time (validado caso a caso).
    @Test
    void timeStartEndOfJvm(@TempDir Path tempDir) throws IOException {
        runJvm(tempDir, startEndOfSrc(), startEndOfGolden());
    }

    @Test
    void timeStartEndOfJs(@TempDir Path tempDir) throws IOException {
        runJs(tempDir, startEndOfSrc(), startEndOfGolden());
    }

    @Test
    void timeStartEndOfNative(@TempDir Path tempDir) throws IOException {
        runNative(tempDir, startEndOfSrc(), startEndOfGolden());
    }

    @Test
    void timeStartEndOfCrossArch(@TempDir Path tempDir) throws IOException {
        String src = startEndOfSrc();
        String expected = startEndOfGolden();
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String qemu = t == Target.NATIVE_RISCV64 ? "qemu-riscv64" : "qemu-aarch64";
            String[] tools = t == Target.NATIVE_RISCV64
                    ? new String[]{"riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"}
                    : new String[]{"aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"};
            assumeToolchain(tools);
            Path file = tempDir.resolve("Se-" + t + "-" + System.nanoTime() + ".kf");
            Files.writeString(file, src);
            Path outDir = tempDir.resolve("se-" + t + "-" + System.nanoTime());
            CompilationResult r = new CompilerDriver().compile(file, outDir, t);
            assertTrue(r.success(), t + " compile: " + r.diagnostics().getDiagnostics());
            Process p = NativeRiscv64E2ETest.qemu(qemu.substring(5), outDir.resolve("Default/Main"))
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec;
            try {
                ec = p.waitFor();
            } catch (InterruptedException e) {
                throw new IOException("interrupted", e);
            }
            assertEquals(0, ec, t + " qemu exit, out: " + out);
            assertEquals(expected, out, t + " golden startOf/endOf");
        }
    }

    @Test
    void timeStartEndOfCompilesOnAllTargets(@TempDir Path tempDir) throws IOException {
        String src = """
            main() {
                println(time.startOf("2026-10-01", "week"))
                println(time.endOf("2026-10-01", "month"))
            }
            """;
        Path gateSrc = tempDir.resolve("GateStartEnd.kf");
        Files.writeString(gateSrc, src);
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            CompilationResult r = new CompilerDriver().compile(gateSrc, tempDir.resolve("gate-se-" + t), t);
            assertTrue(r.success(), t + " deve compilar startOf/endOf: " + r.diagnostics().getDiagnostics());
        }
    }

    private static String startEndOfSrc() {
        return """
            main() {
                println(time.startOf("2026-10-01", "day"))
                println(time.startOf("2026-10-01", "week"))
                println(time.endOf("2026-10-01", "week"))
                println(time.startOf("2026-09-28", "week"))
                println(time.startOf("2024-02-15", "month"))
                println(time.endOf("2024-02-15", "month"))
                println(time.endOf("2023-02-15", "month"))
                println(time.startOf("2024-07-04", "year"))
                println(time.endOf("2024-07-04", "year"))
                println(time.startOf("0001-01-01", "week"))
                println(time.endOf("0001-01-01", "week"))
                println(time.startOf("9999-12-31", "week"))
                println(time.endOf("9999-12-31", "week"))
                println(time.startOf("2024-02-30", "month"))
                println(time.startOf("garbage", "day"))
                println(time.startOf("2026-10-01", "decade"))
                println(time.endOf("2026-12-31", "month"))
            }
            """;
    }

    private static String startEndOfGolden() {
        return "2026-10-01\n2026-09-28\n2026-10-04\n2026-09-28\n2024-02-01\n2024-02-29"
                + "\n2023-02-28\n2024-01-01\n2024-12-31\n0001-01-01\n0001-01-07\n9999-12-27"
                + "\n\n\n\n\n2026-12-31";
    }

    /**
     * §426 (improved 25/09): time.collect() on JS is a REAL host GC request
     * (kof_platform.gcCollect -> System.gc()), not the old compile-time
     * TIME004 gate. The JS program must compile AND run to completion (the
     * old path compiled clean but failed at module load — the export was
     * missing).
     */
    @Test
    void collectJsRunsOnHost(@TempDir Path tempDir) throws IOException {
        runJs(tempDir, """
                main() {
                    time.collect()
                    println("collected")
                }
                """, "collected");
    }

    // ── STDLIB S7e (D-STDLIB ratificado 13/09): todayIso/formatDateIso/
    // isToday — UTC-only (D1), formato zero-DSL com invalidade => "" (D4),
    // isToday = igualdade com a data UTC de now() (D5). Vetores
    // determinísticos (formatDateIso/isToday não dependem do relógio);
    // todayIso validado por FORMATO+CONSISTÊNCIA (prefixo/len/regex),
    // nunca por valor literal (o dia pode virar no meio do teste).
    @Test
    void todayIsoFormatDateIsoIsTodayJvm(@TempDir Path tempDir) throws IOException {
        runJvm(tempDir, """
                main() {
                    var today = time.todayIso()
                    println(today.length)
                    println(time.formatDateIso(2026, 9, 13))
                    println(time.formatDateIso(2024, 2, 29))
                    println(time.formatDateIso(2023, 2, 29))
                    println(time.formatDateIso(0, 1, 1))
                    println(time.formatDateIso(10000, 1, 1))
                    println(time.formatDateIso(2026, 13, 1))
                    println(time.formatDateIso(2026, 0, 1))
                    println(time.formatDateIso(2026, 1, 0))
                    println(time.formatDateIso(2026, 4, 31))
                    // Q4: isToday(y,m,d)==true NUNCA literal (o dia vira à
                    // meia-noite UTC — quebrou 14/09). Consistência interna
                    // independente do relogio: as partes de todayIso() sao
                    // hoje => isToday delas = true (parseDateIso fecha com S7g).
                    var parts = today.split("-")
                    var p0: String = parts[0]
                    var p1: String = parts[1]
                    var p2: String = parts[2]
                    println(time.isToday(math.parseInt(p0),
                                         math.parseInt(p1),
                                         math.parseInt(p2)))
                    println(time.isToday(2026, 9, 12))
                    println(time.isToday(2026, 2, 30))
                    println(time.isToday(0, 1, 1))
                    println(parts.size)
                    println(parts[0].length)
                    println(parts[1].length)
                    println(parts[2].length)
                }
                """, "10\n2026-09-13\n2024-02-29\n\n\n\n\n\n\n\ntrue\nfalse\nfalse\nfalse\n3\n4\n2\n2");
    }

    @Test
    void todayIsoFormatDateIsoIsTodayJs(@TempDir Path tempDir) throws IOException {
        runJs(tempDir, """
                main() {
                    var today = time.todayIso()
                    println(today.length)
                    println(time.formatDateIso(2026, 9, 13))
                    println(time.formatDateIso(2024, 2, 29))
                    println(time.formatDateIso(2023, 2, 29))
                    println(time.formatDateIso(0, 1, 1))
                    println(time.formatDateIso(2026, 13, 1))
                    println(time.formatDateIso(2026, 4, 31))
                    // Q4: consistencia interna (relogio-independente)
                    var parts = today.split("-")
                    var p0: String = parts[0]
                    var p1: String = parts[1]
                    var p2: String = parts[2]
                    println(time.isToday(math.parseInt(p0),
                                         math.parseInt(p1),
                                         math.parseInt(p2)))
                    println(time.isToday(2026, 9, 12))
                    println(time.isToday(2026, 2, 30))
                    println(parts.size)
                    println(parts[0].length)
                    println(parts[1].length)
                }
                """, "10\n2026-09-13\n2024-02-29\n\n\n\n\ntrue\nfalse\nfalse\n3\n4\n2");
    }

    @Test
    void todayIsoFormatDateIsoIsTodayNative(@TempDir Path tempDir) throws IOException {
        runNative(tempDir, """
                main() {
                    var today = time.todayIso()
                    println(today.length)
                    println(time.formatDateIso(2026, 9, 13))
                    println(time.formatDateIso(2024, 2, 29))
                    println(time.formatDateIso(2023, 2, 29))
                    println(time.formatDateIso(0, 1, 1))
                    println(time.formatDateIso(10000, 1, 1))
                    println(time.formatDateIso(2026, 13, 1))
                    println(time.formatDateIso(2026, 4, 31))
                    // Q4: consistencia interna (relogio-independente)
                    var parts = today.split("-")
                    var p0: String = parts[0]
                    var p1: String = parts[1]
                    var p2: String = parts[2]
                    println(time.isToday(math.parseInt(p0),
                                         math.parseInt(p1),
                                         math.parseInt(p2)))
                    println(time.isToday(2026, 9, 12))
                    println(time.isToday(2026, 2, 30))
                    println(time.isToday(0, 1, 1))
                    println(parts.size)
                    println(parts[0].length)
                    println(parts[1].length)
                }
                """, "10\n2026-09-13\n2024-02-29\n\n\n\n\n\ntrue\nfalse\nfalse\nfalse\n3\n4\n2");
    }

    @Test
    void todayIsoFormatDateIsoIsTodayCrossArch(@TempDir Path tempDir) throws Exception {
        // Vetores determinísticos + formato do todayIso (len/parts) — sem
        // valor literal do dia (pode virar entre backends). isToday com data
        // fixa SÓ é assertado para a resposta false (independe do relógio);
        // o caminho true é coberto pela igualdade formatDateIso==todayIso
        // implícita no gate de formato. Qemu prova byte-idêntico.
        String src = """
            main() {
                var today = time.todayIso()
                println(today.length)
                println(time.formatDateIso(2026, 9, 13))
                println(time.formatDateIso(2023, 2, 29))
                println(time.formatDateIso(2024, 2, 29))
                println(time.formatDateIso(0, 1, 1))
                println(time.formatDateIso(10000, 1, 1))
                println(time.formatDateIso(2026, 13, 1))
                println(time.isToday(2026, 9, 12))
                println(time.isToday(2026, 2, 30))
                var parts = today.split("-")
                println(parts.size)
                println(parts[0].length)
                println(parts[1].length)
                println(parts[2].length)
            }
            """;
        String expected = "10\n2026-09-13\n\n2024-02-29\n\n\n\nfalse\nfalse\n3\n4\n2\n2";
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String qemu = t == Target.NATIVE_RISCV64 ? "qemu-riscv64" : "qemu-aarch64";
            String[] tools = t == Target.NATIVE_RISCV64
                    ? new String[]{"riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"}
                    : new String[]{"aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"};
            assumeToolchain(tools);
            Path file = tempDir.resolve("T7e-" + t + "-" + System.nanoTime() + ".kf");
            Files.writeString(file, src);
            Path outDir = tempDir.resolve("t7e-" + t + "-" + System.nanoTime());
            CompilationResult r = new CompilerDriver().compile(file, outDir, t);
            assertTrue(r.success(), t + " compile: " + r.diagnostics().getDiagnostics());
            Process p = NativeRiscv64E2ETest.qemu(qemu.substring(5), outDir.resolve("Default/Main"))
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec;
            try {
                ec = p.waitFor();
            } catch (InterruptedException e) {
                throw new IOException("interrupted", e);
            }
            assertEquals(0, ec, t + " qemu exit, out: " + out);
            assertEquals(expected, out, t + " golden S7e");
        }
    }

    @Test
    void todayIsoFormatDateIsoIsTodayCompilesOnAllTargets(@TempDir Path tempDir) throws IOException {
        // Gate SEMPRE-verde (mesmo sem qemu): o backend cross EMITE o asm;
        // a EXECUCAO e provada sob qemu no teste acima.
        String src = """
            main() {
                println(time.todayIso().length)
                println(time.formatDateIso(2026, 9, 13))
                println(time.isToday(2026, 9, 13))
            }
            """;
        Path gateSrc = tempDir.resolve("GateT7e.kf");
        Files.writeString(gateSrc, src);
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            CompilationResult r = new CompilerDriver().compile(gateSrc, tempDir.resolve("gate-t7e-" + t), t);
            assertTrue(r.success(), t + " deve compilar todayIso/formatDateIso/isToday (S7e): "
                    + r.diagnostics().getDiagnostics());
        }
    }

    // ── STDLIB S7f (D3): hoursBetween — floor simétrico (truncado a
    // zero, consistente com daysBetween); datas inválidas/hora fora de
    // 0..23 => 0 (paridade do gating do wedge). Sem float (FLT001).
    // Totalmente determinístico — vetor cru incl. diferenças negativas,
    // virada de dia/mês/ano-bissexto e bounds 9999/1.
    @Test
    void hoursBetweenJvm(@TempDir Path tempDir) throws IOException {
        runJvm(tempDir, """
                main() {
                    println(time.hoursBetween(2026, 1, 1, 10, 2026, 1, 2, 12))
                    println(time.hoursBetween(2026, 1, 2, 12, 2026, 1, 1, 10))
                    println(time.hoursBetween(2026, 1, 1, 0, 2026, 1, 1, 23))
                    println(time.hoursBetween(2026, 1, 1, 23, 2026, 1, 2, 0))
                    println(time.hoursBetween(2026, 1, 1, 5, 2026, 1, 1, 5))
                    println(time.hoursBetween(2026, 2, 30, 5, 2026, 3, 1, 5))
                    println(time.hoursBetween(2026, 1, 1, 24, 2026, 1, 2, 5))
                    println(time.hoursBetween(2026, 1, 1, 5, 2026, 1, 1, 25))
                    println(time.hoursBetween(2024, 2, 29, 1, 2024, 3, 1, 1))
                    println(time.hoursBetween(9999, 12, 31, 0, 1, 1, 0, 23))
                    println(time.hoursBetween(2026, 1, 1, 10, 2027, 1, 1, 10))
                }
                """, "26\n-26\n23\n1\n0\n0\n0\n0\n24\n0\n8760");
    }

    @Test
    void hoursBetweenJs(@TempDir Path tempDir) throws IOException {
        runJs(tempDir, """
                main() {
                    println(time.hoursBetween(2026, 1, 1, 10, 2026, 1, 2, 12))
                    println(time.hoursBetween(2026, 1, 2, 12, 2026, 1, 1, 10))
                    println(time.hoursBetween(2026, 1, 1, 0, 2026, 1, 1, 23))
                    println(time.hoursBetween(2026, 1, 1, 23, 2026, 1, 2, 0))
                    println(time.hoursBetween(2026, 1, 1, 5, 2026, 1, 1, 5))
                    println(time.hoursBetween(2026, 2, 30, 5, 2026, 3, 1, 5))
                    println(time.hoursBetween(2026, 1, 1, 24, 2026, 1, 2, 5))
                    println(time.hoursBetween(2024, 2, 29, 1, 2024, 3, 1, 1))
                    println(time.hoursBetween(2026, 1, 1, 10, 2027, 1, 1, 10))
                }
                """, "26\n-26\n23\n1\n0\n0\n0\n24\n8760");
    }

    @Test
    void hoursBetweenNative(@TempDir Path tempDir) throws IOException {
        runNative(tempDir, """
                main() {
                    println(time.hoursBetween(2026, 1, 1, 10, 2026, 1, 2, 12))
                    println(time.hoursBetween(2026, 1, 2, 12, 2026, 1, 1, 10))
                    println(time.hoursBetween(2026, 1, 1, 0, 2026, 1, 1, 23))
                    println(time.hoursBetween(2026, 1, 1, 23, 2026, 1, 2, 0))
                    println(time.hoursBetween(2026, 1, 1, 5, 2026, 1, 1, 5))
                    println(time.hoursBetween(2026, 2, 30, 5, 2026, 3, 1, 5))
                    println(time.hoursBetween(2026, 1, 1, 24, 2026, 1, 2, 5))
                    println(time.hoursBetween(2024, 2, 29, 1, 2024, 3, 1, 1))
                    println(time.hoursBetween(2026, 1, 1, 10, 2027, 1, 1, 10))
                }
                """, "26\n-26\n23\n1\n0\n0\n0\n24\n8760");
    }

    @Test
    void hoursBetweenCrossArch(@TempDir Path tempDir) throws Exception {
        String src = """
            main() {
                println(time.hoursBetween(2026, 1, 1, 10, 2026, 1, 2, 12))
                println(time.hoursBetween(2026, 1, 2, 12, 2026, 1, 1, 10))
                println(time.hoursBetween(2026, 1, 1, 0, 2026, 1, 1, 23))
                println(time.hoursBetween(2026, 1, 1, 23, 2026, 1, 2, 0))
                println(time.hoursBetween(2026, 1, 1, 5, 2026, 1, 1, 5))
                println(time.hoursBetween(2026, 2, 30, 5, 2026, 3, 1, 5))
                println(time.hoursBetween(2026, 1, 1, 24, 2026, 1, 2, 5))
                println(time.hoursBetween(2024, 2, 29, 1, 2024, 3, 1, 1))
                println(time.hoursBetween(2026, 1, 1, 10, 2027, 1, 1, 10))
            }
            """;
        String expected = "26\n-26\n23\n1\n0\n0\n0\n24\n8760";
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String qemu = t == Target.NATIVE_RISCV64 ? "qemu-riscv64" : "qemu-aarch64";
            String[] tools = t == Target.NATIVE_RISCV64
                    ? new String[]{"riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"}
                    : new String[]{"aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"};
            assumeToolchain(tools);
            Path file = tempDir.resolve("T7f-" + t + "-" + System.nanoTime() + ".kf");
            Files.writeString(file, src);
            Path outDir = tempDir.resolve("t7f-" + t + "-" + System.nanoTime());
            CompilationResult r = new CompilerDriver().compile(file, outDir, t);
            assertTrue(r.success(), t + " compile: " + r.diagnostics().getDiagnostics());
            Process p = NativeRiscv64E2ETest.qemu(qemu.substring(5), outDir.resolve("Default/Main"))
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec;
            try {
                ec = p.waitFor();
            } catch (InterruptedException e) {
                throw new IOException("interrupted", e);
            }
            assertEquals(0, ec, t + " qemu exit, out: " + out);
            assertEquals(expected, out, t + " golden S7f");
        }
    }

    @Test
    void hoursBetweenCompilesOnAllTargets(@TempDir Path tempDir) throws IOException {
        String src = """
            main() {
                println(time.hoursBetween(2026, 1, 1, 10, 2026, 1, 2, 12))
            }
            """;
        Path gateSrc = tempDir.resolve("GateT7f.kf");
        Files.writeString(gateSrc, src);
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            CompilationResult r = new CompilerDriver().compile(gateSrc, tempDir.resolve("gate-t7f-" + t), t);
            assertTrue(r.success(), t + " deve compilar hoursBetween (S7f): "
                    + r.diagnostics().getDiagnostics());
        }
    }

    // ── STDLIB S7g (D4): parseDateIso — "YYYY-MM-DD" estrito -> serial
    // daysFromEpoch; inválido => 0. MESMO serial de hoursBetween/
    // daysBetween (recomposição s - e = diff fecha com os vetores de cima).
    // Totalmente determinístico.
    @Test
    void parseDateIsoJvm(@TempDir Path tempDir) throws IOException {
        runJvm(tempDir, """
                main() {
                    println(time.parseDateIso("1970-01-01"))
                    println(time.parseDateIso("2026-09-13"))
                    println(time.parseDateIso("2024-02-29"))
                    println(time.parseDateIso("0001-01-01"))
                    println(time.parseDateIso("9999-12-31"))
                    println(time.parseDateIso("2023-02-29"))
                    println(time.parseDateIso("2026-13-01"))
                    println(time.parseDateIso("garbage"))
                    println(time.parseDateIso(""))
                    println(time.parseDateIso("2026-9-13"))
                    var s = time.parseDateIso("2026-09-13")
                    var e = time.parseDateIso("1970-01-01")
                    println(s - e)
                }
                """, "0\n20709\n19782\n-719162\n2932896\n0\n0\n0\n0\n0\n20709");
    }

    @Test
    void parseDateIsoJs(@TempDir Path tempDir) throws IOException {
        runJs(tempDir, """
                main() {
                    println(time.parseDateIso("1970-01-01"))
                    println(time.parseDateIso("2026-09-13"))
                    println(time.parseDateIso("2024-02-29"))
                    println(time.parseDateIso("0001-01-01"))
                    println(time.parseDateIso("9999-12-31"))
                    println(time.parseDateIso("2023-02-29"))
                    println(time.parseDateIso("2026-13-01"))
                    println(time.parseDateIso("garbage"))
                    println(time.parseDateIso(""))
                    println(time.parseDateIso("2026-9-13"))
                }
                """, "0\n20709\n19782\n-719162\n2932896\n0\n0\n0\n0\n0");
    }

    @Test
    void parseDateIsoNative(@TempDir Path tempDir) throws IOException {
        runNative(tempDir, """
                main() {
                    println(time.parseDateIso("1970-01-01"))
                    println(time.parseDateIso("2026-09-13"))
                    println(time.parseDateIso("2024-02-29"))
                    println(time.parseDateIso("0001-01-01"))
                    println(time.parseDateIso("9999-12-31"))
                    println(time.parseDateIso("2023-02-29"))
                    println(time.parseDateIso("2026-13-01"))
                    println(time.parseDateIso("garbage"))
                    println(time.parseDateIso(""))
                    println(time.parseDateIso("2026-9-13"))
                }
                """, "0\n20709\n19782\n-719162\n2932896\n0\n0\n0\n0\n0");
    }

    @Test
    void parseDateIsoCrossArch(@TempDir Path tempDir) throws Exception {
        String src = """
            main() {
                println(time.parseDateIso("1970-01-01"))
                println(time.parseDateIso("2026-09-13"))
                println(time.parseDateIso("2024-02-29"))
                println(time.parseDateIso("0001-01-01"))
                println(time.parseDateIso("9999-12-31"))
                println(time.parseDateIso("2023-02-29"))
                println(time.parseDateIso("2026-13-01"))
                println(time.parseDateIso("garbage"))
                println(time.parseDateIso("2026-9-13"))
                var s = time.parseDateIso("2026-09-13")
                var e = time.parseDateIso("1970-01-01")
                println(s - e)
            }
            """;
        String expected = "0\n20709\n19782\n-719162\n2932896\n0\n0\n0\n0\n20709";
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String qemu = t == Target.NATIVE_RISCV64 ? "qemu-riscv64" : "qemu-aarch64";
            String[] tools = t == Target.NATIVE_RISCV64
                    ? new String[]{"riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"}
                    : new String[]{"aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"};
            assumeToolchain(tools);
            Path file = tempDir.resolve("T7g-" + t + "-" + System.nanoTime() + ".kf");
            Files.writeString(file, src);
            Path outDir = tempDir.resolve("t7g-" + t + "-" + System.nanoTime());
            CompilationResult r = new CompilerDriver().compile(file, outDir, t);
            assertTrue(r.success(), t + " compile: " + r.diagnostics().getDiagnostics());
            Process p = NativeRiscv64E2ETest.qemu(qemu.substring(5), outDir.resolve("Default/Main"))
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec;
            try {
                ec = p.waitFor();
            } catch (InterruptedException e) {
                throw new IOException("interrupted", e);
            }
            assertEquals(0, ec, t + " qemu exit, out: " + out);
            assertEquals(expected, out, t + " golden S7g");
        }
    }

    @Test
    void parseDateIsoCompilesOnAllTargets(@TempDir Path tempDir) throws IOException {
        String src = """
            main() {
                println(time.parseDateIso("2026-09-13"))
            }
            """;
        Path gateSrc = tempDir.resolve("GateT7g.kf");
        Files.writeString(gateSrc, src);
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            CompilationResult r = new CompilerDriver().compile(gateSrc, tempDir.resolve("gate-t7g-" + t), t);
            assertTrue(r.success(), t + " deve compilar parseDateIso (S7g): "
                    + r.diagnostics().getDiagnostics());
        }
    }

    // ── STDLIB S7h (D1): tzOffsetSeconds — fuso do HOST como getter
    // explícito. NÃO-determinístico entre máquinas: a prova valida o
    // CONTRATO (múltiplo de 900s na prática, range UTC-12..UTC+14, e
    // consistência interna: now()+tz alinhado em minutos com civil UTC)
    // e a PARIDADE JVM×JS (mesma saída nas 2 execuções — mesmo host).
    // Native/riscv/aarch = gap honesto TIME003 (diagnóstico, R6).
    @Test
    void tzOffsetSecondsJvmAndJsParity(@TempDir Path tempDir) throws IOException {
        String src = """
                main() {
                    var tz = time.tzOffsetSeconds()
                    println(tz % 60)
                    println(tz >= -43200 && tz <= 50400)
                    println(tz)
                }
                """;
        // Oracle JVM (medição real, nunca memória): offset ATUAL da zona do
        // host (com DST). getTimezoneOffset() do JS = mesmo instante.
        int jvmTz = java.time.ZoneId.systemDefault().getRules()
                .getOffset(java.time.Instant.now()).getTotalSeconds();
        String expected = "0\ntrue\n" + jvmTz;
        // JVM e JS rodam NO MESMO HOST => a paridade JVM×JS (D1: sem
        // divergência acidental) é provada por AMBOS baterem com o oracle.
        runJvm(tempDir, src, expected);
        runJs(tempDir, src, expected);
    }

    @Test
    void tzOffsetSecondsNativeRefusedWithDiagnostic(@TempDir Path tempDir) throws IOException {
        // Gap honesto TIME003 (R6): Native recusa com diagnóstico — nunca
        // fallback silencioso, nunca "0 fingido".
        Path source = tempDir.resolve("Tz.kf");
        Files.writeString(source, """
                main() {
                    println(time.tzOffsetSeconds())
                }
                """);
        CompilationResult r = driver.compile(source, tempDir.resolve("out-tz-nat"), Target.NATIVE);
        assertFalse(r.success(), "Native deve RECUSAR tzOffsetSeconds (TIME003)");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> d.message().contains("TIME003")),
                "diagnóstico deve citar TIME003: " + r.diagnostics().getDiagnostics());
    }

    // ── CRON001 (17/09): `scheduler.at(cron)` deixa de ignorar a expressão.
    // Parser cron real de 5 campos em UTC no JVM e no JS; Native recusa em
    // compile-time (R6/R7). A tabela abaixo é o oracle determinístico (âncora
    // 2024-01-01T00:00:00Z = segunda-feira), compartilhada pelos 2 alvos.
    private static final long CRON_ANCHOR = 1704067200000L;

    private static final String[][] CRON_TABLE = {
        {"* * * * *", "60000"},
        {"*/5 * * * *", "300000"},
        {"0 3 * * *", "10800000"},
        {"0 0 1 * *", "2678400000"},
        {"30 14 * * 1", "52200000"},
        {"0 0 29 2 *", "5097600000"},
        {"0 12 * * 7", "561600000"},
        {"0 12 31 * 1", "43200000"},
        {"0 12 * * 1,3", "43200000"},
        {"0 0-6/2 * * *", "7200000"},
        {"0 0 * * 0", "518400000"},
    };

    @Test
    void cronNextDelayJvmMatchesTable(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    var id = scheduler.at("0 0 1 1 *", () -> {})
                    scheduler.cancel(id)
                }
                """);
        Path outDir = tempDir.resolve("cron-jvm");
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile: " + result.diagnostics().getDiagnostics());
        try (java.net.URLClassLoader cl = new java.net.URLClassLoader(
                new java.net.URL[]{outDir.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> rt = cl.loadClass("dev.kof.runtime.KofRuntime");
            java.lang.reflect.Method m = rt.getMethod("kof_cron_next_delay_ms", String.class, long.class);
            for (String[] row : CRON_TABLE) {
                Object got = m.invoke(null, row[0], CRON_ANCHOR);
                assertEquals(Long.parseLong(row[1]), ((Number) got).longValue(),
                        "JVM cron delay para '" + row[0] + "'");
            }
            for (String bad : new String[]{"bogus", "* * * *", "60 * * * *", "* 24 * * *", ""}) {
                try {
                    m.invoke(null, bad, CRON_ANCHOR);
                    fail("JVM cron inválido deveria lançar: '" + bad + "'");
                } catch (java.lang.reflect.InvocationTargetException e) {
                    assertTrue(e.getCause() instanceof IllegalArgumentException,
                            "esperava IllegalArgumentException para '" + bad + "': " + e.getCause());
                }
            }
        }
    }

    @Test
    void cronNextDelayJsMatchesJvmTable(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    var id = scheduler.at("0 0 1 1 *", () -> {})
                    scheduler.cancel(id)
                }
                """);
        Path outDir = tempDir.resolve("cron-js");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "JS compile: " + result.diagnostics().getDiagnostics());
        StringBuilder probe = new StringBuilder(
                "import { kofCronNextDelayMs } from './kof-runtime.mjs';\n");
        for (String[] row : CRON_TABLE) {
            probe.append("console.log(String(kofCronNextDelayMs(\"")
                    .append(row[0]).append("\", ").append(CRON_ANCHOR).append(")));\n");
        }
        for (String bad : new String[]{"bogus", "* * * *", "60 * * * *", "* 24 * * *", ""}) {
            probe.append("try { kofCronNextDelayMs(\"").append(bad)
                    .append("\", ").append(CRON_ANCHOR)
                    .append("); console.log(\"NO-THROW\"); } catch (e) { console.log(\"THREW\"); }\n");
        }
        Path entry = outDir.resolve("CronProbe.mjs");
        Files.writeString(entry, probe.toString());
        StringBuilder expected = new StringBuilder();
        for (String[] row : CRON_TABLE) expected.append(row[1]).append('\n');
        for (int i = 0; i < 5; i++) expected.append("THREW\n");
        try (java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
                java.io.ByteArrayOutputStream errBuf = new java.io.ByteArrayOutputStream()) {
            int ec = dev.kof.runtime.KofJsRunner.run(entry, buf,
                    java.io.InputStream.nullInputStream(), errBuf);
            String output = buf.toString(StandardCharsets.UTF_8).trim();
            assertEquals(0, ec, "JS exit, out: " + output
                    + " err: " + errBuf.toString(StandardCharsets.UTF_8));
            assertEquals(expected.toString().trim(), output, "JS cron delay deve espelhar o JVM");
        }
    }

    // ── D-SCHED-DURATION (19/09): durações idiomáticas no `scheduler.at`.
    // Tabela oracle compartilhada JVM×JS com a MESMA âncora do cron. Sem
    // limite de termos na composição '&'; 7 termos = caso de borda.
    private static final long DUR_MONTH_END_ANCHOR = 1706659200000L; // 2024-01-31T00:00:00Z

    private static final String[][] DUR_TABLE = {
        {"30m", "1800000"},
        {"90s", "90000"},
        {"300ms", "300"},
        {"1d", "86400000"},
        {"1d&30m", "88200000"},
        {"1M", "2678400000"},
        {"1M&15m", "2679300000"},
        {"1a", "31622400000"},
        {"3a&6M&3d&4h&12m&12s&300ms", "110607132300"},
    };

    private static final String[] DUR_BAD = {
        "0m", "5x", "1d&", "m", "1d&30x", "30", "1d&&30m", "-5m",
    };

    @Test
    void durationNextDelayJvmMatchesTable(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    var id = scheduler.at("30m", () -> {})
                    scheduler.cancel(id)
                }
                """);
        Path outDir = tempDir.resolve("dur-jvm");
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile: " + result.diagnostics().getDiagnostics());
        try (java.net.URLClassLoader cl = new java.net.URLClassLoader(
                new java.net.URL[]{outDir.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> rt = cl.loadClass("dev.kof.runtime.KofRuntime");
            java.lang.reflect.Method m = rt.getMethod("kof_duration_next_delay_ms", String.class, long.class);
            for (String[] row : DUR_TABLE) {
                Object got = m.invoke(null, row[0], CRON_ANCHOR);
                assertEquals(Long.parseLong(row[1]), ((Number) got).longValue(),
                        "JVM duration delay para '" + row[0] + "'");
            }
            // clamp civil: 2024-01-31 + 1M = 2024-02-29 (não 02-31→03-02)
            Object clamp = m.invoke(null, "1M", DUR_MONTH_END_ANCHOR);
            assertEquals(2505600000L, ((Number) clamp).longValue(), "clamp 1M em 31/jan");
            for (String bad : DUR_BAD) {
                try {
                    m.invoke(null, bad, CRON_ANCHOR);
                    fail("JVM duração inválida deveria lançar: '" + bad + "'");
                } catch (java.lang.reflect.InvocationTargetException e) {
                    assertTrue(e.getCause() instanceof IllegalArgumentException,
                            "esperava IllegalArgumentException para '" + bad + "': " + e.getCause());
                }
            }
        }
    }

    @Test
    void durationSchedulerAtFiresJvm(@TempDir Path tempDir) throws Exception {
        // Comportamento real do at("20ms"): repete no intervalo, cancel para.
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    var id = scheduler.at("20ms", () -> {})
                    scheduler.cancel(id)
                }
                """);
        Path outDir = tempDir.resolve("dur-fire-jvm");
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile: " + result.diagnostics().getDiagnostics());
        try (java.net.URLClassLoader cl = new java.net.URLClassLoader(
                new java.net.URL[]{outDir.toUri().toURL()}, getClass().getClassLoader())) {
            Class<?> rt = cl.loadClass("dev.kof.runtime.KofRuntime");
            java.lang.reflect.Method at = rt.getMethod("kof_scheduler_at", String.class, Object.class);
            java.lang.reflect.Method cancel = rt.getMethod("kof_scheduler_cancel", String.class);
            TickCounter fn = new TickCounter();
            Object id = at.invoke(null, "20ms", fn);
            assertTrue(TestServerFixture.awaitTrue(200, 5, () -> fn.n >= 3),
                    "esperava >= 3 disparos em 20ms, tivemos " + fn.n);
            cancel.invoke(null, id);
            int afterCancel = fn.n;
            assertFalse(TestServerFixture.awaitTrue(16, 5, () -> fn.n != afterCancel),
                    "cancel deve parar os disparos");
        }
    }

    public interface Tick {
        void invoke();
    }

    /** PUBLICA e nomeada: KofRuntime (outro pacote/classloader) só acessa
     *  via getMethod("invoke") membros de classe pública — classes anônimas
     *  e lambdas Java são package-private/hidden (IllegalAccessException). */
    public static class TickCounter implements Tick {
        public volatile int n = 0;
        @Override
        public void invoke() { n++; }
    }

    @Test
    void durationNextDelayJsMatchesJvmTable(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
                main() {
                    var id = scheduler.at("30m", () -> {})
                    scheduler.cancel(id)
                }
                """);
        Path outDir = tempDir.resolve("dur-js");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(), "JS compile: " + result.diagnostics().getDiagnostics());
        StringBuilder probe = new StringBuilder(
                "import { kofDurationNextDelayMs } from './kof-runtime.mjs';\n");
        for (String[] row : DUR_TABLE) {
            probe.append("console.log(String(kofDurationNextDelayMs(\"")
                    .append(row[0]).append("\", ").append(CRON_ANCHOR).append(")));\n");
        }
        probe.append("console.log(String(kofDurationNextDelayMs(\"1M\", ")
                .append(DUR_MONTH_END_ANCHOR).append(")));\n");
        for (String bad : DUR_BAD) {
            probe.append("try { kofDurationNextDelayMs(\"").append(bad)
                    .append("\", ").append(CRON_ANCHOR)
                    .append("); console.log(\"NO-THROW\"); } catch (e) { console.log(\"THREW\"); }\n");
        }
        Path entry = outDir.resolve("DurProbe.mjs");
        Files.writeString(entry, probe.toString());
        StringBuilder expected = new StringBuilder();
        for (String[] row : DUR_TABLE) expected.append(row[1]).append('\n');
        expected.append("2505600000\n");
        for (int i = 0; i < DUR_BAD.length; i++) expected.append("THREW\n");
        try (java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
                java.io.ByteArrayOutputStream errBuf = new java.io.ByteArrayOutputStream()) {
            int ec = dev.kof.runtime.KofJsRunner.run(entry, buf,
                    java.io.InputStream.nullInputStream(), errBuf);
            String output = buf.toString(StandardCharsets.UTF_8).trim();
            assertEquals(0, ec, "JS exit, out: " + output
                    + " err: " + errBuf.toString(StandardCharsets.UTF_8));
            assertEquals(expected.toString().trim(), output, "JS duration delay deve espelhar o JVM");
        }
    }

    @Test
    void schedulerAtNativeIsHonestGapCron001(@TempDir Path tempDir) throws IOException {
        // R6/R7: Native não tem o parser cron em asm — recusa em compile-time
        // com CRON001, nunca o stub silencioso de 60s que ignorava a expressão.
        Path source = tempDir.resolve("CronNat.kf");
        Files.writeString(source, """
                main() {
                    scheduler.at("*/5 * * * *", () -> println("tick"))
                }
                """);
        for (Target t : new Target[]{Target.NATIVE, Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            CompilationResult r = driver.compile(source, tempDir.resolve("cron-nat-" + t), t);
            assertFalse(r.success(), t + " deve RECUSAR scheduler.at (CRON001)");
            assertTrue(r.diagnostics().getDiagnostics().stream()
                            .anyMatch(d -> d.message().contains("CRON001")),
                    t + " deve citar CRON001: " + r.diagnostics().getDiagnostics());
        }
    }

    @Test
    void schedulerAtInvalidCronFailsLoudlyJvm(@TempDir Path tempDir) throws IOException {
        // Cron inválido não pode ser silencioso: o processo morre com a
        // mensagem (Q0/R6), nunca agenda em 60s como o stub antigo.
        Path source = tempDir.resolve("CronBad.kf");
        Files.writeString(source, """
                main() {
                    scheduler.at("bogus", () -> println("tick"))
                    time.sleep(50)
                    println("unreachable")
                }
                """);
        Path outDir = tempDir.resolve("cron-bad");
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "compile: " + result.diagnostics().getDiagnostics());
        try {
            String javaCmd = TestJdk.javaBin();
            ProcessBuilder pb = new ProcessBuilder(javaCmd, "-Dfile.encoding=UTF-8",
                    "-Dstdout.encoding=UTF-8", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int ec = p.waitFor();
            assertNotEquals(0, ec, "cron inválido deve falhar alto, saída: " + output);
            assertTrue(output.contains("cron"), "mensagem deve citar cron: " + output);
            assertFalse(output.contains("unreachable"), "não deve seguir silencioso: " + output);
        } catch (InterruptedException e) {
            throw new IOException("interrupted", e);
        }
    }

    private void assumeToolchain(String... tools) {
        for (String c : tools) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c)
                        .redirectErrorStream(true).start();
                String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                org.junit.jupiter.api.Assumptions.assumeTrue(
                        p.waitFor() == 0 && !o.isEmpty(), "toolchain ausente: " + c);
            } catch (Exception e) {
                org.junit.jupiter.api.Assumptions.assumeTrue(false, "toolchain ausente: " + c);
            }
        }
    }
}