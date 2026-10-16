package dev.kof.compiler.nat;
import dev.kof.compiler.KofBinary;
import dev.kof.compiler.KofBinaryOp;

/**
 * FASE E / REFACTOR-500: emissão x86_64 da aritmética em PONTO FLUTUANTE (SSE)
 * — float e double. Extraído de {@link NativeX86Arith} para manter a classe sob
 * o gate de 500 linhas. O backend entrega os dois operandos na pilha (b no topo,
 * a abaixo); estes métodos consomem os dois e empilham o resultado.
 */
final class NativeX86SseArith {

    private NativeX86SseArith() {}

    /** Aritmética binária de `float` (SSE single-precision). */
    static void emitFloat(StringBuilder sb, KofBinary kb) {
            sb.append("    popq %rcx\n");
            sb.append("    popq %rax\n");
            sb.append("    movd %eax, %xmm0\n");
            sb.append("    movd %ecx, %xmm1\n");
            switch (kb.op()) {
                case ADD -> sb.append("    addss %xmm1, %xmm0\n");
                case SUB -> sb.append("    subss %xmm1, %xmm0\n");
                case MUL -> sb.append("    mulss %xmm1, %xmm0\n");
                case DIV -> sb.append("    divss %xmm1, %xmm0\n");
                case EQ -> {
                    sb.append("    ucomiss %xmm1, %xmm0\n");
                    sb.append("    sete %al\n");
                    sb.append("    setnp %dl\n");
                    sb.append("    andb %dl, %al\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    return;
                }
                case NE -> {
                    sb.append("    ucomiss %xmm1, %xmm0\n");
                    sb.append("    setne %al\n");
                    sb.append("    setp %dl\n");
                    sb.append("    orb %dl, %al\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    return;
                }
                case LT -> {
                    // §101 (IEEE): unordered (NaN) força false — mesmo guard do LE.
                    sb.append("    ucomiss %xmm1, %xmm0\n");
                    sb.append("    setb %al\n");
                    sb.append("    setp %dl\n");
                    sb.append("    testb %dl, %dl\n");
                    sb.append("    jnz 1f\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    sb.append("    jmp 2f\n");
                    sb.append("1: xorl %eax, %eax\n");
                    sb.append("    pushq %rax\n");
                    sb.append("2:\n");
                    return;
                }
                case LE -> {
                    sb.append("    ucomiss %xmm1, %xmm0\n");
                    sb.append("    setbe %al\n");
                    sb.append("    setp %dl\n");
                    // NaN => unordered => CF=1 PF=1 => be would be true, clear it
                    sb.append("    testb %dl, %dl\n");
                    sb.append("    jnz 1f\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    sb.append("    jmp 2f\n");
                    sb.append("1: xorl %eax, %eax\n");
                    sb.append("    pushq %rax\n");
                    sb.append("2:\n");
                    return;
                }
                case GT -> {
                    sb.append("    ucomiss %xmm1, %xmm0\n");
                    sb.append("    seta %al\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    return;
                }
                case GE -> {
                    sb.append("    ucomiss %xmm1, %xmm0\n");
                    sb.append("    setae %al\n");
                    sb.append("    setp %dl\n");
                    sb.append("    testb %dl, %dl\n");
                    sb.append("    jnz 1f\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    sb.append("    jmp 2f\n");
                    sb.append("1: xorl %eax, %eax\n");
                    sb.append("    pushq %rax\n");
                    sb.append("2:\n");
                    return;
                }
                default -> { sb.append("    movd %xmm0, %eax\n"); sb.append("    pushq %rax\n"); return; }
            }
            sb.append("    movd %xmm0, %eax\n");
            sb.append("    movl %eax, %eax\n");
            sb.append("    pushq %rax\n");
            return;
    }

    /** Aritmética binária de `double` (SSE double-precision). */
    static void emitDouble(StringBuilder sb, KofBinary kb) {
            sb.append("    popq %rcx\n");
            sb.append("    popq %rax\n");
            // §146 (12/09, #101): MOD Double caía no `default` abaixo e
            // devolvia o dividendo. fmod via kof_double_mod (SSE2, sem libm):
            // topo=b, abaixo=a — a pilha x86 entrega b em %rcx e a em %rax,
            // a convenção do helper é rdi=a, rsi=b.
            if (kb.op() == KofBinaryOp.MOD) {
                sb.append("    movq %rax, %rdi\n");
                sb.append("    movq %rcx, %rsi\n");
                sb.append("    call kof_double_mod\n");
                sb.append("    pushq %rax\n");
                return;
            }
            sb.append("    movq %rax, %xmm0\n");
            sb.append("    movq %rcx, %xmm1\n");
            sb.append("    movq %xmm0, %xmm0\n");
            switch (kb.op()) {
                case ADD -> sb.append("    addsd %xmm1, %xmm0\n");
                case SUB -> sb.append("    subsd %xmm1, %xmm0\n");
                case MUL -> sb.append("    mulsd %xmm1, %xmm0\n");
                case DIV -> sb.append("    divsd %xmm1, %xmm0\n");
                case EQ -> {
                    sb.append("    ucomisd %xmm1, %xmm0\n");
                    sb.append("    sete %al\n");
                    sb.append("    setnp %dl\n");
                    sb.append("    andb %dl, %al\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    return;
                }
                case NE -> {
                    sb.append("    ucomisd %xmm1, %xmm0\n");
                    sb.append("    setne %al\n");
                    sb.append("    setp %dl\n");
                    sb.append("    orb %dl, %al\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    return;
                }
                case LT -> {
                    // §101 (IEEE): unordered (NaN) força false. `setb` sozinho
                    // dava CF=1 no NaN (PF=1) → true — mesmo guard do LE.
                    sb.append("    ucomisd %xmm1, %xmm0\n");
                    sb.append("    setb %al\n");
                    sb.append("    setp %dl\n");
                    sb.append("    testb %dl, %dl\n");
                    sb.append("    jnz 1f\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    sb.append("    jmp 2f\n");
                    sb.append("1: xorl %eax, %eax\n");
                    sb.append("    pushq %rax\n");
                    sb.append("2:\n");
                    return;
                }
                case LE -> {
                    sb.append("    ucomisd %xmm1, %xmm0\n");
                    sb.append("    setbe %al\n");
                    sb.append("    setp %dl\n");
                    sb.append("    testb %dl, %dl\n");
                    sb.append("    jnz 1f\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    sb.append("    jmp 2f\n");
                    sb.append("1: xorl %eax, %eax\n");
                    sb.append("    pushq %rax\n");
                    sb.append("2:\n");
                    return;
                }
                case GT -> {
                    sb.append("    ucomisd %xmm1, %xmm0\n");
                    sb.append("    seta %al\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    return;
                }
                case GE -> {
                    sb.append("    ucomisd %xmm1, %xmm0\n");
                    sb.append("    setae %al\n");
                    sb.append("    setp %dl\n");
                    sb.append("    testb %dl, %dl\n");
                    sb.append("    jnz 1f\n");
                    sb.append("    movzbl %al, %eax\n");
                    sb.append("    pushq %rax\n");
                    sb.append("    jmp 2f\n");
                    sb.append("1: xorl %eax, %eax\n");
                    sb.append("    pushq %rax\n");
                    sb.append("2:\n");
                    return;
                }
                default -> { sb.append("    movq %xmm0, %rax\n"); sb.append("    pushq %rax\n"); return; }
            }
            sb.append("    movq %xmm0, %rax\n");
            sb.append("    pushq %rax\n");
            return;
    }
}
