package dev.kof.cli;

import java.util.List;

/**
 * Satélite do REFACTOR-500 (check_500, BytecodeDecoder 618→<600): a família de
 * CONDIÇÕES de retorno (comparisonReturn/ifElseReturn + condFromSuffix usa os
 * mesmos helpers via BytecodeStatements). Zero mudança de comportamento.
 */
final class BytecodeDecoderCond {

    private BytecodeDecoderCond() {}

    /**
     * Fase E (14/10): a comparação por SINAL (lcmp/dcmpl/dcmpg) COM prólogo:
     * [prologue, op2, op1, cmp, ifXX, iconst_1, goto J, iconst_0, ireturn].
     * O prólogo (as operandas computadas) vira statements via emitLinear e o
     * return é `op2 OP op1` (o espelho 2-op do ifXX). Medido no inCircle:
     * `return d <= r*r`. Qualquer desvio → null = stub honesto (R6).
     */
    static java.util.List<String> comparisonReturnPrologue(List<BytecodeReader.Insn> insns,
            String[] cp, BytecodeFrame frame) {
        int n = insns.size();
        if (n < 8) return null;
        int cmpOp = insns.get(n - 6).opcode();
        if (cmpOp != 0x94 && cmpOp != 0x97 && cmpOp != 0x98) return null;
        int brOp = insns.get(n - 5).opcode();
        if (brOp < 0x99 || brOp > 0x9e) return null;
        if (insns.get(n - 4).opcode() != 0x04) return null;   // iconst_1
        if (insns.get(n - 3).opcode() != 0xa7) return null;   // goto
        if (insns.get(n - 2).opcode() != 0x03) return null;   // iconst_0
        if (!insns.get(n - 1).isReturn()) return null;
        if (insns.get(n - 3).target() != insns.get(n - 1).offset()) return null;
        if (insns.get(n - 5).target() != insns.get(n - 2).offset()) return null;
        // as OPERANDAS: o scan k desce do n-7 até achar a fatia que deixa
        // EXATAMENTE 2 valores na pilha (medido no inCircle: k = n-11 —
        // [dload d, dload r, dload r, dmul] antes do dcmpg)
        for (int k = n - 7; k >= Math.max(0, n - 17); k--) {
            BytecodeDecoder.MachineRun rr = BytecodeDecoder.machineRun(insns.subList(k, n - 6), cp, frame);
            if (rr == null || rr.stopped || rr.stack.size() != 2) continue;
            String op1 = rr.stack.topExpr();          // o topo = op1 do cmp
            rr.stack.pop();
            String op2 = rr.stack.topExpr();          // o fundo = op2
            // o espelho FALL-THROUGH-TRUE (o braço iconst_1 é o FALL do
            // ifXX): value1 OP value2 com a negação do jump-true — a forma
            // limpa `v10 <= (r*r)`, não a invertida
            String op = switch (brOp) {
                case 0x99 -> "!="; case 0x9a -> "=="; case 0x9b -> ">=";
                case 0x9c -> "<"; case 0x9d -> "<="; case 0x9e -> ">";
                default -> null;
            };
            if (op == null) return null;
            var declared = new java.util.HashSet<Integer>();
            var pre = BytecodeStatementsEmit.emitLinear(insns.subList(0, k), cp, frame, declared);
            if (pre == null) return null;
            var out = new java.util.ArrayList<String>();
            out.addAll(pre);
            out.add("return " + op2 + " " + op + " " + op1);
            return out;
        }
        return null;
    }

