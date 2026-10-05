package dev.kof.compiler.nat;

import dev.kof.compiler.NativeToolchainAssumptions;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * D-MEMORY-CLEAR (O-03/`MEM003`) — PROVA DE RUNTIME do contrato: {@code clear()}
 * anula CADA slot antes de encolher, entao o container nao retem mais nenhuma
 * referencia depois do clear. Nao ha face de compile ({@code MEM003} nao e
 * diagnostico); a garantia e medida aqui, no asm de producao podado.
 *
 * <p>Harness cru ({@code _start}) nos DOIS alvos cross: a lista recebe 3
 * sentinelas pelo caminho real {@code kof_list_add} e o map recebe 3 pares
 * key/val plantados nos slots; apos {@code kof_list_clear}/{@code kof_map_clear}
 * o harness faz OR dos slots LIDOS DA MEMORIA — qualquer resíduo != 0 falha
 * (exit 1). O OR-antes!=0 garante que a lista nao esta vazia (teste nao
 * vacuo). O runtime e podado pela PRODUCAO ({@link RiscvGcTestRuntimes#prunedFor}).
 */
class NativeRiscvMemClearTest implements NativeToolchainAssumptions {

    private static boolean has(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c)
                        .redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                if (p.waitFor() != 0 || out.isEmpty()) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private void assumeAarch64() {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (D-MEMORY-CLEAR)");
    }

    /** Lista: 3 sentinelas via add real; clear; OR dos 3 slots == 0; size == 0.
     *  Map: 3 pares key/val plantados; clear; OR de keys+vals == 0; size == 0. */
    private static final String HARNESS_CLEAR = """
            .option arch, rv64g
            .section .data
            .align 3
            .Lkof_heap_root_start:
                .quad 0
            .Lkof_heap_root_end:
            .section .text
            .globl _start
            _start:
                andi sp, sp, -16

                # ---- list: 3 sentinelas via kof_list_add ----
                call kof_list_new
                mv   s0, a0
                mv   a0, s0
                li   a1, 0x1111
                call kof_list_add
                mv   a0, s0
                li   a1, 0x2222
                call kof_list_add
                mv   a0, s0
                li   a1, 0x3333
                call kof_list_add
                ld   t0, 24(s0)
                ld   t1, 0(t0)
                ld   t2, 8(t0)
                ld   t3, 16(t0)
                or   s1, t1, t2
                or   s1, s1, t3
                mv   a0, s0
                call kof_list_clear
                ld   t0, 24(s0)
                ld   t1, 0(t0)
                ld   t2, 8(t0)
                ld   t3, 16(t0)
                or   t1, t1, t2
                or   t1, t1, t3
                beq  s1, x0, .Lfail      # antes==0 => teste vacuo
                bne  t1, x0, .Lfail      # algum slot NAO anulado
                lw   t2, 16(s0)
                bne  t2, x0, .Lfail      # size != 0

                # ---- map: 3 pares key/val plantados nos slots ----
                call kof_map_new
                mv   s2, a0
                li   t0, 3
                sw   t0, 16(s2)
                ld   t1, 24(s2)
                ld   t2, 32(s2)
                li   t3, 0x4444
                sd   t3, 0(t1)
                li   t3, 0x5555
                sd   t3, 8(t1)
                li   t3, 0x6666
                sd   t3, 16(t1)
                li   t3, 0x7777
                sd   t3, 0(t2)
                li   t3, 0x8888
                sd   t3, 8(t2)
                li   t3, 0x9999
                sd   t3, 16(t2)
                mv   a0, s2
                call kof_map_clear
                lw   t0, 16(s2)
                bne  t0, x0, .Lfail      # size != 0
                ld   t1, 24(s2)
                ld   t2, 32(s2)
                ld   t3, 0(t1)
                ld   t4, 8(t1)
                ld   t5, 16(t1)
                or   t3, t3, t4
                or   t3, t3, t5
                ld   t4, 0(t2)
                ld   t5, 8(t2)
                ld   t6, 16(t2)
                or   t4, t4, t5
                or   t4, t4, t6
                or   t3, t3, t4
                bne  t3, x0, .Lfail      # algum slot NAO anulado

                li   a0, 0
                li   a7, 93
                ecall
            .Lfail:
                li   a0, 1
                li   a7, 93
                ecall
            .globl kof_super_table
            kof_super_table:
                .word 0
                .globl kof_equals_table
                kof_equals_table:
                .quad 0
                .globl kof_hashcode_table
                kof_hashcode_table:
                .quad 0
            """;

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

    private String buildRiscv(Path tempDir, String name) throws IOException {
        Path asm = tempDir.resolve(name + ".s");
        Files.writeString(asm, HARNESS_CLEAR + "\n" + RiscvGcTestRuntimes.prunedFor(HARNESS_CLEAR));
        Path obj = tempDir.resolve(name + ".o");
        Path bin = tempDir.resolve(name);
        runCapture("riscv64-linux-gnu-as", "-mno-relax", "-o", obj.toString(), asm.toString());
        runCapture("riscv64-linux-gnu-ld", "--no-relax", "-o", bin.toString(), obj.toString());
        bin.toFile().setExecutable(true);
        return runCapture("qemu-riscv64", bin.toString());
    }

    private String buildAarch64(Path tempDir, String name) throws IOException {
        String riscv = HARNESS_CLEAR + "\n" + RiscvGcTestRuntimes.prunedFor(HARNESS_CLEAR);
        StringBuilder arm = new StringBuilder();
        for (String line : riscv.split("\n", -1)) {
            List<String> tr = NativeAarch64Translator.translateRiscvToAarch64(line);
            for (String t : tr) arm.append(t).append('\n');
        }
        Path asm = tempDir.resolve(name + "a.s");
        Files.writeString(asm, arm.toString());
        Path obj = tempDir.resolve(name + "a.o");
        Path bin = tempDir.resolve(name + "a");
        runCapture("aarch64-linux-gnu-as", "-o", obj.toString(), asm.toString());
        runCapture("aarch64-linux-gnu-ld", "--gc-sections", "-o", bin.toString(), obj.toString());
        bin.toFile().setExecutable(true);
        return runCapture("qemu-aarch64", bin.toString());
    }

    @Test
    void clearNullsEverySlotRiscv64(@TempDir Path tempDir) throws IOException {
        assumeNativeRiscv64();
        assertEquals("", buildRiscv(tempDir, "memclear"));
    }

    @Test
    void clearNullsEverySlotAarch64(@TempDir Path tempDir) throws IOException {
        assumeAarch64();
        assertEquals("", buildAarch64(tempDir, "memcleara"));
    }
}
