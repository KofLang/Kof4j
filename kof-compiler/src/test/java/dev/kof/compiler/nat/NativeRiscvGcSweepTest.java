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
 * G-4 (NATIVE002 face 1): SWEEP + COLLECT riscv64 provados por harness asm.
 * O G-3 marcou: D=1, C=0, B=1, A=1. O sweep do G-4:
 * - mark==1 → limpa mark (D/A/B voltam a flags=0, permanecem na gc-list)
 * - mark==0 e !free → C morre: insere na free-list (flags=2), conta frees=1
 *
 * <p>Prova 1 (unit, qemu riscv64 + aarch64): mark+sweep+dump exige
 * `gc 96 0 / gc 96 2 / gc 96 0 / gc 96 0` e `frees: 1` (C recuperada).
 *
 * <p>Prova 2 (VAZAMENTO, o ponto do G-4): um laço de 10000 allocs de 64B onde
 * só o último é vivo. A arena `.bss` tem 262144 B (≈2730 blocos de 96B): SEM
 * coletor o bump esgota em ~2730 e o `kof_alloc` PANICA (`out of memory`,
 * exit 1). COM o G-4, ao esgotar o `kof_alloc` roda `kof_gc_collect_now` uma
 * vez e a free-list devolve os mortos → o laço completa (exit 0). Sabotagem
 * (remover os hooks do coletor) = panic/exit 1, provando não-vacuidade. É o
 * fechamento do vazamento de ~260KB que era impossível de medir sem coletor.
 */
