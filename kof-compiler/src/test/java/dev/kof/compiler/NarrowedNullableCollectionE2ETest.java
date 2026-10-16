package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * #882 — a face IRMÃ do #878/#879 (esses ficaram restritos a ARRAYS). Um receptor
 * de COLEÇÃO anulável ESTREITADO (`List<T>?`/`Map<K,V>?` após `if (l != null)`)
 * continua `Nullable(ClassType)` no IR — o estreitamento é semântico — e
 * `BuiltinTypes.isList`/`isMap`/`isSet` só reconhecem `ClassType` puro: o receptor
 * caía no caminho de ARRAY. JVM: `l[1]` → {@code VerifyError: Bad type on operand
 * stack in aaload} (AALOAD sobre {@code java/util/ArrayList}) e {@code for (var s in l)}
 * → {@code VerifyError … in arraylength}; o Script sempre esteve certo. O que já
 * funcionava no mesmo receptor (resolução semântica, não lowering) fica como
 * CONTROLE: {@code .get()}, {@code .size()}.
 */
class NarrowedNullableCollectionE2ETest {

    /** #882: leitura indexada de {@code List<String>?} estreitada. */
    @ParameterizedTest
    @EnumSource(value = Target.class, names = {"ANDROID", "WASM", "WASI"}, mode = EnumSource.Mode.EXCLUDE)
    void narrowedNullableListIndexedRead(Target target, @TempDir Path dir) throws Exception {
        run(target, dir, """
                List<String>? maybeList(Bool ok) {
                    if (ok) { return listOf("a", "b") }
                    return null
                }
                List<Int>? maybeInts(Bool ok) {
                    if (ok) { return listOf(7, 9) }
                    return null
                }
                main() {
                    var l = maybeList(true)
                    if (l != null) {
                        println(l[1])
                        println(l[0])
                        println(l.size())
                        println(l.get(1))
                    }
                    var n = maybeInts(true)
                    if (n != null) {
                        println(n[1])
                        println(n[0] + n[1])
                    }
                    var z = maybeList(false)
                    if (z != null) { println("bad") } else { println("null") }
                }
                """, "b\na\n2\nb\n9\n16\nnull");
    }

    /** #882: o `for-in` sobre a coleção anulável estreitada (era `arraylength` sobre ArrayList). */
    @ParameterizedTest
    @EnumSource(value = Target.class, names = {"ANDROID", "WASM", "WASI"}, mode = EnumSource.Mode.EXCLUDE)
    void narrowedNullableListForIn(Target target, @TempDir Path dir) throws Exception {
        run(target, dir, """
                List<String>? maybeList(Bool ok) {
                    if (ok) { return listOf("a", "b") }
                    return null
                }
                List<Int>? maybeInts(Bool ok) {
                    if (ok) { return listOf(7, 9) }
                    return null
                }
                main() {
                    var l = maybeList(true)
                    if (l != null) { for (var s in l) { println(s) } }
                    var n = maybeInts(true)
                    if (n != null) { for (var x in n) { println(x + 1) } }
                    var z = maybeList(false)
                    if (z != null) { for (var s in z) { println(s) } } else { println("null") }
                }
                """, "a\nb\n8\n10\nnull");
    }

    /**
     * #882: {@code Map<String,Int>?} estreitado. O mapa indexado ({@code m[k]}) NÃO é
     * sintaxe de Kof (o `SEM054` diz "use get(k)"), mas o RECEPTOR anulado estreitado
     * percorre o mesmo despacho do lowerer — aqui como controle do caminho de coleção.
     */
    @ParameterizedTest
    @EnumSource(value = Target.class, names = {"ANDROID", "WASM", "WASI"}, mode = EnumSource.Mode.EXCLUDE)
    void narrowedNullableMapGetAndSize(Target target, @TempDir Path dir) throws Exception {
        run(target, dir, """
                Map<String, Int>? maybeMap(Bool ok) {
                    if (ok) { return mapOf("a", 1, "b", 2) }
                    return null
                }
                main() {
                    var m = maybeMap(true)
                    if (m != null) {
                        println(m.get("b"))
                        println(m.get("a") + m.get("b"))
                        println(m.size())
                    }
                    var z = maybeMap(false)
                    if (z != null) { println("bad") } else { println("null") }
                }
                """, "2\n3\n2\nnull");
    }

    /** Controle: a coleção NÃO anulável segue idêntica (leitura, `for-in`, `get`/`size`). */
    @ParameterizedTest
    @EnumSource(value = Target.class, names = {"ANDROID", "WASM", "WASI"}, mode = EnumSource.Mode.EXCLUDE)
    void nonNullableCollectionUnaffected(Target target, @TempDir Path dir) throws Exception {
        run(target, dir, """
                main() {
                    var l = listOf("a", "b")
                    println(l[1])
                    for (var s in l) { println(s) }
                    println(l.get(0))
                    println(l.size())
                    var m = mapOf("a", 1)
                    println(m.get("a"))
                }
                """, "b\na\nb\na\n2\n1");
    }

    /**
     * Estrutura JVM: o receptor de coleção tem de baixar para o get de runtime
     * ({@code kof_list_get}) — {@code AALOAD}/{@code ARRAYLENGTH} sobre um
     * {@code java/util/ArrayList} é exatamente o {@code VerifyError} do #882.
     */
    @Test
    void jvmUsesRuntimeGetForNarrowedNullableList(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("Main.kf");
        Files.writeString(file, """
                List<String>? maybe(Bool ok) {
                    if (ok) { return listOf("a", "b") }
                    return null
                }
                main() {
                    var l = maybe(true)
                    if (l != null) {
                        println(l[1])
                        for (var s in l) { println(s) }
                    }
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
        Set<Integer> opcodes = new HashSet<>();
        for (AbstractInsnNode insn : main.instructions) {
            opcodes.add(insn.getOpcode());
        }
        assertFalse(opcodes.contains(Opcodes.AALOAD), "AALOAD sobre ArrayList é o VerifyError do #882: " + opcodes);
        assertFalse(opcodes.contains(Opcodes.ARRAYLENGTH), "ARRAYLENGTH sobre ArrayList é o VerifyError do #882: " + opcodes);
        assertTrue(opcodes.contains(Opcodes.INVOKESTATIC), "o get de runtime é esperado: " + opcodes);
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
