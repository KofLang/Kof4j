package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * #845: um {@code T?} primitivo estreitado por {@code if (x != null)} e consumido
 * como primitivo (declaração {@code Int j = i}, atribuição, {@code return i},
 * argumento de formal primitivo — inclusive com widening {@code Int? → Long} — e
 * índice de {@code List}) chegava ao consumidor como a REFERÊNCIA do wrapper: o
 * slot de {@code Int?} é boxed (D-NULL-INTENT) e o estreitamento é semântico, não
 * físico. JVM: {@code VerifyError} (istore/ireturn/invokevirtual sobre Integer) ou
 * {@code ClassCastException Integer → Long}; Native: o ponteiro da caixa usado
 * como número. O índice nullable SEM guarda era aceito em silêncio (SEM014 agora).
 */
class NarrowedNullablePrimitiveUseE2ETest {

    @ParameterizedTest
    @EnumSource(value = Target.class, names = {"ANDROID", "WASM", "WASI"}, mode = EnumSource.Mode.EXCLUDE)
    void issueReproducerDeclaresNarrowedIntAndIndexes(Target target, @TempDir Path dir) throws Exception {
        run(target, dir, """
                main() {
                    var m = new Map<String, Int>()
                    m.put("a", 0)
                    var l = listOf("x")
                    if (m.contains("a")) {
                        var i = m.get("a")
                        if (i != null) {
                            Int j = i
                            println("lista=" + l.get(j))
                        }
                    }
                }
                """, "lista=x");
    }

    @ParameterizedTest
    @EnumSource(value = Target.class, names = {"ANDROID", "WASM", "WASI"}, mode = EnumSource.Mode.EXCLUDE)
    void narrowedIntUsedDirectlyAsListIndex(Target target, @TempDir Path dir) throws Exception {
        run(target, dir, """
                class Box {
                    String at(Map<String, Int> m, List<String> l, String k) {
                        var i = m.get(k)
                        if (i != null) { return l.get(i) }
                        return "missing"
                    }
                }
                main() {
                    var m = new Map<String, Int>()
                    m.put("a", 1)
                    var l = listOf("x", "y", "z")
                    var i = m.get("a")
                    if (i != null) {
                        println("lista=" + l.get(i))
                        l.set(i, "Y")
                        println(l.subList(0, i).size())
                        println(l.remove(i))
                    }
                    var b = new Box()
                    println(b.at(m, l, "a"))
                    println(b.at(m, l, "zz"))
                }
                """, "lista=y\n1\nY\nz\nmissing");
    }

    @ParameterizedTest
    @EnumSource(value = Target.class, names = {"ANDROID", "WASM", "WASI"}, mode = EnumSource.Mode.EXCLUDE)
    void narrowedIntReturnedAndAssignedAsInt(Target target, @TempDir Path dir) throws Exception {
        run(target, dir, """
                Int pick(Int? i) {
                    if (i != null) { return i }
                    return -1
                }
                Int pickFinally(Int? i) {
                    try {
                        if (i != null) { return i }
                    } finally {
                        println("fin")
                    }
                    return -1
                }
                main() {
                    println(pick(7))
                    println(pick(null))
                    println(pick(1000))
                    println(pick(-5))
                    println(pick(0))
                    println(pickFinally(42))
                    Int? v = 1234
                    Int j = 0
                    if (v != null) { j = v }
                    println(j + 1)
                }
                """, "7\n-1\n1000\n-5\n0\nfin\n42\n1235");
    }

    @ParameterizedTest
    @EnumSource(value = Target.class, names = {"ANDROID", "WASM", "WASI"}, mode = EnumSource.Mode.EXCLUDE)
    void narrowedIntWidensAfterUnboxingTheSourceWrapper(Target target, @TempDir Path dir) throws Exception {
        run(target, dir, """
                Long asLong(Long x) { return x }
                Double asDouble(Double x) { return x }
                Long widenReturn(Int? i) {
                    if (i != null) { return i }
                    return 0L
                }
                main() {
                    var m = new Map<String, Int>()
                    m.put("a", 7)
                    m.put("big", 100000)
                    var i = m.get("a")
                    if (i != null) {
                        println(asLong(i))
                        println(asDouble(i) / 2)
                        Long k = i
                        println(k + 1L)
                        Long z = 0L
                        z = i
                        println(z)
                    }
                    var b = m.get("big")
                    if (b != null) { println(asLong(b) * 100000L) }
                    println(widenReturn(9))
                }
                """, "7\n3.5\n8\n7\n10000000000\n9");
    }

