package dev.kof.cli;

/**
 * Tipos Kof "inline" (sempre em escopo num .kf isolado) + handlers de
 * expressão p/ opcodes de teste-emit, usados pelos decoders p/
 * pop/instanceof/checkcast. Extraído p/ o gate ≤500; a SEMÂNTICA é a regra
 * R6 do decompiler: só emitir código cujo nome de tipo resolve sem import e
 * cujo shape não pode driftar (prova de drift 09/09 — tipos de domínio
 * viravam "Undefined variable or type" no kof check do output).
 */
final class BytecodeKofTypes {

    // Fase E (14/10): o contador dos temps de array fresh (por método —
    // resetado pelo chamador via resetArrTmps).
    private static int arrTmpCount = 0;

    /** Zera o contador dos temps de array (por método recuperado). */
    static void resetArrTmps() { arrTmpCount = 0; }

    private BytecodeKofTypes() {
    }

    /**
     * Handler dos opcodes de EXPRESSÃO no emitLinear. true = manipulou a
     * stack (ops ok); false = recusar (→ stub honesto). Chamado com a pilha
     * não-vazia. pop (0x57) devolve stmt (a chamada de efeito descartada) ou
     * null (topo não-era-chamada); instanceof (0xc1)/checkcast (0xc0) empurram
     * o valor (cast de Object p/ primitivo-alvo) ou recusam (null) — o emit
     * então abandona. Tipos fora da whitelist (String/primitivos/Object)
     * SEMPRE recusam.
     */
    /**
     * Handler dos opcodes de EXPRESSÃO no emitLinear. true = manipulou a
     * stack (ops ok); false = recusar (→ stub honesto). Chamado com a pilha
     * não-vazia. pop (0x57) devolve stmt (a chamada de efeito descartada) ou
     * null (topo não-era-chamada); instanceof (0xc1)/checkcast (0xc0) empurram
     * o valor (cast de Object p/ primitivo-alvo) ou recusam (null) — o emit
     * então abandona. Tipos fora da whitelist (String/primitivos/Object)
     * SEMPRE recusam.
     */
    static boolean statementOp(int op, BytecodeReader.Insn in, java.util.Deque<String> stack,
                               String[] cp, java.util.List<String> stmts, BytecodeFrame frame) {
        switch (op) {
            case 0x57, 0xc0, 0xc1 -> {
                if (stack.isEmpty()) return false;
                int idx = op == 0x57 ? 0 : in.operands()[0];   // pop não tem operando
                return exprOp(op, stack, cp, idx, stmts, frame);
            }
            case 0xbb -> { // new de DOMÍNIO (mirror do path linear):
                // `new java.lang.X` nunca é idiomático (R6). EXCEÇÃO: o
                // idioma errors.md (a exceção É a String do message — o
                // TranslateNew mapeia igual) — `new RuntimeException(msg)`
                // entra com marcador próprio e o <init> devolve os args.
                String cn = BytecodeCp.resolveClassName(cp, in.operands()[0]);
                if (cn == null) return false;
                if (BytecodeCp.isJdkClass(cp, in.operands()[0])) {
                    String simple = cn.substring(cn.lastIndexOf('/') + 1);
                    // Fase E (14/10): o StringBuilder vira STRING no Kof
                    // (SEM015 — o idioma é concat); a marca alimenta o
                    // astore (var sb = "") e os appends (sb = sb + x).
                    if (simple.equals("StringBuilder")) {
                        stack.push("⟦sb⟧");
                        return true;
                    }
                    if (simple.endsWith("Exception") || simple.endsWith("Error")) {
                        stack.push("\u27e6ex\u27e7" + simple);
                        if (BytecodeDecoder.blockerSink != null) BytecodeDecoder.blockerSink.accept(-99);
                        return true;
                    }
                    // Fase E (13/10): mapeamento JDK→stdlib de COLEÇÕES — o
                    // MESMO que o tradutor tem (new ArrayList()→listOf(),
                    // new HashMap()→mapOf()); o <init> devolve o literal. Sem
                    // o mapa, `new ArrayList()` emitia nada → stub.
                    String kof = collectionLiteral(simple);
                    if (kof != null) {
                        stack.push("\u27e6col\u27e7" + kof);
                        return true;
                    }
                    return false;
                }
                if (frame != null && frame.treeScope != null) {
                    String internal = indexInternalName(cp, in.operands()[0]);
                    if (internal != null) frame.treeScope.resolve(internal);
                }
                stack.push("⟦new⟧" + cn);
                return true;
            }
            case 0x59 -> { // dup — new, exceção, coleção E o multi-assign
                // Fase E (13/10): o `x = y = 5` compila p/ iconst_5, dup,
                // istore, istore — o dup de um valor SIMPLES (sem "(") é puro
                // (sem efeito colateral a duplicar). Chamadas seguem recusadas
                // (duplicar uma chamada executaria duas vezes).
                if (stack.isEmpty()) return false;
                String top = stack.peek();
                if (top == null) return false;
                if (top.startsWith("⟦new⟧") || top.startsWith("\u27e6ex\u27e7")
                        || top.startsWith("\u27e6col\u27e7") || top.startsWith("⟦sb⟧")) {
                    stack.push(top);
                    return true;
                }
                // Fase E (14/10): o ARRAY LITERAL fresh (`new Int[2]` + dup +
                // iastore×N + return) → temp: `var arrT1 = new Int[2]` + as
                // stores no var (o texto `new Int[2][0] = v` não parseia;
                // medido no minMax). O nome único por recuperação.
                if (top.matches("new \\w+\\[.*")) {
                    stack.pop();
                    String tmp = "arrT" + (++arrTmpCount);
                    stmts.add("var " + tmp + " = " + top);
                    stack.push(tmp);
                    stack.push(tmp);
                    return true;
                }
                // o dup de um valor PURO (aritmetica/variavel) e seguro — o
                // texto aparece duas vezes mas avalia igual. O risco e apenas
                // CHAMADA (executaria duas vezes): rejeita se ha `ident(` no
                // texto (medido: `(arg0 * 2)` do imul passa; `at(` recusa).
                if (!top.matches(".*\\w\\(.*")) {
                    stack.push(top);
                    return true;
                }
                return false;
            }
            case 0xb7 -> { // invokespecial <init> (mirror do linear)
                String[] m = BytecodeCp.resolveMethodRef(cp, in.operands()[0]);
                if (m == null) return false;
                if (!"<init>".equals(m[1])) {
                    // Fase G (diamond super.m(), mantenedora 14/10): owner ==
                    // super corrente → `super.m(args)` (Kof suporta, medido);
                    // demais (interface `A.super.m()`) seguem não-recuperados
                    // aqui até a fatia do inline multi-classfile.
                    if (BytecodeDecoder.currentSuperInternal != null
                            && BytecodeDecoder.currentSuperInternal.equals(m[0])) {
                        int argc = BytecodeCp.argCount(m[2]);
                        if (stack.size() < argc + 1) return false;
                        var callArgs = new java.util.ArrayList<String>();
                        for (int i = 0; i < argc; i++) callArgs.add(0, stack.pop());
                        String recv = stack.pop();
                        if (!"this".equals(recv)) return false;
                        stack.push("super." + m[1] + "(" + String.join(", ", callArgs) + ")");
                        return true;
                    }
                    return false;
                }
                int argc = BytecodeCp.argCount(m[2]);
                // Fase E (14/10): o `invokespecial Object.<init>` do CONSTRUCTOR
                // com só `this` na pilha (o ctor decompilado é [aload_0, super])
                // → SKIP: o Kof chama super implicitamente (classes.md, a forma
                // real `constructor(...) { this.x = x }` não tem super()).
                if ("java/lang/Object".equals(m[0]) && argc == 0
                        && stack.size() == 1 && "this".equals(stack.peek())) {
                    stack.pop();
                    return true;
                }
                // Fase E (14/10): o `invokespecial B.<init>` do CONSTRUCTOR
                // (o shape [this, args] SEM dup) → `super(args)` explícito
                // (medido: o Kof aceita super(v) no ctor e o Base.v recebe).
                if (!"java/lang/Object".equals(m[0]) && stack.size() == argc + 1
                        && "this".equals(stack.peek())) {
                    var supArgs = new java.util.ArrayList<String>();
                    for (int i = 0; i < argc; i++) supArgs.add(0, stack.pop());
                    stack.pop();   // this
                    stack.push("super(" + String.join(", ", supArgs) + ")");
                    return true;
                }
                if (stack.size() < argc + 2) return false;
                var callArgs = new java.util.ArrayList<String>();
                for (int i = 0; i < argc; i++) callArgs.add(0, stack.pop());
                stack.pop();   // receiver (cópia do dup)
                String marker = stack.pop();
                if (marker == null) return false;
                // Fase E (14/10): StringBuilder() sem args — a marca segue
                // para o astore (var sb = "")
                if (marker.equals("⟦sb⟧")) {
                    stack.push(marker);   // o pop do receiver consumiu a cópia do dup — repõe
                    return true;
                }
                // idioma errors.md: a construção de EXCEÇÃO devolve a String
                // do message (o Kof lança String; `throw msg` direto)
                if (marker.startsWith("\u27e6ex\u27e7")) {
                    stack.push(String.join(", ", callArgs));
                    return true;
                }
                if (marker.startsWith("\u27e6col\u27e7")) {
                    // `new ArrayList<>()` sem args → listOf(); com args a
                    // semântica de cópia não é 1:1 (o tradutor recusa igual)
                    if (!callArgs.isEmpty()) return false;
                    stack.push(marker.substring("\u27e6col\u27e7".length()));
                    return true;
                }
                if (!marker.startsWith("⟦new⟧")) return false;
                stack.push(marker.substring("⟦new⟧".length()) + "(" + String.join(", ", callArgs) + ")");
                return true;
            }
            case 0xbd -> { // anewarray → `new T[n]` (mirror do path linear).
                // Elemento ESTRITO (String/Object/domínio; wrappers recusam).
                if (stack.isEmpty()) return false;
                String elem = arrayElementType(cp, in.operands()[0], frame);
                if (elem == null) return false;
                String n = stack.pop();
                stack.push("new " + elem + "[" + n + "]");
                return true;
            }
            case 0x2e, 0x2f, 0x30, 0x31, 0x32, 0x33, 0x34, 0x35 -> { // xaload → `a[i]`
                if (stack.size() < 2) return false;
                String idx = stack.pop();
                String arr = stack.pop();
                stack.push(arr + "[" + idx + "]");
                return true;
            }
            case 0x4f, 0x50, 0x51, 0x52, 0x53, 0x54, 0x55, 0x56 -> { // xastore → `a[i] = v`
                if (stack.size() < 3) return false;
                String v = stack.pop();
                String idx = stack.pop();
                String arr = stack.pop();
                // stmt sem valor (void no bytecode) — como `pop` de chamada
                stmts.add(arr + "[" + idx + "] = " + v);
                return true;
            }
            case 0xc5 -> { // multianewarray → `new Int[d1][d2]...`
                // (medido: new Int[3][2] + m[0][0] compila e roda no Kof).
                // O CP entry é o DESCRIPTOR do array ("[[I"); as dimensões
                // estão na pilha (operand[1] = quantidade).
                int dims = in.operands()[1];
                if (dims < 1 || dims > 3 || stack.size() < dims) return false;
                String internal = indexInternalName(cp, in.operands()[0]);
                if (internal == null || !internal.startsWith("[")) return false;
                String base = internal;
                while (base.startsWith("[")) base = base.substring(1);
                String elem = switch (base) {
                    case "I" -> "Int"; case "J" -> "Long"; case "F" -> "Float";
                    case "D" -> "Double"; case "Z" -> "Bool"; case "C" -> "Char";
                    case "B" -> "Byte"; case "S" -> "Short";
                    default -> null;
                };
                if (elem == null && base.startsWith("L") && base.endsWith(";")) {
                    String simple = base.substring(1, base.length() - 1);
                    int slash = simple.lastIndexOf('/');
                    simple = slash >= 0 ? simple.substring(slash + 1) : simple;
                    if (simple.equals("String") || simple.equals("Object")) elem = simple;
                }
                if (elem == null) return false;   // domínio — fora do slice
                String[] ds = new String[dims];
                for (int i = dims - 1; i >= 0; i--) ds[i] = stack.pop();
                StringBuilder sb2 = new StringBuilder("new ").append(elem);
                for (int i = 0; i < dims; i++) sb2.append("[").append(ds[i]).append("]");
                stack.push(sb2.toString());
                return true;
            }
            case 0xbc -> { // newarray → `new Int[n]` (arrays PRIMITIVOS; JVMS 6.5)
                if (stack.isEmpty()) return false;
                String elem = primitiveArrayType(in.operands()[0]);
                if (elem == null) return false;
                String n = stack.pop();
                stack.push("new " + elem + "[" + n + "]");
                return true;
            }
            case 0xbe -> { // arraylength → `a.length` (Kof idiomático)
                if (stack.isEmpty()) return false;
                stack.push(stack.pop() + ".length");
                return true;
            }
            default -> { return false; }
        }
    }

