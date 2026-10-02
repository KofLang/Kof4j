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
 * G-3 (NATIVE002 face 1): mark CONSERVATIVO riscv64 provado por harness asm.
 * O G-2 listou os blocos; o G-3 seta o bit0 (mark) nos alcançáveis por (a)
 * raiz estática em .data, (b) raiz de PILHA, (c) fecho TRANSITIVO por campo —
 * e deixa o inalcançável com flags=0. Sem sweep (G-4), o bit é observado pelo
 * `kof_gc_dump` do G-2 ANTES/DEPOIS do mark.
 *
 * <p>Prova (qemu riscv64 + aarch64, toolchain presente, NUNCA skip): monta o
 * runtime de PRODUÇÃO PODADO ({@link RiscvGcTestRuntimes#prunedFor}) com um `_start`
 * que aloca A(estático)→B(pilha)→C(inalcançável)→D(via campo de A), chama
 * `kof_gc_mark` e despeja a gc-list (LIFO): D=1, C=0, B=1, A=1.
 */
class NativeRiscvGcMarkTest {

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
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002 G-3)");
    }

    private void assumeAarch64() {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain aarch64 + qemu ausente — pulando (NATIVE002 G-3)");
    }

    // _start cru. O intervalo de raízes estáticas é definido AQUI com os mesmos
    // rótulos locais que o NativeArchEmitter emite no .data do programa (o
    // harness não passa pelo emitter; o contrato é o nome `.Lkof_heap_root_*`).
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
                # C: inalcançável (nenhum ponteiro guardado)
                li   a0, 64
                call kof_alloc
                # D: alcançável só transitivamente, via campo 0 de A
                li   a0, 64
                call kof_alloc
                mv   t1, a0
                la   t0, .Lroot_a
                ld   t2, 0(t0)           # payload de A
                sd   t1, 32(t2)          # A.campo0 = D
                # marca e despeja
                call kof_gc_mark
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

    private String buildRiscv(Path tempDir, String name) throws IOException {
        Path asm = tempDir.resolve(name + ".s");
        Files.writeString(asm, HARNESS + "\n" + RiscvGcTestRuntimes.prunedFor(HARNESS));
        Path obj = tempDir.resolve(name + ".o");
        Path bin = tempDir.resolve(name);
        runCapture("riscv64-linux-gnu-as", "-mno-relax", "-o", obj.toString(), asm.toString());
        runCapture("riscv64-linux-gnu-ld", "--no-relax", "-o", bin.toString(), obj.toString());
        bin.toFile().setExecutable(true);
        return runCapture("qemu-riscv64", bin.toString());
    }

    private String buildAarch64(Path tempDir, String name) throws IOException {
        String riscv = HARNESS + "\n" + RiscvGcTestRuntimes.prunedFor(HARNESS);
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

    private void assertMark(String out) {
        // LIFO: D(1), C(0), B(1), A(1) — todos 96B (align16(64)+32).
        assertEquals("gc 96 1\ngc 96 0\ngc 96 1\ngc 96 1", out,
                "mark deveria setar bit0 em D/B/A e deixar C=0; saída: " + out);
    }

    @Test
    void conservativeMarkMarksReachable(@TempDir Path tempDir) throws IOException {
        assumeToolchain();
        assertMark(buildRiscv(tempDir, "g3mark"));
    }

    @Test
    void conservativeMarkMarksReachableAarch64(@TempDir Path tempDir) throws IOException {
        assumeAarch64();
        assertMark(buildAarch64(tempDir, "g3marka"));
    }
}
