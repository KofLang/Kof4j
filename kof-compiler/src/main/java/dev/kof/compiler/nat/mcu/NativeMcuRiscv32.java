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
import java.util.Map;
import java.util.List;

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

        long heapBytes = parseHeap(
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

    static long parseHeap(String v) {
        long n;
        try {
            n = Long.parseLong(v);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("NATIVE002: KOF_MCU_HEAP is not a number: '" + v + "'", e);
        }
        if (n <= 0) {
            throw new IllegalStateException("NATIVE002: KOF_MCU_HEAP must be positive: " + n);
        }
        return n;
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
        Map<Integer, StackType> localTypes = new java.util.HashMap<>();

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
                        stack.add(new StackSlot(StackType.LIT_STR, s, -1));
                    } else if (val instanceof Integer i) {
                        r.body.append("    li a0, ").append(i).append('\n');
                        rtPush(r);
                        stack.add(new StackSlot(StackType.INT_VAL, i, -1));
                    } else if (val instanceof Boolean b) {
                        stack.add(new StackSlot(StackType.LIT_STR, b ? "true" : "false", -1));
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
                        } else if (top.type == StackType.INT_VAL && top.value != null) {
                            rtPopTo(r, "t0");
                            stack.add(new StackSlot(StackType.LIT_STR, top.value.toString(), -1));
                        } else if (top.type == StackType.INT_VAL) {
                            // int runtime (ex.: xs.size) -> kof_string_of_int (B-4.2 LANDADA)
                            rtPopTo(r, "a0");
                            r.body.append("    call kof_string_of_int\n");
                            rtPush(r);
                            stack.add(new StackSlot(StackType.RUNTIME_STR, null, -1));
                        } else {
                            throw unsupported("valueOf of " + top.type);
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

                    // kof_list_new/add/size — B-4.2 LANDADA (opção F, mantenedora 25/09):
                    // pilha de avaliação s10 liga argumentos de verdade; o runtime é o
                    // provado por NativeMcuListTest (growable, header estável).
                    if ("kof_list_new".equals(name) && kind == dev.kof.compiler.KofCallKind.FUNCTION) {
                        r.body.append("    call kof_list_new\n");
                        rtPush(r);
                        stack.add(new StackSlot(StackType.LIST_PTR, null, -1));
                        continue;
                    }
                    if ("kof_list_add".equals(name) && kind == dev.kof.compiler.KofCallKind.INSTANCE) {
                        StackSlot val = pop(stack);
                        StackSlot list = pop(stack);
                        if (list.type != StackType.LIST_PTR) throw unsupported("list_add receiver");
                        if (val.type != StackType.INT_VAL || val.value == null) {
                            throw unsupported("list_add element (so int literal na fatia F)");
                        }
                        rtPopTo(r, "a1");
                        rtPopTo(r, "a0");
                        r.body.append("    call kof_list_add\n");
                        rtPush(r);
                        stack.add(new StackSlot(StackType.LIST_PTR, null, -1));
                        continue;
                    }
                    if ("kof_list_size".equals(name) && kind == dev.kof.compiler.KofCallKind.INSTANCE) {
                        StackSlot list = pop(stack);
                        if (list.type != StackType.LIST_PTR) throw unsupported("list_size receiver");
                        rtPopTo(r, "a0");
                        r.body.append("    call kof_list_size\n");
                        rtPush(r);
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

                // KofLoadLocal: .Lmcu_locals[i] -> pilha de avaliação (B-4.2 LANDADA)
                if (op instanceof dev.kof.compiler.KofLoadLocal ll) {
                    r.body.append("    la t0, .Lmcu_locals\n");
                    r.body.append("    lw a0, ").append(4 * ll.index()).append("(t0)\n");
                    rtPush(r);
                    stack.add(new StackSlot(localTypes.getOrDefault(ll.index(), StackType.UNKNOWN), null, -1));
                    continue;
                }
                if (op instanceof dev.kof.compiler.KofStoreLocal sl) {
                    StackSlot val = pop(stack);
                    rtPopTo(r, "t1");
                    r.body.append("    la t0, .Lmcu_locals\n");
                    r.body.append("    sw t1, ").append(4 * sl.index()).append("(t0)\n");
                    localTypes.put(sl.index(), val.type);
                    continue;
                }

                // KofDup: copia o topo da pilha de avaliação (runtime + modelo)
                if (op instanceof dev.kof.compiler.KofDup) {
                    if (stack.isEmpty()) throw unsupported("dup em pilha vazia");
                    StackSlot top = stack.get(stack.size() - 1);
                    if (top.type != StackType.LIST_PTR && top.type != StackType.RUNTIME_STR
                            && top.type != StackType.INT_VAL) {
                        throw unsupported("dup de " + top.type);
                    }
                    r.body.append("    lw a0, -4(s10)\n");
                    rtPush(r);
                    stack.add(top);
                    continue;
                }

                // Return
                if (op instanceof dev.kof.compiler.KofReturnVoid
                        || op instanceof dev.kof.compiler.KofReturn) {
                    continue;
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
        if (slot.type == StackType.INT_VAL && slot.value == null) {
            // int runtime (ex.: xs.size) — B-4.2 LANDADA: string_of_int + println
            if (!newline) throw unsupported("print sem newline de valor dinamico");
            rtPopTo(r, "a0");
            r.body.append("    call kof_string_of_int\n");
            r.body.append("    call kof_println_string\n");
            return;
        }
        if (slot.type == StackType.RUNTIME_STR) {
            if (!newline) throw unsupported("print sem newline de String dinamica");
            rtPopTo(r, "a0");
            r.body.append("    call kof_println_string\n");
            return;
        }
        String text;
        if (slot.type == StackType.LIT_STR || slot.type == StackType.INT_VAL) {
            if (slot.type == StackType.INT_VAL) {
                rtPopTo(r, "t0");
            }
            text = String.valueOf(slot.value);
        } else {
            throw unsupported("println of " + slot.type);
        }
        // Payload EXATO no .rodata (incluindo o '\n' do println): o tamanho
        // baixado é o de BYTES UTF-8 (não s.length() — multi-byte leria lixo),
        // e o byte extra nunca vem "depois" do literal (o vizinho de .ascii é
        // o próximo literal, medido: "ab\nc" virava "abcc" sob qemu).
        String payload = newline ? text + "\n" : text;
        int idx = r.literalCounter++;
        r.rodata.append(".Lmcu_str_").append(idx).append(":\n");
        r.rodata.append("    .ascii ").append(asmBytes(payload)).append('\n');
        r.body.append("    la a0, .Lmcu_str_").append(idx).append('\n');
        r.body.append("    li a1, ").append(payload.getBytes(StandardCharsets.UTF_8).length).append('\n');
        r.body.append("    call kof_plat_write\n");
    }

    private static void rtPush(EmitResult r) {
        r.body.append("    sw a0, 0(s10)\n    addi s10, s10, 4\n");
    }

    private static void rtPopTo(EmitResult r, String reg) {
        r.body.append("    addi s10, s10, -4\n    lw ").append(reg).append(", 0(s10)\n");
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
        // Região de raízes estáticas do kof_gc_mark: VAZIA — o lowering de
        // statics/locals (B-4.2) ainda não deposita ponteiros em memória, e
        // um range adjacente (start==end) é no-op honesto; engolir a .data do
        // runtime (kof_alloc_ptr = cursor do bump, não objeto) marcar lixo.
        sb.append(".section .data\n");
        sb.append(".align 2\n");
        sb.append(".Lkof_heap_root_start:\n");
        sb.append(".Lkof_heap_root_end:\n");
        sb.append(".section .rodata\n");
        sb.append(r.rodata);
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
        // IOException SOMENTE quando o processo não parte (toolchain ausente —
        // guard honesto de ambiente). Exit != 0 = ASM RUIM gerada pelo
        // compilador: falha NATIVE002 visível (R6), nunca "toolchain missing"
        // engolido com success=true sem imagem (§506, medida no link).
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out;
        try {
            out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new IllegalStateException("NATIVE002: " + what + " timeout");
            }
            if (p.exitValue() != 0) {
                throw new IllegalStateException("NATIVE002: " + what + " failed: " + out);
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