package dev.kof.cli;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Recovery de `switch` (Fase C) — extraído p/ o gate ≤500 (usado por
 * `BytecodeStatements.recoverSwitch` e `recoverSwitchStmt`, sem duplicar).
 *
 * <p>Utilidades puras sobre offsets: {@link #range}, {@link #nextBound},
 * {@link #parseSwitch} (+ {@link #readInt4}). Nenhuma decisão de emissão
 * aqui — só fatiamento honesto do bytecode (bounds checados pelo chamador).
 */
final class BytecodeSwitch {

    private BytecodeSwitch() {
    }

    static List<BytecodeReader.Insn> range(List<BytecodeReader.Insn> insns, int from, int to) {
        List<BytecodeReader.Insn> out = new ArrayList<>();
        for (BytecodeReader.Insn in : insns) {
            if (in.offset() >= from && in.offset() < to) out.add(in);
        }
        return out;
    }

    static int nextBound(TreeSet<Integer> bounds, int start, int maxOff) {
        Integer higher = bounds.higher(start);
        return higher == null ? maxOff : higher;
    }

    /** Info de switch parseada (classe final — acessores de record aninhado
     *  não atravessam classe no javac; campos públicos diretos). */
    static final class SwitchInfo {
        final int dflt;
        final int[] values;
        final int[] targets;
        SwitchInfo(int dflt, int[] values, int[] targets) {
            this.dflt = dflt;
            this.values = values;
            this.targets = targets;
        }
    }

    static SwitchInfo parseSwitch(byte[] code, int pc) {
        int op = code[pc] & 0xFF;
        if (op != 0xaa && op != 0xab) return null;
        int pad = (4 - ((pc + 1) % 4)) % 4;
        int p = pc + 1 + pad;
        if (p + 4 > code.length) return null;
        int dflt = pc + readInt4(code, p);
        p += 4;
        if (op == 0xaa) {
            int low = readInt4(code, p); p += 4;
            int high = readInt4(code, p); p += 4;
            int n = high - low + 1;
            if (n < 0 || p + n * 4 > code.length) return null;
            int[] values = new int[n];
            int[] targets = new int[n];
            for (int i = 0; i < n; i++) {
                values[i] = low + i;
                targets[i] = pc + readInt4(code, p);
                p += 4;
            }
            return new SwitchInfo(dflt, values, targets);
        }
        int npairs = readInt4(code, p); p += 4;
        if (npairs < 0 || p + npairs * 8 > code.length) return null;
        int[] values = new int[npairs];
        int[] targets = new int[npairs];
        for (int i = 0; i < npairs; i++) {
            values[i] = readInt4(code, p); p += 4;
            targets[i] = pc + readInt4(code, p); p += 4;
        }
        return new SwitchInfo(dflt, values, targets);
    }

    static int readInt4(byte[] code, int pc) {
        return (code[pc] << 24) | ((code[pc + 1] & 0xFF) << 16)
             | ((code[pc + 2] & 0xFF) << 8) | (code[pc + 3] & 0xFF);
    }

    /**
     * `switch` como STATEMENT (Fase C): cases com corpos statement
     * (side-effects), sem valor produzido. Complementa o `recoverSwitch`
     * (só expressão-com-return). Devolve null (stub honesto) em qualquer
     * forma não-provada:
     *
     * <ul>
     *   <li>discriminante fora do linear puro, ou com `$` (enum `$SwitchMap`,
     *       sintético — valor do case seria ordinal, conteúdo perdido);</li>
     *   <li>corpo com branch fora do terminal (if/loops aninhados, `&&`),
     *       região vazia não-default (`case 1: case 2:` = alias/fallthrough),
     *       `goto` p/ fora do fim comum (fallthrough explícito);</li>
     *   <li>fins comuns divergentes (vários alvos de break);</li>
     *   <li>epílogo após `return`/`throw` em algum braço (inalcançável).</li>
     * </ul>
     *
     * <p>Semântica Kof verificada por probe (JVM+script): cases são
     * EXCLUSIVOS (sem fallthrough); `break` é opcional; `default: break`
     * solitário compila e não faz nada. Por isso: `goto`→fim some (nada
     * emitido), `default` vazio vira `default: break`, último braço pode
     * cair no fim sem `goto`.
     */
    /**
     * Fase E (14/10): o switch de STRING — o javac desdobra em
     * {@code hashCode() -> lookupswitch} + cadeias {@code equals} que
     * atribuem um índice + um SEGUNDO switch sobre o índice. Reconstrói os
     * casos literais e emite a forma seta Kof (que aceita String — medido).
     * Shape canônico ESTRITO; QUALQUER desvio → null = stub honesto (R6).
     */
    static List<String> stringSwitchExpr(byte[] code, List<BytecodeReader.Insn> insns,
                                         String[] cp, BytecodeFrame frame) {
        if (insns.size() < 12) return null;
        // 1. o desugar [aload D, astore T, iconst_m1, istore IDX, aload T,
        //    hashCode, lookupswitch] — escaneado: a forma STATEMENT tem o
        //    prólogo REAL antes (`r = 0`).
        int hOff = -1;
        for (int i = 5; i + 1 < insns.size(); i++) {
            String[] hmP = insns.get(i).opcode() == 0xb6
                    ? BytecodeCp.resolveMethodRef(cp, insns.get(i).operands()[0]) : null;
            if (hmP == null || !"java/lang/String".equals(hmP[0])
                    || !"hashCode".equals(hmP[1]) || insns.get(i + 1).opcode() != 0xab) continue;
            int d = i - 5;
            if (d < 0) continue;
            boolean d0 = insns.get(d).opcode() == 0x19 || (insns.get(d).opcode() >= 0x2a && insns.get(d).opcode() <= 0x2d);
            boolean d1 = insns.get(d + 1).opcode() == 0x3a || (insns.get(d + 1).opcode() >= 0x4a && insns.get(d + 1).opcode() <= 0x4d);
            boolean d2 = insns.get(d + 2).opcode() == 0x02;
            boolean d3 = insns.get(d + 3).opcode() == 0x36 || (insns.get(d + 3).opcode() >= 0x3b && insns.get(d + 3).opcode() <= 0x3e);
            boolean d4 = insns.get(i - 1).opcode() == 0x19 || (insns.get(i - 1).opcode() >= 0x2a && insns.get(i - 1).opcode() <= 0x2d);
            if (d0 && d1 && d2 && d3 && d4) { hOff = i; break; }
        }
        if (hOff < 0) return null;
        int dBase = hOff - 5;
        String disc = BytecodeDecoderStack.loadValue(insns.get(dBase), frame);
        if (disc == null) return null;
        SwitchInfo hash = parseSwitch(code, insns.get(hOff + 1).offset());
        if (hash == null || hash.targets.length == 0) return null;
        // o prólogo REAL (antes do desugar) vira statements via emitLinear;
        // o set `declared` COMPARTILHADO (prólogo + braços + cauda) — sets
        // frescos redeclaram `var v1` (SEM011; medido)
        java.util.Set<Integer> shared = new java.util.HashSet<>();
        List<String> prologue = null;
        if (dBase > 0) {
            prologue = BytecodeStatementsEmit.emitLinear(insns.subList(0, dBase), cp, frame,
                    shared);
            if (prologue == null) return null;
        }
        // 2. as cadeias equals: [aload T, ldc LIT, invokevirtual String.equals,
        //    ifeq J, const K, istore IDX, goto J] — 7 insns, K distinto 0..N-1
        java.util.Map<Integer, String> caseLit = new java.util.TreeMap<>();
        int join = hash.dflt;
        for (int hi : hash.targets) {
            int i = -1;
            for (int k = 0; k < insns.size(); k++)
                if (insns.get(k).offset() == hi) { i = k; break; }
            if (i < 0 || i + 6 >= insns.size()) return null;
            boolean e0 = insns.get(i).opcode() == 0x19 || (insns.get(i).opcode() >= 0x2a && insns.get(i).opcode() <= 0x2d);
            int lop = insns.get(i + 1).opcode();
            boolean e1 = lop == 0x12 || lop == 0x13;
            String lit = e1 ? BytecodeCp.ldc(cp, insns.get(i + 1).operands()[0]) : null;
            String[] eq = insns.get(i + 2).opcode() == 0xb6
                    ? BytecodeCp.resolveMethodRef(cp, insns.get(i + 2).operands()[0]) : null;
            boolean e2 = eq != null && "java/lang/String".equals(eq[0]) && "equals".equals(eq[1]);
            boolean e3 = insns.get(i + 3).opcode() == 0x99 && insns.get(i + 3).target() == join;
            Integer k = constVal(insns.get(i + 4));
            boolean e5 = insns.get(i + 5).opcode() == 0x36 || (insns.get(i + 5).opcode() >= 0x3b && insns.get(i + 5).opcode() <= 0x3e);
            // o ÚLTIMO caso de hash cai no join SEM goto (medido: a cadeia
            // tem 6 insns e i+6 é o próprio insn do join)
            boolean e6;
            if (insns.get(i + 6).opcode() == 0xa7) {
                e6 = insns.get(i + 6).target() == join;
            } else {
                e6 = insns.get(i + 6).offset() == join;
            }
            if (!(e0 && e1 && lit != null && e2 && e3 && k != null && e5 && e6)) return null;
            if (caseLit.put(k, lit) != null) return null;   // índice repetido
        }
        // 3. o SEGUNDO switch (sobre o índice) no join
        int j = -1;
        for (int k = 0; k < insns.size(); k++)
            if (insns.get(k).offset() == join) { j = k; break; }
        if (j < 0 || j + 1 >= insns.size()) return null;
        // o join é [iload IDX, lookupswitch/tableswitch] — o switch vem
        // DEPOIS do load do índice (medido: 61=iload_2, 62=lookupswitch)
        boolean il = insns.get(j).opcode() == 0x15
                || (insns.get(j).opcode() >= 0x1a && insns.get(j).opcode() <= 0x1d);
        int jop = insns.get(j + 1).opcode();
        if (!il || (jop != 0xaa && jop != 0xab)) return null;
        SwitchInfo idx = parseSwitch(code, insns.get(j + 1).offset());
        if (idx == null || idx.targets.length != caseLit.size()) return null;
        // 4. os braços: cada um deixa EXATAMENTE 1 valor e o fim é o return
        BytecodeReader.Insn last = insns.get(insns.size() - 1);
        if (!last.isReturn()) return null;
        // o FIM COMUM é o ALVO DOS GOTOS dos braços — NÃO o offset do return
        // (medido no strSwitchStmt: os gotos apontam p/ 100 [iload_1] e o
        // ireturn está em 101; usar 101 dava ranges vazios). O primeiro goto
        // APÓS o switch do índice define o fim; os demais têm que casar.
        int swOff = insns.get(j + 1).offset();
        int commonEnd = -1;
        for (BytecodeReader.Insn g : insns) {
            if (g.offset() <= swOff) continue;
            if (g.opcode() != 0xa7) continue;
            if (commonEnd == -1) commonEnd = g.target();
            else if (commonEnd != g.target()) return null;
        }
        if (commonEnd < 0) return null;
        java.util.TreeSet<Integer> bounds = new java.util.TreeSet<>();
        bounds.add(idx.dflt);
        for (int tt : idx.targets) bounds.add(tt);
        if (bounds.first() >= commonEnd) return null;
        List<Integer> starts = new ArrayList<>(bounds);
        StringBuilder sb = new StringBuilder("return switch (").append(disc).append(") { ");
        boolean sawDefault = false;
        for (int i = 0; i < starts.size(); i++) {
            int s = starts.get(i);
            int e = (i + 1 < starts.size()) ? starts.get(i + 1) : commonEnd;
            List<BytecodeReader.Insn> arm = range(insns, s, e);
            if (arm.isEmpty()) return null;
            List<BytecodeReader.Insn> core = arm;
            if (arm.get(arm.size() - 1).opcode() == 0xa7) {
                if (arm.get(arm.size() - 1).target() != commonEnd) return null;
                core = arm.subList(0, arm.size() - 1);
            }
            if (core.isEmpty()) return null;
            for (BytecodeReader.Insn in : core) {
                int op = in.opcode();
                if ((op >= 0xac && op <= 0xb1) || op == 0xbf) return null;
            }
            BytecodeDecoder.MachineRun r = BytecodeDecoder.machineRun(core, cp, frame);
            // Fase E (14/10): braço STATEMENT ([push, istore, goto]) — o
            // machineRun não modela store → cai p/ a forma statement
            if (r == null || r.stopped || r.stack.size() != 1) {
                return stringSwitchStmt(insns, cp, frame, disc, idx, caseLit,
                        starts, commonEnd, prologue, shared);
            }
            if (r == null || r.stopped || r.stack.size() != 1) return null;
            String val = r.stack.topExpr();
            if (s == idx.dflt) {
                sb.append("default -> ").append(val);
                sawDefault = true;
            } else {
                Integer cv = null;
                for (int k2 = 0; k2 < idx.targets.length; k2++)
                    if (idx.targets[k2] == s) cv = idx.values[k2];
                String lit = cv == null ? null : caseLit.get(cv);
                if (lit == null) return null;
                sb.append("case ").append(lit).append(" -> ").append(val);
            }
            if (i < starts.size() - 1) sb.append(" ");
        }
        if (!sawDefault) return null;
        sb.append(" }");
        List<String> single = new ArrayList<>();
        single.add(sb.toString());
        return single;
    }

    /**
     * Fase E (14/10): a forma STATEMENT do switch de STRING — os braços são
     * statements ([pushes, istore, goto]) convergindo no fim comum; emite
     * `switch (s) { case "a": <stmts> break ... default: <stmts> }` + a
     * cauda. Qualquer desvio → null = stub honesto (R6).
     */
    private static List<String> stringSwitchStmt(List<BytecodeReader.Insn> insns,
            String[] cp, BytecodeFrame frame, String disc, SwitchInfo idx,
            java.util.Map<Integer, String> caseLit, List<Integer> starts,
            int commonEnd, List<String> prologue, java.util.Set<Integer> shared) {
        List<String> out = new ArrayList<>();
        if (prologue != null) out.addAll(prologue);
        out.add("switch (" + disc + ") {");
        boolean sawDefault = false;
        for (int i = 0; i < starts.size(); i++) {
            int s = starts.get(i);
            int e = (i + 1 < starts.size()) ? starts.get(i + 1) : commonEnd;
            List<BytecodeReader.Insn> arm = range(insns, s, e);
            if (arm.isEmpty()) return null;
            List<BytecodeReader.Insn> core = arm;
            if (arm.get(arm.size() - 1).opcode() == 0xa7) {
                if (arm.get(arm.size() - 1).target() != commonEnd) return null;
                core = arm.subList(0, arm.size() - 1);
            }
            if (core.isEmpty()) return null;
            String label;
            if (s == idx.dflt) {
                label = "default:";
                sawDefault = true;
            } else {
                Integer cv = null;
                for (int k2 = 0; k2 < idx.targets.length; k2++)
                    if (idx.targets[k2] == s) cv = idx.values[k2];
                String lit = cv == null ? null : caseLit.get(cv);
                if (lit == null) return null;
                label = "case " + lit + ":";
            }
            out.add(label);
            var stmts = BytecodeStatementsEmit.emitLinear(core, cp, frame, shared);
            if (stmts == null) return null;
            out.addAll(stmts);
            if (s != idx.dflt) out.add("break");
        }
        if (!sawDefault) return null;
        out.add("}");
        var tail = BytecodeStatementsEmit.emitLinear(range(insns, commonEnd,
                insns.get(insns.size() - 1).offset() + 1), cp, frame, shared);
        if (tail == null) return null;
        out.addAll(tail);
        return out;
    }

    private static Integer constVal(BytecodeReader.Insn in) {
        return switch (in.opcode()) {
            case 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08 -> in.opcode() - 0x03;
            case 0x10 -> (int) (byte) in.operands()[0];
            case 0x11 -> (int) (short) in.operands()[0];
            default -> null;
        };
    }

    /**
     * Fase E (14/10): a forma EXPRESSÃO do switch — cada braço é
     * `[pushes..., goto join]` (o default cai) deixando 1 valor, o join é
     * o return. Emite {@code return switch (x) { case 1 -> 10 ... default -> -1 }}.
     * Qualquer desvio → null (a forma statement cuida).
     */
    private static String switchExprForm(SwitchInfo si, List<Integer> starts, int end,
                                         List<BytecodeReader.Insn> insns, String[] cp,
                                         BytecodeFrame frame, String expr) {
        if (starts.size() < 2) return null;
        StringBuilder sb = new StringBuilder("return switch (").append(expr).append(") { ");
        boolean sawDefault = false;
        for (int i = 0; i < starts.size(); i++) {
            int s = starts.get(i);
            int e = (i + 1 < starts.size()) ? starts.get(i + 1) : end;
            List<BytecodeReader.Insn> arm = range(insns, s, e);
            if (arm.isEmpty()) return null;
            List<BytecodeReader.Insn> core = arm;
            if (arm.get(arm.size() - 1).opcode() == 0xa7) core = arm.subList(0, arm.size() - 1);
            if (core.isEmpty()) return null;
            for (BytecodeReader.Insn in : core) {
                int op = in.opcode();
                if ((op >= 0xac && op <= 0xb1) || op == 0xbf) return null;
            }
            BytecodeDecoder.MachineRun r = BytecodeDecoder.machineRun(core, cp, frame);
            if (r == null || r.stopped || r.stack.size() != 1) return null;
            String val = r.stack.topExpr();
            if (s == si.dflt) {
                sb.append("default -> ").append(val);
                sawDefault = true;
            } else {
                Integer cv = null;
                for (int k = 0; k < si.targets.length; k++)
                    if (si.targets[k] == s) cv = si.values[k];
                if (cv == null) return null;
                sb.append("case ").append(cv).append(" -> ").append(val);
            }
            if (i < starts.size() - 1) sb.append(" ");
        }
        if (!sawDefault) return null;
        sb.append(" }");
        return sb.toString();
    }

    static List<String> recoverSwitchStmt(byte[] code, List<BytecodeReader.Insn> insns,
                                          String[] cp, BytecodeFrame frame) {
        int swOff = -1;
        for (int pc = 0; pc < code.length; ) {
            int op = code[pc] & 0xFF;
            if (op == 0xaa || op == 0xab) { swOff = pc; break; }
            int l = BytecodeReader.length(op);
            pc += (l == -1) ? 1 : l;
        }
        if (swOff < 0) return null;
        SwitchInfo si = parseSwitch(code, swOff);
        if (si == null || si.targets.length == 0) return null;

        // Fase E (14/10): o discriminante pode ter PRÓLOGO com stores
        // (`int r = 0; switch (x) ...`) — o machineRun é avaliador de
        // EXPRESSÃO (sem store) e o switch inteiro stubava. Fatiar: os
        // [pushes, xstore]* iniciais viram statements (emitLinear) e o
        // resto é o discriminante (linearExpr + simDepth, como antes).
        List<BytecodeReader.Insn> pre = range(insns, 0, swOff);
        int cut = 0;
        for (int i = 0; i < pre.size(); i++) {
            if (pre.get(i).opcode() >= 0x3b && pre.get(i).opcode() <= 0x4e) cut = i + 1;
        }
        // (a emissão do prólogo é ADIADA para depois do Set `declared` —
        // compartilhá-lo evita `var v2` do hoist + `var v2 = 0` do prólogo
        // = redeclaração SEM011; medido na primeira tentativa)
        List<BytecodeReader.Insn> disc = pre.subList(cut, pre.size());
        String expr = BytecodeDecoder.linearExpr(disc, cp, frame);
        if (expr == null || expr.contains("$")) return null;
        // discriminante deixa EXATAMENTE a chave na pilha (sem lixo/lixeira):
        // prova por simulação (trecho straight-line até o switch).
        Integer discDepth = simDepth(disc, cp);
        if (discDepth == null || discDepth != 1) return null;

        TreeSet<Integer> bounds = new TreeSet<>();
        bounds.add(si.dflt);
        for (int t : si.targets) bounds.add(t);
        int maxOff = insns.get(insns.size() - 1).offset() + 1;

        // fim comum: único alvo dos `goto` internos (breaks). Sem goto →
        // só return/throw/quedas-no-fim são válidos (O = maxOff).
        Integer commonEnd = null;
        for (BytecodeReader.Insn in : insns) {
            int off = in.offset();
            if (off < bounds.first() || off >= maxOff) continue;
            int target = gotoTarget(code, off, in.opcode());
            if (target < 0) continue;
            if (commonEnd == null) commonEnd = target;
            else if (commonEnd != target) return null;   // fins divergentes
        }
        int end = commonEnd == null ? maxOff : commonEnd;
        // fim antes de todos os braços = laço por fora (goto p/ trás) —
        // território do struct(); recusar (nunca duplicar).
        if (end < bounds.first()) return null;

        // ordem de emissão: valores crescentes (determinístico; validação
        // posicional independe da ordem).
        Integer[] order = new Integer[si.targets.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> Integer.compare(si.values[a], si.values[b]));

        // Fase E (14/10): a FORMA EXPRESSÃO — todos os braços deixam
        // EXATAMENTE 1 valor na pilha e o fim comum é um ÚNICO return →
        // o switch-expression Kof (forma seta). Qualquer braço com
        // statement (store, chamada void...) → null = cai na forma
        // statement (abaixo), sem perda.
        BytecodeReader.Insn lastInsn = insns.get(insns.size() - 1);
        if (lastInsn.isReturn() && lastInsn.offset() == end) {
            String exprForm = switchExprForm(si, new ArrayList<>(bounds), end,
                    range(insns, bounds.first(), maxOff), cp, frame, expr);
            if (exprForm != null) {
                List<String> single = new ArrayList<>();
                single.add(exprForm);
                return single;
            }
        }
        List<String> out = new ArrayList<>();
        Set<Integer> declared = new HashSet<>();
        // Hoist (bug 134): cases Kof são blocos EXCLUDENTES — um `var`
        // 1º-storeado dentro de um braço não alcança os irmãos nem o epílogo
        // (SEM011; probe 11/09). Todo slot NÃO-parâmetro storeado numa região
        // de braço é pré-declarado ANTES do switch como `var` sem init
        // (executa e aceita atribuição de tipos diferentes — probe JVM).
        // Fase E (14/10): o prólogo com stores vem ANTES do hoist — ele
        // declara os slots COM init (`var v2 = 0`), e o hoist pula o que já
        // está declarado (o `var v2` sem init fazia o compilador Kof boxar
        // as atribuições → VerifyError no ireturn; medido).
        if (cut > 0) {
            List<String> prologue = BytecodeStatementsEmit.emitLinear(pre.subList(0, cut), cp, frame, declared);
            if (prologue == null) return null;
            out.addAll(prologue);
        }
        List<Integer> hoist = new ArrayList<>();
        for (int slot : localSlotsStoredIn(insns, bounds.first(), end)) {
            if (frame.isNamedSlot(slot)) continue;   // parâmetro/`this` — nunca hoista
            if (declared.contains(slot)) continue;   // já declarado no prólogo
            if (!hoist.contains(slot)) hoist.add(slot);
        }
        for (int slot : localSlotsStoredIn(insns, end, maxOff)) {
            if (frame.isNamedSlot(slot)) continue;
            if (declared.contains(slot)) continue;
            if (!hoist.contains(slot)) hoist.add(slot);
        }
        for (int slot : hoist) {
            declared.add(slot);
            out.add("var " + BytecodeDecoderStack.slotName(slot, frame));
        }
        out.add("switch (" + expr + ") {");
        // cases em ordem crescente de valor (determinístico); default por último.
        for (int oi = 0; oi < order.length; oi++) {
            int t = si.targets[order[oi]];
            if (!emitCase(code, insns, cp, frame, out, declared, si, t, false, end, maxOff, bounds))
                return null;
        }
        if (!emitCase(code, insns, cp, frame, out, declared, si, si.dflt, true, end, maxOff, bounds))
            return null;

        // epílogo APÓS `}` (os braços saem via `break`; cair aqui de dentro
        // do switch executaria o próximo braço por engano).
        List<String> tail = null;
        if (end != maxOff) {
            tail = emitBody(code, insns, cp, frame, declared, end, maxOff);
            if (tail == null) return null;
        }
        out.add("}");
        if (tail != null) out.addAll(tail);
        return out;
    }

    /** Slots (não-parâmetro, não-`this`) storeados na região [from,to) — a
     *  lista de candidatos ao hoist do bug 134. Mesma decodificação de
     *  opcode que `BytecodeStatements.storeSlot` (mantida local: o util de
     *  statements é privado e a fórmula é estável do JVMS 6.5). */
    static List<Integer> localSlotsStoredIn(List<BytecodeReader.Insn> insns, int from, int to) {
        List<Integer> slots = new ArrayList<>();
        for (BytecodeReader.Insn in : insns) {
            int off = in.offset();
            if (off < from || off >= to) continue;
            int op = in.opcode();
            int slot;
            if (op >= 0x36 && op <= 0x3a) slot = in.operands()[0];            // istore..astore idx
            else if (op >= 0x3b && op <= 0x3e) slot = op - 0x3b;              // istore_0..3
            else if (op >= 0x3f && op <= 0x42) slot = op - 0x3f;              // lstore_0..3
            else if (op >= 0x43 && op <= 0x46) slot = op - 0x43;              // fstore_0..3
            else if (op >= 0x47 && op <= 0x4a) slot = op - 0x47;              // dstore_0..3
            else if (op >= 0x4b && op <= 0x4e) slot = op - 0x4b;              // astore_0..3
            else continue;
            slots.add(slot);
        }
        return slots;
    }

    /**
     * Profundidade de pilha (em VALORES; long/double = 1) ao fim de um trecho
     * straight-line, ou null (opcode fora da tabela = desconhecido → recusar).
     * Só vale p/ trecho sem branches/merges (caminho único) — exatamente o
     * que `emitCase` exige antes de chamar. Usado p/ provar junção vazia:
     * todo `goto`→fim comum parte de profundidade 0 (texto emitido não perde
     * valores; shape Scala-match com valor no join é recusado aqui, não no
     * verificador). `monitorenter/exit` incluídos; `pop2`/wide/`jsr`/
     * `multianewarray`/`invokedynamic` = desconhecido (raros, recusar).
     */
    static Integer simDepth(List<BytecodeReader.Insn> body, String[] cp) {
        int d = 0;
        for (BytecodeReader.Insn in : body) {
            int op = in.opcode();
            Integer delta = stackDelta(op, in, cp);
            if (delta == null) return null;
            d += delta;
            if (d < 0) return null;   // underflow = trecho quebrado/não-linear
        }
        return d;
    }

    /** Efeito-líquido na pilha (valores) ou null (desconhecido). */
    private static Integer stackDelta(int op, BytecodeReader.Insn in, String[] cp) {
        if (op == 0x00) return 0;                                        // nop
        if ((op >= 0x01 && op <= 0x15) || (op >= 0x1a && op <= 0x2d)) return 1; // consts+loads (wide-aware: 1 valor)
        if (op == 0x16 || op == 0x17 || op == 0x18) return 1;             // lload/fload/dload
        if (op >= 0x36 && op <= 0x4e) return -1;                         // stores
        if (op == 0x57) return -1;                                       // pop
        if (op == 0x58) return null;                                     // pop2 (1 ou 2 valores? ambíguo)
        if (op >= 0x59 && op <= 0x5d) return 1;                          // dup*
        if (op == 0x5e || op == 0x5f) return 0;                          // swap
        if ((op >= 0x60 && op <= 0x83) || (op >= 0x78 && op <= 0x7d)) {   // aritmética binária
            if (op == 0x74 || op == 0x75 || op == 0x76 || op == 0x77) return 0; // neg unário
            return -1;
        }
        if (op == 0x84) return 0;                                        // iinc
        if (op >= 0x85 && op <= 0x93) return 0;                          // conversões
        if (op >= 0x94 && op <= 0x98) return -1;                         // lcmp/fcmp/dcmp
        if (op == 0xbc) return 0;                                        // newarray (pop n, push ref)
        if (op == 0xbd) return 0;                                        // anewarray (pop n, push ref)
        if (op == 0xc5) return null;                                     // multianewarray (ndims no operando? recusar)
        if (op == 0xbe) return 0;                                        // arraylength
        if ((op >= 0x2e && op <= 0x35)) return -1;                       // xaload (pop2 push1)
        if ((op >= 0x4f && op <= 0x56)) return -3;                       // xastore (pop3)
        if (op == 0xc0 || op == 0xc1) return 0;                          // checkcast/instanceof
        if (op == 0xc2 || op == 0xc3) return -1;                         // monitorenter/exit
        if (op == 0xb2 || op == 0xb4) return 1;                          // getstatic/getfield
        if (op == 0xb3) return -1;                                       // putstatic
        if (op == 0xb5) return -2;                                       // putfield
        if (op == 0xbb) return 1;                                        // new
        if (op == 0xb8 || op == 0xb6 || op == 0xb9 || op == 0xb7) {       // invokes (não-dynamic)
            String[] m = BytecodeCp.resolveMethodRef(cp, in.operands().length > 0 ? in.operands()[0] : -1);
            if (m == null) return null;
            int argc = BytecodeCp.argCount(m[2]);
            boolean isVoid = m[2].indexOf(')') >= 0 && m[2].charAt(m[2].indexOf(')') + 1) == 'V';
            int recv = (op == 0xb8) ? 0 : 1;   // static sem receiver
            return -argc - recv + (isVoid ? 0 : 1);
        }
        if (op == 0xba) return null;                                     // invokedynamic (bootstrap? recusar)
        return null;                                                     // goto/jsr/ret/wide/switch/return/throw...
    }

    /** Alvo de goto/goto_w no offset, ou -1 (não-goto ou truncado). */
    private static int gotoTarget(byte[] code, int pc, int op) {
        if (op == 0xa7) {
            if (pc + 2 >= code.length) return -1;
            int w = ((code[pc + 1] & 0xFF) << 8) | (code[pc + 2] & 0xFF);
            return pc + (short) w;
        }
        if (op == 0xc8) {
            if (pc + 4 >= code.length) return -1;
            return pc + readInt4(code, pc + 1);
        }
        return -1;
    }

    /** true = branch/switch/wide (qualquer fluxo não-sequencial). */
    private static boolean isBranchOp(int op) {
        return (op >= 0x99 && op <= 0xa8) || op == 0xaa || op == 0xab
                || op == 0xc4 || op == 0xc6 || op == 0xc7 || op == 0xc8 || op == 0xc9;
    }

    private static boolean isReturnOp(int op) {
        return (op >= 0xac && op <= 0xb0) || op == 0xbf;
    }

    /** Região tem branch fora do terminal? (raro: só terminal permitido). */
    private static boolean hasMidBranch(List<BytecodeReader.Insn> body) {
        for (int i = 0; i + 1 < body.size(); i++) {
            if (isBranchOp(body.get(i).opcode())) return true;
        }
        return false;
    }


    /**
     * Decodifica UM braço (case ou default) via emitLinear. O `goto`→fim
     * terminal some (Kof é exclusivo); `return`/`throw` passam; queda no fim
     * comum sem `goto` só vale no último (bodyEnd == end). Região vazia:
     * default → `default: break`; case → recusa (alias/fallthrough).
     */
    private static boolean emitCase(byte[] code, List<BytecodeReader.Insn> insns, String[] cp,
                                    BytecodeFrame frame, List<String> out, Set<Integer> declared,
                                    SwitchInfo si, int target, boolean isDefault,
                                    int end, int maxOff, TreeSet<Integer> bounds) {
        // CAP no fim comum: a região do braço nunca inclui bytes do epílogo
        // ([end, maxOff) é emitido UMA vez, separado). Sem o cap, o braço que
        // cai no fim (ex.: default) duplicaria o epílogo dentro de si.
        // Soundness: o corte é sempre em fronteira de statement — todo `goto`
        // parte de profundidade 0 provada abaixo, e quedas só valem chegando
        // no `end` (fluxo sequencial p/ o epílogo compartilhado).
        int bodyEnd = nextBound(bounds, target, maxOff);
        int cappedEnd = Math.min(bodyEnd, end);
        List<BytecodeReader.Insn> body = range(insns, target, cappedEnd);
        if (body.isEmpty()) {
            if (!isDefault) return false;   // alias (`case 1: case 2:`) = fallthrough
            out.add("default: break");
            return true;
        }
        if (hasMidBranch(body)) return false;
        int lastOp = body.get(body.size() - 1).opcode();
        int lastOff = body.get(body.size() - 1).offset();
        // `break` sai do switch Kof e o epílogo (após `}`) roda (probe 09/09).
        // Sem ele, o braço cairia no próximo `case` (Kof não tem fallthrough
        // implícito como continuação — o caso casa e SAI; cair no texto
        // seguinte executaria o próximo braço por engano).
        boolean needBreak = false;
        if (lastOp == 0xa7 || lastOp == 0xc8) {
            if (gotoTarget(code, lastOff, lastOp) != end) return false;  // goto p/ fora do fim
            // junção vazia PROVADA: nada viaja no salto (shape Scala-match
            // com valor no join é recusado aqui, não no verificador).
            Integer d = simDepth(range(insns, target, lastOff), cp);
            if (d == null || d != 0) return false;
            needBreak = true;
        } else if (isReturnOp(lastOp)) {
            // saída antecipada legítima; o valor retornado deve ser o ÚNICO
            // na pilha (sem temporários vazados com side-effect perdido).
            Integer d = simDepth(range(insns, target, lastOff), cp);
            int want = (lastOp == 0xb1) ? 0 : 1;
            if (d == null || d != want) return false;
        } else {
            if (cappedEnd != end) return false;   // queda no meio = fallthrough
            Integer d = simDepth(body, cp);
            if (d == null || d != 0) return false;   // valores p/ o epílogo?
        }
        List<String> stmts = emitBody(code, insns, cp, frame, declared, target, cappedEnd);
        if (stmts == null) return false;
        if (isDefault) {
            out.add("default:");
        } else {
            int v = valueOf(si, target);
            out.add("case " + v + ":");
        }
        out.addAll(stmts);
        if (needBreak) out.add("break");
        return true;
    }

    /** Valor do case p/ o target (targets únicos por construção do javac). */
    private static int valueOf(SwitchInfo si, int target) {
        for (int i = 0; i < si.targets.length; i++) {
            if (si.targets[i] == target) return si.values[i];
        }
        return 0;
    }

    /**
     * Corpo via emitLinear sobre a região (o `goto` terminal é consumido
     * como parada, sem emissão — verificado pelo chamador).
     */
    private static List<String> emitBody(byte[] code, List<BytecodeReader.Insn> insns, String[] cp,
                                         BytecodeFrame frame, Set<Integer> declared,
                                         int start, int bodyEnd) {
        // recorta o goto terminal (emitLinear pararia nele de todo jeito,
        // mas o range cru protege contra cauda morta após o salto).
        int cut = bodyEnd;
        for (BytecodeReader.Insn in : insns) {
            int off = in.offset();
            if (off < start || off >= bodyEnd) continue;
            int op = in.opcode();
            if (op == 0xa7 || op == 0xc8) { cut = off; break; }
        }
        return BytecodeStatementsEmit.emitLinear(range(insns, start, cut), cp, frame, declared);
    }
}
