package dev.kof.cli;

import java.util.List;
import java.util.Set;

/**
 * Recuperacao estrutural do walker (Fase C, degrau 2c).
 *
 * <p>O bug §238: o caminho `pureIfElse`/`pureIfThen` do
 * {@link BytecodeStatements#struct} emite o `var` de um local na SUA PRIMEIRA
 * atribuicao. Quando essa primeira escrita fica DENTRO de um ramo do
 * `if`/`else` e o valor e lido DEPOIS do join, o `var` sai escopado dentro do
 * ramo e o pos-join vira `SEM000 Undefined variable` — saida que NAO
 * recompila, violando a lei R6 (nunca codigo errado/nao-compilavel). Exemplo
 * javac: {@code int s; if (m==0) { s=10; } else { s=20; } return s;}.
 *
 * <p>A correcao e icar (hoist) a declaracao para ANTES do `if`, com um
 * default pelo TIPO da store, apenas para slots que (a) sao escritos por um
 * `xstore` dentro dos ramos e (b) AINDA NAO estao em `declared` (nao foram
 * inicializados antes do if). Slots ja declarados (init pre-if, como o
 * `int r = 1` do teste E.java) nao sao tocados = saida byte-identica.
 *
 * <p>Honestidade R6 (sem falso-verde): so icamos tipos com default
 * SEMANTICO seguro — {@code Int→0}, {@code Long→0L}, {@code Double→0.0}. Um
 * local escrito como {@code float} (fstore) ou referencia (astore) NAO tem
 * default seguro a emitir aqui (float literal drifta — licao bug 62; e o tipo
 * de referencia e desconhecido) — nesses casos RECUSAMOS o shape inteiro, e o
 * chamador degrada p/ o stub honesto (melhor UNKNOWN que nao-compilavel).
 */
final class StructWalker {

    private StructWalker() {}

    /** Resultado do hoist: null = recusar (ha local nao-seguro escapando);
     *  lista (mesmo vazia) = linha(s) `var ...` a emitir antes do `if` ja
     *  com os slots marcados em {@code declared} (efeito colateral intencional:
     *  o subsequent struct() dos ramos passa a so atribuir, sem re-`var`). */
    static List<String> hoistEscapingLocals(BytecodeReader.Block thenB,
                                            BytecodeReader.Block elseB,
                                            List<BytecodeReader.Insn> insns,
                                            BytecodeFrame frame,
                                            Set<Integer> declared) {
        java.util.List<String> hoisted = new java.util.ArrayList<>();
        java.util.LinkedHashSet<Integer> seen = new java.util.LinkedHashSet<>();
        for (BytecodeReader.Block b : new BytecodeReader.Block[]{thenB, elseB}) {
            if (b == null) continue;
            for (BytecodeReader.Insn in : BytecodeDecoder.insnsWithin(b, insns)) {
                int slot = storeSlot(in);
                if (slot < 0 || declared.contains(slot) || seen.contains(slot)) continue;
                // Fase E (14/10): slot NOMEADO (parâmetro/this) NUNCA hoista —
                // o `var arg0 = 0` do default sombreava o parâmetro e o loop
                // rodava para sempre (medido no collatz; R6)
                if (frame.isNamedSlot(slot)) continue;
                String def = storeDefault(in.opcode());
                if (def == null) return null;                 // float/ref: recusar (R6)
                seen.add(slot);
                hoisted.add("var " + BytecodeDecoderStack.slotName(slot, frame) + " = " + def);
            }
        }
        declared.addAll(seen);
        return hoisted;
    }

