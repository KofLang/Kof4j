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

import static org.junit.jupiter.api.Assertions.*;

/**
 * B-0 (D-BAREMETAL-BOOT, 22/09): a costura de plataforma {@code kof_plat_*}
 * é REAL — sabotar a implementação Linux (tornar {@code kof_plat_write}/
 * {@code kof_plat_writev} um no-op) faz a saída do programa DESAPARECER.
 *
 * <p>Método (mesmo caminho de produção, sem atalho): o runtime é o subset
 * PODADO por {@link RiscvSlices#keepForProgramText} — exatamente o que o
 * {@code NativeArchEmitter} liga — e o harness usa só o vocabulário de
 * produção ({@code kof_println_string} + {@code kof_plat_exit_group}). O
 * controle (runtime íntegro) imprime "seam"; o sabotado (corpo de
 * {@code kof_plat_writev}/{@code kof_plat_write} trocado por {@code ret})
 * não imprime nada. Se a impressão não cruzasse a costura, a sabotagem não
 * teria efeito e o teste falharia.
 *
 * <p>Pula (assume) quando a toolchain cross ou o qemu não existem, como os
 * demais testes cross (NATIVE002).
 */
class PlatformSeamSabotageTest implements NativeToolchainAssumptions {

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

    private void assumeRiscv64() {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (B-0 NATIVE003)");
    }


    private void assumeX86Host() {
        String arch = System.getProperty("os.arch", "");
        Assumptions.assumeTrue(arch.equals("amd64") || arch.equals("x86_64"),
                "host não é x86_64 — pulando a face x86 da costura");
        Assumptions.assumeTrue(has("as", "ld"),
                "binutils nativo (as/ld) ausente — pulando a face x86 da costura");
    }

