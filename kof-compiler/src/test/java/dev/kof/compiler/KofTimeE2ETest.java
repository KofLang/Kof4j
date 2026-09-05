package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;


/**
 * End-to-end tests for {@code kof.time} — sleep, now e scheduler.
 */
class KofTimeE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

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
    void crossNativeReportsTime001ForInterval(@TempDir Path tempDir) throws IOException {
        // R6: time.interval/cancel no riscv64/aarch64 são stubs que nunca
        // disparam o callback (no-op silencioso). Gate TIME001 em compile-time.
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, """
            main() {
                var id = time.interval(50, () -> println("tick"))
                time.cancel(id)
            }
            """);
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            CompilationResult r = new CompilerDriver().compile(source, tempDir.resolve("cross-" + t), t);
            assertFalse(r.success(), t + " should report TIME001");
            assertTrue(r.diagnostics().getDiagnostics().toString().contains("TIME001"),
                    t + ": " + r.diagnostics().getDiagnostics());
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
}