package dev.kof.c;

import java.util.List;

/**
 * Emissor aarch64 (GAS, ABI AAPCS64) do subconjunto C — freestanding: só
 * syscalls cruas ({@code exit}=93, {@code write}=64), montado por
 * {@code aarch64-linux-gnu-as} + {@code ld} e rodado sob {@code qemu-aarch64}.
 *
 * <p>Acumulador = {@code x0} (também o retorno da ABI); a pilha (16 bytes por
 * temporário) guarda o lado esquerdo das binárias; {@code x1} é o operando
 * direito. Frame: {@code x29} é a base, com {@code x29}/{@code x30} salvos por
 * {@code stp}; parâmetros e locais em {@code [x29, #-(8*(slot+1))]} (offsets
 * negativos usam {@code ldur}/{@code stur}). Globais por {@code adrp}+{@code :lo12:}.
 * Argumentos em {@code x0..x5}.
 */
final class KofCEmitterAarch extends KofCEmitterBase {

    private static final String[] ARG_REGS = {"x0", "x1", "x2", "x3", "x4", "x5"};

    KofCEmitterAarch(KofCAst.Program prog, boolean executable) { super(prog, executable); }

    @Override
    protected void emitDataSection(List<KofCAst.VarDecl> globals) {
        if (!globals.isEmpty()) {
            sb.append("    .bss\n");
            for (var g : globals) {
                sb.append("    .globl ").append(g.name()).append("\n");
                sb.append("    .comm ").append(g.name()).append(",").append(gsize(g.type())).append(",8\n");
            }
        }
    }

    @Override
    protected void emitStart() {
        sb.append("    .globl _start\n");
        sb.append("_start:\n");
        sb.append("    bl main\n");
        sb.append("    mov x8, #93\n"); // exit
        sb.append("    mov x0, #0\n");
        sb.append("    svc #0\n");
    }

    @Override
    protected void emitPrintHelpers() {
        sb.append("kof_print_int:\n");
        sb.append("    sub sp, sp, #64\n");
        sb.append("    add x1, sp, #64\n");
        sb.append("    mov w2, #10\n");
        sb.append("    strb w2, [x1, #-1]\n");
        sb.append("    sub x1, x1, #1\n");
        sb.append("    mov x3, #1\n");
        sb.append("    cbnz x0, .Lkof_print_loop\n");
        sb.append("    mov w2, #48\n");
        sb.append("    sub x1, x1, #1\n");
        sb.append("    strb w2, [x1]\n");
        sb.append("    add x3, x3, #1\n");
        sb.append("    b .Lkof_print_write\n");
        sb.append(".Lkof_print_loop:\n");
        sb.append("    cbz x0, .Lkof_print_write\n");
        sb.append("    mov x4, #10\n");
        sb.append("    udiv x5, x0, x4\n");
        sb.append("    msub x6, x5, x4, x0\n");
        sb.append("    add w6, w6, #48\n");
        sb.append("    sub x1, x1, #1\n");
        sb.append("    strb w6, [x1]\n");
        sb.append("    add x3, x3, #1\n");
        sb.append("    mov x0, x5\n");
        sb.append("    b .Lkof_print_loop\n");
        sb.append(".Lkof_print_write:\n");
        sb.append("    mov x2, x3\n");
        sb.append("    mov x0, #1\n");
        sb.append("    mov x8, #64\n"); // write
        sb.append("    svc #0\n");
        sb.append("    add sp, sp, #64\n");
        sb.append("    ret\n");
        sb.append("kof_print:\n");
        sb.append("    adrp x1, print_arg\n");
        sb.append("    add x1, x1, :lo12:print_arg\n");
        sb.append("    ldr x0, [x1]\n");
        sb.append("    b kof_print_int\n");
        if (!hasGlobal("print_arg")) {
            sb.append("    .bss\n");
            sb.append("    .globl print_arg\n");
            sb.append("    .comm print_arg,8,8\n");
            sb.append("    .text\n");
        }
    }

