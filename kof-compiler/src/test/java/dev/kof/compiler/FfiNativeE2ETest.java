package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * #431 (Native FFI, fatia 1 — x86-64): o `extern` escalar AGORA BINDA no alvo
 * NATIVE por link direto (a {@code library()} entra no ld; o call-site emite o
 * marshaling SysV e chama {@code sym@PLT} — o caminho de saída PROVO do §61,
 * mesma mecânica do consumidor SQLite/DB001). Zero dlopen em runtime, zero
 * wrapper à mão: a forma do raylib — {@code InitWindow(Int, Int, String): void}
 * — compila, linka e roda.
 *
 * <p>Ouro dos testes: golden literal EXATO (aritmética IEEE determinística nas
 * fixtures C) + paridade byte-a-byte JVM↔Native no MESMO fonte .kf (o JVM é o
 * oráculo vivo — os dois caminhos rodam no teste). Gaps honestos travados na
 * LINHA DA DECLARAÇÃO (§350): List/struct → FFI001, callback → FFI001 no
 * Native (upcall nativo não existe), `extern` sem biblioteca → FFI001 no
 * Native (nada a linkar). Aridade/tipo do call site → SEM013/SEM014 (#266(c)
 * da família: o compile-time pega o que o runtime pagaria caro).
 */
class FfiNativeE2ETest {

    private static final String C_SRC = """
            #include <stdio.h>
            #include <string.h>
            #include <stdbool.h>
            #include <locale.h>
            #define FIX_LOCALE setlocale(LC_NUMERIC, "C");
            void InitWindow(int w, int h, const char* title) {
                fprintf(stdout, "InitWindow %d %d [%s] first=%d len=%d\\n",
                        w, h, title, (int)title[0], (int)strlen(title));
                fflush(stdout);
            }
            int probe_noargs(void) { return 77; }
            int probe4(int a, int b, int c, int d) { return a*1000 + b*100 + c*10 + d; }
            int probe9i(int a, int b, int c, int d, int e, int f, int g, int h, int i) {
                return a+b+c+d+e+f+g+h+i;
            }
            double probe3d(double x, double y, double z) { return x + y + z; }
            float probe2f(float a, float b) { return a + b; }
            int probe_mixed(int i, double d, int j, double e) {
                FIX_LOCALE
                fprintf(stdout, "MIX %d %g %d %g\\n", i, d, j, e);
                fflush(stdout);
                return i + j;
            }
            int probeS(const char* s) { return s ? (int)strlen(s) : -1; }
            char* retstr(void) { static char buf[] = "hello-c"; return buf; }
            int probe_bool(bool x) { return x ? 10 : 20; }
            """;

    private final CompilerDriver driver = new CompilerDriver();

    private static String buildHostLib(Path dir) throws IOException, InterruptedException {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("linux"),
                "FFI nativo usa um .so real (Linux + cc)");
        Path src = dir.resolve("libkoffixture.c");
        Files.writeString(src, C_SRC);
        Path so = dir.resolve("libkoffixture.so");
        String cc = null;
        for (String cand : new String[] {"/usr/bin/cc", "/usr/bin/gcc", "cc", "gcc"}) {
            try {
                Process p = new ProcessBuilder(cand, "--version").redirectErrorStream(true).start();
                p.getInputStream().readAllBytes();
                if (p.waitFor(15, TimeUnit.SECONDS) && p.exitValue() == 0) { cc = cand; break; }
            } catch (Exception ignored) { /* tenta o próximo */ }
        }
        assumeTrue(cc != null, "sem toolchain C (cc/gcc) para a fixture FFI");
        Process p = new ProcessBuilder(cc, "-shared", "-fPIC", "-O2",
                "-o", so.toString(), src.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assumeTrue(p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0, "cc falhou: " + out);
        return so.toString();
    }

