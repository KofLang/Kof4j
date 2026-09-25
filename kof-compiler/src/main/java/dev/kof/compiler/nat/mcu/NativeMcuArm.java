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
import dev.kof.compiler.KofReturn;
import dev.kof.compiler.KofReturnVoid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * B-4.3 (PLAN-BAREMETAL-BOOT): emissor do MCU ARM Cortex-M3 (Thumb-2).
 *
 * <p>Irmão do {@link NativeMcuRiscv32} no mesmo degrau: compila {@code main}
 * cujo corpo imprime literais String ({@code print}/{@code println}) para a UART
 * CMSDK do {@code qemu-system-arm -M mps2-an385} (0x40004000) e encerra em halt.
 * A diferença de arquitetura está na imagem: uma <b>vector table</b> em 0x0
 * ({@code [0]=SP}, {@code [1]=Reset_Handler|1}) — o reset path que o plano B-4
 * exige asserido — e no ISA Thumb-2 (assembler/linker {@code arm-none-eabi-*}).
 * Qualquer op fora do subset falha com {@code NATIVE002}; concorrência é
 * {@code CONC003} (single-core). Nunca um artefato que finge rodar (R6, Q7).
 */
public final class NativeMcuArm {

    /** UART0 (CMSDK) do mps2-an385; CTRL em +0x08 (TXEN|RXEN=3), STATE +0x04
     *  (bit0 TXFULL), DATA +0x00. Sem CTRL=3 a UART descarta o byte. */
    private static final String UART0 = "0x40004000";
    private static final String STACK_TOP = "0x00080000";

    private NativeMcuArm() {}

