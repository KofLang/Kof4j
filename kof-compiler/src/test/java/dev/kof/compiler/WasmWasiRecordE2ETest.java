package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Faces de RECORD do frontend WASI (15.3d inc2, #776): `toString`/concat,
 * igualdade de conteudo, campos aninhados e `equals` explicito — todas com
 * paridade byte-a-byte do oraculo JVM sob wasmtime. Separado de
 * `WasmWasiE2ETest` pelo ratchet `check_test_hygiene` (teto de 500 linhas).
 */
class WasmWasiRecordE2ETest {

    private static String jvmBin() {
        String home = System.getProperty("java.home");
        return Path.of(home, "bin", "java").toString();
    }

    private static Path host(String tool) {
        Path p = Path.of(System.getProperty("user.home"), ".local", "share", "kof-wasm", "bin", tool);
        return Files.isExecutable(p) ? p : null;
    }

    @Test
    void recordToStringAndConcatMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        // 15.3d inc2 fatia C1: println(record), record.toString(), String.valueOf(record),
        // record dentro de +, campos Char/Bool/String — paridade byte-a-byte com a JVM
        // (formato record Name[f1=v1, f2=v2]). `==` de record continua recusado (C2).
        var wasmtime = host("wasmtime");
        assumeTrue(wasmtime != null, "wasmtime host absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                record Point(Int x, Int y)
                record Pair(String a, Int n)
                record Flag(Char c, Bool ok)

                main(String[] args) {
                    var p = Point(1, 2)
                    println(p)
                    println(p.toString())
                    println(String.valueOf(p))
                    println("v=" + p)
                    var q = Pair("ab", 7)
                    println(q)
                    var f = Flag('k', true)
                    println(f)
                }
                """);
        var driver = new CompilerDriver();
        var jvm = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-jvm"), Target.JVM);
        assertTrue(jvm.success(), "jvm tostring compile: " + jvm.diagnostics().getDiagnostics());
        var oracle = new ProcessBuilder(List.of(jvmBin(), "-cp",
                Path.of(dir.toString(), "out-jvm").toString(), "Default.Main"))
                .redirectErrorStream(true).start();
        String expected = new String(oracle.getInputStream().readAllBytes());
        assertTrue(oracle.waitFor(120, TimeUnit.SECONDS) && oracle.exitValue() == 0,
                "JVM tostring oracle: " + expected);
        var wasi = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-wasi"), Target.WASI);
        assertTrue(wasi.success(), "wasi tostring compile: " + wasi.diagnostics().getDiagnostics());
        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run",
                Path.of(dir.toString(), "out-wasi", "Default", "Main.wasm").toString()))
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS), "host within 60s");
        assertEquals(0, proc.exitValue(), "clean WASI exit tostring: " + out);
        assertEquals(expected.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                out.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                "record toString/concat must equal the JVM oracle");
    }

    @Test
    void recordEqualityMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        // fatia C2 (15.3d inc2): `==`/`!=` de record = igualdade de CONTEUDO
        // null-safe (Objects.equals). O frontend WASI desuga p/ a chamada opaca
        // kofRecordEq (precedente do JS no RecordEqualityLowerer) e o backend
        // sintetiza o fold de campo reto, com accumulator; Double pelos bits
        // (Double.equals JVM), String via kof.strEq. Paridade byte-a-byte.
        var wasmtime = host("wasmtime");
        assumeTrue(wasmtime != null, "wasmtime host absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                record Point(Int x, Int y)
                record Pair(String a, Int n)
                record Flag(Boolean ok, Char c)
                record Dbl(Double v)
                main(String[] args) {
                    var p = Point(1, 2)
                    var q = Point(1, 2)
                    var r = Point(1, 3)
                    println(p == q)
                    println(p == r)
                    println(p != r)
                    println(!(p == r))
                    println(p == null)
                    println(p != null)
                    println(Pair("ab", 7) == Pair("ab", 7))
                    println(Pair("ab", 7) == Pair("xy", 7))
                    println(Flag(true, 'A') == Flag(true, 'A'))
                    println(Flag(false, 'A') == Flag(true, 'A'))
                    println(Dbl(1.5) == Dbl(1.5))
                    println(Dbl(1.5) == Dbl(2.5))
                }
                """);
        var driver = new CompilerDriver();
        var jvm = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-jvm"), Target.JVM);
        assertTrue(jvm.success(), "jvm equality compile: " + jvm.diagnostics().getDiagnostics());
        var oracle = new ProcessBuilder(List.of(jvmBin(), "-cp",
                Path.of(dir.toString(), "out-jvm").toString(), "Default.Main"))
                .redirectErrorStream(true).start();
        String expected = new String(oracle.getInputStream().readAllBytes());
        assertTrue(oracle.waitFor(120, TimeUnit.SECONDS) && oracle.exitValue() == 0,
                "JVM equality oracle: " + expected);
        var wasi = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-wasi"), Target.WASI);
        assertTrue(wasi.success(), "wasi equality compile: " + wasi.diagnostics().getDiagnostics());
        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run",
                Path.of(dir.toString(), "out-wasi", "Default", "Main.wasm").toString()))
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS), "host within 60s");
        assertEquals(0, proc.exitValue(), "clean WASI exit equality: " + out);
        assertEquals(expected.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                out.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                "record ==/!=/null/Double-bits must equal the JVM oracle");
    }


    @Test
    void nestedRecordFieldsMatchTheJvmOracle(@TempDir Path dir) throws Exception {
        var wasmtime = host("wasmtime");
        assumeTrue(wasmtime != null, "wasmtime host absent — run scripts/provision-wasmtime.sh");
        // 15.3d inc2 fatia D: campos record-aninhados = handles i32 no bump heap;
        // toString/==/!= descem recursivamente com um scratch por profundidade.
        Files.writeString(dir.resolve("Main.kf"), """
                record Inner(Int v)
                record Outer(Inner i, Int t)
                main(String[] args) {
                    var o = Outer(Inner(1), 9)
                    println(o.i)
                    println(o)
                    println(o.i.v + o.t)
                    println(o == Outer(Inner(1), 9))
                    println(o == Outer(Inner(2), 9))
                    println(Outer(o.i, 9) == o)
                    println(o != Outer(Inner(1), 8))
                    println(String.valueOf(o.i))
                }
                """);
        var driver = new CompilerDriver();
        var jvm = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-jvm"), Target.JVM);
        assertTrue(jvm.success(), "jvm nested compile: " + jvm.diagnostics().getDiagnostics());
        var oracle = new ProcessBuilder(List.of(jvmBin(), "-cp",
                Path.of(dir.toString(), "out-jvm").toString(), "Default.Main"))
                .redirectErrorStream(true).start();
        String expected = new String(oracle.getInputStream().readAllBytes());
        assertTrue(oracle.waitFor(120, TimeUnit.SECONDS) && oracle.exitValue() == 0,
                "JVM nested oracle: " + expected);
        var wasi = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-wasi"), Target.WASI);
        assertTrue(wasi.success(), "wasi nested compile: " + wasi.diagnostics().getDiagnostics());
        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run",
                Path.of(dir.toString(), "out-wasi", "Default", "Main.wasm").toString()))
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS), "host within 60s");
        assertEquals(0, proc.exitValue(), "clean WASI exit nested: " + out);
        assertEquals(expected.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                out.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                "nested-record fields must equal the JVM oracle");
    }
    @Test
    void explicitRecordEqualsMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        // 15.3d inc2 fatia E: `p.equals(q)` EXPLICITO roteado p/ o mesmo fold de
        // conteudo do `==` (sem dispatch virtual) — contrato §262: equals
        // explicito de record = igualdade de CONTEUDO em todo target.
        var wasmtime = host("wasmtime");
        assumeTrue(wasmtime != null, "wasmtime host absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                record Point(Int x, Int y)
                record Pair(String a, Int n)
                main(String[] args) {
                    var p = Point(1, 2)
                    println(p.equals(Point(1, 2)))
                    println(p.equals(Point(1, 3)))
                    println(!p.equals(Point(2, 2)))
                    println(Pair("ab", 7).equals(Pair("ab", 7)))
                    println(Pair("ab", 7).equals(Pair("cd", 7)))
                }
                """);
        var driver = new CompilerDriver();
        var jvm = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-jvm"), Target.JVM);
        assertTrue(jvm.success(), "jvm explicit-equals compile: " + jvm.diagnostics().getDiagnostics());
        var oracle = new ProcessBuilder(List.of(jvmBin(), "-cp",
                Path.of(dir.toString(), "out-jvm").toString(), "Default.Main"))
                .redirectErrorStream(true).start();
        String expected = new String(oracle.getInputStream().readAllBytes());
        assertTrue(oracle.waitFor(120, TimeUnit.SECONDS) && oracle.exitValue() == 0,
                "JVM explicit-equals oracle: " + expected);
        var wasi = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-wasi"), Target.WASI);
        assertTrue(wasi.success(), "wasi explicit-equals compile: " + wasi.diagnostics().getDiagnostics());
        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run",
                Path.of(dir.toString(), "out-wasi", "Default", "Main.wasm").toString()))
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS), "host within 60s");
        assertEquals(0, proc.exitValue(), "clean WASI exit explicit-equals: " + out);
        assertEquals(expected.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                out.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                "explicit record equals must equal the JVM oracle");
    }

    @Test
    void arrayPrintRefusesWithNoArtifacts(@TempDir Path dir) throws Exception {
        // ARRAY-1 (27/10): o println do array INTEIRO segue fora da fatia
        // (o format [I@1b6d3586 e' identidade de ponteiro — recusa nomeada,
        // sem artefato). O iter (`for (a in args)`) saiu daqui: a fatia
        // ARRAY-1 legitimou o desugar (length/get provados) e virou a face
        // de paridade argsIterationMatchesTheJvmOracle abaixo.
        var driver = new CompilerDriver();
        Path src = dir.resolve("Refuse-array-print.kf");
        Files.writeString(src, "main(String[] args) { println(args) }\n");
        Path out = dir.resolve("out-array-print");
        var r = driver.compile(src, out, Target.WASI);
        assertFalse(r.success(), "array-print must refuse WASM002");
        String msg = r.diagnostics().getDiagnostics().toString();
        assertTrue(msg.contains("WASM002") && msg.contains("#776")
                && msg.contains("wasm-wasi-plan"), "array-print honest: " + msg);
        assertFalse(Files.exists(out.resolve("Default")),
                "array-print: refusal emits NO artifacts");
    }

    @Test
    void argsIterationMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        // ARRAY-1 (27/10, #776): `for (var a in args)` — o desugar (length/
        // get) anda na maquinaria da fatia; a antiga RECUSA registrada
        // (WasmWasiRecordE2ETest) vira paridade byte-idêntica na MESMA
        // commit em que virou tradução. Sem args o desugar imprime NADA
        // (length 0) — o wasmtime roda sem host args.
        var wasmtime = host("wasmtime");
        assumeTrue(wasmtime != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"),
                "main(String[] args) {\n    for (var a in args) { println(a) }\n}\n");
        var driver = new CompilerDriver();
        var jvm = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-jvm"), Target.JVM);
        assertTrue(jvm.success(), "jvm iter compile: " + jvm.diagnostics().getDiagnostics());
        var oracle = new ProcessBuilder(List.of(jvmBin(), "-cp",
                Path.of(dir.toString(), "out-jvm").toString(), "Default.Main"))
                .redirectErrorStream(true).start();
        String expected = new String(oracle.getInputStream().readAllBytes());
        assertTrue(oracle.waitFor(120, TimeUnit.SECONDS) && oracle.exitValue() == 0,
                "JVM iter oracle: " + expected);
        var wasi = driver.compile(dir.resolve("Main.kf"), dir.resolve("out-wasi"), Target.WASI);
        assertTrue(wasi.success(), "wasi iter compile (ARRAY-1 legitimou o desugar): "
                + wasi.diagnostics().getDiagnostics());
        var proc = new ProcessBuilder(List.of(wasmtime.toString(), "run",
                Path.of(dir.toString(), "out-wasi", "Default", "Main.wasm").toString()))
                .redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        assertTrue(proc.waitFor(60, TimeUnit.SECONDS), "host within 60s");
        assertEquals(0, proc.exitValue(), "clean WASI exit iter: " + out);
        assertEquals(expected.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                out.replaceAll("(?m)^warning:.*$", "").replaceAll("\n+$", ""),
                "args iteration must equal the JVM oracle");
    }

    @Test
    void recordToStringWithDoubleFieldsMatchesTheJvmOracle(@TempDir Path dir) throws Exception {
        // FATIA F face 2 (28/10, #776): o toString de record com campos
        // Double — o MESMO kof.doubleToStr do println/concat/caixa (o
        // Schubfach completo; a familia entra pelo gancho recordDoubles
        // do scan). A rodada MEDIU antes: o concat e o valueOf com
        // Doubles JA eram byte-idênticos (F2b) — o que faltava era SÓ o
        // campo do record (o WASM002 honesto no WasmRecordCode). Também
        // fecha a MEDIDA do pow: Math.pow == StrictMath.pow deriva 3/30
        // por 1 ulp (o intrinseco da JVM vs o fdlibm puro) — o pow segue
        // RECUSADO no WASI (o precedente do cos), nunca um port com drift.
        var wasmtime = WasmWasiFlowE2ETest.host("wasmtime");
        var wasmTools = WasmWasiFlowE2ETest.host("wasm-tools");
        org.junit.jupiter.api.Assumptions.assumeTrue(wasmtime != null && wasmTools != null, "wasm hosts absent — run scripts/provision-wasmtime.sh");
        Files.writeString(dir.resolve("Main.kf"), """
                record Point(Double x, Double y)
                main(String[] args) {
                    var p = Point(0.5, -2.25)
                    println(p)
                    println("v=" + 0.1)
                    println(0.1 + 0.2)
                    var s = "x" + p.x
                    println(s)
                    println(p.x)
                    var n = Point(0.0, -0.0)
                    println(n)
                }
                """);
        WasmWasiFlowE2ETest.assertFaceMatchesJvm(dir);
    }
}
