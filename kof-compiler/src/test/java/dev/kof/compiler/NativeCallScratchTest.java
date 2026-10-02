package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §543 — o call-site x86-64 guardava os argumentos de pilha (6+/7+) em slots
 * de rascunho de offset FIXO {@code -256-s*8(%rbp)}, que colidiam com locais
 * REAIS quando o método tinha mais de 32 slots: o valor do local era
 * sobrescrito pelo argumento (corrupção silenciosa, sem crash). Medido no
 * x86: {@code v31} de 31000 virava 6 (o 6.º argumento). O rascunho agora fica
 * abaixo dos locais ({@code NativeBackend#scratchOffset}) e o frame reserva
 * {@code maxCallStackArgs} slots para TODO call — o JVM é o oráculo.
 * Paridade: o mesmo programa rodava correto no riscv64/aarch64 (convenção
 * a0..a7, sem o bug) e segue correto.
 */
class NativeCallScratchTest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    /** 40 locais > 32 slots: o rascunho antigo (offset 256) caía sobre v31. */
    private static String instanceCallProgram() {
        StringBuilder src = new StringBuilder();
        src.append("class C {\n")
           .append("    Int m(Int a, Int b, Int c, Int d, Int e, Int f) { return a + b + c + d + e + f }\n")
           .append("}\n")
           .append("main() {\n");
        for (int i = 0; i < 40; i++) src.append("    var v").append(i).append(" = ").append(i * 1000).append("\n");
        src.append("    var c = C()\n")
           .append("    var s = c.m(1, 2, 3, 4, 5, 6)\n")
           .append("    println(s)\n");
        for (int i = 28; i < 40; i++) src.append("    println(v").append(i).append(")\n");
        src.append("}\n");
        return src.toString();
    }

    /** Função static de 10 params (ramo geral, 4 args de pilha). */
    private static String staticCallProgram() {
        StringBuilder src = new StringBuilder();
        src.append("Int g(Int a, Int b, Int c, Int d, Int e, Int f, Int g, Int h, Int i, Int j) {\n")
           .append("    return a + j\n")
           .append("}\n")
           .append("main() {\n");
        for (int i = 0; i < 40; i++) src.append("    var v").append(i).append(" = ").append(i * 1000).append("\n");
        src.append("    println(g(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))\n")
           .append("    println(v31)\n")
           .append("    println(v38)\n")
           .append("}\n");
        return src.toString();
    }

    private static final String INSTANCE_GOLDEN =
            "21\n28000\n29000\n30000\n31000\n32000\n33000\n34000\n35000\n36000\n37000\n38000\n39000";
    private static final String STATIC_GOLDEN = "11\n31000\n38000";

    private Path compile(String program, String dir, Target target) throws Exception {
        Path root = tmp.resolve(dir);
        Files.createDirectories(root);
        Path source = root.resolve("Main.kf");
        Files.writeString(source, program);
        Path out = root.resolve("out");
        CompilationResult result = driver.compile(source, out, target);
        assertTrue(result.success(), () -> "compile " + target + ": " + result.diagnostics().getDiagnostics());
        return out.resolve("Default/Main");
    }

    private static String runX86(Path binary) throws Exception {
        Process process = new ProcessBuilder(binary.toString()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, process.waitFor(), "x86 output: " + output);
        return output;
    }

    @Test
    void instanceCallScratchDoesNotClobberLocalsOnX86() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        String output = runX86(compile(instanceCallProgram(), "x86-inst", Target.NATIVE));
        assertEquals(INSTANCE_GOLDEN, output);
    }

    @Test
    void staticCallScratchDoesNotClobberLocalsOnX86() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        String output = runX86(compile(staticCallProgram(), "x86-static", Target.NATIVE));
        assertEquals(STATIC_GOLDEN, output);
    }

    @Test
    void instanceCallScratchDoesNotClobberLocalsOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(NativeRiscv64E2ETest.hasToolchain("riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        Path binary = compile(instanceCallProgram(), "riscv-inst", Target.NATIVE_RISCV64);
        assertEquals(INSTANCE_GOLDEN, NativeRiscv64E2ETest.runQemu("riscv64", binary).trim());
    }

    @Test
    void instanceCallScratchDoesNotClobberLocalsOnNativeAarch64() throws Exception {
        Assumptions.assumeTrue(NativeRiscv64E2ETest.hasToolchain("aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        Path binary = compile(instanceCallProgram(), "aarch-inst", Target.NATIVE_AARCH64);
        assertEquals(INSTANCE_GOLDEN, NativeRiscv64E2ETest.runQemu("aarch64", binary).trim());
    }
}
