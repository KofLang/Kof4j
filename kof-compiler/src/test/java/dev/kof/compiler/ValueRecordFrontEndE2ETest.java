package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TIER 2.7 step 2.7.1 — the {@code value} front-end keyword (D-VALUE-RECORD,
 * D-VALUE-RECORDS-GO). {@code value record Name(fields)} parses and types
 * exactly like {@code record} plus the {@code value} modifier; step 2.7.1 adds
 * NO identity semantics yet (that is 2.7.2–2.7.4), so every observable behavior
 * is identical to a plain {@code record} — which is what this test pins.
 *
 * <p>{@code value} is a CONTEXTUAL keyword (IDENTIFIER in the lexer), exactly
 * like {@code sealed}: it is a modifier only before {@code record}. Anywhere
 * else it stays an ordinary identifier — the capture-box {@code value} field
 * and the {@code kof.ui} {@code value} must keep working (additive, R2).
 */
class ValueRecordFrontEndE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String PLAIN = """
            record Vec2(Float x, Float y)
            main() {
                var a = Vec2(1.0, 2.0)
                var b = Vec2(1.0, 2.0)
                var c = Vec2(3.0, 4.0)
                println(a.x)
                println(a.y)
                println(a == b)
                println(a == c)
                println(a)
            }
            """;

    private static final String VALUE = PLAIN.replace("record Vec2", "value record Vec2");

    private String runJvm(Path outDir) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(TestJdk.javaBin(),
                "-cp", outDir.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "JVM exit code, output: " + out);
        return out;
    }

    @Test
    void valueRecordBehavesExactlyLikeRecord(@TempDir Path tmp) throws Exception {
        Path plain = tmp.resolve("plain.kf");
        Files.writeString(plain, PLAIN);
        Path plainOut = tmp.resolve("plain-out");
        CompilationResult rp = driver.compile(plain, plainOut, Target.JVM);
        assertTrue(rp.success(), () -> "plain record compile: " + rp.diagnostics().getDiagnostics());
        String plainResult = runJvm(plainOut);

        Path value = tmp.resolve("value.kf");
        Files.writeString(value, VALUE);
        Path valueOut = tmp.resolve("value-out");
        CompilationResult rv = driver.compile(value, valueOut, Target.JVM);
        assertTrue(rv.success(), () -> "value record compile: " + rv.diagnostics().getDiagnostics());
        String valueResult = runJvm(valueOut);

        assertEquals(plainResult, valueResult,
                "2.7.1: `value record` behaves identically to `record` (no identity semantics yet)");
        assertEquals("1.0\n2.0\ntrue\nfalse\nVec2[x=1.0, y=2.0]", valueResult, "the real values");
    }

    @Test
    void valueRecordOnNativeMatchesJvm(@TempDir Path tmp) throws Exception {
        Assumptions.assumeTrue(dev.kof.compiler.nat.NativeToolchainGate.present(),
                "x86-64 toolchain (as/ld) ausente — pulando");
        Path value = tmp.resolve("vn.kf");
        Files.writeString(value, VALUE);
        Path out = tmp.resolve("vn-native");
        CompilationResult r = driver.compile(value, out, Target.NATIVE);
        assertTrue(r.success(), () -> "native compile: " + r.diagnostics().getDiagnostics());
        ProcessBuilder nb = new ProcessBuilder(out.resolve("Default/Main").toString());
        nb.redirectErrorStream(true);
        Process p = nb.start();
        String got = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "native exit, output: " + got);
        assertEquals("1.0\n2.0\ntrue\nfalse\nVec2[x=1.0, y=2.0]", got, "native = JVM");
    }

    @Test
    void valueStaysAnOrdinaryIdentifierOutsideTheModifierPosition(@TempDir Path tmp) throws Exception {
        // the `value` identifier must keep working: a local named `value`, and a
        // record field named `value` (both would break if `value` were reserved).
        String src = """
                record Amount(Int value, String unit)
                main() {
                    var value = 42
                    println(value)
                    var a = Amount(7, "kg")
                    println(a.value)
                }
                """;
        Path f = tmp.resolve("ident.kf");
        Files.writeString(f, src);
        Path out = tmp.resolve("ident-out");
        CompilationResult r = driver.compile(f, out, Target.JVM);
        assertTrue(r.success(), () -> "identifier `value` compile: " + r.diagnostics().getDiagnostics());
        assertEquals("42\n7", runJvm(out), "`value` is contextual, not reserved (R2)");
    }

    @Test
    void valueRecordInPortuKof(@TempDir Path tmp) throws Exception {
        // PortuKof: `valor registro` — the contextual PT spelling (value→valor).
        String src = """
                valor registro Vec2(Float x, Float y)
                principal() {
                    var a = Vec2(1.0, 2.0)
                    var b = Vec2(1.0, 2.0)
                    escrevaln(a.x)
                    escrevaln(a == b)
                }
                """;
        Path f = tmp.resolve("Main.ptkf");
        Files.writeString(f, src);
        KofInterpreter.Result r = driver.interpret(java.util.List.of(f), tmp, new String[0]);
        assertEquals(0, r.exitCode(), "PT exit/stderr: " + r.stdout() + " " + r.stderr());
        assertEquals("1.0\ntrue", r.stdout().trim(), "PortuKof `valor registro`");
    }
    // ---- TIER 2.7 step 2.7.2 (JVM ABI) ------------------------------------

    /**
     * 2.7.2: the {@code value} modifier is carried from the parse into the
     * class symbol ({@code isValueRecord}), so 2.7.3/2.7.4 and the future
     * compile-time identity error can query it. A plain record reports false.
     */
    @Test
    void valueModifierIsCarriedIntoTheClassSymbol(@TempDir Path tmp) throws Exception {
        Path value = tmp.resolve("symv.kf");
        Files.writeString(value, "value record Vec2(Float x, Float y)\n");
        CompilationResult rv = driver.compile(value, tmp.resolve("symv-out"), Target.JVM);
        assertTrue(rv.success(), () -> "compile: " + rv.diagnostics().getDiagnostics());
        var valueSym = driver.semanticAnalyzer.getClass("Vec2");
        assertNotNull(valueSym, "Vec2 symbol");
        assertTrue(valueSym.isValueRecord(), "`value record` → isValueRecord=true");

        Path plain = tmp.resolve("symp.kf");
        Files.writeString(plain, "record Vec2(Float x, Float y)\n");
        driver.compile(plain, tmp.resolve("symp-out"), Target.JVM);
        var plainSym = driver.semanticAnalyzer.getClass("Vec2");
        assertNotNull(plainSym, "plain Vec2 symbol");
        assertFalse(plainSym.isValueRecord(), "plain `record` → isValueRecord=false");
    }

    /**
     * 2.7.2 JVM ABI (R7): a {@code value record} on the JVM is the common
     * record class — structural equality, and collections hold it BOXED
     * (D-VALUE-RECORDS-GO). Every observable behavior is identical to a plain
     * record, so the parity is pinned by running the SAME program both ways.
     */
    @Test
    void valueRecordJvmAbiMatchesPlainRecord(@TempDir Path tmp) throws Exception {
        String plain = """
                record P(Int x, Int y)
                main() {
                    var a = P(1, 2)
                    var b = P(1, 2)
                    println(a == b)
                    println(a)
                    var l = listOf(a, b)
                    println(l.size())
                    println(l.get(0).x)
                    println(l.get(0) == l.get(1))
                    var m = mapOf("k", a)
                    var got = m.get("k")
                    if (got != null) {
                        println(got.y)
                        println(got == a)
                    } else {
                        println(-1)
                        println(false)
                    }
                }
                """;
        Path p1 = tmp.resolve("plain-jvmabi.kf");
        Files.writeString(p1, plain);
        Path p1out = tmp.resolve("plain-jvmabi-out");
        CompilationResult r1 = driver.compile(p1, p1out, Target.JVM);
        assertTrue(r1.success(), () -> "compile: " + r1.diagnostics().getDiagnostics());
        String plainResult = runJvm(p1out);

        Path p2 = tmp.resolve("value-jvmabi.kf");
        Files.writeString(p2, plain.replace("record P(", "value record P("));
        Path p2out = tmp.resolve("value-jvmabi-out");
        CompilationResult r2 = driver.compile(p2, p2out, Target.JVM);
        assertTrue(r2.success(), () -> "compile: " + r2.diagnostics().getDiagnostics());
        String valueResult = runJvm(p2out);

        assertEquals(plainResult, valueResult,
                "2.7.2: the JVM ABI of a `value record` is the common record (structural equality, boxed in collections)");
        assertEquals("true\nP[x=1, y=2]\n2\n1\ntrue\n2\ntrue", valueResult, "the real values");
    }

    /**
     * 2.7.4 JS ABI (the plan §4: "plain frozen object"): a {@code value
     * record} is emitted with {@code Object.freeze(this)} in its constructor —
     * a plain record is NOT frozen. The freeze is invisible to Kof code
     * (records are immutable) and delivers the no-mutation guarantee at the
     * JS-object level.
     */
    @Test
    void jsEmitsFrozenObjectForValueRecordOnly(@TempDir Path tmp) throws Exception {
        String body = "record Vec2(Float x, Float y)\n"
                + "main() { var a = Vec2(1.0, 2.0)  println(a.x)  println(a.y) }\n";
        Path plain = tmp.resolve("plain.kf");
        Files.writeString(plain, body);
        Path plainOut = tmp.resolve("plain-js");
        CompilationResult rp = driver.compile(plain, plainOut, Target.JS);
        assertTrue(rp.success(), () -> "JS compile: " + rp.diagnostics().getDiagnostics());
        String plainJs = Files.readString(plainOut.resolve("Default.mjs"));
        assertFalse(plainJs.contains("Object.freeze"),
                "a plain record is NOT frozen");

        Path value = tmp.resolve("value.kf");
        Files.writeString(value, body.replace("record Vec2", "value record Vec2"));
        Path valueOut = tmp.resolve("value-js");
        CompilationResult rv = driver.compile(value, valueOut, Target.JS);
        assertTrue(rv.success(), () -> "JS compile: " + rv.diagnostics().getDiagnostics());
        String valueJs = Files.readString(valueOut.resolve("Default.mjs"));
        assertTrue(valueJs.contains("Object.freeze(this);"),
                "the `value record` constructor freezes the object");

        // the frozen face runs identically on node
        ProcessBuilder jb = new ProcessBuilder(TestJdk.onPath("node"),
                valueOut.resolve("Default.mjs").toString());
        jb.redirectErrorStream(true);
        Process jp = jb.start();
        String got = new String(jp.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, jp.waitFor(), "JS exit, output: " + got);
        assertEquals("1.0\n2.0", got, "the frozen value record runs unchanged");
    }
}
