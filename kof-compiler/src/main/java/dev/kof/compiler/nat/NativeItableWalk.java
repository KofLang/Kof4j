package dev.kof.compiler.nat;

/**
 * §719 (19/11): emissão do walk de ITABLES nos dois backends nativos.
 * A seção é anexada por {@link NativeClassMeta#emitInterfaceTables} logo após
 * o terminador da vtable de cada classe: pares {@code (ifaceId, tabela)}
 * fechados por {@code (0,0)}, seguidos das tabelas por interface. O call site
 * de INTERFACE percorre a vtable até o terminador, acha a tabela do seu id e
 * lê o slot. Os dois walks usam só registradores scratch (x86: {@code %r11}/
 * {@code %rax}; riscv: {@code t0/t1/t2}) — os registradores de argumento já
 * carregados ficam intactos.
 */
final class NativeItableWalk {

    private NativeItableWalk() {}

    /** x86-64: receiver em {@code %rdi}; deixa a função do slot em {@code %r11}. */
    static void emitX86(StringBuilder sb, NativeBackend nb, int itableId, int slot) {
        int walk = nb.itableWalkSeq++;
        sb.append("    movq 8(%rdi), %r11\n");
        sb.append(".Liw").append(walk).append(":\n");
        sb.append("    movq (%r11), %rax\n");
        sb.append("    testq %rax, %rax\n");
        sb.append("    jz .Liwe").append(walk).append("\n");
        sb.append("    addq $8, %r11\n");
        sb.append("    jmp .Liw").append(walk).append("\n");
        sb.append(".Liwe").append(walk).append(":\n");
        sb.append("    addq $8, %r11\n");
        sb.append(".Liwp").append(walk).append(":\n");
        sb.append("    movq (%r11), %rax\n");
        sb.append("    testq %rax, %rax\n");
        sb.append("    jz .Liwf").append(walk).append("\n");
        sb.append("    cmpq $").append(itableId).append(", %rax\n");
        sb.append("    je .Liwh").append(walk).append("\n");
        sb.append("    addq $16, %r11\n");
        sb.append("    jmp .Liwp").append(walk).append("\n");
        sb.append(".Liwh").append(walk).append(":\n");
        sb.append("    movq 8(%r11), %r11\n");
        sb.append("    addq $").append(slot * 8).append(", %r11\n");
        sb.append("    movq (%r11), %r11\n");
        sb.append("    jmp .Liwc").append(walk).append("\n");
        sb.append(".Liwf").append(walk).append(":\n");
        sb.append("    xorl %r11d, %r11d\n");
        sb.append(".Liwc").append(walk).append(":\n");
    }

    /** riscv64/aarch64: receiver em {@code a0}; deixa a função do slot em {@code t0}. */
    static void emitRiscv(StringBuilder sb, NativeBackend nb, int itableId, int slot) {
        int walk = nb.itableWalkSeq++;
        sb.append("    ld t0, 8(a0)\n");
        sb.append(".Liw").append(walk).append(":\n");
        sb.append("    ld t1, 0(t0)\n");
        sb.append("    beqz t1, .Liwe").append(walk).append("\n");
        sb.append("    addi t0, t0, 8\n");
        sb.append("    j .Liw").append(walk).append("\n");
        sb.append(".Liwe").append(walk).append(":\n");
        sb.append("    addi t0, t0, 8\n");
        sb.append(".Liwp").append(walk).append(":\n");
        sb.append("    ld t1, 0(t0)\n");
        sb.append("    beqz t1, .Liwf").append(walk).append("\n");
        sb.append("    li t2, ").append(itableId).append("\n");
        sb.append("    beq t1, t2, .Liwh").append(walk).append("\n");
        sb.append("    addi t0, t0, 16\n");
        sb.append("    j .Liwp").append(walk).append("\n");
        sb.append(".Liwh").append(walk).append(":\n");
        sb.append("    ld t0, 8(t0)\n");
        sb.append("    addi t0, t0, ").append(slot * 8).append("\n");
        sb.append("    ld t0, 0(t0)\n");
        sb.append("    j .Liwc").append(walk).append("\n");
        sb.append(".Liwf").append(walk).append(":\n");
        sb.append("    li t0, 0\n");
        sb.append(".Liwc").append(walk).append(":\n");
    }
}
