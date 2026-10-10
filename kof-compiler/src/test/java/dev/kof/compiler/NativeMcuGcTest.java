package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.kof.compiler.nat.mcu.NativeMcuGcRiscv32;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * B4-GC-1/2/3 (PLAN-BAREMETAL-BOOT B-4 + {@code D-BAREMETAL-MCU-GC}): alocador +
 * mark conservador + sweep do MCU RV32I provados por harness asm cru sob
 * {@code qemu-system-riscv32 -M virt}
 * — mesmo padrão do {@code NativeRiscvGcSweepTest} do cross: o runtime de
 * PRODUÇÃO ({@link NativeMcuGcRiscv32#runtimeAsm()}) é concatenado a um harness
 * que chama {@code kof_alloc}/{@code kof_free}/{@code kof_gc_dump}/
 * {@code kof_memstats} direto.
 *
 * <p>Guards honestos (Q5): binutils riscv64 (com {@code -march=rv32i}) +
 * {@code qemu-system-riscv32} provisionado em {@code ~/.local/share/kof-mcu}
 * (ver {@code scripts/provision-mcu-qemu.sh}). Sem eles → {@code assumeTrue}
 * skip, nunca verde falso.
 */
class NativeMcuGcTest implements NativeToolchainAssumptions {

    private static final long HEAP = 0x10000; // 64 KB

    @Test
    void mcuGcAllocatesAndDumps(@TempDir Path tempDir) throws Exception {
        assumeMcuRiscvAsm();
        String out = run(tempDir, "gc1", body(
                "    li   a0, 16\n    call kof_alloc\n    la   t0, .Lroot_a\n    sw   a0, 0(t0)\n"
                + "    li   a0, 16\n    call kof_alloc\n    sw   a0, 0(sp)\n"
                + "    li   a0, 16\n    call kof_alloc\n"
                + "    li   a0, 16\n    call kof_alloc\n"
                + "    call kof_gc_dump\n    call kof_memstats\n"), HEAP);
        assertEquals(4, count(out, "gc 32 0"), "4 blocos de 32B (header 16 + 16) na gc-list: " + out);
        assertTrue(out.contains("allocs: 4"), "allocs deveria ser 4: " + out);
        assertTrue(out.contains("frees: 0"), "frees deveria ser 0: " + out);
        assertTrue(out.contains("live bytes: 128"), "live bytes deveria ser 128: " + out);
    }

    @Test
    void mcuGcMarkMarksRootsTransitively(@TempDir Path tempDir) throws Exception {
        assumeMcuRiscvAsm();
        // A = raiz ESTÁTICA (.Lroot_a); B = raiz de PILHA (abaixo de _stack_top);
        // C = inalcançável; D = alcançável só via A.payload[0] (fecho transitivo).
        String out = run(tempDir, "gc4", body(
                "    li   a0, 16\n    call kof_alloc\n    la   t0, .Lroot_a\n    sw   a0, 0(t0)\n"
                + "    addi sp, sp, -16\n"
                + "    li   a0, 16\n    call kof_alloc\n    sw   a0, 0(sp)\n"
                + "    li   a0, 16\n    call kof_alloc\n"
                + "    li   a0, 16\n    call kof_alloc\n    mv   t1, a0\n"
                + "    la   t0, .Lroot_a\n    lw   t2, 0(t0)\n    sw   t1, 0(t2)\n"
                + "    call kof_gc_mark\n    call kof_gc_dump\n"), HEAP);
        assertEquals(java.util.List.of("gc 32 1", "gc 32 0", "gc 32 1", "gc 32 1"),
                gcLines(out),
                "gc-list LIFO D,C,B,A: D/A transitivo e B pilha marcados (1); C morto fica 0: " + out);
    }

    @Test
    void mcuGcSweepRecoversDeadAndKeepsLive(@TempDir Path tempDir) throws Exception {
        assumeMcuRiscvAsm();
        // Mesmo grafo do mark; collect_now = mark+sweep. A/B/D vivos (flags
        // voltam a 0), C morta vai p/ a free-list (flags 2) e conta frees: 1.
        String out = run(tempDir, "gc5", body(
                "    li   a0, 16\n    call kof_alloc\n    la   t0, .Lroot_a\n    sw   a0, 0(t0)\n"
                + "    addi sp, sp, -16\n"
                + "    li   a0, 16\n    call kof_alloc\n    sw   a0, 0(sp)\n"
                + "    li   a0, 16\n    call kof_alloc\n"
                + "    li   a0, 16\n    call kof_alloc\n    mv   t1, a0\n"
                + "    la   t0, .Lroot_a\n    lw   t2, 0(t0)\n    sw   t1, 0(t2)\n"
                + "    call kof_gc_collect_now\n    call kof_gc_dump\n    call kof_memstats\n"),
                HEAP);
        assertEquals(java.util.List.of("gc 32 0", "gc 32 2", "gc 32 0", "gc 32 0"),
                gcLines(out),
                "sweep deve limpar o mark de D/A/B e pôr C (flags 2) na free-list: " + out);
        assertTrue(out.contains("frees: 1"), "C deveria contar uma free: " + out);
        assertTrue(out.contains("live bytes: 96"), "128 alocados - 32 recuperados = 96: " + out);
    }

    @Test
    void mcuGcMarkHandlesCycles(@TempDir Path tempDir) throws Exception {
        assumeMcuRiscvAsm();
        // A<->E em ciclo, ambos alcançáveis de A: o mark para no já-marcado
        // (guard de ciclo) e o sweep mantém os dois vivos.
        String out = run(tempDir, "gc6", body(
                "    li   a0, 16\n    call kof_alloc\n    la   t0, .Lroot_a\n    sw   a0, 0(t0)\n"
                + "    li   a0, 16\n    call kof_alloc\n    mv   s0, a0\n"
                + "    la   t0, .Lroot_a\n    lw   t1, 0(t0)\n    sw   s0, 0(t1)\n"
                + "    sw   t1, 0(s0)\n"
                + "    call kof_gc_collect_now\n    call kof_gc_dump\n    call kof_memstats\n"),
                HEAP);
        assertEquals(java.util.List.of("gc 32 0", "gc 32 0"), gcLines(out),
                "ciclo A<->E termina e os dois sobrevivem (flags 0): " + out);
        assertTrue(out.contains("frees: 0"), "nada deve morrer no ciclo: " + out);
        assertTrue(out.contains("live bytes: 64"), "os dois blocos seguem vivos: " + out);
    }

    @Test
    void mcuGcLongAllocLoopIsRecycled(@TempDir Path tempDir) throws Exception {
        assumeMcuRiscvAsm();
        // 10000 allocs de 16B com só a última viva. Heap de 64KB ≈ 2048 blocos:
        // SEM coletor o bump estoura e panica; COM o B4-GC-3 o OOM do kof_alloc
        // roda collect+retry e o laço completa — fecho do vazamento.
        String out = run(tempDir, "gc7", body(loopOps(10000)), HEAP);
        assertTrue(out.contains("allocs: 10000"),
                "o laço deveria completar 10000 allocs reciclando o heap: " + out);
        assertTrue(out.matches("(?s).*frees: [1-9][0-9]*.*"),
                "o coletor deveria ter liberado blocos mortos: " + out);
    }

    @Test
    void mcuGcLongAllocLoopOomsWithoutCollector(@TempDir Path tempDir) throws Exception {
        assumeMcuRiscvAsm();
        // Sabotagem: removido o hook do coletor no OOM → o bump esgota e panica.
        String sabotaged = NativeMcuGcRiscv32.all().replace("call kof_gc_collect_now\n", "");
        String out = runWith(tempDir, "gc8", body(loopOps(10000)), HEAP, sabotaged);
        assertTrue(out.contains("out of memory"),
                "SEM coletor o laço DEVERIA estourar o heap e panicar (não-vacuidade): " + out);
        assertTrue(!out.contains("allocs: 10000"),
                "sem coletor o laço NÃO deveria completar: " + out);
    }

    private static String loopOps(int n) {
        return "    li   s0, " + n + "\n"
                + ".Lloop:\n"
                + "    li   a0, 16\n    call kof_alloc\n"
                + "    la   t0, .Lroot_a\n    sw   a0, 0(t0)\n"
                + "    addi s0, s0, -1\n    bnez s0, .Lloop\n"
                + "    call kof_memstats\n";
    }

    @Test
    void mcuGcFreeIsReusedByNextAlloc(@TempDir Path tempDir) throws Exception {
        assumeMcuRiscvAsm();
        String out = run(tempDir, "gc2", body(
                "    li   a0, 16\n    call kof_alloc\n    mv   s0, a0\n"
                + "    mv   a0, s0\n    call kof_free\n"
                + "    li   a0, 16\n    call kof_alloc\n"
                + "    call kof_gc_dump\n    call kof_memstats\n"), HEAP);
        assertEquals(1, count(out, "gc 32 0"),
                "o free deve ser reusado pelo alloc seguinte (um só bloco de 32B): " + out);
        assertTrue(out.contains("allocs: 2"), "allocs deveria ser 2: " + out);
        assertTrue(out.contains("frees: 1"), "frees deveria ser 1: " + out);
        assertTrue(out.contains("live bytes: 32"),
                "live bytes deveria ser 32 (um bloco vivo, o outro reciclado): " + out);
    }

    @Test
    void mcuGcOomPanicsWithDiagnostic(@TempDir Path tempDir) throws Exception {
        assumeMcuRiscvAsm();
        // Heap de 4 KB; pedido de 32 KB estoura o bump → panic nomeado (R6/Q7).
        String out = run(tempDir, "gc3", body(
                "    li   a0, 0x8000\n    call kof_alloc\n    call kof_memstats\n"), 0x1000);
        assertTrue(out.contains("out of memory"),
                "OOM deveria panicar com 'out of memory', veio: [" + out + "]");
    }

    private static String body(String ops) {
        return """
                .option norvc
                .section .data
                .align 2
                .Lkof_heap_root_start: .word 0
                .Lroot_a: .word 0
                .Lkof_heap_root_end:
                .section .text
                .globl _start
                _start:
                    la   sp, _stack_top
                """ + ops + """
                    li   a0, 0
                    call kof_plat_exit
                .Lhalt:
                    j    .Lhalt

                .globl kof_plat_write
                kof_plat_write:
                    add  t2, a0, a1
                    mv   t0, a0
                    li   t1, 0x10000000
                .Lw_loop:
                    bgeu t0, t2, .Lw_done
                    lbu  a0, 0(t0)
                    sb   a0, 0(t1)
                    addi t0, t0, 1
                    j    .Lw_loop
                .Lw_done:
                    ret

                .globl kof_plat_exit
                kof_plat_exit:
                    li   t1, 0x100000
                    li   t0, 0x5555
                    sw   t0, 0(t1)
                .Le_halt:
                    j    .Le_halt
                """;
    }

    private static java.util.List<String> gcLines(String out) {
        java.util.List<String> list = new java.util.ArrayList<>();
        for (String line : out.split("\n")) {
            if (line.startsWith("gc ")) {
                list.add(line.trim());
            }
        }
        return list;
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        int i = 0;
        while ((i = haystack.indexOf(needle, i)) >= 0) {
            n++;
            i += needle.length();
        }
        return n;
    }

    private String run(Path tempDir, String name, String program, long heapBytes) throws Exception {
        return runWith(tempDir, name, program, heapBytes, NativeMcuGcRiscv32.all());
    }

    private String runWith(Path tempDir, String name, String program, long heapBytes, String runtime)
            throws Exception {
        Path asm = tempDir.resolve(name + ".s");
        Path ld = tempDir.resolve(name + ".ld");
        Path obj = tempDir.resolve(name + ".o");
        Path bin = tempDir.resolve(name);
        Files.writeString(asm, program + "\n" + runtime);
        Files.writeString(ld, NativeMcuGcRiscv32.linkerScript(heapBytes));
        capture("riscv64-linux-gnu-as", "-march=rv32i", "-mabi=ilp32", "-o", obj.toString(),
                asm.toString());
        capture("riscv64-linux-gnu-ld", "-m", "elf32lriscv", "-T", ld.toString(), "-o",
                bin.toString(), obj.toString());
        bin.toFile().setExecutable(true);
        return boot(tempDir, bin);
    }

    private String boot(Path tempDir, Path bin) throws Exception {
        Path qemu = findQemu();
        assumeTrue(qemu != null, "qemu-system-riscv32 ausente (scripts/provision-mcu-qemu.sh)");
        Path ser = tempDir.resolve("ser.log");
        java.util.List<String> cmd = new java.util.ArrayList<>(java.util.List.of(
                qemu.toString(), "-M", "virt", "-bios", "none", "-display", "none",
                "-serial", "file:" + ser, "-kernel", bin.toString()));
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        Path prefix = mcuPrefix();
        if (prefix != null && qemu.isAbsolute() && qemu.startsWith(prefix)) {
            pb.environment().put("LD_LIBRARY_PATH",
                    prefix.resolve("usr/lib/x86_64-linux-gnu").toString());
        }
        Process p = pb.start();
        try (var in = p.getInputStream()) {
            in.readAllBytes();
        }
        p.waitFor(20, TimeUnit.SECONDS);
        p.destroyForcibly();
        return Files.readString(ser, StandardCharsets.ISO_8859_1).replace("\0", "");
    }

    private static String capture(String... cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor(20, TimeUnit.SECONDS);
        assertEquals(0, p.exitValue(), "comando falhou: " + String.join(" ", cmd) + "\n" + out);
        return out;
    }

    private static boolean hasTool(String tool, String... args) {
        String[] cmd = new String[args.length + 1];
        cmd[0] = tool;
        System.arraycopy(args, 0, cmd, 1, args.length);
        try {
            Process p = new ProcessBuilder(cmd).start();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static Path mcuPrefix() {
        String env = System.getenv("KOF_MCU_HOME");
        if (env != null && Files.isRegularFile(Path.of(env, "usr/bin/qemu-system-riscv32"))) {
            return Path.of(env);
        }
        Path home = Path.of(System.getProperty("user.home"), ".local/share/kof-mcu");
        if (Files.isRegularFile(home.resolve("usr/bin/qemu-system-riscv32"))) {
            return home;
        }
        return null;
    }

    private static Path findQemu() {
        if (hasTool("qemu-system-riscv32", "--version")) return Path.of("qemu-system-riscv32");
        Path p = mcuPrefix();
        return p != null ? p.resolve("usr/bin/qemu-system-riscv32") : null;
    }
}