    public static void emit(IRModule module, Path outputDir) throws IOException {
        IRClass mainCls = findMainClass(module);
        IRMethod main = mainCls == null ? null : findMethod(mainCls, "main");
        if (main == null) {
            throw new IllegalStateException(
                    "NATIVE002: MCU cortex-m slice requires a main function");
        }

        List<String> output = collectPrints(main);

        String className = mainCls.name();
        Path asmFile = outputDir.resolve(className + ".s");
        Path ldFile = outputDir.resolve(className + ".ld");
        Path binFile = outputDir.resolve(className);
        Path objFile = outputDir.resolve(className + ".o");
        Files.createDirectories(asmFile.getParent());

        Files.writeString(asmFile, renderAsm(output), StandardCharsets.UTF_8);
        Files.writeString(ldFile, LINKER_SCRIPT, StandardCharsets.UTF_8);

        String as = tool("KOF_MCU_AS", "arm-none-eabi-as");
        String ld = tool("KOF_MCU_LD", "arm-none-eabi-ld");
        try {
            run(new String[]{as, "-mcpu=cortex-m3", "-mthumb", "-o", objFile.toString(),
                    asmFile.toString()}, "cortex-m-as");
            run(new String[]{ld, "-T", ldFile.toString(), "-o", binFile.toString(),
                    objFile.toString()}, "cortex-m-ld");
            binFile.toFile().setExecutable(true);
            System.err.println("NativeMcuArm: generated cortex-m " + binFile);
        } catch (IOException e) {
            System.err.println("NativeBackend: cortex-m MCU toolchain missing (NATIVE002),"
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

    /**
     * Aceita apenas {@code System.out.print/println(String literal)}; devolve o
     * texto de cada escrita (com {@code \n} nos {@code println}). Qualquer op
     * fora do subset → {@code NATIVE002}; concorrência → {@code CONC003}.
     */
    private static List<String> collectPrints(IRMethod main) {
        // Pre-scan: single-core MCU has no threads/channels/scheduler. The
        // lowering emits the closure object BEFORE kof_spawn*, so scan first.
        for (IRBasicBlock bb : main.basicBlocks()) {
            for (KofOperation op : bb.operations()) {
                if (op instanceof KofCall conc && isConcurrency(conc.methodName())) {
                    throw new IllegalStateException("CONC003: '" + conc.methodName()
                            + "' is absent on the single-core MCU (no threads/channels/"
                            + "scheduler), never stubbed on the cortex-m target");
                }
            }
        }
        List<String> out = new ArrayList<>();
        String pending = null;
        for (IRBasicBlock bb : main.basicBlocks()) {
            for (KofOperation op : bb.operations()) {
                if (op instanceof KofGetStatic gs) {
                    if ("out".equals(gs.name())) continue;
                    throw unsupported(gs.getClass().getSimpleName());
                }
                if (op instanceof KofLoadLiteral lit) {
                    if (lit.value() instanceof String s) {
                        pending = s;
                        continue;
                    }
                    throw unsupported("literal " + lit.type());
                }
                // print/println(String) passes through String.valueOf first
                // (ExpressionPrintLowerer); for a String literal it is a no-op.
                if (op instanceof KofCall vo && "valueOf".equals(vo.methodName())
                        && vo.kind() == KofCallKind.STATIC) {
                    continue;
                }
                if (op instanceof KofCall call
                        && ("print".equals(call.methodName()) || "println".equals(call.methodName()))
                        && call.kind() == KofCallKind.INSTANCE) {
                    if (pending == null) {
                        throw new IllegalStateException("NATIVE002: MCU cortex-m slice only"
                                + " supports print/println of a String literal");
                    }
                    out.add("println".equals(call.methodName()) ? pending + "\n" : pending);
                    pending = null;
                    continue;
                }
                if (op instanceof KofReturnVoid || op instanceof KofReturn) {
                    continue;
                }
                throw unsupported(op.getClass().getSimpleName());
            }
        }
        return out;
    }

    private static boolean isConcurrency(String name) {
        if (name == null) return false;
        return name.startsWith("kof_spawn") || name.startsWith("kof_await")
                || name.startsWith("kof_channel") || name.startsWith("kof_scheduler")
                || name.equals("kof_plat_sync") || name.equals("kof_plat_thread")
                || name.equals("kof_plat_thread_create");
    }

    private static IllegalStateException unsupported(String what) {
        return new IllegalStateException("NATIVE002: MCU cortex-m slice does not support '"
                + what + "' yet (only print/println of String literals in main)");
    }

    private static String renderAsm(List<String> output) {
        StringBuilder sb = new StringBuilder();
        sb.append(".syntax unified\n");
        sb.append(".thumb\n");
        sb.append(".cpu cortex-m3\n");
        // Vector table at 0x0: [0]=initial SP, [1]=Reset_Handler (Thumb bit set),
        // [2]=NMI, [3]=HardFault (-> .Lmcu_fault halt, honest — never a jump to 0).
        sb.append(".section .vectors,\"a\"\n");
        sb.append(".globl _vectors\n");
        sb.append("_vectors:\n");
        sb.append("    .word _stack_top\n");
        sb.append("    .word Reset_Handler + 1\n");
        sb.append("    .word 0\n");
        sb.append("    .word HardFault_Handler + 1\n");
        sb.append("    .space 0x100 - 16, 0\n\n");
        sb.append(".section .text\n");
        sb.append(".align 2\n");
        sb.append(".thumb_func\n");
        sb.append(".globl Reset_Handler\n");
        sb.append("Reset_Handler:\n");
        sb.append("    ldr r4, =").append(UART0).append('\n');
        sb.append("    movs r1, #3\n");
        sb.append("    str r1, [r4, #8]\n"); // CTRL: TXEN|RXEN
        for (int i = 0; i < output.size(); i++) {
            byte[] b = output.get(i).getBytes(StandardCharsets.UTF_8);
            sb.append("    ldr r0, =.Lmcu_str_").append(i).append('\n');
            sb.append("    ldr r1, =").append(b.length).append('\n');
            sb.append("    bl kof_plat_write\n");
        }
        sb.append("    movs r0, #0\n");
        sb.append("    bl kof_plat_exit\n");
        sb.append(".Lmcu_halt:\n");
        sb.append("    b .Lmcu_halt\n\n");
        // MCU HAL bodies (PLAN-BAREMETAL-BOOT §3) in Thumb-2/AAPCS:
        // kof_plat_write(buf,len) -> CMSDK UART; kof_plat_exit(code) -> halt;
        // kof_plat_thread_id() -> 0 (single core); kof_plat_random(buf,len) ->
        // xorshift32 seeded by an address/constant (never all-zero).
        sb.append(".thumb_func\n");
        sb.append(".globl kof_plat_write\n");
        sb.append("kof_plat_write:\n");
        sb.append("    ldr r3, =").append(UART0).append('\n');
        sb.append("    add r2, r0, r1\n");
        sb.append(".Lmcu_write_loop:\n");
        sb.append("    cmp r0, r2\n");
        sb.append("    bhs .Lmcu_write_done\n");
        sb.append("    ldrb r1, [r0]\n");
        sb.append(".Lmcu_write_wait:\n");
        sb.append("    ldr r12, [r3, #4]\n"); // STATE
        sb.append("    tst r12, #1\n"); // TXFULL?
        sb.append("    bne .Lmcu_write_wait\n");
        sb.append("    str r1, [r3]\n"); // DATA
        sb.append("    adds r0, r0, #1\n");
        sb.append("    b .Lmcu_write_loop\n");
        sb.append(".Lmcu_write_done:\n");
        sb.append("    bx lr\n\n");
        sb.append(".thumb_func\n");
        sb.append(".globl kof_plat_exit\n");
        sb.append("kof_plat_exit:\n");
        sb.append(".Lmcu_exit_halt:\n");
        sb.append("    b .Lmcu_exit_halt\n\n");
        sb.append(".thumb_func\n");
        sb.append(".globl kof_plat_thread_id\n");
        sb.append("kof_plat_thread_id:\n");
        sb.append("    movs r0, #0\n"); // single core (mps2-an385 is one Cortex-M3)
        sb.append("    bx lr\n\n");
        sb.append(".thumb_func\n");
        sb.append(".globl kof_plat_random\n");
        sb.append("kof_plat_random:\n");
        sb.append("    ldr r2, =").append(STACK_TOP).append('\n');
        sb.append("    ldr r3, =0x9E3779B9\n");
        sb.append("    eors r2, r2, r3\n"); // non-zero seed (xorshift dies at 0)
        sb.append(".Lmcu_rand_loop:\n");
        sb.append("    cbz r1, .Lmcu_rand_done\n");
        sb.append("    lsls r3, r2, #13\n");
        sb.append("    eors r2, r2, r3\n");
        sb.append("    lsrs r3, r2, #17\n");
        sb.append("    eors r2, r2, r3\n");
        sb.append("    lsls r3, r2, #5\n");
        sb.append("    eors r2, r2, r3\n");
        sb.append("    strb r2, [r0]\n");
        sb.append("    adds r0, r0, #1\n");
        sb.append("    subs r1, r1, #1\n");
        sb.append("    b .Lmcu_rand_loop\n");
        sb.append(".Lmcu_rand_done:\n");
        sb.append("    bx lr\n\n");
        sb.append(".thumb_func\n");
        sb.append(".globl HardFault_Handler\n");
        sb.append("HardFault_Handler:\n");
        sb.append(".Lmcu_fault_halt:\n");
        sb.append("    b .Lmcu_fault_halt\n\n");
        sb.append(".pool\n");
        sb.append(".section .rodata\n");
        for (int i = 0; i < output.size(); i++) {
            sb.append(".Lmcu_str_").append(i).append(":\n");
            sb.append("    .ascii ").append(asmBytes(output.get(i))).append('\n');
        }
        // B-4/item 2: os corpos kof_plat_time* (recusa de wall + mono SysTick),
        // no mesmo degrau do NativeMcuRiscv32 (paridade riscv32/cortex-m).
        sb.append(NativeMcuArmTime.runtimeAsm());
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

    /**
     * Resolve a ferramenta bare-metal: env var explícita, senão o prefixo
     * provisionado sem root ({@code ~/.local/share/kof-mcu/usr/bin}, ver
     * {@code scripts/provision-mcu-qemu.sh}), senão o PATH.
     */
    private static String tool(String envKey, String name) {
        String v = System.getenv(envKey);
        if (v != null && !v.isBlank()) return v;
        Path home = mcuHome();
        if (home != null) {
            Path t = home.resolve("usr/bin").resolve(name);
            if (Files.isExecutable(t)) return t.toString();
        }
        return name;
    }

    private static Path mcuHome() {
        String env = System.getenv("KOF_MCU_HOME");
        if (env != null && Files.isDirectory(Path.of(env))) return Path.of(env);
        Path home = Path.of(System.getProperty("user.home"), ".local/share/kof-mcu");
        return Files.isDirectory(home) ? home : null;
    }

    private static void run(String[] cmd, String what) throws IOException {
        // §506 (espelho do riscv32): exit != 0 = ASM ruim do compilador →
        // NATIVE002 visível; IOException só quando o processo não parte.
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

    private static final String LINKER_SCRIPT = """
            ENTRY(Reset_Handler)
            SECTIONS {
              . = 0x00000000;
              .vectors : { KEEP(*(.vectors)) }
              .text : { *(.text*) }
              .rodata : { *(.rodata*) }
              .data : { *(.data*) }
              .bss : { *(.bss*) *(COMMON) }
              . = ALIGN(8);
              _stack_top = 0x00080000;
            }
            """;
}
