package dev.kof.c;

import java.util.List;

/**
 * Emissor riscv64 (GAS, ABI LP64) do subconjunto C — freestanding: só syscalls
 * cruas ({@code exit}=93, {@code write}=64), montado por
 * {@code riscv64-linux-gnu-as} + {@code ld} e rodado sob {@code qemu-riscv64}.
 *
 * <p>Acumulador = {@code a0} (também o retorno da ABI); a pilha (16 bytes por
 * temporário, alinhada) guarda o lado esquerdo das binárias; {@code a1} é o
 * operando direito. Frame: {@code s0} é a base, com {@code ra}/{@code s0}
 * salvos em {@code 8(s0)}/{@code 0(s0)}; parâmetros e locais em
 * {@code -(8*(slot+1))(s0)}. Argumentos em {@code a0..a7}.
 */
final class KofCEmitterRiscv extends KofCEmitterBase {

    private static final String[] ARG_REGS = {"a0", "a1", "a2", "a3", "a4", "a5"};

    KofCEmitterRiscv(KofCAst.Program prog, boolean executable) { super(prog, executable); }

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
        // inicializa gp: o linker do riscv relaxa `la` de globais próximos para
        // gp-relativo (addi t0, gp, off); sem gp apontando p/ __global_pointer$
        // o acesso cai em endereço inválido (segfault). O `.option norelax`
        // impede que a própria carga de gp seja relaxada.
        sb.append("    .option push\n");
        sb.append("    .option norelax\n");
        sb.append("    la gp, __global_pointer$\n");
        sb.append("    .option pop\n");
        sb.append("    call main\n");
        sb.append("    li a7, 93\n"); // exit
        sb.append("    li a0, 0\n");
        sb.append("    ecall\n");
    }

    @Override
    protected void emitPrintHelpers() {
        sb.append("kof_print_int:\n");
        sb.append("    addi sp, sp, -48\n");
        sb.append("    addi t0, sp, 48\n");
        sb.append("    li t1, 10\n");
        sb.append("    sb t1, -1(t0)\n");
        sb.append("    addi t2, t0, -1\n");
        sb.append("    li t3, 1\n");
        sb.append("    bnez a0, .Lkof_print_loop\n");
        sb.append("    li t1, 48\n");
        sb.append("    addi t2, t2, -1\n");
        sb.append("    sb t1, 0(t2)\n");
        sb.append("    addi t3, t3, 1\n");
        sb.append("    j .Lkof_print_write\n");
        sb.append(".Lkof_print_loop:\n");
        sb.append("    beqz a0, .Lkof_print_write\n");
        sb.append("    li t4, 10\n");
        sb.append("    remu t5, a0, t4\n");
        sb.append("    addi t5, t5, 48\n");
        sb.append("    addi t2, t2, -1\n");
        sb.append("    sb t5, 0(t2)\n");
        sb.append("    addi t3, t3, 1\n");
        sb.append("    divu a0, a0, t4\n");
        sb.append("    j .Lkof_print_loop\n");
        sb.append(".Lkof_print_write:\n");
        sb.append("    mv a1, t2\n");
        sb.append("    mv a2, t3\n");
        sb.append("    li a0, 1\n");
        sb.append("    li a7, 64\n"); // write
        sb.append("    ecall\n");
        sb.append("    addi sp, sp, 48\n");
        sb.append("    ret\n");
        sb.append("kof_print:\n");
        sb.append("    la t0, print_arg\n");
        sb.append("    ld a0, 0(t0)\n");
        sb.append("    j kof_print_int\n");
        if (!hasGlobal("print_arg")) {
            sb.append("    .bss\n");
            sb.append("    .globl print_arg\n");
            sb.append("    .comm print_arg,8,8\n");
            sb.append("    .text\n");
        }
    }

    @Override
    protected void emitFuncPrologue(int frameSlots) {
        sb.append("    addi sp, sp, -16\n");
        sb.append("    sd ra, 8(sp)\n");
        sb.append("    sd s0, 0(sp)\n");
        sb.append("    mv s0, sp\n");
        if (frameSlots > 0) sb.append("    addi sp, sp, -").append(frameSlots * 8).append("\n");
    }

    @Override
    protected void emitFuncEpilogue() {
        sb.append("    mv sp, s0\n");
        sb.append("    ld ra, 8(sp)\n");
        sb.append("    ld s0, 0(sp)\n");
        sb.append("    addi sp, sp, 16\n");
        sb.append("    ret\n");
    }

    @Override
    protected void emitStoreParam(int argIndex, int slot, int eightbytes) {
        for (int j = 0; j < eightbytes; j++)
            sb.append("    sd ").append(ARG_REGS[argIndex + j]).append(", -").append(offset(slot - j)).append("(s0)\n");
    }

    private int gsize(String type) { int b = structBytes(type); return b == 0 ? 8 : b; }

    private static int offset(int slot) { return 8 * (slot + 1); }

    @Override
    protected void emitLoadImm(int v) {
        sb.append("    li a0, ").append(v).append("\n");
    }

    @Override
    protected void emitLoadStorage(Storage storage) {
        if (storage.local()) {
            sb.append("    ld a0, -").append(offset(storage.slot())).append("(s0)\n");
        } else {
            sb.append("    la t0, ").append(storage.name()).append("\n");
            sb.append("    ld a0, 0(t0)\n");
        }
    }

    @Override
    protected void emitStoreStorage(Storage storage) {
        if (storage.local()) {
            sb.append("    sd a0, -").append(offset(storage.slot())).append("(s0)\n");
        } else {
            sb.append("    la t0, ").append(storage.name()).append("\n");
            sb.append("    sd a0, 0(t0)\n");
        }
    }

    @Override
    protected void emitLoadField(Storage storage, int byteOffset) {
        if (storage.local()) {
            sb.append("    lw a0, -").append(fieldDisp(storage.slot(), byteOffset)).append("(s0)\n");
        } else {
            sb.append("    la t0, ").append(storage.name()).append("\n");
            sb.append("    lw a0, ").append(byteOffset).append("(t0)\n");
        }
    }

    @Override
    protected void emitStoreField(Storage storage, int byteOffset) {
        if (storage.local()) {
            sb.append("    sw a0, -").append(fieldDisp(storage.slot(), byteOffset)).append("(s0)\n");
        } else {
            sb.append("    la t0, ").append(storage.name()).append("\n");
            sb.append("    sw a0, ").append(byteOffset).append("(t0)\n");
        }
    }

    /** Frame displacement of a field: base slot minus the field byte offset. */
    private static int fieldDisp(int slot, int byteOffset) { return offset(slot) - byteOffset; }

    @Override
    protected void emitAddrOfStorage(Storage storage) {
        if (storage.local()) {
            sb.append("    addi a0, s0, -").append(offset(storage.slot())).append("\n");
        } else {
            sb.append("    la a0, ").append(storage.name()).append("\n");
        }
    }

    @Override
    protected void emitLoadThroughStorage(Storage storage) {
        emitPtr(storage, "t0");
        sb.append("    ld a0, 0(t0)\n");
    }

    @Override
    protected void emitDerefStoreStorage(Storage storage) {
        emitPtr(storage, "t0");
        sb.append("    sd a0, 0(t0)\n");
    }

    /** Carrega o valor da variável (ponteiro) em {@code reg}, preservando {@code a0}. */
    private void emitPtr(Storage storage, String reg) {
        if (storage.local()) {
            sb.append("    ld ").append(reg).append(", -").append(offset(storage.slot())).append("(s0)\n");
        } else {
            sb.append("    la ").append(reg).append(", ").append(storage.name()).append("\n");
            sb.append("    ld ").append(reg).append(", 0(").append(reg).append(")\n");
        }
    }

    @Override
    protected void emitPushAcc() {
        sb.append("    addi sp, sp, -16\n");
        sb.append("    sd a0, 0(sp)\n");
    }

    @Override
    protected void emitMoveAccToRight() {
        sb.append("    mv a1, a0\n"); // right in a1
    }

    @Override
    protected void emitPopLeftToAcc() {
        sb.append("    ld a0, 0(sp)\n"); // left in a0
        sb.append("    addi sp, sp, 16\n");
    }

    @Override
    protected void emitPackEightbyte(Storage base, int idx, int fields) {
        fld32("a0", base, 8 * idx);
        if (2 * idx + 1 < fields) { fld32("a3", base, 8 * idx + 4); sb.append("    slli a3, a3, 32\n"); sb.append("    or a0, a0, a3\n"); }
    }

    private void fld32(String reg, Storage s, int bo) {
        if (s.local()) sb.append("    lw ").append(reg).append(", -").append(offset(s.slot()) - bo).append("(s0)\n");
        else { sb.append("    la t0, ").append(s.name()).append("\n"); sb.append("    lw ").append(reg).append(", ").append(bo).append("(t0)\n"); }
    }

    @Override
    protected void emitStoreSecondReturn(Storage storage) {
        if (storage.local()) sb.append("    sd a1, -").append(offset(storage.slot())).append("(s0)\n");
        else { sb.append("    la t0, ").append(storage.name()).append("\n"); sb.append("    sd a1, 0(t0)\n"); }
    }

    @Override
    protected void emitLoadSecondReturn(Storage storage) {
        if (storage.local()) sb.append("    ld a1, -").append(offset(storage.slot())).append("(s0)\n");
        else { sb.append("    la t0, ").append(storage.name()).append("\n"); sb.append("    ld a1, 0(t0)\n"); }
    }

    @Override
    protected void emitPopArg(int argIndex, int eightbytes) {
        // último eightbyte pushado = topo da pilha → registra-se do maior para o menor
        for (int j = eightbytes - 1; j >= 0; j--) {
            sb.append("    ld ").append(ARG_REGS[argIndex + j]).append(", 0(sp)\n");
            sb.append("    addi sp, sp, 16\n");
        }
    }

    @Override
    protected void emitBinaryOp(String op) {
        // a0 = left, a1 = right -> a0 = left op right
        switch (op) {
            case "+" -> sb.append("    add a0, a0, a1\n");
            case "-" -> sb.append("    sub a0, a0, a1\n");
            case "&" -> sb.append("    and a0, a0, a1\n");
            case "|" -> sb.append("    or a0, a0, a1\n");
            case "^" -> sb.append("    xor a0, a0, a1\n");
            case "<<" -> sb.append("    sll a0, a0, a1\n");
            case ">>" -> sb.append("    sra a0, a0, a1\n");
            case "==" -> { sb.append("    xor a0, a0, a1\n"); sb.append("    seqz a0, a0\n"); }
            case "!=" -> { sb.append("    xor a0, a0, a1\n"); sb.append("    snez a0, a0\n"); }
            case "<" -> sb.append("    slt a0, a0, a1\n");
            case ">" -> sb.append("    slt a0, a1, a0\n");
            case "<=" -> { sb.append("    slt a0, a1, a0\n"); sb.append("    xori a0, a0, 1\n"); }
            case ">=" -> { sb.append("    slt a0, a0, a1\n"); sb.append("    xori a0, a0, 1\n"); }
            default -> sb.append("    # unknown op ").append(op).append("\n");
        }
    }

    @Override
    protected void emitBranchIfZero(String label) {
        sb.append("    beqz a0, ").append(label).append("\n");
    }

    @Override
    protected void emitJump(String label) {
        sb.append("    j ").append(label).append("\n");
    }

    @Override
    protected void emitCall(String name) {
        sb.append("    call ").append(name).append("\n");
    }
}
