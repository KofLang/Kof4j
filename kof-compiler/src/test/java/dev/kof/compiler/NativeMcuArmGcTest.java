package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.kof.compiler.nat.mcu.NativeMcuArmGc;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * B-4 follow-up (a) (PLAN-BAREMETAL-BOOT B-4 + {@code D-BAREMETAL-MCU-GC}):
 * alocador + mark conservador + sweep do MCU <b>Cortex-M3 (Thumb-2)</b> provados
 * por harness asm cru sob {@code qemu-system-arm -M mps2-an385} — espelho do
 * {@link NativeMcuGcTest} do riscv32. O runtime de PRODUÇÃO
 * ({@link NativeMcuArmGc#all()}) é concatenado a um harness que chama
 * {@code kof_alloc}/{@code kof_free}/{@code kof_gc_dump}/{@code kof_memstats}
 * direto. O vector table em 0x0 traz o reset path ({@code [0]=SP},
 * {@code [1]=Reset_Handler|1}).
 *
 * <p>Guards honestos (Q5): binutils {@code arm-none-eabi-*} + {@code
 * qemu-system-arm} provisionados em {@code ~/.local/share/kof-mcu} (ver
 * {@code scripts/provision-mcu-qemu.sh}). Sem eles → {@code assumeTrue} skip,
 * nunca verde falso.
 */
class NativeMcuArmGcTest implements NativeToolchainAssumptions {

    private static final long HEAP = 0x10000; // 64 KB

    @Test
    void mcuArmGcAllocatesAndDumps(@TempDir Path tempDir) throws Exception {
        assumeMcuArmAsm();
        String out = run(tempDir, "gc1", body(
                "    movs r0, #16\n    bl kof_alloc\n    ldr r1, =.Lroot_a\n    str r0, [r1]\n"
                + "    movs r0, #16\n    bl kof_alloc\n"
                + "    movs r0, #16\n    bl kof_alloc\n"
                + "    movs r0, #16\n    bl kof_alloc\n"
                + "    bl kof_gc_dump\n    bl kof_memstats\n"), HEAP);
        assertEquals(4, count(out, "gc 32 0"), "4 blocos de 32B (header 16 + 16) na gc-list: " + out);
        assertTrue(out.contains("allocs: 4"), "allocs deveria ser 4: " + out);
        assertTrue(out.contains("frees: 0"), "frees deveria ser 0: " + out);
        assertTrue(out.contains("live bytes: 128"), "live bytes deveria ser 128: " + out);
    }

    @Test
    void mcuArmGcMarkMarksRootsTransitively(@TempDir Path tempDir) throws Exception {
        assumeMcuArmAsm();
        // A = raiz ESTÁTICA (.Lroot_a); B = raiz de PILHA; C = inalcançável;
        // D = alcançável só via A.payload[0] (fecho transitivo).
        String out = run(tempDir, "gc4", body(
                "    movs r0, #16\n    bl kof_alloc\n    ldr r1, =.Lroot_a\n    str r0, [r1]\n"
                + "    sub sp, sp, #16\n"
                + "    movs r0, #16\n    bl kof_alloc\n    str r0, [sp]\n"
                + "    movs r0, #16\n    bl kof_alloc\n"
                + "    movs r0, #16\n    bl kof_alloc\n    mov r4, r0\n"
                + "    ldr r1, =.Lroot_a\n    ldr r2, [r1]\n    str r4, [r2]\n"
                + "    bl kof_gc_mark\n    bl kof_gc_dump\n"), HEAP);
        assertEquals(java.util.List.of("gc 32 1", "gc 32 0", "gc 32 1", "gc 32 1"),
                gcLines(out),
                "gc-list LIFO D,C,B,A: D/A transitivo e B pilha marcados (1); C morto fica 0: " + out);
    }

    @Test
    void mcuArmGcSweepRecoversDeadAndKeepsLive(@TempDir Path tempDir) throws Exception {
        assumeMcuArmAsm();
        String out = run(tempDir, "gc5", body(
                "    movs r0, #16\n    bl kof_alloc\n    ldr r1, =.Lroot_a\n    str r0, [r1]\n"
                + "    sub sp, sp, #16\n"
                + "    movs r0, #16\n    bl kof_alloc\n    str r0, [sp]\n"
                + "    movs r0, #16\n    bl kof_alloc\n"
                + "    movs r0, #16\n    bl kof_alloc\n    mov r4, r0\n"
                + "    ldr r1, =.Lroot_a\n    ldr r2, [r1]\n    str r4, [r2]\n"
                + "    bl kof_gc_collect_now\n    bl kof_gc_dump\n    bl kof_memstats\n"),
                HEAP);
        assertEquals(java.util.List.of("gc 32 0", "gc 32 2", "gc 32 0", "gc 32 0"),
                gcLines(out),
                "sweep deve limpar o mark de D/A/B e pôr C (flags 2) na free-list: " + out);
        assertTrue(out.contains("frees: 1"), "C deveria contar uma free: " + out);
        assertTrue(out.contains("live bytes: 96"), "128 alocados - 32 recuperados = 96: " + out);
    }

    @Test
    void mcuArmGcMarkHandlesCycles(@TempDir Path tempDir) throws Exception {
        assumeMcuArmAsm();
        String out = run(tempDir, "gc6", body(
                "    movs r0, #16\n    bl kof_alloc\n    ldr r1, =.Lroot_a\n    str r0, [r1]\n"
                + "    movs r0, #16\n    bl kof_alloc\n    mov r4, r0\n"
                + "    ldr r1, =.Lroot_a\n    ldr r2, [r1]\n    str r4, [r2]\n    str r2, [r4]\n"
                + "    bl kof_gc_collect_now\n    bl kof_gc_dump\n    bl kof_memstats\n"),
                HEAP);
        assertEquals(java.util.List.of("gc 32 0", "gc 32 0"), gcLines(out),
                "ciclo A<->E termina e os dois sobrevivem (flags 0): " + out);
        assertTrue(out.contains("frees: 0"), "nada deve morrer no ciclo: " + out);
        assertTrue(out.contains("live bytes: 64"), "os dois blocos seguem vivos: " + out);
    }

    @Test
    void mcuArmGcLongAllocLoopIsRecycled(@TempDir Path tempDir) throws Exception {
        assumeMcuArmAsm();
        String out = run(tempDir, "gc7", body(loopOps(10000)), HEAP);
        assertTrue(out.contains("allocs: 10000"),
                "o laço deveria completar 10000 allocs reciclando o heap: " + out);
        assertTrue(out.matches("(?s).*frees: [1-9][0-9]*.*"),
                "o coletor deveria ter liberado blocos mortos: " + out);
    }

    @Test
    void mcuArmGcLongAllocLoopOomsWithoutCollector(@TempDir Path tempDir) throws Exception {
        assumeMcuArmAsm();
        // Sabotagem: removido o hook do coletor no OOM → o bump esgota e panica.
        String sabotaged = NativeMcuArmGc.all().replace("bl kof_gc_collect_now\n", "");
        String out = runWith(tempDir, "gc8", body(loopOps(10000)), HEAP, sabotaged);
        assertTrue(out.contains("out of memory"),
                "SEM coletor o laço DEVERIA estourar o heap e panicar (não-vacuidade): " + out);
        assertTrue(!out.contains("allocs: 10000"),
                "sem coletor o laço NÃO deveria completar: " + out);
    }

    private static String loopOps(int n) {
        return "    ldr r4, =" + n + "\n"
                + ".Lloop:\n"
                + "    movs r0, #16\n    bl kof_alloc\n"
                + "    ldr r1, =.Lroot_a\n    str r0, [r1]\n"
                + "    subs r4, r4, #1\n    bne .Lloop\n"
                + "    bl kof_memstats\n";
    }

    @Test
    void mcuArmGcFreeIsReusedByNextAlloc(@TempDir Path tempDir) throws Exception {
        assumeMcuArmAsm();
        String out = run(tempDir, "gc2", body(
                "    movs r0, #16\n    bl kof_alloc\n    mov r4, r0\n"
                + "    mov r0, r4\n    bl kof_free\n"
                + "    movs r0, #16\n    bl kof_alloc\n"
                + "    bl kof_gc_dump\n    bl kof_memstats\n"), HEAP);
        assertEquals(1, count(out, "gc 32 0"),
                "o free deve ser reusado pelo alloc seguinte (um só bloco de 32B): " + out);
        assertTrue(out.contains("allocs: 2"), "allocs deveria ser 2: " + out);
        assertTrue(out.contains("frees: 1"), "frees deveria ser 1: " + out);
        assertTrue(out.contains("live bytes: 32"),
                "live bytes deveria ser 32 (um bloco vivo, o outro reciclado): " + out);
    }

    @Test
    void mcuArmGcOomPanicsWithDiagnostic(@TempDir Path tempDir) throws Exception {
        assumeMcuArmAsm();
        // Heap de 4 KB; pedido de 32 KB estoura o bump → panic nomeado (R6/Q7).
        String out = run(tempDir, "gc3", body(
                "    ldr r0, =0x8000\n    bl kof_alloc\n    bl kof_memstats\n"), 0x1000);
        assertTrue(out.contains("out of memory"),
                "OOM deveria panicar com 'out of memory', veio: [" + out + "]");
    }

    private static String body(String ops) {
        return """
                .syntax unified
                .thumb
                .cpu cortex-m3
                .section .vectors,"a"
                _vectors:
                    .word _stack_top
                    .word Reset_Handler + 1
                    .word 0
                    .word HardFault_Handler + 1
                    .space 0x100 - 16, 0
                .section .data
                .align 2
                .Lkof_heap_root_start: .word 0
                .Lroot_a: .word 0
                .Lkof_heap_root_end:
                .section .text
                .thumb_func
                .globl Reset_Handler
                Reset_Handler:
                    ldr r4, =0x40004000
                    movs r1, #3
                    str r1, [r4, #8]
                """ + ops + """
                    movs r0, #0
                    bl kof_plat_exit
                .Lhalt:
                    b .Lhalt

                .thumb_func
                .globl kof_plat_write
                kof_plat_write:
                    ldr r3, =0x40004000
                    add r2, r0, r1
                .Lw_loop:
                    cmp r0, r2
                    bhs .Lw_done
                    ldrb r1, [r0]
                .Lw_wait:
                    ldr r12, [r3, #4]
                    tst r12, #1
                    bne .Lw_wait
                    str r1, [r3]
                    add r0, r0, #1
                    b .Lw_loop
                .Lw_done:
                    bx lr

                .thumb_func
                .globl kof_plat_exit
                kof_plat_exit:
                    ldr r1, =0x20026
                    movs r0, #0x18
                    bkpt #0xAB
                .Le_halt:
                    b .Le_halt

                .thumb_func
                .globl HardFault_Handler
                HardFault_Handler:
                    b .
                .pool
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
        return runWith(tempDir, name, program, heapBytes, NativeMcuArmGc.all());
    }

    private String runWith(Path tempDir, String name, String program, long heapBytes, String runtime)
            throws Exception {
        Path asm = tempDir.resolve(name + ".s");
        Path ld = tempDir.resolve(name + ".ld");
        Path obj = tempDir.resolve(name + ".o");
        Path bin = tempDir.resolve(name);
        Files.writeString(asm, program + "\n" + runtime);
        Files.writeString(ld, NativeMcuArmGc.linkerScript(heapBytes));
        capture(tool("arm-none-eabi-as"), "-mcpu=cortex-m3", "-mthumb", "-o", obj.toString(),
                asm.toString());
        capture(tool("arm-none-eabi-ld"), "-T", ld.toString(), "-o", bin.toString(), obj.toString());
        bin.toFile().setExecutable(true);
        return boot(tempDir, bin);
    }

    private String boot(Path tempDir, Path bin) throws Exception {
        String qemu = tool("qemu-system-arm");
        assumeTrue(qemu != null, "qemu-system-arm ausente (scripts/provision-mcu-qemu.sh)");
        Path ser = tempDir.resolve("ser.log");
        ProcessBuilder pb = new ProcessBuilder(qemu, "-M", "mps2-an385", "-display", "none",
                "-semihosting", "-serial", "file:" + ser, "-kernel", bin.toString())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD);
        Path prefix = mcuPrefix();
        if (prefix != null && qemu.startsWith(prefix.toString())) {
            pb.environment().put("LD_LIBRARY_PATH",
                    prefix.resolve("usr/lib/x86_64-linux-gnu").toString());
        }
        Process p = pb.start();
        p.waitFor(30, TimeUnit.SECONDS);
        p.destroyForcibly();
        return Files.readString(ser, StandardCharsets.ISO_8859_1).replace("\0", "");
    }

    /** Caminho da ferramenta: PATH ou prefixo provisionado sem root. */
    private static String tool(String name) {
        if (hasTool(name, "--version")) return name;
        Path p = mcuPrefix();
        if (p != null) {
            Path t = p.resolve("usr/bin").resolve(name);
            if (Files.isExecutable(t)) return t.toString();
        }
        return null;
    }

    private static String capture(String... cmd) throws IOException, InterruptedException {
        assumeTrue(cmd[0] != null, "ferramenta ausente: " + cmd[1]);
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
        if (env != null && Files.isDirectory(Path.of(env))) return Path.of(env);
        Path home = Path.of(System.getProperty("user.home"), ".local/share/kof-mcu");
        return Files.isDirectory(home) ? home : null;
    }
}