    @Override
    protected void emitFuncPrologue(int frameSlots) {
        sb.append("    stp x29, x30, [sp, #-16]!\n");
        sb.append("    mov x29, sp\n");
        if (frameSlots > 0) sb.append("    sub sp, sp, #").append(frameBytes(frameSlots)).append("\n");
    }

    @Override
    protected void emitFuncEpilogue() {
        sb.append("    mov sp, x29\n");
        sb.append("    ldp x29, x30, [sp], #16\n");
        sb.append("    ret\n");
    }

    /** Bytes do frame, arredondados p/ 16 (mantém alinhamento da ABI). */
    private static int frameBytes(int slots) { return ((slots + 1) / 2) * 16; }

    @Override
    protected void emitStoreParam(int argIndex, int slot, int eightbytes) {
        for (int j = 0; j < eightbytes; j++)
            sb.append("    stur ").append(ARG_REGS[argIndex + j]).append(", [x29, #-").append(offset(slot - j)).append("]\n");
    }

    private int gsize(String type) { int b = structBytes(type); return b == 0 ? 8 : b; }

    private static int offset(int slot) { return 8 * (slot + 1); }

    @Override
    protected void emitLoadImm(int v) {
        movImm("x0", v);
    }

    /** Move um imediato de 64 bits (padrão x86: sign-extend do int). */
    private void movImm(String reg, long value) {
        sb.append("    movz ").append(reg).append(", #").append(value & 0xFFFF).append("\n");
        for (int shift : new int[]{16, 32, 48}) {
            int part = (int) ((value >>> shift) & 0xFFFF);
            if (part != 0) {
                sb.append("    movk ").append(reg).append(", #").append(part)
                        .append(", lsl #").append(shift).append("\n");
            }
        }
    }

    @Override
    protected void emitLoadStorage(Storage storage) {
        if (storage.local()) {
            sb.append("    ldur x0, [x29, #-").append(offset(storage.slot())).append("]\n");
        } else {
            adrp("x1", storage.name());
            sb.append("    ldr x0, [x1]\n");
        }
    }

    @Override
    protected void emitStoreStorage(Storage storage) {
        if (storage.local()) {
            sb.append("    stur x0, [x29, #-").append(offset(storage.slot())).append("]\n");
        } else {
            adrp("x1", storage.name());
            sb.append("    str x0, [x1]\n");
        }
    }

    @Override
    protected void emitLoadField(Storage storage, int byteOffset) {
        if (storage.local()) {
            sb.append("    ldursw x0, [x29, #-").append(fieldDisp(storage.slot(), byteOffset)).append("]\n");
        } else {
            adrp("x1", storage.name());
            sb.append("    ldrsw x0, [x1").append(globalDisp(byteOffset)).append("]\n");
        }
    }

    @Override
    protected void emitStoreField(Storage storage, int byteOffset) {
        if (storage.local()) {
            sb.append("    stur w0, [x29, #-").append(fieldDisp(storage.slot(), byteOffset)).append("]\n");
        } else {
            adrp("x1", storage.name());
            sb.append("    str w0, [x1").append(globalDisp(byteOffset)).append("]\n");
        }
    }

    /** Frame displacement of a field: base slot minus the field byte offset. */
    private static int fieldDisp(int slot, int byteOffset) { return offset(slot) - byteOffset; }

    private static String globalDisp(int byteOffset) { return byteOffset == 0 ? "" : ", #" + byteOffset; }

    @Override
    protected void emitAddrOfStorage(Storage storage) {
        if (storage.local()) {
            sb.append("    sub x0, x29, #").append(offset(storage.slot())).append("\n");
        } else {
            adrp("x0", storage.name());
        }
    }

    @Override
    protected void emitLoadThroughStorage(Storage storage) {
        emitPtr(storage, "x1");
        sb.append("    ldr x0, [x1]\n");
    }

    @Override
    protected void emitDerefStoreStorage(Storage storage) {
        emitPtr(storage, "x1");
        sb.append("    str x0, [x1]\n");
    }

