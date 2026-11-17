package dev.kof.c;

import java.util.List;

/**
 * Emissor x86_64 (AT&T/gas com {@code .intel_syntax noprefix}) do subconjunto
 * C — alvo histórico, freestanding ({@code as --64} + {@code ld}). Preserva a
 * estrutura original: globais de 8 bytes, acumulador {@code rax}, temporários
 * na pilha e {@code _start} que chama {@code main} e sai por syscall.
 *
 * <p>Frame: {@code rbp} é a base; o par salvo fica em {@code [rbp]} e o retorno
 * em {@code [rbp+8]}; parâmetros e locais vivem em {@code [rbp-8*(slot+1)]}.
 * Argumentos seguem a ABI SysV ({@code rdi,rsi,rdx,rcx,r8,r9}); o retorno sai
 * em {@code rax}, que já é o acumulador.
 */
final class KofCEmitterX86 extends KofCEmitterBase {

    private static final String[] ARG_REGS = {"rdi", "rsi", "rdx", "rcx", "r8", "r9"};

    KofCEmitterX86(KofCAst.Program prog, boolean executable) { super(prog, executable); }

    @Override
    protected void emitDataSection(List<KofCAst.VarDecl> globals) {
        sb.append("    .intel_syntax noprefix\n");
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
        sb.append("    call main\n");
        sb.append("    mov rax, 60\n"); // exit
        sb.append("    xor rdi, rdi\n");
        sb.append("    syscall\n");
    }

    @Override
    protected void emitPrintHelpers() {
        sb.append("kof_print_int:\n");
        sb.append("    push rbp\n");
        sb.append("    mov rbp, rsp\n");
        sb.append("    sub rsp, 32\n");
        sb.append("    mov rax, rdi\n");
        sb.append("    lea rsi, [rbp-32]\n");
        sb.append("    add rsi, 31\n");
        sb.append("    mov byte ptr [rsi], 10\n");
        sb.append("    mov rcx, 1\n");
        sb.append("    cmp rax, 0\n");
        sb.append("    jne .Lprint_loop\n");
        sb.append("    dec rsi\n");
        sb.append("    mov byte ptr [rsi], 48\n");
        sb.append("    inc rcx\n");
        sb.append("    jmp .Lprint_write\n");
        sb.append(".Lprint_loop:\n");
        sb.append("    test rax, rax\n");
        sb.append("    je .Lprint_write\n");
        sb.append("    xor rdx, rdx\n");
        sb.append("    mov rbx, 10\n");
        sb.append("    div rbx\n");
        sb.append("    add dl, 48\n");
        sb.append("    dec rsi\n");
        sb.append("    mov byte ptr [rsi], dl\n");
        sb.append("    inc rcx\n");
        sb.append("    jmp .Lprint_loop\n");
        sb.append(".Lprint_write:\n");
        sb.append("    mov rdx, rcx\n");
        sb.append("    mov rax, 1\n");
        sb.append("    mov rdi, 1\n");
        sb.append("    syscall\n");
        sb.append("    leave\n");
        sb.append("    ret\n");
        sb.append("kof_print:\n");
        sb.append("    mov rdi, qword ptr [rip + print_arg]\n");
        sb.append("    jmp kof_print_int\n");
        if (!hasGlobal("print_arg")) {
            sb.append("    .bss\n");
            sb.append("    .globl print_arg\n");
            sb.append("    .comm print_arg,8,8\n");
            sb.append("    .text\n");
        }
    }

    @Override
    protected void emitFuncPrologue(int frameSlots) {
        sb.append("    push rbp\n");
        sb.append("    mov rbp, rsp\n");
        if (frameSlots > 0) sb.append("    sub rsp, ").append(frameSlots * 8).append("\n");
    }

    @Override
    protected void emitFuncEpilogue() {
        sb.append("    leave\n");
        sb.append("    ret\n");
    }

    @Override
    protected void emitStoreParam(int argIndex, int slot, int eightbytes) {
        for (int j = 0; j < eightbytes; j++)
            sb.append("    mov qword ptr [rbp - ").append(offset(slot - j)).append("], ")
                    .append(ARG_REGS[argIndex + j]).append("\n");
    }

    private int gsize(String type) { int b = structBytes(type); return b == 0 ? 8 : b; }

    private static int offset(int slot) { return 8 * (slot + 1); }

    @Override
    protected void emitLoadImm(int v) {
        sb.append("    mov rax, ").append(v).append("\n");
    }

    @Override
    protected void emitLoadStorage(Storage storage) {
        if (storage.local()) {
            sb.append("    mov rax, qword ptr [rbp - ").append(offset(storage.slot())).append("]\n");
        } else {
            sb.append("    mov rax, qword ptr [rip + ").append(storage.name()).append("]\n");
        }
    }

    @Override
    protected void emitStoreStorage(Storage storage) {
        if (storage.local()) {
            sb.append("    mov qword ptr [rbp - ").append(offset(storage.slot())).append("], rax\n");
        } else {
            sb.append("    mov qword ptr [rip + ").append(storage.name()).append("], rax\n");
        }
    }

    @Override
    protected void emitLoadField(Storage storage, int byteOffset) {
        if (storage.local()) {
            sb.append("    movsxd rax, dword ptr [rbp - ").append(fieldDisp(storage.slot(), byteOffset)).append("]\n");
        } else {
            sb.append("    movsxd rax, dword ptr [rip + ").append(storage.name()).append(globalDisp(byteOffset)).append("]\n");
        }
    }