    /** Compila p/ NATIVE, executa o binário e devolve stdout+stderr combinados. */
    private String runNative(Path dir, String base, String kof) throws IOException {
        Path src = dir.resolve(base + "-nat.kf");
        Files.writeString(src, kof);
        Path out = dir.resolve("out-" + base + "-nat");
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), () -> "NATIVE compile " + base + " (must bind, #431): "
                + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binary " + bin + " deve existir");
        try {
            Process p = new ProcessBuilder(bin.toString())
                    .directory(dir.toFile()).redirectErrorStream(true).start();
            String o = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n");
            int ec = p.waitFor();
            assertEquals(0, ec, () -> "NATIVE run " + base + " exit code, output: " + o);
            return o;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    /** Oráculo: o MESMO fonte no JVM (FFM) — paridade byte-a-byte. */
    private String runJvmOracle(Path dir, String base, String kof) throws IOException {
        Path src = dir.resolve(base + "-jvm.kf");
        Files.writeString(src, kof);
        Path out = dir.resolve("out-" + base + "-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), () -> "JVM compile " + base + ": " + r.diagnostics().getDiagnostics());
        try {
            Process p = new ProcessBuilder(
                    Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "--enable-native-access=ALL-UNNAMED", "-cp", out.toString(), "Default.Main")
                    .directory(dir.toFile()).redirectErrorStream(true).start();
            String o = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n");
            int ec = p.waitFor();
            assertEquals(0, ec, () -> "JVM run " + base + " exit code, output: " + o);
            return o.trim();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    // ── a forma do raylib (aceitação do #431) ────────────────────────────
    @Test
    void initWindowShapeVoidThreeArgsWithStringCompilesLinksRuns(@TempDir Path dir) throws Exception {
        String lib = buildHostLib(dir);
        String kof = """
                extern "%1$s" InitWindow(Int w, Int h, String title)

                main() {
                    InitWindow(800, 450, "direct Kof FFI")
                    println("done")
                }
                """.formatted(lib);
        String nat = runNative(dir, "initwin", kof);
        assertEquals("InitWindow 800 450 [direct Kof FFI] first=100 len=14\ndone\n", nat,
                "NATIVE golden: multi-arg + cstring (conteúdo) + void return");
        assertEquals(nat, runJvmOracle(dir, "initwin", kof) + "\n",
                "JVM↔Native byte-for-byte parity");
    }

    @Test
    void voidReturnExplicitAnnot(@TempDir Path dir) throws Exception {
        String lib = buildHostLib(dir);
        String kof = """
                extern "%1$s" InitWindow(Int w, Int h, String title): void

                main() {
                    InitWindow(640, 480, "annot-void")
                    println("ok")
                }
                """.formatted(lib);
        String nat = runNative(dir, "voidannot", kof);
        assertEquals("InitWindow 640 480 [annot-void] first=97 len=10\nok\n", nat,
                "explicit `: void` must bind identically");
    }

    // ── aridade: 0, 1 (retro-compat), 4, 9 (spill SysV) ─────────────────
    @Test
    void zeroArgIntReturn(@TempDir Path dir) throws Exception {
        String lib = buildHostLib(dir);
        String kof = """
                extern "%1$s" probe_noargs(): Int

                main() {
                    println(probe_noargs())
                }
                """.formatted(lib);
        assertEquals("77", runNative(dir, "zero", kof).trim());
        assertEquals("77", runJvmOracle(dir, "zero", kof).trim());
    }

    @Test
    void oneArgBackCompatLibcAndLibm(@TempDir Path dir) throws Exception {
        // Retro-compat aditiva: as formas que a JVM já rodava (abs/atoi/sqrt/
        // srand void) agora rodam no Native com o MESMO golden — nada que
        // compilava antes mudou; o que era FFI001 passou a executar.
        String kof = """
                extern "libc.so.6" abs(Int x): Int
                extern "libc.so.6" atoi(String s): Int
                extern "libm.so.6" sqrt(Double x): Double
                extern "libc.so.6" srand(Int x)

                main() {
                    println(abs(-5))
                    println(atoi("42"))
                    println(sqrt(9.0))
                    srand(42)
                    println("ok")
                }
                """;
        String nat = runNative(dir, "back", kof).trim();
        assertEquals("5\n42\n3.0\nok", nat, "NATIVE golden da ABI já ligada na JVM (aditivo)");
        assertEquals(nat, runJvmOracle(dir, "back", kof).trim(), "JVM↔Native parity back-compat");
    }

    @Test
    void fourIntArgs(@TempDir Path dir) throws Exception {
        String lib = buildHostLib(dir);
        String kof = """
                extern "%1$s" probe4(Int a, Int b, Int c, Int d): Int

                main() {
                    println(probe4(1, 2, 3, 4))
                }
                """.formatted(lib);
        assertEquals("1234", runNative(dir, "p4", kof).trim());
        assertEquals("1234", runJvmOracle(dir, "p4", kof).trim());
    }

    @Test
    void nineIntArgsRegisterSpill(@TempDir Path dir) throws Exception {
        String lib = buildHostLib(dir);
        String kof = """
                extern "%1$s" probe9i(Int a, Int b, Int c, Int d, Int e, Int f, Int g, Int h, Int i): Int

                main() {
                    println(probe9i(1, 2, 3, 4, 5, 6, 7, 8, 9))
                }
                """.formatted(lib);
        assertEquals("45", runNative(dir, "p9", kof).trim(),
                "7o+ args vão à pilha SysV (rdi..r9 + stack) — spill correto");
        assertEquals("45", runJvmOracle(dir, "p9", kof).trim());
    }

    @Test
    void mixedIntDoubleArgsPerClassCounting(@TempDir Path dir) throws Exception {
        String lib = buildHostLib(dir);
        String kof = """
                extern "%1$s" probe_mixed(Int i, Double d, Int j, Double e): Int

                main() {
                    println(probe_mixed(10, 1.5, 32, 64.25))
                }
                """.formatted(lib);
        // int-class: i→rdi, j→rsi | float-class: d→xmm0, e→xmm1 (contagem por
        // classe, NÃO posição — o erro clássico). O fixture ecoa ao stderr.
        String nat = runNative(dir, "mix", kof).trim();
        assertEquals("MIX 10 1.5 32 64.25\n42", nat,
                "SysV per-class register assignment (int e float contam separadas)");
        assertEquals(nat, runJvmOracle(dir, "mix", kof).trim(), "JVM↔Native parity mixed classes");
    }

    @Test
    void doubleAndFloatArgs(@TempDir Path dir) throws Exception {
        String lib = buildHostLib(dir);
        String kof = """
                extern "%1$s" probe3d(Double x, Double y, Double z): Double
                extern "%1$s" probe2f(Float a, Float b): Float

                main() {
                    println(probe3d(1.5, 2.25, 3.125))
                    println(probe2f(4.0 as Float, 9.0 as Float) + 0.0)
                }
                """.formatted(lib);
        String nat = runNative(dir, "fp", kof).trim();
        assertEquals("6.875\n13.0", nat, "float-class args: movq/movd → xmm (bits crus do slot)");
        assertEquals(nat, runJvmOracle(dir, "fp", kof).trim(), "JVM↔Native parity fp");
    }

    // ── matriz adversarial ffi-abi: limites numéricos + FP especiais ─────
    @Test
    void numericLimitsIntEdges(@TempDir Path dir) throws Exception {
        // 0, negativo, positivo e os LIMITES de Int (widening Int->Long exato,
        // inclusive MIN que não cabe em Int positivo).
        String kof = """
                extern "libc.so.6" labs(Long x): Long

                main() {
                    println(labs(0))
                    println(labs(-5))
                    println(labs(5))
                    println(labs(-2147483648))
                    println(labs(2147483647))
                }
                """;
        String nat = runNative(dir, "lim", kof).trim();
        assertEquals("0\n5\n5\n2147483648\n2147483647", nat, "NATIVE golden: Int MIN/MAX via widening");
        assertEquals(nat, runJvmOracle(dir, "lim", kof).trim(), "JVM↔Native parity numeric limits");
    }

    @Test
    void fpSpecialsNanInfNegativeZero(@TempDir Path dir) throws Exception {
        // NaN, +Inf, -Inf e -0.0 têm de imprimir byte a byte como o JDK.
        String kof = """
                extern "libm.so.6" sqrt(Double x): Double
                extern "libm.so.6" log(Double x): Double
                extern "libm.so.6" exp(Double x): Double
                extern "libm.so.6" copysign(Double x, Double y): Double

                main() {
                    println(sqrt(-1.0))
                    println(log(0.0))
                    println(exp(1000.0))
                    println(copysign(0.0, -1.0))
                }
                """;
        String nat = runNative(dir, "fps", kof).trim();
        assertEquals("NaN\n-Infinity\nInfinity\n-0.0", nat, "NATIVE golden: FP specials");
        assertEquals(nat, runJvmOracle(dir, "fps", kof).trim(), "JVM↔Native parity FP specials");
    }

    // ── String nas duas pontas ────────────────────────────────────────────
    @Test
    void stringInAsCstringContent(@TempDir Path dir) throws Exception {
        String lib = buildHostLib(dir);
        String kof = """
                extern "%1$s" probeS(String s): Int
                extern "libc.so.6" strlen(String s): Long

                main() {
                    println(probeS("hello kof"))
                    println(strlen("abc"))
                }
                """.formatted(lib);
        String nat = runNative(dir, "str", kof).trim();
        assertEquals("9\n3", nat, "String→char* (UTF-8 NUL no offset 24 do objeto)");
        assertEquals(nat, runJvmOracle(dir, "str", kof).trim(), "JVM↔Native parity string-in");
    }

    @Test
    void stringReturnCopiesAtBoundary(@TempDir Path dir) throws Exception {
        String lib = buildHostLib(dir);
        String kof = """
                extern "%1$s" retstr(): String

                main() {
                    println(retstr())
                }
                """.formatted(lib);
        assertEquals("hello-c", runNative(dir, "rs", kof).trim(),
                "char*→String: cópia no helper kof_ffi_from_cstr (buffer C nunca é free'd)");
        assertEquals("hello-c", runJvmOracle(dir, "rs", kof).trim());
    }

    @Test
    void boolArg(@TempDir Path dir) throws Exception {
        String lib = buildHostLib(dir);
        String kof = """
                extern "%1$s" probe_bool(Bool x): Int

                main() {
                    println(probe_bool(1 < 2))
                    println(probe_bool(2 < 1))
                }
                """.formatted(lib);
        String nat = runNative(dir, "bool", kof).trim();
        assertEquals("10\n20", nat);
        assertEquals(nat, runJvmOracle(dir, "bool", kof).trim(), "JVM↔Native parity bool");
    }

    // ── gaps honestos travados (R6, §350: linha da DECLARAÇÃO) ──────────
    @Test
    void nestedArrayStaysFfi001AtDeclLineNative(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("ffi-nestedarr.kf");
        Files.writeString(src, """
                extern "libc.so.6" sum(Int[][] xs): Int

                main() {
                    println("hi")
                }
                """);
        CompilationResult r = driver.compile(src, dir.resolve("out"), Target.NATIVE);
        assertFalse(r.success(), "Int[][] (array aninhado) segue gap honesto no Native");
        String diags = r.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("FFI001"), "expected FFI001, got: " + diags);
        assertTrue(diags.contains("line=1, column=1"), "diagnostic must point at the DECL line (§350): " + diags);
    }

    @Test
    void callbackArgStaysFfi001Native(@TempDir Path dir) throws IOException {
        // Upcall nativo (C chamando de volta o Kof) não existe no backend —
        // onde a JVM binda (3.4-C2), o Native mantém o gap honesto.
        Path src = dir.resolve("ffi-cb.kf");
        Files.writeString(src, """
                extern "libc.so.6" qsort_cb(Int n, (Int, Int) -> Int cb): Int

                main() {
                    println("hi")
                }
                """);
        CompilationResult r = driver.compile(src, dir.resolve("out"), Target.NATIVE);
        assertFalse(r.success(), "callback arg must not bind on Native");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("FFI001"),
                "expected FFI001 on Native callback: " + r.diagnostics().getDiagnostics());
        // ...e a JVM binda a mesma forma (a divergência é por target, não um bug)
        Path jvmSrc = dir.resolve("ffi-cb-jvm.kf");
        Files.writeString(jvmSrc, """
                extern "libc.so.6" qsort_cb(Int n, (Int, Int) -> Int cb): Int

                main() {
                    println("ok")
                }
                """);
        CompilationResult rj = driver.compile(jvmSrc, dir.resolve("out-jvm"), Target.JVM);
        assertTrue(rj.success(), "JVM binds callbacks (3.4-C2) — pin da divergência: "
                + rj.diagnostics().getDiagnostics());
    }

    @Test
    void noLibraryExternStaysFfi001Native(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("ffi-nolib.kf");
        Files.writeString(src, """
                extern mystery(Int x): Int

                main() {
                    println("hi")
                }
                """);
        CompilationResult r = driver.compile(src, dir.resolve("out"), Target.NATIVE);
        assertFalse(r.success(), "extern sem biblioteca não tem o que linkar no Native");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("FFI001"),
                "expected FFI001: " + r.diagnostics().getDiagnostics());
    }

    // ── validação do call site (SEM013/SEM014 — protegem o codegen nativo) ──
    @Test
    void externCallArityMismatchIsSem013(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("ffi-arity.kf");
        Files.writeString(src, """
                extern "libc.so.6" abs(Int x): Int

                main() {
                    println(abs(1, 2, 3))
                }
                """);
        CompilationResult r = driver.compile(src, dir.resolve("out"), Target.JVM);
        assertFalse(r.success(), "aridade errada num extern não pode passar (stack do Native)");
        String diags = r.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("SEM013"), "expected SEM013, got: " + diags);
    }

    @Test
    void externCallTypeMismatchIsSem014(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("ffi-type.kf");
        Files.writeString(src, """
                extern "libc.so.6" abs(Int x): Int

                main() {
                    println(abs("nope"))
                }
                """);
        CompilationResult r = driver.compile(src, dir.resolve("out"), Target.JVM);
        assertFalse(r.success(), "tipo errado num arg de extern é SEM014");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("SEM014"),
                "expected SEM014: " + r.diagnostics().getDiagnostics());
    }
}
