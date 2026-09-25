package dev.kof.compiler.nat.mcu;

import dev.kof.compiler.IRBasicBlock;
import dev.kof.compiler.IRClass;
import dev.kof.compiler.IRMethod;
import dev.kof.compiler.IRModule;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofCallKind;
import dev.kof.compiler.KofGetStatic;
import dev.kof.compiler.KofLoadLiteral;
import dev.kof.compiler.KofOperation;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * B-4.1 + follow-up (b): MCU RV32I emitter with GC runtime wired in.
 * The slice supports print/println of String literals. The GC runtime
 * (alloc/mark/sweep + list/string) is emitted so that hand-coded harness
 * tests can exercise list/string ops on the MCU.
 */
public final class NativeMcuRiscv32 {

    private static final String UART0 = "0x10000000";
    private static final String TEST_DEV = "0x100000";
    private static final long DEFAULT_HEAP_BYTES = 65536L; // 64 KB

    private NativeMcuRiscv32() {}

    public static void emit(IRModule module, Path outputDir) throws IOException {
        IRClass mainCls = findMainClass(module);
        if (mainCls == null) {
            throw new IllegalStateException(
                    "NATIVE002: MCU riscv32 slice requires a main function");
        }
        IRMethod main = findMethod(mainCls, "main");
        if (main == null) {
            throw new IllegalStateException(
                    "NATIVE002: MCU riscv32 slice requires a main function");
        }

        List<String> output = collectPrints(main);

        String className = mainCls.name();
        Path asmFile = outputDir.resolve(className + ".s");
        Path ldFile = outputDir.resolve(className + ".ld");
        Path binFile = outputDir.resolve(className);
        Path objFile = outputDir.resolve(className + ".o");
        Files.createDirectories(asmFile.getParent());

        long heapBytes = Long.parseLong(
                System.getenv().getOrDefault("KOF_MCU_HEAP", String.valueOf(65536L)));

        Files.writeString(asmFile, renderAsm(output), StandardCharsets.UTF_8);
        Files.writeString(ldFile, NativeMcuGcRiscv32.linkerScript(65536L), StandardCharsets.UTF_8);

        String as = System.getenv().getOrDefault("KOF_MCU_AS", "riscv64-linux-gnu-as");
        String ld = System.getenv().getOrDefault("KOF_MCU_LD", "riscv64-linux-gnu-ld");
        try {
            run(new String[]{as, "-march=rv32i", "-mabi=ilp32", "-o", objFile.toString(),
                    asmFile.toString()}, "riscv32-as");
            run(new String[]{ld, "-m", "elf32lriscv", "-T", ldFile.toString(),
                    "-o", binFile.toString(), objFile.toString()}, "riscv32-ld");
            binFile.toFile().setExecutable(true);
            System.err.println("NativeMcuRiscv32: generated riscv32 " + objFile + " (heap=64KB)");
        } catch (IOException e) {
            System.err.println("NativeBackend: riscv32 MCU toolchain missing (NATIVE002),"
                    + " keeping asm: " + e.getMessage());
        }
    }

    private static IRClass findMainClass(IRModule module) {
        for (IRClass c : module.classes()) {
            if (findMethod(c, "main") != null) return c;
        }
        return null;
    }

    private static IRMethod findMethod(IRClass c, String name) {
        for (IRMethod m : c.methods()) {
            if (name.equals(m.name())) return m;
        }
        return null;
    }

    private static List<String> collectPrints(IRMethod main) {
        for (IRBasicBlock bb : main.basicBlocks()) {
            for (KofOperation op : bb.operations()) {
                if (op instanceof dev.kof.compiler.KofCall conc && isConcurrency(conc.methodName())) {
                    throw new IllegalStateException("CONC003: '" + conc.methodName()
                            + "' is absent on the single-core MCU");
                }
            }
        }
        List<String> out = new ArrayList<>();
        String pending = null;
        for (IRBasicBlock bb : main.basicBlocks()) {
            for (KofOperation op : bb.operations()) {
                if (op instanceof dev.kof.compiler.KofGetStatic gs) {
                    if ("out".equals(gs.name())) continue;
                    throw unsupported(gs.getClass().getSimpleName());
                }
                if (op instanceof dev.kof.compiler.KofLoadLiteral lit) {
                    if (lit.value() instanceof String s) {
                        pending = s;
                        continue;
                    }
                    throw unsupported("literal " + lit.type());
                }
                if (op instanceof dev.kof.compiler.KofCall vo && "valueOf".equals(vo.methodName())
                        && vo.kind() == dev.kof.compiler.KofCallKind.STATIC) {
                    continue;
                }
                if (op instanceof dev.kof.compiler.KofCall call
                        && ("print".equals(call.methodName()) || "println".equals(call.methodName()))
                        && call.kind() == dev.kof.compiler.KofCallKind.INSTANCE) {
                    if (pending == null) {
                        throw new IllegalStateException("NATIVE002: MCU riscv32 slice only"
                                + " supports print/println of a String literal");
                    }
                    out.add("println".equals(call.methodName()) ? pending + "\n" : pending);
                    pending = null;
                    continue;
                }
                if (op instanceof dev.kof.compiler.KofReturnVoid
                        || op instanceof dev.kof.compiler.KofReturn) {
                    continue;
                }
                if (op instanceof dev.kof.compiler.KofCall conc && isConcurrency(conc.methodName())) {
                    throw new IllegalStateException("CONC003: '" + conc.methodName()
                            + "' is absent on the single-core MCU");
                }
                throw unsupported(op.getClass().getSimpleName());
            }
        }
        return out;
    }

