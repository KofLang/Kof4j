package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Fase 5 / unidade 1 (#666, D-MEMORY-SAFETY): a interseção FFI x spawn/captura
 * nunca fora exercitada — medido 28/09 (zero `spawn` em FfiE2ETest/BufferFfiE2ETest;
 * MemorySafetyE2ETest sem extern; SpawnE2ETest sem ffi). Goldens SÃO O QUE A ÁRVORE
 * PRODUZ (sonda 28/09: JVM/JS/Native = 3.0/5; MEM021 x extern = ERROR), não promessa.
 *
 * Faces da unidade 2 (fase 5, decididas 28/09) agora PINADAS: #667 — Script x
 * `extern` é recusado no compile-time reusando `FFI001` na linha da declaração
 * (`D-SCRIPT-EXTERN-REFUSE`; prova `ScriptTargetTest#externIsRefusedOnScriptFfi001`);
 * #668 — duas escritas FFI no MESMO `Buffer(U8)` via `spawn` sem sync agora
 * ardém `MEM020` (`D-MEM020-COMPILE`; provas abaixo).
 */
class FfiCaptureSpawnE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase().contains("linux");
    }

    // FFI dentro da task spawn, lendo captura read-only (por valor, sem caixa).
    private static final String FFI_SPAWN_READ = """
            extern "libm.so.6" sqrt(Double x): Double

            main() {
                var v = 9.0
                var h = spawn { return sqrt(v) }
                println(await h)
            }
            """;

    // FFI dentro da task spawn sem nenhuma captura.
    private static final String FFI_SPAWN_NO_CAPTURE = """
            extern "libc.so.6" abs(Int x): Int

            main() {
                var h = spawn { return abs(-5) }
                println(await h)
            }
            """;

    private String runJvm(Path outDir) throws IOException {
        try {
            String javaHome = System.getProperty("java.home");
            ProcessBuilder pb = new ProcessBuilder(
                    Path.of(javaHome, "bin", "java").toString(),
                    "--enable-native-access=ALL-UNNAMED",
                    "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code, output: '" + output + "'");
            return output;
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    private String runJs(Path outDir) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        assertEquals(0, ec, "JS exit code, output: " + out);
        return out.toString(java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
    }

    private String runNative(Path outDir) throws IOException {
        try {
            Process p = new ProcessBuilder(outDir.resolve("Default/Main").toString())
                    .redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Native exit code, output: '" + output + "'");
            return output;
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    private void compileExpectOk(Path src, Path out, Target t) throws IOException {
        CompilationResult r = driver.compile(src, out, t);
        assertTrue(r.success(), t + " compile: " + r.diagnostics().getDiagnostics());
    }

    private static final String MEM021_VIA_FFI = """
            extern "libc.so.6" atoi(String x): Int

            main() {
                var n = 1
                var h = spawn { n = atoi("2"); return n }
                n = 3
                println(await h)
            }
            """;

    @Test
    void jvmFfiInsideSpawnAwaitReturnsValue(@TempDir Path t) throws IOException {
        Path s = t.resolve("Main-jvm-read.kf");
        Files.writeString(s, FFI_SPAWN_READ);
        compileExpectOk(s, t.resolve("out-jvm-read"), Target.JVM);
        assertEquals("3.0", runJvm(t.resolve("out-jvm-read")));
    }

    @Test
    void jsFfiInsideSpawnAwaitReturnsValue(@TempDir Path t) throws IOException {
        Path s = t.resolve("Main-js-read.kf");
        Files.writeString(s, FFI_SPAWN_READ);
        compileExpectOk(s, t.resolve("out-js-read"), Target.JS);
        assertEquals("3.0", runJs(t.resolve("out-js-read")), "JS host-FFM bridge sob spawn");
    }

    @Test
    void nativeFfiInsideSpawnAwaitReturnsValue(@TempDir Path t) throws IOException {
        assumeTrue(isLinux(), "link direto por PLT exige toolchain linux (precedente FfiNativeE2ETest)");
        Path s = t.resolve("Main-nat-read.kf");
        Files.writeString(s, FFI_SPAWN_READ);
        compileExpectOk(s, t.resolve("out-nat-read"), Target.NATIVE);
        assertEquals("3.0", runNative(t.resolve("out-nat-read")), "pthread worker + call@PLT");
    }

    @Test
    void jvmFfiInsideSpawnNoCapture(@TempDir Path t) throws IOException {
        Path s = t.resolve("Main-jvm-nocap.kf");
        Files.writeString(s, FFI_SPAWN_NO_CAPTURE);
        compileExpectOk(s, t.resolve("out-jvm-nocap"), Target.JVM);
        assertEquals("5", runJvm(t.resolve("out-jvm-nocap")));
    }

    @Test
    void jsFfiInsideSpawnNoCapture(@TempDir Path t) throws IOException {
        Path s = t.resolve("Main-js-nocap.kf");
        Files.writeString(s, FFI_SPAWN_NO_CAPTURE);
        compileExpectOk(s, t.resolve("out-js-nocap"), Target.JS);
        assertEquals("5", runJs(t.resolve("out-js-nocap")));
    }

    @Test
    void nativeFfiInsideSpawnNoCapture(@TempDir Path t) throws IOException {
        assumeTrue(isLinux(), "link direto por PLT exige toolchain linux");
        Path s = t.resolve("Main-nat-nocap.kf");
        Files.writeString(s, FFI_SPAWN_NO_CAPTURE);
        compileExpectOk(s, t.resolve("out-nat-nocap"), Target.NATIVE);
        assertEquals("5", runNative(t.resolve("out-nat-nocap")));
    }

    @Test
    void ffiDoesNotBypassMem021(@TempDir Path t) throws IOException {
        // A corrida é a mesma com ou sem FFI no meio: o extern NAO e uma raiz de
        // escape do OwnershipPass — pino da interação D-MEM021-SCALAR x fase 5.
        Path s = t.resolve("Main-mem021.kf");
        Files.writeString(s, MEM021_VIA_FFI);
        for (Target target : List.of(Target.JVM, Target.NATIVE, Target.JS)) {
            CompilationResult r = driver.compile(s, t.resolve("out-mem021-" + target), target);
            assertFalse(r.success(), target + ": escrita do pai pos-spawn com extern deve falhar");
            String diags = r.diagnostics().getDiagnostics().toString();
            assertTrue(diags.contains("MEM021"), target + ": esperado MEM021, obtido " + diags);
        }
    }

    // ── #668 / D-MEM020-COMPILE: escrita FFI concorrente no MESMO Buffer ──
    // B-03: um `extern` com parametro `Buffer(U8)` INOUT escreve o buffer no
    // lado C. Duas escritas sem `await` entre (worker×pai ou worker×worker)
    // são corrida real; antes compilavam LIMPO (medido no #666).

    private static final String MEM020_PARENT_AND_SPAWN = """
            extern "libc.so.6" kof_fill(Buffer(U8) b): Int

            main() {
                var b = buffer.alloc(8)
                var h = spawn { return kof_fill(b) }
                kof_fill(b)
                await h
            }
            """;

    private static final String MEM020_TWO_SPAWNS = """
            extern "libc.so.6" kof_fill(Buffer(U8) b): Int

            main() {
                var b = buffer.alloc(8)
                var h1 = spawn { return kof_fill(b) }
                var h2 = spawn { return kof_fill(b) }
                await h1
                await h2
            }
            """;

    private static final String FFI_SPAWN_AWAITED_OK = """
            extern "libc.so.6" kof_fill(Buffer(U8) b): Int

            main() {
                var b = buffer.alloc(8)
                var h = spawn { return kof_fill(b) }
                println(await h)
            }
            """;

    private void compileExpectMem020(Path t, String tag, String kof) throws IOException {
        Path s = t.resolve("Main-" + tag + ".kf");
        Files.writeString(s, kof);
        for (Target target : List.of(Target.JVM, Target.NATIVE, Target.JS)) {
            CompilationResult r = driver.compile(s, t.resolve("out-" + tag + "-" + target), target);
            assertFalse(r.success(), target + ": escrita FFI concorrente no Buffer deve falhar");
            String diags = r.diagnostics().getDiagnostics().toString();
            assertTrue(diags.contains("MEM020"), target + ": esperado MEM020, obtido " + diags);
        }
    }

    @Test
    void ffiRefusedOnScriptEvenInsideSpawn(@TempDir Path t) throws IOException {
        // #667 integração: a recusa vale para o MESMO fonte FFI×spawn — o Script
        // não compila o extern e o programa nunca chega ao runtime cru. Por
        // construção, a face MEM020 no Script é INALCANÇÁVEL (sem extern não há
        // escrita FFI), então o pin correto é FFI001, não MEM020.
        Path s = t.resolve("Main-script-ffi.kf");
        Files.writeString(s, FFI_SPAWN_READ);
        KofInterpretException ex = assertThrows(KofInterpretException.class,
                () -> driver.interpret(List.of(s), t, new String[0]),
                "Script deve recusar o extern mesmo dentro de spawn (#667)");
        assertTrue(ex.errorDiagnostics().stream().anyMatch(d -> "FFI001".equals(d.code())),
                "esperado FFI001, foi " + ex.errorDiagnostics());
    }

    @Test
    void concurrentFfiBufferWriteParentAndSpawnIsMem020(@TempDir Path t) throws IOException {
        compileExpectMem020(t, "mem020-parent", MEM020_PARENT_AND_SPAWN);
    }

    @Test
    void concurrentFfiBufferWriteTwoSpawnsIsMem020(@TempDir Path t) throws IOException {
        compileExpectMem020(t, "mem020-two-spawns", MEM020_TWO_SPAWNS);
    }

    @Test
    void singleAwaitedFfiBufferWriteIsNotMem020(@TempDir Path t) throws IOException {
        // Controle: um único extern dentro do spawn, com `await` imediato —
        // nenhuma corrida; o passe não pode sobre-reportar.
        Path s = t.resolve("Main-mem020-ok.kf");
        Files.writeString(s, FFI_SPAWN_AWAITED_OK);
        for (Target target : List.of(Target.JVM, Target.NATIVE, Target.JS)) {
            CompilationResult r = driver.compile(s, t.resolve("out-mem020-ok-" + target), target);
            String diags = r.diagnostics().getDiagnostics().toString();
            assertFalse(diags.contains("MEM020"),
                    target + ": não deve haver MEM020 sem escrita concorrente: " + diags);
        }
    }
}
