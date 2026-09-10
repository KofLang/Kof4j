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
            ProcessBuilder pb = new ProcessBuilder("java", "-Dfile.encoding=UTF-8",
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
            Process p = new ProcessBuilder(qemu, bin.toString()).redirectErrorStream(true).start();
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
            var p = new ProcessBuilder("timeout", "10", q[i], bin.toString()).redirectErrorStream(true).start();
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
    void timeAddDaysDiffDaysJvmShapeAndTime002Gate(@TempDir Path tempDir) throws IOException {
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
        // Restam riscv64/aarch64 (TIME002) com erro claro no compile (R6).
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            CompilationResult r = new CompilerDriver().compile(gateSrc, tempDir.resolve("gate-" + t), t);
            assertFalse(r.success(), t + " deve rejeitar addDays/diffDays (TIME002)");
            boolean hasTime002 = r.diagnostics().getDiagnostics().stream()
                    .anyMatch(d -> "TIME002".equals(d.code())
                            && d.severity() == Diagnostic.Severity.ERROR);
            assertTrue(hasTime002, t + " deve reportar TIME002, veio: "
                    + r.diagnostics().getDiagnostics());
        }
    }
}