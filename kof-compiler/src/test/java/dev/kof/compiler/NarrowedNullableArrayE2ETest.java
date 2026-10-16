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
 * #878 + #879 (UMA unidade — o mesmo mecanismo): um array anulável de primitivo
 * ({@code Int[]?}, {@code Double[]?}) estreitado por {@code if (b != null)} era
 * indexado ({@code b[i]}, leitura e escrita) e percorrido ({@code for (var x in b)})
 * com o tipo do elemento {@code Unknown}: {@code Type.arrayElementType} só
 * reconhecia {@code ArrayType} puro e o tipo do local ESTREITADO no IR continua
 * {@code Nullable(ArrayType)} — o estreitamento é semântico, não físico (a mesma
 * família da #845). O load saía como referência: JVM {@code VerifyError: Bad type
 * on operand stack in aaload/aastore} (a classe nem carrega — o launcher ainda
 * mascara como "JavaFX ausente", §556) e os três nativos SIGSEGV. O Script sempre
 * esteve certo. {@code String[]?} nunca foi afetado (o elemento já é referência),
 * o que é o controle desta suíte.
 */
class NarrowedNullableArrayE2ETest {

    /** #878: leitura e escrita indexadas de {@code Int[]?}/{@code Double[]?} estreitados. */
    @ParameterizedTest
    @EnumSource(value = Target.class, names = {"ANDROID", "WASM", "WASI"}, mode = EnumSource.Mode.EXCLUDE)
    void narrowedNullablePrimitiveArrayReadAndWrite(Target target, @TempDir Path dir) throws Exception {
        run(target, dir, """
                Int[]? maybeInt(Bool ok) {
                    if (ok) {
                        var a = new Int[2]
                        a[0] = 7
                        a[1] = 9
                        return a
                    }
                    return null
                }
                Double[]? maybeDouble(Bool ok) {
                    if (ok) {
                        var a = new Double[2]
                        a[0] = 1.5
                        a[1] = 2.5
                        return a
                    }
                    return null
                }
                main() {
                    var b = maybeInt(true)
                    if (b != null) {
                        println(b[1])
                        b[0] = 5
                        println(b[0])
                        println(b.length)
                    }
                    var d = maybeDouble(true)
                    if (d != null) { println(d[1]) }
                    var n = maybeInt(false)
                    if (n != null) { println("bad") } else { println("null") }
                }
                """, "9\n5\n2\n2.5\nnull");
    }

    /** #879: {@code for-in} sobre o mesmo array estreitado (ramo {@code ArrayType} do StatementLowerer). */
    @ParameterizedTest
    @EnumSource(value = Target.class, names = {"ANDROID", "WASM", "WASI"}, mode = EnumSource.Mode.EXCLUDE)
    void narrowedNullablePrimitiveArrayForIn(Target target, @TempDir Path dir) throws Exception {
        run(target, dir, """
                Int[]? maybeInt(Bool ok) {
                    if (ok) {
                        var a = new Int[2]
                        a[0] = 7
                        a[1] = 9
                        return a
                    }
                    return null
                }
                Double[]? maybeDouble(Bool ok) {
                    if (ok) {
                        var a = new Double[2]
                        a[0] = 1.5
                        a[1] = 2.5
                        return a
                    }
                    return null
                }
                main() {
                    var b = maybeInt(true)
                    if (b != null) { for (var x in b) { println(x) } }
                    var d = maybeDouble(true)
                    if (d != null) { for (var y in d) { println(y) } }
                    var c = maybeInt(false)
                    if (c != null) { for (var z in c) { println(z) } } else { println("null") }
                }
                """, "7\n9\n1.5\n2.5\nnull");
    }

    /** Terceiro sítio: incremento composto em elemento (`b[0] += 3`) passa por outro lowering. */
    @ParameterizedTest
    @EnumSource(value = Target.class, names = {"ANDROID", "WASM", "WASI"}, mode = EnumSource.Mode.EXCLUDE)
    void narrowedNullablePrimitiveArrayCompoundIncrement(Target target, @TempDir Path dir) throws Exception {
        run(target, dir, """
                Int[]? maybeInt() {
                    var a = new Int[2]
                    a[0] = 7
                    a[1] = 9
                    return a
                }
                Double[]? maybeDouble() {
                    var a = new Double[1]
                    a[0] = 2.5
                    return a
                }
                main() {
                    var b = maybeInt()
                    if (b != null) {
                        b[0] += 3
                        println(b[0])
                    }
                    var d = maybeDouble()
                    if (d != null) {
                        d[0] += 0.5
                        println(d[0])
                    }
                }
                """, "10\n3.0");
    }

    /** Controle: {@code String[]?} estreitado já funcionava (o elemento é referência) — não pode regredir. */
    @ParameterizedTest
    @EnumSource(value = Target.class, names = {"ANDROID", "WASM", "WASI"}, mode = EnumSource.Mode.EXCLUDE)
    void narrowedNullableStringArrayUnaffected(Target target, @TempDir Path dir) throws Exception {
        run(target, dir, """
                String[]? maybe(Bool ok) {
                    if (ok) {
                        var a = new String[2]
                        a[0] = "a"
                        a[1] = "b"
                        return a
                    }
                    return null
                }
                main() {
                    var b = maybe(true)
                    if (b != null) {
                        println(b[1])
                        for (var s in b) { println(s) }
                    }
                }
                """, "b\na\nb");
    }

    /**
     * Estrutura JVM: o elemento do {@code Int[]?} estreitado é {@code Int}, então a
     * leitura indexada e o {@code for-in} têm de emitir {@code IALOAD} — nunca
     * {@code AALOAD} (o defeito exato do reporter).
     */
    @Test
    void jvmEmitsElementTypedLoadForNarrowedPrimitiveArray(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("Main.kf");
        Files.writeString(file, """
                Int[]? maybe(Bool ok) {
                    if (ok) {
                        var a = new Int[2]
                        a[0] = 7
                        a[1] = 9
                        return a
                    }
                    return null
                }
                main() {
                    var b = maybe(true)
                    if (b != null) {
                        println(b[1])
                        for (var x in b) { println(x) }
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
        assertTrue(opcodes.contains(Opcodes.IALOAD), "IALOAD esperado (elemento Int); opcodes=" + opcodes);
        assertFalse(opcodes.contains(Opcodes.AALOAD), "AALOAD sobre [I é o VerifyError do #878/#879; opcodes=" + opcodes);
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
