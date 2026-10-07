package dev.kof.compiler;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** #259/N2: the same return/local contract on each target, independently reported. */
class NativeNullablePrimitiveContractE2ETest {
    // 15.1 (#776, 07/10): WASM/WASI joined the TOPOLOGY but have no emitting
    // backend yet (compile refuses with the clean WASM001 diagnostic) — they
    // are excluded from the run-the-surface matrix exactly like ANDROID, and
    // must RE-ENTER this matrix when units 15.2/15.3 land with real goldens.

    @ParameterizedTest
    @EnumSource(value = Target.class, names = {"ANDROID", "WASM", "WASI"}, mode = EnumSource.Mode.EXCLUDE)
    void absenceAndPresentDefaults(Target target, @TempDir Path dir) throws Exception {
        run(target, dir, """
                Int? ni() { return null }
                Int? zi() { return 0 }
                Long? nl() { return null }
                Long? zl() { return 0 }
                Troolean nb() { return null }
                Troolean zb() { return false }
                Float? nf() { return null }
                Float? zf() { return 0.0 }
                Double? nd() { return null }
                Double? zd() { return 0.0 }
                Char? nc() { return null }
                Char? vc() { return 'K' }
                Byte? by() { return 7 as Byte }
                Short? sh() { return 9 as Short }
                main() {
                    println(ni() == null); println(ni()); println(zi() == null); println(zi())
                    println(nl() == null); println(nl()); println(zl() == null); println(zl())
                    println(nb() == null); println(nb()); println(zb() == null); println(zb())
                    println(nf() == null); println(nf()); println(zf() == null); println(zf())
                    println(nd() == null); println(nd()); println(zd() == null); println(zd())
                    println(nc() == null); println(nc()); println(vc() == null); println(vc())
                    println(by()); println(sh())
                }
                """, "true\nnull\nfalse\n0\ntrue\nnull\nfalse\n0\ntrue\nnull\nfalse\nfalse\n"
                + "true\nnull\nfalse\n0.0\ntrue\nnull\nfalse\n0.0\ntrue\nnull\nfalse\nK\n7\n9");
    }

    @ParameterizedTest
    @EnumSource(value = Target.class, names = {"ANDROID", "WASM", "WASI"}, mode = EnumSource.Mode.EXCLUDE)
    void branchesLocalsAndForwardedReturn(Target target, @TempDir Path dir) throws Exception {
        run(target, dir, """
                Int? f(Bool c) = if (c) 7 else null
                Int? g(Bool c) { return f(c) }
                main() {
                    Int? a = if (false) 7 else null
                    println(a == null); println(a)
                    Int? b = g(true)
                    println(b); println(g(false) == null)
                    b = 9
                    println(b)
                    b += 1
                    println(b)
                    b++
                    println(b)
                    var n = g(true)
                    if (n != null) { println(n + 1) }
                    Double? d = 2
                    println(d)
                    Float? x = 2.5
                    x += 1.0
                    println(x)
                }
                """, "true\nnull\n7\ntrue\n9\n10\n11\n8\n2.0\n3.5");
    }

    @ParameterizedTest
    @EnumSource(value = Target.class, names = {"ANDROID", "WASM", "WASI"}, mode = EnumSource.Mode.EXCLUDE)
    void equalityUsesPayloadAndNullIsSymmetric(Target target, @TempDir Path dir) throws Exception {
        run(target, dir, """
                Int? a() { return 10000 }
                Int? b() { return 10000 }
                Int? n() { return null }
                Troolean f() { return false }
                Double? d() { return 10.5 }
                Float? x() { return 2.5 }
                main() {
                    println(a() == b()); println(a() == 10000); println(10000 == b())
                    println(a() == n()); println(n() == a()); println(n() == n())
                    println(f() == false); println(false == f()); println(f() == f())
                    println(d() == d()); println(d() == 10.5); println(10.5 == d())
                    println(x() == x())
                }
                """, "true\ntrue\ntrue\nfalse\nfalse\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue\ntrue");
    }

    @ParameterizedTest
    @EnumSource(value = Target.class, names = {"ANDROID", "WASM", "WASI"}, mode = EnumSource.Mode.EXCLUDE)
    void stringificationAndFinallyPreserveRepresentation(Target target, @TempDir Path dir) throws Exception {
        run(target, dir, """
                Char? c(Bool b) { if (b) return 'K'; return null }
                Double? d(Bool b) { if (b) return 2.5; return null }
                Int? f(Bool b) {
                    try { if (b) return 7; return null }
                    finally { println("finally") }
                }
                main() {
                    println("c=" + c(true)); println("c=" + c(false))
                    println("d=" + d(true)); println("d=" + d(false))
                    println(f(true)); println(f(false))
                    var x = d(true)
                    if (x != null) { println(x + 1.0); println(x > 2.0) }
                }
                """, "c=K\nc=null\nd=2.5\nd=null\nfinally\n7\nfinally\nnull\n3.5\ntrue");
    }