    /** Núcleo de 0x57/0xc0/0xc1 (pop/instanceof/checkcast); frame=null ok
     *  (modo 1-arquivo: sem índice, só whitelist). Chamado com pilha não-vazia. */
    static boolean exprOp(int op, java.util.Deque<String> stack, String[] cp, int cpIdx,
                          java.util.List<String> stmts, BytecodeFrame frame) {
        switch (op) {
            case 0x57 -> {   // pop: descarta CHAMADA de valor não-usado
                String top = stack.pop();
                if (top.equals("⟦sb⟧")) return true;   // append já virou statement
                if (!isCallExpr(top)) return false;
                stmts.add(top);
                return true;
            }
            case 0xc1 -> {   // instanceof → whitelist inline (String/primitivos/
                // Object) ou classe do ÍNDICE da árvore (§7 degrau 2). Fora
                // disso → stub (recusa R6: o nome não resolve no .kf).
                String t = inlineKofType(cp, cpIdx);
                if (t == null) t = indexKofType(cp, cpIdx, frame);
                if (t == null) return false;
                stack.push(stack.pop() + " instanceof " + t);
                return true;
            }
            case 0xc0 -> {   // checkcast → `(x as T)`
                // Fase E (fatia cast-String, mantenedora "stubs viram código",
                // 14/10): `as String` é FIEL — MEDIDO: compila, o cast correto
                // retorna e o errado LANÇA a CCE (a mesma verificação runtime
                // do checkcast Java); a recusa antiga ("redundante/ambíguo")
                // não se sustenta contra a execução. Object-alvo é no-op
                // (checkcast Object nunca falha para não-null) → mantém `x`.
                // Domínio SÓ via índice (§7 degrau 2) — nome resolve no tree.
                String t = inlineKofType(cp, cpIdx);
                if (t == null) {
                    t = indexKofType(cp, cpIdx, frame);
                    if (t == null) {
                        // Fase E (14/10): classe de DOMÍNIO não-JDK no modo
                        // 1-arquivo (sem treeScope) → o NOME SIMPLES é fiel:
                        // o .kf irmão decompilado declara a classe (medido no
                        // Repo/Prod: `(arg0.get(0) as Prod)` compila e roda)
                        String internal = indexInternalName(cp, cpIdx);
                        if (internal == null || BytecodeCp.isJdkClass(cp, cpIdx)) return false;
                        int slash = internal.lastIndexOf('/');
                        t = slash >= 0 ? internal.substring(slash + 1) : internal;
                    }
                    stack.push("(" + stack.pop() + " as " + t + ")");
                    return true;
                }
                if (t.equals("Object")) return true;   // no-op: o valor segue
                stack.push("(" + stack.pop() + " as " + t + ")");
                return true;
            }
            default -> { return false; }
        }
    }

