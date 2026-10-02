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
 * FFI struct ABI (D6-1(A) / slice 3.8b): um `record` Kof de campos escalares
 * atravessa POR VALOR como struct C no target JVM (FFM classifica pela
 * StructLayout derivada do RecordComponent) e no target JS (bridge: param via
 * `__kof_ffi_fields`, retorno via `__kof_ffi_from`). Prova com um shim C real;
 * campos não-escalares seguem FFI001 honesto (R6), e Native fica no seu gap
 * code — nunca um binding parcial silencioso. A fatia 2 cobre o RETORNO struct
 * (registradores p/ struct pequeno e sret p/ struct maior), reconstruído pelo
 * construtor canônico do `record` (JVM por reflexão, JS pelo factory estático).
 */
class FfiStructE2ETest extends FfiStructSupport {



    @Test
    void structParamByValueJvm(@TempDir Path dir) throws Exception {
        String so = compileHostLib(dir);
        Path src = dir.resolve("point.kf");
        Files.writeString(src, """
                record Point(Int x, Int y)

                extern "%s" sumpoint(Point p): Int
                extern "%s" scale(Point p, Double f): Double

                main() {
                    println(sumpoint(Point(3, 4)))
                    println(scale(Point(2, 3), 2.0))
                }
                """.formatted(so, so));

        Path out = dir.resolve("out-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM struct param must bind (3.8b): "
                + r.diagnostics().getDiagnostics());
        assertEquals("7\n10.0", runJvm(out),
                "record-by-value: sumpoint(Point(3,4))=7, scale(Point(2,3),2.0)=10.0");
    }

