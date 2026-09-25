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
 * B-4 follow-up (b): MCU list/string runtime provado por harness asm cru.
 * Testa kof_list_new, kof_list_add, kof_list_size, kof_string_of_int,
 * kof_println_string sob qemu-system-riscv32 -M virt.
 */
class NativeMcuListTest {

    private static final long HEAP = 0x10000; // 64 KB

    @Test
    void mcuListNewAddSizeAndPrint(@TempDir Path tempDir) throws Exception {
        assumeToolchain();
        String out = run(tempDir, "list1", body("""
                li   a0, 0              # list = list_new()
                call kof_list_new
                mv   s0, a0

                li   a1, 1              # list_add(list, 1)
                mv   a0, s0
                call kof_list_add

                li   a1, 2              # list_add(list, 2)
                mv   a0, s0
                call kof_list_add

                li   a1, 3              # list_add(list, 3)
                mv   a0, s0
                call kof_list_add

                mv   a0, s0             # size = list_size(list)
                call kof_list_size
                call kof_string_of_int  # int -> String
                call kof_println_string  # println(String)

                li   a0, 0
                call kof_plat_exit
                """), HEAP);
        assertTrue(out.contains("3"),
                "list size 3 deve ser impresso: " + out);
    }

    @Test
    void mcuListEmptySizePrintsZero(@TempDir Path tempDir) throws Exception {
        assumeToolchain();
        String out = run(tempDir, "list2", body("""
                li   a0, 0
                call kof_list_new
                mv   a0, a0
                call kof_list_size
                call kof_string_of_int
                call kof_println_string

                li   a0, 0
                call kof_plat_exit
                """), HEAP);
        assertTrue(out.contains("0"),
                "lista vazia size = 0: " + out);
    }

    @Test
    void mcuStringOfIntPrintsDecimal(@TempDir Path tempDir) throws Exception {
        assumeToolchain();
        String out = run(tempDir, "list3", body("""
                li   a0, 42
                call kof_string_of_int
                call kof_println_string

                li   a0, -7
                call kof_string_of_int
                call kof_println_string

                li   a0, 0x80000000
                call kof_string_of_int
                call kof_println_string

                li   a0, 0
                call kof_plat_exit
                """), HEAP);
        assertTrue(out.contains("42"),
                "kof_string_of_int(42) deve imprimir 42: " + out);
        assertTrue(out.contains("-7"),
                "negativo deve imprimir -7: " + out);
        assertTrue(out.contains("-2147483648"),
                "INT_MIN (magnitude unsigned) deve imprimir -2147483648: " + out);
    }

    @Test
    void mcuListManyAddsAndSize(@TempDir Path tempDir) throws Exception {
        assumeToolchain();
        StringBuilder ops = new StringBuilder();
        ops.append("    call kof_list_new\n    mv s0, a0\n");
        for (int i = 1; i <= 10; i++) {
            ops.append("    li   a1, ").append(i).append("\n");
            ops.append("    mv   a0, s0\n    call kof_list_add\n");
        }
        ops.append("    mv   a0, s0\n    call kof_list_size\n");
        ops.append("    call kof_string_of_int\n");
        ops.append("    call kof_println_string\n");
        String out = run(tempDir, "list4", body(ops.toString()), HEAP);
        assertTrue(out.contains("10"),
                "size após 10 adds = 10: " + out);
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

    private String run(Path tempDir, String name, String program, long heapBytes) throws Exception {
        Path asm = tempDir.resolve(name + ".s");
        Path ld = tempDir.resolve(name + ".ld");
        Path obj = tempDir.resolve(name + ".o");
        Path bin = tempDir.resolve(name);
        Files.writeString(asm, program + "\n" + NativeMcuGcRiscv32.all());
        Files.writeString(ld, dev.kof.compiler.nat.mcu.NativeMcuGcRiscv32.linkerScript(heapBytes));
        capture("riscv64-linux-gnu-as", "-march=rv32i", "-mabi=ilp32", "-o", obj.toString(),
                asm.toString());
        capture("riscv64-linux-gnu-ld", "-m", "elf32lriscv", "-T", ld.toString(), "-o",
                tempDir.resolve(name).toString(), obj.toString());
        tempDir.resolve(name).toFile().setExecutable(true);
        return boot(tempDir, tempDir.resolve(name));
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
        p.waitFor(20, java.util.concurrent.TimeUnit.SECONDS);
        p.destroyForcibly();
        return Files.readString(ser, StandardCharsets.ISO_8859_1).replace("\0", "");
    }

    private void assumeToolchain() {
        assumeTrue(hasTool("riscv64-linux-gnu-as", "--version"),
                "binutils riscv64 ausente (riscv64-linux-gnu-as)");
    }

    private static String capture(String... cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor(20, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(0, p.exitValue(), "comando falhou: " + String.join(" ", cmd) + "\n" + out);
        return out;
    }

    private static boolean hasTool(String tool, String... args) {
        String[] cmd = new String[args.length + 1];
        cmd[0] = tool;
        System.arraycopy(args, 0, cmd, 1, args.length);
        try {
            Process p = new ProcessBuilder(cmd).start();
            return p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0;
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