    @ParameterizedTest
    @EnumSource(value = Target.class, names = {"ANDROID", "WASM", "WASI"}, mode = EnumSource.Mode.EXCLUDE)
    void floatingEqualityMatchesJvmWrapperContract(Target target, @TempDir Path dir) throws Exception {
        run(target, dir, """
                Double? dn() { return 0.0 / 0.0 }
                Double? di() { return 1.0 / 0.0 }
                Double? dp() { return 0.0 }
                Double? dm() { return -0.0 }
                Float? nanFloat() { return 0.0 / 0.0 }
                Float? fi() { return 1.0 / 0.0 }
                Float? fp() { return 0.0 }
                Float? fm() { return -0.0 }
                main() {
                    println(dn() == dn()); println(di() == di()); println(dp() == dm())
                    println(nanFloat() == nanFloat()); println(fi() == fi()); println(fp() == fm())
                }
                """, "true\ntrue\nfalse\ntrue\ntrue\nfalse");
    }

    /**
     * #259: a atribuição simples (não a declaração) também precisa boxar pelo
     * inner DECLARADO do slot — o widening já converteu a pilha. Esteve
     * dormente no Native enquanto o slot era desembrulhado; ao preservar
     * {@code NullableType} ele acordou.
     */
    @ParameterizedTest
    @EnumSource(value = Target.class, names = {"ANDROID", "WASM", "WASI"}, mode = EnumSource.Mode.EXCLUDE)
    void plainAssignmentBoxesAtDeclaredInnerWidth(Target target, @TempDir Path dir) throws Exception {
        run(target, dir, """
                main() {
                    Float? x = 2.5
                    x = 3.5
                    println(x)
                    Double? d = 1.5
                    d = 2
                    println(d)
                    Long? l = 1
                    l = 2
                    println(l)
                    Int? i = 1
                    i = 5
                    println(i)
                    Double? d0 = 2
                    println(d0)
                    Long? l0 = 2
                    println(l0)
                    Float? f0 = 2
                    println(f0)
                    Float? f1 = 2.5
                    println(f1)
                }
                """, "3.5\n2.0\n2\n5\n2.0\n2\n2.0\n2.5");
    }

    /**
     * #259 × §306: com o slot nativo de {@code Troolean} agora BOXED (e não mais o
     * int cru), a truthiness não pode testar o PONTEIRO — {@code Present(false)}
     * é um ponteiro não-nulo e viraria "verdadeiro". O §306 tinha deixado o
     * Native de fora justamente porque o slot era o int cru.
     */
    @ParameterizedTest
    @EnumSource(value = Target.class, names = {"ANDROID", "WASM", "WASI"}, mode = EnumSource.Mode.EXCLUDE)
    void boxedBoolTruthinessReadsValueNotPointer(Target target, @TempDir Path dir) throws Exception {
        run(target, dir, """
                Troolean nb() { return null }
                Troolean tb() { return true }
                Troolean fb() { return false }
                main() {
                    if (nb()) println("null-true") else println("null-false")
                    if (tb()) println("true-true") else println("true-false")
                    if (fb()) println("false-true") else println("false-false")
                    Troolean x = fb()
                    if (x) println("x-true") else println("x-false")
                    Troolean y = tb()
                    if (y) println("y-true") else println("y-false")
                    var g = 0
                    while (fb() && g < 4) { g += 1 }
                    println(g)
                }
                """, "null-false\ntrue-true\nfalse-false\nx-false\ny-true\n0");
    }

    private static void run(Target target, Path dir, String source, String expected) throws Exception {
        if (target.isNative()) {
            assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"), "Linux ELF required");
            if (target != Target.NATIVE) {
                assumeTrue(NativeRiscv64E2ETest.hasToolchain(target.nativeArch()), "cross toolchain/QEMU required");
            }
        }
        Path file = dir.resolve("Main.kf");
        Files.writeString(file, source);
        CompilerDriver driver = new CompilerDriver();
        String output;
        if (target == Target.SCRIPT) {
            KofInterpreter.Result result = driver.interpret(List.of(file), dir, new String[0]);
            assertEquals(0, result.exitCode(), result.stdout());
            output = result.stdout();
        } else {
            Path out = dir.resolve("out");
            CompilationResult result = driver.compile(file, out, target);
            assertTrue(result.success(), target + ": " + result.diagnostics().getDiagnostics());
            if (target == Target.JS) {
                ByteArrayOutputStream captured = new ByteArrayOutputStream();
                int exit = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), captured,
                        new ByteArrayInputStream(new byte[0]), captured);
                output = captured.toString(StandardCharsets.UTF_8);
                assertEquals(0, exit, output);
            } else {
                ProcessBuilder command;
                if (target == Target.JVM) {
                    Path runner = dir.resolve("Run.java");
                    Files.writeString(runner, """
                            class Run {
                                public static void main(String[] args) throws Exception {
                                    Class.forName("Default.Main").getMethod("main", String[].class)
                                        .invoke(null, (Object) new String[0]);
                                }
                            }
                            """);
                    command = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                            "--class-path", out.toString(), runner.toString());
                } else if (target == Target.NATIVE) {
                    command = new ProcessBuilder(out.resolve("Default/Main").toString());
                } else {
                    command = NativeRiscv64E2ETest.qemu(target.nativeArch(), out.resolve("Default/Main"));
                }
                Path log = dir.resolve("process.log");
                Process process = command.redirectErrorStream(true).redirectOutput(log.toFile()).start();
                try {
                    assertTrue(process.waitFor(30, TimeUnit.SECONDS), target + " execution timed out");
                    output = Files.readString(log);
                    assertEquals(0, process.exitValue(), target + ": " + output);
                } finally {
                    if (process.isAlive()) process.destroyForcibly();
                }
            }
        }
        assertEquals(expected, output.replace("\r\n", "\n").strip(), target.toString());
    }
}
