package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * §546 (#703, linhas 1.0): o backend cross riscv64/aarch64 corrompia chamadas
 * com 9+ argumentos inteiros (incl. o `this` implícito). LP64/AAPCS64 tem só
 * a0..a7; o emit anterior clampava o registrador (`crossArgReg` devolvia "a7"
 * para qualquer índice ≥ 8) e o prólogo do callee ignorava args fora dos 8
 * registradores — as duas pontas: args passados à pilha eram lidos como lixo
 * (`f9(1..9)` = 36 em vez de 45) e argumentos se sobrescreviam em a7.
 *
 * <p>O fix porta o esquema `S7f` do x86-64: o call-site despeja os args no
 * rascunho do frame, carrega a0..a7 e re-empurra os args 9+ em ordem reversa
 * (arg8 no menor endereço); o callee os relê de {@code 8*(i-8)(s11)}. A
 * prova é a paridade R5 byte a byte com o oráculo JVM nas três aridades:
 * função 9-arg, método de instância com 8 params explícitos (>8 contando
 * `this`) e record de 8 campos (ctor com `this`).
 */
class NativeCrossWideArgsE2ETest extends KofStringsSupport {

    private static final String PROGRAM = """
            Int f9(Int a, Int b, Int c, Int d, Int e, Int f, Int g, Int h, Int i) {
                return a + b + c + d + e + f + g + h + i
            }
            class M {
                Int v
                public constructor(Int v) {
                    this.v = v
                }
                Int m8(Int a, Int b, Int c, Int d, Int e, Int f, Int g, Int h) {
                    return v + a + b + c + d + e + f + g + h
                }
            }
            record Wide(Int a, Int b, Int c, Int d, Int e, Int f, Int g, Int h)
            main() {
                println(f9(1, 2, 3, 4, 5, 6, 7, 8, 9))
                println(M(1000).m8(1, 2, 3, 4, 5, 6, 7, 8))
                var w = Wide(1, 2, 3, 4, 5, 6, 7, 8)
                println(w.a() + w.b() + w.c() + w.d() + w.e() + w.f() + w.g() + w.h())
            }
            """;
    private static final String GOLDEN = "45\n1036\n36";

    @Test
    void jvmOracle(@TempDir Path t) throws Exception {
        runJvm(t, PROGRAM, GOLDEN);
    }

    @Test
    void riscv64WideArgsMatchJvm(@TempDir Path t) throws Exception {
        crossMatch(t, Target.NATIVE_RISCV64, "riscv64");
    }

    @Test
    void aarch64WideArgsMatchJvm(@TempDir Path t) throws Exception {
        crossMatch(t, Target.NATIVE_AARCH64, "aarch64");
    }

    private void crossMatch(Path tempDir, Target target, String arch) throws Exception {
        assumeTrue(NativeRiscv64E2ETest.hasToolchain(arch),
                "toolchain cross " + arch + " + qemu ausente — pulando (§546)");
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, PROGRAM);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, target);
        assertTrue(result.success(), target + " compile failed: "
                + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        Process p = NativeRiscv64E2ETest.qemu(arch, bin).redirectErrorStream(true).start();
        String output = NativeRiscv64E2ETest.runBounded(p, arch + " binary");
        assertEquals(0, p.exitValue(), arch + " exit, out: " + output);
        assertEquals(GOLDEN, output, arch + " deve casar o oráculo JVM (regra 5, §546)");
    }
}