    @Override
    protected void emitStoreField(Storage storage, int byteOffset) {
        if (storage.local()) {
            sb.append("    mov dword ptr [rbp - ").append(fieldDisp(storage.slot(), byteOffset)).append("], eax\n");
        } else {
            sb.append("    mov dword ptr [rip + ").append(storage.name()).append(globalDisp(byteOffset)).append("], eax\n");
        }
    }

    /** Frame displacement of a field: base slot minus the field byte offset. */
    private static int fieldDisp(int slot, int byteOffset) { return offset(slot) - byteOffset; }

    private static String globalDisp(int byteOffset) { return byteOffset == 0 ? "" : " + " + byteOffset; }

    @Override
    protected void emitAddrOfStorage(Storage storage) {
        if (storage.local()) {
            sb.append("    lea rax, [rbp - ").append(offset(storage.slot())).append("]\n");
        } else {
            sb.append("    lea rax, [rip + ").append(storage.name()).append("]\n");
        }
    }

    @Override
    protected void emitLoadThroughStorage(Storage storage) {
        emitLoadStorage(storage);
        sb.append("    mov rax, qword ptr [rax]\n");
    }

    @Override
    protected void emitDerefStoreStorage(Storage storage) {
        sb.append("    mov rcx, rax\n");
        emitLoadStorage(storage);
        sb.append("    mov qword ptr [rax], rcx\n");
    }

    @Override
    protected void emitPushAcc() {
        sb.append("    push rax\n");
    }

    @Override
    protected void emitMoveAccToRight() {
        sb.append("    mov rcx, rax\n"); // right in rcx
    }

    @Override
    protected void emitPopLeftToAcc() {
        sb.append("    pop rax\n"); // left in rax
    }

    @Override
    protected void emitPackEightbyte(Storage base, int idx, int fields) {
        fld32("eax", base, 8 * idx);
        if (2 * idx + 1 < fields) { fld32("ecx", base, 8 * idx + 4); sb.append("    shl rcx, 32\n"); sb.append("    or rax, rcx\n"); }
    }

    private void fld32(String reg, Storage s, int bo) {
        if (s.local()) sb.append("    mov ").append(reg).append(", dword ptr [rbp - ").append(offset(s.slot()) - bo).append("]\n");
        else sb.append("    mov ").append(reg).append(", dword ptr [rip + ").append(s.name()).append(bo == 0 ? "" : " + " + bo).append("]\n");
    }

    @Override
    protected void emitStoreSecondReturn(Storage storage) {
        if (storage.local()) sb.append("    mov qword ptr [rbp - ").append(offset(storage.slot())).append("], rdx\n");
        else sb.append("    mov qword ptr [rip + ").append(storage.name()).append("], rdx\n");
    }

    @Override
    protected void emitLoadSecondReturn(Storage storage) {
        if (storage.local()) sb.append("    mov rdx, qword ptr [rbp - ").append(offset(storage.slot())).append("]\n");
        else sb.append("    mov rdx, qword ptr [rip + ").append(storage.name()).append("]\n");
    }

    @Override
    protected void emitPopArg(int argIndex, int eightbytes) {
        // último eightbyte pushado = topo da pilha → registra-se do maior para o menor
        for (int j = eightbytes - 1; j >= 0; j--) sb.append("    pop ").append(ARG_REGS[argIndex + j]).append("\n");
    }

    @Override
    protected void emitBinaryOp(String op) {
        // rax = left, rcx = right -> rax = left op right
        switch (op) {
            case "+" -> sb.append("    add rax, rcx\n");
            case "-" -> sb.append("    sub rax, rcx\n");
            case "&" -> sb.append("    and rax, rcx\n");
            case "|" -> sb.append("    or rax, rcx\n");
            case "^" -> sb.append("    xor rax, rcx\n");
            case "<<" -> sb.append("    shl rax, cl\n");
            case ">>" -> sb.append("    sar rax, cl\n");
            case "==" -> { sb.append("    cmp rax, rcx\n"); sb.append("    sete al\n"); sb.append("    movzx rax, al\n"); }
            case "!=" -> { sb.append("    cmp rax, rcx\n"); sb.append("    setne al\n"); sb.append("    movzx rax, al\n"); }
            case "<" -> { sb.append("    cmp rax, rcx\n"); sb.append("    setl al\n"); sb.append("    movzx rax, al\n"); }
            case ">" -> { sb.append("    cmp rax, rcx\n"); sb.append("    setg al\n"); sb.append("    movzx rax, al\n"); }
            case "<=" -> { sb.append("    cmp rax, rcx\n"); sb.append("    setle al\n"); sb.append("    movzx rax, al\n"); }
            case ">=" -> { sb.append("    cmp rax, rcx\n"); sb.append("    setge al\n"); sb.append("    movzx rax, al\n"); }
            default -> sb.append("    ; unknown op ").append(op).append("\n");
        }
    }

    @Override
    protected void emitBranchIfZero(String label) {
        sb.append("    cmp rax, 0\n");
        sb.append("    je ").append(label).append("\n");
    }

    @Override
    protected void emitJump(String label) {
        sb.append("    jmp ").append(label).append("\n");
    }

    @Override
    protected void emitCall(String name) {
        sb.append("    call ").append(name).append("\n");
    }
}
