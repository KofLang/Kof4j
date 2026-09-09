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
    static boolean exprOp(int op, java.util.Deque<String> stack, String[] cp, int cpIdx,
                          java.util.List<String> stmts) {
        switch (op) {
            case 0x57 -> {   // pop: descarta CHAMADA de valor não-usado
                String top = stack.pop();
                if (!isCallExpr(top)) return false;
                stmts.add(top);
                return true;
            }
            case 0xc1 -> {   // instanceof → whitelist inline (String/primitivos/
                // Object). Tipo de domínio → null → stub (recusa R6: o nome
                // não resolve sem import no .kf isolado).
                String t = inlineKofType(cp, cpIdx);
                if (t == null) return false;
                stack.push(stack.pop() + " instanceof " + t);
                return true;
            }
            case 0xc0 -> {   // checkcast → `(x as T)` só p/ primitivo-alvo
                // (Kof: downcast Object→Int é explícito). String/Object
                // recusados: `x as String` é redundante/ambíguo na verificação.
                String t = inlineKofType(cp, cpIdx);
                if (t == null || t.equals("String") || t.equals("Object")) return false;
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
    static String inlineKofType(String[] cp, int classIdx) {
        String n = BytecodeDecoder.resolveClassName(cp, classIdx);
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

    /** Heurística p/ pop (0x57): o topo é uma chamada? exige '(' imediatamente
     *  antes do ')' final, sem operador aritmético em depth-0 (não casa
     *  `(a + (b))`); tolera receiver `x.m(...)`, `T(...)` de new. */
    static boolean isCallExpr(String s) {
        if (s.length() < 2 || s.charAt(s.length() - 1) != ')') return false;
        int depth = 0;
        int lastOpen = -1;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') { if (depth == 0) lastOpen = i; depth++; }
            else if (c == ')') depth--;
            else if (depth == 0 && (c == '+' || c == '-' || c == '*' || c == '/' || c == '%')) return false;
        }
        return lastOpen >= 1 && lastOpen < s.length() - 1 && s.charAt(lastOpen - 1) != ' ';
    }
}
