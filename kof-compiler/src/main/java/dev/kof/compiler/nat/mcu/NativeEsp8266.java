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
import java.util.List;
import java.util.Map;

/**
 * kof-esp8266 gate 1 (14/11): the ESP8266 (Xtensa L106) emitter — the THIRD MCU
 * face, following the `NativeMcuRiscv32` pattern (484 lines, the template the
 * gate-0 audit named).
 *
 * <p>Supports (the gate-0 measured subset):
 * - print/println of String literals (the UART0 HAL at 0x60000074)
 * - Int locals (load/store) and Int literals
 * - String.valueOf(Int) via the decimal printer in the runtime asm
 * - arithmetic on Int literals (+ - *)
 * <p>Everything else refuses NATIVE002 by name (the MCU contract: never a stub,
 * never a silent gate).
 *
 * <p>The L106 rules MEASURED in the gate-0 probes (recorded here because the
 * assembler rejects the naive emission):
 * - `.literal_position` must precede literals (`l32r: literal placed after
 *   use` otherwise);
 * - call targets must be 4-byte aligned (`call0: misaligned call target`);
 * - the toolchain emits LITTLE-ENDIAN only under
 *   `XTENSA_GNU_CONFIG=<lib>/xtensa_esp8266.so` (without it the objects come
 *   out big-endian and the esp8266 crt0 refuses to link);
 * - esp8266 ships NO linker scripts — this emitter writes its own (the
 *   memory map: irom0 at 0x40200010, dram at 0x3ffe8000, the entry from the
 *   overlay's real crt0).
 */
public final class NativeEsp8266 {

    private static final String UART0 = "0x60000074";
    private static final String UART0_STATUS = "0x60000078";
    private static final String UART0_CONF = "0x60000092";
    private static final long DEFAULT_HEAP_BYTES = 16384L; // 16 KB (the plan's §3: ~80 KB SRAM total)

    private NativeEsp8266() {}

    public static void emit(IRModule module, Path outputDir) throws IOException {
        IRClass mainCls = findMainClass(module);
        if (mainCls == null) {
            throw new IllegalStateException("NATIVE002: MCU esp8266 slice requires a main function");
        }
        IRMethod main = findMethod(mainCls, "main");
        if (main == null) {
            throw new IllegalStateException("NATIVE002: MCU esp8266 slice requires a main function");
        }

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
        Files.writeString(ldFile, linkerScript(heapBytes), StandardCharsets.UTF_8);

        String home = System.getenv().getOrDefault("KOF_ESP_HOME", discoverHome());
        String as = System.getenv().getOrDefault("KOF_ESP_AS",
                Path.of(home, "bin/xtensa-esp-elf-as").toString());
        String ld = System.getenv().getOrDefault("KOF_ESP_LD",
                Path.of(home, "bin/xtensa-esp-elf-ld").toString());
        String crt = System.getenv().getOrDefault("KOF_ESP_CRT0",
                Path.of(home, "xtensa-esp-elf/lib/esp8266/crt0.o").toString());
        try {
            run(new String[]{as, "-o", objFile.toString(), asmFile.toString()}, "esp8266-as");
            run(new String[]{ld, "-T", ldFile.toString(), "-o", binFile.toString(),
                    crt, objFile.toString()}, "esp8266-ld");
            binFile.toFile().setExecutable(true);
            System.err.println("NativeEsp8266: generated xtensa " + binFile + " (heap=" + heapBytes + ")");
        } catch (IOException e) {
            System.err.println("NativeEsp8266: esp8266 toolchain missing (NATIVE002),"
                    + " keeping asm: " + e.getMessage());
        }
    }

