package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * §284 (18/09) — box de erasure no nativo. Antes do fix, {@code var o: Object =
 * 99} + {@code println(o)} SIGSEGVava (kof_box/kof_unbox eram no-op e o
 * dispatcher valueOf(Object) lia o tag do box como ponteiro de vtable). RED da
 * prova Q0: exit 139 / saida vazia nos tres targets nativos.
 *
 * Golden = medido 18/09 no tip pos-§286 (x86 real + riscv64/aarch64 sob qemu,
 * md5 identico nos tres): char box imprime o codigo ("97", paridade JVM), long
 * usa conversao de 64 bits (sem trunc), `as Int` sobre box Long arromba com o
 * diagnostico compartilhado (RuntimeErasureBox.UNBOX_MSG — fonte unica dos tres
 * backends), String/nao-box passa cru.
 */
class NativeErasureBoxE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String PROGRAM = """
            class C { Object o }
            Int peek(Object v) { var i = v as Int; return i + 1 }
            main() {
                var o: Object = 99
                println(o)
                var c = C()
                c.o = 1234567890123
                println(c.o)
                c.o = true
                println(c.o)
                c.o = 1.5
                println(c.o)
                c.o = 'a'
                println(c.o)
                println(peek(41))
                var back: Int = c.o as Int
                println(back)
                val s: Object = "txt"
                println(s)
                var big: Object = 1234567890123
                try { var bad: Int = big as Int; println("no-throw " + bad) }
                catch (String e) { println("caught: " + e) }
            }
            """;

    /**
     * §284-map (18/09) — contrato FISICO do slot de Map no nativo: valor da
     * familia {@code int/char/short/byte/long} vive em caixa MAGIC (escrita no
     * lowerer — put/literal/getOrDefault), leitura `get` com tipo concreto
     * desembala no call-site (soft), `println(map)` imprime pela tag 7,
     * `values()` normaliza caixa→cru na fonte, `==` nullable passa pelo
     * kof_box_equals null-seguro (JVM oracle: 18/09, `java run` medido).
     */
    private static final String PROGRAM_MAP = """
            main() {
                var m = mapOf()
                m.put("a", 1)
                m.put("b", 2)
                println(m.get("a"))
                println(m.get("a") == 1)
                var big = mapOf()
                big.put("L", 5000000000)
                println(big.get("L"))
                println(big.get("L") == 5000000000)
                var g = mapOf("k", 7)
                println(g.get("zz"))
                println(m.getOrDefault("zz", 9))
                println(m)
                var vs = m.values()
                println(vs)
                println(vs.get(0) == 1)
                var e = mapOf("x", 1)
                println(e.get("x") + 41)
                println(e.get("q") == e.get("r"))
                var b = mapOf("t", true)
                println(b.get("t"))
                println(b.get("t") == true)
                println(b)
                var d = mapOf("d", 2.5)
                println(d.get("d"))
                println(d.get("d") > 1.0)
                println(d)
                var bm = mapOf()
                bm.put("k", true)
                println(bm.get("k") == true)
                println(bm)
                var dm = mapOf()
                dm.put("k", 3.5)
                println(dm.get("k"))
            }
            """;

    private static String goldenMap() {
        return String.join("\n",
                "1", "true", "5000000000", "true", "null", "9", "{a=1, b=2}",
                "[1, 2]", "true", "42", "true",
                "true", "true", "{t=true}", "2.5", "true", "{d=2.5}",
                "true", "{k=true}", "3.5");
    }

    private static String golden() {
        return String.join("\n",
                "99", "1234567890123", "true", "1.5", "97", "42", "97", "txt",
                "caught: " + dev.kof.compiler.runtime.RuntimeErasureBox.UNBOX_MSG);
    }

    @Test
    void erasureBoxNativeX86(@TempDir Path tempDir) throws IOException {
        runAndAssert(tempDir, Target.NATIVE, "Default/Main", PROGRAM, golden());
    }

    @Test
    void erasureBoxRiscv64(@TempDir Path tempDir) throws IOException {
        assumeTrue(NativeRiscv64E2ETest.hasToolchain("riscv64"),
                "toolchain riscv64/qemu ausente — pulando (NATIVE002)");
        runAndAssert(tempDir, Target.NATIVE_RISCV64, "Default/Main", PROGRAM, golden());
    }

    @Test
    void erasureBoxAarch64(@TempDir Path tempDir) throws IOException {
        assumeTrue(NativeRiscv64E2ETest.hasToolchain("aarch64"),
                "toolchain aarch64/qemu ausente — pulando (NATIVE002)");
        runAndAssert(tempDir, Target.NATIVE_AARCH64, "Default/Main", PROGRAM, golden());
    }

    @Test
    void mapBoxedSlotNativeX86(@TempDir Path tempDir) throws IOException {
        runAndAssert(tempDir, Target.NATIVE, "Default/Main", PROGRAM_MAP, goldenMap());
    }

    @Test
    void mapBoxedSlotRiscv64(@TempDir Path tempDir) throws IOException {
        assumeTrue(NativeRiscv64E2ETest.hasToolchain("riscv64"),
                "toolchain riscv64/qemu ausente — pulando (NATIVE002)");
        runAndAssert(tempDir, Target.NATIVE_RISCV64, "Default/Main", PROGRAM_MAP, goldenMap());
    }

    @Test
    void mapBoxedSlotAarch64(@TempDir Path tempDir) throws IOException {
        assumeTrue(NativeRiscv64E2ETest.hasToolchain("aarch64"),
                "toolchain aarch64/qemu ausente — pulando (NATIVE002)");
        runAndAssert(tempDir, Target.NATIVE_AARCH64, "Default/Main", PROGRAM_MAP, goldenMap());
    }

    private void runAndAssert(Path tempDir, Target target, String relBin, String program,
                              String expected) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, program);
        Path outDir = tempDir.resolve("out-" + target.name());
        CompilationResult result = driver.compile(source, outDir, target);
        assertTrue(result.success(), target + " compile should succeed: "
                + result.diagnostics().getDiagnostics());
        Path binFile = outDir.resolve(relBin);
        assertTrue(Files.exists(binFile), target + " binary should exist");
        ProcessBuilder pb = new ProcessBuilder(binFile.toString());
        if (target == Target.NATIVE_RISCV64) {
            pb = NativeRiscv64E2ETest.qemu("riscv64", binFile);
        } else if (target == Target.NATIVE_AARCH64) {
            pb = NativeRiscv64E2ETest.qemu("aarch64", binFile);
        }
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        int ec;
        try {
            ec = p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted running " + target + " binary", e);
        }
        assertEquals(0, ec, target + " exit code should be 0 (Q0: pre-fix SIGSEGV=139), output: '" + output + "'");
        assertEquals(expected, output, target + " erasure-box output mismatch");
    }
}
