package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FFI end-to-end (TIER 2.1.4): extern binds to a real .so (libc) via FFM and
 * is callable from Kof. Requires a full JDK on Linux (libc.so.6).
 */
class FfiE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void nativeUnboundAbiEmitsFfi001(@TempDir Path dir) throws IOException {
        // #431/§61 (fatia 1): a ABI ESCALAR agora binda no Native por link direto
        // (`sym@PLT` na .so ligada — prova de execução em FfiNativeE2ETest, com a
        // forma InitWindow(Int,Int,String):void incluída). O gap honesto segue
        // pinado na forma que CONTINUA não-bindável no Native: `extern` SEM
        // `library()` (nada a linkar) — `String[]` passou a binder em 30/09
        // (D-MEM-FFI-CROSS-FULL face 2, FfiNativeStringArrayE2ETest). Nunca stub
        // silencioso (R6).
        Path src = dir.resolve("ffi-native.kf");
        Files.writeString(src, """
                extern sum(Int x): Int

                main() {
                    println("hi")
                }
                """);

        CompilationResult result = driver.compile(src, dir.resolve("out-native"), Target.NATIVE);
        assertFalse(result.success(), "Native target must not silently emit a call it cannot lower");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("FFI001"), "expected FFI001 on Native, got: " + diags);
    }

    @Test
    void jsUnboundAbiEmitsFfi002(@TempDir Path dir) throws IOException {
        // R3 fatia 3.6: o target JS agora BIND a ABI escalar (runner GraalJS tem
        // java.lang.foreign no host) — ver ffiJs* abaixo — e, desde 21/09, também
        // `T[]` escalar (copy-in, D6-2) e `Buffer(U8)` INOUT (D6-3). O que ainda
        // está fora do v1 no JS (array de ponteiros `String[]`, struct return,
        // Handle) segue FFI002 honesto na compilação (D6/3.8 pendentes) — nunca
        // stub silencioso (R6).
        Path src = dir.resolve("ffi-js-strarray.kf");
        Files.writeString(src, """
                extern "libc.so.6" sum(String[] xs): Int

                main() {
                    println("hi")
                }
                """);

        CompilationResult result = driver.compile(src, dir.resolve("out-js"), Target.JS);
        assertFalse(result.success(), "JS target must not silently drop an unbound extern ABI");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("FFI002"), "expected FFI002 on an unbound JS extern, got: " + diags);
    }

    // ── R3 fatia 3.6: paridade JS (host GraalJS) byte-a-byte com a JVM ──
    // Mesmo fonte `.kf`, mesma saída nos dois alvos (o `KofJsFfiBridge` reproduz o
    // downcall do `kof_ffi` do target JVM). O runner usa java.lang.foreign no host
    // (JDK >=22, final). Browser sem host: degrada em runtime via kof_platform Proxy
    // (R7) — não testável aqui, mesmo padrão do kof.io/console.

    @Test
    void ffiJsAbsIntToInt(@TempDir Path dir) throws Exception {
        assertJvmJsParity(dir, "abs", """
                extern "libc.so.6" abs(Int x): Int

                main() {
                    println(abs(-5))
                }
                """, "5");
    }

    @Test
    void ffiJsAtoiStringToInt(@TempDir Path dir) throws Exception {
        assertJvmJsParity(dir, "atoi", """
                extern "libc.so.6" atoi(String s): Int

                main() {
                    println(atoi("42"))
                }
                """, "42");
    }

    @Test
    void ffiJsSqrtDoubleToDouble(@TempDir Path dir) throws Exception {
        assertJvmJsParity(dir, "sqrt", """
                extern "libm.so.6" sqrt(Double x): Double

                main() {
                    println(sqrt(9.0))
                }
                """, "3.0");
    }

    @Test
    void ffiJsPowTwoDoubleArgs(@TempDir Path dir) throws Exception {
        assertJvmJsParity(dir, "pow", """
                extern "libm.so.6" pow(Double x, Double y): Double

                main() {
                    println(pow(2.0, 10.0))
                }
                """, "1024.0");
    }

    @Test
    void ffiJsLongReturnAndParam(@TempDir Path dir) throws Exception {
        assertJvmJsParity(dir, "long", """
                extern "libc.so.6" atol(String s): Long
                extern "libc.so.6" labs(Long x): Long

                main() {
                    println(labs(atol("-9")))
                }
                """, "9");
    }

    @Test
    void ffiJsStrstrTwoStringsToString(@TempDir Path dir) throws Exception {
        assertJvmJsParity(dir, "strstr", """
                extern "libc.so.6" strstr(String hay, String needle): String

                main() {
                    println(strstr("hello world", "wor"))
                }
                """, "world");
    }

    @Test
    void ffiJsSrandVoid(@TempDir Path dir) throws Exception {
        assertJvmJsParity(dir, "srand", """
                extern "libc.so.6" srand(Int x)

                main() {
                    srand(42)
                    println("ok")
                }
                """, "ok");
    }

    private void assertJvmJsParity(Path dir, String base, String kof, String expected) throws Exception {
        Path jvmSrc = dir.resolve(base + "-jvm.kf");
        Files.writeString(jvmSrc, kof);
        Path jvmOut = dir.resolve(base + "-out-jvm");
        CompilationResult rj = driver.compile(jvmSrc, jvmOut, Target.JVM);
        assertTrue(rj.success(), "JVM compile " + base + ": " + rj.diagnostics().getDiagnostics());
        String jvm = runJvm(jvmOut);
        assertEquals(expected, jvm, "JVM golden " + base);

        Path jsSrc = dir.resolve(base + "-js.kf");
        Files.writeString(jsSrc, kof);
        Path jsOut = dir.resolve(base + "-out-js");
        CompilationResult rjs = driver.compile(jsSrc, jsOut, Target.JS);
        assertTrue(rjs.success(), "JS compile " + base + ": " + rjs.diagnostics().getDiagnostics());
        String js = runJs(jsOut);
        assertEquals(expected, js, "JS parity " + base);
        assertEquals(jvm, js, "JVM==JS byte-for-byte parity " + base);
    }

    private String runJs(Path outDir) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        assertEquals(0, ec, "JS exit code, output: " + out);
        return out.toString(java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
    }

    @Test
    void libcAtoiStringToIntBothTargets(@TempDir Path dir) throws Exception {
        String kof = """
                extern "libc.so.6" atoi(String s): Int

                main() {
                    println(atoi("42"))
                }
                """;

        // JVM
        Path jvmSrc = dir.resolve("atoi-jvm.kf");
        Files.writeString(jvmSrc, kof);
        CompilationResult rj = driver.compile(jvmSrc, dir.resolve("out-jvm"), Target.JVM);
        assertTrue(rj.success(), "JVM compile: " + rj.diagnostics().getDiagnostics());
        assertEquals("42", runJava(dir.resolve("out-jvm")));

        // Native (#431/§61 fatia 1): String→char* + Int via link direto — roda.
        Path natSrc = dir.resolve("atoi-native.kf");
        Files.writeString(natSrc, kof);
        CompilationResult rn = driver.compile(natSrc, dir.resolve("out-native"), Target.NATIVE);
        assertTrue(rn.success(), "Native atoi(String) binds since #431: "
                + rn.diagnostics().getDiagnostics());
        assertEquals("42", runNative(dir.resolve("out-native"), dir),
                "atoi(\"42\") via libc no binário nativo");
    }

    private String runNative(Path outDir, Path cwd) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(outDir.resolve("Default/Main").toString());
        pb.directory(cwd.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        try {
            assertEquals(0, p.waitFor(), "Native exit code, output: " + output);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
        return output;
    }

    @Test
    void libcFabsDoubleToDouble(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("ffi-double.kf");
        Files.writeString(src, """
                extern "libm.so.6" sqrt(Double x): Double

                main() {
                    println(sqrt(9.0))
                }
                """);

        Path out = dir.resolve("out-double");
        CompilationResult result = driver.compile(src, out, Target.JVM);
        assertTrue(result.success(), "JVM double extern must compile: "
                + result.diagnostics().getDiagnostics());
        assertEquals("3.0", runJava(out));
    }

    @Test
    void libcAbsEndToEnd(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("ffi.kf");
        Files.writeString(src, """
                extern "libc.so.6" abs(Int x): Int

                main() {
                    println(abs(-5))
                }
                """);

        Path out = dir.resolve("out");
        CompilationResult result = driver.compile(src, out, Target.JVM);
        assertTrue(result.success(), "compile must succeed (extern bound on JVM): "
                + result.diagnostics().getDiagnostics());

        String output = runJvm(out);
        assertEquals("5", output, "abs(-5) must return 5 via libc");
    }

    @Test
    void scalarHelpersRepeatStableUnderConfinedArena(@TempDir Path dir) throws IOException {
        // D6-5 (fatia 2, 21/09): kof_ffi_i/si/dd passam a usar arena CONFINADA por
        // chamada, fechada no finally (antes: `Arena.global()` em i/dd = handle da
        // biblioteca vazando, e si sem close). A repetição prova que criar/fechar a
        // arena por chamada é estável e o valor é idempotente.
        Path src = dir.resolve("ffi-repeat.kf");
        Files.writeString(src, """
                extern "libc.so.6" abs(Int x): Int
                extern "libc.so.6" atoi(String s): Int
                extern "libm.so.6" sqrt(Double x): Double

                main() {
                    var total = 0
                    var dtot = 0.0
                    var i = 0
                    while (i < 300) {
                        total = total + abs(-5) + atoi("7")
                        dtot = dtot + sqrt(9.0)
                        i = i + 1
                    }
                    println(total)
                    println(dtot)
                }
                """);

        Path out = dir.resolve("out-repeat");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "scalar externs must compile: "
                + r.diagnostics().getDiagnostics());
        assertEquals("3600\n900.0", runJvm(out),
                "300x (abs+atoi)=3600 e 300x sqrt(9.0)=900.0, estável sob arena confinada");
    }

    @Test
    void libcSrandDefaultVoidBindsJVM(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("ffi.kf");
        Files.writeString(src, """
                extern "libc.so.6" srand(Int x)

                main() {
                    srand(42)
                    println("ok")
                }
                """);
        Path out = dir.resolve("out");
        CompilationResult result = driver.compile(src, out, Target.JVM);
        assertTrue(result.success(), "default-void extern must bind on JVM (R3 3.2): "
                + result.diagnostics().getDiagnostics());
        assertEquals("ok", runJvm(out), "srand(Int) returns void via kof_ffi_void");
    }

    @Test
    void ffiPowTwoDoubleArgsJVM(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("ffi.kf");
        Files.writeString(src, """
                extern "libm.so.6" pow(Double x, Double y): Double

                main() {
                    println(pow(2.0, 10.0))
                }
                """);
        Path out = dir.resolve("out");
        CompilationResult result = driver.compile(src, out, Target.JVM);
        assertTrue(result.success(), "2-arg double extern must compile: "
                + result.diagnostics().getDiagnostics());
        assertEquals("1024.0", runJvm(out), "pow(2.0,10.0) via libm (multi-arity)");
    }

    @Test
    void ffiStrstrTwoStringsToStringJVM(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("ffi.kf");
        Files.writeString(src, """
                extern "libc.so.6" strstr(String hay, String needle): String

                main() {
                    println(strstr("hello world", "wor"))
                }
                """);
        Path out = dir.resolve("out");
        CompilationResult result = driver.compile(src, out, Target.JVM);
        assertTrue(result.success(), "String-return extern must compile on JVM: "
                + result.diagnostics().getDiagnostics());
        assertEquals("world", runJvm(out), "strstr char*→String with 2 String args");
    }

    @Test
    void ffiLongReturnAndParamJVM(@TempDir Path dir) throws IOException {
        // R3 escalar 'j' (Long) ponta-a-ponta: o Long é PRODUZIDO por atol(String)
        // (evita depender de literal long no Kof) e CONSUMIDO por labs(Long).
        // Prova layout JAVA_LONG + boxing/unboxing Long no caminho downcall genérico.
        Path src = dir.resolve("ffi-long.kf");
        Files.writeString(src, """
                extern "libc.so.6" atol(String s): Long
                extern "libc.so.6" labs(Long x): Long

                main() {
                    println(labs(atol("-9")))
                }
                """);
        Path out = dir.resolve("out");
        CompilationResult result = driver.compile(src, out, Target.JVM);
        assertTrue(result.success(), "Long return+param extern must bind on JVM (R3 'j'): "
                + result.diagnostics().getDiagnostics());
        assertEquals("9", runJvm(out), "labs(atol(\"-9\")) via libc (Long in/out)");
    }

    private String runJava(Path outDir) throws IOException {
        return runJvm(outDir);
    }

    private String runJvm(Path outDir) throws IOException {
        try {
            String javaHome = System.getProperty("java.home");
            ProcessBuilder pb = new ProcessBuilder(
                    Path.of(javaHome, "bin", "java").toString(),
                    "--enable-native-access=ALL-UNNAMED",
                    "-cp", outDir.toString(),
                    "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "JVM exit code, output: " + output);
            return output;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }
}