    static String comparisonReturn(List<BytecodeReader.Insn> insns, BytecodeFrame frame) {
        int n = insns.size();
        if (n < 5) return null;
        BytecodeReader.Insn last = insns.get(n - 1);
        if (!last.isReturn() || last.opcode() != 0xac) return null;
        BytecodeReader.Insn zero = insns.get(n - 2);
        if (zero.opcode() != 0x03) return null;
        BytecodeReader.Insn go = insns.get(n - 3);
        if (go.opcode() != 0xa7 || go.target() != last.offset()) return null;
        if (insns.get(n - 4).opcode() != 0x04) return null;
        BytecodeReader.Insn cmp = insns.get(n - 5);
        String inv = BytecodeCp.invCond(cmp.opcode());
        if (inv == null || cmp.target() != zero.offset()) return null;

        // Fase E (13/10) CORRECAO (medido: cmpChain dava `if (arg0 > arg1) 1
        // else 0`, DESCARTANDO o braco -1 = CODIGO ERRADO compilavel, anti-R6):
        // o cmp tem que ser o INICIO do fluxo — os operandos sao exatamente as
        // instrucoes imediatamente ANTES dele e NAO pode haver codigo antes.
        // A versao antiga lia os 2 primeiros loads do METODO (nao os do cmp) e
        // ignorava qualquer branch anterior, aceitando o TAIL de um ternario
        // ANINHADO como se fosse flat (o operando `-1` era perdido).
        int arity = (cmp.opcode() >= 0x9f && cmp.opcode() <= 0xa4) ? 2 : 1;
        int operandStart = n - 5 - arity;
        if (operandStart != 0) return null;   // ha codigo/branch antes do cmp → nao e flat
        java.util.List<String> operands = new java.util.ArrayList<>();
        for (int i = operandStart; i < n - 5; i++) {
            String v = BytecodeDecoderStack.loadValue(insns.get(i), frame);
            if (v == null) return null;
            operands.add(v);
        }
        String cond;
        if (arity == 2) {
            cond = operands.get(0) + " " + inv + " " + operands.get(1);
        } else {
            cond = operands.get(0) + " " + inv;
        }
        // §236 (14/09): o shape cmp/iconst_1/goto/iconst_0/ireturn e AMBIGUO na
        // raiz — `return x > 0` (Bool, Z no descriptor) e `return a < b ? 1 : 0`
        // (Int, I) temo MESMO bytecode. O fold cru `= cond` so vale p/ Bool;
        // num corpo Int virava Bool→SEM010 (nao-compilavel, anti-R6). Porta:
        // I → if-expression de inteiros (mesma forma do ifElseReturn:
        // `= if (c) 1 else 0`); Z → cond puro (byte-identico ao que passava);
        // outros (S/B/C do ireturn) → recusar (stub honesto).
        String ret = frame.retType();
        if (ret == null) return null;
        if (ret.equals("Z")) return cond;
        if (ret.equals("I")) return "if (" + cond + ") 1 else 0";
        return null;
    }

    // ── if/else de retorno (via CFG) ─────────────────────────────────────

    /**
     * Fase E (15/10): o JOIN dos bracos tem que ser um xreturn SOLITARIO
     * (o corpo inteiro e o if-expression). Medido no Warehouse.grade
     * (`String g = a >= 90 ? "A" : a >= 80 ? "B" : "C"; return g + "(" +
     * (int) a + ")"`): o join tinha astore+concat DEPOIS do if-expression e
     * a cauda era DERRUBADA — codigo compilavel ERRADO (R6). O then e o
     * guia (o goto dele aponta o join); sem goto (os bracos retornam
     * direto) o guard nao se aplica.
     */
    private static boolean joinIsBareReturn(BytecodeReader.Block thenB,
            List<BytecodeReader.Block> blocks, List<BytecodeReader.Insn> insns) {
        if (thenB == null) return true;
        List<BytecodeReader.Insn> bi = BytecodeDecoder.insnsWithin(thenB, insns);
        if (bi.isEmpty() || bi.get(bi.size() - 1).opcode() != 0xa7) return true;
        int j = bi.get(bi.size() - 1).target();
        BytecodeReader.Block jb = BytecodeDecoder.find(blocks, j);
        if (jb == null) return false;
        List<BytecodeReader.Insn> ji = BytecodeDecoder.insnsWithin(jb, insns);
        return ji.size() == 1 && ji.get(0).isReturn();
    }

