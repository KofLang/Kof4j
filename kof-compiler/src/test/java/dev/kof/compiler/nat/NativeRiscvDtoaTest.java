package dev.kof.compiler.nat;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FLT001 (NATIVE002, 15/09): prova em NÍVEL DE SLICE do
 * {@code kof_double_to_string}/{@code kof_float_to_string} (fatia B45) nos
 * DOIS alvos cross. Diferente do E2E via compilador (NativeRiscv64E2ETest /
 * NativeAarch64E2ETest), aqui varre uma TABELA grande de valores (doubles +
 * floats) e compara com o oráculo do JVM ({@code Double.toString}/
 * {@code Float.toString}) — a mesma semântica §180 (decimal MAIS CURTO que faz
 * round-trip, limiar científico do Java, NaN/±Inf normalizados).
 *
 * <p>O harness monta o runtime PODADO (produção: B45 entra porque o harness
 * chama os helpers) e linka DINAMICAMENTE (a B45 usa libc snprintf/strtod).
 * A sabotagem REMOVE a peça B45 do keep: o {@code ld} falha com referência
 * indefinida — prova que o teste realmente exercita a fatia nova.
 */
class NativeRiscvDtoaTest {

    private static boolean has(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c).redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                if (p.waitFor() != 0 || out.isEmpty()) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private void assumeToolchain() {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando");
        Assumptions.assumeTrue(NativeCrossLink.sysrootFor("riscv64") != null,
                "libc cross ausente (KOF_CROSS_SYSROOT / /tmp/opencode/x) — pulando");
    }

    private void assumeAarch64() {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando");
        Assumptions.assumeTrue(NativeCrossLink.sysrootFor("aarch64") != null,
                "libc cross ausente (KOF_CROSS_SYSROOT / /tmp/opencode/x) — pulando");
    }

    private static final double[] DOUBLES = {
            3.14, -1.0, 0.5, 100.0, 1.0 / 3.0, 0.1, 1e7, 9999999.0, 1e-3, 9.99e-4,
            123456.789, 1.5e7, 1.23456789e9, 1e21, 1.7976931348623157e308,
            2.2250738585072014e-308, 0.0, -0.0, 1.0, 1.0e20, 1.0e-5, 123.0,
            1230000.0, 12300000.0, 0.001, 0.0009999, 6.02e23, -3.05e-7, 2.5, 1000000.0,
            3.5, 250.0, -0.75,
            // §448: subnormais onde o loop snprintf/strtod escolhia o mais curto
            // (5.0E-324) e o JVM/Schubfach escolhe o mais proximo (4.9E-324).
            Double.MIN_VALUE, Double.longBitsToDouble(2L), Double.longBitsToDouble(3L),
            Double.longBitsToDouble(5L), Double.longBitsToDouble(1000L),
            Double.longBitsToDouble((1L << 52) - 1), Double.MIN_NORMAL,
            -Double.MIN_VALUE, Double.longBitsToDouble(1L << 51),
    };

    private static final float[] FLOATS = {
            3.14f, -1.0f, 0.5f, 1e7f, 9999999f, 1e-3f, 1.17549435e-38f,
            3.4028235e38f, 0.1f, 1.0f / 3.0f, 123456.78f, 2.5f, 100.0f, 1e-5f,
            0.001f, 0.0f, -0.0f, 6.02e23f, 7.5f, -12.25f,
            // §448 cross: subnormais de Float (mesma face do Double).
            Float.MIN_VALUE, Float.intBitsToFloat(2), Float.intBitsToFloat(3),
            Float.intBitsToFloat(5), Float.intBitsToFloat((1 << 23) - 1),
            Float.MIN_NORMAL, -Float.MIN_VALUE,
    };

    private static String oracle() {
        StringBuilder sb = new StringBuilder();
        for (double d : DOUBLES) sb.append(Double.toString(d)).append('\n');
        for (float f : FLOATS) sb.append(Float.toString(f)).append('\n');
        return sb.toString().trim();
    }

    /** Harness que imprime cada valor pela helper correspondente. */
    private static String harness() {
        StringBuilder sb = new StringBuilder();
        // raízes GC vazias (contrato do emitter); o harness de Dtoa não aloca
        // objetos na stack, mas o runtime podado referencia os rótulos.
        sb.append(".section .data\n.align 3\n");
        sb.append(".Lkof_heap_root_start:\n.Lkof_heap_root_end:\n");
        sb.append(".section .text\n.globl _start\n_start:\n");
        for (double d : DOUBLES) {
            sb.append("    li a0, ").append(Double.doubleToRawLongBits(d)).append('\n');
            sb.append("    call kof_double_to_string\n");
            sb.append("    call kof_println_string\n");
        }
        for (float f : FLOATS) {
            sb.append("    li a0, ").append(Float.floatToRawIntBits(f)).append('\n');
            sb.append("    call kof_float_to_string\n");
            sb.append("    call kof_println_string\n");
        }
        sb.append("    li a0, 0\n    li a7, 93\n    ecall\n");
        // externo program-side do runtime (vazio neste harness).
        sb.append(".globl kof_super_table\nkof_super_table:\n    .word 0\n");
        sb.append(".globl kof_equals_table\nkof_equals_table:\n    .quad 0\n"
                + ".globl kof_hashcode_table\nkof_hashcode_table:\n    .quad 0\n");
        return sb.toString();
    }