    /**
     * Mapeia Class CP entry → tipo Kof que SEMPRE resolve num .kf isolado:
     * primitivos/String/Object. Devolve null p/ QUALQUER outro (classe de
     * domínio, Number, List/Map/Set — precisam de import/contexto
     * multi-classe do §7) → o emissor recusa → stub honesto.
     */
    static String inlineKofType(String[] cp, int classIdx) {        String n = BytecodeCp.resolveClassName(cp, classIdx);
        if (n == null || !n.matches("[A-Za-z_][A-Za-z0-9_]*")) return null;
        return switch (n) {
            case "String" -> "String";
            case "Integer" -> "Int";
            case "Long" -> "Long";
            case "Double" -> "Double";
            case "Float" -> "Float";
            case "Boolean" -> "Bool";
            case "Object" -> "Object";
            default -> null;   // domínio/Number/List/… → recusar (stub)
        };
    }

    /**
     * Elemento de `anewarray` → nome Kof, ou null (recusar → stub).
     * Regra ESTRITA (probes 09/09): `String`/`Object`/domínio-via-índice
     * compilam como `new T[n]`; wrappers (`Integer[]`) NÃO têm paralelo
     * (`new Int[n]` é `int[]`, semântica distinta) → recusar; arrays e
     * malformados caem no regex do índice.
     */
    /**
     * Fase E: o mapa JDK→stdlib de coleções (o mesmo do tradutor) — o literal
     * Kof para um `new java.util.X()` sem args, ou null (fora do mapa).
     */
    static String collectionLiteral(String simple) {
        return switch (simple) {
            case "ArrayList", "LinkedList" -> "listOf()";
            case "HashMap", "LinkedHashMap", "TreeMap" -> "mapOf()";
            case "HashSet", "LinkedHashSet", "TreeSet" -> "setOf()";
            default -> null;
        };
    }