class NativeRiscvGcSweepTest {

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
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002 G-4)");
    }

    private void assumeAarch64() {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002 G-4)");
    }

    private static final String HARNESS = """
            .option arch, rv64g
            .section .data
            .align 3
            .Lkof_heap_root_start:
                .quad 0
            .Lroot_a:
                .quad 0
            .Lkof_heap_root_end:
            .section .text
            .globl _start
            _start:
                andi sp, sp, -16
                # A: só por raiz estática
                li   a0, 64
                call kof_alloc
                la   t0, .Lroot_a
                sd   a0, 0(t0)
                # B: só na pilha
                li   a0, 64
                call kof_alloc
                sd   a0, 0(sp)
                # C: inalcançável (nenhum ponteiro guardado) — candidato a morto
                li   a0, 64
                call kof_alloc
                # D: alcançável transitivamente, via campo 0 de A
                li   a0, 64
                call kof_alloc
                mv   t1, a0
                la   t0, .Lroot_a
                ld   t2, 0(t0)
                sd   t1, 32(t2)
                # mark (G-3) + sweep (G-4) + observação
                call kof_gc_mark
                call kof_gc_collect_now
                call kof_gc_dump
                call kof_memstats
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

    /** Laço de 10000 allocs de 2KB com só o último vivo na raiz estática.
     *  Sem coletor, a arena de 16MB (§540) esgota e o kof_alloc panica
     *  (10000×2KB = ~20MB > 16MB); com o G-4 o coletor recicla e completa. */
    private static final String HARNESS_LOOP = """
            .option arch, rv64g
            .section .data
            .align 3
            .Lkof_heap_root_start:
                .quad 0
            .Lroot_live:
                .quad 0
            .Lkof_heap_root_end:
            .section .text
            .globl _start
            _start:
                andi sp, sp, -16
                li   s0, 10000
            .Lloop:
                li   a0, 2048
                call kof_alloc
                la   t0, .Lroot_live
                sd   a0, 0(t0)
                addi s0, s0, -1
                bnez s0, .Lloop
                call kof_memstats
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

    private String buildRiscv(Path tempDir, String name, String harness, String runtime) throws IOException {
        Path asm = tempDir.resolve(name + ".s");
        Files.writeString(asm, harness + "\n" + runtime);
        Path obj = tempDir.resolve(name + ".o");
        Path bin = tempDir.resolve(name);
        runCapture("riscv64-linux-gnu-as", "-mno-relax", "-o", obj.toString(), asm.toString());
        runCapture("riscv64-linux-gnu-ld", "--no-relax", "-o", bin.toString(), obj.toString());
        bin.toFile().setExecutable(true);
        return runCapture("qemu-riscv64", bin.toString());
    }

    private String buildAarch64(Path tempDir, String name, String harness, String runtime) throws IOException {
        String riscv = harness + "\n" + runtime;
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

    /** Roda qemu e devolve saída + exit code (sem exigir 0) — p/ a sabotagem. */
    private String[] runCaptureAllowFail(String... cmd) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        try {
            int ec = p.waitFor();
            return new String[]{out, String.valueOf(ec)};
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    private void assertSweep(String out) {
        String[] lines = out.split("\n");
        StringBuilder gcLines = new StringBuilder();
        for (String l : lines) if (l.startsWith("gc ")) gcLines.append(l).append("\n");
        assertEquals("gc 96 0\ngc 96 2\ngc 96 0\ngc 96 0", gcLines.toString().trim(),
                "sweep deveria limpar mark de D/A/B (flags=0) e marcar C morta (flags=2=free-list); saída: " + out);
        assertTrue(out.contains("allocs: 4"), "allocs deveria ser 4: " + out);
        assertTrue(out.contains("frees: 1"), "frees deveria ser 1 (C recuperada): " + out);
    }

    @Test
    void sweepRecoversDeadAndMarksLive(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        assertSweep(buildRiscv(tempDir, "g4sweep", HARNESS, RiscvGcTestRuntimes.prunedFor(HARNESS)));
    }

    @Test
    void sweepRecoversDeadAndMarksLiveAarch64(@TempDir Path tempDir) throws IOException {
        assumeAarch64();
        assertSweep(buildAarch64(tempDir, "g4sweepa", HARNESS, RiscvGcTestRuntimes.prunedFor(HARNESS)));
    }

    @Test
    void longAllocLoopSurvivesArenaExhaustionViaCollect(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        String out = buildRiscv(tempDir, "g4loop", HARNESS_LOOP, RiscvGcTestRuntimes.prunedFor(HARNESS_LOOP));
        assertTrue(out.contains("allocs: 10000"),
                "o laço deveria completar as 10000 allocs (arena reciclada pelo G-4): " + out);
        assertTrue(out.matches("(?s).*frees: [1-9][0-9]*.*"),
                "o coletor deveria ter liberado blocos mortos: " + out);
    }

    @Test
    void longAllocLoopSurvivesArenaExhaustionViaCollectAarch64(@TempDir Path tempDir) throws IOException {
        assumeAarch64();
        String out = buildAarch64(tempDir, "g4loopa", HARNESS_LOOP, RiscvGcTestRuntimes.prunedFor(HARNESS_LOOP));
        assertTrue(out.contains("allocs: 10000"),
                "o laço deveria completar as 10000 allocs no aarch64 (arena reciclada): " + out);
        assertTrue(out.matches("(?s).*frees: [1-9][0-9]*.*"),
                "o coletor deveria ter liberado blocos mortos: " + out);
    }

    @Test
    void longAllocLoopOomsWithoutCollector(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        // Sabotagem: remove TODOS os hooks do coletor (entry + OOM) do runtime.
        String sabotaged = RiscvGcTestRuntimes.prunedFor(HARNESS_LOOP)
                .replace("    call kof_gc_collect\n", "")
                .replace("    call kof_gc_collect_now\n", "");
        Path asm = tempDir.resolve("g4sab.s");
        Path obj = tempDir.resolve("g4sab.o");
        Path bin = tempDir.resolve("g4sab");
        Files.writeString(asm, HARNESS_LOOP + "\n" + sabotaged);
        runCapture("riscv64-linux-gnu-as", "-mno-relax", "-o", obj.toString(), asm.toString());
        runCapture("riscv64-linux-gnu-ld", "--no-relax", "-o", bin.toString(), obj.toString());
        bin.toFile().setExecutable(true);
        String[] r = runCaptureAllowFail("qemu-riscv64", bin.toString());
        assertNotEquals("0", r[1],
                "SEM coletor o laço DEVERIA estourar a arena e panicar (não-vacuidade): " + r[0]);
        assertTrue(r[0].contains("out of memory"),
                "deveria panicar com 'out of memory': " + r[0]);
    }
}
