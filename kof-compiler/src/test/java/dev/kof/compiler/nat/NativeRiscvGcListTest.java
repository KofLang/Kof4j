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
 * G-2 (NATIVE002 face 1): gc-list + flags riscv64 provados por harness asm.
 * O G-1 ligou kof_alloc na free list; o G-2 liga cada bloco NOVO na gc-list
 * global (`.Lkof_gc_head`, LIFO) com flags=0, e expõe `kof_gc_dump` (o "dump
 * KOF_GC_DEBUG" do plano — no asm puro o gatilho é a chamada explícita).
 *
 * <p>Prova (qemu riscv64 + aarch64, toolchain presente, NUNCA skip): monta o
 * runtime de PRODUÇÃO PODADO ({@link RiscvGcTestRuntimes#prunedFor}) com um `_start`
 * que aloca N blocos e chama `kof_gc_dump`; a saída lista cada bloco com o
 * tamanho TOTAL (align16(size)+32, header G-0) e os flags, na ordem LIFO.
 */
class NativeRiscvGcListTest {

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
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002 G-2)");
    }

    private void assumeAarch64() {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002 G-2)");
    }

    /** _start cru: 3 allocs de tamanhos distintos + dump da gc-list. */
    private static final String HARNESS_ALLOCS = """
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
                li   a0, 16
                call kof_alloc
                li   a0, 32
                call kof_alloc
                li   a0, 64
                call kof_alloc
                call kof_gc_dump
                li   a0, 0
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

    /** _start: alloc(64) -> free -> alloc(64): a reutilização NÃO duplica a
     *  entrada na gc-list (o bloco já estava nela) e os flags voltam a 0. */
    private static final String HARNESS_REUSE = """
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
                call kof_gc_dump
                li   a0, 0
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

    private String buildRiscv(Path tempDir, String harness, String name) throws IOException {
        Path asm = tempDir.resolve(name + ".s");
        Files.writeString(asm, harness + "\n" + RiscvGcTestRuntimes.prunedFor(harness));
        Path obj = tempDir.resolve(name + ".o");
        Path bin = tempDir.resolve(name);
        runCapture("riscv64-linux-gnu-as", "-mno-relax", "-o", obj.toString(), asm.toString());
        runCapture("riscv64-linux-gnu-ld", "--no-relax", "-o", bin.toString(), obj.toString());
        bin.toFile().setExecutable(true);
        return runCapture("qemu-riscv64", bin.toString());
    }

    private String buildAarch64(Path tempDir, String harness, String name) throws IOException {
        String riscv = harness + "\n" + RiscvGcTestRuntimes.prunedFor(harness);
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
        return QemuRun.runExpect0("qemu-aarch64", bin.toString());
    }

    private void assertList(String out) {
        // LIFO: 64->96, 32->64, 16->48 (total = align16(size)+32)
        assertEquals("gc 96 0\ngc 64 0\ngc 48 0", out,
                "gc-list deveria listar 96/64/48 com flags 0 (LIFO); saída: " + out);
    }

    private void assertReuse(String out) {
        assertEquals("gc 96 0", out,
                "free+realloc NÃO deve duplicar a entrada na gc-list; saída: " + out);
    }

    @Test
    void gcListLinksNewBlocksWithFlags(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        assertList(buildRiscv(tempDir, HARNESS_ALLOCS, "g2list"));
    }

    @Test
    void gcListReuseDoesNotDuplicate(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        assertReuse(buildRiscv(tempDir, HARNESS_REUSE, "g2reuse"));
    }

    @Test
    void gcListLinksNewBlocksWithFlagsAarch64(@TempDir Path tempDir) throws IOException {
        assumeAarch64();
        assertList(buildAarch64(tempDir, HARNESS_ALLOCS, "g2lista"));
    }

    @Test
    void gcListReuseDoesNotDuplicateAarch64(@TempDir Path tempDir) throws IOException {
        assumeAarch64();
        assertReuse(buildAarch64(tempDir, HARNESS_REUSE, "g2reusea"));
    }
}
