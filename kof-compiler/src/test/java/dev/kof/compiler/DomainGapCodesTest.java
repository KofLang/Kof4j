package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/**
 * R6 sweep guard: domain namespaces must refuse unsupported targets with a
 * documented compile-time gap code — never a silent stub nor a link break.
 * Each case pins a row of {@code docs/backend-parity.md} (Documented Gaps).
 * Measured on the CLI 17/09; this keeps the matrix honest.
 *
 * The web-gate cases pin the code the corpus promises for each feature
 * (TLS {@code WEB002}, SSE {@code WEB003}, WebSocket {@code WEB004},
 * security middleware {@code WEB006}) — the {@code app.serveDir} /
 * {@code WEB005} drift of §275 happened exactly because the catch-all
 * emitted {@code WEB001} while only the docs knew {@code WEB005}.
 *
 * {@link #everyPinnedGapIsDocumentedInTheParityMatrix()} closes the other
 * direction: a code this guard proves the compiler EMITS must also be in the
 * matrix (R6 — "every domain gap has a code + an entry in the parity
 * matrix"). The ledger is derived from this file's own {@code assertGap}
 * calls, so a new pin cannot be added without documenting it.
 */
class DomainGapCodesTest {
    private final CompilerDriver driver = new CompilerDriver();

    private static final Pattern GAP_CODE = Pattern.compile("\"([A-Z]{2,6}[0-9]{3})\"");

    @Test
    void processRunOnNativeCompiles(@TempDir Path tmp) throws Exception {
        // D-FULL-PARITY-050 linha 1 (RuntimeProcess): process.run tem
        // paridade JVM=NATIVE x86-64; spawn (fatia B, RuntimeProcessSpawn)
        // também no x86-64 — cross/MCU seguem PROC001.
        Path file = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, """
            main() {
                val r = process.run("echo", "hi")
                println(r.stdout)
            }
            """);
        CompilationResult result = driver.compile(file, tmp.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "NATIVE process.run must compile: "
                + result.diagnostics().getDiagnostics());
    }

    @Test
    void processSpawnOnNativeCompiles(@TempDir Path tmp) throws Exception {
        // D-FULL-PARITY-050 linha 1, fatia B (RuntimeProcessSpawn): spawn +
        // handle ops agora têm paridade JVM≡NATIVE x86-64 (golden em
        // ProcessSpawnNativeE2ETest). Cross/MCU continuam PROC001 (abaixo).
        Path file = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, """
            main() {
                val h = process.spawn("echo", "hi")
                println(if (h.alive()) "alive" else "dead")
            }
            """);
        CompilationResult result = driver.compile(file, tmp.resolve("out"), Target.NATIVE);
        assertTrue(result.success(), "NATIVE x86-64 process.spawn must compile: "
                + result.diagnostics().getDiagnostics());
    }

    @Test
    void processSpawnOnCrossIsProc001(@TempDir Path tmp) throws Exception {
        // A fatia de handles (tabela + fork/exec/pipe) é x86-64-only por ora;
        // riscv64/aarch64 mantêm o gap honesto (R6).
        assertGap(tmp, Target.NATIVE_RISCV64, "PROC001", """
            main() {
                val h = process.spawn("echo", "hi")
                println(if (h.alive()) "alive" else "dead")
            }
            """);
    }

    @Test
    void processSpawnOnJsHasNoGap(@TempDir Path tmp) throws Exception {
        // JS face landed 19/09 (KofJsProcessBridge host binding, F10 parity):
        // process.spawn must compile on JS now — the E2E parity lives in
        // ProcessSpawnE2ETest. Native x86-64 landed 26/09 (slice B); cross/MCU
        // keep the PROC001 pin above.
        Path file = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, """
            main() {
                val h = process.spawn("echo", "hi")
                println(if (h.alive()) "alive" else "dead")
            }
            """);
        CompilationResult result = driver.compile(file, tmp.resolve("out"), Target.JS);
        assertTrue(result.success(), "JS process.spawn must compile: "
                + result.diagnostics().getDiagnostics());
    }

    @Test
    void processSpawnOnJvmHasNoGap(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, """
            main() {
                val h = process.spawn("echo", "hi")
                println(if (h.alive()) "alive" else "dead")
            }
            """);
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
            Files.writeString(f, """
                main() {
                    val f = File("/tmp/kof-io-probe")
                    println(f.copyTo("/tmp/kof-io-probe2"))
                }
                """);
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
            Files.writeString(f, """
                main() {
                    val f = File("/tmp/kof-io-probe")
                    println(f.exists())
                    println(f.isFile())
                    println(f.isDirectory())
                    println(f.writeText("x"))
                    println(f.readText())
                    println(f.appendText("y"))
                }
                """);
            CompilationResult r = driver.compile(f, tmp.resolve("out-" + t + "-" + System.nanoTime()), t);
            assertTrue(r.success(), t + " io stat faces must compile on cross: "
                    + r.diagnostics().getDiagnostics());
        }
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
    void ioAndWebT1OnX86AndJsHaveNoGap(@TempDir Path tmp) throws Exception {
        Path x86 = tmp.resolve("Main-x86-" + System.nanoTime() + ".kf");
        Files.writeString(x86, """
            main() {
                val f = File("/tmp/kof-io-probe")
                println(f.exists())
                val app = web.app()
                app.listen(8080)
            }
            """);
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
        // §424: matches/replaceAll/replaceFirst/compareToIgnoreCase are JVM-only;
        // JS/Native refuse honestly instead of a TypeError/link-fail.
        // D-FULL-PARITY-050 row 11 ported toCharArray to all targets (left the gate).
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
                "main() { println(\"a1b\".replaceFirst(\"b\", \"x\")) }",
                "main() { println(\"ab\".compareToIgnoreCase(\"AB\")) }"}) {
            assertGap(tmp, Target.JS, "STR003", src);
        }
    }

    @Test
    void stringIncompleteOnJvmHasNoGap(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, """
            main() {
                println("abc".matches("a.*"))
                println("a1b".replaceAll("b", "x"))
                println("ab".compareToIgnoreCase("AB"))
            }
            """);
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

    /**
     * R6 machine gate (mirrors the R1 boundary gate): every gap code this
     * guard pins — i.e. every code the compiler is proven to emit for a
     * domain namespace — must appear in {@code docs/backend-parity.md}. The
     * ledger is read from this file's own {@code assertGap} calls, so the
     * check cannot rot: adding a pin without a matrix row fails here.
     */
    @Test
    void everyPinnedGapIsDocumentedInTheParityMatrix() throws IOException {
        Path root = repoRoot();
        Set<String> pinned = new LinkedHashSet<>();
        Matcher m = GAP_CODE.matcher(Files.readString(root.resolve(
                "kof-compiler/src/test/java/dev/kof/compiler/DomainGapCodesTest.java")));
        while (m.find()) pinned.add(m.group(1));
        assertFalse(pinned.isEmpty(), "no gap codes found in this guard's assertGap calls");

        String matrix = Files.readString(root.resolve("docs/backend-parity.md"));
        for (String code : pinned) {
            assertTrue(matrix.contains(code),
                    "gap " + code + " is pinned by this guard (the compiler emits it) "
                            + "but has no entry in docs/backend-parity.md (R6)");
        }
    }

    /** Repo root, found by walking up to the parity matrix (same as
     *  {@code ConformanceMatrixDocTest}). */
    private static Path repoRoot() {
        Path p = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && p != null; i++, p = p.getParent()) {
            if (Files.exists(p.resolve("docs/backend-parity.md"))) return p;
        }
        throw new IllegalStateException("backend-parity.md not found from " + p);
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