    @ParameterizedTest
    @EnumSource(value = Target.class, names = {"ANDROID", "WASM", "WASI"}, mode = EnumSource.Mode.EXCLUDE)
    void otherNarrowedPrimitivesAndAbsence(Target target, @TempDir Path dir) throws Exception {
        run(target, dir, """
                main() {
                    Long? lo = 5000000000L
                    Double? d = 2.5
                    if (lo != null) { Long x = lo; println(x + 1L) }
                    if (d != null) { Double y = d; println(y * 2.0) }
                    var m = new Map<String, Int>()
                    var l = listOf("x")
                    var miss = m.get("nope")
                    if (miss != null) {
                        Int j = miss
                        println("bad " + l.get(j))
                    } else {
                        println("missing")
                    }
                    println(miss == null)
                    var o = m.getOrDefault("a", -1)
                    println(o)
                }
                """, "5000000001\n5.0\nmissing\ntrue\n-1");
    }

    /** §736 RE-MEASURED: o getter de campo {@code Int?} de record é um segundo produtor (Native imprimia lixo). */
    @ParameterizedTest
    @EnumSource(value = Target.class, names = {"ANDROID", "WASM", "WASI"}, mode = EnumSource.Mode.EXCLUDE)
    void narrowedNullableRecordFieldUsedAsPrimitive(Target target, @TempDir Path dir) throws Exception {
        run(target, dir, """
                record Box(Int? v, Long? w)
                main() {
                    var b = Box(5, 7000000000L)
                    var x = b.v
                    if (x != null) {
                        Int k = x
                        println("field=" + k)
                    }
                    var y = b.w
                    if (y != null) {
                        Long z = y
                        println(z + 1L)
                    }
                }
                """, "field=5\n7000000001");
    }

    @ParameterizedTest
    @EnumSource(value = Target.class, names = {"ANDROID", "WASM", "WASI"}, mode = EnumSource.Mode.EXCLUDE)
    void narrowedValuesIntoFieldsArraysCompoundAndChar(Target target, @TempDir Path dir) throws Exception {
        run(target, dir, """
                class C {
                    Int n
                    Long w
                    public constructor() { this.n = 0; this.w = 0L }
                    void absorb(Int? i) { if (i != null) { this.n = i; this.w = i } }
                }
                Char firstOr(Char? c) {
                    if (c != null) { Char z = c; return z }
                    return '-'
                }
                main() {
                    var c = new C()
                    c.absorb(5)
                    println(c.n)
                    println(c.w)
                    var m = new Map<String, Int>()
                    m.put("a", 1)
                    var arr = new Int[3]
                    var total = 10
                    Long big = 1L
                    var i = m.get("a")
                    if (i != null) {
                        arr[0] = i
                        arr[i] = 9
                        println(arr[i])
                        println(arr[0])
                        total += i
                        big += i
                        println(total)
                        println(big)
                    }
                    println(firstOr('K'))
                    println(firstOr(null))
                }
                """, "5\n5\n9\n1\n11\n2\nK\n-");
    }

    @ParameterizedTest
    @EnumSource(value = Target.class, names = {"ANDROID", "WASM", "WASI"}, mode = EnumSource.Mode.EXCLUDE)
    void narrowedValuesIntoListAndMapSlots(Target target, @TempDir Path dir) throws Exception {
        run(target, dir, """
                main() {
                    var m = new Map<String, Int>()
                    m.put("a", 3)
                    var l = listOf(1, 2, 3)
                    var ls = listOf(10L)
                    var i = m.get("a")
                    if (i != null) {
                        l.add(i)
                        l.set(0, i)
                        ls.add(i)
                        println(l.size())
                        println(l.get(0) + l.get(3))
                        println(ls.get(1) + 1L)
                        var m2 = new Map<String, Int>()
                        m2.put("b", i)
                        println(m2.get("b"))
                    }
                }
                """, "4\n6\n4\n3");
    }

    /** Estrutura JVM: {@code Int? → Long} abre o Integer (intValue) e alarga (I2L); nunca checkcast Long. */
    @Test
    void jvmWideningUnboxesTheSourceWrapperThenConverts(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("Main.kf");
        Files.writeString(file, """
                Long asLong(Long x) { return x }
                main() {
                    var m = new Map<String, Int>()
                    m.put("a", 7)
                    var i = m.get("a")
                    if (i != null) { println(asLong(i)) }
                }
                """);
        Path out = dir.resolve("out");
        CompilationResult result = new CompilerDriver().compile(file, out, Target.JVM);
        assertTrue(result.success(), result.diagnostics().getDiagnostics().toString());
        ClassNode cn = new ClassNode();
        try (InputStream in = Files.newInputStream(out.resolve("Default/Main.class"))) {
            new ClassReader(in).accept(cn, 0);
        }
        MethodNode main = cn.methods.stream().filter(m -> "main".equals(m.name)).findFirst().orElseThrow();
        List<String> trace = new ArrayList<>();
        for (AbstractInsnNode insn : main.instructions) {
            if (insn.getOpcode() == Opcodes.I2L) trace.add("I2L");
            if (insn instanceof TypeInsnNode t && t.getOpcode() == Opcodes.CHECKCAST) trace.add("checkcast " + t.desc);
            if (insn instanceof MethodInsnNode mi) trace.add(mi.owner + "." + mi.name + mi.desc);
        }
        int unbox = trace.indexOf("java/lang/Integer.intValue()I");
        int widen = trace.indexOf("I2L");
        int call = trace.indexOf("Default/Main.asLong(J)J");
        assertTrue(unbox >= 0 && unbox < widen && widen < call, "unbox → I2L → call esperado: " + trace);
        assertFalse(trace.contains("checkcast java/lang/Long"), "o wrapper real é Integer: " + trace);
    }