    private String runCapture(String... cmd) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        try {
            int ec = p.waitFor();
            assertEquals(0, ec, "comando falhou (" + ec + "): " + String.join(" ", cmd) + "\n" + out);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        return out;
    }

    private String[] runAllowFail(String... cmd) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        try {
            return new String[]{out, String.valueOf(p.waitFor())};
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    private String buildDynamic(String arch, Path tempDir, String name, String asmText) throws IOException {
        Path asm = tempDir.resolve(name + ".s");
        Files.writeString(asm, asmText);
        Path obj = tempDir.resolve(name + ".o");
        Path bin = tempDir.resolve(name);
        String as = arch.equals("riscv64") ? "riscv64-linux-gnu-as" : "aarch64-linux-gnu-as";
        String ld = arch.equals("riscv64") ? "riscv64-linux-gnu-ld" : "aarch64-linux-gnu-ld";
        if (arch.equals("riscv64")) {
            runCapture(as, "-mno-relax", "-o", obj.toString(), asm.toString());
        } else {
            runCapture(as, "-o", obj.toString(), asm.toString());
        }
        String sysroot = NativeCrossLink.sysrootFor(arch);
        runCapture(NativeCrossLink.ldArgs(ld, bin, obj, arch, true, sysroot));
        bin.toFile().setExecutable(true);
        String prefix = NativeCrossLink.qemuPrefixFor(arch);
        java.util.Map<String, String> env = prefix == null ? java.util.Map.of()
                : java.util.Map.of("QEMU_LD_PREFIX", prefix);
        return QemuRun.runExpect0(env, "qemu-" + arch, bin.toString());
    }

    @Test
    void dtoaMatchesJvmOracleOnRiscv64(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String harness = harness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String out = buildDynamic("riscv64", tempDir, "dtoarv", harness + "\n" + runtime);
        assertEquals(oracle(), out);
    }

    @Test
    void dtoaMatchesJvmOracleOnAarch64(@TempDir Path tempDir) throws IOException {
        assumeAarch64();
        String harness = harness();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness);
        String riscv = harness + "\n" + runtime;
        StringBuilder arm = new StringBuilder();
        for (String line : riscv.split("\n", -1)) {
            for (String t : NativeAarch64Translator.translateRiscvToAarch64(line)) arm.append(t).append('\n');
        }
        String out = buildDynamic("aarch64", tempDir, "dtoaaa", arm.toString());
        assertEquals(oracle(), out);
    }

    /** §448: o dtoa cross e libc-free — o runtime podado do harness nao pode
     *  referenciar snprintf/strtod (antes eram o motor do loop mais-curto). */
    @Test
    void dtoaPrunedRuntimeHasNoLibcFormatRefs() {
        assumeToolchain();
        String runtime = RiscvGcTestRuntimes.prunedFor(harness());
        assertFalse(runtime.contains("call snprintf"), "dtoa riscv nao deve chamar snprintf");
        assertFalse(runtime.contains("call strtod"), "dtoa riscv nao deve chamar strtod");
    }

    @Test
    void withoutDtoaSliceLinkFailsSabotage(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String harness = harness();
        Set<Integer> keep = new LinkedHashSet<>(RiscvSlices.keepForProgramText(harness));
        int b45 = -1;
        for (RiscvSlices.Piece p : RiscvSlices.pieces()) {
            if ("RISCV_RUNTIME_ASM_B_45".equals(p.field())) b45 = p.index();
        }
        assertTrue(b45 >= 0, "peça B45 não encontrada no inventário");
        assertTrue(keep.remove(b45), "B45 deveria estar no keep do harness de Dtoa");
        String runtime = RiscvSlices.renderSubset(keep);
        Path asm = tempDir.resolve("sab.s");
        Files.writeString(asm, harness + "\n" + runtime);
        Path obj = tempDir.resolve("sab.o");
        Path bin = tempDir.resolve("sab");
        runCapture("riscv64-linux-gnu-as", "-mno-relax", "-o", obj.toString(), asm.toString());
        String[] r = runAllowFail("riscv64-linux-gnu-ld", "--no-relax", "-o", bin.toString(), obj.toString());
        assertNotEquals("0", r[1], "sem a B45 o link deveria falhar (undefined kof_double_to_string); saída: " + r[0]);
        assertTrue(r[0].contains("kof_double_to_string") || r[0].contains("kof_float_to_string"),
                "a falha deve citar a helper de Dtoa: " + r[0]);
    }
}