    /** JVMS 6.5 newarray atype → tipo Kof do elemento, ou null. */
    static String primitiveArrayType(int atype) {
        return switch (atype) {
            case 4 -> "Bool";
            case 5 -> "Char";
            case 6 -> "Float";
            case 7 -> "Double";
            case 8 -> "Byte";
            case 9 -> "Short";
            case 10 -> "Int";
            case 11 -> "Long";
            default -> null;
        };
    }

    static String arrayElementType(String[] cp, int classIdx, BytecodeFrame frame) {
        String n = BytecodeCp.resolveClassName(cp, classIdx);
        if (n == null) return null;
        if (n.equals("String") || n.equals("Object")) return n;
        if (frame == null) return null;
        return indexKofType(cp, classIdx, frame);
    }

    /**
     * §7 degrau 2–3: resolve Class CP entry contra o escopo da árvore
     * (`kof decompile <dir>`). Mesmo pacote → simples (sem import);
     * outro pacote → simples + import (degrau 3), se globalmente único.
     * Fora disso (escopo ausente = modo 1-arquivo, outro pacote, `Outer$Inner`,
     * ambíguo, CP malformada) → null → stub honesto. Nunca inventa.
     */
    static String indexKofType(String[] cp, int classIdx, BytecodeFrame frame) {
        if (frame == null || frame.treeScope == null) return null;
        if (classIdx <= 0 || classIdx >= cp.length || cp[classIdx] == null) return null;
        String e = cp[classIdx];
        if (!e.startsWith("#") || e.indexOf('#', 1) >= 0) return null;   // Class = 1 ref (não NameAndType)
        Integer nameIdx = BytecodeCp.parseCp(e.substring(1));
        if (nameIdx == null || nameIdx >= cp.length || cp[nameIdx] == null) return null;
        return frame.treeScope.resolve(cp[nameIdx]);
    }

