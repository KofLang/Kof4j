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

        String expr = BytecodeDecoder.linearExpr(range(insns, 0, swOff), cp, frame);
        if (expr == null || expr.contains("$")) return null;
        // discriminante deixa EXATAMENTE a chave na pilha (sem lixo/lixeira):
        // prova por simulação (trecho straight-line até o switch).
        Integer discDepth = simDepth(range(insns, 0, swOff), cp);
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

        List<String> out = new ArrayList<>();
        Set<Integer> declared = new HashSet<>();
        List<String> body = new ArrayList<>();
        body.add("switch (" + expr + ") {");
        // cases em ordem crescente de valor (determinístico); default por último.
        for (int oi = 0; oi < order.length; oi++) {
            int t = si.targets[order[oi]];
            if (!emitCase(code, insns, cp, frame, body, declared, si, t, false, end, maxOff, bounds))
                return null;
        }
        if (!emitCase(code, insns, cp, frame, body, declared, si, si.dflt, true, end, maxOff, bounds))
            return null;

        // epílogo APÓS `}` (os braços saem via `break`; cair aqui de dentro
        // do switch executaria o próximo braço por engano).
        List<String> tail = null;
        if (end != maxOff) {
            tail = emitBody(code, insns, cp, frame, declared, end, maxOff);
            if (tail == null) return null;
        }
        body.add("}");
        List<String> pre = hoistEscapeVars(body, tail);
        if (pre == null) return null;   // local escape com init não-literal → recusar
        out.addAll(pre);
        out.addAll(body);
        if (tail != null) out.addAll(tail);
        return out;
    }

    /**
     * §137 — escopo de `case`: em Kof, `var` declarado dentro de um braço
     * NÃO é visível depois do switch (nem nos outros braços). O javac declara
     * o local fora do switch (sem inicializador) e cada braço só faz store —
     * o `emitLinear` shared-`declared` traduz o PRIMEIRO store em `var name =
     * init`, que cai dentro do case 1 e some p/ o epílogo (`return v1` →
     * SEM011, o DecompileTest do statement-switch). Hoist: se um nome `var`
     * declarado num braço ESCAPE da região (usado noutro braço ou no
     * epílogo), mover a declaração p/ antes do `switch` e deixar um simples
     * `name = init` no braço (semântica idêntica: o init é literal — sem
     * side-effects nem ordem observável). Init não-literal (chamada/new) não
     * pode subir (executaria no caminho errado) → recusar o recovery (R6:
     * stub honesto, nunca código SEM011). Retorna as linhas `pre` (vazio se
     * nada escapa); null = recusar.
     */
    private static List<String> hoistEscapeVars(List<String> body, List<String> tail) {
        int close = body.size() - 1;           // índice do `}` final
        List<String> pre = new ArrayList<>();
        java.util.LinkedHashSet<String> done = new java.util.LinkedHashSet<>();
        for (int i = 1; i < close; i++) {
            String ln = body.get(i);
            if (!ln.startsWith("var ")) continue;
            int eq = ln.indexOf(" = ");
            if (eq < 0) continue;
            String name = ln.substring(4, eq);
            String init = ln.substring(eq + 3);
            boolean escapes = tail != null && mentions(tail.toString(), name);
            if (!escapes) {
                for (int j = i + 1; j < close; j++) {
                    if (mentions(body.get(j), name)) { escapes = true; break; }
                }
            }
            if (!escapes) continue;
            if (!isLiteral(init)) return null;
            if (done.add(name)) {
                pre.add(ln);
                body.set(i, name + " = " + init);
            }
        }
        return pre;
    }

    /** Menção por LIMITE de token (v1 não casa dentro de v12). */
    private static boolean mentions(String line, String name) {
        int from = 0;
        while (true) {
            int k = line.indexOf(name, from);
            if (k < 0) return false;
            char before = k == 0 ? ' ' : line.charAt(k - 1);
            int e = k + name.length();
            char after = e < line.length() ? line.charAt(e) : ' ';
            if (!Character.isLetterOrDigit(before) && before != '_'
                    && !Character.isLetterOrDigit(after) && after != '_') return true;
            from = k + 1;
        }
    }

    /** Literal puro (string/numérico/booleano/null): um token, sem chamada. */
    private static boolean isLiteral(String init) {
        if (init.isEmpty() || init.contains(" ") || init.contains("(")
                || init.contains("+")) return false;
        char c = init.charAt(0);
        if (c == '"') return init.length() > 1 && init.endsWith("\"");
        return c == '-' || (c >= '0' && c <= '9') || c == 't' || c == 'f' || c == 'n'; // true/false/null
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
            String[] m = BytecodeDecoder.resolveMethodRef(cp, in.operands().length > 0 ? in.operands()[0] : -1);
            if (m == null) return null;
            int argc = BytecodeDecoder.argCount(m[2]);
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
        return BytecodeStatements.emitLinear(range(insns, start, cut), cp, frame, declared);
    }
}
