package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * R6 sweep guard: domain namespaces must refuse unsupported targets with a
 * documented compile-time gap code — never a silent stub nor a link break.
 * Each case pins a row of {@code docs/backend-parity.md} (Documented Gaps).
 * Measured on the CLI 17/09; this keeps the matrix honest.
 *
 * {@link DomainGapParityMatrixTest#everyPinnedGapIsDocumentedInTheParityMatrix()}
 * closes the other direction: any code this guard proves the compiler EMITS must
 * also be in the matrix (R6); the ledger is derived from this file's own
 * {@code assertGap} calls, so a new pin cannot be added without documenting it.
 */
class DomainGapCodesTest extends DomainGapPrograms {
    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void processRunOnNativeCompiles(@TempDir Path tmp) throws Exception {
        // D-FULL-PARITY-050 linha 1 (RuntimeProcess): process.run tem
        // paridade JVM=NATIVE x86-64; spawn (fatia B, RuntimeProcessSpawn)
        // também no x86-64 — cross/MCU seguem PROC001.
        Path file = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, SRC_PROCESS_RUN_ON_NATIVE_COMPILES);
        CompilationResult result = driver.compile(file, tmp.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "NATIVE process.run must compile: "
                + result.diagnostics().getDiagnostics());
    }

    @Test
    void processSpawnOnNativeCompiles(@TempDir Path tmp) throws Exception {
        // D-FULL-PARITY-050 linha 1, fatia B (RuntimeProcessSpawn): spawn +
        // handle ops agora têm paridade JVM≡NATIVE x86-64 (golden em
        // ProcessSpawnNativeE2ETest). Cross (fatia D) compila sem gap; só o
        // MCU continua PROC001.
        Path file = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, SRC_PROCESS_SPAWN_ON_NATIVE_COMPILES);
        CompilationResult result = driver.compile(file, tmp.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "NATIVE x86-64 process.spawn must compile: "
                + result.diagnostics().getDiagnostics());
    }

    @Test
    void processSpawnOnCrossHasNoGap(@TempDir Path tmp) throws Exception {
        // D-FULL-PARITY-050 linha 1, fatia D (NativeRiscvAsmProcessSpawn):
        // spawn + handle ops agora emitem de verdade no cross riscv64/aarch64
        // (golden em ProcessSpawnCrossE2ETest). Só o MCU/riscv32 segue PROC001.
        Path file = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, SRC_PROCESS_SPAWN_ON_CROSS_HAS_NO_GAP);
        CompilationResult result = driver.compile(file, tmp.resolve("out"), Target.NATIVE_RISCV64);
        assertTrue(result.success(), "riscv64 process.spawn must compile: "
                + result.diagnostics().getDiagnostics());
    }

    @Test
    void processSpawnOnJsHasNoGap(@TempDir Path tmp) throws Exception {
        // JS face landed 19/09 (KofJsProcessBridge host binding, F10 parity):
        // process.spawn must compile on JS now — the E2E parity lives in
        // ProcessSpawnE2ETest. Native x86-64 landed 26/09 (slice B); cross/MCU
        // keep the PROC001 pin above.
        Path file = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, SRC_PROCESS_SPAWN_ON_JS_HAS_NO_GAP);
        CompilationResult result = driver.compile(file, tmp.resolve("out"), Target.JS);
        assertTrue(result.success(), "JS process.spawn must compile: "
                + result.diagnostics().getDiagnostics());
    }

    @Test
    void processSpawnOnJvmHasNoGap(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, SRC_PROCESS_SPAWN_ON_JVM_HAS_NO_GAP);
        CompilationResult result = driver.compile(file, tmp.resolve("out"), Target.JVM);
        assertTrue(result.success(), "JVM process.spawn must compile: "
                + result.diagnostics().getDiagnostics());
    }

    @Test
    void chacha20OnNativeIsSecn002(@TempDir Path tmp) throws Exception {
        assertGap(tmp, Target.NATIVE, "SECN002", """
            main() {
                println(crypto.encryptChacha20("k", "m"))
            }
            """);
    }

    @Test
    void cookiesOnNativeIsSecn006(@TempDir Path tmp) throws Exception {
        assertGap(tmp, Target.NATIVE, "SECN006", """
            main() {
                println(security.cookieSet("a", "b"))
            }
            """);
    }

    @Test
    void securityOnRiscvIsSecn000(@TempDir Path tmp) throws Exception {
        assertGap(tmp, Target.NATIVE_RISCV64, "SECN000", """
            main() {
                println(crypto.sha256("x"))
            }
            """);
    }

    @Test
    void configOnCrossHasNoGap(@TempDir Path tmp) throws Exception {
        // D-FULL-PARITY-050 row 9 (26/09): the cross kof_config_* runtime is
        // real (NativeRiscvAsmConfig1/2/3) — the old CONF001 gate is closed.
        // Execution parity is proven in KofConfigCrossTest (riscv64+aarch64).
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            Path file = tmp.resolve("Main-" + t + "-" + System.nanoTime() + ".kf");
            Files.writeString(file, """
                main() {
                    println(config.str("server.port", "8080"))
                }
                """);
            CompilationResult r = driver.compile(file, tmp.resolve("out-" + t), t);
            assertTrue(r.success(), t + " config.str must compile (own cross asm): "
                    + r.diagnostics().getDiagnostics());
        }
    }

    @Test
    void configOnJsAndX86HasNoGap(@TempDir Path tmp) throws Exception {
        Path jsv = tmp.resolve("Main-js-" + System.nanoTime() + ".kf");
        Files.writeString(jsv, """
            main() {
                println(config.str("server.port", "8080"))
            }
            """);
        CompilationResult js = driver.compile(jsv, tmp.resolve("out-js"), Target.JS);
        assertTrue(js.success(), "JS config.str must compile (kof_platform): "
                + js.diagnostics().getDiagnostics());
        Path x86 = tmp.resolve("Main-x86-" + System.nanoTime() + ".kf");
        Files.writeString(x86, """
            main() {
                println(config.str("server.port", "8080"))
            }
            """);
        CompilationResult nat = driver.compile(x86, tmp.resolve("out-x86"), Target.NATIVE);
        assertTrue(nat.success(), "Native x86_64 config.str must compile (own asm): "
                + nat.diagnostics().getDiagnostics());
    }

    @Test
    void collectOnJsHasNoGap(@TempDir Path tmp) throws Exception {
        // §426 (improved 25/09): the JS runtime now EXPORTS kofGcCollectNow
        // (host GC request via KofJsRunner kof_platform.gcCollect); the old
        // compile-time TIME004 gate is lifted. Execution is proven in
        // KofTimeE2ETest#collectJsRunsOnHost.
        Path file = tmp.resolve("Main-js-" + System.nanoTime() + ".kf");
        Files.writeString(file, """
            main() {
                time.collect()
            }
            """);
        CompilationResult r = driver.compile(file, tmp.resolve("out-js-" + System.nanoTime()), Target.JS);
        // A TIME004 gate was an error diagnostic, so success() already proves
        // the gate is gone (no need to re-spell the code literal here — the
        // R6 matrix guard reads every gap-code-shaped string in this file as a pin).
        assertTrue(r.success(), "JS time.collect must compile (real host GC face): "
                + r.diagnostics().getDiagnostics());
    }

    @Test
    void collectOnJvmAndX86HasNoGap(@TempDir Path tmp) throws Exception {
        for (Target t : new Target[]{Target.JVM, Target.NATIVE,
                Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            Path file = tmp.resolve("Main-" + t + "-" + System.nanoTime() + ".kf");
            Files.writeString(file, """
                main() {
                    time.collect()
                }
                """);
            CompilationResult r = driver.compile(file, tmp.resolve("out-" + t + "-" + System.nanoTime()), t);
            assertTrue(r.success(), t + " time.collect must compile (real GC runtime): "
                    + r.diagnostics().getDiagnostics());
        }
    }

    @Test
    void ioCopyOnCrossHasNoGap(@TempDir Path tmp) throws Exception {
        // D-FULL-PARITY-050 row 13: the whole kof.io face set (incl. copyTo) now
        // compiles AND runs on the riscv64/aarch64 cross (proof of execution in
        // NativeIoCopyCrossTest); here we pin that the compiler emits no gap.
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            Path f = tmp.resolve("Main-" + t + "-" + System.nanoTime() + ".kf");
            Files.writeString(f, SRC_IO_COPY_ON_CROSS_HAS_NO_GAP);
            CompilationResult r = driver.compile(f, tmp.resolve("out-" + t + "-" + System.nanoTime()), t);
            assertTrue(r.success(), t + " copyTo must compile on cross: "
                    + r.diagnostics().getDiagnostics());
        }
    }

    @Test
    void ioStatOnCrossHasNoGap(@TempDir Path tmp) throws Exception {
        // D-FULL-PARITY-050 row 13: exists()/isFile()/isDirectory() now compile
        // AND run on the riscv64/aarch64 cross (proof of execution in
        // NativeIoStatCrossTest); here we pin that the compiler emits no gap.
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            Path f = tmp.resolve("Main-" + t + "-" + System.nanoTime() + ".kf");
            Files.writeString(f, SRC_IO_STAT_ON_CROSS_HAS_NO_GAP);
            CompilationResult r = driver.compile(f, tmp.resolve("out-" + t + "-" + System.nanoTime()), t);
            assertTrue(r.success(), t + " io stat faces must compile on cross: "
                    + r.diagnostics().getDiagnostics());
        }
    }

    @Test
    void ioReadRangeOnJsIsIojs001(@TempDir Path tmp) throws Exception {
        // D-KOF-FILE-GO slice 2: the JS guest runtime (kof-runtime-io.mjs) has
        // no kofIoReadRange export — emitting the call compiled and died at
        // runtime with a SyntaxError (R6). The lowering now refuses at compile
        // time with IOJS001; Script keeps the real interpreter semantics.
        assertGap(tmp, Target.JS, "IOJS001", SRC_IO_READ_RANGE_ON_JS_IS_IOJS001);
    }

    @Test
    void imageDecodeOnJsIsImg001(@TempDir Path tmp) throws Exception {
        // §34.2: kof.image.decode is JVM-only (javax.imageio) — others IMG001.
        assertGap(tmp, Target.JS, "IMG001", """
            main() { println(image.decode("/tmp/kof-image-probe.jpg")) }
            """);
    }

    @Test
    void ioCopyOnJsIsIojs001(@TempDir Path tmp) throws Exception {
        // Same missing-binding face: copyTo/moveTo/modifiedTime/isSymlink have
        // no JS host binding either.
        assertGap(tmp, Target.JS, "IOJS001", """
            main() {
                val f = File("/tmp/kof-io-probe")
                println(f.copyTo("/tmp/kof-io-probe2"))
            }
            """);
    }

    @Test
    void webT1OnCrossIsNat007(@TempDir Path tmp) throws Exception {
        // §427: NativeWebRuntime (listen/route) is emitted on the x86_64 path
        // only; the cross has no kof_web_* symbols.
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            assertGap(tmp, t, "NAT007", """
                main() {
                    val app = web.app()
                    app.listen(8080)
                }
                """);
        }
    }

    @Test
    void zipPrimitiveElementOnNativeIsNat008(@TempDir Path tmp) throws Exception {
        // D-MULTIPARADIGMA-ZIP-NATIVE (30/09): a native list of a concrete
        // primitive element stores the value RAW, so viewing it through the
        // injected `zipPairs<A,B>` (bare type variable → erasure "reference")
        // emitted `kof_unbox_*` over the raw int → SIGSEGV (rc=139). The
        // lowering now refuses every native target honestly with NAT008.
        for (Target t : new Target[]{Target.NATIVE, Target.NATIVE_RISCV64,
                Target.NATIVE_AARCH64}) {
            assertGap(tmp, t, "NAT008", """
                main() {
                    val z = listOf(1, 2, 3).zip(listOf("a", "b"))
                    println(z.size)
                }
                """);
        }
    }

    @Test
    void zipReferenceElementOnNativeHasNoGap(@TempDir Path tmp) throws Exception {
        // Control for NAT008: a reference element is a real pointer and crosses
        // the generic boundary safely (execution proof in ListZipE2ETest#zipNativeX86).
        Path file = tmp.resolve("Main-zipref-" + System.nanoTime() + ".kf");
        Files.writeString(file, """
            main() {
                val z = listOf("a", "b").zip(listOf("x", "y", "z"))
                println(z.size)
            }
            """);
        CompilationResult r = driver.compile(file, tmp.resolve("out-zipref"), Target.NATIVE_RISCV64);
        for (var d : r.diagnostics().getDiagnostics()) {
            assertFalse("NAT008".equals(d.code()),
                    "reference-element zip must NOT be refused with NAT008: " + d);
        }
    }

    @Test
    void ioAndWebT1OnX86AndJsHaveNoGap(@TempDir Path tmp) throws Exception {
        Path x86 = tmp.resolve("Main-x86-" + System.nanoTime() + ".kf");
        Files.writeString(x86, SRC_IO_AND_WEB_T1_ON_X86_AND_JS_HAVE_NO_GAP);
        CompilationResult nat = driver.compile(x86, tmp.resolve("out-x86"), Target.NATIVE);
        assertTrue(nat.success(), "Native x86_64 io + web T1 must compile: "
                + nat.diagnostics().getDiagnostics());
        Path js = tmp.resolve("Main-js-" + System.nanoTime() + ".kf");
        Files.writeString(js, """
            main() {
                val f = File("/tmp/kof-io-probe")
                println(f.exists())
            }
            """);
        CompilationResult jsRes = driver.compile(js, tmp.resolve("out-js"), Target.JS);
        assertTrue(jsRes.success(), "JS kof.io must compile: "
                + jsRes.diagnostics().getDiagnostics());
    }

    @Test
    void stringIncompleteMethodsOnJsAndNativeAreStr003(@TempDir Path tmp) throws Exception {
        // §424: the regex trio (matches/replaceAll/replaceFirst) is JVM-only
        // (deferred to 1.0); JS/Native refuse honestly instead of a
        // TypeError/link-fail. D-FULL-PARITY-050 row 11 ported toCharArray and
        // compareToIgnoreCase to all targets (both left the gate).
        for (Target t : new Target[]{Target.JS, Target.NATIVE,
                Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            assertGap(tmp, t, "STR003", """
                main() {
                    println("abc".matches("a.*"))
                }
                """);
        }
        for (String src : new String[]{
                "main() { println(\"a1b\".replaceAll(\"b\", \"x\")) }",
                "main() { println(\"a1b\".replaceFirst(\"b\", \"x\")) }"}) {
            assertGap(tmp, Target.JS, "STR003", src);
        }
    }

    @Test
    void stringIncompleteOnJvmHasNoGap(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, SRC_STRING_INCOMPLETE_ON_JVM_HAS_NO_GAP);
        CompilationResult r = driver.compile(file, tmp.resolve("out-jvm"), Target.JVM);
        assertTrue(r.success(), "JVM implements all five String methods: "
                + r.diagnostics().getDiagnostics());
    }

    @Test
    void exportSpansOnNativeIsObs003(@TempDir Path tmp) throws Exception {
        assertGap(tmp, Target.NATIVE, "OBS003", """
            main() {
                println(observability.exportSpans())
            }
            """);
    }

    @Test
    void webTlsOnNonJvmIsWeb002(@TempDir Path tmp) throws Exception {
        assertGap(tmp, Target.JS, "WEB002", """
            main() {
                val app = web.app()
                app.listenSecure(8443)
            }
            """);
        assertGap(tmp, Target.NATIVE, "WEB002", """
            main() {
                val app = web.app()
                app.listenSecure(8443)
            }
            """);
    }

    @Test
    void webSseOnNativeIsWeb003(@TempDir Path tmp) throws Exception {
        assertGap(tmp, Target.NATIVE, "WEB003", """
            main() {
                val app = web.app()
                app.sse("/events") { return "x" }
            }
            """);
    }

    @Test
    void webWsOnNonJvmIsWeb004(@TempDir Path tmp) throws Exception {
        assertGap(tmp, Target.JS, "WEB004", """
            main() {
                val app = web.app()
                app.ws("/chat") { return "x" }
            }
            """);
        assertGap(tmp, Target.NATIVE, "WEB004", """
            main() {
                val app = web.app()
                app.ws("/chat") { return "x" }
            }
            """);
    }

    @Test
    void webSecurityMiddlewareOnNonJvmIsWeb006(@TempDir Path tmp) throws Exception {
        assertGap(tmp, Target.JS, "WEB006", """
            main() {
                val app = web.app()
                app.security()
            }
            """);
        assertGap(tmp, Target.NATIVE, "WEB006", """
            main() {
                val app = web.app()
                app.security()
            }
            """);
    }

    @Test
    void processRunOnJvmHasNoGap(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, """
            main() {
                val r = process.run("echo", "hi")
                println(r.stdout)
            }
            """);
        CompilationResult result = driver.compile(file, tmp.resolve("out"), Target.JVM);
        assertTrue(result.success(), "JVM process.run must compile: "
                + result.diagnostics().getDiagnostics());
    }

    /**
     * Android reuses {@code JvmBackend} — the {@code kof.db}/{@code kof.orm}
     * over-gating of §278 was closed 20/09 by D-DB-GAPS DB-2 ("android is
     * JVM", byte-identical emission), so db now compiles clean on Android
     * (asserted right here AND byte-pinned in {@code KofDbE2ETest}). The
     * security half was closed 23/09 (D-TECHDEBT-23/09). The gpu half is
     * closed here: the over-gating was removed (the JVM runtime needs FFM,
     * absent on ART, so Android gets the FFM-free stub at runtime — the
     * front/IR is unchanged and the {@code Main.class} stays byte-identical;
     * see {@code GpuAndroidE2ETest}). No false gap hides a parity error.
     */
    @Test
    void androidCompilesDbCryptoAndGpuLikeJvm(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("Db-" + System.nanoTime() + ".kf");
        Files.writeString(file, """
            main() {
                val c = db.connect("sqlite::memory:")
                println(c)
            }
            """);
        CompilationResult r = driver.compile(file, tmp.resolve("out-db"), Target.ANDROID);
        assertTrue(r.success(), "android compila kof.db desde DB-2 (20/09): "
                + r.diagnostics().getDiagnostics());
        // §278 (D-TECHDEBT-23/09): kof.security roda no Android (reusa o JVM;
        // shims JCA/java.util) — SECN003 nao mais no Android.
        Path crypto = tmp.resolve("Crypto-" + System.nanoTime() + ".kf");
        Files.writeString(crypto, """
            main() {
                println(crypto.sha512("x"))
            }
            """);
        CompilationResult rc = driver.compile(crypto, tmp.resolve("out-crypto"), Target.ANDROID);
        assertTrue(rc.success(), "android compila kof.security desde §278: "
                + rc.diagnostics().getDiagnostics());
        // §278 face gpu: anda junto com o JVM (mesmo backend/IR); o runtime
        // Android e o stub sem FFM (GPU001 nao e mais emitido no Android).
        Path gpu = tmp.resolve("Gpu-" + System.nanoTime() + ".kf");
        Files.writeString(gpu, """
            main() {
                println(gpu.available())
            }
            """);
        CompilationResult rg = driver.compile(gpu, tmp.resolve("out-gpu"), Target.ANDROID);
        assertTrue(rg.success(), "android compila kof.gpu desde §278: "
                + rg.diagnostics().getDiagnostics());
    }

    @Test
    void externalStaticFieldOnNonJvmBackedTargetsIsInterop003(@TempDir Path tmp) throws Exception {
        assertGap(tmp, Target.JS, "INTEROP003", """
            main() {
                println(Integer.MAX_VALUE)
            }
            """);
        assertGap(tmp, Target.NATIVE, "INTEROP003", """
            main() {
                println(Integer.MAX_VALUE)
            }
            """);
    }

    private void assertGap(Path tmp, Target target, String code, String source)
            throws java.io.IOException {
        Path file = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        CompilationResult result = driver.compile(file, tmp.resolve("out-" + System.nanoTime()), target);
        assertFalse(result.success(), target + " should refuse the call (" + code + ")");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains(code),
                "expected " + code + " for " + target + ", got: " + diags);
    }
}