    /**
     * Internal name de Class CP entry (p/ `new`/uso direto), ou null.
     * Mesmas guardas do `indexKofType`, sem resolver (o chamador resolve).
     */
    static String indexInternalName(String[] cp, int classIdx) {
        if (classIdx <= 0 || classIdx >= cp.length || cp[classIdx] == null) return null;
        String e = cp[classIdx];
        if (!e.startsWith("#") || e.indexOf('#', 1) >= 0) return null;
        Integer nameIdx = BytecodeCp.parseCp(e.substring(1));
        if (nameIdx == null || nameIdx >= cp.length || cp[nameIdx] == null) return null;
        return cp[nameIdx];
    }

    /** Heurística p/ pop (0x57): o topo é uma chamada? exige '(' imediatamente
     *  antes do ')' final, sem operador aritmético em depth-0 (não casa
     *  `(a + (b))`); tolera receiver `x.m(...)`, `T(...)` de new. */
    static boolean isCallExpr(String s) {
        if (s.length() < 2 || s.charAt(s.length() - 1) != ')') return false;
        int depth = 0;
        int lastOpen = -1;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') {
                if (depth == 0) lastOpen = i;
                depth++;
            }
            else if (c == ')') depth--;
            else if (depth == 0 && (c == '+' || c == '-' || c == '*' || c == '/' || c == '%')) return false;
        }
        return lastOpen >= 1 && lastOpen < s.length() - 1 && s.charAt(lastOpen - 1) != ' ';
    }
}
