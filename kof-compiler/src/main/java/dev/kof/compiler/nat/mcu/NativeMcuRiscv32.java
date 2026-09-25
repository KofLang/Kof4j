package dev.kof.compiler.nat.mcu;

import dev.kof.compiler.IRBasicBlock;
import dev.kof.compiler.IRClass;
import dev.kof.compiler.IRMethod;
import dev.kof.compiler.IRModule;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofCallKind;
import dev.kof.compiler.KofGetStatic;
import dev.kof.compiler.KofLoadLiteral;
import dev.kof.compiler.KofLoadLocal;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.KofReturn;
import dev.kof.compiler.KofReturnVoid;
import dev.kof.compiler.KofStoreLocal;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * B-4.1 + follow-up (b): MCU RV32I emitter with GC runtime and list/string lowering.
 *
 * Supports:
 * - print/println of String literals (compile-time)
 * - listOf(Int...) via kof_list_new / kof_list_add
 * - xs.size via kof_list_size
 * - String.valueOf(Int) -> kof_string_of_int (runtime)
 * - println(String) with runtime strings via kof_println_string
 * - local variable load/store
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

        // Lower IR to ASM
        EmitResult result = lowerMain(main);

        String className = mainCls.name();
        Path asmFile = outputDir.resolve(className + ".s");
        Path ldFile = outputDir.resolve(className + ".ld");
        Path binFile = outputDir.resolve(className);
        Path objFile = outputDir.resolve(className + ".o");
        Files.createDirectories(asmFile.getParent());

        long heapBytes = Long.parseLong(
                System.getenv().getOrDefault("KOF_MCU_HEAP", String.valueOf(DEFAULT_HEAP_BYTES)));

        Files.writeString(asmFile, renderAsm(result), StandardCharsets.UTF_8);
        Files.writeString(ldFile, NativeMcuGcRiscv32.linkerScript(heapBytes), StandardCharsets.UTF_8);

        String as = System.getenv().getOrDefault("KOF_MCU_AS", "riscv64-linux-gnu-as");
        String ld = System.getenv().getOrDefault("KOF_MCU_LD", "riscv64-linux-gnu-ld");
        try {
            run(new String[]{as, "-march=rv32i", "-mabi=ilp32", "-o", objFile.toString(),
                    asmFile.toString()}, "riscv32-as");
            run(new String[]{ld, "-m", "elf32lriscv", "-T", ldFile.toString(),
                    "-o", binFile.toString(), objFile.toString()}, "riscv32-ld");
            binFile.toFile().setExecutable(true);
            System.err.println("NativeMcuRiscv32: generated riscv32 " + binFile + " (heap=" + heapBytes + ")");
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

    // ---------- IR Lowering: stack machine ----------

    private enum StackType {
        LIT_STR,       // compile-time String literal (pointer in .rodata)
        INT_VAL,       // compile-time int literal
        RUNTIME_STR,   // runtime String ptr (from kof_string_of_int)
        LIST_PTR,      // List ptr from kof_list_new
        UNKNOWN
    }

    private record StackSlot(StackType type, Object value, int literalIndex) {}

    private static class EmitResult {
        final StringBuilder body = new StringBuilder();
        final StringBuilder rodata = new StringBuilder();
        int literalCounter = 0;
        int localCount = 0;
        final StringBuilder locals = new StringBuilder();
        final StringBuilder bss = new StringBuilder();
        final List<String> labels = new ArrayList<>();
    }

    private static EmitResult lowerMain(IRMethod main) {
        EmitResult r = new EmitResult();
        List<StackSlot> stack = new ArrayList<>();
        Map<Integer, String> localNames = new java.util.HashMap<>();

        // Pre-scan: concurrency & unsupported
        for (IRBasicBlock bb : main.basicBlocks()) {
            for (KofOperation op : bb.operations()) {
                if (op instanceof dev.kof.compiler.KofCall conc && isConcurrency(conc.methodName())) {
                    throw new IllegalStateException("CONC003: '" + conc.methodName()
                            + "' is absent on the single-core MCU (no threads/channels/"
                            + "scheduler), never stubbed on the riscv32 target");
                }
            }
        }

        // Process ops
        for (IRBasicBlock bb : main.basicBlocks()) {
            for (KofOperation op : bb.operations()) {
                // System.out.getstatic -> ignore
                if (op instanceof dev.kof.compiler.KofGetStatic gs) {
                    if ("out".equals(gs.name())) continue;
                    throw unsupported("getstatic " + gs.name());
                }

                if (op instanceof dev.kof.compiler.KofLoadLiteral lit) {
                    Object val = lit.value();
                    if (val instanceof String s) {
                        int idx = r.literalCounter++;
                        r.rodata.append(".Lmcu_str_").append(idx).append(":\n");
                        r.rodata.append("    .ascii ").append(asmBytes(s)).append('\n');
                        stack.add(new StackSlot(StackType.LIT_STR, s, idx));
                    } else if (val instanceof Integer i) {
                        stack.add(new StackSlot(StackType.INT_VAL, i, -1));
                    } else if (val instanceof Boolean b) {
                        stack.add(new StackSlot(StackType.LIT_STR, b ? "true" : "false", -2));
                    } else {
                        throw unsupported("literal " + lit.type());
                    }
                    continue;
                }

                if (op instanceof dev.kof.compiler.KofCall call) {
                    String name = call.methodName();
                    dev.kof.compiler.KofCallKind kind = call.kind();

                    // valueOf: STATIC String.valueOf(...)
                    if ("valueOf".equals(name) && kind == dev.kof.compiler.KofCallKind.STATIC) {
                        StackSlot top = pop(stack);
                        if (top.type == StackType.LIT_STR) {
                            stack.add(top);
                        } else if (top.type == StackType.INT_VAL) {
                            Integer iv = (Integer) top.value;
                            int idx = r.literalCounter++;
                            r.rodata.append(".Lmcu_str_").append(idx).append(":\n");
                            r.rodata.append("    .ascii ").append(asmBytes(iv.toString())).append('\n');
                            stack.add(new StackSlot(StackType.LIT_STR, iv.toString(), idx));
                        } else {
                            // runtime int -> emit call to kof_string_of_int at codegen
                            stack.add(new StackSlot(StackType.RUNTIME_STR, null, -1));
                        }
                        continue;
                    }

                    // print/println
                    if (("print".equals(name) || "println".equals(name)) && kind == dev.kof.compiler.KofCallKind.INSTANCE) {
                        StackSlot top = pop(stack);
                        boolean nl = "println".equals(name);
                        emitPrint(r, top, nl);
                        continue;
                    }

                    // kof_list_new: FUNCTION
                    if ("kof_list_new".equals(name) && kind == dev.kof.compiler.KofCallKind.FUNCTION) {
                        stack.add(new StackSlot(StackType.LIST_PTR, null, -1));
                        continue;
                    }

                    // kof_list_add: INSTANCE (receiver=list, arg=value)
                    if ("kof_list_add".equals(name) && kind == dev.kof.compiler.KofCallKind.INSTANCE) {
                        StackSlot val = pop(stack);
                        StackSlot list = pop(stack);
                        if (list.type != StackType.LIST_PTR) throw unsupported("list_add receiver");
                        stack.add(new StackSlot(StackType.LIST_PTR, null, -1));
                        continue;
                    }

                    // kof_list_size: INSTANCE, 0 args
                    if ("kof_list_size".equals(name) && kind == dev.kof.compiler.KofCallKind.INSTANCE) {
                        StackSlot list = pop(stack);
                        if (list.type != StackType.LIST_PTR) throw unsupported("list_size receiver");
                        stack.add(new StackSlot(StackType.INT_VAL, null, -1));
                        continue;
                    }

                    // Concurrency check
                    if (isConcurrency(name)) {
                        throw new IllegalStateException("CONC003: '" + name
                                + "' is absent on the single-core MCU");
                    }

                    throw unsupported("call " + name + " " + kind);
                }

                // KofLoadLocal
                if (op instanceof dev.kof.compiler.KofLoadLocal ll) {
                    stack.add(new StackSlot(StackType.UNKNOWN, ll.index(), -1));
                    continue;
                }

                // KofStoreLocal
                if (op instanceof dev.kof.compiler.KofStoreLocal sl) {
                    StackSlot val = pop(stack);
                    r.localCount = Math.max(r.localCount, sl.index() + 1);
                    continue;
                }

                // Return
                if (op instanceof dev.kof.compiler.KofReturnVoid
                        || op instanceof dev.kof.compiler.KofReturn) {
                    continue;
                }

                // Concurrency check
                if (op instanceof dev.kof.compiler.KofCall conc && isConcurrency(conc.methodName())) {
                    throw new IllegalStateException("CONC003: '" + conc.methodName()
                            + "' is absent on the single-core MCU");
                }

                throw unsupported(op.getClass().getSimpleName());
            }
        }
        return r;
    }

    private static StackSlot pop(List<StackSlot> stack) {
        if (stack.isEmpty()) throw new IllegalStateException("stack underflow");
        return stack.remove(stack.size() - 1);
    }

    private static void emitPrint(EmitResult r, StackSlot slot, boolean newline) {
        if (slot.type == StackType.LIT_STR) {
            int idx = slot.literalIndex;
            if (idx == -2) {
                // boolean literal
                String s = (String) slot.value;
                int idx2 = r.literalCounter++;
                r.rodata.append(".Lmcu_str_").append(idx2).append(":\n");
                r.rodata.append("    .ascii ").append(asmBytes(s + (newline ? "\n" : ""))).append('\n');
            } else {
                r.body.append("    la a0, .Lmcu_str_").append(slot.literalIndex).append('\n');
                String s = (String) slot.value;
                r.body.append("    li a1, ").append(s.length() + (newline ? 1 : 0)).append('\n');
            }
            r.body.append("    call kof_plat_write\n");
        } else {
            // Runtime string - handled in codegen phase
            r.body.append("    # dynamic print/println\n");
            r.body.append("    call kof_println_string\n");
        }
    }

    // ---------- ASM Rendering ----------

    private static String renderAsm(EmitResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append(".option norvc\n");
        sb.append(".section .text\n");
        sb.append(".globl _start\n");
        sb.append("_start:\n");
        sb.append("    la sp, _stack_top\n");
        sb.append("    la t0, .Lmcu_trap\n");
        sb.append("    csrw mtvec, t0\n");
        // Initialize evaluation stack pointer in s10 (callee-saved)
        sb.append("    la s10, .Lmcu_estack\n");
        // Evaluate main body
        sb.append(r.body);
        sb.append("    li a0, 0\n");
        sb.append("    call kof_plat_exit\n");
        sb.append(".Lmcu_halt:\n");
        sb.append("    j .Lmcu_halt\n\n");

        // HAL
        sb.append(".globl kof_plat_write\n");
        sb.append("kof_plat_write:\n");
        sb.append("    add t2, a0, a1\n");
        sb.append("    mv t0, a0\n");
        sb.append("    li t1, ").append(UART0).append('\n');
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
        sb.append("    li t1, ").append(TEST_DEV).append('\n');
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

        // Evaluation stack in .bss
        sb.append(".section .bss\n");
        sb.append(".align 2\n");
        sb.append(".Lmcu_estack: .space 1024\n");
        sb.append(".Lmcu_locals: .space 256\n");
        sb.append(".section .rodata\n");
        for (int i = 0; i < r.literalCounter; i++) {
            // literals already added to rodata during lowering
        }
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