    static String ifElseReturn(List<BytecodeReader.Insn> insns, String[] cp,
                                       BytecodeFrame frame) {
        List<BytecodeReader.Block> blocks = BytecodeReader.cfg(insns, new int[0]);
        if (blocks.isEmpty()) return null;
        BytecodeReader.Block entry = blocks.get(0);
        if (entry.succ.size() != 2) return null;
        // entrada é condicional: dois sucessores (then e else)
        String cond = BytecodeDecoder.blockCondition(entry, insns, frame, cp);
        if (cond == null) return null;
        // succ[0] = alvo do branch (falso/else); succ[1] = fall-through (verdadeiro/then)
        int elseStart = entry.succ.get(0);
        int thenStart = entry.succ.get(1);
        BytecodeReader.Block thenB = BytecodeDecoder.find(blocks, thenStart);
        BytecodeReader.Block elseB = BytecodeDecoder.find(blocks, elseStart);
        if (thenB == null || elseB == null) return null;
        // Fase E (13/10) `||`: se o braco ELSE (o alvo do jump) tem valor
        // VERDADEIRO constante, o shape e `a > 0 || b` (o jump do teste e o
        // braco TRUE) — o ifElseReturn so entende a orientacao &&/ternario;
        // devolver null deixa o struct walker emitir o || IDIOMATICO (sem
        // isso saia o De Morgan `a <= 0 && b <= 0`, correto porem ofuscado).
        String elseProbe = armExpr(elseB, blocks, insns, cp, frame, 0);
        if ("1".equals(elseProbe) || "true".equals(elseProbe)) return null;
        // Fase E (13/10) `&&`: se o braco THEN e ele mesmo um teste cujo FALSO
        // converge no MESMO else (o falso externo), o shape e `if (A) { if (B)
        // X else Y } else Y` = `if (A && B) X else Y` — a forma IDIOMATICA
        // (sem isso a recursao o emitia como ternario aninhado, ofuscando o &&).
        if (thenB != null && thenB.succ.size() == 2) {
            // DUAS orientacoes do teste interno do `&&` (medidas):
            //  (a) jump=FALSO (succ[0]==elseStart) — invCond; o verdadeiro e
            //      o fall-through (orientacao do && simples);
            //  (b) fall-through=FALSO (succ[1]==elseStart) — takenCond; o
            //      verdadeiro e o JUMP (a orientacao que javac usa quando o
            //      resultado do && alimenta um `||` — a precedencia MISTA).
            boolean orientA = thenB.succ.get(0) == elseStart;
            boolean orientB = thenB.succ.get(1) == elseStart;
            if (orientA || orientB) {
                String cInner = orientA
                        ? BytecodeDecoder.blockCondition(thenB, insns, frame, cp)
                        : BytecodeDecoder.blockConditionTaken(thenB, insns, frame, cp);
                BytecodeReader.Block innerThen = BytecodeDecoder.find(blocks,
                        orientA ? thenB.succ.get(1) : thenB.succ.get(0));
                String t = armExpr(innerThen, blocks, insns, cp, frame, 0);
                String e = armExpr(elseB, blocks, insns, cp, frame, 0);
                if (cInner != null && t != null && e != null
                        && joinIsBareReturn(thenB, blocks, insns)) {
                    return "if (" + cond + " && " + cInner + ") " + t + " else " + e;
                }
            }
        }
        // Fase E (13/10): o braco pode ser ELE MESMO um teste (ternario
        // ANINHADO — `a < b ? -1 : (a > b ? 1 : 0)`); o armExpr recursa.
        String thenE = armExpr(thenB, blocks, insns, cp, frame, 0);
        String elseE = armExpr(elseB, blocks, insns, cp, frame, 0);
        if (thenE == null || elseE == null) return null;
        if (!joinIsBareReturn(thenB, blocks, insns)) return null;
        return "if (" + cond + ") " + thenE + " else " + elseE;
    }

    // ── chamada VARARGS com array literal (String.join / Arrays.asList) ──

    /**
     * Fase E (14/10): `String.join(sep, a, b)` / `Arrays.asList(a, b)`
     * compilam para {@code iconst_N, anewarray, (dup, iconst_i, push,
     * aastore)*N, invokestatic}. O shape reconstrói os valores e emite o
     * idioma Kof; QUALQUER desvio (índice fora de ordem, valor não-linear,
     * resto inesperado) → null = stub honesto (R6 — nunca código errado).
     */
    static String varargsCall(List<BytecodeReader.Insn> insns, String[] cp,
                                      BytecodeFrame frame) {
        int n = insns.size();
        // 1. o anewarray com contagem literal imediatamente antes
        int p = -1;
        for (int i = 1; i < n; i++) {
            if (insns.get(i).opcode() == 0xbd && constInt(insns.get(i - 1)) != null) { p = i; break; }
        }
        if (p < 0) return null;
        int count = constInt(insns.get(p - 1));
        if (count < 1 || count > 16) return null;
        // 2. os args fixos: o prefixo [0..p-2] — vazio ou exatamente 1 valor
        List<BytecodeReader.Insn> pre = insns.subList(0, p - 1);
        String fixed = null;
        if (!pre.isEmpty()) {
            for (BytecodeReader.Insn in : pre) {
                int op = in.opcode();
                if ((op >= 0xac && op <= 0xb1) || op == 0xbf) return null;
            }
            BytecodeDecoder.MachineRun r = BytecodeDecoder.machineRun(pre, cp, frame);
            if (r == null || r.stopped || r.stack.size() != 1) return null;
            fixed = r.stack.topExpr();
        }
        // 3. o fill: (dup, iconst_i, pushes..., aastore) * count, em ordem
        List<String> elems = new java.util.ArrayList<>();
        int q = p + 1;
        for (int i = 0; i < count; i++) {
            if (q + 2 >= n || insns.get(q).opcode() != 0x59) return null;
            Integer idx = constInt(insns.get(q + 1));
            if (idx == null || idx != i) return null;
            int end = q + 2;
            while (end < n && insns.get(end).opcode() != 0x53) end++;
            if (end >= n || end == q + 2) return null;
            List<BytecodeReader.Insn> val = insns.subList(q + 2, end);
            for (BytecodeReader.Insn in : val) {
                int op = in.opcode();
                if ((op >= 0xac && op <= 0xb1) || op == 0xbf) return null;
            }
            BytecodeDecoder.MachineRun r = BytecodeDecoder.machineRun(val, cp, frame);
            if (r == null || r.stopped || r.stack.size() != 1) return null;
            elems.add(r.stack.topExpr());
            q = end + 1;
        }
        // 4. a chamada e SÓ o return depois
        if (q != n - 2 || insns.get(q).opcode() != 0xb8) return null;
        if (!insns.get(n - 1).isReturn()) return null;
        String[] m = BytecodeCp.resolveMethodRef(cp, insns.get(q).operands()[0]);
        if (m == null) return null;
        // 5. o mapeamento Kof
        if ("java/lang/String".equals(m[0]) && "join".equals(m[1])
                && m[2].endsWith(")Ljava/lang/String;")) {
            if (fixed == null) return null;
            StringBuilder sb = new StringBuilder(elems.get(0));
            for (int i = 1; i < elems.size(); i++)
                sb.append(" + ").append(fixed).append(" + ").append(elems.get(i));
            return sb.toString();
        }
        if ("java/util/Arrays".equals(m[0]) && "asList".equals(m[1])
                && m[2].endsWith(")Ljava/util/List;")) {
            if (fixed != null) return null;
            return "listOf(" + String.join(", ", elems) + ")";
        }
        return null;
    }

