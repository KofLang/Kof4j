package dev.kof.compiler.nat;
import dev.kof.compiler.KofBinary;
import dev.kof.compiler.KofBinaryOp;
import dev.kof.compiler.KofUnary;
import dev.kof.compiler.KofUnaryOp;
import dev.kof.compiler.Type;

/**
 * FASE 3 (REFACTOR-500): emissão x86_64 de aritmética (binária e unária).
 * Extraído verbatim de NativeBackend (emitBinary + emitUnary) — os dois
 * métodos só dependem de NativeTypeKinds (estático), zero estado do backend.
 */
public final class NativeX86Arith {

    private NativeX86Arith() {}

    /**
     * The integer value of the last value-producing line pushed, when it is a
     * constant ({@code pushq $N}) — skipping any trailing {@code .loc} debug lines.
     * Returns {@code null} when the top of the operand stack was produced by
     * something other than a literal push.
     */
    static Integer constantPushedOnTop(StringBuilder sb) {
        int end = sb.length();
        if (end > 0 && sb.charAt(end - 1) == '\n') end--;
        while (end > 0) {
            int start = sb.lastIndexOf("\n", end - 1) + 1;
            if (sb.indexOf("    .loc ", start) == start) {
                end = start - 1;
                continue;
            }
            String line = sb.substring(start, end);
            if (!line.startsWith("    pushq $")) return null;
            try {
                return Integer.parseInt(line.substring("    pushq $".length()).trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /** Removes the last value-producing line without copying the assembly buffer. */
    static void dropLastNonLocLine(StringBuilder sb) {
        int end = sb.length();
        if (end > 0 && sb.charAt(end - 1) == '\n') end--;
        while (end > 0) {
            int start = sb.lastIndexOf("\n", end - 1) + 1;
            if (sb.indexOf("    .loc ", start) == start) {
                end = start - 1;
                continue;
            }
            int deleteEnd = end;
            if (deleteEnd < sb.length() && sb.charAt(deleteEnd) == '\n') {
                deleteEnd++;
            }
            sb.delete(start, deleteEnd);
            return;
        }
    }

    static void emitBinary(StringBuilder sb, KofBinary kb) {
        Type opTy = kb.operandType();
        if (NativeTypeKinds.isFloatType(opTy)) {
            NativeX86SseArith.emitFloat(sb, kb);
            return;
        }
        if (NativeTypeKinds.isDoubleType(opTy)) {
            NativeX86SseArith.emitDouble(sb, kb);
            return;
        }
        // §7 x86_64 ("reduction of instructions for constants"): a CONSTANT shift
        // count is the last value pushed (`pushq $N`); emit the immediate form
        // `shl{l,q} $N, %{e,r}ax` and drop the push/pop instead of round-tripping
        // the count through %rcx. A computed count (the tail is not a literal push)
        // keeps the %cl form. The count is masked (31 for int, 63 for long) to match
        // the JVM/hardware semantics exactly.
        if (kb.op() == KofBinaryOp.SHL || kb.op() == KofBinaryOp.SHR || kb.op() == KofBinaryOp.USHR) {
            Integer imm = constantPushedOnTop(sb);
            if (imm != null) {
                dropLastNonLocLine(sb);
                boolean i32 = NativeTypeKinds.isInt32Type(opTy);
                String mn = switch (kb.op()) {
                    case SHL -> "shl";
                    case SHR -> "sar";
                    default -> "shr";
                };
                sb.append("    popq %rax\n");
                sb.append("    ").append(mn).append(i32 ? "l" : "q")
                        .append(" $").append(imm & (i32 ? 31 : 63))
                        .append(", %").append(i32 ? "e" : "r").append("ax\n");
                sb.append("    pushq %rax\n");
                return;
            }
        }
        // §7 x86_64 ("reduction of instructions for constants"): a CONSTANT right
        // operand uses the immediate form `op{l,q} $N, %{e,r}ax` (or `imul{l,q}
        // $N, %{e,r}ax`) instead of round-tripping it through %rcx. The constant is
        // the LAST value pushed (the RHS); for a non-commutative op (SUB) that is
        // exactly the operand the immediate form expects, so the result is
        // identical. The immediate must fit a sign-extended imm32 (always true for
        // int; a wide long falls back).
        if (kb.op() == KofBinaryOp.ADD || kb.op() == KofBinaryOp.SUB || kb.op() == KofBinaryOp.MUL
                || kb.op() == KofBinaryOp.AND || kb.op() == KofBinaryOp.OR || kb.op() == KofBinaryOp.XOR) {
            Integer imm = constantPushedOnTop(sb);
            if (imm != null) {
                dropLastNonLocLine(sb);
                boolean i32 = NativeTypeKinds.isInt32Type(opTy);
                String mn = switch (kb.op()) {
                    case ADD -> "add";
                    case SUB -> "sub";
                    case MUL -> "imul";
                    case AND -> "and";
                    case OR -> "or";
                    default -> "xor";
                };
                sb.append("    popq %rax\n");
                sb.append("    ").append(mn).append(i32 ? "l" : "q")
                        .append(" $").append(imm)
                        .append(", %").append(i32 ? "e" : "r").append("ax\n");
                sb.append("    pushq %rax\n");
                return;
            }
        }
        // §7 x86_64 ("reduction of instructions for constants"): a CONSTANT right
        // operand of a comparison also uses the immediate form `cmp{l,q} $N,
        // %{e,r}ax` instead of round-tripping it through %rcx. The constant is the
        // RHS (pushed last), so `cmp $N, %eax` compares the same way the register
        // form did (left - right). `x == 0` gets the canonical `testl %eax, %eax`.
        if (kb.op() == KofBinaryOp.EQ || kb.op() == KofBinaryOp.NE || kb.op() == KofBinaryOp.LT
                || kb.op() == KofBinaryOp.LE || kb.op() == KofBinaryOp.GT || kb.op() == KofBinaryOp.GE) {
            Integer imm = constantPushedOnTop(sb);
            if (imm != null) {
                dropLastNonLocLine(sb);
                boolean i32 = NativeTypeKinds.isInt32Type(opTy);
                String setcc = switch (kb.op()) {
                    case EQ -> "sete";
                    case NE -> "setne";
                    case LT -> "setl";
                    case LE -> "setle";
                    case GT -> "setg";
                    default -> "setge";
                };
                sb.append("    popq %rax\n");
                if (imm == 0 && (kb.op() == KofBinaryOp.EQ || kb.op() == KofBinaryOp.NE)) {
                    sb.append("    test").append(i32 ? "l" : "q")
                            .append(" %").append(i32 ? "e" : "r").append("ax, %")
                            .append(i32 ? "e" : "r").append("ax\n");
                } else {
                    sb.append("    cmp").append(i32 ? "l" : "q")
                            .append(" $").append(imm)
                            .append(", %").append(i32 ? "e" : "r").append("ax\n");
                }
                sb.append("    ").append(setcc).append(" %al\n    movzbl %al, %eax\n");
                sb.append("    pushq %rax\n");
                return;
            }
        }
        sb.append("    popq %rcx\n");
        sb.append("    popq %rax\n");
        boolean int32 = NativeTypeKinds.isInt32Type(opTy);
        String suf = int32 ? "l" : "q";
        String a32 = int32 ? "e" : "r";
        switch (kb.op()) {
            case ADD -> sb.append("    add").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n");
            case SUB -> sb.append("    sub").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n");
            case MUL -> sb.append("    imul").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n");
            case DIV -> {
                String divzSfx = divZeroLabel();
                emitDivZeroGuard(sb, int32, divzSfx);
                if (int32) {
                    sb.append("    cdq\n    idivl %ecx\n");
                } else {
                    sb.append("    cqo\n    idivq %rcx\n");
                }
                emitDivZeroGuardEnd(sb, divzSfx);
            }
            case MOD -> {
                String divzSfx = divZeroLabel();
                emitDivZeroGuard(sb, int32, divzSfx);
                if (int32) {
                    sb.append("    cdq\n    idivl %ecx\n    movl %edx, %eax\n");
                } else {
                    sb.append("    cqo\n    idivq %rcx\n    movq %rdx, %rax\n");
                }
                emitDivZeroGuardEnd(sb, divzSfx);
            }
            case EQ -> sb.append("    cmp").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n    sete %al\n    movzbl %al, %eax\n");
            case NE -> sb.append("    cmp").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n    setne %al\n    movzbl %al, %eax\n");
            case LT -> sb.append("    cmp").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n    setl %al\n    movzbl %al, %eax\n");
            case LE -> sb.append("    cmp").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n    setle %al\n    movzbl %al, %eax\n");
            case GT -> sb.append("    cmp").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n    setg %al\n    movzbl %al, %eax\n");
            case GE -> sb.append("    cmp").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n    setge %al\n    movzbl %al, %eax\n");
            case AND -> sb.append("    and").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n");
            case OR -> sb.append("    or").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n");
            case XOR -> sb.append("    xor").append(suf).append(" %").append(a32).append("cx, %").append(a32).append("ax\n");
            case SHL -> sb.append("    shl").append(suf).append(" %cl, %").append(a32).append("ax\n");
            case SHR -> sb.append("    sar").append(suf).append(" %cl, %").append(a32).append("ax\n");
            case USHR -> sb.append("    shr").append(suf).append(" %cl, %").append(a32).append("ax\n");
        }
        sb.append("    pushq %rax\n");
    }

    static void emitUnary(StringBuilder sb, KofUnary ku) {
        if (ku.operandType() != null && NativeTypeKinds.isFloatType(ku.operandType()) && ku.op() == KofUnaryOp.NEG) {
            sb.append("    popq %rax\n");
            sb.append("    movd %eax, %xmm0\n");
            sb.append("    movl $0x80000000, %ecx\n");
            sb.append("    movd %ecx, %xmm1\n");
            sb.append("    xorps %xmm1, %xmm0\n");
            sb.append("    movd %xmm0, %eax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (ku.operandType() != null && NativeTypeKinds.isDoubleType(ku.operandType()) && ku.op() == KofUnaryOp.NEG) {
            sb.append("    popq %rax\n");
            sb.append("    movq %rax, %xmm0\n");
            sb.append("    movabs $0x8000000000000000, %rcx\n");
            sb.append("    movq %rcx, %xmm1\n");
            sb.append("    xorpd %xmm1, %xmm0\n");
            sb.append("    movq %xmm0, %rax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        // primitive conversions
        if (ku.op() == KofUnaryOp.I2F) {
            sb.append("    popq %rax\n");
            sb.append("    cvtsi2ss %eax, %xmm0\n");
            sb.append("    movd %xmm0, %eax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (ku.op() == KofUnaryOp.I2D) {
            sb.append("    popq %rax\n");
            sb.append("    cvtsi2sd %eax, %xmm0\n");
            sb.append("    movq %xmm0, %rax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (ku.op() == KofUnaryOp.L2F) {
            sb.append("    popq %rax\n");
            sb.append("    cvtsi2ss %rax, %xmm0\n");
            sb.append("    movd %xmm0, %eax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (ku.op() == KofUnaryOp.L2D) {
            sb.append("    popq %rax\n");
            sb.append("    cvtsi2sd %rax, %xmm0\n");
            sb.append("    movq %xmm0, %rax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (ku.op() == KofUnaryOp.F2D) {
            sb.append("    popq %rax\n");
            sb.append("    movd %eax, %xmm0\n");
            sb.append("    cvtss2sd %xmm0, %xmm0\n");
            sb.append("    movq %xmm0, %rax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (ku.op() == KofUnaryOp.D2F) {
            sb.append("    popq %rax\n");
            sb.append("    movq %rax, %xmm0\n");
            sb.append("    cvtsd2ss %xmm0, %xmm0\n");
            sb.append("    movd %xmm0, %eax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        // §181 (13/09): D2I/F2I/D2L/F2L SATURANTES (JLS 5.1.3 — NaN => 0,
        // > MAX => MAX, < MIN => MIN). cvttsd2si cru devolvia "integer
        // indefinite" (INT_MIN) p/ NaN e fora de faixa = divergência do
        // JVM (regra 5). Guard: ucomisd (unordered => jp NaN) + clamp.
        if (ku.op() == KofUnaryOp.D2I) {
            emitSatConv(sb, false, true);
            return;
        }
        if (ku.op() == KofUnaryOp.F2I) {
            emitSatConv(sb, true, true);
            return;
        }
        if (ku.op() == KofUnaryOp.D2L) {
            emitSatConv(sb, false, false);
            return;
        }
        if (ku.op() == KofUnaryOp.F2L) {
            emitSatConv(sb, true, false);
            return;
        }
        if (ku.op() == KofUnaryOp.I2L) {
            sb.append("    popq %rax\n");
            sb.append("    movslq %eax, %rax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        sb.append("    popq %rax\n");
        // §695: `as Byte`/`as Short` ESTREITAM — o emissor não tinha ramo para
        // I2B/I2S e os dois caíam no `pushq` final como NO-OP: `200 as Byte`
        // devolvia 200 no nativo enquanto Script/JVM/JS davam -56 (o byte
        // 0xC8 sign-extended). O JVM emite `i2b`/`i2s`; aqui é o equivalente
        // em duas instruções (sign-extend da metade baixa).
        if (ku.op() == KofUnaryOp.I2B) {
            sb.append("    movsbl %al, %eax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        if (ku.op() == KofUnaryOp.I2S) {
            sb.append("    movswl %ax, %eax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        // §697: `as Char` mascara para 16 bits (o JVM emite `i2c`). Este
        // emissor NÃO tinha ramo para I2C — o cast caía no `pushq` final e o
        // valor passava CRU: `(70000 as Char) as Int` dava 70000 aqui e 4464
        // (70000 & 0xFFFF) em Script/JVM. ZERO-extend: o Char é sem sinal.
        if (ku.op() == KofUnaryOp.I2C) {
            sb.append("    movzwl %ax, %eax\n");
            sb.append("    pushq %rax\n");
            return;
        }
        boolean int32u = NativeTypeKinds.isInt32Type(ku.operandType());
        String suf = int32u ? "l" : "q";
        String reg = int32u ? "%eax" : "%rax";
        if (ku.op() == KofUnaryOp.NEG) {
            sb.append("    neg").append(suf).append(" ").append(reg).append("\n");
        } else if (ku.op() == KofUnaryOp.NOT) {
            sb.append("    cmp").append(suf).append(" $0, ").append(reg).append("\n");
            sb.append("    sete %al\n");
            sb.append("    movzbl %al, %eax\n");
        }

        sb.append("    pushq %rax\n");
    }

    /**
     * §181 — conversão float/double -> Int/Long SATURANTE (JLS 5.1.3).
     * Entrada: valor na pilha (bits crus). NaN => 0; fora de faixa =>
     * saturado no limite; senão truncamento (cvttsd2si).
     *
     * <p><b>FIX 13/09 (regressão do commit `c90e85ee`):</b> os limites eram
     * carregados com os BITS INTEIROS (`movq $2147483647, %rdx; movq %rdx,
     * %xmm2`) — interpretados como double, isso é um denormal (~1e-314), então
     * QUALQUER valor positivo caía no ramo ">= MAX" e saturava em `MAX`
     * (`9.9 as Int` → `2147483647`; a célula `cast` pegou — verde falso Q5 do
     * `castrange`, que só testava fora-de-faixa). Agora usa os padrões de bit
     * do DOUBLE: 2^31 = `0x41E0000000000000`, -2^31 = `0xC1E0000000000000`,
     * 2^63 = `0x43E0000000000000`, -2^63 = `0xC3E0000000000000`. Comparar com
     * 2^31 (não 2^31-1) preserva o caso limítrofe `2147483647.0` (in-range).
     * O Float é promovido a Double ANTES (NaN/faixa idênticos; o `movd` cru
     * deixava a checagem de NaN errada).
     * isFloat: entrada é Float (32 bits, movd) senão Double (movq).
     * toLong: resultado 64-bit (rax) senão 32-bit (eax).
     */
    private static java.util.concurrent.atomic.AtomicLong SAT_SEQ =
            new java.util.concurrent.atomic.AtomicLong();

    /** §693: divisor de RUNTIME zero — o `idiv` TRAVA (SIGFPE) e o trap nao e
     *  um throwable do Kof, entao o `try`/`catch` do usuario nao o salvava. A
     *  guarda checa ANTES e desvia para o `kof_throw_string` (o mesmo caminho
     *  que a paginacao e o `throw "msg"` usam), fazendo a falha virar um throw
     *  capturavel — a semantica do JVM/Script, que e o contrato. */
    private static java.util.concurrent.atomic.AtomicLong DIVZ_SEQ =
            new java.util.concurrent.atomic.AtomicLong();

    private static String divZeroLabel() {
        return Long.toString(DIVZ_SEQ.incrementAndGet());
    }

    private static void emitDivZeroGuard(StringBuilder sb, boolean int32, String sfx) {
        sb.append(int32 ? "    testl %ecx, %ecx\n" : "    testq %rcx, %rcx\n");
        sb.append("    jz .Ldivz_").append(sfx).append("\n");
    }

    private static void emitDivZeroGuardEnd(StringBuilder sb, String sfx) {
        sb.append("    jmp .Ldivz_done_").append(sfx).append("\n");
        sb.append(".Ldivz_").append(sfx).append(":\n");
        // §693/§729: a mensagem precisa ser uma KofString (o catch do usuario
        // LE o header — len@16); um `.asciz` cru faz `e` virar lixo e o uso de
        // `e` SIGSEGVA (medido). kof_string_from_literal constroi a KofString.
        sb.append("    leaq .Ldivz_msg_").append(sfx).append("(%rip), %rdi\n");
        sb.append("    movl $9, %esi\n");  // len("/ by zero")
        sb.append("    call kof_string_from_literal\n");
        sb.append("    movq %rax, %rdi\n");
        sb.append("    call kof_throw_string\n");
        sb.append("    ud2\n");
        sb.append(".Ldivz_done_").append(sfx).append(":\n");
        sb.append(".pushsection .rodata\n");
        sb.append(".Ldivz_msg_").append(sfx).append(": .asciz \"/ by zero\"\n");
        sb.append(".popsection\n");
    }

    private static void emitSatConv(StringBuilder sb, boolean isFloat, boolean toInt) {
        String sfx = Long.toString(SAT_SEQ.incrementAndGet());
        sb.append("    popq %rax\n");
        // normaliza p/ double e trunca (indefinite p/ NaN/fora-de-faixa; os
        // ramos abaixo sobrescrevem rax quando o valor não é representável).
        if (isFloat) {
            sb.append("    movd %eax, %xmm0\n");
            sb.append("    cvtss2sd %xmm0, %xmm0\n");
        } else {
            sb.append("    movq %rax, %xmm0\n");
        }
        sb.append("    cvttsd2si %xmm0, %").append(toInt ? "eax" : "rax").append("\n");
        // NaN PRIMEIRO: ucomisd self é unordered sse NaN => PF=1.
        sb.append("    ucomisd %xmm0, %xmm0\n");
        sb.append("    jp .Lsat_nan_").append(sfx).append("\n");
        // xmm0 >= 2^31 (2^63) => satura MAX (CF=0).
        sb.append("    movabs $").append(toInt ? "0x41E0000000000000" : "0x43E0000000000000")
          .append(", %rdx\n");
        sb.append("    movq %rdx, %xmm1\n");
        sb.append("    ucomisd %xmm1, %xmm0\n");
        sb.append("    jnc .Lsat_hi_").append(sfx).append("\n");
        // xmm0 < -2^31 (-2^63) => satura MIN (CF=1).
        sb.append("    movabs $").append(toInt ? "0xC1E0000000000000" : "0xC3E0000000000000")
          .append(", %rdx\n");
        sb.append("    movq %rdx, %xmm1\n");
        sb.append("    ucomisd %xmm1, %xmm0\n");
        sb.append("    jc .Lsat_lo_").append(sfx).append("\n");
        sb.append("    jmp .Lsat_done_").append(sfx).append("\n");
        sb.append(".Lsat_hi_").append(sfx).append(":\n");
        sb.append("    movabs $").append(toInt ? "2147483647" : "9223372036854775807")
          .append(", %rax\n");
        sb.append("    jmp .Lsat_done_").append(sfx).append("\n");
        sb.append(".Lsat_lo_").append(sfx).append(":\n");
        sb.append("    movabs $").append(toInt ? "-2147483648" : "-9223372036854775808")
          .append(", %rax\n");
        sb.append("    jmp .Lsat_done_").append(sfx).append("\n");
        sb.append(".Lsat_nan_").append(sfx).append(":\n");
        sb.append("    xorl %eax, %eax\n");
        sb.append(".Lsat_done_").append(sfx).append(":\n");
        sb.append("    pushq %rax\n");
    }
}