    @Test
    void structReturnByValueJvm(@TempDir Path dir) throws Exception {
        String so = compileHostLib(dir);
        Path src = dir.resolve("ret.kf");
        Files.writeString(src, """
                record Point(Int x, Int y)
                record Big(Int a, Int b, Int c)
                record Mix(Double d, Int i)

                extern "%s" mkpoint(Int x, Int y): Point
                extern "%s" bigret(Int a, Int b, Int c): Big
                extern "%s" mixret(Double d, Int i): Mix

                main() {
                    val p = mkpoint(3, 4)
                    println(p.x())
                    println(p.y())
                    val g = bigret(10, 20, 30)
                    println(g.a())
                    println(g.b())
                    println(g.c())
                    val m = mixret(2.5, 7)
                    println(m.d())
                    println(m.i())
                }
                """.formatted(so, so, so));

        Path out = dir.resolve("out-jvm-ret");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM struct return must bind (3.8b fatia 2): "
                + r.diagnostics().getDiagnostics());
        assertEquals("3\n4\n10\n20\n30\n2.5\n7", runJvm(out),
                "record reconstructed from the returned struct (register + sret paths)");
    }

    @Test
    void structReturnViaLibcDivJvm(@TempDir Path dir) throws Exception {
        // div() da libc devolve `div_t { int quot; int rem; }` por valor.
        Path src = dir.resolve("divret.kf");
        Files.writeString(src, """
                record Div(Int quot, Int rem)

                extern "libc.so.6" div(Int a, Int b): Div

                main() {
                    val d = div(7, 2)
                    println(d.quot())
                    println(d.rem())
                }
                """);
        Path out = dir.resolve("out-div");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "libc div struct return must bind on JVM: "
                + r.diagnostics().getDiagnostics());
        assertEquals("3\n1", runJvm(out), "div(7,2) = { quot 3, rem 1 }");
    }

    @Test
    void structReturnStringFieldStaysFfi001(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("retstr.kf");
        Files.writeString(src, """
                record Named(String name, Int n)

                extern "libc.so.6" mk(): Named

                main() {
                    println("hi")
                }
                """);
        CompilationResult r = driver.compile(src, dir.resolve("out-retstr"), Target.JVM);
        assertFalse(r.success(), "record with a String field must not bind as a return");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("FFI001"),
                "expected FFI001, got: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void structReturnSretNative(@TempDir Path dir) throws Exception {
        // 3.7 fatia 2b: struct > 16 B (SysV MEMORY) devolvido por valor — o
        // caller passa o ponteiro escondido em rdi e o callee preenche; o
        // backend aloca o objeto Kof antes do call e copia os campos do buffer.
        // ParamMix(Long,Double,Int) = 20 B (l/d/i no layout misto).
        String so = compileHostLib(dir);
        String kof = """
                record ParamMix(Long l, Double d, Int i)

                extern "%s" parammixret(Long l, Double d, Int i): ParamMix

                main() {
                    val pm = parammixret(9, 4.5, 2)
                    println(pm.l())
                    println(pm.d())
                    println(pm.i())
                }
                """.formatted(so);
        String expected = "9\n4.5\n2";

        Path jvmSrc = dir.resolve("sret-jvm.kf");
        Files.writeString(jvmSrc, kof);
        Path jvmOut = dir.resolve("out-sret-jvm");
        CompilationResult rj = driver.compile(jvmSrc, jvmOut, Target.JVM);
        assertTrue(rj.success(), "JVM oracle compile: " + rj.diagnostics().getDiagnostics());
        String jvm = runJvm(jvmOut);
        assertEquals(expected, jvm, "JVM golden (struct return sret)");

        String nat = runNative(dir, "sret", kof);
        assertEquals(expected, nat, "NATIVE x86-64 SysV struct return (sret > 16 B)");
        assertEquals(jvm, nat, "JVM↔Native byte-for-byte parity (sret)");
    }

    @Test
    void structReturnByValueNativeRegisterPath(@TempDir Path dir) throws Exception {
        // 3.7 fatia 2a: `record` devolvido por valor no caminho de registradores
        // x86-64 — Point (1 eightbyte INTEGER em rax, x+y empacotados), Big
        // (2 eightbytes INTEGER rax+rdx) e Mix (e0 SSE xmm0 + e1 INTEGER rax).
        // Golden = o MESMO fonte no JVM (FFM) — paridade byte-a-byte.
        String so = compileHostLib(dir);
        String kof = """
                record Point(Int x, Int y)
                record Big(Int a, Int b, Int c)
                record Mix(Double d, Int i)

                extern "%s" mkpoint(Int x, Int y): Point
                extern "%s" bigret(Int a, Int b, Int c): Big
                extern "%s" mixret(Double d, Int i): Mix

                main() {
                    val p = mkpoint(3, 4)
                    println(p.x())
                    println(p.y())
                    val g = bigret(10, 20, 30)
                    println(g.a())
                    println(g.b())
                    println(g.c())
                    val m = mixret(2.5, 7)
                    println(m.d())
                    println(m.i())
                }
                """.formatted(so, so, so);
        String expected = "3\n4\n10\n20\n30\n2.5\n7";

        Path jvmSrc = dir.resolve("natret-jvm.kf");
        Files.writeString(jvmSrc, kof);
        Path jvmOut = dir.resolve("out-natret-jvm");
        CompilationResult rj = driver.compile(jvmSrc, jvmOut, Target.JVM);
        assertTrue(rj.success(), "JVM oracle compile: " + rj.diagnostics().getDiagnostics());
        String jvm = runJvm(jvmOut);
        assertEquals(expected, jvm, "JVM golden (struct return)");

        String nat = runNative(dir, "natret", kof);
        assertEquals(expected, nat, "NATIVE x86-64 SysV struct return (register path)");
        assertEquals(jvm, nat, "JVM↔Native byte-for-byte parity (struct return)");
    }

    @Test
    void structReturnByValueJsParity(@TempDir Path dir) throws Exception {
        // Bridge de retorno no JS (D6-1/3.8b, 21/09): o host materializa o struct
        // devolvido por valor na arena, lê os campos e o guest reconstrói o record
        // via `__kof_ffi_from` — mesma semântica do `kof_ffi_read_struct` do JVM
        // (registrador p/ struct pequeno, sret p/ maior). Inclui `Long` (BigInt).
        String so = compileHostLib(dir);
        String kof = """
                record Point(Int x, Int y)
                record Big(Int a, Int b, Int c)
                record Mix(Double d, Int i)
                record ParamMix(Long l, Double d, Int i)

                extern "%s" mkpoint(Int x, Int y): Point
                extern "%s" bigret(Int a, Int b, Int c): Big
                extern "%s" mixret(Double d, Int i): Mix
                extern "%s" parammixret(Long l, Double d, Int i): ParamMix

                main() {
                    val p = mkpoint(3, 4)
                    println(p.x())
                    println(p.y())
                    val g = bigret(10, 20, 30)
                    println(g.a())
                    println(g.b())
                    println(g.c())
                    val m = mixret(2.5, 7)
                    println(m.d())
                    println(m.i())
                    val pm = parammixret(9, 4.5, 2)
                    println(pm.l())
                    println(pm.d())
                    println(pm.i())
                }
                """.formatted(so, so, so, so);
        String expected = "3\n4\n10\n20\n30\n2.5\n7\n9\n4.5\n2";

        Path jvmSrc = dir.resolve("retjs-jvm.kf");
        Files.writeString(jvmSrc, kof);
        CompilationResult rj = driver.compile(jvmSrc, dir.resolve("out-retjs-jvm"), Target.JVM);
        assertTrue(rj.success(), "JVM compile: " + rj.diagnostics().getDiagnostics());
        String jvm = runJvm(dir.resolve("out-retjs-jvm"));
        assertEquals(expected, jvm, "JVM golden (struct return)");

        Path jsSrc = dir.resolve("retjs-js.kf");
        Files.writeString(jsSrc, kof);
        CompilationResult rjs = driver.compile(jsSrc, dir.resolve("out-retjs-js"), Target.JS);
        assertTrue(rjs.success(), "JS struct RETURN must bind (bridge 21/09): "
                + rjs.diagnostics().getDiagnostics());
        String js = runJs(dir.resolve("out-retjs-js"));
        assertEquals(expected, js, "JS golden (struct return, __kof_ffi_from)");
        assertEquals(jvm, js, "JVM==JS byte-for-byte parity (struct return)");
    }

    @Test
    void structParamStringFieldStaysFfi001(@TempDir Path dir) throws IOException {
        // Campo `String`/`char*` é ponteiro (não-escalar no v1) → FFI001 honesto.
        Path src = dir.resolve("strfield.kf");
        Files.writeString(src, """
                record Named(String name, Int n)

                extern "libc.so.6" abs(Named p): Int

                main() {
                    println("hi")
                }
                """);
        CompilationResult r = driver.compile(src, dir.resolve("out-strfield"), Target.JVM);
        assertFalse(r.success(), "record with a String field must not bind in v1");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("FFI001"),
                "expected FFI001, got: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void structParamEmptyRecordStaysFfi001(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("empty.kf");
        Files.writeString(src, """
                record E()

                extern "libc.so.6" abs(E p): Int

                main() {
                    println("hi")
                }
                """);
        CompilationResult r = driver.compile(src, dir.resolve("out-empty"), Target.JVM);
        assertFalse(r.success(), "empty record is not a bindable struct");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("FFI001"),
                "expected FFI001, got: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void structParamNativeMemoryPathStaysFfi001(@TempDir Path dir) throws IOException {
        // 3.7 fatia 1: só o caminho de REGISTRADORES x86-64 binda. Um struct
        // grande (> 16 B → SysV MEMORY) segue FFI001 honesto (R6).
        Path src = dir.resolve("nat.kf");
        Files.writeString(src, """
                record Big3(Int a, Int b, Int c, Int d, Int e)

                extern "libc.so.6" bigsum(Big3 g): Int

                main() {
                    println("hi")
                }
                """);
        CompilationResult r = driver.compile(src, dir.resolve("out-native"), Target.NATIVE);
        assertFalse(r.success(), "Native struct memory path is not bound in 3.7 fatia 1");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("FFI001"),
                "expected FFI001 on Native, got: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void structParamByValueNativeRegisterPath(@TempDir Path dir) throws Exception {
        // 3.7 fatia 1: struct by-value no caminho de registradores x86-64 SysV.
        // Point (2×Int, 1 eightbyte INTEGER), MixIF (Int+Float no MESMO eightbyte
        // → INTEGER, shift/or) e Time (Long INTEGER + Double SSE). Golden = o
        // MESMO fonte no JVM (FFM) — paridade byte-a-byte.
        String so = compileHostLib(dir);
        String kof = """
                record Point(Int x, Int y)
                record MixIF(Int i, Float f)
                record Time(Long t, Double d)

                extern "%s" sumpoint(Point p): Int
                extern "%s" scale(Point p, Double f): Double
                extern "%s" mixif(MixIF m): Double
                extern "%s" timesum(Time t): Double

                main() {
                    println(sumpoint(Point(3, 4)))
                    println(scale(Point(2, 3), 2.0))
                    println(mixif(MixIF(7, 1.5 as Float)))
                    println(timesum(Time(5, 2.25)))
                }
                """.formatted(so, so, so, so);
        String expected = "7\n10.0\n8.5\n7.25";

        Path jvmSrc = dir.resolve("natstruct-jvm.kf");
        Files.writeString(jvmSrc, kof);
        Path jvmOut = dir.resolve("out-natstruct-jvm");
        CompilationResult rj = driver.compile(jvmSrc, jvmOut, Target.JVM);
        assertTrue(rj.success(), "JVM oracle compile: " + rj.diagnostics().getDiagnostics());
        String jvm = runJvm(jvmOut);
        assertEquals(expected, jvm, "JVM golden (struct param by value)");

        String nat = runNative(dir, "natstruct", kof);
        assertEquals(expected, nat, "NATIVE x86-64 SysV struct param (register path)");
        assertEquals(jvm, nat, "JVM↔Native byte-for-byte parity (struct param)");
    }

    @Test
    void structParamByValueJsParity(@TempDir Path dir) throws Exception {
        // Bridge de struct no JS (D6-1/3.8b, 21/09): o record vira struct C por
        // valor no host GraalJS — MESMO StructLayout/offsets do JVM, provado
        // byte-a-byte contra o shim C real. `Mixed` exercita o alinhamento de
        // `long`/`double`/`int` (j/d/i) num layout misto.
        String so = compileHostLib(dir);
        String kof = """
                record Point(Int x, Int y)
                record ParamMix(Long l, Double d, Int i)

                extern "%s" sumpoint(Point p): Int
                extern "%s" scale(Point p, Double f): Double
                extern "%s" parammix(ParamMix m): Double

                main() {
                    println(sumpoint(Point(3, 4)))
                    println(scale(Point(2, 3), 2.0))
                    println(parammix(ParamMix(3, 2.5, 4)))
                }
                """.formatted(so, so, so);
        String expected = "7\n10.0\n9.5";

        Path jvmSrc = dir.resolve("jsstruct-jvm.kf");
        Files.writeString(jvmSrc, kof);
        Path jvmOut = dir.resolve("out-jsstruct-jvm");
        CompilationResult rj = driver.compile(jvmSrc, jvmOut, Target.JVM);
        assertTrue(rj.success(), "JVM compile: " + rj.diagnostics().getDiagnostics());
        String jvm = runJvm(jvmOut);
        assertEquals(expected, jvm, "JVM golden (struct param)");

        Path jsSrc = dir.resolve("jsstruct-js.kf");
        Files.writeString(jsSrc, kof);
        Path jsOut = dir.resolve("out-jsstruct-js");
        CompilationResult rjs = driver.compile(jsSrc, jsOut, Target.JS);
        assertTrue(rjs.success(), "JS struct param must bind (bridge 21/09): "
                + rjs.diagnostics().getDiagnostics());
        String js = runJs(jsOut);
        assertEquals(expected, js, "JS golden (struct param)");
        assertEquals(jvm, js, "JVM==JS byte-for-byte parity (struct param)");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

}