    private static Integer constInt(BytecodeReader.Insn in) {
        return switch (in.opcode()) {
            case 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08 -> in.opcode() - 0x03;
            case 0x10 -> (int) (byte) in.operands()[0];
            case 0x11 -> (int) (short) in.operands()[0];
            default -> null;
        };
    }

    /**
     * Expressao do valor de um braco: primeiro tenta o caminho LINEAR (tirando
     * o goto final que converge no join); se o bloco for um TESTE (2 sucessores)
     * recursa — cobre o ternario ANINHADO. Profundidade limitada (o aninhamento
     * e finito no codigo real). null = stub honesto (R6).
     */
    static String armExpr(BytecodeReader.Block b, List<BytecodeReader.Block> blocks,
                                  List<BytecodeReader.Insn> insns, String[] cp,
                                  BytecodeFrame frame, int depth) {
        if (b == null || depth > 6) return null;
        List<BytecodeReader.Insn> bi = BytecodeDecoder.insnsWithin(b, insns);
        if (bi.isEmpty()) return null;
        List<BytecodeReader.Insn> core = bi;
        if (bi.get(bi.size() - 1).opcode() == 0xa7) core = bi.subList(0, bi.size() - 1);
        if (!core.isEmpty()) {
            String lin = BytecodeDecoder.linearReturn(core, cp, frame);
            if (lin == null) {
                // braco SEM return (termina em goto — o valor fica na pilha
                // p/ o join, shape da precedencia mista): via LAX (topExpr,
                // sem checagem de tipo de retorno)
                boolean hasRet = core.stream().anyMatch(i ->
                        (i.opcode() >= 0xac && i.opcode() <= 0xb1) || i.opcode() == 0xbf);
                if (!hasRet) lin = BytecodeDecoder.linearReturn(core, cp, frame, true);
            }
            if (lin != null) {
                // coercao Bool (medido SEM010): metodo `Z` com bracos
                // iconst 1/0 → true/false (o Kof exige Bool)
                if (frame != null && "Z".equals(frame.retType())) {
                    if ("1".equals(lin)) return "true";
                    if ("0".equals(lin)) return "false";
                }
                return lin;
            }
        }
        if (b.succ.size() != 2) return null;
        String cond = BytecodeDecoder.blockCondition(b, insns, frame, cp);
        if (cond == null) return null;
        BytecodeReader.Block elseB = BytecodeDecoder.find(blocks, b.succ.get(0));
        BytecodeReader.Block thenB = BytecodeDecoder.find(blocks, b.succ.get(1));
        String t = armExpr(thenB, blocks, insns, cp, frame, depth + 1);
        String e = armExpr(elseB, blocks, insns, cp, frame, depth + 1);
        if (t == null || e == null) return null;
        return "if (" + cond + ") " + t + " else " + e;
    }
}