    @ParameterizedTest
    @EnumSource(value = Target.class, names = {"JVM", "JS"})
    void nonNarrowedNullableIndexIsRejected(Target target, @TempDir Path dir) throws Exception {
        // índice (método e `[]`, leitura e escrita) e elemento gravado em List<Int>, todos sem guarda
        for (String use : List.of("println(l.get(i))", "l.set(i, \"y\")", "println(l.remove(i))",
                "println(l.subList(0, i))", "println(l[i])", "println(arr[i])", "arr[i] = 4",
                "ns.add(i)", "ns.set(0, i)")) {
            Path file = dir.resolve("Main.kf");
            Files.writeString(file, """
                    main() {
                        var m = new Map<String, Int>()
                        m.put("a", 0)
                        var l = listOf("x")
                        var ns = listOf(1, 2)
                        var arr = new Int[2]
                        var i = m.get("a")
                        %s
                    }
                    """.formatted(use));
            CompilationResult result = new CompilerDriver().compile(file, dir.resolve("out"), target);
            assertFalse(result.success(), use + " sem guarda não pode compilar");
            assertTrue(result.diagnostics().getDiagnostics().stream().anyMatch(d -> "SEM014".equals(d.code())),
                    use + ": SEM014 esperado, veio " + result.diagnostics().getDiagnostics());
        }
    }

    /**
     * Controle: BUSCA com {@code T?} sem guarda segue legal (null nunca está numa
     * List&lt;Int&gt;) — o gate e a abertura da caixa valem só para índice e elemento
     * gravado. Native fica fora: o slot cru × arg boxed na busca é face própria.
     */
    @ParameterizedTest
    @EnumSource(value = Target.class, names = {"JVM", "SCRIPT", "JS"})
    void nonNarrowedNullableSearchStaysLegal(Target target, @TempDir Path dir) throws Exception {
        run(target, dir, """
                main() {
                    var m = new Map<String, Int>()
                    m.put("a", 2)
                    var l = listOf(1, 2, 3)
                    println(l.contains(m.get("a")))
                    println(l.contains(m.get("zz")))
                }
                """, "true\nfalse");
    }

    @Test
    void nonNarrowedNullableStoreStaysRejected(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("Main.kf");
        Files.writeString(file, """
                main() {
                    var m = new Map<String, Int>()
                    m.put("a", 0)
                    Int j = m.get("a")
                    println(j)
                }
                """);
        CompilationResult result = new CompilerDriver().compile(file, dir.resolve("out"), Target.JVM);
        assertFalse(result.success());
        assertTrue(result.diagnostics().getDiagnostics().stream().anyMatch(d -> "SEM021".equals(d.code())),
                result.diagnostics().getDiagnostics().toString());
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
                    // carga por reflexão: o launcher mascara a falha de load como "JavaFX ausente"
                    // (§556); -Xverify:all verifica a classe inteira, inclusive ramos não tomados
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
                            "-Xverify:all", "--class-path", out.toString(), runner.toString());
                } else if (target == Target.NATIVE) {
                    command = new ProcessBuilder(out.resolve("Default/Main").toString());
                } else {
                    command = NativeRiscv64E2ETest.qemu(target.nativeArch(), out.resolve("Default/Main"));
                }
                Path log = dir.resolve("process.log");
                Process process = command.redirectErrorStream(true).redirectOutput(log.toFile()).start();
                try {
                    assertTrue(process.waitFor(30, TimeUnit.SECONDS), target + " execution timed out");
                    output = new String(Files.readAllBytes(log), StandardCharsets.UTF_8);
                    assertEquals(0, process.exitValue(), target + ": " + output);
                } finally {
                    if (process.isAlive()) process.destroyForcibly();
                }
            }
        }
        assertEquals(expected, output.replace("\r\n", "\n").strip(), target.toString());
    }
}