    /**
     * Fase E (15/10): o hoist PROFUNDO para a cadeia else-if SEM init —
     * `String s;` do Java NÃO gera store no prólogo; o primeiro `var` nascia
     * DENTRO do primeiro braço e os demais atribuíam slot não-declarado
     * (SEM011, medido no Range.size). O scan é por RANGE de offsets: o
     * then-subtree e o else-subtree ocupam [start, tail) contíguos no
     * bytecode, então um único varredura pega os stores ANINHADOS que o
     * hoist raso (insnsWithin do bloco imediato) não vê. Um slot é hoistado
     * quando armazenado em AMBOS os lados (escapa do if); o default vem do
     * opcode; qualquer divergência de tipo/parametro nomeado → null (R6).
     */
    static java.util.List<String> hoistEscapingLocalsDeep(List<BytecodeReader.Insn> insns,
                                                          int thenLo, int thenHi,
                                                          int elseLo, int elseHi,
                                                          BytecodeFrame frame,
                                                          Set<Integer> declared) {
        java.util.LinkedHashSet<Integer> thenSlots = new java.util.LinkedHashSet<>();
        java.util.LinkedHashSet<Integer> elseSlots = new java.util.LinkedHashSet<>();
        for (BytecodeReader.Insn in : insns) {
            int off = in.offset();
            int slot = storeSlot(in);
            if (slot < 0) continue;
            if (off >= thenLo && off < thenHi) thenSlots.add(slot);
            if (off >= elseLo && off < elseHi) elseSlots.add(slot);
        }
        thenSlots.retainAll(elseSlots);            // só o que escapa
        java.util.List<String> hoisted = new java.util.ArrayList<>();
        for (int slot : thenSlots) {
            if (declared.contains(slot)) continue;
            if (frame.isNamedSlot(slot)) continue; // a lei do named-slot (R6)
            // o default precisa ser consistente nos DOIS lados (o opcode do
            // store determina; qualquer mistura de categoria → recusa)
            String def = null;
            boolean sawStore = false;
            for (int k = 0; k < insns.size(); k++) {
                BytecodeReader.Insn in = insns.get(k);
                int off = in.offset();
                boolean inThen = off >= thenLo && off < thenHi;
                boolean inElse = off >= elseLo && off < elseHi;
                if (!inThen && !inElse) continue;
                if (storeSlot(in) != slot) continue;
                sawStore = true;
                int op = in.opcode();
                if (op == 0x3a || (op >= 0x4b && op <= 0x4e)) {
                    // Fase E (15/10): o default de REFERÊNCIA não existe
                    // honestamente (SEM048 bane o literal null) — EXCETO
                    // quando TODOS os valores armazenados são LITERAIS
                    // String (o `ldc` imediatamente antes): o `""` é o
                    // default do TIPO REAL medido. Qualquer outra fonte →
                    // recusa (R6).
                    if (k == 0) return null;
                    BytecodeReader.Insn prev = insns.get(k - 1);
                    if (prev.opcode() != 0x12 && prev.opcode() != 0x13) return null;
                    String lit = BytecodeCp.ldc(cpRef, prev.operands()[0]);
                    if (lit == null || !lit.startsWith("\"")) return null;
                    if (def != null && !def.equals("\"\"")) return null;
                    def = "\"\"";
                } else {
                    String d = storeDefault(op);
                    if (d == null) return null;
                    if (def != null && !def.equals(d)) return null;
                    def = d;
                }
            }
            if (!sawStore || def == null) continue;
            declared.add(slot);
            hoisted.add("var " + BytecodeDecoderStack.slotName(slot, frame) + " = " + def);
        }
        return hoisted;
    }

    /** O pool de constantes p/ o hoist profundo (o ldc dos literais). */
    static String[] cpRef;

    /** Slot que o insn escreve (store), ou -1 se nao e store. */
    private static int storeSlot(BytecodeReader.Insn in) {
        int op = in.opcode();
        int[] o = in.operands();
        if (op == 0x36) return o[0];                          // istore idx
        if (op == 0x37) return o[0];                          // lstore idx
        if (op == 0x38) return o[0];                          // fstore idx
        if (op == 0x39) return o[0];                          // dstore idx
        if (op == 0x3a) return o[0];                          // astore idx
        if (op >= 0x3b && op <= 0x3e) return op - 0x3b;       // istore_0..3
        if (op >= 0x3f && op <= 0x42) return op - 0x3f;       // lstore_0..3
        if (op >= 0x43 && op <= 0x46) return op - 0x43;       // fstore_0..3
        if (op >= 0x47 && op <= 0x4a) return op - 0x47;       // dstore_0..3
        if (op >= 0x4b && op <= 0x4e) return op - 0x4b;       // astore_0..3
        return -1;
    }

    /** Default literal seguro por categoria de store; null = nao icar. */
    private static String storeDefault(int op) {
        if (op == 0x36 || (op >= 0x3b && op <= 0x3e)) return "0";      // int
        if (op == 0x37 || (op >= 0x3f && op <= 0x42)) return "0L";     // long
        if (op == 0x39 || (op >= 0x47 && op <= 0x4a)) return "0.0";    // double
        return null;                                                   // fstore/astore
    }
}