    private static String renderAsm(List<String> output) {
        StringBuilder sb = new StringBuilder();
        sb.append(".option norvc\n");
        sb.append(".section .text\n");
        sb.append(".globl _start\n");
        sb.append("_start:\n");
        sb.append("    la sp, _stack_top\n");
        sb.append("    la t0, .Lmcu_trap\n");
        sb.append("    csrw mtvec, t0\n");
        for (int i = 0; i < output.size(); i++) {
            byte[] b = output.get(i).getBytes(StandardCharsets.UTF_8);
            sb.append("    la a0, .Lmcu_str_").append(i).append('\n');
            sb.append("    li a1, ").append(b.length).append('\n');
            sb.append("    call kof_plat_write\n");
        }
        sb.append("    li a0, 0\n");
        sb.append("    call kof_plat_exit\n");
        sb.append(".Lmcu_halt:\n");
        sb.append("    j .Lmcu_halt\n\n");

        // HAL
        sb.append(".globl kof_plat_write\n");
        sb.append("kof_plat_write:\n");
        sb.append("    add t2, a0, a1\n");
        sb.append("    mv t0, a0\n");
        sb.append("    li t1, 0x10000000\n");
        sb.append(".Lmcu_write_loop:\n");
        sb.append("    bgeu t0, t2, .Lmcu_write_done\n");
        sb.append("    lbu a0, 0(t0)\n");
        sb.append("    sb a0, 0(t1)\n");
        sb.append("    addi t0, t0, 1\n");
        sb.append("    j .Lmcu_write_loop\n");
        sb.append(".Lmcu_write_done:\n");
        sb.append("    ret\n\n");
        sb.append(".globl kof_plat_exit\n");
        sb.append("kof_plat_exit:\n");
        sb.append("    li t1, 0x100000\n");
        sb.append("    li t0, 0x5555\n");
        sb.append("    sw t0, 0(t1)\n");
        sb.append(".Lmcu_exit_halt:\n");
        sb.append("    j .Lmcu_exit_halt\n\n");

        // Thread ID + random + time
        sb.append(".globl kof_plat_thread_id\n");
        sb.append("kof_plat_thread_id:\n");
        sb.append("    csrr a0, mhartid\n");
        sb.append("    ret\n\n");
        sb.append(".globl kof_plat_random\n");
        sb.append("kof_plat_random:\n");
        sb.append("    csrr t2, cycle\n");
        sb.append(".Lmcu_rand_loop:\n");
        sb.append("    beqz a1, .Lmcu_rand_done\n");
        sb.append("    slli t3, t2, 13\n");
        sb.append("    xor t2, t2, t3\n");
        sb.append("    srli t3, t2, 17\n");
        sb.append("    xor t2, t2, t3\n");
        sb.append("    slli t3, t2, 5\n");
        sb.append("    xor t2, t2, t3\n");
        sb.append("    sb t2, 0(a0)\n");
        sb.append("    addi a0, a0, 1\n");
        sb.append("    addi a1, a1, -1\n");
        sb.append("    j .Lmcu_rand_loop\n");
        sb.append(".Lmcu_rand_done:\n");
        sb.append("    ret\n\n");
        sb.append(".align 2\n");
        sb.append(".Lmcu_trap:\n");
        sb.append("    j .Lmcu_trap\n\n");

        // Time (wall refusal + mono + sleep)
        sb.append(dev.kof.compiler.nat.mcu.NativeMcuTimeRiscv32.runtimeAsm());

        // GC runtime (alloc + mark + sweep + list + string)
        sb.append(dev.kof.compiler.nat.mcu.NativeMcuGcRiscv32.all());

        sb.append(".section .rodata\n");
        return sb.toString();
    }

    private static String asmBytes(String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        StringBuilder b = new StringBuilder("\"");
        for (byte value : bytes) {
            int c = value & 0xFF;
            switch (c) {
                case '\\' -> b.append("\\\\");
                case '"' -> b.append("\\\"");
                default -> {
                    if (c >= 32 && c < 127) b.append((char) c);
                    else b.append(String.format("\\%03o", c));
                }
            }
        }
        return b.append('"').toString();
    }

    private static void run(String[] cmd, String what) throws IOException {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out;
        try {
            out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS) || p.exitValue() != 0) {
                throw new IOException(what + " failed: " + out);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(what + " interrupted", e);
        }
    }

    private static boolean isConcurrency(String name) {
        if (name == null) return false;
        return name.startsWith("kof_spawn") || name.startsWith("kof_await")
                || name.startsWith("kof_channel") || name.startsWith("kof_scheduler")
                || name.equals("kof_plat_sync") || name.equals("kof_plat_thread")
                || name.equals("kof_plat_thread_create");
    }

    private static IllegalStateException unsupported(String what) {
        return new IllegalStateException("NATIVE002: MCU riscv32 slice does not support '"
                + what + "' yet (print/println of String literals; list/string ops in harness tests)");
    }
}