    // Harness: um KofStr "seam" estático (header 24B + dados) impresso via
    // kof_println_string e saída via kof_plat_exit_group — só vocabulário de
    // produção, para o seed da poda achar as peças reais.
    private static final String HARNESS = """
            .option arch, rv64g
            .section .data
            .align 3
            .Lkof_heap_root_start:
                .quad 0
            .Lkof_heap_root_end:
            .section .rodata
            .align 3
            .Lseam_msg:
                .word 1
                .word 0
                .quad 0
                .word 4
                .word 0
                .ascii "seam"
            .section .text
            .globl _start
            _start:
                andi sp, sp, -16
                la   a0, .Lseam_msg
                call kof_println_string
                li   a0, 0
                call kof_plat_exit_group
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

    /** Sabotagem da costura: corpo de kof_plat_writev/write vira `ret`. */
    private static String sabotageSeam(String runtime) {
        String s = runtime.replace(
                "kof_plat_write:\n    li   a7, 64\n    ecall\n    ret",
                "kof_plat_write:\n    ret");
        String s2 = s.replace(
                "kof_plat_writev:\n    li   a7, 66\n    ecall\n    ret",
                "kof_plat_writev:\n    ret");
        assertNotEquals(s, s2,
                "sabotagem não encontrou o corpo de kof_plat_write/writev — a costura mudou de forma?");
        return s2;
    }

    // Harness x86_64 (AT&T): mesmo contrato do harness riscv — KofStr estático
    // (header 24B + dados) impresso via kof_println_string e saída pela costura.
    // O x86 define no lado-PROGRAMA o que a produção define (NativeBackend/
    // NativeClassMeta): kof_heap_root_start (global) e .Lnewline.
    private static final String HARNESS_X86 = """
            .section .data
            .align 8
            .globl kof_heap_root_start
            kof_heap_root_start:
                .quad 0
            .section .rodata
            .align 8
            .Lseam_msg:
                .long 1
                .long 0
                .quad 0
                .long 4
                .long 0
                .ascii "seam"
            .Lnewline: .asciz "\\n"
            .Lkof_str_true: .asciz "true"
            .Lkof_str_false: .asciz "false"
            .section .text
            .globl _start
            _start:
                andq $-16, %rsp
                leaq .Lseam_msg(%rip), %rdi
                call kof_println_string
                xorl %edi, %edi
                call kof_plat_exit_group
            .globl kof_super_table
            kof_super_table:
                .long 0
                .globl kof_equals_table
                kof_equals_table:
                .quad 0
                .globl kof_hashcode_table
                kof_hashcode_table:
                .quad 0
            """;

    /** Sabotagem da costura x86: corpo de kof_plat_write/writev vira `ret`. */
    private static String sabotageSeamX86(String runtime) {
        String s = runtime.replace(
                "kof_plat_write:\n    movq $1, %rax\n    syscall\n    ret",
                "kof_plat_write:\n    ret");
        String s2 = s.replace(
                "kof_plat_writev:\n    movq $20, %rax\n    syscall\n    ret",
                "kof_plat_writev:\n    ret");
        assertNotEquals(s, s2,
                "sabotagem não encontrou o corpo x86 de kof_plat_write/writev — a costura mudou de forma?");
        return s2;
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

    private String buildRiscv(Path tempDir, String name, boolean sabotaged) throws IOException {
        String runtime = RiscvGcTestRuntimes.prunedFor(HARNESS);
        if (sabotaged) runtime = sabotageSeam(runtime);
        Path asm = tempDir.resolve(name + ".s");
        Files.writeString(asm, HARNESS + "\n" + runtime);
        Path obj = tempDir.resolve(name + ".o");
        Path bin = tempDir.resolve(name);
        runCapture("riscv64-linux-gnu-as", "-mno-relax", "-o", obj.toString(), asm.toString());
        runCapture("riscv64-linux-gnu-ld", "--no-relax", "-o", bin.toString(), obj.toString());
        bin.toFile().setExecutable(true);
        return runCapture("qemu-riscv64", bin.toString());
    }

    private String buildAarch64(Path tempDir, String name, boolean sabotaged) throws IOException {
        String runtime = RiscvGcTestRuntimes.prunedFor(HARNESS);
        if (sabotaged) runtime = sabotageSeam(runtime);
        String riscv = HARNESS + "\n" + runtime;
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

    private String buildX86(Path tempDir, String name, boolean sabotaged) throws IOException {
        String runtime = RuntimeSlices.renderSubset(RuntimeSlices.keepForProgramText(HARNESS_X86));
        if (sabotaged) runtime = sabotageSeamX86(runtime);
        Path asm = tempDir.resolve(name + ".s");
        Files.writeString(asm, HARNESS_X86 + "\n" + runtime);
        Path obj = tempDir.resolve(name + ".o");
        Path bin = tempDir.resolve(name);
        runCapture("as", "-o", obj.toString(), asm.toString());
        runCapture("ld", "-o", bin.toString(), obj.toString(),
                "-dynamic-linker", "/lib64/ld-linux-x86-64.so.2", "-lc");
        bin.toFile().setExecutable(true);
        return runCapture(bin.toString());
    }

    @Test
    void seamIsLoadBearingRiscv64(@TempDir Path tempDir) throws IOException {
        assumeRiscv64();
        assertEquals("seam", buildRiscv(tempDir, "seam_ctrl", false),
                "controle: o runtime de produção deve imprimir via a costura");
        assertEquals("", buildRiscv(tempDir, "seam_sab", true),
                "sabotado: kof_plat_writev no-op ⇒ NENHUMA saída (a costura é usada, não decorativa)");
    }

    @Test
    void seamIsLoadBearingAarch64(@TempDir Path tempDir) throws IOException {
        assumeNativeAarch64();
        assertEquals("seam", buildAarch64(tempDir, "seam_ctrl", false),
                "controle: o runtime traduzido deve imprimir via a costura");
        assertEquals("", buildAarch64(tempDir, "seam_sab", true),
                "sabotado: kof_plat_writev no-op ⇒ NENHUMA saída (a costura é usada, não decorativa)");
    }

    @Test
    void seamIsLoadBearingX86Host(@TempDir Path tempDir) throws IOException {
        assumeX86Host();
        assertEquals("seam", buildX86(tempDir, "seam_x86_ctrl", false),
                "controle: o runtime x86 de produção deve imprimir via a costura");
        assertEquals("", buildX86(tempDir, "seam_x86_sab", true),
                "sabotado: kof_plat_write no-op ⇒ NENHUMA saída (a costura é usada, não decorativa)");
    }

    /** Regressão (achada na 1ª rodada por `KofRandomTest.randomIntCrossArch`):
     *  uma função que ERA folha e passou a chamar a costura tem de salvar/
     *  restaurar `ra` — `kof_random_boolean` não salvava e o `ret` voltava
     *  para dentro da própria função (loop eterno, qemu pendurado). Guarda a
     *  classe inteira: todo caller de `kof_plat_*` no runtime declara o frame. */
    @Test
    void everySeamCallerSavesAndRestoresRa() {
        String rt = RiscvSlices.renderRuntime();
        String[] lines = rt.split("\n", -1);
        String fn = null;
        boolean hasSeamCall = false, saved = false, restored = false, hasRet = false;
        java.util.List<String> bad = new java.util.ArrayList<>();
        for (int i = 0; i <= lines.length; i++) {
            String l = i < lines.length ? lines[i] : "";
            boolean label = l.matches("^(?!\\.L)\\S+:$");
            if (label || i == lines.length) {
                if (fn != null && hasSeamCall && hasRet && !(saved && restored)) {
                    bad.add(fn);
                }
                fn = label ? l.substring(0, l.length() - 1) : null;
                hasSeamCall = saved = restored = hasRet = false;
                if (label) continue;
            }
            if (l.contains("call kof_plat_")) hasSeamCall = true;
            if (l.matches("^\\s*sd\\s+ra,.*")) saved = true;
            if (l.matches("^\\s*ld\\s+ra,.*")) restored = true;
            if (l.matches("^\\s*ret\\s*$")) hasRet = true;
        }
        assertEquals(java.util.List.of(), bad,
                "caller da costura sem sd/ld ra (folha virou caller): " + bad);
    }

    /** B-1 (22/09): o clone do spawn riscv64 cruza a costura
     *  {@code kof_plat_thread_create} — antes era ecall inline (deferral de
     *  B-0). O {@code call} riscv não empilha, então o filho volta ao caller
     *  e troca o {@code sp} antes do trampoline (frame do pai intacto). Guarda
     *  estrutural: o seam existe com a syscall 220 e o caller roteia por ele —
     *  nenhum clone cru ({@code li a7, 220}) pode sobrar no spawn. */
    @Test
    void riscvThreadCreateRoutesThroughSeam() {
        assertTrue(NativeRiscvAsmRt0.RISCV_RUNTIME_ASM_0.contains(
                "kof_plat_thread_create:\n    li   a7, 220\n    ecall\n    ret"),
                "seam kof_plat_thread_create (clone 220) ausente na costura riscv");
        StringBuilder sb = new StringBuilder();
        NativeRiscvSpawn.emitRiscvSpawn(sb);
        String spawn = sb.toString();
        assertTrue(spawn.contains("call kof_plat_thread_create"),
                "kof_spawn_result deve cruzar a costura do clone (B-1)");
        assertFalse(spawn.contains("li   a7, 220"),
                "clone cru (li a7,220) não pode sobrar no spawn — B-1 seam quebrado");
    }
}
