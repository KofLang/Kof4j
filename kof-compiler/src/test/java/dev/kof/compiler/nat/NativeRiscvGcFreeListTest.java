package dev.kof.compiler.nat;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * G-1 (NATIVE002 face 1): free-list + memstats riscv64 provados por um
 * harness asm cru. O free NÃO tem caller no caminho Kof — medido 15/09:
 * nenhuma peça riscv chama {@code kof_free} e o nó de log riscv escreve por
 * {@code write()} sem alocar (a correção doc-vs-realidade está registrada em
 * {@code docs/development/native-multiarch.md}). Sem caminho Kof, a prova do
 * G-1 é este harness: concatena o runtime de PRODUÇÃO
 * PODADO ({@link RiscvGcTestRuntimes#prunedFor}) com um {@code _start} que faz
 * alloc/free/alloc do MESMO tamanho e exige o reuso do slot, depois imprime
 * {@code kof_memstats}. Roda sob qemu-riscv64.
 *
 * <p>Guard {@code assumeTrue}: sem toolchain cross o teste PULA (nunca asm
 * não executado — regra do plano NATIVE002).
 */
class NativeRiscvGcFreeListTest {

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

    private void assumeToolchain() {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002 G-1)");
    }

    private void assumeAarch64() {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002 G-5)");
    }

    /** _start cru: p1 = alloc(64); free(p1); p2 = alloc(64); exige p2 == p1. */
    private static final String HARNESS = """
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
                li   a0, 64
                call kof_alloc
                mv   s0, a0
                mv   a0, s0
                call kof_free
                li   a0, 64
                call kof_alloc
                mv   s1, a0
                beq  s0, s1, .Lharness_reuse
                la   a1, .Lharness_fail
                li   a2, 4
                j    .Lharness_w
            .Lharness_reuse:
                la   a1, .Lharness_ok
                li   a2, 5
            .Lharness_w:
                li   a0, 1
                li   a7, 64
                ecall
                call kof_memstats
                li   a0, 0
                li   a7, 93
                ecall
            # kof_instanceof referencia kof_super_table (program-side); o
            # harness não tem programa, então define o stub p/ o link fechar.
            .globl kof_super_table
            kof_super_table:
                .word 0
                .globl kof_equals_table
                kof_equals_table:
                .quad 0
                .globl kof_hashcode_table
                kof_hashcode_table:
                .quad 0
            .section .rodata
            .Lharness_ok: .asciz "reuse"
            .Lharness_fail: .asciz "fail"
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

    private void assertReuseAndStats(String out) {
        assertTrue(out.startsWith("reuse"),
                "alloc/free/alloc deveria reusar o slot (free-list); saída: " + out);
        assertTrue(out.contains("allocs: 2"),
                "kof_memstats deveria reportar allocs: 2; saída: " + out);
        assertTrue(out.contains("frees: 1"),
                "kof_memstats deveria reportar frees: 1; saída: " + out);
        assertTrue(out.contains("live bytes: "),
                "kof_memstats deveria imprimir live bytes; saída: " + out);
    }

    @Test
    void freeListReusesSlotAndMemstatsCounts(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        Path asm = tempDir.resolve("g1.s");
        Files.writeString(asm, HARNESS + "\n" + RiscvGcTestRuntimes.prunedFor(HARNESS));
        Path obj = tempDir.resolve("g1.o");
        Path bin = tempDir.resolve("g1");
        runCapture("riscv64-linux-gnu-as", "-mno-relax", "-o", obj.toString(), asm.toString());
        runCapture("riscv64-linux-gnu-ld", "--no-relax", "-o", bin.toString(), obj.toString());
        bin.toFile().setExecutable(true);
        assertReuseAndStats(runCapture("qemu-riscv64", bin.toString()));
    }

    /** G-5: o aarch64 herda o free-list/memstats por tradução linha-a-linha do
     *  riscv (amoswap.w→swpal, amoadd.d→ldadd) — a mesma prova roda em
     *  qemu-aarch64 sobre o runtime TRANSLADO. */
    @Test
    void freeListReusesSlotAndMemstatsCountsAarch64(@TempDir Path tempDir) throws IOException {
        assumeAarch64();
        StringBuilder riscv = new StringBuilder(HARNESS).append('\n').append(RiscvGcTestRuntimes.prunedFor(HARNESS));
        StringBuilder arm = new StringBuilder();
        for (String line : riscv.toString().split("\n", -1)) {
            List<String> tr = NativeAarch64Translator.translateRiscvToAarch64(line);
            for (String t : tr) arm.append(t).append('\n');
        }
        Path asm = tempDir.resolve("g1a.s");
        Files.writeString(asm, arm.toString());
        Path obj = tempDir.resolve("g1a.o");
        Path bin = tempDir.resolve("g1a");
        runCapture("aarch64-linux-gnu-as", "-o", obj.toString(), asm.toString());
        runCapture("aarch64-linux-gnu-ld", "--gc-sections", "-o", bin.toString(), obj.toString());
        bin.toFile().setExecutable(true);
        assertReuseAndStats(QemuRun.runExpect0("qemu-aarch64", bin.toString()));
    }
}
