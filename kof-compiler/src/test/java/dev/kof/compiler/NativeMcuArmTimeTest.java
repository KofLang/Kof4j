package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.kof.compiler.nat.mcu.NativeMcuArmGc;
import dev.kof.compiler.nat.mcu.NativeMcuArmTime;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * B-4 follow-up (a) (PLAN-BAREMETAL-BOOT B-4/B-5 + {@code D-BAREMETAL-MCU-GC}
 * item 2): os corpos {@code kof_plat_time*} do MCU Cortex-M3 provados por
 * harness asm cru sob {@code qemu-system-arm -M mps2-an385}, ligando o runtime
 * de PRODUÇÃO ({@link NativeMcuArmTime#runtimeAsm()}) — espelho do
 * {@link NativeMcuTimeTest} do riscv32.
 *
 * <p>O wall é uma recusa NOMEADA (R6/R7, nunca epoch falsa); o monotônico é o
 * contador SysTick com {@code boot=0} e precisa ser não-decrescente. Guards
 * honestos (Q5): sem binutils arm-none-eabi/qemu → {@code assumeTrue} skip.
 */
class NativeMcuArmTimeTest implements NativeToolchainAssumptions {

    @Test
    void mcuArmWallTimeIsNamedRefusal(@TempDir Path tempDir) throws Exception {
        assumeMcuArmAsm();
        String out = run(tempDir, "tw", harness(
                "    ldr r0, =.Lts1\n    bl kof_plat_time\n"));
        assertTrue(out.contains("time.now() unavailable on MCU"),
                "o wall deveria recusar NOMEADAMENTE (sem epoch falsa): " + out);
    }

    @Test
    void mcuArmMonoTimeIsNonDecreasing(@TempDir Path tempDir) throws Exception {
        assumeMcuArmAsm();
        String out = run(tempDir, "tm", harness(
                "    ldr r0, =.Lts1\n    bl kof_plat_time_mono\n"
                + "    ldr r4, =.Lts1\n    ldr r5, [r4, #4]\n"
                + "    ldr r6, =500000\n"
                + ".Lmloop:\n    subs r6, r6, #1\n    bne .Lmloop\n"
                + "    ldr r0, =.Lts2\n    bl kof_plat_time_mono\n"
                + "    ldr r4, =.Lts2\n    ldr r6, [r4, #4]\n"
                + "    cmp r6, r5\n    blo .Lmbad\n"
                + "    ldr r0, =.Lok\n    ldr r1, =8\n    bl kof_plat_write\n"
                + "    b .Lmdone\n"
                + ".Lmbad:\n    ldr r0, =.Lbad\n    ldr r1, =9\n    bl kof_plat_write\n"
                + ".Lmdone:\n"));
        assertTrue(out.contains("MONO_OK"),
                "o contador monotônico deveria ser não-decrescente: " + out);
    }

    private static String harness(String ops) {
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
                .Lts1: .word 0
                       .word 0
                .Lts2: .word 0
                       .word 0
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

                .section .rodata
                .Lok:  .ascii "MONO_OK\\n"
                .Lbad: .ascii "MONO_BAD\\n"
                .pool
                """;
    }

    private String run(Path tempDir, String name, String program) throws Exception {
        Path asm = tempDir.resolve(name + ".s");
        Path ld = tempDir.resolve(name + ".ld");
        Path obj = tempDir.resolve(name + ".o");
        Path bin = tempDir.resolve(name);
        Files.writeString(asm, program + "\n" + NativeMcuArmTime.runtimeAsm());
        Files.writeString(ld, NativeMcuArmGc.linkerScript(0x4000));
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
        assumeTrue(cmd[0] != null, "ferramenta ausente");
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
