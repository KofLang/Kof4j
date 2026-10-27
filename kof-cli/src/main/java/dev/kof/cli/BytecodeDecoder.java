package dev.kof.cli;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reconstrução de expressão Kof a partir do bytecode (decompiler, Fase C/E).
 *
 * Opera sobre a lista de instruções do {@link BytecodeReader} com uma pilha
 * simbólica: constantes, loads, aritmética → expressão; comparações booleanas
 * e if/else de retorno → if-expression idiomática. Fora do subconjunto,
 * devolve {@code null} (o decompiler cai no stub honesto — nunca inventa).
 */
 final class BytecodeDecoder {

    /**
     * Diagnóstico da fila Fase E: quando não-nulo, recebe cada opcode que faz
     * a recuperação desistir (default → null). Nulo em produção (zero custo).
     * Usado só por medidores offline (ver DECOMPILER.md §Fase E).
     */
    static java.util.function.IntConsumer blockerSink;

    /**
     * Fase G (diamond super.m(), mantenedora 14/10): o cp achatado perde o
     * TAG (Methodref vs InterfaceMethodref) — o contexto da classe corrente
     * vem do Decompile por rodada: `invokespecial` não-<init> cujo owner é a
     * SUPERCLASSE corrente emite `super.m(args)` (Kof suporta — medido);
     * owner que é interface (`A.super.m()`) é o refinamento de default.
     */
    static String currentSuperInternal;

    private BytecodeDecoder() {
    }

    /** Devolve a expressão do corpo, {@code ""} para vazio, ou {@code null} se não recuperável. */
    static String recoverExpression(byte[] code, String[] cp, BytecodeFrame frame) {
        List<BytecodeReader.Insn> insns = BytecodeReader.decode(code);
        if (insns.isEmpty()) return null;

        String lin = linearReturn(insns, cp, frame);
        if (lin != null) return lin;
        String cmp = BytecodeDecoderCond.comparisonReturn(insns, frame);
        if (cmp != null) return cmp;
        String va = BytecodeDecoderCond.varargsCall(insns, cp, frame);
        if (va != null) return va;
        return BytecodeDecoderCond.ifElseReturn(insns, cp, frame);
    }

    /**
     * Expressão pura da pilha (Fase C, discriminante de switch): texto do topo
     * se restar EXATAMENTE 1 valor, senão null. Sem checagem contra o tipo de
     * retorno (o discriminante raramente tem o tipo do retorno — usar
     * `linearReturn` aqui recusava todo switch não-String-em-String).
     * `return`/`throw` no prefixo = switch inalcançável → recusar.
     */
    static String linearExpr(List<BytecodeReader.Insn> insns, String[] cp, BytecodeFrame frame) {
        for (BytecodeReader.Insn in : insns) {
            int op = in.opcode();
            if ((op >= 0xac && op <= 0xb1) || op == 0xbf) return null;
        }
        return linearReturn(insns, cp, frame, true);
    }

    // ── linear: pilha simbólica → value no return ────────────────────────

    static String linearReturn(List<BytecodeReader.Insn> insns, String[] cp,
                                       BytecodeFrame frame) {
        return linearReturn(insns, cp, frame, false);
    }

    /**
     * @param laxType true = ignora o tipo de retorno no final (só exige 1
     * valor na pilha); usado SÓ pelo `linearExpr` (discriminante). Chamadores
     * existentes usam o modo estrito — comportamento byte-idêntico.
     */
    static String linearReturn(List<BytecodeReader.Insn> insns, String[] cp,
                                       BytecodeFrame frame, boolean laxType) {
        MachineRun r = machineRun(insns, cp, frame);
        if (r == null) return null;
        if (r.stopped) return r.returned;
        // fim sem return: devolve o topo da pilha (região protegida de try)
        if (laxType) {
            return r.stack.size() == 1 ? r.stack.topExpr() : null;
        }
        return r.stack.size() == 1 ? r.stack.retTyped(frame.retType()) : null;
    }

    /** Estado da pilha simbólica após a máquina de expressão (degrau 3,
     *  13/09): exposto p/ o walker de pós-dominadores reusar a MESMA máquina
     *  — um segundo interpretador divergiria (lição trap 3/contFor%2).
     *  `stopped` = chegou em `return` (a pilha final NÃO reavalia: o
     *  `retTyped` do return já decidiu, inclusive por null — byte-idêntico). */
    static final class MachineRun {
        final BytecodeTypes.TStack stack;
        final String returned;
        final boolean stopped;
        MachineRun(BytecodeTypes.TStack stack, String returned, boolean stopped) {
            this.stack = stack; this.returned = returned; this.stopped = stopped;
        }
    }

    static MachineRun machineRun(List<BytecodeReader.Insn> insns, String[] cp,
                                       BytecodeFrame frame) {
        return machineRun(insns, cp, frame, null);
    }

    static MachineRun machineRun(List<BytecodeReader.Insn> insns, String[] cp,
                                       BytecodeFrame frame, java.util.Map<Integer, String[]> seed) {
        // Fase E (14/10): memória de LOCAIS da fatia (o binding do
        // `instanceof String s` compila p/ checkcast+astore+aload); só
        // referências (o tipo "L" basta p/ os usos no braço).
        // Fase E (15/10): o SEED — os braços do ternário avaliam com o mapa
        // do run EXTERNO (medido no verdict: o `g` do astore_1 virava o
        // `v1` fantasma no braço else = SEM011).
        java.util.Map<Integer, String[]> locals = seed != null
                ? new java.util.HashMap<>(seed) : new java.util.HashMap<>();
        BytecodeTypes.TStack stack = new BytecodeTypes.TStack();
        // Fase E (14/10): o TERNÁRIO-EM-ARGUMENTO — branches no MEIO de uma
        // expressão linear (`money`: `.. < 10 ? "0" : ""` alimentando o
        // invokedynamic). O pcJump consome os 2 braços avaliados e salta p/
        // o join.
        int pcJump = -1;
        for (BytecodeReader.Insn in : insns) {
            if (pcJump >= 0) {
                if (in.offset() < pcJump) continue;
                pcJump = -1;
            }
            int op = in.opcode();
            switch (op) {
                case 0x01 -> stack.push("null", "L");                 // aconst_null
                case 0x02 -> stack.push("-1", "I");
                case 0x03, 0x04, 0x05, 0x06, 0x07, 0x08 -> stack.push(String.valueOf(op - 0x03), "I");
                // lconst/dconst: tipo embutido no opcode (lição bug 62 — só
                // emitir quando não pode driftar). fconst (0x0b-0x0d) recusado.
                case 0x09 -> stack.push("0L", "J");
                case 0x0a -> stack.push("1L", "J");
                case 0x0b, 0x0c, 0x0d -> { return null; }
                case 0x0e -> stack.push("0.0", "D");
                case 0x0f -> stack.push("1.0", "D");
                case 0x10 -> stack.push(String.valueOf((byte) in.operands()[0]), "I");
                case 0x11 -> stack.push(String.valueOf((short) in.operands()[0]), "I");
                case 0x12, 0x13 -> {                               // ldc, ldc_w (mesmo CP, índice u1/u2)
                    String c = BytecodeCp.ldc(cp, in.operands()[0]);
                    if (c == null) return null;
                    stack.push(c, c.startsWith("\"") ? "L" : "I");   // String vs Integer
                }
                case 0x14 -> {
                    String c = BytecodeCp.ldc2(cp, in.operands()[0]);
                    if (c == null) return null;
                    stack.push(c, c.endsWith("L") ? "J" : "D");
                }
                case 0x1a, 0x1b, 0x1c, 0x1d -> stack.push(BytecodeDecoderStack.slotName(op - 0x1a, frame), "I");
                case 0x1e, 0x1f, 0x20, 0x21 -> stack.push(BytecodeDecoderStack.slotName(op - 0x1e, frame), "J");   // lload_0..3
                case 0x26, 0x27, 0x28, 0x29 -> stack.push(BytecodeDecoderStack.slotName(op - 0x26, frame), "D");   // dload_0..3
                case 0x2a, 0x2b, 0x2c, 0x2d -> {
                    int slot = op - 0x2a;
                    if (locals.containsKey(slot)) stack.push(locals.get(slot)[0], locals.get(slot)[1]);
                    else stack.push(BytecodeDecoderStack.slotName(slot, frame), "L");
                }
                case 0x15 -> stack.push(BytecodeDecoderStack.slotName(in.operands()[0], frame), "I");
                case 0x19 -> stack.push(BytecodeDecoderStack.slotName(in.operands()[0], frame), "L");
                case 0x16 -> stack.push(BytecodeDecoderStack.slotName(in.operands()[0], frame), "J");   // lload
                case 0x17 -> { return null; }                                       // fload (fconst já recusado)
                case 0x18 -> stack.push(BytecodeDecoderStack.slotName(in.operands()[0], frame), "D");   // dload
                case 0x60 -> { if (!stack.bin("+", "I")) return null; }
                case 0x64 -> { if (!stack.bin("-", "I")) return null; }
                case 0x68 -> { if (!stack.bin("*", "I")) return null; }
                case 0x6c -> { if (!stack.bin("/", "I")) return null; }
                case 0x70 -> { if (!stack.bin("%", "I")) return null; }
                case 0x61 -> { if (!stack.bin("+", "J")) return null; }
                case 0x65 -> { if (!stack.bin("-", "J")) return null; }
                case 0x69 -> { if (!stack.bin("*", "J")) return null; }
                case 0x6d -> { if (!stack.bin("/", "J")) return null; }
                case 0x71 -> { if (!stack.bin("%", "J")) return null; }
                case 0x63 -> { if (!stack.bin("+", "D")) return null; }
                case 0x67 -> { if (!stack.bin("-", "D")) return null; }
                case 0x6b -> { if (!stack.bin("*", "D")) return null; }
                case 0x6f -> { if (!stack.bin("/", "D")) return null; }
                case 0x73 -> { if (!stack.bin("%", "D")) return null; }
                case 0x74 -> { if (!stack.mono("-%s", "I", "I")) return null; }         // ineg (paridade: -atom)
                case 0x75 -> { if (!stack.mono("(-%s)", "J", "J")) return null; }       // lneg
                case 0x77 -> { if (!stack.mono("(-%s)", "D", "D")) return null; }       // dneg
                case 0x85 -> { if (!stack.mono("(%s as Long)", "I", "J")) return null; }    // i2l
                case 0x87 -> { if (!stack.mono("(%s as Double)", "I", "D")) return null; }  // i2d
                case 0x88 -> { if (!stack.mono("(%s as Int)", "J", "I")) return null; }     // l2i
                case 0x8a -> { if (!stack.mono("(%s as Double)", "J", "D")) return null; }  // l2d
                case 0x8e -> { if (!stack.mono("(%s as Int)", "D", "I")) return null; }     // d2i
                case 0x8f -> { if (!stack.mono("(%s as Long)", "D", "J")) return null; }    // d2l
                // Fase E (22/10): i2c/i2b/i2s — os tres truncam fielmente
                // desde o §695/#471 (o JVM emite i2b/i2s/i2c reais para
                // `as Byte`/`as Short`/`as Char`; x86 e riscv idem). O
                // comentario antigo de "i2b/i2s NAO tem equivalente"
                // descrevia o codigo PRE-§695. Medido: 300 as Byte=44,
                // 256 as Byte=0, -1 as Byte=-1, 70000 as Short=4464.
                case 0x91 -> { if (!stack.mono("(%s as Byte)", "I", "I")) return null; }    // i2b
                case 0x92 -> { if (!stack.mono("(%s as Char)", "I", "I")) return null; }    // i2c
                case 0x93 -> { if (!stack.mono("(%s as Short)", "I", "I")) return null; }   // i2s
                case 0xb8 -> { // invokestatic
                    String[] m = BytecodeCp.resolveMethodRef(cp, in.operands()[0]);
                    if (m == null) return null;
                    String a = stack.args(BytecodeCp.argCount(m[2]));
                    if (a == null) return null;
                    String mapped = BytecodeStdlib.statics(m[0], m[1], a, m[2]);
                    if (mapped == null && BytecodeCp.isJdkOwner(m[0])) return null;   // R6: owner JDK não-idiomático
                    stack.push(mapped != null ? mapped : BytecodeCp.simpleOwner(m[0]) + "." + m[1] + "(" + a + ")",
                            BytecodeCp.retOf(m[2]));
                }
                case 0xb6, 0xb9 -> { // invokevirtual / invokeinterface
                    String[] m = BytecodeCp.resolveMethodRef(cp, in.operands()[0]);
                    if (m == null) return null;
                    // Fase E (14/10): o overload INT de indexOf/lastIndexOf
                    // (char) não tem equivalente — o do Kof é String-param
                    // (SEM051 medida: o verbatim `lastIndexOf(46)` não
                    // compila). Recusar ANTES do pass-through.
                    if ("java/lang/String".equals(m[0])
                            && ((("indexOf".equals(m[1]) || "lastIndexOf".equals(m[1]))
                            && !m[2].startsWith("(Ljava/lang/String;"))
                            || "strip".equals(m[1]) || "stripLeading".equals(m[1]) || "stripTrailing".equals(m[1]))) return null;
                    String a = stack.args(BytecodeCp.argCount(m[2]));
                    if (a == null) return null;
                    String recv = stack.popExpr();
                    if (recv == null) return null;
                    String mapped = BytecodeStdlib.virtual(recv, m[0], m[1], a);
                    if (mapped == null && recv.startsWith("⟦new⟧")) return null;  // R6: método em novo Objeto JDK não-mapeado
                    stack.push(mapped != null ? mapped : recv + "." + m[1] + "(" + a + ")", BytecodeCp.retOf(m[2]));
                }
                case 0xb4 -> { // getfield
                    String[] f = BytecodeCp.resolveMethodRef(cp, in.operands()[0]);
                    String obj = stack.popExpr();
                    if (f == null || obj == null) return null;
                    stack.push(obj + "." + f[1], BytecodeCp.retOf(f[2]));
                }
                case 0xb2 -> { // getstatic
                    String[] f = BytecodeCp.resolveMethodRef(cp, in.operands()[0]);
                    if (f == null) return null;
                    stack.push(BytecodeCp.simpleOwner(f[0]) + "." + f[1], BytecodeCp.retOf(f[2]));
                }
                case 0xba -> { // invokedynamic — String concat OU lambda
                    int idynIdx = in.operands()[0];
                    String idyn = idynIdx < cp.length ? cp[idynIdx] : null;
                    if (idyn != null && idyn.startsWith("LAMBDA:")
                            && BytecodeConcat.LAMBDA_TEXT.containsKey(idynIdx)) {
                        stack.push(BytecodeConcat.LAMBDA_TEXT.get(idynIdx), "L");
                        continue;
                    }
                    String rec = BytecodeConcat.recipe(cp, in.operands()[0]);
                    if (rec == null) return null;
                    int n = 0;
                    for (int i = 0; i < rec.length(); i++) if (rec.charAt(i) == 1) n++;
                    // BUG DE CORRECAO (medido 13/10: concat3 dava "cba"): o
                    // apply() JA desempilha a ordem original — o caller NAO
                    // deve re-inverter (o add(0,..) + o Deque dupla-invertiam).
                    // Coleta em ordem de PILHA (topo primeiro) e o apply poe
                    // em ordem original.
                    java.util.Deque<String> vals = new java.util.ArrayDeque<>();
                    // addLast: o pop() do ArrayDeque e removeFirst — o apply
                    // espera o ULTIMO arg na cabeca (ele re-inverte com
                    // add(0,pop) para montar a ordem original). Medido: push
                    // (=addFirst) dava "cba"; addLast da "abc".
                    for (int i = 0; i < n; i++) vals.addLast(stack.popExpr());
                    if (vals.stream().anyMatch(java.util.Objects::isNull)) return null;
                    String expr = BytecodeConcat.apply(vals, rec);
                    if (expr == null) return null;
                    stack.push(expr, "L");
                }
                case 0xbb -> { // new — só p/ constructors de classes DE DOMÍNIO;
                    // new java.lang.X(...) nunca é idiomático p/ Kof (R6).
                    String cn = BytecodeCp.resolveClassName(cp, in.operands()[0]);
                    if (cn == null || BytecodeCp.isJdkClass(cp, in.operands()[0])) return null;
                    // §7 degrau 3: registra uso cross-package p/ import
                    // (emissão inalterada — nome simples como antes).
                    if (frame != null && frame.treeScope != null) {
                        String internal = BytecodeKofTypes.indexInternalName(cp, in.operands()[0]);
                        if (internal != null) frame.treeScope.resolve(internal);
                    }
                    stack.push("⟦new⟧" + cn, "L");
                }
                case 0x59 -> { // dup (só no padrão new)
                    String t = stack.topExpr();
                    if (t == null || !t.startsWith("⟦new⟧")) return null;
                    stack.dup();
                }
                case 0xb7 -> { // invokespecial (<init>)
                    String[] m = BytecodeCp.resolveMethodRef(cp, in.operands()[0]);
                    if (m == null) return null;
                    if (!"<init>".equals(m[1])) {
                        // Fase G: `super.m(args)` explícito — a invocação da
                        // superclasse é expressável em Kof (`super.m()` medido
                        // rodando); owner que NÃO é a super corrente = interface
                        // (`A.super.m()`) → não recuperado AQUI (o inline
                        // multi-classfile é a fatia seguinte — nunca fabricar).
                        if (currentSuperInternal != null && currentSuperInternal.equals(m[0])) {
                            String a = stack.args(BytecodeCp.argCount(m[2]));
                            if (a == null) return null;
                            String recv = stack.popExpr();
                            if (!"this".equals(recv)) return null;
                            stack.push("super." + m[1] + "(" + a + ")", "L");
                            continue;
                        }
                        return null;
                    }
                    String a = stack.args(BytecodeCp.argCount(m[2]));
                    if (a == null || stack.size() < 2) return null;
                    stack.pop();                       // receiver (cópia do dup)
                    String result = stack.popExpr();   // marcador do new
                    if (result == null || !result.startsWith("⟦new⟧")) return null;
                    stack.push(result.substring("⟦new⟧".length()) + "(" + a + ")", "L");
                }
                case 0x3a, 0x4b, 0x4c, 0x4d, 0x4e -> {   // astore (binding; JVMS: astore_0=0x4b)
                    if (stack.isEmpty()) return null;
                    int slot = op == 0x3a ? in.operands()[0] : op - 0x4b;
                    String v = stack.popExpr();
                    // Fase E (15/10): o inline de um CONCAT sem parênteses
                    // quebra a PRECEDÊNCIA quando uma call anda em cima
                    // (medido no Sess.token: `raw = user + ":" + now` +
                    // `.hashCode()` emitia `arg1.hashCode()` — o hashCode
                    // anexava ao ÚLTIMO ARG). O store guarda PARENTEZADO.
                    if (v.contains(" + ")) v = "(" + v + ")";
                    locals.put(slot, new String[]{v, "L"});
                }
                case 0xc0 -> {                            // checkcast → `as`
                    String ct = BytecodeKofTypes.inlineKofType(cp, in.operands()[0]);
                    if (ct == null) {
                        ct = BytecodeKofTypes.indexKofType(cp, in.operands()[0], frame);
                        if (ct == null) return null;
                    }
                    if (stack.isEmpty()) return null;
                    String v = stack.popExpr();
                    stack.push(ct.equals("Object") ? v : "(" + v + " as " + ct + ")", "L");
                }
                case 0x99, 0x9a, 0x9b, 0x9c, 0x9d, 0x9e, 0x9f, 0xa0, 0xa1, 0xa2, 0xa3, 0xa4, 0xa5, 0xa6, 0xc6, 0xc7 -> {
                    // Fase E (14/10): TERNÁRIO-EM-ARGUMENTO — o branch no meio
                    // da expressão ([operandos, ifXX T, then..., goto J,
                    // else..., J]). Os 2 braços avaliam standalone (insns
                    // autocontidas); qualquer desvio → null = stub (R6).
                    int t = in.target();
                    int myIdx = -1;
                    for (int k = 0; k < insns.size(); k++)
                        if (insns.get(k).offset() == in.offset()) { myIdx = k; break; }
                    if (myIdx < 0) return null;
                    int gIdx = -1;
                    int join = -1;
                    for (int k = myIdx + 1; k < insns.size(); k++) {
                        BytecodeReader.Insn g = insns.get(k);
                        if (g.offset() >= t) break;
                        if (g.opcode() == 0xa7 && g.target() > t) { gIdx = k; join = g.target(); break; }
                    }
                    if (gIdx < 0 || join <= t) return null;
                    List<BytecodeReader.Insn> thenSeq = insns.subList(myIdx + 1, gIdx);
                    List<BytecodeReader.Insn> elseSeq = new java.util.ArrayList<>();
                    for (BytecodeReader.Insn g : insns)
                        if (g.offset() >= t && g.offset() < join) elseSeq.add(g);
                    if (thenSeq.isEmpty() || elseSeq.isEmpty()) return null;
                    MachineRun ra = machineRun(thenSeq, cp, frame, locals);
                    if (ra == null || ra.stopped || ra.stack.size() != 1) return null;
                    MachineRun rb = machineRun(elseSeq, cp, frame, locals);
                    if (rb == null || rb.stopped || rb.stack.size() != 1) return null;
                    int arity = (op >= 0x9f && op <= 0xa4) ? 2 : 1;
                    if (stack.size() < arity) return null;
                    String c;
                    if (arity == 2) {
                        String b2 = stack.popExpr();
                        String a2 = stack.popExpr();
                        String iv = BytecodeCp.invCond(op);
                        if (iv == null) return null;
                        c = a2 + " " + iv + " " + b2;
                    } else {
                        String v = stack.popExpr();
                        // Fase E (24/10): a POLARIDADE do 1-operando no shape
                        // [ifXX salta ao else, queda = then, goto ao join] —
                        // a condição da QUEDA é o não-salto: o ifeq (salta
                        // quando v==0) deixa a queda em v!=0, e o `v` nu é
                        // equivalente sob o truthiness do Kof; o ifne (salta
                        // quando v!=0, o javac usa no `x == 0 ? A : B`)
                        // deixa a queda em v==0 — o `v` nu era CÓDIGO
                        // ERRADO (o label do Fizz: `n % 15 == 0 ?`
                        // saía invertido — medido: label(9) dava
                        // "FizzBuzz" onde o Java dá "Fizz"); o ifne leva
                        // o inv dele (`== 0`), como os null-comps levam
                        // o seu (`== null`/`!= null`).
                        if (op == 0x99) c = v;
                        else c = v + " " + BytecodeCp.invCond(op);
                    }
                    stack.push("(if (" + c + ") " + ra.stack.topExpr() + " else " + rb.stack.topExpr() + ")", "L");
                    pcJump = join;
                }
                case 0xac -> { return new MachineRun(stack, stack.retTyped("I"), true); }
                case 0xad -> { return new MachineRun(stack, stack.retTyped("J"), true); }
                case 0xae -> { return new MachineRun(stack, stack.retTyped("F"), true); }
                case 0xaf -> { return new MachineRun(stack, stack.retTyped("D"), true); }
                case 0xb0 -> { return new MachineRun(stack, stack.retTyped("L"), true); }
                case 0xb1 -> {
                    // §CodeQL deref-null: guard defensivo igual ao da 0xb8
                    // (linha 216). frame nunca chega null pelos callers reais
                    // (recoverExpression/machineRun sempre recebem new
                    // BytecodeFrame), mas a API é package-private; sem frame
                    // não dá para checar o tipo de retorno, então trata-se
                    // como void-return (mesma saida do "return" simples).
                    if (frame != null && !"V".equals(frame.retType())) return null;   // return em método não-void → drift
                    return new MachineRun(stack, stack.isEmpty() ? "" : null, true);
                }
                default -> {
                    if (blockerSink != null) blockerSink.accept(op);
                    return null;
                }
            }
        }
        return new MachineRun(stack, null, false);
    }

    // ── comparação booleana de retorno ───────────────────────────────────


    static BytecodeReader.Block find(List<BytecodeReader.Block> blocks, int start) {
        for (BytecodeReader.Block b : blocks) if (b.start == start) return b;
        return null;
    }

    static List<BytecodeReader.Insn> insnsWithin(BytecodeReader.Block b, List<BytecodeReader.Insn> insns) {
        List<BytecodeReader.Insn> block = new java.util.ArrayList<>();
        for (BytecodeReader.Insn in : insns) {
            if (in.offset() >= b.start && in.offset() < b.end) block.add(in);
        }
        return block;
    }

    /**
     * Fase C (13/10, `||`): a condição com o operador TAKEN (não-invertido) —
     * para o teste cujo JUMP é o braço VERDADEIRO (a orientação do `||`).
     * Espelho do blockCondition (que inverte).
     */
    static String blockConditionTaken(BytecodeReader.Block entry, List<BytecodeReader.Insn> insns,
                                      BytecodeFrame frame, String[] cp) {
        List<BytecodeReader.Insn> block = insnsWithin(entry, insns);
        if (block.isEmpty()) return null;
        BytecodeReader.Insn last = block.get(block.size() - 1);
        if (!last.isCond()) return null;
        String op = BytecodeCp.takenCond(last.opcode());
        if (op == null) return null;
        int arity = (last.opcode() >= 0x9f && last.opcode() <= 0xa4) ? 2 : 1;
        if (block.size() - 1 < arity) return null;
        if (block.size() - 1 == arity) {
            List<String> operands = new java.util.ArrayList<>();
            for (int i = 0; i < block.size() - 1; i++) {
                String v = BytecodeDecoderStack.loadValue(block.get(i), frame, cp);
                if (v == null) return null;
                operands.add(v);
            }
            return operands.get(0) + " " + op + (arity == 2 ? " " + operands.get(1) : "");
        }
        // Fase E (16/10): o PAR-CMP GERAL no TAKEN — o bloco
        // [ops..., lcmp/dcmpl/dcmpg, ifXX] com o cmp DIRETAMENTE antes do
        // ifXX: o avaliador fechado avalia o prefixo e deixa EXATAMENTE 2
        // valores → `x takenOp y` (a direção do JUMP; medido no
        // isRightAngle: `normalize(deg) == 90 || normalize(deg) == 270` —
        // o dcmpl + ldc2_w sem isto = null). Stores no prefixo = prólogo
        // (statement) → recusa (a pureza do 15/10; o fusion-path cuida).
        int lastOpT = block.get(block.size() - 1).opcode();
        if (lastOpT >= 0x99 && lastOpT <= 0x9e && block.size() >= 3) {
            int cmpIdxT = block.size() - 2;
            int co = block.get(cmpIdxT).opcode();
            if (co == 0x94 || co == 0x97 || co == 0x98) {
                String takenOp = switch (lastOpT) {
                    case 0x99 -> "=="; case 0x9a -> "!="; case 0x9b -> "<";
                    case 0x9c -> ">="; case 0x9d -> "<="; case 0x9e -> ">";
                    default -> null;
                };
                if (takenOp != null) {
                    java.util.Deque<String> stT = new java.util.ArrayDeque<>();
                    boolean pureT = true;
                    for (BytecodeReader.Insn in : block.subList(0, cmpIdxT)) {
                        int sop = in.opcode();
                        if (sop >= 0x36 && sop <= 0x4e) { pureT = false; break; }
                    }
                    if (pureT && evalInto(block.subList(0, cmpIdxT), frame, cp, stT)
                            && stT.size() == 2) {
                        String bT = stT.pop();
                        String aT = stT.pop();
                        return aT + " " + takenOp + " " + bT;
                    }
                    return null;
                }
            }
        }
        return testExpr(block.subList(0, block.size() - 1), frame, arity, op, cp);
    }

    static String blockCondition(BytecodeReader.Block entry, List<BytecodeReader.Insn> insns,
                                         BytecodeFrame frame, String[] cp) {
        List<BytecodeReader.Insn> block = insnsWithin(entry, insns);
        if (block.isEmpty()) return null;
        BytecodeReader.Insn last = block.get(block.size() - 1);
        if (!last.isCond()) return null;
        String inv = BytecodeCp.invCond(last.opcode());
        if (inv == null) return null;
        // Aridade EXATA (regressão travada 13/09, contFor %2): bloco
        // [iload, iconst, irem, ifne] com o antigo "ate 2 loads" coletava
        // v2 e 2, IGNORAVA o irem e emitia `v2 == 0` — CODIGO ERRADO
        // COMPILAVEL (medido: 0 0 1 3 6... vs oracle 0 0 1 1 4...).
        // Se o bloco do teste tem qualquer calculo, o test-expr nao e
        // recuperavel aqui: null = stub honesto (R6).
        int arity = (last.opcode() >= 0x9f && last.opcode() <= 0xa4) ? 2 : 1;
        if (block.size() - 1 < arity) return null;
        // Path EXATO original: o bloco do teste é SÓ os loads (aridade igual).
        // Fase E (14/10): se um load falha (ex.: `ldc 65535` — fora do
        // bipush), CAI no mini-avaliador (que modela o ldc) em vez de
        // recusar — medido no parsePort: o `||` quebrava no 2º termo.
        if (block.size() - 1 == arity) {
            java.util.List<String> operands = new java.util.ArrayList<>();
            boolean exactOk = true;
            for (int i = 0; i < block.size() - 1; i++) {
                String v = BytecodeDecoderStack.loadValue(block.get(i), frame, cp);
                if (v == null) { exactOk = false; break; }
                operands.add(v);
            }
            if (exactOk) {
                return operands.get(0) + " " + inv + (arity == 2 ? " " + operands.get(1) : "");
            }
        }
        // Fase E (14/10): comparação LONG — o javac emite [x, y, lcmp, ifXX]
        // (o ifXX testa o SINAL do lcmp): a condição real tem DOIS operandos.
        // A queda do ifXX é o inv de 2 operandos = invCond(ifXX + 0x06).
        int lastOp = block.get(block.size() - 1).opcode();
        // Fase E (14/10): a família de comparação por SINAL alarga — o lcmp
        // (0x94, Long) E o dcmpg/dcmpl (0x98/0x97, Double; o NaN-handling
        // difere mas o shape [a, b, cmp, ifXX] é o mesmo, medido no
        // inCircle: `d <= r*r`).
        // Fase E (15/10): a guarda PUREZA — o par-case só vale no bloco
        // EXATO [op2, op1, cmp, ifXX]; com prólogo fundido (medido no
        // withTax: [dstore, dload, ldc2, dcmpl, ifle]) o cond saía do rabo
        // e o PRÓLOGO era engolido (cond referenciava local não-declarada =
        // código errado). O prólogo é do caminho fundido (testSplit).
        if (lastOp >= 0x99 && lastOp <= 0x9e && block.size() == 4
                && (block.get(block.size() - 2).opcode() == 0x94
                    || block.get(block.size() - 2).opcode() == 0x97
                    || block.get(block.size() - 2).opcode() == 0x98)) {
            String x = BytecodeDecoderStack.loadValue(block.get(block.size() - 4), frame, cp);
            String y = BytecodeDecoderStack.loadValue(block.get(block.size() - 3), frame, cp);
            String lcmpInv = switch (lastOp) {
                case 0x99 -> "!="; case 0x9a -> "=="; case 0x9b -> ">=";
                case 0x9c -> "<"; case 0x9d -> "<="; case 0x9e -> ">";
                default -> null;
            };
            if (x != null && y != null && lcmpInv != null) return x + " " + lcmpInv + " " + y;
            return null;
        }
        // Fase E (fatia array-cond, 14/10): o teste tem CÁLCULO simples —
        // mini-avaliador de pilha com conjunto FECHADO de opcodes (cada um
        // modelado exatamente; qualquer outro → null = stub honesto). No fim
        // a pilha tem EXATAMENTE os `arity` operandos em ordem.
        java.util.Deque<String> st = new java.util.ArrayDeque<>();
        // Fase E (14/10): STORES no trecho = statements que o caller precisa
        // emitir (o fusion-path) — a memória de locais do avaliador não pode
        // engoli-las (medido: `var v1 = 0` do prólogo sumia e o corpo
        // referenciava v1 antes da declaração, SEM011).
        for (BytecodeReader.Insn in : block.subList(0, block.size() - 1)) {
            int sop = in.opcode();
            if (sop >= 0x36 && sop <= 0x4e) return null;
        }
        // Fase E (16/10): o PAR-CMP GERAL (o espelho do TAKEN) — o bloco
        // [ops..., cmp, ifXX] com o cmp antes do ifXX: o avaliador no
        // prefixo → 2 valores → `x inv y` (medido no isRightAngle: o 2º
        // termo do || com o dcmpl + ldc2_w num bloco de 5 insns).
        if (lastOp >= 0x99 && lastOp <= 0x9e && block.size() >= 3) {
            int cmpIdxF = block.size() - 2;
            int co = block.get(cmpIdxF).opcode();
            if (co == 0x94 || co == 0x97 || co == 0x98) {
                String fallOp = switch (lastOp) {
                    case 0x99 -> "!="; case 0x9a -> "=="; case 0x9b -> ">=";
                    case 0x9c -> "<"; case 0x9d -> "<="; case 0x9e -> ">";
                    default -> null;
                };
                if (fallOp != null) {
                    java.util.Deque<String> stF = new java.util.ArrayDeque<>();
                    boolean pureF = true;
                    for (BytecodeReader.Insn in : block.subList(0, cmpIdxF)) {
                        int sop = in.opcode();
                        if (sop >= 0x36 && sop <= 0x4e) { pureF = false; break; }
                    }
                    if (pureF && evalInto(block.subList(0, cmpIdxF), frame, cp, stF)
                            && stF.size() == 2) {
                        String bF = stF.pop();
                        String aF = stF.pop();
                        return aF + " " + fallOp + " " + bF;
                    }
                    return null;
                }
            }
        }
        if (!evalInto(block.subList(0, block.size() - 1), frame, cp, st)) return null;
        if (st.size() != arity) return null;
        if (arity == 1) {
            String v = st.pop();
            // Fase E (14/10): instanceof como condição — o inv `!= 0` sobre
            // Bool é ruído (espelho do testSplit/testExpr; medido describe)
            if (block.get(block.size() - 2).opcode() == 0xc1) {
                if (v.startsWith("(") && v.endsWith(")")) v = v.substring(1, v.length() - 1);
                return "!= 0".equals(inv) ? v : "!(" + v + ")";
            }
            return v + " " + inv;   // ifne→`== 0` etc.
        }
        String b = st.pop();
        String a = st.pop();
        return a + " " + inv + " " + b;
    }

    /**
     * Avalia a expressão do teste com conjunto FECHADO: loads, pushes de
     * constante, `arraylength`, binárias + - * / %. Exato por construção —
     * os opcodes são modelados 1:1; qualquer outro recusa (R6: nunca código
     * errado compilável — a lição da regressão do irem acima).
     */
    /**
     * Fase C (13/10): a condição do teste por BUSCA DE SUFIXO — o bloco pode
     * ter um PRÓLOGO (stores/chamadas) antes da expressão; tenta sufixos
     * crescentes com o avaliador fechado e aceita o que deixar EXATAMENTE os
     * `arity` operandos. Exato por construção (o mesmo conjunto fechado);
     * SÓ use onde o prólogo É emitido pelo chamador (o path do prologue no
     * struct) — em outro lugar o prólogo seria descartado em silêncio.
     */
    record TestSplit(String cond, int prologueLen) {}

    /**
     * Fase C (13/10): o SPLIT seguro do bloco de teste em
     * (prólogo-statements, expressão-da-condição). O prólogo DEVE terminar
     * num terminador de statement (store/pop) ou ser vazio — senão o sufixo
     * seria um fragmento de expressão (o risco MEDIDO: `[ldc "(", at]`
     * avalia mas reaproveita o `(` como receptor — código ERRADO). A
     * expressão (sufixo) tem de avaliar para EXATAMENTE `arity` valores.
     */
    static TestSplit testSplit(java.util.List<BytecodeReader.Insn> blk, BytecodeFrame frame,
                               String[] cp, int arity, String inv) {
        return testSplit(blk, frame, cp, arity, inv, false);
    }

    /**
     * Fase E (15/10): o overload com allowLoad — o pre terminando em LOAD
     * (o aload: o RECEIVER da call do join andando na pilha). O caller TEM
     * que validar o shape callarg (o join = [call 1-arg, pop]) ANTES de
     * usar — sem o consumo o receiver se perde = código errado (R6).
     */
    static TestSplit testSplit(java.util.List<BytecodeReader.Insn> blk, BytecodeFrame frame,
                               String[] cp, int arity, String inv, boolean allowLoad) {
        int max = Math.min(blk.size() - 1, 24);
        for (int len = arity; len <= max; len++) {
            java.util.List<BytecodeReader.Insn> pre = blk.subList(0, blk.size() - 1 - len);
            java.util.List<BytecodeReader.Insn> suf = blk.subList(blk.size() - 1 - len, blk.size() - 1);
            if (!pre.isEmpty()) {
                int lo = pre.get(pre.size() - 1).opcode();
                // Fase E (24/10): putstatic/putfield também encerram o
                // statement (o primeiro statement do corpo do do-while
                // fundido ao header — medido no sep: `OUT = i; if ...`)
                boolean stmtEnd = (lo >= 0x36 && lo <= 0x4e) || lo == 0x57
                        || lo == 0xb3 || lo == 0xb5
                        || (allowLoad && (lo == 0x19 || (lo >= 0x2a && lo <= 0x2d)));
                if (!stmtEnd) continue;
            }
            java.util.Deque<String> st = new java.util.ArrayDeque<>();
            boolean hasStore = false;
            for (BytecodeReader.Insn sin : suf) {
                int sop = sin.opcode();
                if (sop >= 0x36 && sop <= 0x4e) { hasStore = true; break; }
            }
            if (hasStore) continue;   // stores = statements (fusion-path)
            // Fase E (15/10): a família de SINAL no sufixo — [ops.., lcmp/
            // dcmpl/dcmpg]: a fatia ANTES do cmp deixa EXATAMENTE 2 valores
            // (evalInto avalia loads/consts/arith) e o cond é a composição
            // DIRETA `a inv b` (o espelho do pair-case do blockCondition;
            // medido no withTax: `if (v3 > 200.0)` com o prólogo emitido).
            int tailOp = suf.get(suf.size() - 1).opcode();
            if (tailOp == 0x94 || tailOp == 0x97 || tailOp == 0x98) {
                java.util.Deque<String> st2 = new java.util.ArrayDeque<>();
                if (!evalInto(suf.subList(0, suf.size() - 1), frame, cp, st2)) continue;
                if (st2.size() != 2) continue;
                String b2 = st2.pop();
                String a2 = st2.pop();
                // o inv AQUI é o espelho 2-op (o inv do caller é o
                // valor-teste `> 0` — medido: saía `v3 > 0 200.0`)
                String cmpInv = switch (blk.get(blk.size() - 1).opcode()) {
                    case 0x99 -> "!="; case 0x9a -> "=="; case 0x9b -> ">=";
                    case 0x9c -> "<"; case 0x9d -> "<="; case 0x9e -> ">";
                    default -> null;
                };
                if (cmpInv == null) continue;
                return new TestSplit(a2 + " " + cmpInv + " " + b2, blk.size() - 1 - len);
            }
            if (!evalInto(suf, frame, cp, st)) continue;
            if (st.size() != arity) continue;
            String cond;
            if (arity == 1) {
                String v = st.pop();
                // Fase E (14/10): o sufixo terminando em INSTANCEOF — o
                // inv `!= 0`/`== 0` sobre Bool é ruído; o instanceof É a
                // condição (ifeq salta no FALSO → a queda é o verdadeiro =
                // o instanceof puro; ifne → negado).
                int lop = suf.get(suf.size() - 1).opcode();
                if (lop == 0xc1) {
                    if (v.startsWith("(") && v.endsWith(")")) v = v.substring(1, v.length() - 1);
                    cond = "!= 0".equals(inv) ? v : "!(" + v + ")";
                } else {
                    cond = v + " " + inv;
                }
            } else {
                String b = st.pop();
                String a = st.pop();
                cond = a + " " + inv + " " + b;
            }
            return new TestSplit(cond, blk.size() - 1 - len);
        }
        return null;
    }

    static String testExpr(java.util.List<BytecodeReader.Insn> seq, BytecodeFrame frame,
                           int arity, String inv, String[] cp) {
        java.util.Deque<String> st = new java.util.ArrayDeque<>();
        for (BytecodeReader.Insn in : seq) {
            int sop = in.opcode();
            if (sop >= 0x36 && sop <= 0x4e) return null;   // stores = statements
        }
        if (!evalInto(seq, frame, cp, st)) return null;
        if (st.size() != arity) return null;
        if (arity == 1) {
            String v = st.pop();
            // Fase E (14/10): instanceof como condição — o inv `!= 0` sobre
            // Bool é ruído (espelho do testSplit; medido no describe)
            int lop = seq.get(seq.size() - 1).opcode();
            if (lop == 0xc1) {
                if (v.startsWith("(") && v.endsWith(")")) v = v.substring(1, v.length() - 1);
                return "!= 0".equals(inv) ? v : "!(" + v + ")";
            }
            return v + " " + inv;
        }
        String b = st.pop();
        String a = st.pop();
        return a + " " + inv + " " + b;
    }

    /**
     * Fase C (13/10): avalia a EXPRESSÃO de um braço curto (os pushes antes
     * do goto/xreturn) — o valor pendente que o join consome. O MESMO
     * conjunto fechado do testExpr; exatamente 1 valor ou null.
     */
    static String stackValue(java.util.List<BytecodeReader.Insn> seq, BytecodeFrame frame, String[] cp) {
        java.util.Deque<String> st = new java.util.ArrayDeque<>();
        if (!evalInto(seq, frame, cp, st)) return null;
        return st.size() == 1 ? st.pop() : null;
    }

    /**
     * O avaliador de conjunto FECHADO compartilhado (testExpr/stackValue).
     * loads, pushes de constante, `arraylength`, binárias + - * / %,
     * chamadas (os MESMOS gates R6 do emitLinear: JDK-owner recusa; método
     * em objeto novo recusa), getstatic/getfield, checkcast. Exato por
     * construção; qualquer outro opcode → false.
     */
    static boolean evalInto(java.util.List<BytecodeReader.Insn> seq, BytecodeFrame frame,
                            String[] cp, java.util.Deque<String> st) {
        return BytecodeEval.evalInto(seq, frame, cp, st);
    }

    /** Converte o topo da pilha com o metodo Kof do opcode (0x85-0x90), ou false. */


    // ── statement-based body (loops / stores / multi-statement) ──────────
}