    private static long parseHeap(String v) {
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

    // ---------- IR lowering: the stack machine (the Riscv32 pattern) ----------

    private enum StackType {
        LIT_STR, INT_VAL, UNKNOWN
    }

    private record StackSlot(StackType type, Object value) {}

    private static class EmitResult {
        final StringBuilder body = new StringBuilder();
        final StringBuilder rodata = new StringBuilder();
        int literalCounter = 0;
        int localCount = 0;
        final List<String> labels = new ArrayList<>();
    }

    private static EmitResult lowerMain(IRMethod main) {
        EmitResult r = new EmitResult();
        List<StackSlot> stack = new ArrayList<>();
        Map<Integer, StackType> localTypes = new java.util.HashMap<>();
        Map<Integer, Object> localValues = new java.util.HashMap<>();

        for (IRBasicBlock bb : main.basicBlocks()) {
            for (KofOperation op : bb.operations()) {
                if (op instanceof dev.kof.compiler.KofGetStatic gs) {
                    if ("out".equals(gs.name())) continue;
                    throw unsupported("getstatic " + gs.name());
                }

                if (op instanceof KofLoadLiteral lit) {
                    Object val = lit.value();
                    if (val instanceof String s) {
                        stack.add(new StackSlot(StackType.LIT_STR, s));
                    } else if (val instanceof Integer i) {
                        r.body.append("    movi a0, ").append(i).append('\n');
                        espPush(r);
                        stack.add(new StackSlot(StackType.INT_VAL, i));
                    } else if (val instanceof Boolean b) {
                        stack.add(new StackSlot(StackType.LIT_STR, b ? "true" : "false"));
                    } else {
                        throw unsupported("literal " + lit.type());
                    }
                    continue;
                }

                if (op instanceof KofCall call) {
                    String name = call.methodName();
                    KofCallKind kind = call.kind();

                    if ("valueOf".equals(name) && kind == KofCallKind.STATIC) {
                        StackSlot top = pop(stack);
                        if (top.type == StackType.LIT_STR) {
                            stack.add(top);
                        } else if (top.type == StackType.INT_VAL) {
                            espPopTo(r, "a2");
                            r.body.append("    call0 kof_str_of_int\n");
                            espPush(r);
                            stack.add(new StackSlot(StackType.LIT_STR, null));
                        } else {
                            throw unsupported("valueOf of " + top.type);
                        }
                        continue;
                    }

                    if (("print".equals(name) || "println".equals(name))
                            && (kind == KofCallKind.STATIC || kind == KofCallKind.INSTANCE)) {
                        StackSlot top = pop(stack);
                        boolean nl = "println".equals(name);
                        emitPrint(r, top, nl);
                        continue;
                    }

                    throw unsupported("call " + name + " " + kind);
                }

                if (op instanceof KofLoadLocal ll) {
                    int off = 4 * ll.index();
                    r.body.append("    l32i a0, a13, ").append(off).append('\n');
                    espPush(r);
                    StackType t = localTypes.getOrDefault(ll.index(), StackType.UNKNOWN);
                    // a constant-propagated local re-enters the stack with its VALUE
                    // (the StoreLocal recorded it) — the arithmetic stays literal
                    Object v = t == StackType.INT_VAL ? localValues.get(ll.index()) : null;
                    stack.add(new StackSlot(t, v));
                    continue;
                }
                if (op instanceof KofStoreLocal sl) {
                    StackSlot val = pop(stack);
                    espPopTo(r, "a3");
                    r.body.append("    s32i a3, a13, ").append(4 * sl.index()).append('\n');
                    localTypes.put(sl.index(), val.type);
                    localValues.put(sl.index(), val.value);
                    r.localCount = Math.max(r.localCount, sl.index() + 1);
                    continue;
                }

                // KofBinary: the IR's own arithmetic op (the + - * on Ints)
                if (op instanceof dev.kof.compiler.KofBinary kb) {
                    StackSlot b = pop(stack);
                    StackSlot a = pop(stack);
                    if (a.type != StackType.INT_VAL || b.type != StackType.INT_VAL
                            || a.value == null || b.value == null) {
                        throw unsupported("arithmetic on " + a.type + "/" + b.type);
                    }
                    int x = (Integer) a.value;
                    int y = (Integer) b.value;
                    int res = switch (kb.op()) {
                        case ADD -> x + y;
                        case SUB -> x - y;
                        case MUL -> x * y;
                        default -> throw unsupported("binary " + kb.op());
                    };
                    r.body.append("    movi a0, ").append(res).append('\n');
                    espPush(r);
                    stack.add(new StackSlot(StackType.INT_VAL, res));
                    continue;
                }

                if (op instanceof KofReturnVoid || op instanceof KofReturn) {
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
            if (!newline) throw unsupported("print without newline of a dynamic value");
            espPopTo(r, "a2");
            r.body.append("    call0 kof_str_of_int\n");
            r.body.append("    call0 kof_println_string\n");
            return;
        }
        String text;
        if (slot.type == StackType.LIT_STR || slot.type == StackType.INT_VAL) {
            text = String.valueOf(slot.value);
        } else {
            throw unsupported("println of " + slot.type);
        }
        String payload = newline ? text + "\n" : text;
        int idx = r.literalCounter++;
        r.rodata.append(".Lesp_str_").append(idx).append(":\n");
        r.rodata.append("    .literal_position\n");
        r.rodata.append("    .ascii ").append(asmBytes(payload)).append('\n');
        // the L106 l32r discipline (measured): literals BEFORE the use
        r.body.append("    .literal .Lesp_addr_").append(idx).append(", .Lesp_str_").append(idx).append('\n');
        r.body.append("    l32r a2, .Lesp_addr_").append(idx).append('\n');
        r.body.append("    movi a3, ").append(payload.getBytes(StandardCharsets.UTF_8).length).append('\n');
        r.body.append("    call0 kof_plat_write\n");
    }

    /** The evaluation stack on a13 (the ESP8266 call0 ABI: a13 is frame-sized). */
    private static void espPush(EmitResult r) {
        r.body.append("    s32i a0, a13, 0\n");
        r.body.append("    addi a13, a13, 4\n");
    }

    private static void espPopTo(EmitResult r, String reg) {
        r.body.append("    addi a13, a13, -4\n");
        r.body.append("    l32i ").append(reg).append(", a13, 0\n");
    }

    // ---------- ASM rendering ----------

    private static String renderAsm(EmitResult r) {
        StringBuilder sb = new StringBuilder();
        sb.append("# kof.esp8266 — generated by NativeEsp8266 (gate 1)\n");
        sb.append("# the L106 rules: .literal_position before literals, call targets 4-aligned\n");
        sb.append("    .text\n");
        sb.append("    .globl main\n");
        sb.append("    .align 4\n");
        sb.append("main:\n");
        // the locals area on a13: carve it under the eval stack (the crt0 sets sp)
        sb.append("    movi a13, .Lesp_estack\n");
        sb.append(r.body);
        sb.append("    movi a2, 0\n");
        sb.append("    call0 kof_plat_exit\n");
        sb.append(".Lesp_halt:\n");
        sb.append("    j .Lesp_halt\n\n");

        // the decimal printer (String.valueOf(Int)): a2 = value, returns a2 = ptr
        sb.append("    .align 4\n");
        sb.append("kof_str_of_int:\n");
        sb.append("    .literal_position\n");
        sb.append("    movi a3, .Lesp_decbuf_end\n");
        sb.append("    mov a4, a2\n");
        sb.append("    movi a6, 10\n");
        sb.append("    addi a3, a3, -1\n");
        sb.append("    movi a5, 48\n");
        sb.append("    s8i a5, a3, 0\n");
        sb.append("    bnez a4, .Lesp_divloop\n");
        sb.append("    addi a3, a3, -1\n");
        sb.append("    movi a5, 48\n");
        sb.append("    s8i a5, a3, 0\n");
        sb.append("    j .Lesp_divdone\n");
        // the L106 has NO divide instruction (measured: 'remu'/'divu' are
        // unknown opcodes) — the digit loop is shift-subtract in a5/a4:
        // a4 = value, a6 = 10; repeat: q = 0; while a4 >= 10 { a4 -= 10; q++ }
        sb.append(".Lesp_divloop:\n");
        sb.append("    beqz a4, .Lesp_divdone\n");
        sb.append("    movi a7, 0\n");
        sb.append(".Lesp_subloop:\n");
        sb.append("    bltu a4, a6, .Lesp_subdone\n");
        sb.append("    sub a4, a4, a6\n");
        sb.append("    addi a7, a7, 1\n");
        sb.append("    j .Lesp_subloop\n");
        sb.append(".Lesp_subdone:\n");
        sb.append("    addi a5, a4, 48\n");
        sb.append("    addi a3, a3, -1\n");
        sb.append("    s8i a5, a3, 0\n");
        sb.append("    mov a4, a7\n");
        sb.append("    j .Lesp_divloop\n");
        sb.append(".Lesp_divdone:\n");
        sb.append("    mov a2, a3\n");
        sb.append("    ret\n\n");

        // the HAL: UART0 out (the gate-0 measured addresses)
        sb.append("    .align 4\n");
        sb.append("kof_plat_write:\n");
        sb.append("    .literal_position\n");
        sb.append("    movi a5, ").append(UART0).append('\n');
        sb.append(".Lesp_w:\n");
        sb.append("    beqz a3, .Lesp_wd\n");
        sb.append("    l8ui a6, a2, 0\n");
        sb.append("    s8i a6, a5, 0\n");
        sb.append("    addi a2, a2, 1\n");
        sb.append("    addi a3, a3, -1\n");
        sb.append("    j .Lesp_w\n");
        sb.append(".Lesp_wd:\n");
        sb.append("    movi a2, 0\n");
        sb.append("    ret\n\n");

        sb.append("    .align 4\n");
        sb.append("kof_println_string:\n");
        sb.append("    .literal_position\n");
        sb.append("    movi a3, 1\n");
        sb.append("    movi a5, ").append(UART0).append('\n');
        sb.append("    movi a6, 10\n");
        sb.append("    s8i a6, a5, 0\n");
        sb.append("    movi a2, 0\n");
        sb.append("    ret\n\n");

        sb.append("    .align 4\n");
        sb.append("kof_plat_exit:\n");
        sb.append("    .literal_position\n");
        sb.append("    movi a5, ").append(UART0_CONF).append('\n');
        sb.append("    movi a6, 5\n");
        sb.append("    s8i a6, a5, 0\n");
        sb.append(".Lesp_hang:\n");
        sb.append("    j .Lesp_hang\n\n");

        // the eval stack + locals in .bss (the dram window)
        sb.append("    .section .bss\n");
        sb.append("    .align 4\n");
        sb.append(".Lesp_estack: .space 1024\n");
        sb.append(".Lesp_decbuf: .space 16\n");
        sb.append(".Lesp_decbuf_end:\n");

        sb.append("    .section .rodata\n");
        sb.append(r.rodata);
        return sb.toString();
    }

    /** The linker script: esp8266 ships NONE (measured, gate 0) — the emitter
     *  writes its own with the REAL memory map (the `NativeMcuRiscv32` pattern). */
    private static String linkerScript(long heapBytes) {
        return """
                /* kof.esp8266 — generated by NativeEsp8266 (gate 1) */
                MEMORY
                {
                  iram0_0_seg : org = 0x40100000, len = 0x8000
                  irom0_0_seg : org = 0x40200010, len = 0xf0000
                  dram0_0_seg : org = 0x3FFE8000, len = 0x14000
                }
                PHDRS { dram0_0_phdr PT_LOAD; irom0_0_phdr PT_LOAD; }
                SECTIONS
                {
                  .data : { *(.data) *(.rodata) *(.rodata.*) } > dram0_0_seg :dram0_0_phdr
                  .text : { *(.literal) *(.text) *(.text.*) } > irom0_0_seg :irom0_0_phdr
                  .bss  : { *(.bss) *(.bss.*) *(COMMON) } > dram0_0_seg :dram0_0_phdr
                }
                """;
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
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        // THE GATE-0 MEASURED LAW: without the esp8266 dynconfig the objects come
        // out BIG-endian and the esp8266 crt0 refuses to link. The .so ships in
        // the toolchain's lib/; the env carries it to the forked tools.
        String root = System.getenv().getOrDefault("KOF_ESP_HOME", discoverHome());
        Path so = Path.of(root, "lib/xtensa_esp8266.so");
        if (Files.isRegularFile(so)) {
            pb.environment().put("XTENSA_GNU_CONFIG", so.toString());
        }
        Process p = pb.start();
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

    /** The toolchain root: the provision target first, then the gate-0 audit's
     *  working dir (both measured). */
    static String discoverHome() {
        for (Path p : List.of(
                Path.of(System.getProperty("user.home"), ".local/share/kof-esp8266"),
                Path.of("/tmp/xtensa162"))) {
            if (Files.isExecutable(p.resolve("bin/xtensa-esp-elf-as"))) return p.toString();
        }
        return Path.of(System.getProperty("user.home"), ".local/share/kof-esp8266").toString();
    }

    private static IllegalStateException unsupported(String what) {
        return new IllegalStateException("NATIVE002: MCU esp8266 slice does not support '"
                + what + "' yet (the gate-1 subset: print/println of String literals,"
                + " Int locals/literals/arithmetic)");
    }
}
