package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.kof.compiler.nat.mcu.NativeMcuTimeRiscv32;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * B4-TIME (PLAN-BAREMETAL-BOOT B-4/B-5 + {@code D-BAREMETAL-MCU-GC} item 2):
 * os corpos {@code kof_plat_time*} do MCU RV32I provados por harness asm cru sob
 * {@code qemu-system-riscv32 -M virt}, ligando o runtime de PRODUÇÃO
 * ({@link NativeMcuTimeRiscv32#runtimeAsm()}).
 *
 * <p>O wall é uma recusa NOMEADA (R6/R7, nunca epoch falsa); o monotônico é o
 * contador {@code time} com {@code boot=0} e precisa ser não-decrescente.
 * Guards honestos (Q5): sem binutils riscv64/qemu → {@code assumeTrue} skip.
 */
class NativeMcuTimeTest implements NativeToolchainAssumptions {

    @Test
    void mcuWallTimeIsNamedRefusal(@TempDir Path tempDir) throws Exception {
        assumeMcuRiscvAsm();
        String out = run(tempDir, "tw", harness(
                "    la   a0, .Lts1\n    call kof_plat_time\n"), 0x4000);
        assertTrue(out.contains("time.now() unavailable on MCU"),
                "o wall deveria recusar NOMEADAMENTE (sem epoch falsa): " + out);
    }

    @Test
    void mcuMonoTimeIsNonDecreasing(@TempDir Path tempDir) throws Exception {
        assumeMcuRiscvAsm();
        String out = run(tempDir, "tm", harness(
                "    la   s1, .Lts1\n    mv   a0, s1\n    call kof_plat_time_mono\n"
                + "    lw   s0, 4(s1)\n"
                + "    li   s2, 500000\n"
                + ".Lmloop:\n    addi s2, s2, -1\n    bnez s2, .Lmloop\n"
                + "    la   s1, .Lts2\n    mv   a0, s1\n    call kof_plat_time_mono\n"
                + "    lw   s3, 4(s1)\n"
                + "    bltu s3, s0, .Lmbad\n"
                + "    la   a0, .Lok\n    li   a1, 8\n    call kof_plat_write\n"
                + "    j    .Lmdone\n"
                + ".Lmbad:\n    la   a0, .Lbad\n    li   a1, 9\n    call kof_plat_write\n"
                + ".Lmdone:\n"), 0x4000);
        assertTrue(out.contains("MONO_OK"),
                "o contador monotônico deveria ser não-decrescente: " + out);
    }

    private static String harness(String ops) {
        return """
                .option norvc
                .section .data
                .align 2
                .Lts1: .word 0
                       .word 0
                .Lts2: .word 0
                       .word 0
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

                .section .rodata
                .Lok:  .ascii "MONO_OK\\n"
                .Lbad: .ascii "MONO_BAD\\n"
                """;
    }

    private String run(Path tempDir, String name, String program, long heapBytes) throws Exception {
        Path asm = tempDir.resolve(name + ".s");
        Path ld = tempDir.resolve(name + ".ld");
        Path obj = tempDir.resolve(name + ".o");
        Path bin = tempDir.resolve(name);
        Files.writeString(asm, program + "\n" + NativeMcuTimeRiscv32.runtimeAsm());
        Files.writeString(ld, dev.kof.compiler.nat.mcu.NativeMcuGcRiscv32.linkerScript(heapBytes));
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