    /** Carrega o valor da variável (ponteiro) em {@code reg}, preservando {@code x0}. */
    private void emitPtr(Storage storage, String reg) {
        if (storage.local()) {
            sb.append("    ldur ").append(reg).append(", [x29, #-").append(offset(storage.slot())).append("]\n");
        } else {
            adrp(reg, storage.name());
            sb.append("    ldr ").append(reg).append(", [").append(reg).append("]\n");
        }
    }

    /** {@code adrp reg, sym} + {@code add reg, reg, :lo12:sym}. */
    private void adrp(String reg, String name) {
        sb.append("    adrp ").append(reg).append(", ").append(name).append("\n");
        sb.append("    add ").append(reg).append(", ").append(reg)
                .append(", :lo12:").append(name).append("\n");
    }

    @Override
    protected void emitPushAcc() {
        sb.append("    str x0, [sp, #-16]!\n");
    }

    @Override
    protected void emitMoveAccToRight() {
        sb.append("    mov x1, x0\n"); // right in x1
    }

    @Override
    protected void emitPopLeftToAcc() {
        sb.append("    ldr x0, [sp], #16\n"); // left in x0
    }

    @Override
    protected void emitPackEightbyte(Storage base, int idx, int fields) {
        fld32w("x0", base, 8 * idx);
        if (2 * idx + 1 < fields) { fld32w("x3", base, 8 * idx + 4); sb.append("    lsl x3, x3, #32\n"); sb.append("    orr x0, x0, x3\n"); }
    }

    private void fld32w(String reg, Storage s, int bo) {
        if (s.local()) sb.append("    ldr ").append(reg).append(", [x29, #").append(-(offset(s.slot()) - bo)).append("]\n");
        else { adrp("x9", s.name()); sb.append("    ldr ").append(reg).append(", [x9, #").append(bo).append("]\n"); }
    }

    @Override
    protected void emitStoreSecondReturn(Storage storage) {
        if (storage.local()) sb.append("    str x1, [x29, #-").append(offset(storage.slot())).append("]\n");
        else { adrp("x9", storage.name()); sb.append("    str x1, [x9]\n"); }
    }

    @Override
    protected void emitLoadSecondReturn(Storage storage) {
        if (storage.local()) sb.append("    ldr x1, [x29, #-").append(offset(storage.slot())).append("]\n");
        else { adrp("x9", storage.name()); sb.append("    ldr x1, [x9]\n"); }
    }

    @Override
    protected void emitPopArg(int argIndex, int eightbytes) {
        // último eightbyte pushado = topo da pilha → registra-se do maior para o menor
        for (int j = eightbytes - 1; j >= 0; j--) sb.append("    ldr ").append(ARG_REGS[argIndex + j]).append(", [sp], #16\n");
    }

    @Override
    protected void emitBinaryOp(String op) {
        // x0 = left, x1 = right -> x0 = left op right
        switch (op) {
            case "+" -> sb.append("    add x0, x0, x1\n");
            case "-" -> sb.append("    sub x0, x0, x1\n");
            case "&" -> sb.append("    and x0, x0, x1\n");
            case "|" -> sb.append("    orr x0, x0, x1\n");
            case "^" -> sb.append("    eor x0, x0, x1\n");
            case "<<" -> sb.append("    lsl x0, x0, x1\n");
            case ">>" -> sb.append("    asr x0, x0, x1\n");
            case "==" -> cset("eq");
            case "!=" -> cset("ne");
            case "<" -> cset("lt");
            case ">" -> cset("gt");
            case "<=" -> cset("le");
            case ">=" -> cset("ge");
            default -> sb.append("    // unknown op ").append(op).append("\n");
        }
    }

    private void cset(String cond) {
        sb.append("    cmp x0, x1\n");
        sb.append("    cset x0, ").append(cond).append("\n");
    }

    @Override
    protected void emitBranchIfZero(String label) {
        sb.append("    cbz x0, ").append(label).append("\n");
    }

    @Override
    protected void emitJump(String label) {
        sb.append("    b ").append(label).append("\n");
    }

    @Override
    protected void emitCall(String name) {
        sb.append("    bl ").append(name).append("\n");
    }
}
