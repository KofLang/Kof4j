package dev.kof.cli;

import dev.kof.compiler.parser.ClassFileParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Detecção de Java record no decompilador (Fase E — fila medida 13/09: ~215
 * dos 686 classes do corpus são record; os corpos {@code toString}/{@code
 * hashCode}/{@code equals} são sintéticos ({@code invokedynamic ObjectMethods}
 * — o corpo NÃO existe no bytecode) e, como o esqueleto atual emitia
 * {@code class X extends Record} + corpo-stub, cada um gerava 3 stubs
 * silenciosos. O Kof tem {@code record} nativo (dados imutáveis — regra de
 * ferro): um record PURO (só componentes + os 4 métodos gerados, sem lógica
 * extra, sem {@code implements} — o frontend dá PARSE007) é recuperado como
 * {@code record X(T a, T b)}, que já gera ctor/accessors/equals/hashCode/
 * toString. Qualquer desvio do shape → null → output de hoje (fallback
 * honesto, zero-drift por construção).
 */
final class BytecodeRecords {

    private BytecodeRecords() {}

    /** Palavras que o parser Kof não aceita como nome de componente (Lexer.KEYWORDS). */
    private static final Set<String> RESERVED = Set.of(
            "class", "interface", "record", "enum", "entity", "extern", "generated", "unique",
            "extends", "implements", "fun", "fn", "func", "package", "import", "public", "private",
            "protected", "static", "final", "abstract", "transient", "volatile", "synchronized",
            "native", "default", "override", "void", "new", "this", "super", "return", "throw",
            "if", "else", "for", "while", "do", "switch", "case", "break", "continue", "try",
            "catch", "finally", "spawn", "await", "assert", "instanceof", "var", "val", "as",
            "bool", "byte", "short", "int", "long", "float", "double", "char", "string",
            "true", "false", "null");

    /** Header de um record puro: componentes + interfaces JÁ resolvidas (nomes Kof). */
    record Rec(List<ClassFileParser.FieldInfo> components, List<String> interfaces) {}

    /**
     * Igual a {@link #pureRecordComponents} mas aceita `implements` de nomes
     * que RESOLVEM no escopo da árvore. Conservador: JDK / fora-da-árvore /
     * ambíguo / interna CROSS-pacote (import `p.Outer$I` não provado) →
     * null = skeleton (porque `record X implements Desconhecida` é
     * PKG006/SEM011 = drift). Interna do MESMO pacote é aceita: o decompiler
     * emite `Outer$Inner` como tipo TOP de arquivo irmão e o frontend aceita
     * `record X$Y implements X$Z(...)` (probe 13/09; nomes únicos na árvore).
     * scope null (modo 1-arquivo): só resolve se for do MESMO pacote (sem
     * import); JDK/fora → skeleton (bytes idênticos ao estágio 1).
     */
    /**
     * Fase E (14/10): Java enum PURO → `enum Nome { A, B, C }` (o Kof tem
     * enum nativo — medido: compila e roda). Shape ESTRITO: super
     * java/lang/Enum, TODA field é static do próprio tipo (as constantes, na
     * ordem do source) e os métodos são EXATAMENTE os sintéticos
     * {&lt;init&gt;, &lt;clinit&gt;, values, valueOf}. Qualquer desvio (método
     * extra, field de instância) → null = o esqueleto de hoje (zero-drift).
     */
    static String pureEnum(ClassFileParser.ClassFile ir) {
        if (!"java/lang/Enum".equals(ir.superClass)) return null;
        String self = ir.thisClass;
        int slash = self.lastIndexOf('/');
        String simple = slash >= 0 ? self.substring(slash + 1) : self;
        String selfDesc = "L" + self + ";";
        List<String> consts = new ArrayList<>();
        for (var f : ir.fields) {
            if ((f.accessFlags & 0x0008) == 0) return null;      // field de instância
            if (f.name.equals("$VALUES")) continue;              // o array sintético
            if (!f.descriptor.equals(selfDesc)) return null;     // não-constante
            consts.add(f.name);
        }
        if (consts.isEmpty()) return null;
        for (var m : ir.methods) {
            if (m.name.equals("<init>") || m.name.equals("<clinit>")
                    || m.name.equals("values") || m.name.equals("valueOf")
                    || m.name.equals("$values")) continue;   // sintéticos do javac
            return null;   // método extra = enum não-puro
        }
        return "enum " + simple + " { " + String.join(", ", consts) + " }";
    }

    static Rec pureRecord(ClassFileParser.ClassFile ir, TreeScope scope) {
        var comps = pureRecordComponentsNoIface(ir);
        if (comps == null) return null;
        if (ir.interfaces == null || ir.interfaces.length == 0) return new Rec(comps, List.of());
        String myPkg = scope == null ? null : scope.currentPkg();
        List<String> ifaces = new ArrayList<>();
        for (String i : ir.interfaces) {
            int slash = i.lastIndexOf('/');
            String pkg = slash < 0 ? "" : i.substring(0, slash).replace('/', '.');
            String simple = slash < 0 ? i : i.substring(slash + 1);
            if (pkg.equals(myPkg) && scope.inIndex(i)) {
                // MESMO pacote E presente no índice da árvore: top OU interna.
                // O decompiler emite cada `Outer$Inner` como classe TOP num
                // arquivo irmão (probe 13/09: `record X$Y implements X$Z(...)`
                // compila; SEM042 proíbe aninhamento, mas nome top com `$` é
                // aceito). Os 688 nomes simples da árvore são globalmente
                // únicos (medido). inIndex: árvore parcial → sem irmão = drift.
                ifaces.add(simple);
            } else if (scope == null || i.indexOf('$') >= 0) {
                return null;   // cross-pacote interna: import `p.Outer$I` não provado → skeleton
            } else {
                String r = scope.resolve(i);
                if (r == null) return null;                          // ambíguo/fora/JDK → skeleton
                ifaces.add(r);
            }
        }
        return new Rec(comps, ifaces);
    }

    /** Componentes de record PURO SEM `implements` (estágio 1; chamador decide interfaces). */
    private static List<ClassFileParser.FieldInfo> pureRecordComponentsNoIface(ClassFileParser.ClassFile ir) {
        if (!ir.attributes.containsKey("Record")) return null;
        if (typeParams(ir.classSignature) == null) return null;
        if (ir.fields == null || ir.fields.isEmpty()) return null;
        for (var f : ir.fields) {
            if ((f.accessFlags & 0x0008) != 0) return null;           // static — não é componente
            if ((f.accessFlags & 0x0010) == 0) return null;           // componente de record é final
            if (RESERVED.contains(f.name)) return null;
            if (f.name.equals("equals") || f.name.equals("hashCode") || f.name.equals("toString")) return null;
        }

        int inits = 0, n = ir.fields.size();
        for (var m : ir.methods) {
            if (m.code == null) return null;                      // método abstrato/nativo → não-puro
            var ins = BytecodeReader.decode(m.code.bytecode);
            if (m.name.equals("<init>")) {
                inits++;
                if (!initMatches(ins, m, ir)) return null;
            } else if (m.name.equals("equals")) {
                if (!synthMatches(ins, m, ir, true)) return null;
            } else if (m.name.equals("hashCode") || m.name.equals("toString")) {
                if (!synthMatches(ins, m, ir, false)) return null;
            } else {
                int fi = -1;
                for (int k = 0; k < n; k++) if (ir.fields.get(k).name.equals(m.name)) fi = k;
                if (fi < 0 || !accessorMatches(ins, m, ir, fi)) return null;
            }
        }
        if (inits != 1) return null;
        if (!ir.superClass.equals("java/lang/Record")) return null;
        // exatamente {ctor, equals, hashCode, toString} + 1 accessor por componente
        int expected = 4 + n;
        int seen = 0;
        for (var m : ir.methods) {
            if (m.name.equals("<init>") || m.name.equals("equals")
                    || m.name.equals("hashCode") || m.name.equals("toString")) seen++;
            else { for (var f : ir.fields) if (f.name.equals(m.name)) { seen++; break; } }
        }
        return seen == expected ? ir.fields : null;
    }

    private static boolean isRet(int op) { return op >= 0xac && op <= 0xb1; }
    private static boolean isLoad(int op) { return op >= 0x15 && op <= 0x2d; }

    /** ctor: aload_0; invokespecial Record.<init>; N×(aload_0;load;putfield f_i); return. */
    private static boolean initMatches(List<BytecodeReader.Insn> ins,
                                       ClassFileParser.MethodInfo m, ClassFileParser.ClassFile ir) {
        int n = ir.fields.size();
        if (ins.size() != 3 + 3 * n) return false;
        if (ins.get(0).opcode() != 0x2a || ins.get(1).opcode() != 0xb7) return false;
        if (!m.descriptor.endsWith(")V")) return false;
        for (int k = 0; k < n; k++) {
            if (ins.get(2 + 3 * k).opcode() != 0x2a) return false;
            if (!isLoad(ins.get(3 + 3 * k).opcode())) return false;
            if (!putfieldPointsTo(ins.get(4 + 3 * k), ir.fields.get(k).name, ir)) return false;
        }
        return ins.get(2 + 3 * n).opcode() == 0xb1;
    }

    /** equals: aload_0; aload_1; invokedynamic; ret. hash/toString: aload_0; invokedynamic; ret. */
    private static boolean synthMatches(List<BytecodeReader.Insn> ins,
                                        ClassFileParser.MethodInfo m, ClassFileParser.ClassFile ir,
                                        @SuppressWarnings("unused") boolean eq) {
        int sz = eq ? 4 : 3;
        if (ins.size() != sz) return false;
        if (ins.get(0).opcode() != 0x2a) return false;
        if (eq && ins.get(1).opcode() != 0x2b) return false;
        if (ins.get(sz - 2).opcode() != 0xba) return false;
        return isRet(ins.get(sz - 1).opcode());
    }

    /** accessor: aload_0; getfield <nome>; ret — e o descriptor casa com o campo. */
    private static boolean accessorMatches(List<BytecodeReader.Insn> ins, ClassFileParser.MethodInfo m,
                                           ClassFileParser.ClassFile ir, int fieldIdx) {
        if (ins.size() != 3) return false;
        if (ins.get(0).opcode() != 0x2a || ins.get(1).opcode() != 0xb4) return false;
        if (!isRet(ins.get(2).opcode())) return false;
        return getfieldPointsTo(ins.get(1), ir.fields.get(fieldIdx).name, ir)
                && m.descriptor.equals("()" + ir.fields.get(fieldIdx).descriptor);
    }

    private static boolean putfieldPointsTo(BytecodeReader.Insn in, String field, ClassFileParser.ClassFile ir) {
        return in.opcode() == 0xb5 && refNameEquals(ir.constantPool, in.operands()[0], field);
    }

    private static boolean getfieldPointsTo(BytecodeReader.Insn in, String field, ClassFileParser.ClassFile ir) {
        return in.opcode() == 0xb4 && refNameEquals(ir.constantPool, in.operands()[0], field);
    }

    private static boolean refNameEquals(String[] cp, int idx, String name) {
        String[] r = BytecodeCp.resolveMethodRef(cp, idx);   // Fieldref e Methodref têm o mesmo layout
        return r != null && name.equals(r[1]);
    }

    /**
     * Type-params da assinatura da classe (JVMS 4.7.9.1) na forma que o Kof
     * usa: {@code record Nome<T>}. Aceita só a forma que o javac gera p/
     * records reais — {@code Ident:Lclass;} repetido ({@code <T:Ljava/lang/Object;>}).
     * Qualquer desao (bound genérico, interface-bound {@code ::}, tipo base)
     * → {@code null}: o chamador RECUSA o record (skeleton atual) em vez de
     * emitir {@code <T>} errado (regra: recuperar código que não compila é proibido).
     */
    static List<String> typeParams(String sig) {
        if (sig == null) return List.of();
        if (sig.isEmpty()) return List.of();
        if (sig.charAt(0) != '<') return List.of();            // sem type params
        List<String> names = new ArrayList<>();
        int i = 1;
        while (i < sig.length()) {
            if (sig.charAt(i) == '>') return names;             // fim do bloco de type params
            int s = i;
            while (i < sig.length() && Character.isJavaIdentifierPart(sig.charAt(i))) i++;
            if (i == s) return null;                            // não-nome → forma não-suportada
            names.add(sig.substring(s, i));
            if (i >= sig.length()) return null;
            char c = sig.charAt(i);
            if (c == '>') return names;                         // último param, sem bound
            if (c != ':') return null;                          // javac sempre emite ':' → conservador
            i++;
            if (i < sig.length() && sig.charAt(i) == ':') return null;   // interface-bound extra (::)
            if (i >= sig.length() || sig.charAt(i) != 'L') return null;  // só class bound simples
            i++;
            while (i < sig.length() && sig.charAt(i) != ';') {
                if (sig.charAt(i) == '<') return null;          // bound genérico
                i++;
            }
            if (i >= sig.length()) return null;
            i++;                                                 // consome ';'
        }
        return null;                                             // nunca viu '>'
    }
}
